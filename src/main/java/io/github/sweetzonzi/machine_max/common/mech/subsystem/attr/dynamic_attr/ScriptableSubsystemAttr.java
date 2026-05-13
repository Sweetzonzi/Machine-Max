package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.ScriptableSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ScriptableSubsystem;
import io.github.sweetzonzi.machine_max.external.js.hook.Hook;
import net.minecraft.resources.ResourceLocation;

public class ScriptableSubsystemAttr extends BasicSubsystemDynamicAttr {
    public final ScriptableSubsystemStaticAttr staticAttribute;
    public final String script;

    public ScriptableSubsystemAttr(ResourceLocation modelName, String script) {
        super(modelName);
        this.staticAttribute = (ScriptableSubsystemStaticAttr) getStaticAttr();
        this.script = script;
        Hook.run(this, modelName, script);
    }

    public static final MapCodec<ScriptableSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("definition").forGetter(AbstractSubsystemAttr::getModelName),
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
    public SubsystemTypes getType() {
        return SubsystemTypes.JAVASCRIPT;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        Hook.run(this, owner, name);
        return new ScriptableSubsystem(owner, name, this);
    }
}
