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

/**
 * 装弹机子系统动态属性。<br>
 * TODO: 后续可扩展弹药储备槽位、弹序配置、弹药计数等运行时状态。
 */
@Getter
public class AmmoLoaderSubsystemAttr extends BasicSubsystemDynamicAttr {
    public final AmmoLoaderSubsystemStaticAttr staticAttribute;
    public static final MapCodec<AmmoLoaderSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("definition").forGetter(AbstractSubsystemAttr::getModelName)
    ).apply(instance, AmmoLoaderSubsystemAttr::new));

    public AmmoLoaderSubsystemAttr(ResourceLocation modelName) {
        super(modelName);
        this.staticAttribute = (AmmoLoaderSubsystemStaticAttr) getStaticAttr();
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
