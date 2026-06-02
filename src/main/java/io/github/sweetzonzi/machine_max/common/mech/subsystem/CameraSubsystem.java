package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalChannel;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalResult;
import io.github.sweetzonzi.machine_max.common.mech.signal.ViewInputSignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalSender;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.CameraSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.CameraSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 摄像机子系统：提供炮镜视角与瞄准点输出功能。<p>
 * 服务端每 20tps 运行 onTick，将客户端注入的 lastAimPoint 以 ViewInputSignal 输出到指定频道。
 * 瞄准点由客户端 CameraController 以世界空间坐标直接维护——鼠标移动平移瞄准点，鼠标不动则瞄准点不动。
 */
public class CameraSubsystem extends BasicSubsystem {
    public final CameraSubsystemAttr attr;

    /** 当前俯仰角偏移（弧度），相对于摄像机基座正前方。
     *  由客户端 CameraController 在渲染帧中通过稳定器修正。
     *  volatile 供客户端跨线程读写。 */
    public volatile float aimPitch = 0f;
    /** 当前偏航角偏移（弧度），相对于摄像机基座正前方。
     *  volatile 供客户端跨线程读写。 */
    public volatile float aimYaw = 0f;
    /** 客户端注入的世界空间瞄准点。
     *  由 receiveClientAimInput 每网络包设置，onTick 读取后输出 ViewInputSignal。 */
    public volatile Vec3 lastAimPoint = null;
    /** TRACKING 模式下跟踪的外部目标世界坐标（从信号频道读取） */
    private volatile Vec3 trackingTarget = null;
    /** 当前是否处于 TRACKING 模式（由 trackingTarget 驱动，非静态属性） */
    private volatile boolean isTracking = false;
    /** 当前是否有观众在看此摄像机。
     *  服务端由 receiveClientAimInput 每网络包设置，onTick 末尾清除；
     *  客户端由 CameraController 进入/退出时设置。 */
    public volatile boolean hasViewer = false;

    // ===== SynchedEntityData keys（同步到客户端）=====
    public static final EntityDataAccessor<Float> DATA_AIM_PITCH =
            SynchedEntityData.defineId(CameraSubsystem.class, EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Float> DATA_AIM_YAW =
            SynchedEntityData.defineId(CameraSubsystem.class, EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Boolean> DATA_IS_TRACKING =
            SynchedEntityData.defineId(CameraSubsystem.class, EntityDataSerializers.BOOLEAN);

    public CameraSubsystem(ISubsystemHost owner, String name, CameraSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_AIM_PITCH, 0f);
        builder.define(DATA_AIM_YAW, 0f);
        builder.define(DATA_IS_TRACKING, false);
    }

    @Override
    public void onTick() {
        super.onTick();
        if (isDestroyed() || !isActive()) {
            resetSignalOutputs();
            return;
        }

        // 以客户端注入的 lastAimPoint 为唯一真相源，直接输出
        if (lastAimPoint != null) {
            synchedData.set(DATA_AIM_PITCH, aimPitch);
            synchedData.set(DATA_AIM_YAW, aimYaw);
            synchedData.set(DATA_IS_TRACKING, isTracking);
            for (String channel : attr.aimOutputTargets.keySet()) {
                sendSignalToAllTargets(channel, new ViewInputSignal(lastAimPoint));
            }
        } else {
            resetSignalOutputs();
        }
    }

    /**
     * 接收来自 ViewInputPayload 服务端 handler 的客户端瞄准点。
     * 直接设为 lastAimPoint——客户端 CameraController 以世界空间坐标维护。
     */
    public void receiveClientAimInput(Vec3 aimPoint) {
        this.lastAimPoint = aimPoint;
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
                this.trackingTarget = vis.value;
                this.isTracking = true;
                return;
            }
        }
        this.trackingTarget = null;
        this.isTracking = false;
    }

    /**
     * TRACKING 模式下，从 trackingTarget 计算帧起始 aimPitch/aimYaw。
     */
    private void updatePitchYawFromTracking() {
        if (trackingTarget == null) return;
        Transform locator = getLocatorWorldTransform();
        aimPitch = computePitchToPoint(locator, trackingTarget);
        aimYaw = computeYawToPoint(locator, trackingTarget);
    }

    /**
     * 从 lastAimPoint 反算当前 aimPitch/aimYaw。
     */
    private void updatePitchYawFromAimPoint(Vec3 aimPoint) {
        if (aimPoint == null) return;
        Transform locator = getLocatorWorldTransform();
        aimPitch = computePitchToPoint(locator, aimPoint);
        aimYaw = computeYawToPoint(locator, aimPoint);
    }

    /**
     * 计算从 locator 到目标点的俯仰角（弧度）。
     */
    private float computePitchToPoint(Transform locator, Vec3 aimPoint) {
        Vector3f locatorPos = locator.getTranslation();
        Vector3f toTarget = PhysicsHelperKt.toBVector3f(aimPoint).subtract(locatorPos);
        Vector3f localDir = new Vector3f();
        locator.getRotation().inverse().toRotationMatrix().mult(toTarget, localDir);
        float dist = (float) Math.sqrt(
                localDir.x * localDir.x + localDir.y * localDir.y + localDir.z * localDir.z);
        if (dist < 0.001f) return this.aimPitch;
        return (float) Math.asin(Math.clamp(localDir.y / dist, -1.0, 1.0));
    }

    /**
     * 计算从 locator 到目标点的偏航角（弧度）。
     */
    private float computeYawToPoint(Transform locator, Vec3 aimPoint) {
        Vector3f locatorPos = locator.getTranslation();
        Vector3f toTarget = PhysicsHelperKt.toBVector3f(aimPoint).subtract(locatorPos);
        Vector3f localDir = new Vector3f();
        locator.getRotation().inverse().toRotationMatrix().mult(toTarget, localDir);
        return (float) Math.atan2(localDir.x, localDir.z);
    }

    @Override
    public void onAttach() {
        super.onAttach();
    }

    @Override
    public SignalResult onSignalUpdated(String channelName, ISignalSender sender) {
        super.onSignalUpdated(channelName, sender);
        return SignalResult.PASS;
    }

    @Override
    public List<String> getAcceptedChannels() {
        List<String> channels = new ArrayList<>();
        channels.addAll(attr.staticAttribute.getTrackingTargetInputs());
        channels.addAll(attr.staticAttribute.getDiscoveryInputs());
        return channels;
    }

    @Override
    public Map<String, List<String>> getTargetNames() {
        return new HashMap<>(attr.aimOutputTargets);
    }

    // ===== Locator 变换 =====

    /** 获取摄像机 locator 的世界空间位姿（当前帧，主线程用） */
    public Transform getLocatorWorldTransform() {
        return getOwner().getSubPart().getLocatorWorldTransform(attr.locator);
    }

    /** 获取插值后的世界位姿（渲染帧用） */
    public Transform getLerpedLocatorWorldTransform(float partialTick) {
        return getOwner().getSubPart().getLerpedLocatorWorldTransform(attr.locator, partialTick);
    }

    // ===== 持久化 =====

    @Override
    public CompoundTag saveData(CompoundTag data) {
        super.saveData(data);
        data.putFloat("aimPitch", this.aimPitch);
        data.putFloat("aimYaw", this.aimYaw);
        if (lastAimPoint != null) {
            data.putDouble("aimPointX", lastAimPoint.x);
            data.putDouble("aimPointY", lastAimPoint.y);
            data.putDouble("aimPointZ", lastAimPoint.z);
        }
        return data;
    }

    @Override
    public void loadData(CompoundTag data) {
        super.loadData(data);
        this.aimPitch = data.getFloat("aimPitch");
        this.aimYaw = data.getFloat("aimYaw");
        if (data.contains("aimPointX")) {
            this.lastAimPoint = new Vec3(
                    data.getDouble("aimPointX"),
                    data.getDouble("aimPointY"),
                    data.getDouble("aimPointZ"));
        }
    }
}
