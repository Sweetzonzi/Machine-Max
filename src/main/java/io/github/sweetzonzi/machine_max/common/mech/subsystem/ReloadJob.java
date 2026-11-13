package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.common.mech.subsystem.IAmmoSupplier.SupplierRef;

/**
 * 一份装填作业：某台 {@link LauncherSubsystem} 当前正在从供给者处取的那一份交付。
 * <p>
 * <b>归属：</b>进度随消费者的当前作业，供给者只回答"放行条件"与"本 tick 可推进多少"
 * （{@link IAmmoSupplier#getAdvanceTicks(IAmmoConsumer)}），不另持一份针对同次操作的计时。
 * 一台管同一时刻只有一个作业，因此进度、总时长与交付目标各是一个值。
 * <p>
 * <b>时长在开工时定死：</b>{@code totalTicks} 在作业创建时取自供给者当时的
 * {@link IAmmoSupplier#getReloadTimeTicks()}。段内不重算分母，因此型号属性或人员倍率中途改变
 * 只影响下一次作业；进度不会因为分母变化而倒退。
 * <p>
 * <b>不存档：</b>未完成的装填读档后由服务端重新发起，作业不跨存档延续。
 */
public final class ReloadJob {

    /** 本次交付的目标：交出这一份的供给者 */
    private final SupplierRef deliveryTarget;

    /** 本阶段的实际时长（tick），开工时定死 */
    private final int totalTicks;

    /** 已推进的 tick 数，单调不减，最大等于 {@link #totalTicks} */
    private int elapsedTicks;

    public ReloadJob(SupplierRef deliveryTarget, int totalTicks) {
        this.deliveryTarget = deliveryTarget;
        this.totalTicks = Math.max(1, totalTicks);
    }

    /** 本次交付的目标供给者身份 */
    public SupplierRef deliveryTarget() {
        return deliveryTarget;
    }

    /** 本阶段的实际时长（tick） */
    public int totalTicks() {
        return totalTicks;
    }

    /** 已推进的 tick 数 */
    public int elapsedTicks() {
        return elapsedTicks;
    }

    /** 距离阶段完成还剩多少 tick */
    public int remainingTicks() {
        return Math.max(0, totalTicks - elapsedTicks);
    }

    /** 已完成比例（0~1） */
    public float progress() {
        return Math.clamp((float) elapsedTicks / totalTicks, 0f, 1f);
    }

    /** 本阶段是否已走完 */
    public boolean isComplete() {
        return elapsedTicks >= totalTicks;
    }

    /**
     * 推进本 tick 的时间。推进量由执行者给出，作业只负责累计。
     *
     * @param ticks 本 tick 可推进的 tick 数；{@code <= 0} 表示本 tick 推不动（缺弹、机构受阻）
     */
    public void advance(int ticks) {
        if (ticks <= 0) return;
        elapsedTicks = Math.min(totalTicks, elapsedTicks + ticks);
    }
}
