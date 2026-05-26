package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.SeatSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

@Getter
public class SeatSubsystemAttr extends BasicSubsystemDynamicAttr {
    public final SeatSubsystemStaticAttr staticAttribute;
    public final String locator;
    public final ResourceLocation controlGroupPreset;
    public final Map<String, List<String>> passengerNumSignalTargets;
    //TODO:是否无视命中情况转嫁乘客伤害到部件

    public static final MapCodec<SeatSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("definition").forGetter(AbstractSubsystemAttr::getModelName),
            Codec.STRING.optionalFieldOf("locator", "").forGetter(SeatSubsystemAttr::getLocator),
            ResourceLocation.CODEC.optionalFieldOf("control_group_preset", NO_CONTROL_GROUP_PRESET).forGetter(SeatSubsystemAttr::getControlGroupPreset),
            SIGNAL_TARGETS_CODEC.optionalFieldOf("passenger_num_outputs", Map.of()).forGetter(SeatSubsystemAttr::getPassengerNumSignalTargets)
    ).apply(instance, SeatSubsystemAttr::new));

    public SeatSubsystemAttr(
            ResourceLocation modelName,
            String locator,
            ResourceLocation controlGroupPreset,
            Map<String, List<String>> passengerNumSignalTargets) {
        super(modelName);
        this.staticAttribute = (SeatSubsystemStaticAttr) getStaticAttr();
        //合法性检查
        if (locator == null || locator.isEmpty())
            throw new IllegalStateException("error.machine_max.seat_subsystem.no_locator");
        this.locator = locator;
        this.controlGroupPreset = controlGroupPreset;
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
    public ResourceLocation getControlGroupPresetRl() {
        return controlGroupPreset;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new SeatSubsystem(owner, name, this);
    }

}
