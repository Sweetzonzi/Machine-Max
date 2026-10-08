package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.Part;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 弹药供给者接口。<br>
 * 供给者负责管理弹药储备、装填计时和弹药交付。
 * 消费者调用 requestRound → 等待 isRoundReady → consumeReadyRound 三步完成弹药获取。
 * <p>
 * <b>双端契约：</b>"哪根管由哪台供给者供弹"是服务端单方面的决定，客户端经
 * {@link IAmmoConsumer} 上的同步指认跟随（身份由 {@link SupplierRef} 表达）。
 * 客户端同样会跑完整的 {@code requestRound → isRoundReady → consumeReadyRound} 链，
 * 但它只驱动本机表现——装填进度、装填音效、开火特效；库存计数与弹链指针只有服务端写。
 */
public interface IAmmoSupplier {

    /**
     * 供给者当前工作状态枚举。<br>
     * 由供给者自行报告，消除消费者侧的 instanceof 分支。
     */
    enum SupplierStatus {
        /** 正常可用，有弹药待交付 */
        READY,
        /** 弹药耗尽 */
        EMPTY,
        /** 装填或冷却中 */
        RELOADING
    }

    /** 当前是否有可用弹药 */
    boolean hasAmmo();

    /** 剩余弹药计数。-1 表示无计数概念（如再生装弹机） */
    int getRemainingCount();

    /** 当前将提供的弹药类型，null = 无弹药 */
    @Nullable ProjectileType getSuppliedType();

    /** 是否为逐发装填模式 */
    boolean isRoundByRound();

    /** 装填耗时（tick） */
    int getReloadTimeTicks();

    /** 是否允许多个消费者同时等待装填 */
    boolean canSupplyMultiple();

    /**
     * 该消费者是否已被本供给者开始交付（交付计时中，或弹药已就绪待取）。<br>
     * 这是"轮到我了"的唯一判据：
     * <ul>
     *   <li>{@code canSupplyMultiple()=true} — 请求被接受即开始交付，本方法随即为 true</li>
     *   <li>{@code canSupplyMultiple()=false} — 同一时刻只有一位消费者被交付，
     *       未排上的请求返回 true 但本方法保持 false，消费者下个 tick 重发请求即可再试</li>
     * </ul>
     * 因此"排队"不是持久状态，而是"本次未被排上"的瞬时结果，供给者无需为它记录队列。
     * <p>
     * 与 {@link #getStatus(IAmmoConsumer)} 的区别：状态里的 {@code RELOADING} 既可能是"已轮到它"，
     * 也可能是"它还没被排上"，本方法只回答前者。
     * <p>
     * 未覆写时返回 false，调用方按"尚未轮到"处理。
     *
     * @param consumer 请求查询的消费者
     * @return true 表示该消费者正被交付
     */
    default boolean isRoundScheduled(IAmmoConsumer consumer) {
        return false;
    }

    /**
     * 是否已有消费者被开始交付（等价于"这条交付通道已被占"）。<br>
     * 独占供给者用它决定能否再接一位消费者；武器控制器用它判断这台装弹机是不是已经
     * 名花有主、别的发射器该去找别人。允许并行供弹（{@link #canSupplyMultiple()}）时该标志
     * 不构成限制，多根管可以同时挂在同一台上。
     * <p>
     * 未覆写时返回 false，调用方按"无人交付"处理。
     */
    default boolean hasScheduledRound() {
        return false;
    }

    /**
     * 取消该消费者在本供给者处<b>进行中或尚未取走</b>的交付，让那发重新可被分配。<br>
     * 由消费者在换供给者时调用（见 {@code LauncherSubsystem.setCurrentSupplier}），
     * 否则旧供给者会一直为一位已经不管它的消费者留着这条交付通道，
     * 弹仓里那发既不会被取走，也不会被别的消费者使用。
     * <p>
     * 调用时该消费者可能处于两种状态，本方法都要覆盖：交付<b>计时中</b>，或弹药<b>已就绪待取</b>。
     * 取消后（独占模式下）这条通道立刻空出，等待中的消费者下次请求即可被排上。
     * <p>
     * 与 {@link #returnRound(ProjectileType)} 的分工：那个是"把已经拿到手的弹药还回来"，
     * 会改变库存计数；本方法只取消"尚未取走"的交付承诺，而承诺阶段库存本来就没扣，
     * 所以<b>不要在这里补弹</b>——本接口的既有实现都只在
     * {@link #consumeReadyRound(IAmmoConsumer)} 里扣减库存，补弹会让同一发被算两次。
     * 只有"在承诺阶段就已扣出库存"的实现才需要归还，由该实现自行处理。
     * <p>
     * 未覆写时为空操作。
     *
     * @param consumer 要取消交付的消费者
     */
    default void cancelRound(IAmmoConsumer consumer) {
    }

    /**
     * HUD 弹链名。
     * <p>
     * 返回值<b>仅供显示</b>：不得用于比对、查找或路由。名字是给玩家读的语义标签，
     * 两条构成不同的弹链可以合法地显示同一个名字（例如都以穿甲弹开头的两条弹链）。
     * 需要标识弹链时用展平弹链列表（{@code RegenLoaderSubsystem.getProjectileTypes()}），
     * 需要标识弹种时用 {@link #getSuppliedType()} 返回的 {@link ProjectileType} 实例。
     * <p>
     * <b>仅在客户端调用</b>：文本取自玩家语言的翻译键，服务端的语言表不含内容包的键。
     */
    String getLabel();

    /**
     * 请求一发弹药，启动非阻塞装填计时器。<br>
     * 幂等性：同一 consumer 多次调用不会创建重复计时器。
     *
     * @return true 表示请求已接受（首次请求或已在装填中）
     */
    boolean requestRound(IAmmoConsumer consumer);

    /** 指定消费者的弹药是否已就绪 */
    boolean isRoundReady(IAmmoConsumer consumer);

    /**
     * 消费已就绪的弹药。<br>
     * 仅在 {@link #isRoundReady(IAmmoConsumer)} 返回 true 时调用。
     *
     * @return 弹药类型，若未就绪则返回 null
     */
    @Nullable ProjectileType consumeReadyRound(IAmmoConsumer consumer);

    /**
     * 供给者的最大容量。-1 表示无容量概念（如无限供给）。
     * 由 AmmoLoaderSubsystem / RegenLoaderSubsystem 等具体实现类重写返回各自容量。
     */
    default int getCapacity() {
        return -1;
    }

    /**
     * 供给者当前工作状态（per-consumer）。<br>
     * 由具体供给者自行报告，无需消费者做 instanceof 判断。
     *
     * @param consumer 请求状态的消费者
     * @return 当前状态
     */
    default SupplierStatus getStatus(IAmmoConsumer consumer) {
        return getRemainingCount() > 0 ? SupplierStatus.READY : SupplierStatus.EMPTY;
    }

    /**
     * 装填/再生进度（0~1）。<br>
     *
     * @param consumer 请求进度的消费者
     * @return 进度值 0.0~1.0
     */
    float getReloadProgress(IAmmoConsumer consumer);

    /**
     * 该消费者在本供给者处这次输送已经走了多少 tick。<br>
     * 与 {@link #getReloadProgress(IAmmoConsumer)} 的分工：那个是给显示用的比例（会被钳位、
     * 在没有计时器时可能退化成别的含义），这里是内部计时的原始刻度，供调用方判断"这一发是否
     * 还值得改主意"（例如武器控制器只在开装后的头几 tick 内允许换装弹机）。
     * <p>
     * 未覆写时返回 -1，调用方按"没有为本消费者输送"处理。
     *
     * @param consumer 请求查询的消费者
     * @return 已输送的 tick 数；本供给者没有为它输送时返回 -1
     */
    default int getDeliveryElapsedTicks(IAmmoConsumer consumer) {
        return -1;
    }

    /**
     * 接收一发退还的弹药。<br>
     * <b>收不收由供给者自己判</b>——弹种是不是自己这条弹链上的、弹仓还有没有位置，
     * 都是供给者内部的规则，消费者不该替它做判断，因此本方法返回是否收下。
     * <p>
     * 典型调用点是发射器退膛：退膛的弹想还给供给者，被拒（弹种不符或已满）时由消费者
     * 自行处置（例如掉成物品）。
     *
     * @param type 退还的弹药类型
     * @return true 表示已收下；false 表示未接收（弹种不符或无空位）
     */
    boolean returnRound(ProjectileType type);

    /**
     * 供给者身份：宿主 Part 的 uuid + SubPart 名 + 子系统名。<br>
     * 三者都随存档持久化（{@code PartData.uuid}、SubPart 名、子系统名），因此这个身份在重启、
     * 换会话、两端之间都指同一台机器，可以直接写进 NBT，也可以直接跨端传递。
     * SubPart 的 int id 不满足这个要求：它由会话内计数器分配，存档里不带它，
     * 客户端那份是从载具数据包里恢复的，只够运行期定位，不能当身份用。
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
         * 供给者不是子系统、或拿不到宿主 Part 时返回 {@link #NONE}。
         */
        public static SupplierRef of(@Nullable IAmmoSupplier supplier) {
            if (supplier instanceof AbstractSubsystem sub) {
                SubPart subPart = sub.getSubPart();
                Part part = subPart.part;
                if (part != null) {
                    return new SupplierRef(part.uuid, subPart.name, sub.getName());
                }
            }
            return NONE;
        }

        /**
         * 先按宿主 Part uuid、再按 SubPart 名与子系统名的全序。
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
