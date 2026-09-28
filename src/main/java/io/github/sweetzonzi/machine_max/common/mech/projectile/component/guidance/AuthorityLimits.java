package io.github.sweetzonzi.machine_max.common.mech.projectile.component.guidance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * 可用过载包线参数 — 跨制导律共享的整块限幅配置。
 * <p>
 * 设计文档 §4.2 原本只给一个常量上限 {@code max_lateral_acceleration}；
 * 本实现把它替换为<b>随速度与高度变化的可用过载包线</b>：
 * <pre>
 * n_struct   = structural_limit_g                       // 结构上限，与速度无关
 * n_aero(v)  = ½·ρ(h)·v²·Cl_max·S / (m·g0)              // 气动上限，∝ v²
 * n_avail    = min(n_struct, n_aero(v))
 * </pre>
 * 两条上限取小：低速段由气动主导（{@code n_aero → 0}），高速段由结构主导，
 * 交点即<b>角点速度</b>——以最少速度换最大过载的点。
 * <p>
 * 参数采用国际单位制（SI）。字段语义参见《武器系统-制导组件实现备忘》§七。
 *
 * @param structuralLimitG        结构过载上限（g），与速度无关
 * @param liftCoefficientMax      最大升力系数（含舵面），决定气动上限的斜率
 * @param referenceArea           参考面积（m²），&le;0 表示自动取弹体截面积 π·r²
 * @param inducedDragCoefficient  诱导阻力系数 k（{@code Cd_i = k·Cl²}），0 = 关闭诱导阻力
 * @param maxTurnRateDps          转率硬钳（度/秒），低速下的数值守卫
 */
public record AuthorityLimits(
    float structuralLimitG,
    float liftCoefficientMax,
    float referenceArea,
    float inducedDragCoefficient,
    float maxTurnRateDps
) {

    /** 标准重力加速度（m/s²） */
    public static final float G0 = 9.81f;

    /** 默认包线：30g 结构上限、Cl_max=1.5、截面积自动、k=0.10、600°/s */
    public static final AuthorityLimits DEFAULT = new AuthorityLimits(30.0f, 1.5f, 0.0f, 0.10f, 600.0f);

    public static final Codec<AuthorityLimits> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.FLOAT.optionalFieldOf("structural_limit_g", DEFAULT.structuralLimitG)
            .forGetter(AuthorityLimits::structuralLimitG),
        Codec.FLOAT.optionalFieldOf("lift_coefficient_max", DEFAULT.liftCoefficientMax)
            .forGetter(AuthorityLimits::liftCoefficientMax),
        Codec.FLOAT.optionalFieldOf("reference_area", DEFAULT.referenceArea)
            .forGetter(AuthorityLimits::referenceArea),
        Codec.FLOAT.optionalFieldOf("induced_drag_coefficient", DEFAULT.inducedDragCoefficient)
            .forGetter(AuthorityLimits::inducedDragCoefficient),
        Codec.FLOAT.optionalFieldOf("max_turn_rate_dps", DEFAULT.maxTurnRateDps)
            .forGetter(AuthorityLimits::maxTurnRateDps)
    ).apply(instance, AuthorityLimits::new));

    /**
     * 解析参考面积：未显式给定（&le;0）时回退到弹体截面积 π·r²。
     * <p>
     * 有翼/有舵弹的气动参考面积通常远大于弹体截面，需在 JSON 中显式给定。
     *
     * @param radius 弹体半径（m）
     * @return 生效的参考面积（m²）
     */
    public float resolveReferenceArea(float radius) {
        return referenceArea > 0f ? referenceArea : (float) (Math.PI * radius * radius);
    }

    /**
     * 气动可用过载 {@code n_aero = ½·ρ·v²·Cl_max·S / (m·g0)}，单位为 g。
     *
     * @param airDensity 归一化空气密度 ρ(h)（海平面为 1.0）
     * @param speed      速率（m/s）
     * @param mass       质量（kg）
     * @param area       已解析的参考面积（m²）
     * @return 气动可用过载（g），参数非法时返回 0
     */
    public float aerodynamicLoadFactor(float airDensity, float speed, float mass, float area) {
        if (speed <= 0f || mass <= 0f || area <= 0f) return 0f;
        return 0.5f * airDensity * speed * speed * liftCoefficientMax * area / (mass * G0);
    }
}
