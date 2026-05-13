package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import io.github.sweetzonzi.machine_max.external.js.hook.Hook;

public class ScriptableSubsystemStaticAttr extends BasicSubsystemStaticAttr {
    public final String script;

    public ScriptableSubsystemStaticAttr(
            BasicSubsystemStaticAttr.BasicAttr basicAttr, String script, BasicSoundAttr sounds) {
        super(basicAttr, sounds);
        this.script = script;
        Hook.run(this, basicDurability, script);
    }

    public static final MapCodec<ScriptableSubsystemStaticAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BasicAttr.CODEC.forGetter(BasicSubsystemStaticAttr::getBasicAttr),
            Codec.STRING.optionalFieldOf("script", "").forGetter(ScriptableSubsystemStaticAttr::getScript),
            BasicSoundAttr.CODEC.codec().optionalFieldOf("sounds", BasicSoundAttr.DEFAULT).forGetter(BasicSubsystemStaticAttr::getSoundAttr)
    ).apply(instance, ScriptableSubsystemStaticAttr::new));

    private String getScript() {
        return script;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemStaticAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.JAVASCRIPT;
    }

}
