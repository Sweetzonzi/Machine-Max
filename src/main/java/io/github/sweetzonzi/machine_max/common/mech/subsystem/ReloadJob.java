package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.common.mech.subsystem.IAmmoSupplier.SupplierRef;

/**
 * 一份装填作业：某台 {@link LauncherSubsystem} 当前正在从供给者处取的那一份交付。
 * <p>
 * <b>归属：</b>进度随消费者的当前作业，供给者只回答"放行条件"与"本 tick 可推进多少"
 * （{@link IAmmoSupplier#getAdvanceTicks(IAmmoConsumer)}），不另持一份针对同次操作的计时。
 * 一台管同一时刻只有一个作业，因此进度、总时长与交付目标各是一个值。
 * <p>
 * <b>身份：</b>{@link #jobId()} 由持有它的 Launcher 在受理时分配并自增，与宿主 SubPart 的 int ID、
 * 子系统名组成 {@code (subPartId, launcherName, jobId)}。机构占用与架预留都以这个三元组为键：释放、
 * 交付与结束结果只对身份完全匹配的作业生效，因此旧作业的回调不会摘掉同一条输弹线上的新作业。
 * 作业不跨存档延续，读档后重新受理会拿到新的编号。
 * <p>
 * <b>时长在开工时定死：</b>{@code totalTicks} 在作业创建时取自供给者当时的
 * {@link IAmmoSupplier#getReloadTimeTicks()}。段内不重算分母，因此型号属性或人员倍率中途改变
 * 只影响下一次作业；进度不会因为分母变化而倒退。
 * <p>
 * <b>不存档：</b>未完成的装填读档后由服务端重新发起，作业不跨存档延续。
 */
public final class ReloadJob {

    /**
     * 尚未携带服务端身份的作业编号：客户端镜像在收到身份字段之前用它。<br>
     * 服务端分配的编号从 1 起，这个值因此不会与真实作业冲突。
     */
    public static final int UNASSIGNED_JOB_ID = 0;

    /** 作业身份，由持有它的 Launcher 在受理时分配 */
    private final int jobId;

    /** 本次交付的目标：交出这一份的供给者 */
    private final SupplierRef deliveryTarget;

    /** 本阶段的实际时长（tick），开工时定死 */
    private final int totalTicks;

    /** 已推进的 tick 数，单调不减，最大等于 {@link #totalTicks} */
    private int elapsedTicks;

    public ReloadJob(int jobId, SupplierRef deliveryTarget, int totalTicks) {
        this.jobId = jobId;
        this.deliveryTarget = deliveryTarget;
        this.totalTicks = Math.max(1, totalTicks);
    }

    /**
     * 作业身份；{@link #UNASSIGNED_JOB_ID} 表示这份副本还没携带服务端分配的身份。<br>
     * 占用与预留的释放都以它为准，因此同一台管先后两次作业不会互相认领。
     */
    public int jobId() {
        return jobId;
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
