package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr;

import io.github.sweetzonzi.machine_max.common.visual.HudLayout;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** 实体视点的公共光学与发现配置；固定式与可转式各自定义运动能力。 */
@Getter
public abstract class AbstractViewpointSubsystemStaticAttr extends BasicSubsystemStaticAttr {
    /** 变焦倍率的参考垂直视场角，单位为度。 */
    public static final float REFERENCE_FOV = 70f;
    /** 设备切入时的基础倍率。 */
    public final float baseZoom;
    /** 设备允许的最大倍率。 */
    public final float maxZoom;
    /** 炮镜视角 HUD 的资源地址，HudLayout.EMPTY 表示无布局。 */
    public final ResourceLocation scopeHudLayout;
    /** 设备接受的发现频道；操作者通过握手把设备加入候选列表。 */
    public final List<String> discoveryInputs;
    /** 是否允许通过切换键选择此设备。 */
    public final boolean allowCycle;

    protected AbstractViewpointSubsystemStaticAttr(BasicAttr basic, BasicSoundAttr sounds,
            float baseZoom, float maxZoom, ResourceLocation hud, List<String> discovery, boolean cycle) {
        super(basic, sounds);
        this.baseZoom = baseZoom;
        this.maxZoom = maxZoom;
        this.scopeHudLayout = hud;
        this.discoveryInputs = List.copyOf(discovery);
        this.allowCycle = cycle;
    }
}
