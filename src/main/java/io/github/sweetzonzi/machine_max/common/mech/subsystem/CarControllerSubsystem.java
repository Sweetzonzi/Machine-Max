package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.bullet.joints.New6Dof;
import com.jme3.bullet.joints.motors.MotorParam;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.attachment.ControlPreference;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.signal.*;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.CarControllerSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AdvancedConnector;
import io.github.sweetzonzi.machine_max.util.MMMath;
import io.github.sweetzonzi.machine_max.util.control.PIDController;
import lombok.Getter;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Getter
public class CarControllerSubsystem extends BasicSubsystem {
    public final CarControllerSubsystemAttr attr;
    public LivingEntity controller;
    public byte[] moveInput;
    public byte[] moveInputConflict;
    Vector3f[] vs = new Vector3f[6];
    public float speed = 0.0f;
    public float avgSlipRatio = 0.0f;
    private final ConcurrentMap<ISignalReceiver, Float> overrideCountDown = new ConcurrentHashMap<>();

    float avgEngineMaxSpeed = 0f;
    float avgEngineMinSpeed = 0f;
    float avgEngineMaxTorqueSpeed = 0f;
    int engineCount = 0;
    int motorCount = 0;

    private final Map<EngineSubsystem, String> engines = new HashMap<>();//控制的发动机其接收控制的信号频道映射 Control engine and its receiving signal channel mapping
    private final Map<MotorSubsystem, String> motors = new HashMap<>();//控制的电动机其接收控制的信号频道映射 Control engine and its receiving signal channel mapping
    private final Map<GearboxSubsystem, String> gearboxes = new HashMap<>();//控制的变速箱其接收控制的信号频道映射 Control gearbox and its receiving signal channel mapping
    private final Map<WheelDriverSubsystem, String> wheels = new HashMap<>();//控制的车轮其接收控制的信号频道映射 Control wheel and its receiving signal channel mapping

    public boolean handBrake = false;
    public boolean isDrifting = false;
    public float actualThrottle = 0f;
    public float actualBrake = 0f;
    public float actualHandBrake = 0f;
    public float actualSteering = 0f;
    /**
     * 手刹状态，仅用于客户端音效
     */
    private boolean handBrakeEnabled = false;

    private final PIDController driftingPD;
    /**
     * 漂移角，弧度制
     */
    public float driftRad = 0.0f;
    public boolean drifting = false;
    private final float DRIFT_START_RAD = SparkMathKt.toRadians(5.0f);
    private final float DRIFT_END_RAD = SparkMathKt.toRadians(3.0f);
    /**
     * 漂移控制与一般控制的融合权重，0为纯原生控制，1为漂移接管
     */
    protected float driftWeight = 0;
    /**
     * 漂移转向输入，弧度制
     */
    private float driftControl = 0;

    /** 根据已装配车轮几何位置自动计算的转向中心，用于阿克曼转向和漂移判断 */
    protected volatile Vec3 computedSteeringCenter = Vec3.ZERO;

    public CarControllerSubsystem(ISubsystemHost owner, String name, CarControllerSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
        this.driftingPD = new PIDController(1.5, 0.01, 0.1, 1.0 / getPhysicsLevel().getTps(), -1, 1);
    }

    @Override
    public void onTick() {
        super.onTick();
        for (Map.Entry<String, List<String>> entry : attr.speedOutputTargets.entrySet()) {
            String signalChannel = entry.getKey();
            List<String> targets = entry.getValue();
            for (String targetName : targets)
                sendSignalToTarget(signalChannel, targetName, this.speed);
        }
        updateMoveInputs();
        if (isActive()) {
            //自动换档与手刹车冷却时间 Auto gear shift cooldown
            for (Map.Entry<ISignalReceiver, Float> entry : overrideCountDown.entrySet()) {
                if (entry.getValue() > 0) {
                    entry.setValue(entry.getValue() - 0.05f);
                }
            }
        }
        // 手刹车音效播放
        var pos = getSubPart().getPosition();
        if (handBrakeEnabled) {
            if (actualHandBrake < 0.1f) {
                handBrakeEnabled = false;
                if (getLevel().isClientSide())
                    getLevel().playLocalSound(
                            pos.x, pos.y, pos.z,
                            getAttr().getStaticAttribute().getHandBrakeOffSound(),
                            SoundSource.NEUTRAL,
                            1.0f,
                            1.0f,
                            false
                    );
            }
        } else if (actualHandBrake >= 0.1f) {
            handBrakeEnabled = true;
            if (getLevel().isClientSide())
                getLevel().playLocalSound(
                        pos.x, pos.y, pos.z,
                        getAttr().getStaticAttribute().getHandBrakeOnSound(),
                        SoundSource.NEUTRAL,
                        1.0f,
                        1.0f,
                        false
                );
        }
    }

    @Override
    public void onPrePhysicsTick() {
        super.onPrePhysicsTick();
        // 获取基础物理数据
        Vector3f localVel = getOwner().getSubPart().getLinearVelocityLocal();
        this.speed = -localVel.z; // 前进速度
        this.driftRad = (float) Math.atan2(localVel.x, Math.abs(localVel.z) + 0.1f);
        float bodyYawRate = getOwner().getSubPart().body.getAngularVelocityLocal(null).y; // 当前角速度
        // 漂移状态判断
        if (drifting && Math.abs(driftRad) < DRIFT_END_RAD) {
            drifting = false;
        } else if (!drifting && Math.abs(driftRad) > DRIFT_START_RAD && speed > 0) {
            drifting = true;
        }
        if (!ControlPreference.shouldDriftAssist(this)) driftWeight = 0.0f;
        else if (actualHandBrake > 0.5f && localVel.lengthSquared() > 4) driftWeight = 1.0f; // 强制手刹起漂时漂移控制权重为 1
        else if (drifting && localVel.lengthSquared() > 4) driftWeight = 1.0f;
        else driftWeight = 0.0f;
        if (isActive() && getOwner().getSubPart().getPart().vehicle.mode == VehicleCore.ControlMode.GROUND) {
            //更新受灵敏度影响的实际控制量，油门与刹车控制在分发控制信号时进行
            if (this.moveInput != null) {
                actualSteering = actualSteering * 0.9f + (moveInput[4] / 100f) * 0.1f;
                // 使用最大漂移角速度作为PD控制器的目标值（°/s → rad/s）
                float maxDriftAngularVelocity = attr.staticAttribute.getMaxDriftAngularVelocityAtSpeed(Math.abs(speed)) * (float) Math.PI / 180f;
                if (driftWeight > 1e-4)
                    driftControl = (float) driftingPD.step(actualSteering * maxDriftAngularVelocity, bodyYawRate);
                else {
                    driftControl = 0.0f;
                    driftingPD.resetError();
                }
            }
            actualHandBrake = actualHandBrake * 0.9f + (handBrake ? 1 : 0) * 0.1f;
            distributeControlSignals();
        } else resetSignalOutputs();
    }

    @Override
    public void onPostPhysicsTick() {
        super.onPostPhysicsTick();
        for (Map.Entry<String, List<String>> entry : attr.throttleOutputTargets.entrySet()) {
            String signalChannel = entry.getKey();
            List<String> targets = entry.getValue();
            for (String targetName : targets)
                sendSignalToTarget(signalChannel, targetName, actualThrottle * 0.01f);
        }
        for (Map.Entry<String, List<String>> entry : attr.steeringOutputTargets.entrySet()) {
            String signalChannel = entry.getKey();
            List<String> targets = entry.getValue();
            var steering = actualSteering;
            for (String targetName : targets)
                sendSignalToTarget(signalChannel, targetName, steering);
        }
        for (Map.Entry<String, List<String>> entry : attr.brakeOutputTargets.entrySet()) {
            String signalChannel = entry.getKey();
            List<String> targets = entry.getValue();
            for (String targetName : targets)
                sendSignalToTarget(signalChannel, targetName, actualBrake);
        }
        for (Map.Entry<String, List<String>> entry : attr.handbrakeOutputTargets.entrySet()) {
            String signalChannel = entry.getKey();
            List<String> targets = entry.getValue();
            for (String targetName : targets)
                sendSignalToTarget(signalChannel, targetName, actualHandBrake);
        }
    }

    @Override
    public void onAttach() {
        super.onAttach();
        handShake();
    }

    @Override
    public void onVehicleStructureChanged() {
        super.onVehicleStructureChanged();
        handShake();
    }

    /**
     * 分离模式下推断自动换挡的方向参数。
     * 踩油门时检查当前档位：若已有变速箱处于负挡（玩家手动挂R挡），
     * 则返回 -1 避免 autoGearShift 的方向过滤器把负挡排除掉。
     * 松油门时使用车速方向作为回退。
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
     * 子系统初始化或载具结构发生变化时，发送空信号，根据回调重新建立连接。<br>
     * 所有受控下属子系统（引擎/电动机/变速箱/车轮）统一通过 control_outputs 频道进行握手。
     *
     * @see CarControllerSubsystem#onSignalUpdated(String signalKey, ISignalSender sender)
     */
    protected void handShake() {
        for (String signalChannel : attr.controlOutputTargets.keySet()) {
            sendSignalToAllTargetsWithCallback(signalChannel, EmptySignal.INSTANCE, false);
        }
        recalculateSteeringCenter();
    }

    /**
     * 根据已装配车轮的关节枢轴位置自动计算转向中心。<br>
     * 遍历所有受控车轮驱动子系统，读取关节 Y 轴旋转限位以区分转向/非转向轮，<br>
     * 用非转向轮枢轴的 Z 均值作为转向参考轴（X 取左右对称中心），<br>
     * 若全为转向轮则回退到全部车轮的几何中心。
     */
    protected void recalculateSteeringCenter() {
        float sumNonSteerX = 0, sumNonSteerZ = 0;
        int nonSteerCount = 0;
        float sumAllX = 0, sumAllZ = 0;
        int allCount = 0;

        var carBody = getOwner().getSubPart().body;

        for (WheelDriverSubsystem wheel : getWheels().keySet()) {
            if (wheel.connector == null || wheel.connector.joint == null) continue;

            New6Dof joint = wheel.connector.joint;
            Vector3f pivotLocal = new Vector3f();
            if (wheel.connector.subPart.body == joint.getBodyA()) {
                joint.getPivotA(pivotLocal);
            } else {
                joint.getPivotB(pivotLocal);
            }
            // 统一转换到车控子系统的刚体局部坐标系，确保不同 SubPart 上的车轮在同一空间内对比
            Vector3f worldPivot = MMMath.relPointWorldPos(pivotLocal, wheel.connector.subPart.body);
            Vector3f carLocalPivot = MMMath.worldPointLocalPos(worldPivot, carBody);

            // 检查 Y 轴旋转自由度范围判断是否为转向轮（带 1e-4 容差）
            double lowerYr = joint.get(MotorParam.LowerLimit, 4);
            double upperYr = joint.get(MotorParam.UpperLimit, 4);
            boolean isSteering = Math.abs(upperYr - lowerYr) > 1e-4;

            if (!isSteering) {
                sumNonSteerX += carLocalPivot.x;
                sumNonSteerZ += carLocalPivot.z;
                nonSteerCount++;
            }
            sumAllX += carLocalPivot.x;
            sumAllZ += carLocalPivot.z;
            allCount++;
        }

        float avgX, avgZ;
        if (nonSteerCount > 0) {
            avgX = sumNonSteerX / nonSteerCount;
            avgZ = sumNonSteerZ / nonSteerCount;
        } else if (allCount > 0) {
            avgX = sumAllX / allCount;
            avgZ = sumAllZ / allCount;
        } else {
            avgX = 0;
            avgZ = 0;
        }

        this.computedSteeringCenter = new Vec3(avgX, 0, avgZ);
    }

    /**
     * 每当载具结构发生变化时，发送空信号，根据回调重新建立连接<p>
     * Every time the vehicle structure changes, send an empty signal, and reestablish connections based on callbacks.
     *
     * @see CarControllerSubsystem#onVehicleStructureChanged()
     */
    @Override
    public SignalResult onSignalUpdated(String channelName, ISignalSender sender) {
        Object signalValue = getSignalChannel(channelName).get(sender);
        if (channelName.equals("callback") && signalValue instanceof String controlChannel) {
            if (sender instanceof WheelDriverSubsystem wheel) {
                if (wheel.getOwner().getSubPart().getPart().vehicle != this.getOwner().getSubPart().getPart().vehicle)
                    wheels.remove(wheel);
                else {
                    wheels.put(wheel, controlChannel);
                    addCallbackTarget(controlChannel, wheel);
                }
            } else if (sender instanceof EngineSubsystem engine) {
                if (engine.getOwner().getSubPart().getPart().vehicle != this.getOwner().getSubPart().getPart().vehicle)
                    this.engines.remove(engine);
                else {
                    engines.put(engine, controlChannel);
                    addCallbackTarget(controlChannel, engine);
                    //计算引擎最大转速和最大扭矩转速的平均值 Calculate the average maximum speed and max torque speed of the motor
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
            } else if (sender instanceof MotorSubsystem motor) {
                if (motor.getOwner().getSubPart().getPart().vehicle != this.getOwner().getSubPart().getPart().vehicle)
                    this.motors.remove(motor);
                else {
                    motors.put(motor, controlChannel);
                    addCallbackTarget(controlChannel, motor);
                    //计算引擎最大转速和最大扭矩转速的平均值 Calculate the average maximum speed and max torque speed of the motor
                    avgEngineMinSpeed = 0;
                    avgEngineMaxTorqueSpeed = 0;
                    avgEngineMaxSpeed = 0;
                    for (Map.Entry<MotorSubsystem, String> entry : motors.entrySet()) {
                        float maxTorqueMinRpm = (float) (entry.getKey().attr.staticAttribute.maxPower / entry.getKey().attr.staticAttribute.maxTorque * 30f / Math.PI);
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
            } else if (sender instanceof GearboxSubsystem gearbox) {
                if (gearbox.getOwner().getSubPart().getPart().vehicle != this.getOwner().getSubPart().getPart().vehicle) {
                    this.gearboxes.remove(gearbox);
                    overrideCountDown.remove(gearbox);
                } else {
                    gearboxes.put(gearbox, controlChannel);
                    addCallbackTarget(controlChannel, gearbox);
                    overrideCountDown.put(gearbox, 0f);
                }
            }
        } else if (signalValue instanceof RegularInputSignal regularInputSignal) { //处理按键输入 Handle key input
            int tickCount = regularInputSignal.getInputTickCount();
            switch (regularInputSignal.getInputType()) {
                case CLUTCH:
                    for (ISignalReceiver gearbox : gearboxes.keySet()) {
                        overrideCountDown.put(gearbox, 1f);//手动操作后一段时间内不自动切换 Clutch for a period of time after manual operation
                    }
                    if (tickCount == 0) {//踩离合 Unclutch
                        for (GearboxSubsystem gearbox : gearboxes.keySet())
                            gearbox.setClutched(false);
                    } else {//松离合 Clutch
                        for (GearboxSubsystem gearbox : gearboxes.keySet())
                            gearbox.setClutched(true);
                    }
                    break;
                case UP_SHIFT://升档 Shift up
                    for (ISignalReceiver gearbox : gearboxes.keySet()) {
                        overrideCountDown.put(gearbox, 3f);//手动操作后一段时间内不自动切换 Clutch for a period of time after manual operation
                    }
                    for (GearboxSubsystem gearbox : gearboxes.keySet()) gearbox.upShift();
                    break;
                case DOWN_SHIFT://降档 Shift down
                    for (ISignalReceiver gearbox : gearboxes.keySet()) {
                        overrideCountDown.put(gearbox, 3f);//手动操作后一段时间内不自动切换 Clutch for a period of time after manual operation
                    }
                    for (GearboxSubsystem gearbox : gearboxes.keySet()) gearbox.downShift();
                    break;
                case HAND_BRAKE:
                    handBrake = tickCount == 0;
                    overrideCountDown.put(this, tickCount == 0 ? 100f : 0f);
                    break;
                case TOGGLE_HAND_BRAKE:
                    handBrake = !handBrake;
                    overrideCountDown.put(this, 1f);
                    break;
                default://忽视其他输入 Ignore other inputs
                    break;
            }
        }
        return SignalResult.PASS;
    }

    protected void updateMoveInputs() {
        byte[] moveInput = null;
        byte[] moveInputConflict = null;
        boolean hasMoveInput = false;
        for (String inputKey : attr.staticAttribute.controlInputKeys) {//遍历输入信号 Iterate over input signalChannel
            SignalChannel signalChannel = getSignalChannel(inputKey);
            for (Map.Entry<ISignalSender, Object> entry : signalChannel.entrySet()) {
                ISignalSender sender = entry.getKey();
                Object signal = entry.getValue();
                if (signal instanceof MoveInputSignal moveInputSignal) {//找到移动输入信号 Find move input signal
                    moveInput = moveInputSignal.getMoveInput();
                    moveInputConflict = moveInputSignal.getMoveInputConflict();
                    hasMoveInput = true;
                    if (sender instanceof SeatSubsystem seat)
                        this.controller = seat.passenger;
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

    protected void distributeControlSignals() {
        if (this.moveInput != null && moveInputConflict != null) {
            byte[] moveInput = this.moveInput;
            byte[] moveInputConflict = this.moveInputConflict;

            if (ControlPreference.shouldRawThrottleBrake(this)) {
                // 分离模式：input[2]=油门(0~100), inputConflict[2]=刹车(0~100)，各自独立
                float targetThrottle = moveInput[2] / 100f;
                float targetBrake = moveInputConflict[2] / 100f;

                actualThrottle = actualThrottle * 0.2f + targetThrottle * 0.8f;
                actualBrake = actualBrake * 0.2f + targetBrake * 0.8f;

                // 自动手刹逻辑
                if (targetThrottle < 0.01f && targetBrake < 0.01f) {
                    if (Math.abs(speed) < 1f && ControlPreference.shouldAutoHandBrake(this)) {
                        handBrake = true;
                    }
                } else if (targetThrottle > 0.05f) {
                    if (ControlPreference.shouldAutoHandBrake(this) && handBrake)
                        handBrake = false;
                }

                float avgEngineSpeed = calculateAvgSpeedAndControl();

                // 自动换挡方向推断：踩油门时检查是否有变速箱已在负挡
                byte direction = inferDirectionForAutoShift(targetThrottle);
                for (GearboxSubsystem gearbox : gearboxes.keySet()) {
                    if (overrideCountDown.getOrDefault(gearbox, 0f) <= 0) {
                        gearbox.switchGear(autoGearShift(gearbox, avgEngineSpeed, direction));
                    }
                }

                for (Map.Entry<WheelDriverSubsystem, String> entry : wheels.entrySet()) {
                    String channel = entry.getValue();
                    WheelDriverSubsystem wheel = entry.getKey();
                    if (wheel.connector.joint != null) {
                        float steeringInput = steering(actualSteering, wheel.connector);
                        float effectiveBrake = calculateEffectiveBrake(wheel, actualBrake);
                        sendCallbackToListener(channel, wheel, new WheelControlSignal(effectiveBrake, actualHandBrake, steeringInput));
                    }
                }

            } else {
                // 意图模式：input[2]=方向意图(-100~+100), conflict[2]=刹车道(-100~+100)
                // speed>0: 用 input>0 加油, conflict<0 刹车
                // speed<0: 用 input<0 加油, conflict>0 刹车
                // 极低速时始终按输入方向加速（S=倒车起步）
                float throttleInput = moveInput[2] / 100f;
                float brakeInput = moveInputConflict[2] / 100f;

                float targetThrottle;
                float targetBrake;
                byte direction;

                if (Math.abs(speed) < 0.5f) {
                    // 近乎静止：始终加速，方向由输入决定
                    targetThrottle = throttleInput;
                    targetBrake = 0;
                    direction = throttleInput > 0.01f ? (byte) 1 : (throttleInput < -0.01f ? (byte) -1 : 0);
                } else {
                    boolean forward = speed > 0;
                    targetThrottle = throttleInput;
                    targetBrake    = forward ? Math.max(-brakeInput, 0f)  : Math.max(brakeInput, 0f);
                    direction = targetThrottle > 0.01f ? (byte)(forward ? 1 : -1) : (byte)(speed >= 0 ? 1 : -1);
                    if(targetThrottle * direction < 0){
                        targetThrottle = 0; // 无效输入：重置油门
                    }
                }

                actualThrottle = actualThrottle * 0.2f + targetThrottle * 0.8f;
                actualBrake = actualBrake * 0.2f + targetBrake * 0.8f;
                float avgEngineSpeed = calculateAvgSpeedAndControl();

                for (GearboxSubsystem gearbox : gearboxes.keySet()) {
                    if (overrideCountDown.getOrDefault(gearbox, 0f) <= 0) {
                        gearbox.switchGear(autoGearShift(gearbox, avgEngineSpeed, direction));
                        if (Math.abs(speed) <= 1f && Math.abs(targetThrottle) > 0.01f) {
                            gearbox.setClutched(true);
                        }
                    }
                }

                for (Map.Entry<WheelDriverSubsystem, String> entry : wheels.entrySet()) {
                    String channel = entry.getValue();
                    WheelDriverSubsystem wheel = entry.getKey();
                    if (wheel.connector.joint != null) {
                        float steeringInput = steering(actualSteering, wheel.connector);
                        float effectiveBrake = calculateEffectiveBrake(wheel, actualBrake);
                        sendCallbackToListener(channel, wheel, new WheelControlSignal(effectiveBrake, actualHandBrake, steeringInput));
                    }
                }

                // 起动时松手刹
                if (Math.abs(targetThrottle) > 0.05f && ControlPreference.shouldAutoHandBrake(this) && handBrake
                        && overrideCountDown.getOrDefault(this, 0f) <= 0) {
                    handBrake = false;
                    overrideCountDown.put(this, 2f);
                }
                // 静止无输入时自动手刹
                if (Math.abs(targetThrottle) < 0.01f && Math.abs(targetBrake) < 0.01f && Math.abs(speed) < 1f
                        && ControlPreference.shouldAutoHandBrake(this) && !handBrake
                        && overrideCountDown.getOrDefault(this, 0f) <= 0) {
                    handBrake = true;
                    overrideCountDown.put(this, 0.5f);
                }
            }
        } else { //无输入信号 No input signal
            for (Map.Entry<EngineSubsystem, String> entry : engines.entrySet()) {
                sendCallbackToListener(entry.getValue(), entry.getKey(), EmptySignal.INSTANCE);
            }
            actualBrake = actualBrake * 0.8f + 0 * 0.2f;
            if (ControlPreference.shouldAutoHandBrake(this) && !handBrake) {
                handBrake = true;
                actualHandBrake = 1f;
                overrideCountDown.put(this, 0.5f);
            }
            for (Map.Entry<WheelDriverSubsystem, String> entry : wheels.entrySet()) {
                String channel = entry.getValue();
                WheelDriverSubsystem wheel = entry.getKey();
                if (wheel.connector.joint != null) {
                    float steeringInput = steering(actualSteering, wheel.connector);
                    sendCallbackToListener(channel, wheel, new WheelControlSignal(actualBrake, actualHandBrake, steeringInput));
                }
            }
        }
    }

    /**
     * 计算引擎/电动机平均转速，并向每个引擎/电动机单独发送油门控制信号。<br>
     * 使用 sendCallbackToListener 逐个发送（而非广播所有监听者），避免信号串扰。
     */
    private float calculateAvgSpeedAndControl() {
        float avgEngineSpeed = 0f;
        for (Map.Entry<EngineSubsystem, String> entry : engines.entrySet()) {
            sendCallbackToListener(entry.getValue(), entry.getKey(), Math.abs(actualThrottle));
            avgEngineSpeed += (float) entry.getKey().getRotSpeed();
        }
        for (Map.Entry<MotorSubsystem, String> entry : motors.entrySet()) {
            sendCallbackToListener(entry.getValue(), entry.getKey(), actualThrottle);
            avgEngineSpeed += entry.getKey().getRotSpeed();
        }
        if (engineCount > 1)
            avgEngineSpeed /= engineCount;
        else if (motorCount > 1)
            avgEngineSpeed /= motorCount;
        return avgEngineSpeed;
    }

    /**
     * 基于最大输出轴扭矩的自动换挡。<p>
     * 以变速箱输出轴转速为基准，遍历所有可用档位，预测引擎/电动机转速，
     * 查询扭矩曲线并乘以传动比得到输出轴扭矩，选择扭矩最大的档位。<p>
     * 这等价于在每个车速下选择使发动机功率最大化的档位（P = T_output * ω_out）。
     *
     * @param gearbox     变速箱对象
     * @param engineSpeed 当前引擎转速 (rad/s)
     * @param direction   期望运动方向，正向 > 0，反向 < 0
     * @return 最优档位
     */
    protected int autoGearShift(GearboxSubsystem gearbox, float engineSpeed, byte direction) {
        int currentGear = gearbox.getCurrentGear();
        if (!ControlPreference.shouldAutoSwitchGear(this))
            return currentGear;

        // 松油门（direction==0）时根据当前车速推断移动方向
        byte actualDirection = direction;
        if (actualDirection == 0 && Math.abs(speed) > 0.5f) {
            actualDirection = (byte) (speed >= 0 ? 1 : -1);
        }

        // 低速起步：直接挂最低档
        if (Math.abs(speed) < 0.5f) {
            if (actualDirection == 0) return currentGear; // 无输入维持当前档位
            return actualDirection > 0 ? gearbox.minPositiveGear : gearbox.minNegativeGear;
        }

        // 计算变速箱输出轴转速: ω_out = ω_engine / R_current
        double outputShaftSpeed = engineSpeed / gearbox.gearRatios[currentGear];

        int bestGear = currentGear;
        double bestOutputTorque = -1;
        double currentRatio = gearbox.gearRatios[currentGear];

        // 遍历所有可用档位，选择输出轴扭矩最大的档位
        for (int i = 0; i < gearbox.gearRatios.length; i++) {
            double ratio = gearbox.gearRatios[i];

            // 方向过滤：正方向只看正档，反方向只看负档
            // 纯电动车（无引擎）跳过方向过滤——电动机反转即可倒车，无需负挡
            boolean isPureElectric = engineCount == 0 && motorCount > 0;
            if (!isPureElectric && ratio * actualDirection <= 0) continue;

            // 预测挂入此档后的引擎转速
            double predictedSpeed = outputShaftSpeed * ratio;

            // 跳过过低转速（避免无效计算）
            if (Math.abs(predictedSpeed) < 0.1) continue;

            // 遍历所有引擎/电动机，累计净扭矩（扣除内阻后的可用扭矩，保证滑行时也能正常降档）
            double totalTorque = 0;
            for (EngineSubsystem engine : engines.keySet()) {
                totalTorque += engine.getTorqueAtSpeed(predictedSpeed) - Math.abs(engine.getDampingTorque(predictedSpeed));
            }
            for (MotorSubsystem motor : motors.keySet()) {
                totalTorque += motor.getTorqueAtSpeed(predictedSpeed) - Math.abs(motor.getDampingTorque(predictedSpeed));
            }

            // 输出轴扭矩 = 引擎扭矩 × 传动比（传动比放大扭矩）
            double outputTorque = totalTorque * Math.abs(ratio);

            if (outputTorque > bestOutputTorque) {
                bestOutputTorque = outputTorque;
                bestGear = i;
            }
        }

        // 计算当前档位的输出轴扭矩，用于滞回比较
        double currentOutputTorque = 0;
        double curPredictedSpeed = outputShaftSpeed * currentRatio;
        if (Math.abs(curPredictedSpeed) >= 0.1) {
            double curTotalTorque = 0;
            for (EngineSubsystem engine : engines.keySet()) {
                curTotalTorque += engine.getTorqueAtSpeed(curPredictedSpeed) - Math.abs(engine.getDampingTorque(curPredictedSpeed));
            }
            for (MotorSubsystem motor : motors.keySet()) {
                curTotalTorque += motor.getTorqueAtSpeed(curPredictedSpeed) - Math.abs(motor.getDampingTorque(curPredictedSpeed));
            }
            currentOutputTorque = curTotalTorque * Math.abs(currentRatio);
        }

        // 滞回：新档位扭矩优势小于 5% 则保持当前档位，避免频繁跳档
        if (bestGear != currentGear && bestOutputTorque < currentOutputTorque * 1.05) {
            bestGear = currentGear;
        }

        // 每次最多跳一档，防止换挡冲击
        if (bestGear > currentGear + 1) bestGear = currentGear + 1;
        if (bestGear < currentGear - 1) bestGear = currentGear - 1;

        if (bestGear != currentGear) {
            overrideCountDown.put(gearbox, Math.max(0.2f, gearbox.attr.staticAttribute.switchTime + 0.5f));
        }
        return bestGear;
    }

    protected float steering(float steeringInput, AdvancedConnector wheelDrive) {
        if (driftWeight <= 0f) return ackermannSteering(steeringInput, wheelDrive);
        else
            return (1 - driftWeight) * ackermannSteering(steeringInput, wheelDrive) + driftWeight * driftSteering(wheelDrive);
    }

    protected float ackermannSteering(float steeringInput, AdvancedConnector wheelDrive) {
        New6Dof joint = wheelDrive.joint;
        Vector3f pivot = new Vector3f();
        if (wheelDrive.subPart.body == joint.getBodyA()) joint.getPivotA(pivot);
        else joint.getPivotB(pivot);
        if (steeringInput == 0) {
            return 0;
        } else {
            //实际转向半径(米) Actual steering radius (m)
            float steeringRadius = ControlPreference.shouldLimitSpeedTurning(this)
                    ? attr.staticAttribute.getSteeringRadiusAtSpeed(speed) / steeringInput // 使用动态转向半径映射表，根据当前速度获取合适的转向半径
                    : attr.staticAttribute.getMinSteeringRadius() / steeringInput; // 否则使用最小转向半径
            double deltaRadius = pivot.x - computedSteeringCenter.x;
            deltaRadius *= Math.signum(steeringInput);
            double deltaForward = pivot.z - computedSteeringCenter.z;
            return (float) Math.atan(deltaForward / (steeringRadius + deltaRadius));
        }
    }

    /**
     * 漂移模式下自动反打方向，并使用PD控制器逼近目标角速度，仅前轮转向
     *
     * @return 轮胎转向角度，以弧度为单位
     */
    protected float driftSteering(AdvancedConnector wheelDrive) {
        New6Dof joint = wheelDrive.joint;
        Vector3f pivot = new Vector3f();
        if (wheelDrive.subPart.body == joint.getBodyA()) joint.getPivotA(pivot);
        else joint.getPivotB(pivot);
        return pivot.z <= computedSteeringCenter.z ? 0.5f * driftRad - driftControl : 0;
    }

    @Override
    public List<String> getAcceptedChannels() {
        return attr.staticAttribute.getControlInputKeys();
    }

    @Override
    public Map<String, List<String>> getTargetNames() {
        Map<String, List<String>> result = new HashMap<>(attr.getControlOutputTargets());
        result.putAll(attr.getSpeedOutputTargets());
        result.putAll(attr.getThrottleOutputTargets());
        result.putAll(attr.getSteeringOutputTargets());
        result.putAll(attr.getBrakeOutputTargets());
        result.putAll(attr.getHandbrakeOutputTargets());
        return result;
    }

    /**
     * 计算考虑ABS的有效刹车力
     *
     * @param wheel    轮胎驱动子系统
     * @param rawBrake 原始刹车力
     * @return 经过ABS调整后的有效刹车力
     */
    protected float calculateEffectiveBrake(WheelDriverSubsystem wheel, float rawBrake) {
        float vehicleSpeed = Math.abs(this.speed);
        // 使用轮驱系统的ABS配置
        if (!wheel.attr.staticAttribute.isAbsEnabled() || rawBrake <= 0 || vehicleSpeed < 2f) {
            // 不使用ABS或刹车力为0或车速小于2m/s时，直接返回原始刹车力
            return rawBrake;
        }
        float slipRatio = calculateSlipRatio(wheel);
        // ABS控制逻辑
        float targetSlipRatio = wheel.attr.staticAttribute.getAbsTargetSlipRatio();
        float effectiveBrake = rawBrake;

        if (slipRatio > targetSlipRatio) {
            // 滑移率过高，减少刹车力
            float reductionFactor = Math.clamp(1.0f - (slipRatio - targetSlipRatio) / 0.15f, 0.1f, 1.0f);
            effectiveBrake = rawBrake * reductionFactor;
        }

        return Math.max(0f, Math.min(1f, effectiveBrake));
    }

    protected float calculateSlipRatio(WheelDriverSubsystem wheel) {
        // 获取轮胎角速度
        float angularVelocity = -wheel.getRelativeAngularVel().get(0); // X轴角速度
        // 计算轮胎线速度
        float wheelLinearSpeed = Math.abs(angularVelocity * wheel.attr.staticAttribute.getAbsWheelRadius());
        float absSpeed = Math.abs(speed);
        // 计算滑移率
        return Math.abs(absSpeed - wheelLinearSpeed) / (Math.min(absSpeed, wheelLinearSpeed) + 0.1f);
    }
}
