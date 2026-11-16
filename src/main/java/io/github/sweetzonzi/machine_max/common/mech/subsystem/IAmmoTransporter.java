package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 弹药运输者接口：把弹药从提供者搬到接收者的那一段。<br>
 * 它拥有<b>本段的时间基准</b>、<b>执行资源</b>（机械槽或一名乘员）与<b>本 tick 能推进多少</b>，
 * 不拥有库存、预留与交付实例——那些归提供者；进度与阶段归接收者的 {@link ReloadJob}。
 * <p>
 * <b>两个实现，两种形态：</b>{@link AmmoLoaderSubsystem} 是机械运输者（有机械段，可选一段人工准备），
 * {@link LoaderRoster} 是人力运输者（整条运输由乘员完成，没有机械段与喂弹线）。类型决定
 * {@link #isHuman()}，不由时长推断：
 *
 * <pre>
 * isHuman()  ⇒ getReloadTimeTicks() == 0 &amp;&amp; getFeedSources().isEmpty()
 * !isHuman() ⇒ getReloadTimeTicks() &gt; 0
 * </pre>
 * <p>
 * <b>受理分两步，都由接收者发起：</b>先 {@link #claim} 取执行资源，再向提供者取预留（提供者不在本接口的
 * 受理与推进签名里）。{@code claim} 可重入且幂等：同一身份重复调用只补齐本阶段的资源、并按当前候选换人，
 * 不追加预留、不重置进度。人工段每 tick 调它一次，因此"换不换人"与"还有没有人"是同一个入口。
 * 任一步失败时接收者撤销已取得的那一份（{@link #releaseClaim}），不建作业、不开始计时。
 * <p>
 * <b>撤销归接收者：</b>{@link #releaseClaim} 只释放本机资源，不碰提供者的预留表；正常收尾由接收者按身份
 * 撤销预留。唯一例外是失效清理——消费者已被摧毁或停用、再没有机会走收尾时，运输者在剔除自己的失效占用时
 * 按身份撤销对应预留，否则那份预留会永久占住库存。
 */
public interface IAmmoTransporter {

    /**
     * 是不是人力运输者：整条运输由一名乘员完成。<br>
     * 由类型决定（roster 恒为真、装弹机恒为假），是构造时就成立的事实，不是从时长推断出来的；
     * 它与两条时长、喂弹线的一致性在解码期校验一次（见类文档）。
     */
    boolean isHuman();

    /** 本机此刻可工作：启用、未摧毁（含恢复锁存）、供能与机构条件满足；人力运输者恒 {@code true}。 */
    boolean isOperational();

    /**
     * 能否同时为多条作业运输。<br>
     * 唯一的产能声明：{@code false} 的运输者一次只推进一条作业，别的作业这一 tick 拿到 0，路由也不把
     * 已被占用的它放进候选；{@code true} 允许并行。人力运输者恒为 {@code false}。提供者没有产能声明——
     * 同一份库存的独占性由预留表保证。
     */
    boolean canServeMultipleConsumers();

    /**
     * 人工段的基准时长（tick，标准人力，未乘倍率）；0 表示没有人工段。<br>
     * 人力运输者为 0 时不进入路由（{@link LoaderRoster#discover(int)} 收到 0 即清空基准时长），
     * 因此"被选中的运输者有人工段"在运行期恒成立。
     */
    int getHumanReloadTimeTicks();

    /**
     * 机械段的基准时长（tick）；0 表示没有机械段。<br>
     * 接收者在进入该段时读一次并写进作业，段内不再改读。
     */
    int getReloadTimeTicks();

    /**
     * 喂弹线：本机从哪些提供者取弹。<br>
     * 机械运输者交出自己发现到的架（可以暂时为空——那是结构变化后的短暂窗口，此时它没有可用的路，
     * 不是配置错误）；人力运输者恒为空表，它的提供者取接收者自己发现到的架。
     */
    List<IAmmoSupplier> getFeedSources();

    /**
     * 在喂弹线里按身份找一台提供者。<br>
     * 客户端判"这份预留还在"时只问作业上的那一台，不退回别的架。
     *
     * @param ref 提供者身份；{@link AmmoRef#NONE} 一律不匹配
     * @return 对应实例；喂弹线里没有它时返回 {@code null}
     */
    default @Nullable IAmmoSupplier findProvider(AmmoRef ref) {
        if (ref == null || ref.isNone()) return null;
        for (IAmmoSupplier provider : getFeedSources()) {
            if (AmmoRef.ofProvider(provider).equals(ref)) return provider;
        }
        return null;
    }

    /**
     * 为本阶段取得（或维持）执行资源。<br>
     * 人力运输者认领一名乘员；没有人工段的机械运输者取机械槽；有人工段的机械运输者在人工段里认领乘员。
     * 可重入且幂等：同一身份重复调用只补本阶段资源并按当前候选换人。
     *
     * @param consumer 申请开工的接收者
     * @param key      它即将写进作业的身份
     * @return true 表示此刻这条作业有可用的执行者；false 表示本 tick 接不了单
     */
    boolean claim(IAmmoConsumer consumer, ReloadJobKey key);

    /**
     * 人工段结束时补齐机械槽（并把人工段的执行者认领交还）。<br>
     * 只有同时声明了人工段与机械段的机械运输者会收到它；人力运输者与全自动机构不会走到这里，
     * 默认实现恒为 {@code true}（没有槽位要补）。
     */
    default boolean claimForMachineStage(IAmmoConsumer consumer, ReloadJobKey key) {
        return true;
    }

    /**
     * 本 tick 能为该消费者的作业推进多少 tick。<br>
     * 接收者唯一的放行入口：{@code 0} 表示本 tick 推不动（本机不可工作、槽位被别的作业占着、乘员离场），
     * 正数表示推进量。人工段的时长已把岗位倍率与个人倍率折进去，人员变化由
     * {@link ReloadJob#retimeHuman(double)} 换算剩余量，因此人工段的推进量恒为 1。
     *
     * @param consumer 请求放行的接收者
     * @return 本 tick 可推进的 tick 数
     */
    int getAdvanceTicks(IAmmoConsumer consumer);

    /**
     * 该消费者的作业在本机的执行资源是否还在（机械槽或乘员认领）。<br>
     * {@code false} 表示这份作业已经推不动也不会有交付，接收者按失效收尾而不等暂停超时。它只回答"本机
     * 资源"，预留那一半由接收者按作业上的提供者判（{@link IAmmoSupplier#holdsReservation}）。
     * <p>
     * 读之前先剔除失效占用，因此返回值反映此刻的真实状态。人工段的乘员离场不算承诺消失——人可以在下一
     * tick 换一个，那时本方法仍为 {@code true}，推进量为 0。
     */
    boolean holdsCommitment(IAmmoConsumer consumer);

    /**
     * 询问的接收者之外，是否已有活跃作业占着本机。<br>
     * 路由用它把忙着的独占运输者排除在候选之外——被占的机器根本不进候选。{@code canServeMultipleConsumers}
     * 为真的运输者允许并行，恒为 {@code false}。
     */
    boolean isClaimedByOther(IAmmoConsumer consumer);

    /**
     * 按身份释放本机的执行资源（机械槽与乘员认领）。<br>
     * 身份不匹配时无操作，旧作业的回调因此摘不掉新作业的占用。释放不改变提供者的预留表——撤销归接收者，
     * 唯一例外是失效清理。
     */
    void releaseClaim(IAmmoConsumer consumer, ReloadJobKey key);

    /**
     * 已认领执行者的人工段时间倍率（岗位倍率 {@code P} × 个人倍率 {@code A}）。<br>
     * 接收者每 tick 现读并用于换人重定时；没有人工段或没有认领时返回 1。
     */
    double getOperatorTimeScale(IAmmoConsumer consumer);

    /**
     * 沿这条路，接收者还要多久拿到下一份交付（tick），各类加成由本机折算。<br>
     * 这是路由排序与换人比较的唯一代价，接收者与武器控制器都只读这一个值：
     *
     * <pre>
     * !isOperational()                                    → Integer.MAX_VALUE
     * 提供者为空或 !provider.isSupplyOperational()         → Integer.MAX_VALUE
     * 作业已在这条路上（运输者与提供者都对得上）
     *     人工段 → 本阶段剩余（已含 P × A）+ 机械段
     *     机械段 → 本阶段剩余
     * 作业不在这条路上
     *     提供者的可分配库存为 0（-1 表示无计数概念，按有货） → Integer.MAX_VALUE
     *     人工段选不到任何候选乘员                            → Integer.MAX_VALUE
     *     其余                                               → 人工段标准剩余 × P × A + 机械段
     * </pre>
     * <p>
     * 估价是一次只读的选人（不认领、不改预留），因此调用方在同一次选路内按整条路复用一次结果；它不写回
     * 发现表或弹药池，也不跨 tick 缓存。
     */
    int estimateTicks(IAmmoConsumer consumer, IAmmoSupplier provider);

    /**
     * 按接收者作业的当前进度播放本机负责的那一段装填音效。<br>
     * 已播游标由运输者自己记录，同一段不重播；人工段的声音定位在接收者（人把弹交到炮闩跟前），机械段
     * 定位在本机。默认空实现：没有进度音效的运输者不必覆写。
     * <p>
     * <b>仅在客户端调用</b>，每个客户端 tick 一次。
     *
     * @param consumer 正在装填的接收者
     * @param progress 该作业当前进度（0~1）
     */
    default void tickStageSounds(IAmmoConsumer consumer, float progress) {
    }
}
