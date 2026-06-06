package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SightSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.SightSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * 瞄准镜子系统动态属性：定义炮镜在载具上的挂载位置、瞄准点输出目标等实例级配置。
 * 目前字段与 CameraSubsystemAttr 一致。
 */
@Getter
public class SightSubsystemAttr extends CameraSubsystemAttr {
    public final SightSubsystemStaticAttr staticAttribute;

    public static final MapCodec<SightSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("definition")
                    .forGetter(AbstractSubsystemAttr::getModelName),
            Codec.STRING.optionalFieldOf("locator", "")
                    .forGetter(SightSubsystemAttr::getLocator),
            AbstractSubsystemAttr.SIGNAL_TARGETS_CODEC
                    .optionalFieldOf("aim_output_targets", Map.of())
                    .forGetter(SightSubsystemAttr::getAimOutputTargets)
    ).apply(instance, SightSubsystemAttr::new));

    public SightSubsystemAttr(
            ResourceLocation modelName,
            String locator,
            Map<String, List<String>> aimOutputTargets) {
        super(modelName, locator, aimOutputTargets);
        this.staticAttribute = (SightSubsystemStaticAttr) getStaticAttr();
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.SIGHT;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new SightSubsystem(owner, name, this);
    }
}
