package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import com.jme3.math.Vector3f;
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
 * 履带车辆控制器（单流 v1）。
 *
 * <p>通过 {@code control_outputs} 发现发动机/电机/变速箱与 {@link TrackDriveSubsystem}，
 * 按中心化横向位置 {@code x_i} 分配目标线速度；没有转向电机/转向传动时用内侧制动实现差速。</p>
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
    private float previousYawError = Float.NaN;

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
            previousYawError = Float.NaN;
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
        previousYawError = Float.NaN;
    }

    @Override
    protected void distributeDriveControl() {
        List<TrackBinding> bindings = validBindings();
        if (bindings.isEmpty()) {
            baseSpeed = 0f;
            yawRateCmd = 0f;
            return;
        }

        float vBase = computeBaseSpeed(bindings);
        this.baseSpeed = vBase;

        float maxYawRate = computeMaxYawRate(vBase, bindings);
        float rDes = Math.abs(vBase) >= 0.5f
                ? Math.clamp(actualSteering, -1f, 1f) * maxYawRate
                : 0f;

        float bodyYawRate = getSubPart().body.getAngularVelocityLocal(null).y;
        float error = rDes - bodyYawRate;
        float tps = getPhysicsLevel().getTps();
        float dt = tps > 0f ? 1f / tps : 0.05f;
        float derivative = Float.isNaN(previousYawError) ? 0f : (error - previousYawError) / dt;
        previousYawError = error;

        float rCmd = Math.clamp(
                rDes + attr.staticAttribute.yawP * error + attr.staticAttribute.yawD * derivative,
                -maxYawRate, maxYawRate);
        this.yawRateCmd = rCmd;

        for (TrackBinding binding : bindings) {
            float desired = vBase + rCmd * binding.x();
            float achieved = TrackMath.singleFlowAchieved(desired, vBase);
            float brake = TrackMath.singleFlowBrake(vBase, achieved, actualBrake);
            sendCallbackToListener(binding.channel(), binding.drive(),
                    new TrackControlSignal(brake, actualHandBrake, 1f));
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
     * 单流基础速度：前进取最大、倒车取最小；没有有效驱动轮时退回车体速度。
     */
    private float computeBaseSpeed(List<TrackBinding> bindings) {
        boolean found = false;
        float value = 0f;
        boolean forward = speed >= 0f;
        for (TrackBinding binding : bindings) {
            float trackSpeed = binding.drive().linearSpeed();
            if (!Float.isFinite(trackSpeed)) continue;
            if (!found) {
                value = trackSpeed;
                found = true;
            } else if (forward) {
                value = Math.max(value, trackSpeed);
            } else {
                value = Math.min(value, trackSpeed);
            }
        }
        return found ? value : speed;
    }

    private float computeMaxYawRate(float vBase, List<TrackBinding> bindings) {
        if (bindings.size() < 2) return 0f;
        float minX = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        for (TrackBinding binding : bindings) {
            minX = Math.min(minX, binding.x());
            maxX = Math.max(maxX, binding.x());
        }
        if (maxX - minX < 0.1f) return 0f;

        float table = (float) Math.toRadians(
                attr.staticAttribute.getMaxYawRateAtSpeed(Math.abs(speed)));
        return TrackMath.maxYawRate(table,
                attr.staticAttribute.maxLateralAcceleration,
                attr.staticAttribute.minTurningRadius,
                vBase);
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
