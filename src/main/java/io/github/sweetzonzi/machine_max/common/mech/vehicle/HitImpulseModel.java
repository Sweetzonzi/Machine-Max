package io.github.sweetzonzi.machine_max.common.mech.vehicle;

/**
 * 命中冲量的纯算术部分：由「伤害 / 最大耐久」比推出施加到命中零件刚体上的冲量（N·s）。
 *
 * <p>本类不读取配置、不访问物理世界，也没有状态：系数与速度渐近线都由调用方作为参数传入，
 * 因此服务端结算与离线测试走的是同一段算术。</p>
 *
 * <p>口径：{@code r = damage / maxDurability} 是这次命中打掉该零件多少比例的耐久，
 * {@code s = C · r} 是它在线性段能给出的冲量，{@code J∞ = m · V} 是该零件质量把冲量全部
 * 转成速度时的上限。两者按谐波合成：</p>
 *
 * <pre>J = 1 / (1/s + 1/J∞) = s · J∞ / (s + J∞)</pre>
 *
 * <p>这条曲线有三个性质，它们就是本类要守的契约：</p>
 * <ul>
 *     <li>小 {@code r} 时 {@code J ≈ C · r}，冲量随伤害线性增长，命中之间的强弱次序不变；</li>
 *     <li>{@code r} 增大时 {@code J} 单调递增并渐近 {@code J∞}，渐近线永不到达，所以上限
 *     长在规律内部，而不是从外面裁一刀；</li>
 *     <li>{@code J∞} 与零件质量成正比，同一发子弹打重件与打轻件的响应不同。</li>
 * </ul>
 *
 * <p>另一条契约是标度不变性：伤害与耐久同乘一个系数时 {@code r} 不变，冲量也不变。内容包
 * 整包换标度（伤害与耐久一起放大）因此不会把载具变成炮弹。</p>
 *
 * <p>所有函数对 {@code NaN}、无穷与负值取退化而不抛异常：无法给出可信读数的输入一律按
 * “不产生冲量”处理。</p>
 */
public final class HitImpulseModel {

    private HitImpulseModel() {
    }

    /**
     * 一次命中施加到零件刚体上的冲量大小（N·s）。
     *
     * <p>冲量方向不在这里决定：调用方取命中速度方向，本方法只给出大小。物理基础只回答
     * “多少动量交给这个刚体”，而刚体最终获得的速度由它的质量与惯量决定。</p>
     *
     * @param damage          本次命中的伤害（穿甲与材质减伤之前的原始值）
     * @param maxDurability   命中零件的最大耐久（HP）
     * @param mass            命中零件刚体的质量（kg）
     * @param impulsePerRatio 该类伤害的斜率系数 C（N·s / 每 1.0 伤害耐久比）
     * @param maxSpeed        该类伤害的速度渐近线 V（m/s）
     * @return 冲量大小；任一参数无法给出可信读数时为 0
     */
    public static float impulse(float damage, float maxDurability, float mass,
                                float impulsePerRatio, float maxSpeed) {
        if (!(damage > 0f) || !(maxDurability > 0f) || !(mass > 0f)
                || !(impulsePerRatio > 0f) || !(maxSpeed > 0f)) {
            return 0f;
        }
        float slope = impulsePerRatio * (damage / maxDurability);
        float asymptote = mass * maxSpeed;
        // 先逐项取倒数再合成：斜率的分子来自外部伤害值，直接相乘会在极端数值下溢出成 NaN
        float result = 1f / (1f / slope + 1f / asymptote);
        return Float.isFinite(result) && result > 0f ? result : 0f;
    }
}
