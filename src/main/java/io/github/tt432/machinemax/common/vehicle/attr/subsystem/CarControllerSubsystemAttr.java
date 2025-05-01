package io.github.tt432.machinemax.common.vehicle.attr.subsystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.tt432.machinemax.common.vehicle.ISubsystemHost;
import io.github.tt432.machinemax.common.vehicle.subsystem.AbstractSubsystem;
import io.github.tt432.machinemax.common.vehicle.subsystem.CarControllerSubsystem;
import lombok.Getter;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
//TODO:渐进油门，根据油门开度调整换挡时机
@Getter
public class CarControllerSubsystemAttr extends AbstractSubsystemAttr {
    public final List<String> controlInputKeys;
    public final Vec3 steeringCenter;
    public final boolean manualGearShift;
    public final Map<String, List<String>> engineControlOutputTargets;//信号名和目标名称列表，下同 Signal keys and target hitBoxName list, etc.
    public final Map<String, List<String>> wheelControlOutputTargets;
    public final Map<String, List<String>> gearboxControlOutputTargets;

    public static final MapCodec<CarControllerSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("basic_durability", 100f).forGetter(AbstractSubsystemAttr::getBasicDurability),
            Codec.STRING.optionalFieldOf("hit_box", "").forGetter(AbstractSubsystemAttr::getHitBox),
            Codec.STRING.listOf().fieldOf("control_inputs").forGetter(CarControllerSubsystemAttr::getControlInputKeys),
            Vec3.CODEC.optionalFieldOf("steering_center", Vec3.ZERO).forGetter(CarControllerSubsystemAttr::getSteeringCenter),
            Codec.BOOL.optionalFieldOf("manual_gear_shift", false).forGetter(CarControllerSubsystemAttr::isManualGearShift),
            SIGNAL_TARGETS_CODEC.fieldOf("engine_outputs").forGetter(CarControllerSubsystemAttr::getEngineControlOutputTargets),
            SIGNAL_TARGETS_CODEC.fieldOf("wheel_outputs").forGetter(CarControllerSubsystemAttr::getWheelControlOutputTargets),
            SIGNAL_TARGETS_CODEC.fieldOf("gearbox_outputs").forGetter(CarControllerSubsystemAttr::getGearboxControlOutputTargets)
    ).apply(instance, CarControllerSubsystemAttr::new));

    public CarControllerSubsystemAttr(
            float basicDurability,
            String hitBox,
            List<String> controlInputKeys,
            Vec3 steeringCenter,
            boolean manualGearShift,
            Map<String, List<String>> engineControlOutputTargets,
            Map<String, List<String>> wheelControlOutputTargets,
            Map<String, List<String>> gearboxControlOutputTargets) {
        super(basicDurability, hitBox);
        this.controlInputKeys = controlInputKeys;
        this.steeringCenter = steeringCenter;
        this.manualGearShift = manualGearShift;
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

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new CarControllerSubsystem(owner, name, this);
    }
}
