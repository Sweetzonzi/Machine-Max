package io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.dynamic_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.WorkingState;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.static_attr.MotorSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.MotorSubsystem;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

@Getter
public class MotorSubsystemAttr extends BasicSubsystemDynamicAttr {
    public final MotorSubsystemStaticAttr staticAttribute;
    public final String powerOutputTarget;
    public final Map<String, List<String>> rpmOutputTargets;

    public static final Codec<Map<String, List<String>>> RPM_OUTPUT_TARGETS_CODEC = Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf());

    public static final MapCodec<MotorSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("definition").forGetter(AbstractSubsystemAttr::getModelName),
            Codec.STRING.fieldOf("power_output").forGetter(MotorSubsystemAttr::getPowerOutputTarget),
            RPM_OUTPUT_TARGETS_CODEC.optionalFieldOf("speed_outputs", Map.of("engine_speed", List.of("vehicle", "part"))).forGetter(MotorSubsystemAttr::getRpmOutputTargets)
    ).apply(instance, MotorSubsystemAttr::new));

    public MotorSubsystemAttr(
            ResourceLocation modelName,
            String powerOutputTarget,
            Map<String, List<String>> rpmOutputTargets) {
        super(modelName);
        this.staticAttribute = (MotorSubsystemStaticAttr) getStaticAttr();
        this.powerOutputTarget = powerOutputTarget;
        this.rpmOutputTargets = rpmOutputTargets;
    }

    public WorkingState getBestMatchWorkingState(double rpm, double load) {
        var staticAttr = getStaticAttribute();
        if (staticAttr instanceof MotorSubsystemStaticAttr staticMotorAttr) return staticMotorAttr.getBestMatchWorkingState(rpm, load);
        else return MotorSubsystemStaticAttr.EMPTY_WORKING_STATE;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.MOTOR;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new MotorSubsystem(owner, name, this);
    }
}