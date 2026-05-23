package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import lombok.Getter;

import java.util.List;

/**
 * 发射器子系统静态属性。<br>
 * 定义射速、初速加成、精度修正、开火信号输入频道等硬件参数。<br>
 * 一个子系统代表一个发射管/挂架/炮闩。
 */
@Getter
public class LauncherSubsystemStaticAttr extends BasicSubsystemStaticAttr {

    private final float fireRate;                       // 射速 (RPM)
    private final float velocityMultiplier;             // 初速乘数，先应用此
    private final float velocityBonus;                  // 初速线性加成 (m/s)
    private final float horizontalAccuracyMultiplier;   // 水平精度乘子，1.0=不改变弹丸默认水平精度
    private final float verticalAccuracyMultiplier;     // 垂直精度乘子，1.0=不改变弹丸默认垂直精度
    /** 开火信号输入频道列表，优先级从高到低 */
    private final List<String> controlInputs;

    public static final MapCodec<LauncherSubsystemStaticAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BasicAttr.CODEC.forGetter(BasicSubsystemStaticAttr::getBasicAttr),
            Codec.FLOAT.fieldOf("fire_rate").forGetter(LauncherSubsystemStaticAttr::getFireRate),
            Codec.FLOAT.optionalFieldOf("velocity_multiplier", 1.0f).forGetter(LauncherSubsystemStaticAttr::getVelocityMultiplier),
            Codec.FLOAT.optionalFieldOf("velocity_bonus", 0f).forGetter(LauncherSubsystemStaticAttr::getVelocityBonus),
            Codec.FLOAT.optionalFieldOf("horizontal_accuracy_multiplier", 1.0f).forGetter(LauncherSubsystemStaticAttr::getHorizontalAccuracyMultiplier),
            Codec.FLOAT.optionalFieldOf("vertical_accuracy_multiplier", 1.0f).forGetter(LauncherSubsystemStaticAttr::getVerticalAccuracyMultiplier),
            Codec.STRING.listOf().optionalFieldOf("control_inputs", List.of("weapon_control")).forGetter(LauncherSubsystemStaticAttr::getControlInputs),
            BasicSoundAttr.CODEC.codec().optionalFieldOf("sounds", BasicSoundAttr.DEFAULT).forGetter(BasicSubsystemStaticAttr::getSoundAttr)
    ).apply(instance, LauncherSubsystemStaticAttr::new));

    public LauncherSubsystemStaticAttr(
            BasicAttr basicAttr,
            float fireRate,
            float velocityMultiplier,
            float velocityBonus,
            float horizontalAccuracyMultiplier,
            float verticalAccuracyMultiplier,
            List<String> controlInputs,
            BasicSoundAttr sounds) {
        super(basicAttr, sounds);
        this.fireRate = fireRate;
        this.velocityMultiplier = velocityMultiplier;
        this.velocityBonus = velocityBonus;
        this.horizontalAccuracyMultiplier = horizontalAccuracyMultiplier;
        this.verticalAccuracyMultiplier = verticalAccuracyMultiplier;
        this.controlInputs = controlInputs;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemStaticAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.LAUNCHER;
    }
}
