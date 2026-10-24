package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Transform;
import io.github.sweetzonzi.machine_max.common.mech.control.aim.AimPose;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.AbstractViewpointSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.visual.*;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * 实体视点的公共基类，提供挂点采样、光学、发现与 HUD 契约。<br>
 * getSubPart 确定挂载刚体，locator 的完整变换同时给出位置与旋转。
 * 固定炮镜直接使用该位姿，可转摄像头在其上合成本机观察角。<br>
 * 输入通过操作者的控制组发送；设备发现本身不赋予武器控制权。
 */
public abstract class AbstractViewpointSubsystem extends BasicSubsystem implements IHudHost {
    public final AbstractViewpointSubsystemAttr attr;

    protected AbstractViewpointSubsystem(ISubsystemHost owner, String name, AbstractViewpointSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
    }

    /** 主线程／渲染线程从已发布的刚体状态采样安装位姿，不读取 native 活体状态。 */
    public Transform getLerpedLocatorWorldTransform(float partialTick) {
        return getSubPart().getLerpedLocatorWorldTransform(attr.locator, partialTick);
    }

    /**
     * 主线程／渲染线程采样挂点的完整实际位姿。<br>
     * 子类如需观察转轴，应在该安装参考上合成，不拆分位置与朝向的来源。
     *
     * @param partialTick 逻辑刻内的插值比例
     */
    public AimPose samplePose(float partialTick) {
        Transform mount = getLerpedLocatorWorldTransform(partialTick);
        return new AimPose(SparkMathKt.toVec3(mount.getTranslation()), mount.getRotation());
    }

    @Override
    public List<String> getAcceptedChannels() {
        return attr.staticAttribute.getDiscoveryInputs();
    }

    @Override
    @Nullable
    public HudLayout hudLayoutFor(HudView view) {
        return IHudHost.resolve(view == HudView.SCOPE ? attr.staticAttribute.getScopeHudLayout() : HudLayout.EMPTY);
    }
}
