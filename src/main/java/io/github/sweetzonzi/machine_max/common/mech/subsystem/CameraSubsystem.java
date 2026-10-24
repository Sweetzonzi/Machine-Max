package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.common.mech.control.aim.*;
import io.github.sweetzonzi.machine_max.common.mech.control.view.ViewMath;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalChannel;
import io.github.sweetzonzi.machine_max.common.mech.signal.ViewInputSignal;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.CameraSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.CameraSubsystemStaticAttr;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 可转摄像头持有世界观察目标与相对观察角，客户端主线程推进，渲染只读采样。<br>
 * 稳定轴按世界目标和本次挂点求方向，相对角只提供行程与角速度边界。
 * TODO：观察角的多人同步与镜头共享控制权协议。
 */
public class CameraSubsystem extends AbstractViewpointSubsystem {
    public final CameraSubsystemAttr attr;

    /** 当前观察区间起点的实际局部角，作为刻内限速参考；弧度，x=俯仰、y=偏航。 */
    @Getter
    private Vec2 viewAngles = Vec2.ZERO;
    /** 无稳轴保持的相对观察目标；稳定轴分量由世界点求出，不独立累计。 */
    private Vec2 targetViewAngles = Vec2.ZERO;
    /** 刻内采样的时间跨度，单位为秒；采样本身只读，不按调用次数推进。 */
    private float viewStepSeconds = .05f;
    /** 稳定轴保持的有限世界点；空表示尚未进入观察。 */
    @Nullable
    private Vec3 viewTarget;

    /** 按配置频道读取的外部目标世界坐标 */
    private volatile Vec3 trackingTarget = null;
    /** 当前是否存在有效跟踪目标 */
    private volatile boolean isTracking = false;

    // 跟踪状态的服务端同步字段。
    public static final EntityDataAccessor<Boolean> DATA_IS_TRACKING =
            SynchedEntityData.defineId(CameraSubsystem.class, EntityDataSerializers.BOOLEAN);

    public CameraSubsystem(ISubsystemHost owner, String name, CameraSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_IS_TRACKING, false);
    }

    @Override
    public void onTick() {
        super.onTick();
        synchedData.set(DATA_IS_TRACKING, isTracking);
        readTrackingTarget();
        if (getLevel().isClientSide() && isActive() && !isDestroyed()) {
            updateView(.05f);
        }
    }

    /** 主线程建立本机安装参考下的两轴几何；配置角度为度，进入计算时转换为弧度。 */
    private AimGeometrySnapshot viewGeometry(AimPose mount) {
        var sa = attr.staticAttribute;
        return new AimGeometrySnapshot(mount, new Transform(), new Vector3f(0, -1, 0),
                new Transform(), Vector3f.UNIT_X, new Transform(),
                sa.yawLimit >= 360 ? AimAxisLimit.CONTINUOUS : new AimAxisLimit(false,
                        Math.toRadians(-sa.yawLimit / 2), Math.toRadians(sa.yawLimit / 2)),
                new AimAxisLimit(false, Math.toRadians(sa.minPitch), Math.toRadians(sa.maxPitch)));
    }

    /**
     * 客户端主线程切入设备时按可达行程继承目标。<br>
     * 切入对齐属于观察初始化；后续实际角以设备转速追赶鼠标给出的期望角。
     *
     * @param inherited 切出视点的世界目标；为 null 时使用本机保存的目标或默认光轴
     */
    public void enterView(@Nullable Vec3 inherited) {
        AimGeometrySnapshot geometry = viewGeometry(super.samplePose(1));
        viewTarget = inherited != null ? inherited : viewTarget != null ? viewTarget
                : AimMath.buildAimPoint(geometry, viewAngles);
        targetViewAngles = AimMath.solveAim(geometry, viewTarget, viewAngles).angles();
        viewAngles = targetViewAngles;
    }

    /** 只读求解本次挂点中的观察目标；稳定轴取世界点，无稳轴取保存的相对角。 */
    private Vec2 resolveViewTarget(AimGeometrySnapshot geometry) {
        var sa = attr.staticAttribute;
        return switch (AimTargetState.Mode.of(sa.horizontalStabilized, sa.verticalStabilized)) {
            case RELATIVE -> targetViewAngles;
            case WORLD -> AimMath.solveAim(geometry, viewTarget, viewAngles).angles();
            case YAW_STABLE -> AimMath.solveSingleAxisAim(geometry, viewTarget, targetViewAngles, true, viewAngles).angles();
            case PITCH_STABLE -> AimMath.solveSingleAxisAim(geometry, viewTarget, targetViewAngles, false, viewAngles).angles();
        };
    }

    /**
     * 客户端主线程按采样顺序累计鼠标输入，先限位再接受下一份输入。<br>
     * 有效输入会更新稳定轴的世界参考；零输入不重建有限世界点，允许正常的平移视差。
     *
     * @param delta 俯仰／偏航增量，弧度，x=俯仰、y=偏航
     */
    public void turnView(Vec2 delta) {
        if (delta.x == 0 && delta.y == 0) {
            return;
        }
        if (viewTarget == null) {
            enterView(null);
        }
        AimPose mount = super.samplePose(1);
        AimGeometrySnapshot geometry = viewGeometry(mount);
        if (attr.staticAttribute.horizontalStabilized && attr.staticAttribute.verticalStabilized) {
            // 双稳输入与第三人称自由观察共用世界 yaw/pitch；安装体侧倾不改变鼠标轴。
            Vec3 next = ViewMath.advanceFree(viewTarget, mount.position(), delta);
            Vec2 limited = AimMath.solveAim(geometry, next, viewAngles).angles();
            viewTarget = AimMath.buildAimPoint(geometry, limited);
            return;
        }
        Vec2 before = resolveViewTarget(geometry);
        targetViewAngles = AimMath.advanceRelativeAim(geometry, before, delta);
        if ((attr.staticAttribute.horizontalStabilized || attr.staticAttribute.verticalStabilized)
                && (targetViewAngles.x != before.x || targetViewAngles.y != before.y)) {
            viewTarget = AimMath.buildAimPoint(geometry, targetViewAngles);
        }
    }

    /**
     * 客户端主线程提交观察区间终点，保存下一区间的限速起点。<br>
     * 当前已发布挂点是上一区间的终点；本次计算与 samplePose(1) 使用同一求解过程。
     * 世界目标保持不变，后续采样会使用本帧挂点重新施加约束，避免缓存局部角携带安装体运动。
     *
     * @param seconds 本次逻辑更新的秒数，正常 tick 为 0.05 秒
     */
    public void updateView(float seconds) {
        if (viewTarget == null) {
            return;
        }
        viewAngles = sampleAngles(viewGeometry(super.samplePose(1)), seconds);
        viewStepSeconds = seconds;
    }

    /** 只读计算给定时刻的可达角，转速预算同时约束鼠标转向和安装体运动补偿。 */
    private Vec2 sampleAngles(AimGeometrySnapshot geometry, float seconds) {
        Vec2 desired = resolveViewTarget(geometry);
        return geometry.constrain(new Vec2(
                approach(viewAngles.x, desired.x, attr.staticAttribute.pitchSpeed, seconds),
                approach(viewAngles.y, desired.y, attr.staticAttribute.yawSpeed, seconds)));
    }

    /**
     * 主线程／渲染线程读取操作者的观察目标，供 HUD 表达目标与实际光轴的偏移。<br>
     * 双稳保持世界点；含无稳轴时，在本帧挂点中求解目标角，随安装体运动。
     * 此处不施加转速预算，也不推进观察状态；武器输出使用 samplePose 的实际光轴。
     *
     * @param partialTick 与画面相机一致的刻内插值比例
     */
    @Nullable
    public Vec3 sampleViewTarget(float partialTick) {
        if (viewTarget == null || (attr.staticAttribute.horizontalStabilized && attr.staticAttribute.verticalStabilized)) {
            return viewTarget;
        }
        AimGeometrySnapshot geometry = viewGeometry(super.samplePose(partialTick));
        return AimMath.buildAimPoint(geometry, resolveViewTarget(geometry));
    }

    /** 按度／秒配置限制本步弧度变化量，不把显示帧率作为转速依据。 */
    private static float approach(float actual, float desired, float speed, float seconds) {
        float step = (float) Math.toRadians(speed) * seconds;
        return actual + Math.clamp(desired - actual, -step, step);
    }

    /**
     * 主线程／渲染线程按本次挂点直接读取受约束的观察方向。<br>
     * 稳定轴在行程和转速允许时指向世界目标；超限时使用本区间可达方向。
     * 双稳视野与第三人称自由观察共用世界朝向合成，镜头局部角仅用于约束；
     * 无稳轴相对挂点转动。刻内时间只决定转速预算，读取不会推进状态或改写目标。
     *
     * @param partialTick 逻辑刻内的插值比例
     */
    @Override
    public AimPose samplePose(float partialTick) {
        AimPose mount = super.samplePose(partialTick);
        if (viewTarget == null) {
            return mount;
        }
        AimGeometrySnapshot geometry = viewGeometry(mount);
        Vec2 angle = sampleAngles(geometry, viewStepSeconds * Math.clamp(partialTick, 0, 1));
        AimPose actual = geometry.forward(angle);
        if (attr.staticAttribute.horizontalStabilized && attr.staticAttribute.verticalStabilized) {
            Vec3 point = actual.position().add(actual.forward().scale(AimMath.AIM_DISTANCE_METERS));
            return ViewMath.free(point, mount.position()).pose();
        }
        return actual;
    }

    /**
     * 从 trackingTargetInputs 频道读取跟踪目标。
     * 有目标 → isTracking=true；无目标 → isTracking=false。
     */
    private void readTrackingTarget() {
        CameraSubsystemStaticAttr sa = attr.staticAttribute;
        for (String channelName : sa.getTrackingTargetInputs()) {
            SignalChannel channel = getSignalChannel(channelName);
            Object val = channel.getFirstSignal();
            if (val instanceof Vec3 vec) {
                this.trackingTarget = vec;
                this.isTracking = true;
                return;
            } else if (val instanceof ViewInputSignal vis) {
                this.trackingTarget = vis.aimPoint;
                this.isTracking = true;
                return;
            }
        }
        this.trackingTarget = null;
        this.isTracking = false;
    }

    @Override
    public List<String> getAcceptedChannels() {
        List<String> channels = new ArrayList<>();
        channels.addAll(attr.staticAttribute.getTrackingTargetInputs());
        channels.addAll(attr.staticAttribute.getDiscoveryInputs());
        return channels;
    }

}
