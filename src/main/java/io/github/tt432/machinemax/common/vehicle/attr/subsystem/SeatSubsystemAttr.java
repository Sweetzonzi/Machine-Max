package io.github.tt432.machinemax.common.vehicle.attr.subsystem;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Getter;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

@Getter
public class SeatSubsystemAttr extends AbstractSubsystemAttr {
    public final String subpart;
    public final String connector;
    public final List<String> doorStatusSignalKeys;
    @Nullable
    public final Pair<String, List<String>> moveSignalTargets;
    @Nullable
    public final Pair<String, List<String>> regularSignalTargets;

    public static final MapCodec<SeatSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("sub_part").forGetter(SeatSubsystemAttr::getSubpart),
            Codec.STRING.fieldOf("connector").forGetter(SeatSubsystemAttr::getConnector),
            Codec.STRING.listOf().optionalFieldOf("door_status_input", List.of()).forGetter(SeatSubsystemAttr::getDoorStatusSignalKeys),
            TARGET_NAMES_CODEC.optionalFieldOf("move_output").forGetter(t -> Optional.ofNullable(t.moveSignalTargets)),
            TARGET_NAMES_CODEC.optionalFieldOf("regular_output").forGetter(t -> Optional.ofNullable(t.regularSignalTargets))
    ).apply(instance, (subpart, connector, doorStatusSignalKeys, moveSignalTargets, regularSignalTargets) -> new SeatSubsystemAttr(
            subpart,
            connector,
            doorStatusSignalKeys,
            moveSignalTargets.orElse(null),
            regularSignalTargets.orElse(null)
    )));

    public SeatSubsystemAttr(
            String subpart,
            String connector,
            List<String> doorStatusSignalKeys,
            @Nullable Pair<String, List<String>> moveSignalTargets,
            @Nullable Pair<String, List<String>> regularSignalTargets) {
        super();
        this.subpart = subpart;
        this.connector = connector;
        this.doorStatusSignalKeys = doorStatusSignalKeys;
        this.moveSignalTargets = moveSignalTargets;
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
