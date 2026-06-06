package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.RegenLoaderSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.RegenLoaderSubsystem;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * 再生装弹机子系统动态属性。<br>
 * 定义弹药发现频道等运行时配置。
 */
@Getter
public class RegenLoaderSubsystemAttr extends BasicSubsystemDynamicAttr {
    public final RegenLoaderSubsystemStaticAttr staticAttribute;

    /** 弹药发现频道 → 目标名称列表（用于自动发现同载具内的 IAmmoConsumer） */
    public final Map<String, List<String>> discoveryOutputs;

    public static final MapCodec<RegenLoaderSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("definition").forGetter(AbstractSubsystemAttr::getModelName),
            AbstractSubsystemAttr.SIGNAL_TARGETS_CODEC.optionalFieldOf("discovery_outputs", Map.of())
                .forGetter(RegenLoaderSubsystemAttr::getDiscoveryOutputs)
    ).apply(instance, RegenLoaderSubsystemAttr::new));

    public RegenLoaderSubsystemAttr(
            ResourceLocation modelName,
            Map<String, List<String>> discoveryOutputs) {
        super(modelName);
        this.staticAttribute = (RegenLoaderSubsystemStaticAttr) getStaticAttr();
        this.discoveryOutputs = discoveryOutputs;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.REGEN_LOADER;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new RegenLoaderSubsystem(owner, name, this);
    }
}
