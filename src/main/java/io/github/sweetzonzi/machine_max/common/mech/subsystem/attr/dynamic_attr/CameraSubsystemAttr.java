package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.CameraSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.CameraSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * 摄像机子系统动态属性：定义摄像机在载具上的挂载位置、瞄准点输出目标等实例级配置。
 */
@Getter
public class CameraSubsystemAttr extends BasicSubsystemDynamicAttr {
    public final CameraSubsystemStaticAttr staticAttribute;

    /** 摄像机挂载 locator 名称 */
    public final String locator;

    /** 瞄准点输出频道 → 目标接收者名列表 */
    public final Map<String, List<String>> aimOutputTargets;

    public static final MapCodec<CameraSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("definition")
                    .forGetter(AbstractSubsystemAttr::getModelName),
            Codec.STRING.optionalFieldOf("locator", "")
                    .forGetter(CameraSubsystemAttr::getLocator),
            AbstractSubsystemAttr.SIGNAL_TARGETS_CODEC
                    .optionalFieldOf("aim_output_targets", Map.of())
                    .forGetter(CameraSubsystemAttr::getAimOutputTargets)
    ).apply(instance, CameraSubsystemAttr::new));

    public CameraSubsystemAttr(
            ResourceLocation modelName,
            String locator,
            Map<String, List<String>> aimOutputTargets) {
        super(modelName);
        this.staticAttribute = (CameraSubsystemStaticAttr) getStaticAttr();
        this.locator = locator;
        this.aimOutputTargets = aimOutputTargets;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.CAMERA;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new CameraSubsystem(owner, name, this);
    }
}
