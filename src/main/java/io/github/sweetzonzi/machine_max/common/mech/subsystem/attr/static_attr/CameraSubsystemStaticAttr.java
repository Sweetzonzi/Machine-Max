package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.visual.HudLayout;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** 可转摄像头配置；角度与行程使用度，转速使用度／秒，稳定标志控制镜头自身的观察目标。 */
@Getter
public class CameraSubsystemStaticAttr extends AbstractViewpointSubsystemStaticAttr {
    /** 观察俯仰保持世界参考，不改变 WC 的控制模式。 */
    public final boolean verticalStabilized;
    /** 观察偏航保持世界参考；关闭时跟随安装参考。 */
    public final boolean horizontalStabilized;
    /** 本机观察俯仰下限，单位为度。 */
    public final float minPitch;
    /** 本机观察俯仰上限，单位为度。 */
    public final float maxPitch;
    /** 偏航总行程，单位为度；达到 360 表示连续回转。 */
    public final float yawLimit;
    /** 实际俯仰追赶期望角的最大转速，单位为度／秒。 */
    public final float pitchSpeed;
    /** 实际偏航追赶期望角的最大转速，单位为度／秒。 */
    public final float yawSpeed;
    /** 外部跟踪目标输入频道，按声明顺序读取有效世界点。 */
    public final List<String> trackingTargetInputs;

    public static final MapCodec<CameraSubsystemStaticAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BasicAttr.CODEC.forGetter(BasicSubsystemStaticAttr::getBasicAttr),
            BasicSoundAttr.CODEC.codec().optionalFieldOf("sounds", BasicSoundAttr.DEFAULT)
                    .forGetter(BasicSubsystemStaticAttr::getSoundAttr),
            Codec.FLOAT.optionalFieldOf("base_zoom", 1f)
                    .forGetter(CameraSubsystemStaticAttr::getBaseZoom),
            Codec.FLOAT.optionalFieldOf("max_zoom", 1f)
                    .forGetter(CameraSubsystemStaticAttr::getMaxZoom),
            ResourceLocation.CODEC.optionalFieldOf("scope_hud_layout", HudLayout.EMPTY)
                    .forGetter(CameraSubsystemStaticAttr::getScopeHudLayout),
            Codec.STRING.listOf().optionalFieldOf("discovery_inputs", List.of("camera_sight"))
                    .forGetter(CameraSubsystemStaticAttr::getDiscoveryInputs),
            Codec.BOOL.optionalFieldOf("allow_cycle", true)
                    .forGetter(CameraSubsystemStaticAttr::isAllowCycle),
            Codec.BOOL.optionalFieldOf("vertical_stabilized", false)
                    .forGetter(CameraSubsystemStaticAttr::isVerticalStabilized),
            Codec.BOOL.optionalFieldOf("horizontal_stabilized", false)
                    .forGetter(CameraSubsystemStaticAttr::isHorizontalStabilized),
            Codec.FLOAT.optionalFieldOf("min_pitch", -10f)
                    .forGetter(CameraSubsystemStaticAttr::getMinPitch),
            Codec.FLOAT.optionalFieldOf("max_pitch", 10f)
                    .forGetter(CameraSubsystemStaticAttr::getMaxPitch),
            Codec.FLOAT.optionalFieldOf("yaw_limit", 5f)
                    .forGetter(CameraSubsystemStaticAttr::getYawLimit),
            Codec.FLOAT.optionalFieldOf("pitch_speed", 90f)
                    .forGetter(CameraSubsystemStaticAttr::getPitchSpeed),
            Codec.FLOAT.optionalFieldOf("yaw_speed", 90f)
                    .forGetter(CameraSubsystemStaticAttr::getYawSpeed),
            Codec.STRING.listOf().optionalFieldOf("tracking_target_inputs", List.of("tracking_target"))
                    .forGetter(CameraSubsystemStaticAttr::getTrackingTargetInputs)
    ).apply(instance, CameraSubsystemStaticAttr::new));

    public CameraSubsystemStaticAttr(BasicAttr basic, BasicSoundAttr sounds,
                                      float baseZoom, float maxZoom, ResourceLocation hud,
                                      List<String> discovery, boolean cycle,
                                      boolean verticalStabilized, boolean horizontalStabilized,
                                      float minPitch, float maxPitch, float yawLimit,
                                      float pitchSpeed, float yawSpeed, List<String> trackingTargetInputs) {
        super(basic, sounds, baseZoom, maxZoom, hud, discovery, cycle);
        this.verticalStabilized = verticalStabilized;
        this.horizontalStabilized = horizontalStabilized;
        this.minPitch = minPitch;
        this.maxPitch = maxPitch;
        this.yawLimit = yawLimit;
        this.pitchSpeed = pitchSpeed;
        this.yawSpeed = yawSpeed;
        this.trackingTargetInputs = trackingTargetInputs;
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
