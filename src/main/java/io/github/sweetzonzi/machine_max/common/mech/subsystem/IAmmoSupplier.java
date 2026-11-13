package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.common.mech.ammo.AmmoBelt;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

/**
 * 弹药供给者接口。<br>
 * 供给者提供的是<b>可工作条件</b>、<b>库存承诺</b>与<b>交付本身</b>：消费者（{@link LauncherSubsystem}）
 * 在自己的 {@link ReloadJob} 里推进这份装填的时间，每 tick 向供给者索取"本 tick 能推进多少"
 * （{@link #getAdvanceTicks(IAmmoConsumer)}），走完后兑现那份预留、取走一份 {@link AmmoBelt}。
 * 供给者为任何消费者都不保存并行计时，也不向消费者推送进度。
 * <p>
 * <b>两段承诺：</b>受理时（{@link #claimFor}）一次取得本条作业的弹药预留与机构槽位，此后这条作业按
 * 自己的预留推进，不受其他作业抢走可分配库存的影响；交付时（{@link #consumeReadyBelt}）把预留兑现成
 * 实际扣减。取消、换人、来源失效与摧毁都按身份释放预留（{@link #releaseClaim}），释放只减少承诺、
 * 不给库存补数。
 * <p>
 * 交付单位是一份 Belt 实例：{@link #getDeliverySpec()} 预览下一份的满额规格，
 * {@link #consumeReadyBelt(IAmmoConsumer)} 兑现并交出独立实例，{@link #returnBelt(AmmoBelt)}
 * 全收或全拒地回收。校验在消费者拉到交付之后执行——供给者不判断消费者收不收。
 * <p>
 * <b>双端契约：</b>"哪根管由哪台供给者供弹"是服务端单方面的决定，客户端经
 * {@link IAmmoConsumer} 上的同步指认与作业字段跟随（身份由 {@link SupplierRef} 表达）。分配、占用与
 * 预留只在服务端决定；客户端按同一份已发布状态自行推进计时并显示，不重做竞争与库存扣减。
 */
public interface IAmmoSupplier {

    /**
     * 装填阶段：消费者此刻等的是哪一段。<br>
     * 作业本身的推进由消费者的 {@link ReloadJob} 表达；本枚举只描述"没有作业时供给者在忙什么"，
     * 以及显示层该用哪种进度口径。
     */
    enum ReloadPhase {
        /** 没有进行中的装填或再生 */
        IDLE,
        /** 一份交付正在推进（消费者有作业） */
        LOADING,
        /** 弹仓补货中：按速率逐发再生下一发 */
        REGENERATING,
        /** 弹仓补货中：打空后的整批回充冷却 */
        REFILLING
    }

    /**
     * 此刻是否有可供<b>新作业</b>申请的弹药。<br>
     * 读的是可分配数量（实际库存减去已预留总量），已被其他作业预留的那一份不再对外供应；已有预留的作业
     * 不看这个值（见 {@link #getAdvanceTicks(IAmmoConsumer)}）。判据是"有没有可分配的弹"，不是"够不够
     * 一整份"：不足一份时交付剩余的全部，具体发数由 {@link #consumeReadyBelt(IAmmoConsumer)} 返回的
     * 实例表达。
     */
    boolean hasAmmo();

    /**
     * 供给者自身此刻是否具备交付能力：机构启用、未摧毁、供能条件满足。<br>
     * 这是三个判断里的第一个（机构自身可工作），与"有没有可分配的弹"（{@link #hasAmmo()}）和
     * "本次作业有没有有效预留"（{@link #getAdvanceTicks(IAmmoConsumer)}）分开：空仓但机构健康为 true
     * （它照常再生），满仓但机构损坏为 false。上游架是否存在、是否可工作属于来源可用性，由供给者
     * 在受理与交付时按作业的来源判定，不并入本方法——否则一台暂时没接弹药架的机构会被报成"机械故障"。
     * <p>
     * 不启动计时、不修改库存，因此可以在每个 tick 的路由里调用。修理完成后无需重新握手，
     * 调用方下次判定即看到 true。
     * <p>
     * 武器控制器用它在选取、保持与回退供给者时实时过滤；供给者自己也要用它在
     * {@link #getAdvanceTicks(IAmmoConsumer)} 与 {@link #consumeReadyBelt(IAmmoConsumer)}
     * 两个入口挡下请求，否则绕过控制器的直接请求仍能取走弹药。
     */
    boolean isSupplyOperational();

    /**
     * 剩余弹药计数，供显示与选择：按供给者的口径报<b>可申请</b>的数量（已按身份预留的那一份不计入）。
     * -1 表示无计数概念（如无限供给）。
     */
    int getRemainingCount();

    /** 下一发的弹种，null = 无弹药或无交付规格 */
    @Nullable ProjectileType getSuppliedType();

    /**
     * 下一份交付的内容规格：满额模板（{@code remaining} 等于 {@code length}，回充运行态为零）。<br>
     * 供路由在取货前检查长度与全部弹种，也用作弹药池的分组键；查询不扣库存、不启动计时。
     * 实际交付的实例可以比它少发（源库存不足一份时按剩余发数交付），规格本身仍是这一份。
     * 没有可交付规格的供给者（例如物品读写尚未实现的装弹机）返回 null，不进入候选。
     * <p>
     * 返回的对象是共享的只读模板，调用方不得原地推进它；要交付就返回 {@link #consumeReadyBelt} 的实例。
     */
    @Nullable AmmoBelt getDeliverySpec();

    /**
     * 交付一份 Belt 的机械段基准时长（tick）。<br>
     * 消费者在开工时把它写进 {@link ReloadJob#totalTicks()}，此后不再改读本方法。
     */
    int getReloadTimeTicks();

    /** 是否允许多个消费者同时等待装填 */
    boolean canSupplyMultiple();

    /**
     * 受理结果：本条作业被接单时授予的分配。<br>
     * 只在 {@link #claimFor} 受理成功时产生一次，由消费者立刻写进自己的 {@link ReloadJob}；它不是一个可长期
     * 持有的状态对象，消费者之外的任何地方都不保存它。
     *
     * @param origin        本次交付的实际来源弹药架
     * @param spec          本次交付的规格（模式、长度、标签与回充配置）
     * @param reservedCount 为本次作业预留的弹药数量 {@code q}，{@code > 0}
     */
    record Grant(SupplierRef origin, AmmoBelt spec, int reservedCount) {
    }

    /**
     * 为一条作业占用本机，并取得本次交付的弹药预留。<br>
     * 受理点只有一处：消费者在开工时用即将写进自己作业的身份调用本方法。两项资源在同一次调用里取得——
     * 机构槽位（仅独占机构）与实际架的 {@code q} 发预留；任一项失败就撤销本次已取得的资源并返回
     * {@code null}，消费者因此不建作业、不开始计时，下个 tick 再问一次。未被受理的请求不写记录，
     * 所以它既不占槽也不挡别人。
     * <p>
     * 记录里只有消费者引用与该作业的身份，不复制作业内容；预留记在来源弹药架上，键是同一个身份。
     * 两者都不入存档：读档后第一次受理重新建立。消费者写作业与调用本方法是同一次受理，
     * 因此记录里的身份总能与对方当前作业对上。
     *
     * @param consumer 申请开工的消费者
     * @param key      它即将写进作业的身份
     * @return 本次授予的来源与份额；{@code null} 表示本 tick 接不了单
     */
    @Nullable Grant claimFor(IAmmoConsumer consumer, ReloadJobKey key);

    /**
     * 按身份释放占用与弹药预留。<br>
     * 只有记录里的身份与给定值相同才处理：旧作业的回调因此不会摘掉同一消费者的新作业，也不会释放新作业
     * 的预留。释放只减少承诺，不改动实际库存、不补弹。重复释放、释放别人、释放一条已经不存在的记录
     * 都是无操作。
     *
     * @param consumer 结束作业的消费者
     * @param key      这条作业的身份，必须是当初 {@link #claimFor} 收到的那个值
     */
    void releaseClaim(IAmmoConsumer consumer, ReloadJobKey key);

    /**
     * 询问的消费者之外，是否已有活跃作业占着本机。<br>
     * 路由用它把忙着的独占机构排除在候选之外——被占的机器根本不进候选，N 根管不会轮抢同一台，
     * 只会摊到 N 台上；摊不开时那根管如实显示无供给者。这比"排到末位"更强，也免得等待方每 tick
     * 被选上又拿不到推进量。
     * <p>
     * 读之前先剔除失效记录（消费者已摧毁／失活、指认已换、当前作业缺失或编号对不上），因此
     * 返回值反映的是此刻真正在推进的作业，而不是历史残骸。{@code can_supply_multiple=true} 的机构
     * 允许并行，恒为 {@code false}。
     *
     * @param consumer 询问的消费者
     * @return true 表示本机已被属于别人的作业占着
     */
    boolean isClaimedByOther(IAmmoConsumer consumer);

    /**
     * 该消费者的作业在本机是否仍有有效承诺：占用还在，且该身份的弹药预留还在。<br>
     * {@code false} 表示这份作业已经推不动也不会有交付——机构换了手、来源架失效、份额被撤销或已兑现。
     * 消费者据此按失效收尾并让路由重选，不必等暂停超时；把它与"本 tick 推不动"分开，是因为暂停只是
     * 暂时不前进，承诺本身还在。
     * <p>
     * 读之前同样先剔除失效占用，因此返回值反映的是此刻这条作业的真实状态。
     *
     * @param consumer 询问的消费者
     * @return true 表示本机仍为这条作业保留着承诺
     */
    boolean holdsCommitment(IAmmoConsumer consumer);

    /**
     * 本 tick 能为该消费者的作业推进多少 tick。<br>
     * 这是供给者唯一的"放行"入口：{@code 0} 表示本 tick 推不动（机构损坏、供能不足、槽位被别的作业
     * 占着、本作业的来源预留已不存在），正数表示推进量。调用方把返回值累计进
     * {@link ReloadJob#advance(int)}，因此推进速度由供给者表达，进度仍归消费者。
     * <p>
     * 已受理的作业按<b>自己的预留</b>推进：只要该身份的预留还在、机构还能工作，可分配数量为 0 也不
     * 影响它——那份弹已经记在这条作业名下。反过来，预留被撤销（来源失效、强制损失、作业被终止）时
     * 本方法返回 {@code 0}，消费者随后按失效路径收尾。
     * <p>
     * 本方法与 {@link #consumeReadyBelt(IAmmoConsumer)} 都必须用 {@link #isSupplyOperational()}
     * 挡下失效的机构，否则绕过控制器的直接请求仍能取走弹药。
     * <p>
     * {@code can_supply_multiple=false} 的机构一次只为一条作业推进：别的活跃作业已经占着本机时，
     * 未占用者拿到 {@code 0}——一条输弹线一次只移动一份，等待方下个 tick 再问。
     *
     * @param consumer 请求放行的消费者
     * @return 本 tick 可推进的 tick 数；{@code 0} 表示本 tick 推不动
     */
    int getAdvanceTicks(IAmmoConsumer consumer);

    /**
     * 兑现一份预留，把它变成一份交付实例。<br>
     * 消费者在作业走完后调用。交付量取作业的预留份额：实际库存已按该份额预留，因此这一步只把承诺变成
     * 实际扣减，不需要再争抢库存。同一身份最多兑现一次——预留被兑现后即删除，重复调用返回
     * {@code null}，不会扣第二次。
     * <p>
     * 返回的实例发数为预留量 {@code q}，规格与作业的 {@link ReloadJob#spec()} 一致（模式、长度、标签、
     * 回充），只有 {@code remaining} 可能小于规格长度。实例归消费者所有，源不再持有或更新它。
     *
     * @param consumer 走完本阶段、来兑现的消费者
     * @return 一份独立实例的 {@link AmmoBelt}；本机不可交付、作业缺失或该身份没有预留时返回 null
     */
    @Nullable AmmoBelt consumeReadyBelt(IAmmoConsumer consumer);

    /**
     * 供给者的最大容量。-1 表示无容量概念（如无限供给）。
     * 由 {@code AmmoLoaderSubsystem} 等具体实现类重写返回各自容量；机构不持有库存，
     * 返回值取自它的上游弹药架。
     */
    default int getCapacity() {
        return -1;
    }

    /**
     * 没有进行中的作业时，供给者在忙哪一段。<br>
     * 默认不区分再生形态（{@link ReloadPhase#IDLE}）：只有按速率补货的实现才覆写。
     * 显示层用它把"等待供弹"与"等待再生"分开，秒数才算在正确的阶段上。
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

    /**
     * 按消费者作业的当前进度播放本机负责的那一段装填音效。<br>
     * 机械段的声音定位在装弹机上，因此由供给者自己播放；消费者只交来进度，已播阈值由供给者记录，
     * 同一段不重播。默认空实现：没有进度音效的供给者不必覆写。
     * <p>
     * <b>仅在客户端调用</b>，每个客户端 tick 一次。
     *
     * @param consumer 正在装填的消费者
     * @param progress 该消费者当前作业的进度（0~1）
     */
    default void tickStageSounds(IAmmoConsumer consumer, float progress) {
    }

    /**
     * 接收一份退还的弹药。<br>
     * <b>收不收由供给者自己判</b>——内容里的弹种是不是自己允许的、弹仓当前能不能储存
     * （坏掉的弹仓不收，否则等于往里灌弹药）、剩余容量是否装得下这一份，都是供给者内部的规则，
     * 消费者不该替它做判断，因此本方法返回是否收下。全收或全拒：拒收时不改动传入对象。
     * <p>
     * 典型调用点是发射器退膛与整份换装：被拒时由消费者保留这一份，不能原地丢弃。
     *
     * @param belt 退还的那一份弹药
     * @return true 表示已收下；false 表示未接收（弹种不被允许、弹仓不可储存或容量不足）
     */
    boolean returnBelt(AmmoBelt belt);

    /**
     * HUD 弹链名。
     * <p>
     * 返回值<b>仅供显示</b>：不得用于比对、查找或路由。名字是给玩家读的语义标签，
     * 两条构成不同的弹链可以合法地显示同一个名字（例如都以穿甲弹开头的两条弹链）。
     * 需要标识弹链时用 {@link #getDeliverySpec()} 的内容规格，
     * 需要标识弹种时用 {@link #getSuppliedType()} 返回的 {@link ProjectileType} 实例。
     * <p>
     * <b>仅在客户端调用</b>：文本取自玩家语言的翻译键，服务端的语言表不含内容包的键。
     */
    String getLabel();

    /**
     * 子系统身份：宿主 SubPart 的 int ID + 子系统名。<br>
     * 作用域是<b>本次会话内的这一台载具</b>：SubPart 的 int ID 由服务端创建载体时分配并随载具数据下发，
     * 客户端拿到的就是服务端那一份；子系统名由内容包定义。两端因此用同一个值指认同一台机器，
     * 不需要跨端传对象引用。
     * <p>
     * 同一形状承担两个角色，不是两种地址：{@code deliveryTarget} 指本次把弹药交到手上的对象，
     * {@code upstreamOrigin} 指机构为这次作业挑中的上游弹药架。作业不保存第三个"来源"字段。
     * <p>
     * 用这个短地址而不是宿主 Part 的 uuid：uuid 随载具模板重建而重新生成，来源关系又不持久化
     * （读档后作业重新受理），没有需要它在重启后保持稳定的地方。
     * <p>
     * 空值用 {@link #NONE} 表达（{@code subPartId} 为 {@code -1}；真实 SubPart ID 非负）。
     * <p>
     * {@link #compareTo} 给出与内容无关的全序，供路由在候选排序里做稳定的决胜键。
     */
    record SupplierRef(int subPartId, String subsystemName)
            implements Comparable<SupplierRef> {

        /** 无供给者 */
        public static final SupplierRef NONE = new SupplierRef(-1, "");

        /** 通过网络传输的编解码器：宿主 ID + UTF-8 子系统名 */
        public static final StreamCodec<ByteBuf, SupplierRef> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SupplierRef::subPartId,
                ByteBufCodecs.STRING_UTF8, SupplierRef::subsystemName,
                SupplierRef::new);

        /** 是否为空值 */
        public boolean isNone() {
            return subPartId < 0;
        }

        /**
         * 取某台供给者的身份。<br>
         * 供给者不是子系统时返回 {@link #NONE}。
         */
        public static SupplierRef of(@Nullable IAmmoSupplier supplier) {
            return supplier instanceof AbstractSubsystem sub ? of(sub) : NONE;
        }

        /**
         * 取某个子系统的身份：宿主 SubPart ID + 子系统名。<br>
         * 上游弹药架不是供给者接口的实现，但作业要按同一个形状记录它，因此这里也提供子系统重载。
         */
        public static SupplierRef of(AbstractSubsystem subsystem) {
            return new SupplierRef(subsystem.getSubPart().getId(), subsystem.getName());
        }

        /**
         * 先按宿主 SubPart ID、再按子系统名的全序。
         */
        @Override
        public int compareTo(SupplierRef other) {
            int bySubPart = Integer.compare(subPartId, other.subPartId);
            return bySubPart != 0 ? bySubPart : subsystemName.compareTo(other.subsystemName);
        }
    }

}
