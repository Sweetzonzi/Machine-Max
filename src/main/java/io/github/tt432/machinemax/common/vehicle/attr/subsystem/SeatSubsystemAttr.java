package io.github.tt432.machinemax.common.vehicle.attr.subsystem;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Getter;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Getter
public class SeatSubsystemAttr extends AbstractSubsystemAttr {
    public final String subpart;
    public final String connector;
    public final Map<String, List<String>> moveSignalTargets;
    public final Map<String, List<String>> viewSignalTargets;
    public final Map<String, List<String>> regularSignalTargets;

    public static final MapCodec<SeatSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("sub_part").forGetter(SeatSubsystemAttr::getSubpart),
            Codec.STRING.fieldOf("connector").forGetter(SeatSubsystemAttr::getConnector),
            SIGNAL_TARGETS_CODEC.optionalFieldOf("move_outputs", Map.of()).forGetter(SeatSubsystemAttr::getMoveSignalTargets),
            SIGNAL_TARGETS_CODEC.optionalFieldOf("view_outputs", Map.of()).forGetter(SeatSubsystemAttr::getViewSignalTargets),
            SIGNAL_TARGETS_CODEC.optionalFieldOf("regular_outputs", Map.of()).forGetter(SeatSubsystemAttr::getRegularSignalTargets)
    ).apply(instance, SeatSubsystemAttr::new));

    public SeatSubsystemAttr(
            String subpart,
            String connector,
            Map<String, List<String>> moveSignalTargets,
            Map<String, List<String>> viewSignalTargets,
            Map<String, List<String>> regularSignalTargets) {
        super();
        this.subpart = subpart;
        this.connector = connector;
        this.moveSignalTargets = moveSignalTargets;
        this.viewSignalTargets = viewSignalTargets;
        this.regularSignalTargets = regularSignalTargets;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemType getType() {
        return SubsystemType.SEAT;
    }
}
