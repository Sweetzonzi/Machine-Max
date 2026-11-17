package io.github.sweetzonzi.machine_max.common.attachment;

import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.IAmmoConsumer;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ReloadJob;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ReloadJobKey;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.common.registry.MMAttributes;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * 一个生物的人工装填能力。<br>
 * 能力属于实际的人，不属于座位也不属于武器：同一个人在不同武器、不同座位上的差别由"需求方 → 座位"那条
 * 发现关系的岗位倍率表达（见装填计划 §4.1），这里只持有这个人自己的量。
 * <p>
 * <b>数据在属性上，本类只做合成与认领。</b>装填速度记在 {@link MMAttributes#CREW_RELOAD_SPEED} 上——
 * 属性能存档、能随原版机制同步、能被装备与 {@code /attribute} 改，本类因此不持有任何可存盘字段，
 * 也就没有编解码器与复制策略。有效时间倍率 {@link #effectiveMultiplier()} 每次读取时由属性值与药水效果
 * 现算：两层都不落地，效果到期时没有需要撤销的中间状态。
 * <p>
 * <b>两个量分开归属：</b>时间倍率 {@code A_eff} 是能力（属性层 × 药水层，1 为标准，小于 1 更快），
 * 认领是此刻在替哪条作业干活（不持久化）。一次只能认领一条作业——同一实际乘员一次只推进一个人工装填段，
 * 多台武器同时需要人时由各自的选择结果竞争，本附件不排队也不抢占。
 * <p>
 * <b>认领与释放都按作业身份：</b>{@link #claim} 在作业受理时写入，{@link #release} 只对身份匹配的那条生效，
 * 因此旧作业的回调摘不掉新作业的认领。认领本身还带一层自检：被认领的消费者已经收尾或换了作业时，
 * 这条认领按不存在处理（见 {@link #isClaimAlive()}），所以漏掉一次释放不会把一个人永久锁在旧作业上。
 * <p>
 * <b>只在服务端有意义：</b>倍率由服务端读来算人工段时长，客户端读座位下发的候选快照；认领也只由服务端
 * 写入与查询。客户端的同一份契约由 {@link io.github.sweetzonzi.machine_max.common.mech.ObjectManager} 的
 * 客户端 Level 认领表承担（按人物 UUID 记录，判据与本类相同），因此选人与承诺判据在两端是同一套业务条件。
 */
public class CrewReloadAttachment {

    /** 标准人力：属性值与药水层都无加成时的有效时间倍率 */
    public static final double DEFAULT_MULTIPLIER = 1.0;

    /** 每级净药水等级改动的装填速度：急迫每级 +20%，挖掘疲劳每级 −20% */
    public static final double SPEED_PER_LEVEL = 0.2;

    /** 药水层的速度下限：挖掘疲劳再深也不会慢过标准人力的两倍 */
    public static final double MIN_EFFECT_SPEED = 0.5;

    /** 药水层的速度上限：急迫再高也不会快过标准人力的两倍 */
    public static final double MAX_EFFECT_SPEED = 2.0;

    /** 本附件所属的生物 */
    private final LivingEntity owner;

    /** 正在推进的作业身份；null = 空闲 */
    @Nullable
    private ReloadJobKey claimedKey;

    /** 认领这条作业的消费者；与 {@link #claimedKey} 一起给出完整身份 */
    @Nullable
    private IAmmoConsumer claimedBy;

    public CrewReloadAttachment(LivingEntity owner) {
        this.owner = owner;
    }

    /**
     * 取某个生物的人工装填能力；该实体还没有创建过这份附件时按注册的工厂新建一份。<br>
     * 玩家、NPC 与机娘共用这一条路径，不按宿主类型分支。
     *
     * @param entity 宿主生物
     */
    public static CrewReloadAttachment of(LivingEntity entity) {
        return entity.getData(MMAttachments.getENTITY_CREW_RELOAD().get());
    }

    /**
     * 这个人的有效时间倍率 {@code A_eff}：属性层（{@link MMAttributes#CREW_RELOAD_SPEED}）与药水层
     * （{@link #effectSpeed()}）之积的倒数。小于 1 用时较短，大于 1 用时较长。
     * <p>
     * 两层都在读取时合成，不写任何持久状态，因此效果到期、装备被摘掉都不需要撤销动作。
     * <p>
     * 除数恒为正：属性值由 {@code RangedAttribute} 钳在 {@code [0.25, 4.0]}（NaN 也落到下限），药水层钳在
     * {@code [MIN_EFFECT_SPEED, MAX_EFFECT_SPEED]}，两个下界都大于 0，所以除法本身不需要兜底。
     */
    public double effectiveMultiplier() {
        return 1.0 / (owner.getAttributeValue(MMAttributes.CREW_RELOAD_SPEED) * effectSpeed());
    }

    /**
     * 药水层的装填速度倍率：急迫与挖掘疲劳的<b>净等级</b>按 {@link #SPEED_PER_LEVEL} 线性折算，
     * 再钳到 {@code [MIN_EFFECT_SPEED, MAX_EFFECT_SPEED]}。
     * <p>
     * 两者相减而不是相乘：速度是一个物理量，只有一条曲线与一个上下界。相乘会让"急迫 II ＋ 挖掘疲劳 II"
     * 得到 1.167 而不是标准人力，且两个各自钳位的因子相乘能把结果推到边界之外。
     * <p>
     * 只读等级，不读剩余时间：剩余时间每 tick 都变，折进倍率会让每一 tick 都触发一次重定时。
     */
    private double effectSpeed() {
        int netLevel = effectLevel(MobEffects.DIG_SPEED) - effectLevel(MobEffects.DIG_SLOWDOWN);
        return Math.clamp(1.0 + SPEED_PER_LEVEL * netLevel, MIN_EFFECT_SPEED, MAX_EFFECT_SPEED);
    }

    /**
     * 某个效果在本实体上的等级（amplifier + 1）；没有该效果时为 0。
     *
     * @param effect 效果
     */
    private int effectLevel(Holder<MobEffect> effect) {
        MobEffectInstance instance = owner.getEffect(effect);
        return instance == null ? 0 : instance.getAmplifier() + 1;
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
