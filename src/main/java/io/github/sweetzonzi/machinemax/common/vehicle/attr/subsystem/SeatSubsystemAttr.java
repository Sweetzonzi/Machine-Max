package io.github.sweetzonzi.machinemax.common.vehicle.attr.subsystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machinemax.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.SeatSubsystem;
import lombok.Getter;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Getter
public class SeatSubsystemAttr extends AbstractSubsystemAttr {
    public final String locator;
    public final boolean renderPassenger;
    public final Vec3 passengerScale;
    public final boolean allowUseItems;
    public final boolean allowFirstPersonView;
    public final boolean allowThirdPersonView;
    public final Set<String> viewInputs;
    public final Map<String, List<String>> moveSignalTargets;
    public final Map<String, List<String>> viewSignalTargets;
    public final Map<String, List<String>> regularSignalTargets;

    public static final MapCodec<SeatSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("basic_durability", 100f).forGetter(AbstractSubsystemAttr::getBasicDurability),
            Codec.STRING.optionalFieldOf("hit_box", "").forGetter(AbstractSubsystemAttr::getHitBox),
            Codec.STRING.optionalFieldOf("seat_point_locator","").forGetter(SeatSubsystemAttr::getLocator),
            Codec.BOOL.optionalFieldOf("render_passenger", true).forGetter(SeatSubsystemAttr::isRenderPassenger),
            Vec3.CODEC.optionalFieldOf("passenger_scale", new Vec3(1, 1, 1)).forGetter(SeatSubsystemAttr::getPassengerScale),
            Codec.BOOL.optionalFieldOf("allow_first_person_view", true).forGetter(SeatSubsystemAttr::isAllowFirstPersonView),
            Codec.BOOL.optionalFieldOf("allow_third_person_view", true).forGetter(SeatSubsystemAttr::isAllowThirdPersonView),
            Codec.BOOL.optionalFieldOf("allow_use_items", false).forGetter(SeatSubsystemAttr::isAllowUseItems),
            Codec.STRING.listOf().optionalFieldOf("view_inputs", List.of()).forGetter(SeatSubsystemAttr::getViewInputs),
            SIGNAL_TARGETS_CODEC.optionalFieldOf("move_outputs", Map.of()).forGetter(SeatSubsystemAttr::getMoveSignalTargets),
            SIGNAL_TARGETS_CODEC.optionalFieldOf("view_outputs", Map.of()).forGetter(SeatSubsystemAttr::getViewSignalTargets),
            SIGNAL_TARGETS_CODEC.optionalFieldOf("regular_outputs", Map.of()).forGetter(SeatSubsystemAttr::getRegularSignalTargets)
    ).apply(instance, SeatSubsystemAttr::new));

    public SeatSubsystemAttr(
            float basicDurability,
            String hitBox,
            String locator,
            boolean renderPassenger, Vec3 passengerScale,
            boolean allowFirstPersonView,
            boolean allowThirdPersonView,
            boolean allowUseItems,
            List<String> viewInputs,
            Map<String, List<String>> moveSignalTargets,
            Map<String, List<String>> viewSignalTargets,
            Map<String, List<String>> regularSignalTargets) {
        super(basicDurability, hitBox);
        //合法性检查
        if (locator == null || locator.isEmpty())
            throw new IllegalStateException("error.machine_max.seat_subsystem.no_locator");
        if (!allowFirstPersonView && !allowThirdPersonView)
            throw new IllegalArgumentException("error.machine_max.seat_subsystem.no_view");
        this.locator = locator;
        this.renderPassenger = renderPassenger;
        this.passengerScale = passengerScale;
        this.allowFirstPersonView = allowFirstPersonView;
        this.allowThirdPersonView = allowThirdPersonView;
        this.allowUseItems = allowUseItems;
        this.viewInputs = new HashSet<>();
        this.viewInputs.addAll(viewInputs);
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

    private List<String> getViewInputs() {
        return viewInputs.stream().toList();
    }
}
