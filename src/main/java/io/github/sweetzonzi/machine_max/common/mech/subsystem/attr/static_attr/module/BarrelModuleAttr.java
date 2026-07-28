package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.module;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SubModule;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.AbstractModuleAttr;

/**
 * 炮管模块属性——定义精度和初速的基准值及战损/摧毁衰减。
 * <p>
 * 炮管受损 → 散布增大、初速下降，但仍可击发（非 critical 模块，{@code critical=false} 硬编码）。
 * 战损区间使用 {@link SubModule#computeDecayMultiplier} 计算衰减曲线。
 * </p>
 */
public class BarrelModuleAttr extends AbstractModuleAttr {
    /** 模块级衰减曲线指数 */
    private final float decayPower;
    /** 战损极限散布乘数（耐久→0 但未归零） */
    private final float damagedSpread;
    /** 摧毁散布乘数（耐久归零，质变悬崖） */
    private final float destroyedSpread;
    /** 战损极限初速乘数 */
    private final float damagedMuzzleVelocity;
    /** 摧毁初速乘数 */
    private final float destroyedMuzzleVelocity;

    // ====== 从 LauncherSubsystemStaticAttr 迁入的基准值 ======

    /** 初速乘数，先应用此（炮管长度/膛线决定） */
    private final float velocityMultiplier;
    /** 初速线性加成 (m/s) */
    private final float velocityBonus;
    /** 水平精度乘子，1.0=不改变弹丸默认水平精度 */
    private final float horizontalAccuracyMultiplier;
    /** 垂直精度乘子，1.0=不改变弹丸默认垂直精度 */
    private final float verticalAccuracyMultiplier;

    public static final MapCodec<BarrelModuleAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("durability_weight", 1.0f).forGetter(AbstractModuleAttr::durabilityWeight),
            Codec.FLOAT.optionalFieldOf("decay_power", 2.0f).forGetter(BarrelModuleAttr::decayPower),
            Codec.FLOAT.optionalFieldOf("damaged_spread", 2.0f).forGetter(BarrelModuleAttr::damagedSpread),
            Codec.FLOAT.optionalFieldOf("destroyed_spread", 8.0f).forGetter(BarrelModuleAttr::destroyedSpread),
            Codec.FLOAT.optionalFieldOf("damaged_muzzle_velocity", 0.7f).forGetter(BarrelModuleAttr::damagedMuzzleVelocity),
            Codec.FLOAT.optionalFieldOf("destroyed_muzzle_velocity", 0.1f).forGetter(BarrelModuleAttr::destroyedMuzzleVelocity),
            Codec.FLOAT.optionalFieldOf("velocity_multiplier", 1.0f).forGetter(BarrelModuleAttr::velocityMultiplier),
            Codec.FLOAT.optionalFieldOf("velocity_bonus", 0f).forGetter(BarrelModuleAttr::velocityBonus),
            Codec.FLOAT.optionalFieldOf("horizontal_accuracy_multiplier", 1.0f).forGetter(BarrelModuleAttr::horizontalAccuracyMultiplier),
            Codec.FLOAT.optionalFieldOf("vertical_accuracy_multiplier", 1.0f).forGetter(BarrelModuleAttr::verticalAccuracyMultiplier)
    ).apply(instance, BarrelModuleAttr::new));

    /** 炮管模块默认实例（耐久权重=0.6，不含衰减，精度/初速为 1.0x） */
    public static final BarrelModuleAttr DEFAULT = new BarrelModuleAttr(
            0.6f, 2.0f, 2.0f, 8.0f, 0.7f, 0.1f,
            1.0f, 0f, 1.0f, 1.0f);

    public BarrelModuleAttr(float durabilityWeight, float decayPower,
                            float damagedSpread, float destroyedSpread,
                            float damagedMuzzleVelocity, float destroyedMuzzleVelocity,
                            float velocityMultiplier, float velocityBonus,
                            float horizontalAccuracyMultiplier, float verticalAccuracyMultiplier) {
        super(durabilityWeight, false); // 炮管非 critical，硬编码
        this.decayPower = decayPower;
        this.damagedSpread = damagedSpread;
        this.destroyedSpread = destroyedSpread;
        this.damagedMuzzleVelocity = damagedMuzzleVelocity;
        this.destroyedMuzzleVelocity = destroyedMuzzleVelocity;
        this.velocityMultiplier = velocityMultiplier;
        this.velocityBonus = velocityBonus;
        this.horizontalAccuracyMultiplier = horizontalAccuracyMultiplier;
        this.verticalAccuracyMultiplier = verticalAccuracyMultiplier;
    }

    // ====== 衰减计算（战损/摧毁） ======

    /** 根据耐久比例计算散布乘数 */
    public float getSpreadMultiplier(float durabilityRatio) {
        return SubModule.computeDecayMultiplier(durabilityRatio, damagedSpread, destroyedSpread, decayPower);
    }

    /** 根据耐久比例计算初速乘数 */
    public float getMuzzleVelocityMultiplier(float durabilityRatio) {
        return SubModule.computeDecayMultiplier(durabilityRatio, damagedMuzzleVelocity, destroyedMuzzleVelocity, decayPower);
    }

    // ====== 衰减参数 getter ======

    public float decayPower() { return decayPower; }
    public float damagedSpread() { return damagedSpread; }
    public float destroyedSpread() { return destroyedSpread; }
    public float damagedMuzzleVelocity() { return damagedMuzzleVelocity; }
    public float destroyedMuzzleVelocity() { return destroyedMuzzleVelocity; }

    // ====== 基准值 getter ======

    public float velocityMultiplier() { return velocityMultiplier; }
    public float velocityBonus() { return velocityBonus; }
    public float horizontalAccuracyMultiplier() { return horizontalAccuracyMultiplier; }
    public float verticalAccuracyMultiplier() { return verticalAccuracyMultiplier; }
}
