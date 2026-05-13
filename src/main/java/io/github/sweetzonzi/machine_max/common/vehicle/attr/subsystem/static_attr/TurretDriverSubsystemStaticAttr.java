package io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.SubsystemTypes;
import lombok.Getter;

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
     * @param maxSpeed 最大角速度 (rad/s)
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

    @Nullable
    private final TurretAxisAttr yawAxis;
    @Nullable
    private final TurretAxisAttr pitchAxis;
    /** 旋转控制信号输入频道列表，优先级从高到低 */
    private final List<String> controlInputs;

    public static final MapCodec<TurretDriverSubsystemStaticAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BasicAttr.CODEC.forGetter(BasicSubsystemStaticAttr::getBasicAttr),
            TurretAxisAttr.CODEC.optionalFieldOf("yaw").forGetter(attr -> Optional.ofNullable(attr.yawAxis)),
            TurretAxisAttr.CODEC.optionalFieldOf("pitch").forGetter(attr -> Optional.ofNullable(attr.pitchAxis)),
            Codec.STRING.listOf().optionalFieldOf("control_inputs", List.of("fire_control")).forGetter(TurretDriverSubsystemStaticAttr::getControlInputs),
            BasicSoundAttr.CODEC.codec().optionalFieldOf("sounds", BasicSoundAttr.DEFAULT).forGetter(BasicSubsystemStaticAttr::getSoundAttr)
    ).apply(instance, (basic, yaw, pitch, controlInputs, sounds) ->
            new TurretDriverSubsystemStaticAttr(basic, yaw.orElse(null), pitch.orElse(null), controlInputs, sounds)
    ));

    public TurretDriverSubsystemStaticAttr(
            BasicAttr basicAttr,
            @Nullable TurretAxisAttr yawAxis,
            @Nullable TurretAxisAttr pitchAxis,
            List<String> controlInputs,
            BasicSoundAttr sounds) {
        super(basicAttr, sounds);
        this.yawAxis = yawAxis;
        this.pitchAxis = pitchAxis;
        this.controlInputs = controlInputs;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemStaticAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.TURRET;
    }
}
