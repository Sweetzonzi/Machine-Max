package io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.dynamic_attr;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.static_attr.BatterySubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.BatterySubsystem;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

/**
 * 电池子系统动态属性——引用静态属性中的电池型号
 */
@Getter
public class BatterySubsystemAttr extends BasicSubsystemDynamicAttr {
    public final BatterySubsystemStaticAttr staticAttribute;

    public static final MapCodec<BatterySubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("definition").forGetter(AbstractSubsystemAttr::getModelName)
    ).apply(instance, BatterySubsystemAttr::new));

    public BatterySubsystemAttr(ResourceLocation modelName) {
        super(modelName);
        this.staticAttribute = (BatterySubsystemStaticAttr) getStaticAttr();
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.BATTERY;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new BatterySubsystem(owner, name, this);
    }
}
