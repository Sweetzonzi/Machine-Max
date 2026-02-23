package io.github.sweetzonzi.machine_max.common.vehicle.attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * 高级气动参数（用于飞行器 / 机翼）
 * 仅在 advanced = true 时生效
 *
 * <p>
 * 坐标约定（刚体本地坐标系）：
 * <ul>
 *     <li>y+ ：升力法线方向</li>
 *     <li>z- ：来流方向（前向）</li>
 * </ul>
 *
 * <p>
 * 该模型基于简化升力线理论：
 * <ul>
 *     <li>升力系数：Cl = liftSlope * (α - alpha0)</li>
 *     <li>阻力系数：Cd = cd0 + kInduced * Cl²</li>
 * </ul>
 *
 * @param liftSlope  升力线斜率 dCl/dα（单位：1/rad）
 * @param alpha0     零升攻角（单位：rad）
 * @param alphaStall 失速攻角（单位：rad，取正值，对称失速）
 * @param cd0        零升阻力系数
 * @param kInduced   诱导阻力系数（≈ 1 / (π * AR * e)）
 */
public record AdvancedAeroAttr(
        float liftSlope,
        float alpha0,
        float alphaStall,
        float cd0,
        float kInduced
) {

    public static final Codec<AdvancedAeroAttr> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.FLOAT.optionalFieldOf("lift_slope", 6.28f)
                            .forGetter(AdvancedAeroAttr::liftSlope),
                    Codec.FLOAT.optionalFieldOf("alpha0", 0f)
                            .forGetter(AdvancedAeroAttr::alpha0),
                    Codec.FLOAT.optionalFieldOf("alpha_stall", 0.52f)
                            .forGetter(AdvancedAeroAttr::alphaStall),
                    Codec.FLOAT.optionalFieldOf("cd0", 0.02f)
                            .forGetter(AdvancedAeroAttr::cd0),
                    Codec.FLOAT.optionalFieldOf("k_induced", 0.05f)
                            .forGetter(AdvancedAeroAttr::kInduced)
            ).apply(instance, AdvancedAeroAttr::new));

    /** 一个相对温和、不会炸数值的默认机翼参数 */
    public static final AdvancedAeroAttr DEFAULT = new AdvancedAeroAttr(
            6.28f,   // 2π，薄翼理论
            0f,
            0.52f,   // ~30°
            0.02f,
            0.05f
    );
}