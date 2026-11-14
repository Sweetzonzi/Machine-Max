package io.github.sweetzonzi.machine_max.common.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.IAmmoConsumer;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ReloadJob;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ReloadJobKey;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * 一个生物的人工装填能力。<br>
 * 能力属于实际的人，不属于座位也不属于武器：同一个人在不同武器、不同座位上的差别由"需求方 → 座位"那条
 * 发现关系的岗位倍率表达（见装填计划 §4.1），这里只持有这个人自己的量。
 * <p>
 * <b>两个量分开归属：</b>个人倍率 {@code A} 是能力（持久化，1 为标准，小于 1 更快），认领是此刻在替哪条
 * 作业干活（不持久化）。一次只能认领一条作业——同一实际乘员一次只推进一个人工装填段，多台武器同时需要人时
 * 由各自的选择结果竞争，本附件不排队也不抢占。
 * <p>
 * <b>认领与释放都按作业身份：</b>{@link #claim} 在作业受理时写入，{@link #release} 只对身份匹配的那条生效，
 * 因此旧作业的回调摘不掉新作业的认领。认领本身还带一层自检：被认领的消费者已经收尾或换了作业时，
 * 这条认领按不存在处理（见 {@link #isClaimAlive()}），所以漏掉一次释放不会把一个人永久锁在旧作业上。
 * <p>
 * <b>只在服务端有意义：</b>倍率由服务端读来算人工段时长，客户端不读它；认领也只由服务端写入与查询。
 */
public class CrewReloadAttachment {

    /** 个人倍率下限：再快也不超过标准人力的四倍 */
    public static final double MIN_MULTIPLIER = 0.25;

    /** 个人倍率上限：再慢也不低于标准人力的四分之一 */
    public static final double MAX_MULTIPLIER = 4.0;

    /** 标准人力 */
    public static final double DEFAULT_MULTIPLIER = 1.0;

    /** 个人倍率 {@code A}：小于 1 用时较短，大于 1 用时较长 */
    private double multiplier = DEFAULT_MULTIPLIER;

    /** 正在推进的作业身份；null = 空闲 */
    @Nullable
    private ReloadJobKey claimedKey;

    /** 认领这条作业的消费者；与 {@link #claimedKey} 一起给出完整身份 */
    @Nullable
    private IAmmoConsumer claimedBy;

    /**
     * 只持久化个人倍率——认领是运行期关系，不跨存档，也不跟着实体一起保存。
     */
    public static final Codec<CrewReloadAttachment> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("multiplier", DEFAULT_MULTIPLIER)
                    .forGetter(CrewReloadAttachment::multiplier)
    ).apply(instance, CrewReloadAttachment::new));

    public CrewReloadAttachment(double multiplier) {
        setMultiplier(multiplier);
    }

    public CrewReloadAttachment() {
    }

    /**
     * 取某个生物的人工装填能力；没有创建过该附件时按注册的工厂新建一份。<br>
     * 玩家、NPC 与机娘共用这一条路径，不按宿主类型分支。
     */
    public static CrewReloadAttachment of(LivingEntity entity) {
        return entity.getData(MMAttachments.getENTITY_CREW_RELOAD().get());
    }

    /**
     * 把原始倍率规整成可用的值：非有限值按 {@link #DEFAULT_MULTIPLIER} 处理，超出范围按边界钳位。
     * <p>非有限值不抛异常——它可能来自数据包或存档，一处坏数据不该让整辆车的装填停摆。
     *
     * @param raw 原始倍率
     * @return {@code [MIN_MULTIPLIER, MAX_MULTIPLIER]} 内的有限值
     */
    public static double sanitizeMultiplier(double raw) {
        if (!Double.isFinite(raw)) return DEFAULT_MULTIPLIER;
        return Math.clamp(raw, MIN_MULTIPLIER, MAX_MULTIPLIER);
    }

    /** 个人倍率 {@code A} */
    public double multiplier() {
        return multiplier;
    }

    /**
     * 写入个人倍率。<br>
     * 越界与非有限值按 {@link #sanitizeMultiplier(double)} 规整；被改动过时记一条警告，便于定位坏数据。
     *
     * @param raw 原始倍率
     */
    public void setMultiplier(double raw) {
        double safe = sanitizeMultiplier(raw);
        if (safe != raw) {
            MachineMax.LOGGER.warn("人工装填个人倍率 {} 不在 [{}, {}] 内或不是有限数，已按 {} 处理",
                    raw, MIN_MULTIPLIER, MAX_MULTIPLIER, safe);
        }
        this.multiplier = safe;
    }

    /**
     * 认领一条作业。<br>
     * 已经为别的作业认领时返回 false（一个人一次只能推进一段人工装填）；已经为同一消费者认领时按成功处理，
     * 只是把身份换成新值。失效的旧认领不阻挡新人。
     *
     * @param consumer 认领这条作业的消费者
     * @param key      作业身份
     * @return true 表示此刻这条认领归给定作业所有
     */
    public boolean claim(IAmmoConsumer consumer, ReloadJobKey key) {
        if (key == null || key.isNone()) return false;
        if (isClaimAlive() && claimedBy != consumer) return false;
        claimedKey = key;
        claimedBy = consumer;
        return true;
    }

    /**
     * 按身份释放认领；身份不匹配、或本来就没有认领时是无操作。
     *
     * @param key 结束的那条作业的身份
     * @return true 表示这条认领确实因本次调用被清掉
     */
    public boolean release(ReloadJobKey key) {
        if (claimedKey == null || !claimedKey.equals(key)) return false;
        claimedKey = null;
        claimedBy = null;
        return true;
    }

    /**
     * 这条认领是否仍指向一条真的在进行的作业。<br>
     * 消费者已摧毁或失活、当前作业为空、编号与认领不符，都说明这条认领是残骸：按不存在处理，
     * 让别处可以重新认领这个人，不必等一次显式释放。
     */
    private boolean isClaimAlive() {
        if (claimedKey == null || claimedBy == null) return false;
        if (claimedBy instanceof AbstractSubsystem sub && (sub.isDestroyed() || !sub.isActive())) return false;
        ReloadJob job = claimedBy.getReloadJob();
        return job != null && job.key().equals(claimedKey);
    }

    /**
     * 这个人是否正被<em>别人</em>的作业占着。<br>
     * 选人时用它把已经在干活的乘员排除在候选之外。
     *
     * @param consumer 询问的消费者
     */
    public boolean isClaimedByOther(IAmmoConsumer consumer) {
        return isClaimAlive() && claimedBy != consumer;
    }

    /**
     * 这条认领是否就是给定消费者此刻的那条作业。<br>
     * 人工段每 tick 用它判断"人还在替这条作业干活"，为 false 时由持有者决定换人还是暂停。
     *
     * @param consumer 询问的消费者
     */
    public boolean holdsClaim(IAmmoConsumer consumer) {
        return isClaimAlive() && claimedBy == consumer;
    }

    /** 是否空闲（没有活着的认领） */
    public boolean isIdle() {
        return !isClaimAlive();
    }
}
