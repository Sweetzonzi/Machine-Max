package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.common.mech.ammo.AmmoBelt;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.IAmmoSupplier.SupplierRef;

/**
 * 一份装填作业：某台 {@link LauncherSubsystem} 当前正在取的那一份交付。
 * <p>
 * <b>归属：</b>进度随消费者的当前作业，供给者只回答"放行条件"与"本 tick 可推进多少"
 * （{@link IAmmoSupplier#getAdvanceTicks(IAmmoConsumer)}），不另持一份针对同次操作的计时。
 * 一台管同一时刻只有一个作业，因此进度、总时长与交付目标各是一个值。
 * <p>
 * <b>身份：</b>{@link #key()} 由持有它的 Launcher 在受理时分配，{@code jobId} 自增。机构占用、实际架弹药
 * 预留与独占机构的槽位拥有者都以这个身份为键：释放、交付与结束只对身份完全匹配的作业生效，因此旧作业的
 * 回调不会摘掉同一条输弹线上的新作业。作业不跨存档延续，读档后重新受理会拿到新的编号。
 * <p>
 * <b>分配在受理时冻结：</b>{@link #deliveryTarget()}（交出这一份的机器）、{@link #upstreamOrigin()}
 * （机构为本次作业挑中的上游弹药架，纯人工直取路径为空）、{@link #spec()}（交付规格）与
 * {@link #reservedCount()}（整次作业的弹药预留量）都在 {@link IAmmoSupplier#claimFor} 受理那一刻确定。
 * 此后供给者的交付规格、库存或来源列表变化都不改写这几个值：换架或换规格要作废旧作业并重新申请，
 * 不能把已受理的作业静默换成另一份。
 * <p>
 * <b>时长在开工时定死：</b>{@code totalTicks} 在作业创建时取自供给者当时的
 * {@link IAmmoSupplier#getReloadTimeTicks()}。段内不重算分母，因此型号属性或人员倍率中途改变
 * 只影响下一次作业；进度不会因为分母变化而倒退。
 * <p>
 * <b>不存档：</b>未完成的装填读档后由服务端重新发起，作业不跨存档延续。
 */
public final class ReloadJob {

    /** 作业身份：宿主 SubPart ID + 发射器名 + 编号 */
    private final ReloadJobKey key;

    /** 本次交付的目标：交出这一份的供给者（机构路径是机构，纯人工直取路径是弹药架） */
    private final SupplierRef deliveryTarget;

    /** 本次交付的实际来源弹药架；纯人工直取路径与 {@link #deliveryTarget} 相同，机构路径为上游架 */
    private final SupplierRef upstreamOrigin;

    /** 本次交付的冻结规格：内容、长度与回充配置在受理时定下，交付实例只可能比它少发 */
    private final AmmoBelt spec;

    /** 整次作业的弹药预留量 {@code q}：实际架已按身份扣着这个数量，交付与撤销都以它为准 */
    private final int reservedCount;

    /** 本阶段的实际时长（tick），开工时定死 */
    private final int totalTicks;

    /** 已推进的 tick 数，单调不减，最大等于 {@link #totalTicks} */
    private int elapsedTicks;

    public ReloadJob(ReloadJobKey key, SupplierRef deliveryTarget, SupplierRef upstreamOrigin,
                     AmmoBelt spec, int reservedCount, int totalTicks) {
        this.key = key;
        this.deliveryTarget = deliveryTarget;
        this.upstreamOrigin = upstreamOrigin;
        this.spec = spec;
        this.reservedCount = Math.max(0, reservedCount);
        this.totalTicks = Math.max(1, totalTicks);
    }

    /** 作业身份；机构占用与架预留都以它为准 */
    public ReloadJobKey key() {
        return key;
    }

    /** 作业编号，是 {@link #key()} 的第三项 */
    public int jobId() {
        return key.jobId();
    }

    /** 本次交付的目标供给者身份 */
    public SupplierRef deliveryTarget() {
        return deliveryTarget;
    }

    /**
     * 本次交付的实际来源弹药架身份。<br>
     * 机构路径下由机构在受理时挑定，纯人工直取路径下与 {@link #deliveryTarget()} 相同。
     */
    public SupplierRef upstreamOrigin() {
        return upstreamOrigin;
    }

    /** 本次交付的冻结规格 */
    public AmmoBelt spec() {
        return spec;
    }

    /** 整次作业的弹药预留量；实际架的可分配库存已按它扣减 */
    public int reservedCount() {
        return reservedCount;
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
