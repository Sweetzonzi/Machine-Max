package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.AmmoLoaderSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AmmoLoaderSubsystem;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Map;

/**
 * 装弹机子系统动态属性。<br>
 * 定义弹药发现频道等运行时配置。
 * 弹药通过 {@code IAmmoSupplier/IAmmoConsumer} 直接接口通信，不经信号频道。
 * <p>
 * <b>WIP：</b>装弹机整体尚未定稿，字段与行为可能调整（见 {@link AmmoLoaderSubsystem}）。
 */
@ApiStatus.Experimental
@Getter
public class AmmoLoaderSubsystemAttr extends BasicSubsystemDynamicAttr {
    public final AmmoLoaderSubsystemStaticAttr staticAttribute;

    /** 弹药发现频道 → 目标名称列表（用于自动发现同载具内的 IAmmoConsumer） */
    public final Map<String, List<String>> discoveryOutputs;

    public static final MapCodec<AmmoLoaderSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("definition").forGetter(AbstractSubsystemAttr::getModelName),
            AbstractSubsystemAttr.SIGNAL_TARGETS_CODEC.optionalFieldOf("discovery_outputs", Map.of())
                .forGetter(AmmoLoaderSubsystemAttr::getDiscoveryOutputs)
    ).apply(instance, AmmoLoaderSubsystemAttr::new));

    public AmmoLoaderSubsystemAttr(
            ResourceLocation modelName,
            Map<String, List<String>> discoveryOutputs) {
        super(modelName);
        this.staticAttribute = (AmmoLoaderSubsystemStaticAttr) getStaticAttr();
        this.discoveryOutputs = discoveryOutputs;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.AMMO_LOADER;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new AmmoLoaderSubsystem(owner, name, this);
    }
}
