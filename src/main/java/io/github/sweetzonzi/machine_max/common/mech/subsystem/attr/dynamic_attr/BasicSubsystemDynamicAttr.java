package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.BasicSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.BasicSubsystem;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

@Getter
public class BasicSubsystemDynamicAttr extends AbstractSubsystemAttr {
    public final BasicSubsystemStaticAttr staticAttribute;
    public static final MapCodec<BasicSubsystemDynamicAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("definition").forGetter(AbstractSubsystemAttr::getModelName)
    ).apply(instance, BasicSubsystemDynamicAttr::new));

    public BasicSubsystemDynamicAttr(ResourceLocation modelName) {
        super(modelName);
        this.staticAttribute = (BasicSubsystemStaticAttr) getStaticAttr();
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.BASIC;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new BasicSubsystem(owner, name, this);
    }

    public boolean isHidden() {
        return staticAttribute.getBasicAttr().hidden();
    }
}