package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import lombok.Getter;

import java.util.List;

/**
 * 武器控制器子系统静态属性。<br>
 * 定义射击模式、瞄准容差、输入频道等硬件参数。
 */
@Getter
public class WeaponControllerSubsystemStaticAttr extends BasicSubsystemStaticAttr {

    /**
     * 射击模式：
     * SALVO — 齐射，所有发射器同时开火
     * RIPPLE — 轮射，发射器按顺序依次开火
     */
    public enum FireMode {
        SALVO,
        RIPPLE
    }

    public static final Codec<FireMode> FIRE_MODE_CODEC = Codec.STRING.xmap(
            s -> FireMode.valueOf(s.toUpperCase()),
            FireMode::name
    );

    /** 默认射击模式（齐射） */
    private final FireMode defaultFireMode;
    /** 轮射模式下每两次发射之间的间隔（tick数） */
    private final int rippleIntervalTick;
    /** 瞄准容差角度（度），发射器指向与目标方向偏差小于此值时允许开火。<br>
     * 设为大于 180 的值（如 360）等效于禁用容差检查。 */
    private final float aimToleranceDeg;
    /** 瞄准目标坐标输入频道列表，从任一频道读取Vec3世界坐标 */
    private final List<String> aimInputs;
    /** 开火指令输入频道列表，任一频道有非EmptySignal即视为开火指令 */
    private final List<String> fireInputs;

    public static final MapCodec<WeaponControllerSubsystemStaticAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BasicAttr.CODEC.forGetter(BasicSubsystemStaticAttr::getBasicAttr),
            FIRE_MODE_CODEC.optionalFieldOf("fire_mode", FireMode.SALVO).forGetter(WeaponControllerSubsystemStaticAttr::getDefaultFireMode),
            Codec.INT.optionalFieldOf("ripple_interval_tick", 2).forGetter(WeaponControllerSubsystemStaticAttr::getRippleIntervalTick),
            Codec.FLOAT.optionalFieldOf("aim_tolerance_deg", 3.0f).forGetter(WeaponControllerSubsystemStaticAttr::getAimToleranceDeg),
            Codec.STRING.listOf().optionalFieldOf("aim_inputs", List.of("aim_input_p0", "aim_input_p1", "aim_input_p2", "aim_input_p3", "aim_input")).forGetter(WeaponControllerSubsystemStaticAttr::getAimInputs),
            Codec.STRING.listOf().optionalFieldOf("fire_inputs", List.of("fire_input_p0", "fire_input_p1", "fire_input_p2", "fire_input_p3", "fire_input")).forGetter(WeaponControllerSubsystemStaticAttr::getFireInputs),
            BasicSoundAttr.CODEC.codec().optionalFieldOf("sounds", BasicSoundAttr.DEFAULT).forGetter(BasicSubsystemStaticAttr::getSoundAttr)
    ).apply(instance, WeaponControllerSubsystemStaticAttr::new));

    public WeaponControllerSubsystemStaticAttr(
            BasicAttr basicAttr,
            FireMode defaultFireMode,
            int rippleIntervalTick,
            float aimToleranceDeg,
            List<String> aimInputs,
            List<String> fireInputs,
            BasicSoundAttr sounds) {
        super(basicAttr, sounds);
        this.defaultFireMode = defaultFireMode;
        this.rippleIntervalTick = rippleIntervalTick;
        this.aimToleranceDeg = aimToleranceDeg;
        this.aimInputs = aimInputs;
        this.fireInputs = fireInputs;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemStaticAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.WEAPON_CTRL;
    }
}
