package io.github.sweetzonzi.machinemax.common.vehicle.attr.subsystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machinemax.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.ScriptableSubsystem;
import io.github.sweetzonzi.machinemax.external.js.hook.Hook;

public class ScriptableSubsystemAttr extends AbstractSubsystemAttr {
    public final String script;
    public ScriptableSubsystemAttr(float basicDurability, String hitBox, String script) {
        super(basicDurability, hitBox);
        this.script = script;
        Hook.run(this, basicDurability, hitBox, script);
    }
    public static final MapCodec<ScriptableSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("basic_durability", 100f).forGetter(AbstractSubsystemAttr::getBasicDurability),
            Codec.STRING.optionalFieldOf("hit_box", "").forGetter(AbstractSubsystemAttr::getHitBox),
            Codec.STRING.fieldOf("script").forGetter(ScriptableSubsystemAttr::getScript)
    ).apply(instance, ScriptableSubsystemAttr::new));

    private String getScript() {
        return script;
    }

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
        return new ScriptableSubsystem(owner, name, this, script);
    }
}
