package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.common.attachment.ControlPreference;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalSender;
import io.github.sweetzonzi.machine_max.common.mech.signal.TrackControlSignal;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.TrackControllerSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackMath;
import io.github.sweetzonzi.machine_max.util.MMMath;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 履带车辆控制器（单流制动差速）。
 *
 * <p>通过 {@code control_outputs} 发现发动机/电机/变速箱与 {@link TrackDriveSubsystem}，
 * 按横向位置分配外侧锚定的带速上限，内侧由驱动的限扭速度伺服减速。
 * 意图模式的纯转向请求前进动力和单侧保持制动，车体绕停止侧附近转动。</p>
 *
 * <p>绑定表在主线程握手/结构变化时重建，物理线程只读取 volatile 快照。</p>
 */
@Getter
public class TrackControllerSubsystem extends AbstractGroundControllerSubsystem {
    public final TrackControllerSubsystemAttr attr;

    private final Map<TrackDriveSubsystem, String> tracks = new HashMap<>();
    private volatile List<TrackBinding> trackBindings = List.of();

    private float yawRateCmd = 0f;
    private float baseSpeed = 0f;
    private float previousYawRate = Float.NaN;

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
            if (track.getOwner().getSubPart().getPart().assembly
                    != this.getOwner().getSubPart().getPart().assembly) {
                tracks.remove(track);
                removeCallbackTarget(channel, track);
            } else {
                tracks.put(track, channel);
                addCallbackTarget(channel, track);
            }
        }
    }

    @Override
    protected void clearActuatorBindings() {
        super.clearActuatorBindings();
        tracks.clear();
        trackBindings = List.of();
    }

    @Override
    protected void onHandShakeComplete() {
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
            previousYawRate = Float.NaN;
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
        previousYawRate = Float.NaN;
    }

    @Override
    protected float resolveDriveIntent(float longitudinalInput) {
        List<TrackBinding> bindings = validBindings();
        if (bindings.size() < 2 || bindings.getLast().x() - bindings.getFirst().x() < 0.1f) {
            return longitudinalInput;
        }
        return TrackMath.singleFlowDriveIntent(longitudinalInput, actualSteering);
    }

    @Override
    protected void distributeControlSignals() {
        if (moveInput == null || moveInputConflict == null) {
            actualThrottle = 0f;
            actualSteering = 0f;
            previousYawRate = Float.NaN;
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
            return;
        }

        float inputSpeed = 0f;
        for (TrackBinding binding : bindings) inputSpeed += binding.drive().inputSpeed();
        float direction = TrackMath.singleFlowDirection(inputSpeed, speed, actualThrottle);
        float vBase = computeBaseSpeed(bindings, direction);
        this.baseSpeed = vBase;
        float minX = bindings.getFirst().x();
        float maxX = bindings.getLast().x();
        float width = maxX - minX;
        boolean raw = ControlPreference.shouldRawThrottleBrake(this);
        float longitudinal = moveInput == null ? 0f : moveInput[2] / 100f;
        float steering = moveInput == null ? 0f : Math.clamp(actualSteering, -1f, 1f);
        float turnIntent = (raw ? steering : TrackMath.singleFlowTurnRatio(longitudinal, steering)) * direction;
        float pivotWeight = raw ? 0f : Math.clamp(
                (Math.abs(steering) - Math.abs(longitudinal)) / Math.max(Math.abs(steering), 0.01f), 0f, 1f);
        float tableYawRate = (float) Math.toRadians(attr.staticAttribute.getMaxYawRateAtSpeed(Math.abs(speed)));
        float turnLimit = TrackMath.singleFlowTurnLimit(vBase, speed, width, tableYawRate,
                attr.staticAttribute.maxLateralAcceleration, attr.staticAttribute.minTurningRadius, pivotWeight);
        float rDes = width < 0.1f ? 0f
                : Math.clamp(turnIntent, -turnLimit, turnLimit) * Math.abs(vBase) / width;
        float bodyYawRate = getSubPart().body.getAngularVelocityLocal(null).y;
        float error = rDes - bodyYawRate;
        float tps = getPhysicsLevel().getTps();
        float dt = tps > 0f ? 1f / tps : 0.05f;
        float derivative = Float.isNaN(previousYawRate) ? 0f : (bodyYawRate - previousYawRate) / dt;
        previousYawRate = bodyYawRate;
        float correction = (attr.staticAttribute.yawP * error - attr.staticAttribute.yawD * derivative)
                * Math.max(width, 0f) / Math.max(Math.abs(vBase), 0.5f)
                * Math.clamp(Math.abs(vBase) / 0.5f, 0f, 1f);
        float turnRatio = Math.clamp(turnIntent + correction, -turnLimit, turnLimit);
        this.yawRateCmd = width < 0.1f ? 0f : turnRatio * Math.abs(vBase) / width;

        for (TrackBinding binding : bindings) {
            float ratio = TrackMath.singleFlowSpeedRatio(binding.x(), minX, maxX, turnRatio, direction);
            float maxSpeed = binding.drive().linearMaxSpeed();
            float pivotSpeed = Math.min(maxSpeed, Math.max(tableYawRate, 0f) * Math.max(width, 0f)
                    * Math.max(Math.abs(longitudinal), Math.abs(steering)));
            float outerLimit = maxSpeed + (pivotSpeed - maxSpeed) * pivotWeight;
            float target = ratio < 1f - TrackMath.EPSILON
                    ? direction * Math.min(Math.abs(vBase), outerLimit) * ratio
                    : (pivotWeight > 0f && width >= 0.1f ? direction * outerLimit : Float.NaN);
            sendCallbackToListener(binding.channel(), binding.drive(),
                    new TrackControlSignal(actualBrake, actualHandBrake, 1f, target));
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
