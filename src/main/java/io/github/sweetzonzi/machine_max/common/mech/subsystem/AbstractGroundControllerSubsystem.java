package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.attachment.ControlPreference;
import io.github.sweetzonzi.machine_max.common.mech.signal.EmptySignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalReceiver;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalSender;
import io.github.sweetzonzi.machine_max.common.mech.signal.MoveInputSignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.RegularInputSignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalChannel;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalResult;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.BasicSubsystemDynamicAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.CarControllerSubsystemStaticAttr.HandBrakeSoundAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.IPartAssembly;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import jme3utilities.math.MyQuaternion;
import lombok.Getter;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 地面载具控制器的共享部分。
 *
 * <p>承载驾驶输入、发动机/电动机/变速箱发现、自动换挡与共享输出；
 * 轮式转向/漂移/ABS 留在 {@link CarControllerSubsystem}，履带的差速分配留在
 * {@code TrackControllerSubsystem}。本类不假设执行器与控制器位于同一个
 * {@code SubPart}：local 名、连接点端口和 {@code global} 广播都是合法目标。</p>
 */
@Getter
public abstract class AbstractGroundControllerSubsystem extends BasicSubsystem {

    public LivingEntity controller;
    public byte[] moveInput;
    public byte[] moveInputConflict;
    public float speed = 0.0f;

    public boolean handBrake = false;
    public volatile float actualThrottle = 0f;
    public volatile float actualBrake = 0f;
    public volatile float actualHandBrake = 0f;
    public float actualSteering = 0f;

    /** 主线程：停用后已按配置线路复位过一次输出，避免每 tick 重复投递空信号。 */
    private boolean outputsReset;
    /** 客户端主线程的手刹声基线，首次 tick 和区块恢复后的首次 tick 只记录状态。 */
    private long lastHandBrakeSoundTick = Long.MIN_VALUE;
    private boolean lastHandBrakeSound;

    protected final ConcurrentMap<ISignalReceiver, Float> overrideCountDown = new ConcurrentHashMap<>();

    /** 服务端行车记录仪；null = 未录制。主线程开闭，物理线程只提交采样。 */
    @Nullable
    private volatile DriveTelemetry telemetry;
    /** 上一物理步的车速，用于算纵向加速度；仅物理线程读写。 */
    private float telemetryPreviousSpeed = Float.NaN;
    /**
     * 行车记录仪取姿态用的缓存：机身旋转、一个中转轴、三根机身轴（前/上/右）、线速度与角速度。
     * 仅物理线程读写。
     */
    private final Quaternion telemetryRotation = new Quaternion();
    private final Vector3f telemetryAxis = new Vector3f();
    private final Vector3f telemetryForward = new Vector3f();
    private final Vector3f telemetryUp = new Vector3f();
    private final Vector3f telemetryRight = new Vector3f();
    private final Vector3f telemetryVelocity = new Vector3f();
    private final Vector3f telemetryAngular = new Vector3f();

    /** 执行器绑定快照：整体替换发布，物理线程只读。键为到达频道。 */
    protected volatile Map<EngineSubsystem, String> engines = Map.of();
    protected volatile Map<MotorSubsystem, String> motors = Map.of();
    protected volatile Map<GearboxSubsystem, String> gearboxes = Map.of();

    protected float avgEngineMaxSpeed = 0f;
    protected float avgEngineMinSpeed = 0f;
    protected float avgEngineMaxTorqueSpeed = 0f;
    protected int engineCount = 0;
    protected int motorCount = 0;

    protected AbstractGroundControllerSubsystem(ISubsystemHost owner, String name, BasicSubsystemDynamicAttr attr) {
        super(owner, name, attr);
    }

    // ===== 子类提供的属性/执行器差异 =====

    protected abstract List<String> getControlInputKeys();

    protected abstract Map<String, List<String>> getControlOutputTargets();

    protected abstract Map<String, List<String>> getSpeedOutputTargets();

    protected abstract Map<String, List<String>> getThrottleOutputTargets();

    protected abstract Map<String, List<String>> getBrakeOutputTargets();

    protected abstract Map<String, List<String>> getHandbrakeOutputTargets();

    /** 返回此类车控使用的生命周期与手刹操作音配置。 */
    protected abstract HandBrakeSoundAttr getHandBrakeSounds();

    public abstract boolean isAutoHandBrakeEnabled();

    public abstract boolean isManualGearShift();

    /**
     * 无玩家偏好时是否使用分离油门/刹车。默认沿用意图模式。
     */
    public boolean isSeparateThrottleBrake() {
        return false;
    }

    /**
     * 转向输入平滑系数。默认沿用轮式控制器的 0.1。
     */
    protected float getSteeringSmoothing() {
        return 0.1f;
    }

    /**
     * 油门/刹车输入平滑系数。默认沿用轮式控制器的 0.2。
     */
    protected float getBrakeSmoothing() {
        return 0.2f;
    }

    /**
     * 把当前控制量分发给本类的执行器。
     * 调用点在 {@link #onPrePhysicsTick()} 内，已经完成输入平滑、自动换挡与发动机/电机油门发送。
     */
    protected abstract void distributeDriveControl();

    /**
     * 处理发动机/电机/变速箱之外的同类执行器（车轮、履带等）。
     *
     * @param sender  已解析的执行器实例
     * @param channel 到达频道
     */
    protected void onActuatorDiscovered(ISignalSender sender, String channel) {
    }

    /**
     * 通用握手发送完成后的钩子，用于重建子类自己的执行器绑定。
     */
    protected void onHandShakeComplete() {
    }

    /**
     * 物理 tick 开始、输入平滑之前的钩子。轮式在这里计算漂移状态，摩托车在这里读取倾角。
     */
    protected void onGroundControlBegin() {
    }

    /**
     * 输入平滑之后的钩子。摩托车在这里施加平衡力矩。
     */
    protected void onGroundControlAfterInput() {
    }

    /** 意图模式的纵向动力请求；履带可把转向输入计入动力需求。物理线程调用。 */
    protected float resolveDriveIntent(float longitudinalInput) {
        return longitudinalInput;
    }

    /**
     * 意图模式的制动请求。默认与纵向输入一致；履带可把转向从制动请求中分离，
     * 避免 A/D 在倒车或制动时被当成油门/刹车。物理线程调用。
     */
    protected float resolveBrakeIntent(float longitudinalInput) {
        return longitudinalInput;
    }

    // ===== 生命周期 =====

    @Override
    public void onTick() {
        super.onTick();
        flushTelemetry();
        for (Map.Entry<String, List<String>> entry : getSpeedOutputTargets().entrySet()) {
            String signalChannel = entry.getKey();
            for (String targetName : entry.getValue()) {
                sendSignalToTarget(signalChannel, targetName, this.speed);
            }
        }
        updateMoveInputs();
        if (isActive()) {
            outputsReset = false;
            sendControlOutputs();
            for (Map.Entry<ISignalReceiver, Float> entry : overrideCountDown.entrySet()) {
                if (entry.getValue() > 0) {
                    entry.setValue(entry.getValue() - 0.05f);
                }
            }
        } else if (!outputsReset) {
            outputsReset = true;
            resetSignalOutputs();
        }
        if (getLevel().isClientSide()) tickHandBrakeSound();
    }

    /**
     * 客户端主线程：按各机车控计算出的有效手刹状态播放一次操作声。
     * 物理线程通过 volatile 的 actualHandBrake 发布制动量，阈值为 0.1。
     * 连续持有手刹不重复发声；FMOD 配置不可用时静音，未配置 FMOD 时使用 OGG。
     */
    private void tickHandBrakeSound() {
        long now = getLevel().getGameTime();
        boolean engaged = actualHandBrake >= 0.1f;
        boolean changed = now == lastHandBrakeSoundTick + 1 && engaged != lastHandBrakeSound;
        lastHandBrakeSoundTick = now;
        lastHandBrakeSound = engaged;
        if (!changed) return;

        HandBrakeSoundAttr sounds = getHandBrakeSounds();
        if (sounds.fmod().isPresent()) {
            var fmod = sounds.fmod().orElseThrow();
            tryPlayFmodSound(fmod.bank(), fmod.handbrake(), SoundSource.NEUTRAL, Map.of());
        } else {
            var position = getSubPart().getPosition();
            getLevel().playLocalSound(position.x, position.y, position.z,
                    engaged ? sounds.handBrakeOn() : sounds.handBrakeOff(), SoundSource.NEUTRAL, 1f, 1f, false);
        }
    }

    /** 主线程：把上一物理步算出的控制量按配置线路投递。 */
    private void sendControlOutputs() {
        for (Map.Entry<String, List<String>> entry : getThrottleOutputTargets().entrySet()) {
            String signalChannel = entry.getKey();
            for (String targetName : entry.getValue()) {
                sendSignalToTarget(signalChannel, targetName, actualThrottle * 0.01f);
            }
        }
        for (Map.Entry<String, List<String>> entry : getBrakeOutputTargets().entrySet()) {
            String signalChannel = entry.getKey();
            for (String targetName : entry.getValue()) {
                sendSignalToTarget(signalChannel, targetName, actualBrake);
            }
        }
        for (Map.Entry<String, List<String>> entry : getHandbrakeOutputTargets().entrySet()) {
            String signalChannel = entry.getKey();
            for (String targetName : entry.getValue()) {
                sendSignalToTarget(signalChannel, targetName, actualHandBrake);
            }
        }
    }

    @Override
    public void onPrePhysicsTick() {
        super.onPrePhysicsTick();
        this.speed = -getOwner().getSubPart().getLinearVelocityLocal().z;
        onGroundControlBegin();

        if (isActive()) {
            if (this.moveInput != null) {
                float smoothing = Math.clamp(getSteeringSmoothing(), 0f, 1f);
                actualSteering = actualSteering * (1f - smoothing) + (moveInput[4] / 100f) * smoothing;
            }
            updateRegularInputs();
            actualHandBrake = actualHandBrake * 0.9f + (handBrake ? 1 : 0) * 0.1f;
            onGroundControlAfterInput();
            distributeControlSignals();
        }
        sampleTelemetry();
    }

    @Override
    public void onAttach() {
        super.onAttach();
        handShake();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        stopTelemetry();
    }

    // ===== 行车记录仪 =====

    /**
     * 主线程：开始把本控制器的每一个物理步写成 CSV，落在 {@code <gamedir>/logs/machine_max/}。
     *
     * <p>只在服务端开录：客户端调用直接返回 false，因此客户端既不建文件也不做逐 tick 采样。</p>
     *
     * @return 是否新开了一份录制；已在录制或当前是客户端时为 false
     */
    public boolean startTelemetry() {
        if (telemetry != null || getLevel().isClientSide()) {
            return false;
        }
        IPartAssembly assembly = getOwner().getSubPart().getPart().assembly;
        String subject = (assembly == null ? "vehicle" : assembly.getAssemblyName()) + "-" + getName();
        try {
            DriveTelemetry recorder = DriveTelemetry.open(subject, telemetryHeader());
            telemetryPreviousSpeed = Float.NaN;
            telemetry = recorder;
            MachineMax.LOGGER.info("行车记录仪开始: {}", recorder.path());
            return true;
        } catch (IOException e) {
            MachineMax.LOGGER.error("行车记录仪创建失败: {}", e.toString());
            return false;
        }
    }

    /**
     * 主线程：结束录制，写完剩余行并关闭文件。
     *
     * @return 本次录制的文件与行数摘要；未在录制时返回 null
     */
    @Nullable
    public String stopTelemetry() {
        DriveTelemetry recorder = telemetry;
        if (recorder == null) {
            return null;
        }
        telemetry = null;
        recorder.close();
        String summary = recorder.path() + "（" + recorder.samples() + " 行）";
        MachineMax.LOGGER.info("行车记录仪结束: {}", summary);
        return summary;
    }

    public boolean isTelemetryActive() {
        return telemetry != null;
    }

    /** 主线程：排空物理线程攒下的行。未录制时只做一次空指针判断。 */
    private void flushTelemetry() {
        DriveTelemetry recorder = telemetry;
        if (recorder != null) {
            recorder.flush();
        }
    }

    /**
     * 物理线程：把本步的控制量与反馈量合成一行。
     *
     * <p>采样点在 {@code distributeControlSignals()} 之后，因此 {@code throttle}、挡位与履带指令
     * 都是本步实际下发的那一份；{@code rpm} 是引擎上一物理步算出的转速，滞后一步。
     * {@code accel} 由相邻两步的车速差按名义步长 {@code 1/tps} 折算，与 {@code wall_ms} 一起
     * 可以区分"仿真里的加速度"和"真实时间的加速度"（物理掉步时两者不同）。</p>
     */
    private void sampleTelemetry() {
        DriveTelemetry recorder = telemetry;
        if (recorder == null) {
            return;
        }
        float tps = getPhysicsLevel().getTps();
        float dt = tps > 0f ? 1f / tps : 0.05f;
        float accel = Float.isNaN(telemetryPreviousSpeed) ? 0f : (speed - telemetryPreviousSpeed) / dt;
        telemetryPreviousSpeed = speed;

        StringBuilder row = new StringBuilder(192);
        row.append(String.format(Locale.ROOT, "%.3f,%d,%.4f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.1f,%d,%d,%d,%.0f",
                physicsTickCount * dt, recorder.elapsedMillis(), speed, speed * 3.6f, accel,
                actualThrottle, actualBrake, actualHandBrake, actualSteering,
                averagePowerRpm(), firstGear(), firstClutched(), isActive() ? 1 : 0, totalMass()));
        appendMotionSample(row);
        appendTelemetrySample(row);
        recorder.offer(row.toString());
    }

    /**
     * 物理线程：追加姿态与合力取样列。
     *
     * <p>读的是刚体上的实时量，不是 {@code SynchedEntityData} 里 20 Hz 的快照：{@code speed_mps}
     * 来自同步值，每 5 个物理步才更新一次，用它分辨不出周期一秒量级的抖动；{@code b_vx/b_vy/b_vz}
     * 是同一步的实时值。</p>
     *
     * <p>气动与张力取的是装配体上一步的合计（见 {@code VehicleCore#prePhysicsTick()}），
     * 比其它列晚一个物理步；{@code aero_fwd} 按本零件机身前向投影，正值表示气动力推着车往前。</p>
     */
    private void appendMotionSample(StringBuilder row) {
        PhysicsRigidBody body = getOwner().getSubPart().body;
        body.getPhysicsRotation(telemetryRotation);
        body.getLinearVelocity(telemetryVelocity);
        MyQuaternion.rotateInverse(telemetryRotation, telemetryVelocity, telemetryVelocity);
        body.getAngularVelocityLocal(telemetryAngular);

        telemetryAxis.set(0f, 0f, -1f);
        MyQuaternion.rotate(telemetryRotation, telemetryAxis, telemetryForward);
        telemetryAxis.set(0f, 1f, 0f);
        MyQuaternion.rotate(telemetryRotation, telemetryAxis, telemetryUp);
        telemetryAxis.set(1f, 0f, 0f);
        MyQuaternion.rotate(telemetryRotation, telemetryAxis, telemetryRight);

        float pitch = (float) Math.toDegrees(Math.asin(Math.clamp(telemetryForward.y, -1f, 1f)));
        float roll = (float) -Math.toDegrees(Math.asin(Math.clamp(telemetryRight.y, -1f, 1f)));
        float pitchRate = (float) Math.toDegrees(telemetryAngular.x);
        float rollRate = (float) Math.toDegrees(telemetryAngular.z);

        float aeroForward = 0f;
        float aeroUp = 0f;
        float trackTension = 0f;
        IPartAssembly assembly = getOwner().getSubPart().getPart().assembly;
        if (assembly instanceof VehicleCore vehicle) {
            Vector3f aero = vehicle.getAeroForceLastStep();
            aeroForward = aero.dot(telemetryForward);
            aeroUp = aero.dot(telemetryUp);
            trackTension = vehicle.getTrackTensionLastStep();
        }

        row.append(String.format(Locale.ROOT, ",%.3f,%.3f,%.3f,%.2f,%.2f,%.1f,%.1f,%.1f,%.1f,%.1f",
                telemetryVelocity.x, telemetryVelocity.y, telemetryVelocity.z,
                pitch, roll, pitchRate, rollRate, aeroForward, aeroUp, trackTension));
    }

    /**
     * 采样行的列名。子类追加的列必须与 {@link #appendTelemetrySample} 追加的数值一一对应，
     * 且录制期间列数保持不变（起录后再改组拓扑会让后续行错位）。
     */
    protected String telemetryHeader() {
        return "t_s,wall_ms,speed_mps,speed_kmh,accel_mps2,throttle,brake,handbrake,steering,"
                + "rpm,gear,clutched,active,mass_kg"
                + ",b_vx,b_vy,b_vz,pitch,roll,pitch_rate,roll_rate,aero_fwd,aero_up,tension_n"
                + telemetryExtraColumns();
    }

    /** 子类追加的列名，含前导逗号；默认无额外列。 */
    protected String telemetryExtraColumns() {
        return "";
    }

    /** 子类追加的数值，含前导逗号；默认无额外数值。 */
    protected void appendTelemetrySample(StringBuilder row) {
    }

    /** 引擎与电动机的平均转速（rpm）；都没有时为 0。物理线程调用。 */
    private float averagePowerRpm() {
        float rpm = 0f;
        int count = 0;
        for (EngineSubsystem engine : engines.keySet()) {
            rpm += (float) (engine.getRotSpeed() * 30.0 / Math.PI);
            count++;
        }
        for (MotorSubsystem motor : motors.keySet()) {
            rpm += (float) (motor.getRotSpeed() * 30.0 / Math.PI);
            count++;
        }
        return count > 0 ? rpm / count : 0f;
    }

    /** 首个变速箱的挡位索引；没有变速箱时为 -1。多变速箱的车按遍历顺序取第一个。 */
    private int firstGear() {
        for (GearboxSubsystem gearbox : gearboxes.keySet()) {
            return gearbox.getCurrentGear();
        }
        return -1;
    }

    /** 首个变速箱的离合状态，1 = 接合；没有变速箱时为 -1。 */
    private int firstClutched() {
        for (GearboxSubsystem gearbox : gearboxes.keySet()) {
            return gearbox.isClutched() ? 1 : 0;
        }
        return -1;
    }

    /** 装配体总质量（kg），供把轮端扭矩折算成加速度；没有装配体时为 0。物理线程调用。 */
    private float totalMass() {
        IPartAssembly assembly = getOwner().getSubPart().getPart().assembly;
        return assembly != null ? assembly.getTotalMass() : 0f;
    }

    @Override
    public void onVehicleStructureChanged() {
        super.onVehicleStructureChanged();
        handShake();
    }

    // ===== 发现与绑定 =====

    /**
     * 从 {@code control_outputs} 声明的频道解析执行器：按到达频道绑定发动机、电机与变速箱，
     * 其余同类终端交给 {@link #onActuatorDiscovered}。绑定表以不可变快照整体发布给物理线程。
     * <p><b>调用线程：</b>主线程。
     */
    protected void handShake() {
        clearActuatorBindings();
        Map<EngineSubsystem, String> foundEngines = new LinkedHashMap<>();
        Map<MotorSubsystem, String> foundMotors = new LinkedHashMap<>();
        Map<GearboxSubsystem, String> foundGearboxes = new LinkedHashMap<>();
        for (String signalChannel : getControlOutputTargets().keySet()) {
            for (Map.Entry<String, List<ISignalReceiver>> arrival : resolveTargets(signalChannel).entrySet()) {
                for (ISignalReceiver terminal : arrival.getValue()) {
                    if (!(terminal instanceof AbstractSubsystem actuator) || !belongsToSameAssembly(actuator)) continue;
                    if (actuator instanceof EngineSubsystem engine) {
                        foundEngines.putIfAbsent(engine, arrival.getKey());
                    } else if (actuator instanceof MotorSubsystem motor) {
                        foundMotors.putIfAbsent(motor, arrival.getKey());
                    } else if (actuator instanceof GearboxSubsystem gearbox) {
                        foundGearboxes.putIfAbsent(gearbox, arrival.getKey());
                    } else if (terminal instanceof ISignalSender sender) {
                        onActuatorDiscovered(sender, arrival.getKey());
                    }
                }
            }
        }
        this.engines = Collections.unmodifiableMap(foundEngines);
        this.motors = Collections.unmodifiableMap(foundMotors);
        this.gearboxes = Collections.unmodifiableMap(foundGearboxes);
        for (GearboxSubsystem gearbox : gearboxes.keySet()) {
            overrideCountDown.put(gearbox, 0f);
        }
        recalculateEngineAverages();
        recalculateMotorAverages();
        onHandShakeComplete();
    }

    /**
     * 发现前清空旧绑定。子类覆盖时必须调用 {@code super}。
     */
    protected void clearActuatorBindings() {
        this.engines = Map.of();
        this.motors = Map.of();
        this.gearboxes = Map.of();
        overrideCountDown.clear();
    }

    private boolean belongsToSameAssembly(AbstractSubsystem subsystem) {
        return subsystem.getOwner().getSubPart().getPart().assembly
                == this.getOwner().getSubPart().getPart().assembly;
    }

    private void recalculateEngineAverages() {
        avgEngineMinSpeed = 0;
        avgEngineMaxTorqueSpeed = 0;
        avgEngineMaxSpeed = 0;
        for (Map.Entry<EngineSubsystem, String> entry : engines.entrySet()) {
            avgEngineMinSpeed += entry.getKey().attr.staticAttribute.idleRpm;
            avgEngineMaxTorqueSpeed += entry.getKey().attr.staticAttribute.maxTorqueRpm;
            avgEngineMaxSpeed += entry.getKey().attr.staticAttribute.redLineRpm;
        }
        engineCount = engines.size();
        if (engineCount > 0) {
            avgEngineMinSpeed = (float) (avgEngineMinSpeed * Math.PI / engineCount / 30f);
            avgEngineMaxTorqueSpeed = (float) (avgEngineMaxTorqueSpeed * Math.PI / engineCount / 30f);
            avgEngineMaxSpeed = (float) (avgEngineMaxSpeed * Math.PI / engineCount / 30f);
        } else {
            avgEngineMinSpeed = 0f;
            avgEngineMaxTorqueSpeed = 0f;
            avgEngineMaxSpeed = 0f;
        }
    }

    private void recalculateMotorAverages() {
        avgEngineMinSpeed = 0;
        avgEngineMaxTorqueSpeed = 0;
        avgEngineMaxSpeed = 0;
        for (Map.Entry<MotorSubsystem, String> entry : motors.entrySet()) {
            float maxTorqueMinRpm = (float) (entry.getKey().attr.staticAttribute.maxPower
                    / entry.getKey().attr.staticAttribute.maxTorque * 30f / Math.PI);
            avgEngineMaxTorqueSpeed += 0.9f * maxTorqueMinRpm + 0.1f * entry.getKey().attr.staticAttribute.redLineRpm;
            avgEngineMaxSpeed += entry.getKey().attr.staticAttribute.redLineRpm;
        }
        motorCount = motors.size();
        if (motorCount > 0) {
            avgEngineMaxTorqueSpeed = (float) (avgEngineMaxTorqueSpeed * Math.PI / motorCount / 30f);
            avgEngineMaxSpeed = (float) (avgEngineMaxSpeed * Math.PI / motorCount / 30f);
        } else {
            avgEngineMaxTorqueSpeed = 0f;
            avgEngineMaxSpeed = 0f;
        }
    }

    // ===== 输入 =====

    protected void updateMoveInputs() {
        byte[] moveInput = null;
        byte[] moveInputConflict = null;
        boolean hasMoveInput = false;
        for (String inputKey : getControlInputKeys()) {
            SignalChannel signalChannel = getSignalChannel(inputKey);
            for (Map.Entry<ISignalSender, Object> entry : signalChannel.entrySet()) {
                ISignalSender sender = entry.getKey();
                Object signal = entry.getValue();
                if (signal instanceof MoveInputSignal moveInputSignal) {
                    moveInput = moveInputSignal.getMoveInput();
                    moveInputConflict = moveInputSignal.getMoveInputConflict();
                    hasMoveInput = true;
                    if (sender instanceof SeatSubsystem seat) {
                        this.controller = seat.passenger;
                    }
                    break;
                }
            }
            if (hasMoveInput) break;
        }
        if (hasMoveInput) {
            this.moveInput = moveInput;
            this.moveInputConflict = moveInputConflict;
        } else {
            this.moveInput = null;
            this.moveInputConflict = null;
            this.controller = null;
        }
    }

    /**
     * 按 controlInputKeys 优先级解析常规输入信号（离合/手刹/换挡）。
     */
    protected void updateRegularInputs() {
        RegularInputSignal activeRegular = null;
        SignalChannel activeChannel = null;
        ISignalSender activeSender = null;
        for (String inputKey : getControlInputKeys()) {
            SignalChannel channel = getSignalChannel(inputKey);
            for (Map.Entry<ISignalSender, Object> entry : channel.entrySet()) {
                if (entry.getValue() instanceof RegularInputSignal ris) {
                    activeRegular = ris;
                    activeChannel = channel;
                    activeSender = entry.getKey();
                    break;
                }
            }
            if (activeRegular != null) break;
        }

        if (activeRegular != null) {
            int tickCount = activeRegular.getInputTickCount();
            switch (activeRegular.getInputType()) {
                case CLUTCH:
                    for (ISignalReceiver gearbox : gearboxes.keySet()) {
                        overrideCountDown.put(gearbox, 1f);
                    }
                    for (GearboxSubsystem gearbox : gearboxes.keySet()) {
                        gearbox.setClutched(tickCount != 0);
                    }
                    break;
                case UP_SHIFT:
                    if (tickCount == 0) {
                        for (ISignalReceiver gearbox : gearboxes.keySet()) {
                            overrideCountDown.put(gearbox, 3f);
                        }
                        for (GearboxSubsystem gearbox : gearboxes.keySet()) {
                            gearbox.upShift();
                        }
                    }
                    activeChannel.put(activeSender, EmptySignal.INSTANCE);
                    break;
                case DOWN_SHIFT:
                    if (tickCount == 0) {
                        for (ISignalReceiver gearbox : gearboxes.keySet()) {
                            overrideCountDown.put(gearbox, 3f);
                        }
                        for (GearboxSubsystem gearbox : gearboxes.keySet()) {
                            gearbox.downShift();
                        }
                    }
                    activeChannel.put(activeSender, EmptySignal.INSTANCE);
                    break;
                case HAND_BRAKE:
                    handBrake = tickCount == 0;
                    overrideCountDown.put(this, tickCount == 0 ? 100f : 0f);
                    break;
                case TOGGLE_HAND_BRAKE:
                    if (tickCount == 0) {
                        handBrake = !handBrake;
                        overrideCountDown.put(this, 1f);
                    }
                    activeChannel.put(activeSender, EmptySignal.INSTANCE);
                    break;
                default:
                    break;
            }
        }
    }

    // ===== 控制量分配 =====

    protected void distributeControlSignals() {
        if (this.moveInput != null && moveInputConflict != null) {
            byte[] moveInput = this.moveInput;
            byte[] moveInputConflict = this.moveInputConflict;

            if (ControlPreference.shouldRawThrottleBrake(this)) {
                float targetThrottle = moveInput[2] / 100f;
                float targetBrake = moveInputConflict[2] / 100f;

                actualThrottle = actualThrottle * 0.2f + targetThrottle * 0.8f;
                float brakeSmoothing = Math.clamp(getBrakeSmoothing(), 0f, 1f);
                actualBrake = actualBrake * (1f - brakeSmoothing) + targetBrake * brakeSmoothing;

                if (targetThrottle < 0.01f && targetBrake < 0.01f) {
                    if (Math.abs(speed) < 1f && ControlPreference.shouldAutoHandBrake(this)) {
                        handBrake = true;
                    }
                } else if (targetThrottle > 0.05f) {
                    if (ControlPreference.shouldAutoHandBrake(this) && handBrake) {
                        handBrake = false;
                    }
                }

                float avgEngineSpeed = calculateAvgSpeedAndControl();
                byte direction = inferDirectionForAutoShift(targetThrottle);
                autoShiftAll(avgEngineSpeed, direction);
                distributeDriveControl();
            } else {
                float throttleInput = resolveDriveIntent(moveInput[2] / 100f);
                float brakeInput = resolveBrakeIntent(moveInputConflict[2] / 100f);

                float targetThrottle;
                float targetBrake;
                byte direction;

                if (Math.abs(speed) < 0.5f) {
                    targetThrottle = throttleInput;
                    targetBrake = 0;
                    direction = throttleInput > 0.01f ? (byte) 1 : (throttleInput < -0.01f ? (byte) -1 : 0);
                } else {
                    boolean forward = speed > 0;
                    targetThrottle = throttleInput;
                    targetBrake = forward ? Math.max(-brakeInput, 0f) : Math.max(brakeInput, 0f);
                    direction = targetThrottle > 0.01f
                            ? (byte) (forward ? 1 : -1)
                            : (byte) (speed >= 0 ? 1 : -1);
                    if (targetThrottle * direction < 0) {
                        targetThrottle = 0;
                    }
                }

                actualThrottle = actualThrottle * 0.2f + targetThrottle * 0.8f;
                float brakeSmoothing = Math.clamp(getBrakeSmoothing(), 0f, 1f);
                actualBrake = actualBrake * (1f - brakeSmoothing) + targetBrake * brakeSmoothing;
                float avgEngineSpeed = calculateAvgSpeedAndControl();

                for (GearboxSubsystem gearbox : gearboxes.keySet()) {
                    if (overrideCountDown.getOrDefault(gearbox, 0f) <= 0) {
                        gearbox.switchGear(autoGearShift(gearbox, avgEngineSpeed, direction));
                        if (Math.abs(speed) <= 1f && Math.abs(targetThrottle) > 0.01f) {
                            gearbox.setClutched(true);
                        }
                    }
                }

                distributeDriveControl();

                if (Math.abs(targetThrottle) > 0.05f && ControlPreference.shouldAutoHandBrake(this) && handBrake
                        && overrideCountDown.getOrDefault(this, 0f) <= 0) {
                    handBrake = false;
                    overrideCountDown.put(this, 2f);
                }
                if (Math.abs(targetThrottle) < 0.01f && Math.abs(targetBrake) < 0.01f && Math.abs(speed) < 1f
                        && ControlPreference.shouldAutoHandBrake(this) && !handBrake
                        && overrideCountDown.getOrDefault(this, 0f) <= 0) {
                    handBrake = true;
                    overrideCountDown.put(this, 0.5f);
                }
            }
        } else {
            for (Map.Entry<EngineSubsystem, String> entry : engines.entrySet()) {
                sendSignalToTarget(entry.getValue(), entry.getKey(), EmptySignal.INSTANCE);
            }
            actualBrake = actualBrake * 0.8f;
            if (ControlPreference.shouldAutoHandBrake(this) && !handBrake) {
                handBrake = true;
                actualHandBrake = 1f;
                overrideCountDown.put(this, 0.5f);
            }
            distributeDriveControl();
        }
    }

    private void autoShiftAll(float avgEngineSpeed, byte direction) {
        for (GearboxSubsystem gearbox : gearboxes.keySet()) {
            if (overrideCountDown.getOrDefault(gearbox, 0f) <= 0) {
                gearbox.switchGear(autoGearShift(gearbox, avgEngineSpeed, direction));
            }
        }
    }

    /**
     * 计算引擎/电动机平均转速，并向每个引擎/电动机单独发送油门控制信号。
     */
    protected float calculateAvgSpeedAndControl() {
        float avgEngineSpeed = 0f;
        for (Map.Entry<EngineSubsystem, String> entry : engines.entrySet()) {
            sendSignalToTarget(entry.getValue(), entry.getKey(), Math.abs(actualThrottle));
            avgEngineSpeed += (float) entry.getKey().getRotSpeed();
        }
        for (Map.Entry<MotorSubsystem, String> entry : motors.entrySet()) {
            sendSignalToTarget(entry.getValue(), entry.getKey(), actualThrottle);
            avgEngineSpeed += entry.getKey().getRotSpeed();
        }
        if (engineCount > 1) {
            avgEngineSpeed /= engineCount;
        } else if (motorCount > 1) {
            avgEngineSpeed /= motorCount;
        }
        return avgEngineSpeed;
    }

    /**
     * 分离模式下推断自动换挡的方向参数。
     */
    private byte inferDirectionForAutoShift(float targetThrottle) {
        if (targetThrottle > 0.05f) {
            for (GearboxSubsystem gearbox : gearboxes.keySet()) {
                if (gearbox.getCurrentGear() < 0) {
                    return -1;
                }
            }
            return 1;
        }
        if (Math.abs(speed) < 0.5f) return 0;
        return (byte) (speed >= 0 ? 1 : -1);
    }

    /**
     * 基于最大输出轴扭矩的自动换挡。
     */
    protected int autoGearShift(GearboxSubsystem gearbox, float engineSpeed, byte direction) {
        int currentGear = gearbox.getCurrentGear();
        if (!ControlPreference.shouldAutoSwitchGear(this)) {
            return currentGear;
        }

        byte actualDirection = direction;
        if (actualDirection == 0 && Math.abs(speed) > 0.5f) {
            actualDirection = (byte) (speed >= 0 ? 1 : -1);
        }

        if (Math.abs(speed) < 0.5f) {
            if (actualDirection == 0) return currentGear;
            return actualDirection > 0 ? gearbox.minPositiveGear : gearbox.minNegativeGear;
        }

        double outputShaftSpeed = engineSpeed / gearbox.gearRatios[currentGear];

        int bestGear = currentGear;
        double bestOutputTorque = -1;
        double currentRatio = gearbox.gearRatios[currentGear];

        for (int i = 0; i < gearbox.gearRatios.length; i++) {
            double ratio = gearbox.gearRatios[i];

            boolean isPureElectric = engineCount == 0 && motorCount > 0;
            if (!isPureElectric && ratio * actualDirection <= 0) continue;

            double predictedSpeed = outputShaftSpeed * ratio;
            if (Math.abs(predictedSpeed) < 0.1) continue;

            double totalTorque = 0;
            for (EngineSubsystem engine : engines.keySet()) {
                totalTorque += engine.getTorqueAtSpeed(predictedSpeed)
                        - Math.abs(engine.getDampingTorque(predictedSpeed));
            }
            for (MotorSubsystem motor : motors.keySet()) {
                totalTorque += motor.getTorqueAtSpeed(predictedSpeed)
                        - Math.abs(motor.getDampingTorque(predictedSpeed));
            }

            double outputTorque = totalTorque * Math.abs(ratio);
            if (outputTorque > bestOutputTorque) {
                bestOutputTorque = outputTorque;
                bestGear = i;
            }
        }

        double currentOutputTorque = 0;
        double curPredictedSpeed = outputShaftSpeed * currentRatio;
        if (Math.abs(curPredictedSpeed) >= 0.1) {
            double curTotalTorque = 0;
            for (EngineSubsystem engine : engines.keySet()) {
                curTotalTorque += engine.getTorqueAtSpeed(curPredictedSpeed)
                        - Math.abs(engine.getDampingTorque(curPredictedSpeed));
            }
            for (MotorSubsystem motor : motors.keySet()) {
                curTotalTorque += motor.getTorqueAtSpeed(curPredictedSpeed)
                        - Math.abs(motor.getDampingTorque(curPredictedSpeed));
            }
            currentOutputTorque = curTotalTorque * Math.abs(currentRatio);
        }

        if (bestGear != currentGear && bestOutputTorque < currentOutputTorque * 1.05) {
            bestGear = currentGear;
        }

        if (bestGear > currentGear + 1) bestGear = currentGear + 1;
        if (bestGear < currentGear - 1) bestGear = currentGear - 1;

        if (bestGear != currentGear) {
            overrideCountDown.put(gearbox, Math.max(0.2f, gearbox.attr.staticAttribute.switchTime + 0.5f));
        }
        return bestGear;
    }

    @Override
    public List<String> getAcceptedChannels() {
        return getControlInputKeys();
    }

    @Override
    public Map<String, List<String>> getTargetNames() {
        Map<String, List<String>> result = new HashMap<>();
        mergeTargets(result, getControlOutputTargets());
        mergeTargets(result, getSpeedOutputTargets());
        mergeTargets(result, getThrottleOutputTargets());
        mergeTargets(result, getBrakeOutputTargets());
        mergeTargets(result, getHandbrakeOutputTargets());
        return result;
    }

    /** 同一频道跨输出类别时取有序并集，后一类不能覆盖前一类的目标名。 */
    private static void mergeTargets(Map<String, List<String>> into, Map<String, List<String>> from) {
        from.forEach((channel, targets) -> into.merge(channel, targets, (first, second) -> {
            Set<String> union = new LinkedHashSet<>(first.size() + second.size());
            union.addAll(first);
            union.addAll(second);
            return List.copyOf(union);
        }));
    }
}
