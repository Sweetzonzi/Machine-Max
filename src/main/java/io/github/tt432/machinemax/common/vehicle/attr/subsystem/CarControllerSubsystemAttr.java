package io.github.tt432.machinemax.common.vehicle.attr.subsystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
public class CarControllerSubsystemAttr extends AbstractSubsystemAttr {
    public final List<String> controlInputKeys;
    public final Map<String, List<String>> engineControlOutputTargets;//信号名和目标名称列表，下同 Signal keys and target name list, etc.
    public final Map<String, List<String>> wheelControlOutputTargets;
    public final Map<String, List<String>> gearboxControlOutputTargets;

    public static final Codec<Map<String, List<String>>> TARGET_NAMES_CODEC = Codec.unboundedMap(
            Codec.STRING,
            Codec.STRING.listOf()
    );

    public static final MapCodec<CarControllerSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.listOf().fieldOf("control_inputs").forGetter(CarControllerSubsystemAttr::getControlInputKeys),
            TARGET_NAMES_CODEC.fieldOf("engine_outputs").forGetter(CarControllerSubsystemAttr::getEngineControlOutputTargets),
            TARGET_NAMES_CODEC.fieldOf("wheel_outputs").forGetter(CarControllerSubsystemAttr::getWheelControlOutputTargets),
            TARGET_NAMES_CODEC.fieldOf("gearbox_outputs").forGetter(CarControllerSubsystemAttr::getGearboxControlOutputTargets)
    ).apply(instance, CarControllerSubsystemAttr::new));

    public CarControllerSubsystemAttr(List<String> controlInputKeys, Map<String, List<String>> engineControlOutputTargets, Map<String, List<String>> wheelControlOutputTargets, Map<String, List<String>> gearboxControlOutputTargets) {
        this.controlInputKeys = controlInputKeys;
        this.engineControlOutputTargets = engineControlOutputTargets;
        this.wheelControlOutputTargets = wheelControlOutputTargets;
        this.gearboxControlOutputTargets = gearboxControlOutputTargets;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemType getType() {
        return SubsystemType.CAR_CTRL;
    }
}
