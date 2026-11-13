package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.common.mech.ammo.AmmoBelt;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 弹药供给者接口。<br>
 * 供给者提供的是<b>可工作条件</b>与<b>交付本身</b>：消费者（{@link LauncherSubsystem}）在自己的
 * {@link ReloadJob} 里推进这份装填的时间，每 tick 向供给者索取"本 tick 能推进多少"
 * （{@link #getAdvanceTicks(IAmmoConsumer)}），走完后取走一份 {@link AmmoBelt}。供给者不再为任何
 * 消费者保存一份并行计时，也不向消费者推送。
 * <p>
 * 交付单位是一份 Belt 实例：{@link #getDeliverySpec()} 预览下一份的满额规格，
 * {@link #consumeReadyBelt(IAmmoConsumer)} 扣减库存并交出独立实例，{@link #returnBelt(AmmoBelt)}
 * 全收或全拒地回收。校验在消费者拉到交付之后执行——供给者不判断消费者收不收。
 * <p>
 * <b>双端契约：</b>"哪根管由哪台供给者供弹"是服务端单方面的决定，客户端经
 * {@link IAmmoConsumer} 上的同步指认跟随（身份由 {@link SupplierRef} 表达）。
 * 取弹链只在服务端执行；客户端从发射器上的同步值读取膛内弹、已装 Belt 与作业进度，只驱动本机表现。
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
     * 当前是否有可交付的弹药。<br>
     * 判据是"有没有弹"，不是"够不够一整份"：库存不足一份时供给者交付剩余的全部，
     * 具体发数由 {@link #consumeReadyBelt(IAmmoConsumer)} 返回的实例表达。
     */
    boolean hasAmmo();

    /**
     * 供给者此刻是否具备交付能力：机构、储存结构、宿主与自身状态都允许交付。<br>
     * 这是"能不能送"的实时判断，与"有没有弹"（{@link #hasAmmo()}）是两件事：
     * 空仓但机构健康为 true（它照常再生），满仓但机构损坏为 false。
     * <p>
     * 不启动计时、不修改库存，因此可以在每个 tick 的路由里调用。修理完成后无需重新握手，
     * 调用方下次判定即看到 true。
     * <p>
     * 武器控制器用它在选取、保持与回退供给者时实时过滤；供给者自己也要用它在
     * {@link #getAdvanceTicks(IAmmoConsumer)} 与 {@link #consumeReadyBelt(IAmmoConsumer)}
     * 两个入口挡下请求，否则绕过控制器的直接请求仍能取走弹药。
     */
    boolean isSupplyOperational();

    /** 剩余弹药计数。-1 表示无计数概念（如无限供给） */
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
     * 为一条已受理的作业占用本机。<br>
     * 受理点只有一处：消费者在开工时用即将写进自己作业的 {@code jobId} 调用本方法。返回 {@code true}
     * 表示这份作业被接单，此后 {@link #getAdvanceTicks(IAmmoConsumer)} 与
     * {@link #consumeReadyBelt(IAmmoConsumer)} 只认这一条占用；返回 {@code false} 表示本 tick 接不了单
     * （不可交付、没有可交付的库存、独占槽位已被别的活跃作业占着），消费者因此不建作业，下个 tick
     * 再问一次。未被受理的请求不写记录，所以它既不占槽也不挡别人。
     * <p>
     * 记录里只有消费者引用与该作业的 {@code jobId}，不复制作业内容；不入存档也不同步——读档后第一次
     * 受理重新建立。消费者写作业与调用本方法是同一次受理，因此记录里的编号总能与对方当前作业对上。
     *
     * @param consumer 申请开工的消费者
     * @param jobId    它即将写入作业的编号
     * @return true 表示已受理并记下这份占用
     */
    boolean claimFor(IAmmoConsumer consumer, int jobId);

    /**
     * 按身份释放占用。<br>
     * 只有记录里的 {@code jobId} 与给定值相同才删除：旧作业的回调因此不会摘掉同一消费者的新作业。
     * 重复释放、释放别人、释放一条已经不存在的记录都是无操作，也不会改动库存。
     *
     * @param consumer 结束作业的消费者
     * @param jobId    这条作业的编号，必须是当初 {@link #claimFor} 收到的那个值
     */
    void releaseClaim(IAmmoConsumer consumer, int jobId);

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
     * 本 tick 能为该消费者的作业推进多少 tick。<br>
     * 这是供给者唯一的"放行"入口：{@code 0} 表示本 tick 推不动（缺弹、机构损坏、供能不足、
     * 槽位被别的作业占着），正数表示推进量。调用方把返回值累计进
     * {@link ReloadJob#advance(int)}，因此推进速度由供给者表达，进度仍归消费者。
     * <p>
     * 本方法与 {@link #consumeReadyBelt(IAmmoConsumer)} 都必须用 {@link #isSupplyOperational()}
     * 挡下失效的机构，否则绕过控制器的直接请求仍能取走弹药。
     * <p>
     * {@code can_supply_multiple=false} 的机构一次只为一条作业推进：别的活跃消费者已经绑着本机时，
     * 未绑定者拿到 {@code 0}——一条输弹线一次只移动一份，等待方下个 tick 再问。
     *
     * @param consumer 请求放行的消费者
     * @return 本 tick 可推进的 tick 数；{@code 0} 表示本 tick 推不动
     */
    int getAdvanceTicks(IAmmoConsumer consumer);

    /**
     * 取走一份已经备好的交付。<br>
     * 消费者在作业走完后调用；扣减发生在这一次调用里，此前库存未动。返回的实例归消费者所有，
     * 源不再持有或更新它。
     * <p>
     * 实例的发数由供给者按自己的库存决定，可以少于 {@link #getDeliverySpec()} 的长度：规格
     * （模式、长度、标签、回充）与规格一致，只有 {@code remaining} 变小。消费者据此仍认得出
     * "这是同一份弹链"，不会把一次缩水的交付当成换装。
     *
     * @return 一份独立实例的 {@link AmmoBelt}；本机不可交付或已无库存时返回 null
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
     * 供给者身份：宿主 RegularPart 的 uuid + SubPart 名 + 子系统名。<br>
     * 三者的作用域都是<b>本次会话内的这一台载具</b>：宿主 RegularPart 的 uuid 随载具数据同步到客户端
     * （{@code RegularPartData.uuid}），SubPart 名与子系统名则由内容包定义，因此同一个值在两端指的是同一台
     * 机器。载具从模板重建时 RegularPart 的 uuid 会重新生成，所以这个身份只用于端到端的指认传递，
     * 不作为存档地址。
     * <p>
     * SubPart 的 int id 不满足要求：它由会话内计数器分配，客户端那份是从载具数据包里恢复的，
     * 只够运行期定位。
     * <p>
     * 空值用 {@link #NONE} 表达（{@code partUuid} 取 nil uuid；真实部件的 uuid 来自
     * {@link java.util.UUID#randomUUID()}，不会是 nil）。
     * <p>
     * {@link #compareTo} 给出与内容无关的全序，供路由在候选排序里做稳定的决胜键。
     */
    record SupplierRef(UUID partUuid, String subPartName, String subsystemName)
            implements Comparable<SupplierRef> {

        /** 无供给者 */
        public static final SupplierRef NONE = new SupplierRef(new UUID(0L, 0L), "", "");

        /** 通过网络传输的编解码器：UUID + 两个 UTF-8 名 */
        public static final StreamCodec<ByteBuf, SupplierRef> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, SupplierRef::partUuid,
                ByteBufCodecs.STRING_UTF8, SupplierRef::subPartName,
                ByteBufCodecs.STRING_UTF8, SupplierRef::subsystemName,
                SupplierRef::new);

        /** 是否为空值 */
        public boolean isNone() {
            return NONE.partUuid.equals(partUuid);
        }

        /**
         * 取某台供给者的身份。<br>
         * 供给者不是子系统、或拿不到宿主 RegularPart 时返回 {@link #NONE}。
         */
        public static SupplierRef of(@Nullable IAmmoSupplier supplier) {
            if (supplier instanceof AbstractSubsystem sub) {
                SubPart subPart = sub.getSubPart();
                var part = subPart.part;
                if (part != null) {
                    return new SupplierRef(part.uuid, subPart.name, sub.getName());
                }
            }
            return NONE;
        }

        /**
         * 先按宿主 RegularPart uuid、再按 SubPart 名与子系统名的全序。
         */
        @Override
        public int compareTo(SupplierRef other) {
            int byPart = partUuid.compareTo(other.partUuid);
            if (byPart != 0) return byPart;
            int bySubPart = subPartName.compareTo(other.subPartName);
            return bySubPart != 0 ? bySubPart : subsystemName.compareTo(other.subsystemName);
        }
    }

}
