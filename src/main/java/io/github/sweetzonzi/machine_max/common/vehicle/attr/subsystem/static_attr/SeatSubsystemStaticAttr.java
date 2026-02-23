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
public class SeatSubsystemStaticAttr extends BasicSubsystemStaticAttr {
    public final boolean blockDamage; //是否无视命中情况转嫁乘客伤害到部件
    public final boolean renderPassenger;
    public final Vec3 passengerScale;
    public final boolean allowUseItems;
    public final ViewAttr views;
    public final Set<String> viewInputs;

    public static final MapCodec<SeatSubsystemStaticAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BasicAttr.CODEC.forGetter(BasicSubsystemStaticAttr::getBasicAttr),
            Codec.BOOL.optionalFieldOf("block_damage", false).forGetter(SeatSubsystemStaticAttr::isBlockDamage),
            Codec.BOOL.optionalFieldOf("render_passenger", true).forGetter(SeatSubsystemStaticAttr::isRenderPassenger),
            Vec3.CODEC.optionalFieldOf("passenger_scale", new Vec3(1, 1, 1)).forGetter(SeatSubsystemStaticAttr::getPassengerScale),
            ViewAttr.CODEC.optionalFieldOf("views", new ViewAttr(
                    true,
                    List.of(),
                    Vec3.ZERO,
                    true,
                    List.of(),
                    new Vec3(0, 0.75, 0),
                    true,
                    true,
                    1.1f
            )).forGetter(SeatSubsystemStaticAttr::getViews),
            Codec.STRING.listOf().optionalFieldOf("view_inputs", List.of()).forGetter(SeatSubsystemStaticAttr::getViewInputs),
            Codec.BOOL.optionalFieldOf("allow_use_items", false).forGetter(SeatSubsystemStaticAttr::isAllowUseItems)
    ).apply(instance, SeatSubsystemStaticAttr::new));

    public SeatSubsystemStaticAttr(
            BasicSubsystemStaticAttr.BasicAttr basicAttr,
            boolean blockDamage,
            boolean renderPassenger,
            Vec3 passengerScale,
            ViewAttr views,
            List<String> viewInputs,
            boolean allowUseItems) {
        super(basicAttr);
        this.blockDamage = blockDamage;
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
            Vec3 firstPersonOffset,
            boolean enableThirdPerson,
            List<ResourceLocation> thirdPersonHud,
            Vec3 thirdPersonOffset,
            boolean followVehicle,
            boolean focusOnCenter,
            float distanceScale
    ) {
        public static final Codec<ViewAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("enable_first_person", true).forGetter(ViewAttr::enableFirstPerson),
                ResourceLocation.CODEC.listOf().optionalFieldOf("first_person_hud", List.of()).forGetter(ViewAttr::firstPersonHud),
                Vec3.CODEC.optionalFieldOf("first_person_offset", Vec3.ZERO).forGetter(ViewAttr::firstPersonOffset),
                Codec.BOOL.optionalFieldOf("enable_third_person", true).forGetter(ViewAttr::enableThirdPerson),
                ResourceLocation.CODEC.listOf().optionalFieldOf("third_person_hud", List.of()).forGetter(ViewAttr::thirdPersonHud),
                Vec3.CODEC.optionalFieldOf("third_person_offset", new Vec3(0, 0.75, 0)).forGetter(ViewAttr::thirdPersonOffset),
                Codec.BOOL.optionalFieldOf("follow_vehicle", true).forGetter(ViewAttr::followVehicle),
                Codec.BOOL.optionalFieldOf("focus_on_center", true).forGetter(ViewAttr::focusOnCenter),
                Codec.FLOAT.optionalFieldOf("distance_scale", 1.1f).forGetter(ViewAttr::distanceScale)
                ).apply(instance, ViewAttr::new));
    }
}

