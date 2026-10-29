package io.github.sweetzonzi.machine_max.common.mech.explosion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * 一份殉爆贡献 — 弹药发射药或战斗部被诱爆时，对殉爆合成给出的三个标定量。
 *
 * <p>三个字段都以公共参考半径 {@code r₀ = 1 m} 标定，量纲分别是穿深（mm）、
 * 单位迎流面积伤害与单位迎流面积冲量。它们<b>不参与正常引爆</b>：正常引信走各自的
 * 世界效果参数，本类型只在库存/战斗部被诱爆时按 {@link CookoffComposition} 合成。</p>
 *
 * <p>字段位置有两处，两处都叫 {@code cookoff}，靠嵌套层级区分语义：</p>
 * <ul>
 *   <li>弹药根对象（{@code projectiles/*.json}）上的 {@code cookoff}：每整发库存弹药的
 *       <b>发射药</b>贡献，不含 {@code warheads} 内的战斗部；</li>
 *   <li>{@code machine_max:blast} 战斗部上的 {@code cookoff}：该战斗部被诱爆时的一份贡献。</li>
 * </ul>
 * <p>对象与单个字段都允许省略，省略值均为零；显式负数、NaN 或 Infinity 属于内容错误，
 * 在 Codec 解码期报错。对象或字段未配置时对应贡献为零，不从普通爆炸的
 * {@code base_penetration} / {@code base_damage} / {@code base_impulse} / {@code near_radius}
 * 或动能终效字段继承。</p>
 *
 * @param penetration 参考距离处的殉爆穿深（mm），对应合成式的 {@code p}
 * @param damage      参考距离处单位迎流面积的殉爆伤害标定量，对应合成式的 {@code d}
 * @param impulse     参考距离处单位迎流面积的殉爆冲量标定量（N·s/m²），对应合成式的 {@code j}
 */
public record CookoffContribution(
        double penetration,
        double damage,
        double impulse
) {

    /** 零贡献：未配置发射药/战斗部诱爆参数时的取值，不参与合成。 */
    public static final CookoffContribution ZERO = new CookoffContribution(0.0, 0.0, 0.0);

    /**
     * 单个字段的校验：必须是有限非负数。
     * <p>
     * 负穿深、负伤害没有物理含义；非有限值只可能来自错误的程序构造或非法 JSON 写入，
     * 两者都按内容错误拒绝，避免把 NaN 带进库存累计后污染整次合成。
     */
    private static Codec<Double> nonNegative(String field) {
        return Codec.DOUBLE.validate(value -> {
            if (!Double.isFinite(value)) {
                return DataResult.error(() -> "cookoff." + field + " 必须是有限数，实际为 " + value);
            }
            if (value < 0.0) {
                return DataResult.error(() -> "cookoff." + field + " 必须为非负数，实际为 " + value);
            }
            return DataResult.success(value);
        });
    }

    /** JSON Codec：三个字段都可省略，省略为零。 */
    public static final Codec<CookoffContribution> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            nonNegative("penetration").optionalFieldOf("penetration", 0.0)
                    .forGetter(CookoffContribution::penetration),
            nonNegative("damage").optionalFieldOf("damage", 0.0)
                    .forGetter(CookoffContribution::damage),
            nonNegative("impulse").optionalFieldOf("impulse", 0.0)
                    .forGetter(CookoffContribution::impulse)
    ).apply(instance, CookoffContribution::new));

    /**
     * 紧凑构造器：把"有限非负"实现为类型不变量。
     * <p>
     * Codec 已经给出带字段名的内容包错误；直接构造（测试、代码内标定）同样不得绕过这条约束。
     */
    public CookoffContribution {
        requireFiniteNonNegative(penetration, "penetration");
        requireFiniteNonNegative(damage, "damage");
        requireFiniteNonNegative(impulse, "impulse");
    }

    private static void requireFiniteNonNegative(double value, String field) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(
                    "cookoff." + field + " 必须是有限非负数，实际为 " + value);
        }
    }

    /** 三项是否全为零；为零的贡献不进入合成，也不单独构成爆炸。 */
    public boolean isZero() {
        return penetration == 0.0 && damage == 0.0 && impulse == 0.0;
    }
}
