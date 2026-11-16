package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.common.mech.ammo.AmmoBelt;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import org.jetbrains.annotations.Nullable;

/**
 * 弹药提供者接口：库存的持有者，也是交付实例的制造者。<br>
 * 它只回答三件事——<b>可工作条件</b>、<b>库存承诺</b>（整次作业一份预留）与<b>交付本身</b>；本段的时间、
 * 执行资源与推进量归运输者（{@link IAmmoTransporter}），进度与阶段归接收者的 {@link ReloadJob}。
 * <p>
 * <b>提供者不认识接收者。</b>预留、兑现与撤销只按作业身份 {@link ReloadJobKey} 记账，参数里没有消费者；
 * "哪根管占着这一份"由身份里的宿主 SubPart ID 与序号表达。一次作业一份预留，预留量
 * {@code q = min(可分配库存, 规格长度)}，{@code 0 ≤ Σq ≤ N}，按身份撤销与兑现。
 * <p>
 * <b>交付单位是一份 {@link AmmoBelt}：</b>{@link #getDeliverySpec()} 预览下一份的满额规格，
 * {@link #deliverReserved} 兑现预留并交出独立实例（发数是该作业的 {@code q}），
 * {@link #returnBelt} 全收或全拒地回收。校验在接收者拉到交付之后执行——提供者不判断接收者收不收。
 * <p>
 * <b>撤销归一：</b>正常收尾由接收者调用 {@link #releaseReservation}；运输者只在失效清理（消费者已无机会
 * 收尾）时按身份代撤销。{@link #holdsReservation} 是"那份预留还在吗"的读口，服务端读预留表，客户端按
 * "实际库存不少于该作业份额"保守回答。
 */
public interface IAmmoSupplier {

    /**
     * 装填阶段：没有进行中的作业时提供者此刻在忙什么。作业本身的推进由接收者的 {@link ReloadJob} 表达；
     * 本枚举只服务显示层——它据它区分"等待供弹"与"等待再生"。
     */
    enum ReloadPhase {
        /** 没有进行中的装填或再生 */
        IDLE,
        /** 一份交付正在推进（接收者有作业） */
        LOADING,
        /** 弹仓补货中：按速率逐发再生下一发 */
        REGENERATING,
        /** 弹仓补货中：打空后的整批回充冷却 */
        REFILLING
    }

    /**
     * 受理结果：本条作业被接单时授予的分配。<br>
     * 只在 {@link #reserve} 成功时产生一次，由接收者立刻写进自己的 {@link ReloadJob}；它不是一个可长期
     * 持有的状态对象。来源由调用方指名的那台提供者给出，消费方把它写成 {@link AmmoRef#ofProvider}，
     * 因此不需要第三个字段。
     *
     * @param spec          本次交付的规格（模式、长度、标签与回充配置）
     * @param reservedCount 为本次作业预留的弹药数量 {@code q}，{@code > 0}
     */
    record Grant(AmmoBelt spec, int reservedCount) {
    }

    /**
     * 提供者自身此刻是否具备交付能力：机构启用、未摧毁（含 30% 恢复锁存）、供能条件满足。<br>
     * 与"有没有可分配的弹"（{@link #hasAmmo()}）和"本次作业有没有有效预留"（{@link #holdsReservation}）
     * 分开：空仓但机构健康为 true（它照常再生），满仓但机构损坏为 false。
     * <p>
     * 不启动计时、不修改库存，因此可以在每个 tick 的路由里调用。修理完成后无需重新握手。
     */
    boolean isSupplyOperational();

    /**
     * 剩余弹药计数，供显示与选择：按提供者的口径报<b>可申请</b>的数量（已按身份预留的那一份不计入）。
     * -1 表示无计数概念（如无限供给）。
     */
    int getRemainingCount();

    /**
     * 此刻是否有可供<b>新作业</b>申请的弹药：{@link #getRemainingCount()} 大于 0（-1 表示无计数概念，
     * 按有货）。判据是"有没有可分配的弹"，不是"够不够一整份"：不足一份时交付剩余的全部。
     */
    default boolean hasAmmo() {
        int count = getRemainingCount();
        return count < 0 || count > 0;
    }

    /** 下一发的弹种，null = 无弹药或无交付规格 */
    @Nullable ProjectileType getSuppliedType();

    /**
     * 下一份交付的内容规格：满额模板（{@code remaining} 等于 {@code length}，回充运行态为零）。<br>
     * 供路由在取货前检查长度与全部弹种，也用作弹药池的分组键；查询不扣库存、不启动计时。
     * 实际交付的实例可以比它少发（源库存不足一份时按剩余发数交付），规格本身仍是这一份。
     * <p>
     * 返回的对象是共享的只读模板，调用方不得原地推进它；要交付就返回 {@link #deliverReserved} 的实例。
     */
    @Nullable AmmoBelt getDeliverySpec();

    /**
     * 提供者的最大容量。-1 表示无容量概念（如无限供给）。
     */
    default int getCapacity() {
        return -1;
    }

    /**
     * 为一条作业预留整次作业的弹药：可分配库存立即减少，实际库存不动。<br>
     * 预留量取 {@code q = min(可分配库存, 规格长度)}，不足一份也能开工（交付时按 {@code q} 发）。
     * 同一身份重复申请沿用原份额，不叠加；{@link AmmoRef#NONE} 与客户端调用都不产生预留。
     * <p><b>调用线程：</b>主线程（服务端受理点）。
     *
     * @param key 作业身份
     * @return 本次授予的规格与份额；没有可分配库存或本机不可工作时返回 {@code null}
     */
    @Nullable Grant reserve(ReloadJobKey key);

    /**
     * 兑现一条作业的预留：按该身份扣减实际库存并交出独立实例。<br>
     * 同一身份只兑现一次——预留在此处删除，重复调用返回 {@code null}，不会扣第二次。
     * <p><b>调用线程：</b>主线程（服务端交付点）。
     *
     * @param key 作业身份
     * @return 交付实例，发数等于该作业的预留量；没有预留时返回 {@code null}
     */
    @Nullable AmmoBelt deliverReserved(ReloadJobKey key);

    /**
     * 按身份撤销一条作业的预留：只减少承诺，不改动实际库存、不补弹。<br>
     * 重复撤销、撤销不存在的身份都是无操作。正常收尾只有接收者在写这张表。
     * <p><b>调用线程：</b>主线程（服务端）。
     */
    void releaseReservation(ReloadJobKey key);

    /**
     * 该作业的预留是否还在。<br>
     * 服务端读预留表（忽略 {@code reservedCount}）；客户端没有整张表，只能按"实际库存不少于本作业份额"
     * 回答，{@code reservedCount} 就是那个份额。两端问的是同一个问题——"你手上还有这一份吗"——
     * 只是证据不同。
     *
     * @param key           作业身份
     * @param reservedCount 该作业的份额 {@code q}
     */
    boolean holdsReservation(ReloadJobKey key, int reservedCount);

    /**
     * 接收一份退还的弹药。<br>
     * <b>收不收由提供者自己判</b>——内容里的弹种是不是自己允许的、弹仓当前能不能储存、剩余容量是否装得下，
     * 都是提供者内部的规则。全收或全拒：拒收时不改动传入对象。
     *
     * @param belt 退还的那一份弹药
     * @return true 表示已收下
     */
    boolean returnBelt(AmmoBelt belt);

    /**
     * HUD 弹链名。<br>
     * 返回值<b>仅供显示</b>：不得用于比对、查找或路由。需要标识弹链时用 {@link #getDeliverySpec()} 的内容
     * 规格，需要标识弹种时用 {@link #getSuppliedType()}。
     * <p><b>仅在客户端调用</b>：文本取自玩家语言的翻译键。
     */
    String getLabel();

    /**
     * 没有进行中的作业时，提供者此刻在忙哪一段。<br>
     * 默认不区分再生形态（{@link ReloadPhase#IDLE}）：只有按速率补货的实现才覆写。
     */
    default ReloadPhase getRefillPhase() {
        return ReloadPhase.IDLE;
    }

    /** 当前再生阶段的进度（0~1）；没有再生时返回 0。 */
    default float getRefillProgress() {
        return 0f;
    }

    /** 当前再生阶段剩余 tick；没有再生时返回 0。 */
    default int getRefillRemainingTicks() {
        return 0;
    }
}
