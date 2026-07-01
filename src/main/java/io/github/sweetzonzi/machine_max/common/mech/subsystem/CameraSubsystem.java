package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import com.jme3.math.Transform;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalChannel;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalResult;
import io.github.sweetzonzi.machine_max.common.mech.signal.ViewInputSignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalSender;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.CameraSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.CameraSubsystemStaticAttr;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 摄像机子系统：提供炮镜视角，作为纯视角数据提供者。<p>
 * 不自行发送信号 —— 座椅直发和炮镜模式的信号发送均由 AbstractControllableSubsystem 通过控制组统一处理。<br>
 * 仅保留 locator 变换查询和 TRACKING 模式跟踪目标读取。
 */
public class CameraSubsystem extends BasicSubsystem {
    public final CameraSubsystemAttr attr;

    /** TRACKING 模式下跟踪的外部目标世界坐标（从信号频道读取） */
    private volatile Vec3 trackingTarget = null;
    /** 当前是否处于 TRACKING 模式（由 trackingTarget 驱动，非静态属性） */
    private volatile boolean isTracking = false;

    // ===== SynchedEntityData keys（同步到客户端）=====
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
        return Collections.emptyMap();
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
}
