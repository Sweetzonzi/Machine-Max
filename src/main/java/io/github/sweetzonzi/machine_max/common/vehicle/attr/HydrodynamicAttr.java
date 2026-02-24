package io.github.sweetzonzi.machine_max.common.vehicle.attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Map;

/**
 * 流体动力学参数定义（通用 + 高级气动）
 *
 * <p>
 * 默认模式（advanced = false）：
 * <ul>
 *     <li>各方向阻力使用一阶（粘性）+ 二阶（惯性）模型</li>
 *     <li>升力为简单速度平方模型（x/y/z_lift）</li>
 * </ul>
 *
 * <p>
 * 高级模式（advanced = true）：
 * <ul>
 *     <li>在高级气动约定方向上，使用升力线模型计算升力与二阶阻力</li>
 *     <li>一阶阻力（粘性）仍然保留并叠加</li>
 *     <li>非升力方向仍使用原有阻力模型</li>
 * </ul>
 *
 * <p>
 * 坐标约定（流体动力计算点本地坐标系）：
 * <ul>
 *     <li>y+ ：升力法线方向</li>
 *     <li>z- ：来流方向（前向）</li>
 * </ul>
 */
public record HydrodynamicAttr(
        float scale,
        float effectiveRange,
        float transSonicAmplifier,
        // 阻力系数：[0] 一阶（线性），[1] 二阶（平方）
        List<Float> forward,
        List<Float> backward,
        List<Float> leftward,
        List<Float> rightward,
        List<Float> upward,
        List<Float> downward,
        // 简易升力系数（非高级模式）
        float xLift,
        float yLift,
        float zLift,
        // —— 高级气动 ——
        boolean advanced,
        AdvancedAeroAttr advancedAero
) {

    public static final Codec<HydrodynamicAttr> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.FLOAT.optionalFieldOf("scale", 1f)
                            .forGetter(HydrodynamicAttr::scale),
                    Codec.FLOAT.optionalFieldOf("effective_range", 1f)
                            .forGetter(HydrodynamicAttr::effectiveRange),
                    Codec.FLOAT.optionalFieldOf("transonic_amplifier", 5f)
                            .forGetter(HydrodynamicAttr::transSonicAmplifier),

                    Codec.FLOAT.listOf(2, 2).optionalFieldOf("front_drag", List.of(0.0f, 1.0f))
                            .forGetter(HydrodynamicAttr::forward),
                    Codec.FLOAT.listOf(2, 2).optionalFieldOf("back_drag", List.of(0.0f, 1.0f))
                            .forGetter(HydrodynamicAttr::backward),
                    Codec.FLOAT.listOf(2, 2).optionalFieldOf("left_drag", List.of(0.0f, 1.0f))
                            .forGetter(HydrodynamicAttr::leftward),
                    Codec.FLOAT.listOf(2, 2).optionalFieldOf("right_drag", List.of(0.0f, 1.0f))
                            .forGetter(HydrodynamicAttr::rightward),
                    Codec.FLOAT.listOf(2, 2).optionalFieldOf("up_drag", List.of(0.0f, 1.0f))
                            .forGetter(HydrodynamicAttr::upward),
                    Codec.FLOAT.listOf(2, 2).optionalFieldOf("down_drag", List.of(0.0f, 1.0f))
                            .forGetter(HydrodynamicAttr::downward),

                    Codec.FLOAT.optionalFieldOf("x_lift", 0f)
                            .forGetter(HydrodynamicAttr::xLift),
                    Codec.FLOAT.optionalFieldOf("y_lift", 0f)
                            .forGetter(HydrodynamicAttr::yLift),
                    Codec.FLOAT.optionalFieldOf("z_lift", 0f)
                            .forGetter(HydrodynamicAttr::zLift),

                    Codec.BOOL.optionalFieldOf("advanced", false)
                            .forGetter(HydrodynamicAttr::advanced),
                    AdvancedAeroAttr.CODEC.optionalFieldOf(
                                    "advanced_aero",
                                    AdvancedAeroAttr.DEFAULT)
                            .forGetter(HydrodynamicAttr::advancedAero)
            ).apply(instance, HydrodynamicAttr::new));

    public static final Codec<Map<String, HydrodynamicAttr>> MAP_CODEC =
            Codec.unboundedMap(Codec.STRING, CODEC);

    public static final HydrodynamicAttr DEFAULT = new HydrodynamicAttr(
            1f,
            1f,
            5f,
            List.of(0.1f, 1f),
            List.of(0.1f, 1f),
            List.of(0.1f, 1f),
            List.of(0.1f, 1f),
            List.of(0.1f, 1f),
            List.of(0.1f, 1f),
            0f,
            0f,
            0f,
            false,
            AdvancedAeroAttr.DEFAULT
    );
}