package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.CameraSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.CameraSubsystem;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

@Getter
public class CameraSubsystemAttr extends BasicSubsystemDynamicAttr {
    public final CameraSubsystemStaticAttr staticAttribute;
    public static final MapCodec<CameraSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("definition").forGetter(AbstractSubsystemAttr::getModelName)
    ).apply(instance, CameraSubsystemAttr::new));

    protected CameraSubsystemAttr(ResourceLocation modelName) {
        super(modelName);
        this.staticAttribute = (CameraSubsystemStaticAttr) getStaticAttr();
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
