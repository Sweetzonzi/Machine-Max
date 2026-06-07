package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * 瞄准镜子系统静态属性：定义炮镜光学与稳定特性的不可变配置。
 * 目前字段与 CameraSubsystemStaticAttr 一致，未来可扩展标尺类型/分划板样式等。
 */
@Getter
public class SightSubsystemStaticAttr extends CameraSubsystemStaticAttr {

    public static final MapCodec<SightSubsystemStaticAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BasicAttr.CODEC.forGetter(BasicSubsystemStaticAttr::getBasicAttr),
            BasicSoundAttr.CODEC.codec()
                    .optionalFieldOf("sounds", BasicSoundAttr.DEFAULT)
                    .forGetter(BasicSubsystemStaticAttr::getSoundAttr),
            Codec.BOOL.optionalFieldOf("vertical_stabilized", false)
                    .forGetter(CameraSubsystemStaticAttr::isVerticalStabilized),
            Codec.BOOL.optionalFieldOf("horizontal_stabilized", false)
                    .forGetter(CameraSubsystemStaticAttr::isHorizontalStabilized),
            Codec.FLOAT.optionalFieldOf("base_zoom", 1.0f)
                    .forGetter(CameraSubsystemStaticAttr::getBaseZoom),
            Codec.FLOAT.optionalFieldOf("max_zoom", 1.0f)
                    .forGetter(CameraSubsystemStaticAttr::getMaxZoom),
            Codec.FLOAT.optionalFieldOf("min_pitch", -10.0f)
                    .forGetter(CameraSubsystemStaticAttr::getMinPitch),
            Codec.FLOAT.optionalFieldOf("max_pitch", 10.0f)
                    .forGetter(CameraSubsystemStaticAttr::getMaxPitch),
            Codec.FLOAT.optionalFieldOf("yaw_limit", 5.0f)
                    .forGetter(CameraSubsystemStaticAttr::getYawLimit),
            ResourceLocation.CODEC.listOf().optionalFieldOf("hud_components", List.of())
                    .forGetter(CameraSubsystemStaticAttr::getHudComponents),
            Codec.STRING.listOf()
                    .optionalFieldOf("tracking_target_inputs", List.of("tracking_target"))
                    .forGetter(CameraSubsystemStaticAttr::getTrackingTargetInputs),
            Codec.STRING.listOf()
                    .optionalFieldOf("discovery_inputs", List.of("camera_sight"))
                    .forGetter(CameraSubsystemStaticAttr::getDiscoveryInputs),
            Codec.BOOL.optionalFieldOf("allow_cycle", true)
                    .forGetter(CameraSubsystemStaticAttr::isAllowCycle)
    ).apply(instance, SightSubsystemStaticAttr::new));

    public SightSubsystemStaticAttr(
            BasicAttr basicAttr,
            BasicSoundAttr soundAttr,
            boolean verticalStabilized,
            boolean horizontalStabilized,
            float baseZoom,
            float maxZoom,
            float minPitch,
            float maxPitch,
            float yawLimit,
            List<ResourceLocation> hudComponents,
            List<String> trackingTargetInputs,
            List<String> discoveryInputs,
            boolean allowCycle
    ) {
        super(basicAttr, soundAttr, verticalStabilized, horizontalStabilized,
                baseZoom, maxZoom, minPitch, maxPitch, yawLimit,
                hudComponents, trackingTargetInputs, discoveryInputs, allowCycle);
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
