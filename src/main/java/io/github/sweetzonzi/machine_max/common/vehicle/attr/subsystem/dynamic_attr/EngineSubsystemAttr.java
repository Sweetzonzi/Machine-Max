package io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.dynamic_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.WorkingState;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.static_attr.EngineSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.EngineSubsystem;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

@Getter
public class EngineSubsystemAttr extends AbstractSubsystemAttr {
    public final EngineSubsystemStaticAttr staticAttribute;
    public final String powerOutputTarget;
    public final Map<String, List<String>> rpmOutputTargets;

    public static final Codec<Map<String, List<String>>> RPM_OUTPUT_TARGETS_CODEC = Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf());

    public static final MapCodec<EngineSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("model").forGetter(AbstractSubsystemAttr::getModelName),
            Codec.STRING.fieldOf("power_output").forGetter(EngineSubsystemAttr::getPowerOutputTarget),
            RPM_OUTPUT_TARGETS_CODEC.optionalFieldOf("speed_outputs", Map.of()).forGetter(EngineSubsystemAttr::getRpmOutputTargets)
    ).apply(instance, EngineSubsystemAttr::new));

    public EngineSubsystemAttr(
            ResourceLocation modelName,
            String powerOutputTarget,
            Map<String, List<String>> rpmOutputTargets) {
        super(modelName);
        this.staticAttribute = (EngineSubsystemStaticAttr) getStaticAttr();
        this.powerOutputTarget = powerOutputTarget;
        this.rpmOutputTargets = rpmOutputTargets;
    }

    public WorkingState getBestMatchWorkingState(double rpm, double load) {
        var staticAttr = getStaticAttribute();
        if (staticAttr instanceof EngineSubsystemStaticAttr staticEngineAttr) return staticEngineAttr.getBestMatchWorkingState(rpm, load);
        else return EngineSubsystemStaticAttr.EMPTY_WORKING_STATE;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.ENGINE;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new EngineSubsystem(owner, name, this);
    }
}
