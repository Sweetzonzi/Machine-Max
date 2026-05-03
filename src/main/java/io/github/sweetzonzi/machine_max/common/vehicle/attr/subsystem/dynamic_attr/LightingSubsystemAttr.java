package io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.dynamic_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.static_attr.LightingSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.LightingSubsystem;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

@Getter
public class LightingSubsystemAttr extends BasicSubsystemDynamicAttr {
    public final LightingSubsystemStaticAttr staticAttribute;
    public final String lightLocator;

    public static final MapCodec<LightingSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("definition").forGetter(AbstractSubsystemAttr::getModelName),
            Codec.STRING.optionalFieldOf("locator", "").forGetter(LightingSubsystemAttr::getLightLocator)
    ).apply(instance, LightingSubsystemAttr::new));

    public LightingSubsystemAttr(ResourceLocation modelName, String lightLocator) {
        super(modelName);
        this.staticAttribute = (LightingSubsystemStaticAttr) getStaticAttr();
        this.lightLocator = lightLocator == null ? "" : lightLocator;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.LIGHTING;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new LightingSubsystem(owner, name, this);
    }
}
