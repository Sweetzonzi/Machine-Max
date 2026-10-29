package io.github.sweetzonzi.machine_max.common.mech.explosion;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import io.github.sweetzonzi.machine_max.common.mech.projectile.component.effect.ExplosionWorldEffect;
import io.github.sweetzonzi.machine_max.common.mech.projectile.component.effect.WorldEffect;
import io.github.sweetzonzi.machine_max.common.mech.projectile.type.BallisticProjectileType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 殉爆合成器 — 把若干份 {@link CookoffContribution} 按实际数量合成一次普通爆炸的参数。
 *
 * <p>合成范围是<b>一个被摧毁的弹药库存子系统</b>（或飞行中被击毁的一发弹药的全部战斗部），
 * 其中的混装弹种全部合成一次。以 {@code i} 表示一项发射药或战斗部贡献、{@code nᵢ} 是它实际
 * 出现的次数，先累计三个中间量：</p>
 *
 * <pre>{@code
 * D = Σ nᵢ·dᵢ
 * S = Σ nᵢ·jᵢ²
 * W = Σ nᵢ·dᵢ·pᵢ
 *
 * J = √S
 * P = W / D        （D = 0 时 P = 0）
 * }</pre>
 *
 * <p>中间量必须整份库存结束后只开一次平方根、做一次加权平均：不能把中间已开根的冲量继续
 * 直接相加，也不能对中间穿深做无权平均。累计使用 {@code double}，构造
 * {@link ExplosionParams} 时再收窄到 {@code float}。</p>
 *
 * <p>映射到普通爆炸参数：{@code base_damage = D}、{@code base_impulse = J}、
 * {@code base_penetration = P}、{@code near_radius = 1 m}。绝对强度由这三个值承担，
 * 归一化总能量仍为 1，普通爆炸的场模型不因殉爆改变。</p>
 *
 * <p>硬截断半径按 §3.5 的公式从三个通道的相对规模取最大值：</p>
 *
 * <pre>{@code
 * max_radius = r₀ · max(1, √(P/Pcut), √(D/Dcut), J/Jcut)
 * r₀ = 1 m
 * }</pre>
 *
 * <p>三个截断阈值 {@link #P_CUT}、{@link #D_CUT}、{@link #J_CUT} 的取值见各自常量注释；
 * 它们只由第一方 7.62×54R 的单份贡献与"1500 发弹链 → 10 m"标定锚反推，与具体弹种的库存量无关。</p>
 *
 * <p><b>不变量：</b>同配方库存量 {@code N} 不改变合成穿深 {@code P}，伤害按 {@code N} 线性增长，
 * 冲量按 {@code √N} 增长；零伤害贡献不参与穿深加权，但可以单独提供冲量。
 * {@code D = 0} 且 {@code J = 0} 时 {@link #isEmpty()} 为真，调用方不得据此构造爆炸。</p>
 *
 * <p>本类是可变的累加器，只在一次主线程起爆提交内使用，不跨线程共享。</p>
 */
public final class CookoffComposition {

    // ==================== 公共标定常量 ====================

    /**
     * 公共参考半径 {@code r₀ = 1 m}：所有 {@link CookoffContribution} 字段的标定距离，
     * 同时是合成后普通爆炸的 {@code near_radius}。
     * <p>
     * 参考距离固定为 1 m，库存量只通过 D、S、W 与 {@link #maxRadius()} 表现，
     * 不改变近场平台半径。
     */
    public static final double REFERENCE_RADIUS = 1.0;

    /**
     * 合成穿深的硬截断阈值（mm），与 {@code P} 同量纲。
     * <p>
     * 该通道只做越界保护：{@code P} 是伤害加权平均穿深、与库存量无关，取不低于第一方各单份
     * 贡献 {@code p} 的最大值即可使 {@code √(P/Pcut) ≤ 1}。当前第一方最大单份贡献是重型战斗部
     * 的 {@code p = 60}，因此取 100 留出后续弹种的余量；取得过小会让单发也被穿深项顶到标定半径。
     */
    public static final double P_CUT = 100.0;

    /**
     * 合成伤害的硬截断阈值，与 {@code D} 同量纲。
     * <p>
     * 由标定锚反推：7.62×54R 的单发发射药贡献 {@code d = 0.1}，1500 发时
     * {@code max_radius = √(D/Dcut) = √(1500·d / Dcut) = 10 m}，故 {@code Dcut = 15·d = 1.5}。
     */
    public static final double D_CUT = 15.0 * 0.1;

    /**
     * 合成冲量的硬截断阈值，与 {@code J} 同量纲。
     * <p>
     * 由同一标定锚反推：7.62×54R 的单发发射药贡献 {@code j = 1.0}，1500 发时
     * {@code J = √1500·j}、{@code J/Jcut = √1500·j / Jcut = 10 m}，故
     * {@code Jcut = √1500·j / 10 ≈ 3.873·j}。
     */
    public static final double J_CUT = Math.sqrt(1500.0) * 1.0 / 10.0;

    /**
     * 粒子表现的穿深参考值，与 {@code P} 同量纲。
     * <p>
     * 三个表现参考值 {@code Pv、Dv、Jv} 只决定粒子缩放，不参与逻辑半径。取
     * {@code Pv = 100、Dv = 150、Jv = 40} 使 1500 发 7.62×54R 弹链的三个通道相对规模都约为 1，
     * 即弹链殉爆的 {@code particle_scale ≈ 1}；口径更大或数量更多的库存按相对规模放大。
     */
    public static final double P_VISUAL_REF = 100.0;

    /** 粒子表现的伤害参考值，与 {@code D} 同量纲；取值理由见 {@link #P_VISUAL_REF}。 */
    public static final double D_VISUAL_REF = 150.0;

    /** 粒子表现的冲量参考值，与 {@code J} 同量纲；取值理由见 {@link #P_VISUAL_REF}。 */
    public static final double J_VISUAL_REF = 40.0;

    /** 殉爆起爆统一使用的粒子资源：{@code machine_max:blast_medium}，一次起爆只播放一次。 */
    public static final ResourceLocation BLAST_MEDIUM_PARTICLE =
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "blast_medium");

    // ==================== 累计量 ====================

    /** D = Σ nᵢ·dᵢ */
    private double damage;

    /** S = Σ nᵢ·jᵢ² */
    private double impulseSquared;

    /** W = Σ nᵢ·dᵢ·pᵢ */
    private double weightedPenetration;

    // ==================== 累计入口 ====================

    /**
     * 计入一份贡献一次。
     *
     * @param contribution 发射药或战斗部贡献；{@code null} 与零贡献都按无贡献处理
     */
    public void add(CookoffContribution contribution) {
        add(contribution, 1);
    }

    /**
     * 计入一份贡献 {@code count} 次。
     * <p>
     * 只累加中间量 D、S、W：同一发内的发射药与多个战斗部也只是多次调用本方法，
     * 整份库存结束后才由 {@link #totalDamage()}、{@link #impulse()}、{@link #penetration()}
     * 统一做一次开根与加权平均。
     *
     * @param contribution 发射药或战斗部贡献；{@code null} 或零贡献时不改变累计量
     * @param count        实际出现次数；{@code <= 0} 时不做任何事
     */
    public void add(CookoffContribution contribution, int count) {
        if (contribution == null || count <= 0) return;
        double n = count;
        damage += n * contribution.damage();
        impulseSquared += n * contribution.impulse() * contribution.impulse();
        weightedPenetration += n * contribution.damage() * contribution.penetration();
    }

    /**
     * 计入一份整发库存弹药的殉爆贡献：弹体发射药加各战斗部的被诱爆贡献，各乘以 {@code count}。
     *
     * @param type  弹种；{@code null}（内容包未定义该弹种）时不计贡献
     * @param count 该弹种在库存快照中的实际发数；{@code <= 0} 时不做任何事
     */
    public void addStoredRound(@Nullable ProjectileType type, int count) {
        if (type == null || count <= 0) return;
        add(type.getCookoff(), count);
        if (type instanceof BallisticProjectileType ballistic) {
            addWarheads(ballistic.getWarheads(), count);
        }
    }

    /**
     * 只计入战斗部的被诱爆贡献（飞行中被击毁的弹药不走发射药分支）。
     *
     * @param warheads 该弹种的战斗部列表；{@code null} 或空列表时不做任何事
     * @param count    每份战斗部贡献的重复次数；{@code <= 0} 时不做任何事
     */
    public void addWarheads(@Nullable List<WorldEffect> warheads, int count) {
        if (warheads == null || warheads.isEmpty() || count <= 0) return;
        for (WorldEffect warhead : warheads) {
            if (warhead instanceof ExplosionWorldEffect blast) {
                add(blast.cookoff(), count);
            }
        }
    }

    // ==================== 合成量 ====================

    /** {@code D = Σ nᵢ·dᵢ}，即合成后的 {@code base_damage}。 */
    public double totalDamage() {
        return damage;
    }

    /** {@code J = √S}，即合成后的 {@code base_impulse}。 */
    public double impulse() {
        return Math.sqrt(impulseSquared);
    }

    /** {@code P = W / D}，即合成后的 {@code base_penetration}；{@code D = 0} 时为 0。 */
    public double penetration() {
        return damage > 0.0 ? weightedPenetration / damage : 0.0;
    }

    /**
     * 是否没有可生成爆炸的贡献。
     * <p>
     * {@code D = J = 0} 时不存在伤害或冲量，按合成规则不构造爆炸（{@code P} 在 {@code D = 0}
     * 时也为零）。只有穿深贡献、没有伤害与冲量的弹药组合同样属于空合成。
     */
    public boolean isEmpty() {
        return damage == 0.0 && impulseSquared == 0.0;
    }

    /**
     * 硬截断半径（m）：{@code r₀ · max(1, √(P/Pcut), √(D/Dcut), J/Jcut)}。
     * <p>
     * 三项相对规模都不到 1 时落 1 m 地板：单发弹药不会被按量放大成大范围爆炸，
     * 也不出现"单发半径 × √N"的外推。
     */
    public double maxRadius() {
        double penetrationRatio = Math.sqrt(penetration() / P_CUT);
        double damageRatio = Math.sqrt(damage / D_CUT);
        double impulseRatio = impulse() / J_CUT;
        double ratio = Math.max(1.0, Math.max(penetrationRatio, Math.max(damageRatio, impulseRatio)));
        return REFERENCE_RADIUS * ratio;
    }

    /**
     * 粒子缩放：三个通道相对规模最大值的立方根，{@code ∛max(P/Pv, D/Dv, J²/Jv²)}。
     * <p>
     * 使用三个通道的最大相对值，使只有伤害或只有冲量的殉爆也能获得粒子缩放。
     * 该缩放只进入粒子表现，逻辑 {@code near_radius} 恒为 {@link #REFERENCE_RADIUS}。
     */
    public float particleScale() {
        double ratio = Math.max(penetration() / P_VISUAL_REF,
                Math.max(damage / D_VISUAL_REF, impulseSquared / (J_VISUAL_REF * J_VISUAL_REF)));
        return (float) Math.cbrt(Math.max(0.0, ratio));
    }

    /**
     * 构造一次普通爆炸的参数。
     *
     * <p>首期殉爆的波前速度与表现取固定值：{@code front_speed = 20.0 m/s}、
     * 粒子仅 {@code machine_max:blast_medium}、火光取 {@link ExplosionLightParams#DEFAULT}。
     * {@code destroy_blocks}、{@code drop_items}、{@code causes_fire} 与 {@code damage_type}
     * 沿用普通参数类型的缺省策略，不从参与合成的各战斗部做布尔合并或继承其正常引爆设置。</p>
     *
     * <p>调用方必须先确认 {@link #isEmpty()} 为 {@code false}；本方法在空合成时也会返回一份
     * {@code base_damage = base_impulse = 0} 的参数，但那样没有可结算的效果。</p>
     *
     * @return 可交给 {@link ExplosionManager#detonate} 的普通爆炸参数
     */
    public ExplosionParams toExplosionParams() {
        return new ExplosionParams(
                (float) penetration(),
                (float) damage,
                (float) impulse(),
                (float) REFERENCE_RADIUS,
                (float) maxRadius(),
                20.0f,
                true,
                false,
                false,
                List.of(BLAST_MEDIUM_PARTICLE),
                particleScale(),
                ExplosionParams.DEFAULT_DAMAGE_TYPE,
                ExplosionLightParams.DEFAULT);
    }
}
