package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.common.attachment.ControlPreference;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalSender;
import io.github.sweetzonzi.machine_max.common.mech.signal.TrackControlSignal;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.TrackControllerSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.CarControllerSubsystemStaticAttr.HandBrakeSoundAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackMath;
import io.github.sweetzonzi.machine_max.util.MMMath;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 履带车辆控制器（单流开环拉杆制动）。
 *
 * <p>通过 {@code control_outputs} 发现发动机/电机/变速箱与 {@link TrackDriveSubsystem}。
 * A/D 是转向拉杆，按横向位置把基础制动偏置到内侧、释放外侧；拉杆超过死区后内侧离合断开，
 * 让开放差速器把动力集中到外侧。W/S 是纵向意图，负责加速或制动。意图模式的纯转向请求
 * 当前行进方向的动力，使车体绕内侧附近转动。</p>
 *
 * <p>绑定表在主线程握手/结构变化时重建，物理线程只读取 volatile 快照。</p>
 */
@Getter
public class TrackControllerSubsystem extends AbstractGroundControllerSubsystem {
    public final TrackControllerSubsystemAttr attr;

    /** 发现阶段的临时绑定表；主线程写，完成后整体发布。 */
    private final Map<TrackDriveSubsystem, String> pendingTracks = new LinkedHashMap<>();
    /** 履带驱动绑定快照：物理线程只读。 */
    private volatile Map<TrackDriveSubsystem, String> tracks = Map.of();
    private volatile List<TrackBinding> trackBindings = List.of();
    /** 上一物理步下发给各条履带的制动量（0~1），下标与 {@link #trackBindings} 对齐。 */
    private volatile float[] trackBrakes = new float[0];
    /** 上一物理步下发给各条履带的离合，下标与 {@link #trackBindings} 对齐。 */
    private volatile boolean[] trackClutches = new boolean[0];

    private float yawRateCmd = 0f;
    private float baseSpeed = 0f;
    /** 拉杆侧别使用；低速/静止时沿用上一次行进方向。 */
    private volatile float lastTravelDirection = 1f;

    public record TrackBinding(TrackDriveSubsystem drive, String channel, float localX, float x, float radius) {
    }

    public TrackControllerSubsystem(ISubsystemHost owner, String name, TrackControllerSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
    }

    @Override
    public boolean isActive() {
        if (!super.isActive()) return false;
        for (TrackBinding binding : trackBindings) {
            if (binding.drive().isDriveUsable()) return true;
        }
        return false;
    }

    @Override
    protected void onActuatorDiscovered(ISignalSender sender, String channel) {
        if (sender instanceof TrackDriveSubsystem track) {
            pendingTracks.put(track, channel);
        }
    }

    @Override
    protected void clearActuatorBindings() {
        super.clearActuatorBindings();
        pendingTracks.clear();
        tracks = Map.of();
        trackBindings = List.of();
        trackBrakes = new float[0];
        trackClutches = new boolean[0];
        lastTravelDirection = 1f;
    }

    @Override
    protected void onHandShakeComplete() {
        tracks = Collections.unmodifiableMap(new LinkedHashMap<>(pendingTracks));
        rebuildTrackBindings();
    }

    /**
     * 从按身份去重的驱动表重建带中心化 {@code x_i} 的不可变快照。
     */
    protected void rebuildTrackBindings() {
        List<TrackBinding> raw = new ArrayList<>();
        for (Map.Entry<TrackDriveSubsystem, String> entry : tracks.entrySet()) {
            TrackDriveSubsystem drive = entry.getKey();
            if (!drive.isDriveUsable()) continue;
            AbstractConnector connector = drive.connector;
            if (connector == null) continue;

            Vector3f offset = connector.offsetFromMassCenter.getTranslation();
            Vector3f world = MMMath.relPointWorldPos(offset, connector.subPart.body);
            Vector3f local = MMMath.worldPointLocalPos(world, getSubPart().body);
            raw.add(new TrackBinding(drive, entry.getValue(), local.x, local.x, drive.radius()));
        }

        if (raw.isEmpty()) {
            trackBindings = List.of();
            trackBrakes = new float[0];
            trackClutches = new boolean[0];
            return;
        }

        float mean = 0f;
        for (TrackBinding binding : raw) mean += binding.localX();
        mean /= raw.size();

        List<TrackBinding> centered = new ArrayList<>(raw.size());
        for (TrackBinding binding : raw) {
            centered.add(new TrackBinding(binding.drive(), binding.channel(), binding.localX(),
                    binding.localX() - mean, binding.radius()));
        }
        centered.sort(Comparator.comparingDouble(TrackBinding::x));
        trackBindings = List.copyOf(centered);
        trackBrakes = new float[centered.size()];
        trackClutches = new boolean[centered.size()];
    }

    @Override
    protected float resolveDriveIntent(float longitudinalInput) {
        List<TrackBinding> bindings = validBindings();
        if (bindings.size() < 2 || bindingWidth(bindings) < 0.1f) {
            return longitudinalInput;
        }
        return TrackMath.accelerationIntent(longitudinalInput, actualSteering, speed);
    }

    @Override
    protected float resolveBrakeIntent(float longitudinalInput) {
        return TrackMath.brakeIntent(longitudinalInput, speed);
    }

    @Override
    protected void distributeControlSignals() {
        if (moveInput == null || moveInputConflict == null) {
            actualThrottle = 0f;
            actualSteering = 0f;
            calculateAvgSpeedAndControl();
        }
        super.distributeControlSignals();
    }

    @Override
    protected void distributeDriveControl() {
        List<TrackBinding> bindings = validBindings();
        if (bindings.isEmpty()) {
            baseSpeed = 0f;
            yawRateCmd = 0f;
            trackBrakes = new float[trackBindings.size()];
            trackClutches = new boolean[trackBindings.size()];
            Arrays.fill(trackBrakes, Float.NaN);
            return;
        }

        float width = bindingWidth(bindings);
        float steering = moveInput == null ? 0f : Math.clamp(actualSteering, -1f, 1f);
        float lever = TrackMath.leverBrake(steering);
        float travel = TrackMath.travelDirection(actualThrottle, speed, lastTravelDirection);
        lastTravelDirection = travel;
        boolean innerLeft = TrackMath.leverBrakesLeft(steering, travel);

        float vBase = computeBaseSpeed(bindings, travel);
        baseSpeed = vBase;
        yawRateCmd = width < 0.1f ? 0f : steering * lever * Math.abs(vBase) / width;

        List<TrackBinding> all = trackBindings;
        float[] brakes = new float[all.size()];
        boolean[] clutches = new boolean[all.size()];
        for (int i = 0; i < all.size(); i++) {
            TrackBinding binding = all.get(i);
            if (!binding.drive().isDriveUsable()) {
                brakes[i] = Float.NaN;
                continue;
            }
            float brake = actualBrake;
            boolean clutch = true;
            if (width >= 0.1f && lever > 0f) {
                boolean inner = innerLeft ? binding.x() < 0f : binding.x() > 0f;
                if (inner) {
                    brake = TrackMath.innerBrake(actualBrake, lever);
                    clutch = TrackMath.steeringClutchEngaged(lever);
                } else {
                    brake = TrackMath.outerBrake(actualBrake, lever);
                }
            }
            brakes[i] = brake;
            clutches[i] = clutch;
            sendSignalToTarget(binding.channel(), binding.drive(),
                    new TrackControlSignal(brake, actualHandBrake, clutch, Float.NaN));
        }
        trackBrakes = brakes;
        trackClutches = clutches;
    }

    @Override
    protected String telemetryExtraColumns() {
        StringBuilder columns = new StringBuilder(",base_mps,yaw_cmd,yaw_act");
        for (int i = 0; i < trackBindings.size(); i++) {
            columns.append(",t").append(i).append("_brk")
                    .append(",t").append(i).append("_in")
                    .append(",t").append(i).append("_spd")
                    .append(",t").append(i).append("_trq")
                    .append(",t").append(i).append("_r")
                    .append(",t").append(i).append("_clu");
        }
        return columns.toString();
    }

    /**
     * 追加单流基准、横摆估计与每条履带的六元组：本步下发的制动量与离合、传动箱给出的伺服目标带速、
     * 履带实际带速、下发给滚动轴电机的驱动扭矩、主动轮半径。
     *
     * <p>{@code _brk} 是拉杆偏置后真正下发的制动量；{@code _clu} 是下发的离合（0 断开、1 结合）；
     * {@code _in} 是传动箱输出目标，等于「动力链折算到主动轮的转速 + {@code LOCK_DRIVE_BIAS}」，
     * 与 {@code _spd} 的差是速度伺服在这一步的误差。{@code _trq} 贴着 {@code max_drive_torque}
     * 而 {@code _spd} 追不上目标，说明接触面吃不下这份力矩。{@code _brk} 为 NaN 表示该侧不可用。</p>
     */
    @Override
    protected void appendTelemetrySample(StringBuilder row) {
        float yawRate = getSubPart().body.getAngularVelocityLocal(null).y;
        row.append(String.format(Locale.ROOT, ",%.3f,%.3f,%.3f", baseSpeed, yawRateCmd, yawRate));
        List<TrackBinding> all = trackBindings;
        float[] brakes = trackBrakes;
        boolean[] clutches = trackClutches;
        for (int i = 0; i < all.size(); i++) {
            TrackDriveSubsystem drive = all.get(i).drive();
            if (!drive.isDriveUsable()) {
                row.append(",NaN,NaN,NaN,NaN,NaN,NaN");
                continue;
            }
            float brake = i < brakes.length ? brakes[i] : Float.NaN;
            float clutch = i < clutches.length ? (clutches[i] ? 1f : 0f) : Float.NaN;
            row.append(String.format(Locale.ROOT, ",%.3f,%.3f,%.3f,%.1f,%.3f,%.3f",
                    brake, drive.inputSpeed() * drive.radius(), drive.linearSpeed(),
                    drive.getDriveTorque(), drive.radius(), clutch));
        }
    }

    private List<TrackBinding> validBindings() {
        List<TrackBinding> result = new ArrayList<>(trackBindings.size());
        for (TrackBinding binding : trackBindings) {
            if (binding.drive().isDriveUsable()) result.add(binding);
        }
        return result;
    }

    /**
     * 低速转向时保持最低挡。
     *
     * <p>原地转向的中心车速很低，但被驱动侧履带可能已经被发动机拖到高速；基类只按发动机转速
     * 预测各挡扭矩，会在 1 挡红区直接升 2 挡，换挡断扭矩后整车顿住。这里在转向且中心车速低于
     * {@link TrackMath#LOW_SPEED_SHIFT_HOLD} 时锁住最低挡，松开转向或车速上来后交还给基类。</p>
     */
    @Override
    protected int autoGearShift(GearboxSubsystem gearbox, float engineSpeed, byte direction) {
        if (!ControlPreference.shouldAutoSwitchGear(this)) {
            return super.autoGearShift(gearbox, engineSpeed, direction);
        }

        float lever = TrackMath.leverBrake(actualSteering);
        if (!TrackMath.holdLowGearForSteering(lever, speed)) {
            return super.autoGearShift(gearbox, engineSpeed, direction);
        }

        byte shiftDirection = direction;
        if (shiftDirection == 0 && Math.abs(speed) > 0.5f) {
            shiftDirection = (byte) (speed >= 0f ? 1 : -1);
        }
        if (shiftDirection == 0) {
            return super.autoGearShift(gearbox, engineSpeed, direction);
        }

        int lowGear = shiftDirection < 0 ? gearbox.minNegativeGear : gearbox.minPositiveGear;
        if (gearbox.getCurrentGear() == lowGear) {
            return lowGear;
        }
        if (Math.abs(speed) < 1f) {
            return lowGear;
        }
        return super.autoGearShift(gearbox, engineSpeed, direction);
    }

    /** 有效绑定的横向宽度；不足两条时为 0。 */
    private float bindingWidth(List<TrackBinding> bindings) {
        return bindings.size() < 2 ? 0f : bindings.getLast().x() - bindings.getFirst().x();
    }

    /**
     * 单流外侧基准：沿本次驱动方向取最大带速，反向残余速度不作为转向基准。
     */
    private float computeBaseSpeed(List<TrackBinding> bindings, float direction) {
        float value = 0f;
        for (TrackBinding binding : bindings) {
            float trackSpeed = binding.drive().linearSpeed();
            if (!Float.isFinite(trackSpeed)) continue;
            value = Math.max(value, direction * trackSpeed);
        }
        return direction * value;
    }

    @Override
    protected List<String> getControlInputKeys() {
        return attr.staticAttribute.getControlInputKeys();
    }

    @Override
    protected Map<String, List<String>> getControlOutputTargets() {
        return attr.getControlOutputTargets();
    }

    @Override
    protected Map<String, List<String>> getSpeedOutputTargets() {
        return attr.getSpeedOutputTargets();
    }

    @Override
    protected Map<String, List<String>> getThrottleOutputTargets() {
        return Map.of();
    }

    @Override
    protected Map<String, List<String>> getBrakeOutputTargets() {
        return Map.of();
    }

    @Override
    protected Map<String, List<String>> getHandbrakeOutputTargets() {
        return Map.of();
    }

    @Override
    protected HandBrakeSoundAttr getHandBrakeSounds() {
        return attr.staticAttribute.getSounds();
    }

    @Override
    public boolean isAutoHandBrakeEnabled() {
        return attr.staticAttribute.isAutoHandBrake();
    }

    @Override
    public boolean isManualGearShift() {
        return attr.staticAttribute.isManualGearShift();
    }

    @Override
    public boolean isSeparateThrottleBrake() {
        return attr.staticAttribute.isSeparateThrottleBrake();
    }

    @Override
    protected float getSteeringSmoothing() {
        return attr.staticAttribute.steeringSmoothing;
    }

    @Override
    protected float getBrakeSmoothing() {
        return attr.staticAttribute.brakeSmoothing;
    }
}
