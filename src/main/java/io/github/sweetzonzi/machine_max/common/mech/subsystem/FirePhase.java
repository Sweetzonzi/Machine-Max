package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import java.util.function.BooleanSupplier;

/**
 * 发射器的射速相位：把"每 tick 该打几发"表达成整数相位与冷却两个有界相对量。
 *
 * <p>单位与周期：每业务 tick 记 {@link #TICK_UNITS} 个相位单位，
 * {@code intervalU = round(1200 × TICK_UNITS / RPM)}。全部算术在 {@code long} 里进行，
 * 因为 {@code fire_rate} 没有下界，极低射速的周期远超 int 范围。</p>
 *
 * <p>三个量各管一件事，不能互相顶替：{@link #phaseU()} 是本次连射累计的相对相位；
 * {@link #cooldownU()} 是上一业务边界距下一发的剩余量，跨松键、失效与换装保留，用来约束
 * 连按；{@link #isBurstActive()} 表示是否处于一次连射中，决定下一次按下是否播种相位。</p>
 *
 * <p>没有绝对时刻：调度只依赖实际执行的业务 tick 调用次数，因此暂停、客户端晚创建与两端
 * {@code tickCount} 数值不同都不需要时钟原点换算。</p>
 *
 * <p><b>调用线程：</b>主线程（由 {@link LauncherSubsystem#onTick()} 每轮驱动一次）。</p>
 */
public final class FirePhase {

    /** 一个业务 tick 的相位单位数。 */
    public static final long TICK_UNITS = 1_000_000L;

    private long phaseU;
    private long cooldownU;
    private boolean burstActive;
    private int firedCount;
    private boolean starved;

    /**
     * 把射速换成整数周期。
     *
     * <p>周期至少为一个相位单位：射速高到量化后不足一个单位时判为不可用，而不是截到 1——
     * 截断会让一个业务 tick 试图打出上百万发。</p>
     *
     * @param rpm 每分钟发数
     * @return 周期（相位单位）；{@code 0} 表示该射速不可用（非正、非有限、量化后小于一个单位，
     *         或周期超出 long 可表示范围）
     */
    public static long intervalUFor(float rpm) {
        if (!Float.isFinite(rpm) || rpm <= 0f) return 0L;
        double interval = 1200.0 * TICK_UNITS / rpm;
        // 相位要能容纳 intervalU + TICK_UNITS 而不溢出
        if (!(interval <= (double) (Long.MAX_VALUE - TICK_UNITS))) return 0L;
        long rounded = Math.round(interval);
        return rounded < 1L ? 0L : rounded;
    }

    /**
     * 推进一个业务 tick。
     *
     * <p>不可用、换装锁定或扳机松开时结束本次连射：相位归零、连射标记清除，冷却只按本 tick 递减，
     * 因此快速连按不能重置低射速武器的间隔。否则先给相位播种（首次按下时为
     * {@code intervalU - cooldownU}，即刚好到期就能发）再累加一个 tick，然后按严格大于判发射：
     * 恰好落在本业务步右端的发射留给下一业务步。</p>
     *
     * <p>已经到发射点却确实缺弹时，把逾期量收敛为"当前已就绪"的 {@code intervalU}，
     * 不蓄积历史欠发；缺弹不清零相位，也不清扳机事实。正常连射则完整保留除法余量。</p>
     *
     * @param intervalU  本 tick 的整数周期；{@code < 1} 视同武器不可用
     * @param triggerHeld 本轮是否既有可用武器又按着扳机
     * @param shot       尝试发射一发；成功消费并提交返回 {@code true}
     * @return 本轮实际发射数
     */
    public int tick(long intervalU, boolean triggerHeld, BooleanSupplier shot) {
        firedCount = 0;
        starved = false;
        if (!triggerHeld || intervalU < 1L) {
            stop();
            return 0;
        }
        // 周期可能随模块受损或升级改变：先按新周期夹住冷却，播种才不会得到负相位
        cooldownU = Math.clamp(cooldownU, 0L, intervalU);
        if (!burstActive) {
            phaseU = Math.clamp(intervalU - cooldownU, 0L, intervalU);
            burstActive = true;
        }
        phaseU += TICK_UNITS;
        while (phaseU > intervalU) {
            if (!shot.getAsBoolean()) {
                phaseU = intervalU;
                starved = true;
                break;
            }
            phaseU -= intervalU;
            firedCount++;
        }
        cooldownU = intervalU - phaseU;
        return firedCount;
    }

    /**
     * 结束本次连射：相位归零、连射标记清除，防连点冷却按一个业务 tick 递减。
     * <p>
     * 冷却保留下界为 0，因此"松键后马上再按"能立刻发射的前提是冷却已经到期——
     * 低射速武器的间隔不会被连按重置成一个完整新间隔。
     */
    public void stop() {
        firedCount = 0;
        starved = false;
        phaseU = 0L;
        burstActive = false;
        cooldownU = Math.max(cooldownU - TICK_UNITS, 0L);
    }

    /** 本次连射累计的相对相位（相位单位）。 */
    public long phaseU() {
        return phaseU;
    }

    /** 上一业务边界距下一发的剩余量（相位单位）；松键与失效期间按业务 tick 递减。 */
    public long cooldownU() {
        return cooldownU;
    }

    /** 是否处于一次连射中。 */
    public boolean isBurstActive() {
        return burstActive;
    }

    /** 最近一次 {@link #tick} 实际发射数。 */
    public int firedCount() {
        return firedCount;
    }

    /** 最近一次 {@link #tick} 是否因为已经在发射点却没有可发射内容而中断。 */
    public boolean starved() {
        return starved;
    }
}
