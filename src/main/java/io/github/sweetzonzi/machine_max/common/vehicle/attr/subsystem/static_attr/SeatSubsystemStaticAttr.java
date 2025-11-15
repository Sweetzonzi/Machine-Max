package io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.SubsystemTypes;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
public class SeatSubsystemStaticAttr extends AbstractSubsystemStaticAttr {
    public final boolean renderPassenger;
    public final Vec3 passengerScale;
    public final boolean allowUseItems;
    public final ViewAttr views;
    public final Set<String> viewInputs;
    //TODO:是否无视命中情况转嫁乘客伤害到部件

    public static final MapCodec<SeatSubsystemStaticAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("basic_durability", 20f).forGetter(AbstractSubsystemStaticAttr::getBasicDurability),
            Codec.BOOL.optionalFieldOf("render_passenger", true).forGetter(SeatSubsystemStaticAttr::isRenderPassenger),
            Vec3.CODEC.optionalFieldOf("passenger_scale", new Vec3(1, 1, 1)).forGetter(SeatSubsystemStaticAttr::getPassengerScale),
            ViewAttr.CODEC.optionalFieldOf("views", new ViewAttr(
                    true,
                    List.of(),
                    0f,
                    true,
                    List.of(),
                    0.75f,
                    true,
                    true,
                    1.1f
            )).forGetter(SeatSubsystemStaticAttr::getViews),
            Codec.STRING.listOf().optionalFieldOf("view_inputs", List.of()).forGetter(SeatSubsystemStaticAttr::getViewInputs),
            Codec.BOOL.optionalFieldOf("allow_use_items", false).forGetter(SeatSubsystemStaticAttr::isAllowUseItems)
    ).apply(instance, SeatSubsystemStaticAttr::new));

    public SeatSubsystemStaticAttr(
            float basicDurability,
            boolean renderPassenger, Vec3 passengerScale,
            ViewAttr views,
            List<String> viewInputs,
            boolean allowUseItems) {
        super(basicDurability);
        //合法性检查
        if (!views.enableFirstPerson() && !views.enableThirdPerson())
            throw new IllegalArgumentException("error.machine_max.seat_subsystem.no_view");
        if (views.distanceScale() < 0)
            throw new IllegalArgumentException("error.machine_max.seat_subsystem.invalid_camera_distance");
        this.renderPassenger = renderPassenger;
        this.passengerScale = passengerScale;
        this.views = views;
        this.viewInputs = new HashSet<>();
        this.viewInputs.addAll(viewInputs);
        this.allowUseItems = allowUseItems;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemStaticAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.SEAT;
    }

    private List<String> getViewInputs() {
        return viewInputs.stream().toList();
    }

    public record ViewAttr(
            //TODO:角度限制
            boolean enableFirstPerson,
            List<ResourceLocation> firstPersonHud,
            float firstPersonHeight,
            boolean enableThirdPerson,
            List<ResourceLocation> thirdPersonHud,
            float thirdPersonHeight,
            boolean followVehicle,
            boolean focusOnCenter,
            float distanceScale
    ) {
        public static final Codec<ViewAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("enable_first_person", true).forGetter(ViewAttr::enableFirstPerson),
                ResourceLocation.CODEC.listOf().optionalFieldOf("first_person_hud", List.of()).forGetter(ViewAttr::firstPersonHud),
                Codec.FLOAT.optionalFieldOf("first_person_offset", 0f).forGetter(ViewAttr::firstPersonHeight),
                Codec.BOOL.optionalFieldOf("enable_third_person", true).forGetter(ViewAttr::enableThirdPerson),
                ResourceLocation.CODEC.listOf().optionalFieldOf("third_person_hud", List.of()).forGetter(ViewAttr::thirdPersonHud),
                Codec.FLOAT.optionalFieldOf("third_person_offset", 0.75f).forGetter(ViewAttr::thirdPersonHeight),
                Codec.BOOL.optionalFieldOf("follow_vehicle", true).forGetter(ViewAttr::followVehicle),
                Codec.BOOL.optionalFieldOf("focus_on_center", true).forGetter(ViewAttr::focusOnCenter),
                Codec.FLOAT.optionalFieldOf("distance_scale", 1.1f).forGetter(ViewAttr::distanceScale)
                ).apply(instance, ViewAttr::new));
    }
}

