package io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.ScriptableSubsystem;
import io.github.sweetzonzi.machine_max.external.js.hook.Hook;

public class ScriptableSubsystemAttr extends AbstractSubsystemAttr {
    public final String script;

    public ScriptableSubsystemAttr(float basicDurability,  String script) {
        super(basicDurability);
        this.script = script;
        Hook.run(this, basicDurability, script);
    }

    public static final MapCodec<ScriptableSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("basic_durability", 20f).forGetter(AbstractSubsystemAttr::getBasicDurability),
            Codec.STRING.optionalFieldOf("script", "").forGetter(ScriptableSubsystemAttr::getScript)
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
        return new ScriptableSubsystem(owner, name, this);
    }
}
