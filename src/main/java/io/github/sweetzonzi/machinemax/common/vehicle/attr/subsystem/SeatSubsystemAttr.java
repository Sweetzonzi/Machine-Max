package io.github.sweetzonzi.machinemax.common.vehicle.attr.subsystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machinemax.MachineMax;
import io.github.sweetzonzi.machinemax.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.SeatSubsystem;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
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
    public final ViewAttr views;
    public final Set<String> viewInputs;
    public final Map<String, List<String>> moveSignalTargets;
    public final Map<String, List<String>> viewSignalTargets;
    public final Map<String, List<String>> regularSignalTargets;
    //TODO:是否无视命中情况转嫁乘客伤害到部件

    public static final MapCodec<SeatSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("basic_durability", 20f).forGetter(AbstractSubsystemAttr::getBasicDurability),
            Codec.STRING.optionalFieldOf("hit_box", "").forGetter(AbstractSubsystemAttr::getHitBox),
            Codec.STRING.optionalFieldOf("seat_point_locator", "").forGetter(SeatSubsystemAttr::getLocator),
            Codec.BOOL.optionalFieldOf("render_passenger", true).forGetter(SeatSubsystemAttr::isRenderPassenger),
            Vec3.CODEC.optionalFieldOf("passenger_scale", new Vec3(1, 1, 1)).forGetter(SeatSubsystemAttr::getPassengerScale),
            ViewAttr.CODEC.optionalFieldOf("views", new ViewAttr(
                    true,
                    List.of(),
                    true,
                    List.of(),
                    true,
                    true,
                    1.1f,
                    List.of()
            )).forGetter(SeatSubsystemAttr::getViews),
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
            ViewAttr views,
            boolean allowUseItems,
            List<String> viewInputs,
            Map<String, List<String>> moveSignalTargets,
            Map<String, List<String>> viewSignalTargets,
            Map<String, List<String>> regularSignalTargets) {
        super(basicDurability, hitBox);
        //合法性检查
        if (locator == null || locator.isEmpty())
            throw new IllegalStateException("error.machine_max.seat_subsystem.no_locator");
        if (!views.enableFirstPerson() && !views.enableThirdPerson())
            throw new IllegalArgumentException("error.machine_max.seat_subsystem.no_view");
        if (views.distanceScale() < 0)
            throw new IllegalArgumentException("error.machine_max.seat_subsystem.invalid_camera_distance");
        this.locator = locator;
        this.renderPassenger = renderPassenger;
        this.passengerScale = passengerScale;
        this.views = views;
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

    public record ViewAttr(
            //TODO:角度限制
            boolean enableFirstPerson,
            List<ResourceLocation> firstPersonHud,
            boolean enableThirdPerson,
            List<ResourceLocation> thirdPersonHud,
            boolean followVehicle,
            boolean focusOnCenter,
            float distanceScale,
            List<String> gunnerViews
    ) {
        public static final Codec<ViewAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("enable_first_person", true).forGetter(ViewAttr::enableFirstPerson),
                ResourceLocation.CODEC.listOf().optionalFieldOf("first_person_hud", List.of()).forGetter(ViewAttr::firstPersonHud),
                Codec.BOOL.optionalFieldOf("enable_third_person", true).forGetter(ViewAttr::enableThirdPerson),
                ResourceLocation.CODEC.listOf().optionalFieldOf("third_person_hud", List.of()).forGetter(ViewAttr::thirdPersonHud),
                Codec.BOOL.optionalFieldOf("follow_vehicle", true).forGetter(ViewAttr::followVehicle),
                Codec.BOOL.optionalFieldOf("focus_on_center", true).forGetter(ViewAttr::focusOnCenter),
                Codec.FLOAT.optionalFieldOf("distance_scale", 1.1f).forGetter(ViewAttr::distanceScale),
                Codec.STRING.listOf().optionalFieldOf("gunner_views", List.of()).forGetter(ViewAttr::gunnerViews)
                ).apply(instance, ViewAttr::new));
    }
}

