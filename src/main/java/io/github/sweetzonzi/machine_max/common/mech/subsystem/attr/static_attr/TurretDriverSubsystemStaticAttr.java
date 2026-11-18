package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.FmodSoundAttr;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * 炮塔驱动子系统静态属性。<br>
 * 定义方向机(Yaw)和/或高低机(Pitch)的硬件参数、控制信号输入频道。
 * 不存在的轴（null）表示该轴未配置，不会进行控制。
 * <p>
 * 旋转顺序约定：Yaw(偏航) → Pitch(俯仰)，保证两轴解耦，控制器独立求解。
 */
@Getter
public class TurretDriverSubsystemStaticAttr extends BasicSubsystemStaticAttr {

    /**
     * 炮塔单轴属性。<br>
     * 定义方向机(偏航Yaw)或高低机(俯仰Pitch)的硬件参数。
     * 信号输入/输出配置在动态属性中。
     *
     * @param maxForce 最大驱动力矩（刹车力矩与之相同）
     * @param maxSpeed 最大角速度 (°/s)
     */
    public record TurretAxisAttr(
            float maxForce,
            float maxSpeed
    ) {
        public static final Codec<TurretAxisAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.fieldOf("max_force").forGetter(TurretAxisAttr::maxForce),
                Codec.FLOAT.fieldOf("max_speed").forGetter(TurretAxisAttr::maxSpeed)
        ).apply(instance, TurretAxisAttr::new));
    }

    /**
     * 炮塔双机的 FMOD 事件引用。<br>
     * 两条轴各一个持续事件，机构型式由内容包选事件表达：手摇炮塔取 {@code *_manual}，动力炮塔取 {@code *_hydraulic}。
     *
     * @param bank       SparkCore 内容包中的 bank ID
     * @param horizontal 方向机（水平回转）事件，省略表示水平轴不发声
     * @param vertical   高低机（俯仰）事件，省略表示俯仰轴不发声
     */
    public record TurretFmodSoundAttr(ResourceLocation bank, Optional<String> horizontal, Optional<String> vertical) {
        public static final Codec<TurretFmodSoundAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("bank").forGetter(TurretFmodSoundAttr::bank),
                FmodSoundAttr.EVENT_PATH_CODEC.optionalFieldOf("horizontal").forGetter(TurretFmodSoundAttr::horizontal),
                FmodSoundAttr.EVENT_PATH_CODEC.optionalFieldOf("vertical").forGetter(TurretFmodSoundAttr::vertical)
        ).apply(instance, TurretFmodSoundAttr::new));
    }

    /**
     * 炮塔音效配置。
     *
     * @param basicSounds 子系统生命周期音效
     * @param turn        双机运转音的兜底事件，两条轴共用；FMOD 不可用时按该轴角速度升调播放
     * @param fmod        双机持续音的可选 FMOD 事件
     */
    public record TurretSoundAttr(BasicSoundAttr basicSounds, SoundEvent turn, Optional<TurretFmodSoundAttr> fmod) {
        /** 兜底运转音取电机运转音：音色本来就接近伺服泵，按角速度升调后即双机声。 */
        public static final SoundEvent DEFAULT_TURN = SoundEvent.createFixedRangeEvent(
                ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "subsystem.motor.elec_3000"), 64);

        public static final TurretSoundAttr DEFAULT =
                new TurretSoundAttr(BasicSoundAttr.DEFAULT, DEFAULT_TURN, Optional.empty());

        public static final Codec<TurretSoundAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BasicSoundAttr.basicSounds(TurretSoundAttr::basicSounds),
                SoundEvent.DIRECT_CODEC.optionalFieldOf("turn", DEFAULT_TURN).forGetter(TurretSoundAttr::turn),
                TurretFmodSoundAttr.CODEC.optionalFieldOf("fmod").forGetter(TurretSoundAttr::fmod)
        ).apply(instance, TurretSoundAttr::new));
    }

    @Nullable
    private final TurretAxisAttr yawAxis;
    @Nullable
    private final TurretAxisAttr pitchAxis;
    /** 旋转控制信号输入频道列表，优先级从高到低 */
    private final List<String> controlInputs;
    private final TurretSoundAttr sounds;

    public static final MapCodec<TurretDriverSubsystemStaticAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BasicAttr.CODEC.forGetter(BasicSubsystemStaticAttr::getBasicAttr),
            TurretAxisAttr.CODEC.optionalFieldOf("yaw").forGetter(attr -> Optional.ofNullable(attr.yawAxis)),
            TurretAxisAttr.CODEC.optionalFieldOf("pitch").forGetter(attr -> Optional.ofNullable(attr.pitchAxis)),
            Codec.STRING.listOf().optionalFieldOf("control_inputs", List.of("weapon_control")).forGetter(TurretDriverSubsystemStaticAttr::getControlInputs),
            TurretSoundAttr.CODEC.optionalFieldOf("sounds", TurretSoundAttr.DEFAULT).forGetter(TurretDriverSubsystemStaticAttr::getSounds)
    ).apply(instance, (basic, yaw, pitch, controlInputs, sounds) ->
            new TurretDriverSubsystemStaticAttr(basic, yaw.orElse(null), pitch.orElse(null), controlInputs, sounds)
    ));

    public TurretDriverSubsystemStaticAttr(
            BasicAttr basicAttr,
            @Nullable TurretAxisAttr yawAxis,
            @Nullable TurretAxisAttr pitchAxis,
            List<String> controlInputs,
            TurretSoundAttr sounds) {
        super(basicAttr, sounds.basicSounds());
        this.yawAxis = yawAxis;
        this.pitchAxis = pitchAxis;
        this.controlInputs = controlInputs;
        this.sounds = sounds;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemStaticAttr> codec() {
        return CODEC;
    }

}
