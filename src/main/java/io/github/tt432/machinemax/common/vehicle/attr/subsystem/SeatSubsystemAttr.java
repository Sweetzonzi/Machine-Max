package io.github.tt432.machinemax.common.vehicle.attr.subsystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.tt432.machinemax.common.vehicle.ISubsystemHost;
import io.github.tt432.machinemax.common.vehicle.subsystem.AbstractSubsystem;
import io.github.tt432.machinemax.common.vehicle.subsystem.SeatSubsystem;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
public class SeatSubsystemAttr extends AbstractSubsystemAttr {
    public final String subpart;
    public final String connector;
    public final boolean renderPassenger;
    public final boolean allowUseItems;
    public final Map<String, List<String>> moveSignalTargets;
    public final Map<String, List<String>> viewSignalTargets;
    public final Map<String, List<String>> regularSignalTargets;

    public static final MapCodec<SeatSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("basic_durability", 100f).forGetter(AbstractSubsystemAttr::getBasicDurability),
            Codec.STRING.optionalFieldOf("hit_box", "").forGetter(AbstractSubsystemAttr::getHitBox),
            Codec.STRING.fieldOf("sub_part").forGetter(SeatSubsystemAttr::getSubpart),
            Codec.STRING.fieldOf("connector").forGetter(SeatSubsystemAttr::getConnector),
            Codec.BOOL.optionalFieldOf("render_passenger", true).forGetter(SeatSubsystemAttr::isRenderPassenger),
            Codec.BOOL.fieldOf("allow_use_items").forGetter(SeatSubsystemAttr::isAllowUseItems),
            SIGNAL_TARGETS_CODEC.optionalFieldOf("move_outputs", Map.of()).forGetter(SeatSubsystemAttr::getMoveSignalTargets),
            SIGNAL_TARGETS_CODEC.optionalFieldOf("view_outputs", Map.of()).forGetter(SeatSubsystemAttr::getViewSignalTargets),
            SIGNAL_TARGETS_CODEC.optionalFieldOf("regular_outputs", Map.of()).forGetter(SeatSubsystemAttr::getRegularSignalTargets)
    ).apply(instance, SeatSubsystemAttr::new));

    public SeatSubsystemAttr(
            float basicDurability,
            String hitBox,
            String subpart,
            String connector,
            boolean renderPassenger,
            boolean allowUseItems,
            Map<String, List<String>> moveSignalTargets,
            Map<String, List<String>> viewSignalTargets,
            Map<String, List<String>> regularSignalTargets) {
        super(basicDurability, hitBox);
        this.subpart = subpart;
        this.connector = connector;
        this.renderPassenger = renderPassenger;
        this.allowUseItems = allowUseItems;
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

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new SeatSubsystem(owner, name, this);
    }
}
