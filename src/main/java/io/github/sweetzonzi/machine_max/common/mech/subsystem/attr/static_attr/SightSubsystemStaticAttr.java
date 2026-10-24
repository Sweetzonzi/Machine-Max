package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.visual.HudLayout;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** 固定炮镜配置；稳定标志控制武器期望目标的保持方式，设备没有独立观察转轴。 */
@Getter
public class SightSubsystemStaticAttr extends AbstractViewpointSubsystemStaticAttr {
    /** 俯仰目标使用世界点保持；false 时采用绝对相对角。 */
    public final boolean verticalStabilized;
    /** 偏航目标使用世界点保持；false 时采用绝对相对角。 */
    public final boolean horizontalStabilized;

    public static final MapCodec<SightSubsystemStaticAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BasicAttr.CODEC.forGetter(BasicSubsystemStaticAttr::getBasicAttr),
            BasicSoundAttr.CODEC.codec().optionalFieldOf("sounds", BasicSoundAttr.DEFAULT)
                    .forGetter(BasicSubsystemStaticAttr::getSoundAttr),
            Codec.FLOAT.optionalFieldOf("base_zoom", 1f)
                    .forGetter(SightSubsystemStaticAttr::getBaseZoom),
            Codec.FLOAT.optionalFieldOf("max_zoom", 1f)
                    .forGetter(SightSubsystemStaticAttr::getMaxZoom),
            ResourceLocation.CODEC.optionalFieldOf("scope_hud_layout", HudLayout.EMPTY)
                    .forGetter(SightSubsystemStaticAttr::getScopeHudLayout),
            Codec.STRING.listOf().optionalFieldOf("discovery_inputs", List.of("camera_sight"))
                    .forGetter(SightSubsystemStaticAttr::getDiscoveryInputs),
            Codec.BOOL.optionalFieldOf("allow_cycle", true)
                    .forGetter(SightSubsystemStaticAttr::isAllowCycle),
            Codec.BOOL.optionalFieldOf("vertical_stabilized", false)
                    .forGetter(SightSubsystemStaticAttr::isVerticalStabilized),
            Codec.BOOL.optionalFieldOf("horizontal_stabilized", false)
                    .forGetter(SightSubsystemStaticAttr::isHorizontalStabilized)
    ).apply(instance, SightSubsystemStaticAttr::new));

    public SightSubsystemStaticAttr(BasicAttr basic, BasicSoundAttr sounds,
            float baseZoom, float maxZoom, ResourceLocation hud, List<String> discovery, boolean cycle,
            boolean verticalStabilized, boolean horizontalStabilized) {
        super(basic, sounds, baseZoom, maxZoom, hud, discovery, cycle);
        this.verticalStabilized = verticalStabilized;
        this.horizontalStabilized = horizontalStabilized;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemStaticAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.SIGHT;
    }
}
