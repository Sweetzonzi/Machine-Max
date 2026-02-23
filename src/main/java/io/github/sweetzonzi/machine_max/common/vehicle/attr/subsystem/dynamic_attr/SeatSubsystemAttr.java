package io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.dynamic_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.static_attr.SeatSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.SeatSubsystem;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

@Getter
public class SeatSubsystemAttr extends BasicSubsystemDynamicAttr {
    public final SeatSubsystemStaticAttr staticAttribute;
    public final String locator;
    public final Map<String, List<String>> moveSignalTargets;
    public final Map<String, List<String>> viewSignalTargets;
    public final Map<String, List<String>> regularSignalTargets;
    public final Map<String, List<String>> passengerNumSignalTargets;
    //TODO:是否无视命中情况转嫁乘客伤害到部件

    public static final MapCodec<SeatSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("definition").forGetter(AbstractSubsystemAttr::getModelName),
            Codec.STRING.optionalFieldOf("seat_point_locator", "").forGetter(SeatSubsystemAttr::getLocator),
            SIGNAL_TARGETS_CODEC.optionalFieldOf("move_outputs", Map.of()).forGetter(SeatSubsystemAttr::getMoveSignalTargets),
            SIGNAL_TARGETS_CODEC.optionalFieldOf("aim_outputs", Map.of()).forGetter(SeatSubsystemAttr::getViewSignalTargets),
            SIGNAL_TARGETS_CODEC.optionalFieldOf("regular_outputs", Map.of()).forGetter(SeatSubsystemAttr::getRegularSignalTargets),
            SIGNAL_TARGETS_CODEC.optionalFieldOf("passenger_num_outputs", Map.of()).forGetter(SeatSubsystemAttr::getPassengerNumSignalTargets)
    ).apply(instance, SeatSubsystemAttr::new));

    public SeatSubsystemAttr(
            ResourceLocation modelName,
            String locator,
            Map<String, List<String>> moveSignalTargets,
            Map<String, List<String>> viewSignalTargets,
            Map<String, List<String>> regularSignalTargets,
            Map<String, List<String>> passengerNumSignalTargets) {
        super(modelName);
        this.staticAttribute = (SeatSubsystemStaticAttr) getStaticAttr();
        //合法性检查
        if (locator == null || locator.isEmpty())
            throw new IllegalStateException("error.machine_max.seat_subsystem.no_locator");
        this.locator = locator;
        this.moveSignalTargets = moveSignalTargets;
        this.viewSignalTargets = viewSignalTargets;
        this.regularSignalTargets = regularSignalTargets;
        this.passengerNumSignalTargets = passengerNumSignalTargets;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.SEAT;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new SeatSubsystem(owner, name, this);
    }

}

