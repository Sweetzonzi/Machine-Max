package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.visual.HudLayout;
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
    /** 型号默认值：乘员能否使用物品；零件实例可用动态属性 {@code allow_use_items} 覆盖 */
    public final boolean allowUseItems;
    public final List<String> interactInputs;
    /**
     * 型号默认视图。其中 {@code first_hud_layout} / {@code third_hud_layout} 可按安装位用
     * 动态属性同名键覆盖；相机几何（视角开关、偏移、限位、跟随）只由型号定义决定。
     */
    public final ViewAttr views;
    public final Set<String> viewInputs;

    public static final MapCodec<SeatSubsystemStaticAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BasicAttr.CODEC.forGetter(BasicSubsystemStaticAttr::getBasicAttr),
            Codec.BOOL.optionalFieldOf("block_damage", false).forGetter(SeatSubsystemStaticAttr::isBlockDamage),
            Codec.BOOL.optionalFieldOf("render_passenger", true).forGetter(SeatSubsystemStaticAttr::isRenderPassenger),
            Vec3.CODEC.optionalFieldOf("passenger_scale", new Vec3(1, 1, 1)).forGetter(SeatSubsystemStaticAttr::getPassengerScale),
            ViewAttr.CODEC.optionalFieldOf("views", new ViewAttr(
                    true,
                    HudLayout.EMPTY,
                    Vec3.ZERO,
                    true,
                    HudLayout.EMPTY,
                    new Vec3(0, 0.75, 0),
                    true,
                    true,
                    1.1f,
                    -70.0f,
                    45.0f,
                    90.0f
            )).forGetter(SeatSubsystemStaticAttr::getViews),
            Codec.STRING.listOf().optionalFieldOf("view_inputs", List.of()).forGetter(SeatSubsystemStaticAttr::getViewInputs),
            Codec.BOOL.optionalFieldOf("allow_use_items", false).forGetter(SeatSubsystemStaticAttr::isAllowUseItems),
            Codec.STRING.listOf().optionalFieldOf("interact_inputs", List.of("interact")).forGetter(SeatSubsystemStaticAttr::getInteractInputs),
            BasicSoundAttr.CODEC.codec().optionalFieldOf("sounds", BasicSoundAttr.DEFAULT).forGetter(BasicSubsystemStaticAttr::getSoundAttr)
    ).apply(instance, SeatSubsystemStaticAttr::new));

    public SeatSubsystemStaticAttr(
            BasicSubsystemStaticAttr.BasicAttr basicAttr,
            boolean blockDamage,
            boolean renderPassenger,
            Vec3 passengerScale,
            ViewAttr views,
            List<String> viewInputs,
            boolean allowUseItems,
            List<String> interactInputs,
            BasicSoundAttr sounds
    ) {
        super(basicAttr, sounds);
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
        this.interactInputs = interactInputs;
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

    /**
     * 获取该子系统接受的交互输入通道列表
     */
    public List<String> getAcceptedChannels() {
        return interactInputs;
    }

    public record ViewAttr(
            boolean enableFirstPerson,
            /** 第一人称视角使用的 HUD 布局默认值；实例可用动态属性 {@code first_hud_layout} 覆盖 */
            ResourceLocation firstHudLayout,
            Vec3 firstPersonOffset,
            boolean enableThirdPerson,
            /** 第三人称视角使用的 HUD 布局默认值；实例可用动态属性 {@code third_hud_layout} 覆盖 */
            ResourceLocation thirdHudLayout,
            Vec3 thirdPersonOffset,
            boolean followVehicle,
            boolean focusOnCenter,
            float distanceScale,
            /**
             * 最小俯仰角限制（单位：度）
             * 零位（水平方向）对应0度
             * 例如：minPitch=-30表示最多向下看30度
             */
            float minPitch,
            /**
             * 最大俯仰角限制（单位：度）
             * 零位（水平方向）对应0度
             * 例如：maxPitch=60表示最多向上看60度
             */
            float maxPitch,
            /**
             * 偏航角张角限制（单位：度）
             * 由于底层坐标系限制，零位（正前方）对应180度
             * 因此实际限制范围为 [180 - yawLimit/2, 180 + yawLimit/2]
             * 例如：yawLimit=90表示水平方向±45度的移动范围
             */
            float yawLimit
    ) {
        public static final Codec<ViewAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("enable_first_person", true).forGetter(ViewAttr::enableFirstPerson),
                ResourceLocation.CODEC.optionalFieldOf("first_hud_layout", HudLayout.EMPTY)
                        .forGetter(ViewAttr::firstHudLayout),
                Vec3.CODEC.optionalFieldOf("first_person_offset", Vec3.ZERO).forGetter(ViewAttr::firstPersonOffset),
                Codec.BOOL.optionalFieldOf("enable_third_person", true).forGetter(ViewAttr::enableThirdPerson),
                ResourceLocation.CODEC.optionalFieldOf("third_hud_layout", HudLayout.EMPTY)
                        .forGetter(ViewAttr::thirdHudLayout),
                Vec3.CODEC.optionalFieldOf("third_person_offset", new Vec3(0, 0.75, 0)).forGetter(ViewAttr::thirdPersonOffset),
                Codec.BOOL.optionalFieldOf("follow_vehicle", true).forGetter(ViewAttr::followVehicle),
                Codec.BOOL.optionalFieldOf("focus_on_center", true).forGetter(ViewAttr::focusOnCenter),
                Codec.FLOAT.optionalFieldOf("distance_scale", 1.1f).forGetter(ViewAttr::distanceScale),
                Codec.FLOAT.optionalFieldOf("min_pitch", -70.0f).forGetter(ViewAttr::minPitch),
                Codec.FLOAT.optionalFieldOf("max_pitch", 45.0f).forGetter(ViewAttr::maxPitch),
                Codec.FLOAT.optionalFieldOf("yaw_limit", 90.0f).forGetter(ViewAttr::yawLimit)
                ).apply(instance, ViewAttr::new));
    }
}

