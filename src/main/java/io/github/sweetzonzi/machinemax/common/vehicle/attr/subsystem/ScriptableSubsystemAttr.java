package io.github.sweetzonzi.machinemax.common.vehicle.attr.subsystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machinemax.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machinemax.common.vehicle.attr.MotorAttr;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.CarControllerSubsystem;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.ScriptableSubsystem;
import io.github.sweetzonzi.machinemax.external.js.hook.Hook;

public class ScriptableSubsystemAttr extends AbstractSubsystemAttr {
    public ScriptableSubsystemAttr(float basicDurability, String hitBox) {
        super(basicDurability, hitBox);
        Hook.run(this, basicDurability, hitBox);
    }
    public static final MapCodec<ScriptableSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("basic_durability", 100f).forGetter(AbstractSubsystemAttr::getBasicDurability),
            Codec.STRING.optionalFieldOf("hit_box", "").forGetter(AbstractSubsystemAttr::getHitBox)
    ).apply(instance, ScriptableSubsystemAttr::new));

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemType getType() {
        return SubsystemType.JAVASCRIPT;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        Hook.run(this, owner, name);
        return new ScriptableSubsystem(owner, name, this);
    }
}
