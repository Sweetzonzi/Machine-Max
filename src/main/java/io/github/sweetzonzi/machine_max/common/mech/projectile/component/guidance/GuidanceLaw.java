package io.github.sweetzonzi.machine_max.common.mech.projectile.component.guidance;

import com.jme3.math.Vector3f;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import io.github.sweetzonzi.machine_max.common.registry.MMDataRegistries;

import java.util.function.Function;

/**
 * 制导律 — 可注册、可由 JSON 分派的制导算法抽象。
 * <p>
 * 与 {@code WorldEffect} / {@code AbstractSubsystemStaticAttr} 同构：
 * 注册表中的元素就是各律类型的 {@link MapCodec}，因此不需要额外的
 * Serializer / Type 类，{@link #codec()} 返回自身类型的 Codec 即可。
 * <p>
 * JSON 中 {@code "guidance"} 整块即本接口的 dispatch 结果，形状与 {@code warheads} 元素一致：
 * {@code "type"} 直接与本律的自由参数、{@link AuthorityLimits 限幅} 平级，不再多套一层：
 * <pre>
 * "guidance": {
 *   "type": "machine_max:pure_pursuit",
 *   "turn_gain": 3.0,
 *   "limits": { "structural_limit_g": 30.0, "...": "..." }
 * }
 * </pre>
 * <p>
 * <b>职责边界：</b>{@link #computeCommandAcceleration} 只给出<b>无约束</b>指令加速度；
 * 可用过载包线、转率钳制与诱导阻力一律由 {@link #computeAcceleration} 按 {@link #limits()}
 * 施加，因此各律的自由参数互不相同（纯追踪要 {@code turn_gain}、PN 要导航增益 {@code N}），
 * 用枚举无法承载。
 * <p>
 * 设计出处：《武器系统-组件化投射物与类型体系设计》§4.2。
 */
public interface GuidanceLaw {

    /**
     * 速度下限（m/s）。低于此速度本步不给指令——{@code a / v} 会发散。
     */
    float MIN_SPEED = 1.0f;

    /** 指令加速度的幅值下限，低于此值视为无指令 */
    float MIN_COMMAND = 1e-6f;

    /** 动压×参考面积的下限，低于此值跳过诱导阻力计算 */
    float MIN_DYNAMIC_PRESSURE = 1e-6f;

    /**
     * 本律类型的 JSON Codec。
     * <p>
     * 仅提供 JSON {@code MapCodec}：制导只在服务端计算，
     * 参数不随投射物同步到客户端。
     *
     * @return 本律类型的 Codec
     */
    MapCodec<? extends GuidanceLaw> codec();

    /**
     * 本律的可用过载包线等限幅参数。
     * <p>
     * 限幅与律的算法解耦：新增律无需改动限幅代码，只需携带自己的
     * {@link AuthorityLimits}（缺省 {@link AuthorityLimits#DEFAULT}）。
     *
     * @return 生效的限幅参数
     */
    AuthorityLimits limits();

    /**
     * 计算本步的<b>无约束</b>指令加速度（m/s²），结果写入 {@code out}。
     * <p>
     * 实现方需自行处理几何退化（速度过小、已对准、目标重合等），
     * 无有效指令时返回 {@code false}（{@code out} 内容视为无效）。
     * <p>
     * <b>调用线程：</b>物理线程（{@code ProjectileManager.updateProjectiles}）。
     *
     * @param ctx 展平的每步上下文（位置、速度、目标点、气动参数）
     * @param out 输出缓冲（复用，避免热路径分配）
     * @return true 表示产出了有效指令
     */
    boolean computeCommandAcceleration(GuidanceContext ctx, Vector3f out);

    /**
     * 一步制导解算：本律出无约束指令 → 三重限幅 → 附加诱导阻力。
     * <p>
     * 顺序与公式（《武器系统-制导组件实现备忘》§七）：
     * <pre>
     * a_cmd    = computeCommandAcceleration(...)            // 无约束
     * n_avail  = min(n_struct, n_aero(v))                   // 过载包线
     * ω        = min(|a_cmd|/|v|, n_avail·g0/|v|, ω_max)     // 限幅（过载 + 转率）
     * a        = 该 ω 对应的加速度（方向同 a_cmd）
     * Cl       = n·m·g0 / (½·ρ·v²·S)                        // 限幅后的实际过载
     * a_ind    = k·Cl²·½·ρ·v²·S / m   ，方向沿 −v̂
     * </pre>
     * 诱导阻力构成"越拉越拉不动"的能量陷阱：拉满过载 → Cl 升高 → 阻力增大 → 掉速 →
     * {@code n_aero(v)} 进一步下降，是过载包线的动力学闭环。
     * <p>
     * 所有弹种共用本路径——新增制导律无需改动此处与调用方。
     * <p>
     * <b>调用线程：</b>物理线程。
     *
     * @param ctx 展平的每步上下文
     * @param out 输出缓冲（复用）；本步无有效指令时被置零
     * @return true 表示产出了有效指令（false = 本步交回弹道）
     */
    default boolean computeAcceleration(GuidanceContext ctx, Vector3f out) {
        out.set(0f, 0f, 0f);
        if (!computeCommandAcceleration(ctx, out)) return false;

        float vx = ctx.velX(), vy = ctx.velY(), vz = ctx.velZ();
        float speedSq = vx * vx + vy * vy + vz * vz;
        float speed = (float) Math.sqrt(speedSq);
        if (speed < MIN_SPEED) {
            out.set(0f, 0f, 0f);
            return false;
        }

        float commandMag = out.length();
        if (commandMag < MIN_COMMAND) {
            out.set(0f, 0f, 0f);
            return false;
        }

        AuthorityLimits limits = limits();
        float mass = ctx.mass();
        float airDensity = ctx.airDensity();
        float area = limits.resolveReferenceArea(ctx.radius());

        // ——— 三重限幅：过载包线（结构 ∩ 气动）与转率硬钳取小 ———
        float availableLoad = Math.min(limits.structuralLimitG(),
                limits.aerodynamicLoadFactor(airDensity, speed, mass, area));
        float maxOmegaByLoad = availableLoad * AuthorityLimits.G0 / speed;
        float maxOmegaByRate = (float) Math.toRadians(limits.maxTurnRateDps());
        float omega = Math.min(commandMag / speed, Math.min(maxOmegaByLoad, maxOmegaByRate));
        float accelMag = omega * speed;
        out.multLocal(accelMag / commandMag);

        // ——— 诱导阻力：Cd_i = k·Cl²，方向沿 −v̂ ———
        float k = limits.inducedDragCoefficient();
        if (k > 0f && mass > 0f && area > 0f && airDensity > 0f) {
            float dynamicPressure = 0.5f * airDensity * speedSq * area;
            if (dynamicPressure > MIN_DYNAMIC_PRESSURE) {
                float loadFactor = accelMag / AuthorityLimits.G0;
                float liftCoefficient = loadFactor * mass * AuthorityLimits.G0 / dynamicPressure;
                float inducedDragAcc = k * liftCoefficient * liftCoefficient * dynamicPressure / mass;
                float invSpeed = 1f / speed;
                out.x -= inducedDragAcc * vx * invSpeed;
                out.y -= inducedDragAcc * vy * invSpeed;
                out.z -= inducedDragAcc * vz * invSpeed;
            }
        }
        return true;
    }

    /**
     * 聚合 Codec：按 JSON 中的 {@code "type"} 字段从
     * {@link MMDataRegistries#getGUIDANCE_LAW_CODEC() GUIDANCE_LAW_CODEC} 注册表分派。
     * <p>
     * 惰性访问点：本字段在首次被读取时才触发接口初始化，
     * 因此不会早于 {@code MachineMax.REGISTER} 就绪。
     */
    Codec<GuidanceLaw> CODEC = MMDataRegistries.getGUIDANCE_LAW_CODEC().byNameCodec()
            .dispatch(GuidanceLaw::codec, Function.identity());
}
