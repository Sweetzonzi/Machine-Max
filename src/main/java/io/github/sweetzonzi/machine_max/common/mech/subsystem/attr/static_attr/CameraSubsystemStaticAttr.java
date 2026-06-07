package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * 摄像机子系统静态属性：定义摄像机光学与稳定特性的不可变配置。
 * 内容包括视场角、变焦范围、俯仰/偏航限制、稳定模式、HUD 组件等。
 */
@Getter
public class CameraSubsystemStaticAttr extends BasicSubsystemStaticAttr {

    /** 垂直方向是否世界稳定（补偿车体俯仰晃动） */
    public final boolean verticalStabilized;

    /** 水平方向是否世界稳定（补偿车体偏航晃动） */
    public final boolean horizontalStabilized;

    /** 参考视场角（度），固定 70° 作为 1× 基准。所有炮镜缩放均基于此计算：实际FOV = REFERENCE_FOV / currentZoom */
    public static final float REFERENCE_FOV = 70f;

    /** 最小变焦倍率（默认 1.0，即基准视野） */
    public final float baseZoom;

    /** 最大变焦倍率（例如 8.0 表示 8×） */
    public final float maxZoom;

    /** 最小俯仰角限制（度），正数=抬头，负数=低头 */
    public final float minPitch;

    /** 最大俯仰角限制（度） */
    public final float maxPitch;

    /** 偏航角张角限制（度），实际范围 [center - limit/2, center + limit/2] */
    public final float yawLimit;

    /** 摄像机 HUD 组件列表（RL 指向 hud/ 下的 GuiAnimatable 定义） */
    public final List<ResourceLocation> hudComponents;

    /** 跟踪目标的输入频道列表 */
    public final List<String> trackingTargetInputs;

    /** 摄像机被发现的握手频道列表（顺序=优先级） */
    public final List<String> discoveryInputs;

    /** 是否允许在 F5 视角循环中出现 */
    public final boolean allowCycle;

    public static final MapCodec<CameraSubsystemStaticAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
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
    ).apply(instance, CameraSubsystemStaticAttr::new));

    public CameraSubsystemStaticAttr(
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
        super(basicAttr, soundAttr);
        this.verticalStabilized = verticalStabilized;
        this.horizontalStabilized = horizontalStabilized;
        this.baseZoom = baseZoom;
        this.maxZoom = maxZoom;
        this.minPitch = minPitch;
        this.maxPitch = maxPitch;
        this.yawLimit = yawLimit;
        this.hudComponents = hudComponents;
        this.trackingTargetInputs = trackingTargetInputs;
        this.discoveryInputs = discoveryInputs;
        this.allowCycle = allowCycle;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemStaticAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.CAMERA;
    }
}
