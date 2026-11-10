package io.github.sweetzonzi.machine_max.common.mech.vehicle.track;

import cn.solarmoon.spark_core.api.SparkLevel;
import cn.solarmoon.spark_core.util.PPhase;
import com.jme3.bullet.joints.GearJoint;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.fitting.FittingType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.fitting.TrackType;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import jme3utilities.math.MyQuaternion;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 履带：VehicleCore 级的关系 + 状态对象，没有独立刚体。
 *
 * <p>成员是载具内既有的 {@link SubPart}（主动轮 + 下行程负重轮 + 上行程诱导轮 / 托带轮），
 * 所有权仍属于各自 RegularPart。成员顺序就是包络的锚定绕行顺序：主动轮在首位，随后是全部
 * 下行程轮（含被顶起的），再是上行程轮。
 * 成员之间用 {@link GearJoint} 做星形角速度耦合；相邻成员之间用单边弹簧-阻尼力近似
 * 张力。履带耐久是独立于成员轮子耐久的一层。</p>
 *
 * <p>型号（{@link TrackType}）只在创建 / 载入时解析一次；履带同时保留原始型号 id
 * {@link #typeId()}，客户端缺型号时回退默认型号的视觉，但存档与同步仍写回原始 id。</p>
 *
 * <p>线程：主线程负责成员表 / 耐久 / 状态；{@code GearJoint} 的增删与启停、
 * 张力施力都在物理线程执行。</p>
 */
public class TrackLink {
    /**
     * 结构下限：主动轮 + 至少 1 个负重轮。
     *
     * <p>成员低于该值时履带解散；型号不提供可调下限。</p>
     */
    public static final int MIN_MEMBERS = 2;

    /** 已经报过「持久化节数不是周期整数倍」的型号，按型号去重，避免每台车各记一条。 */
    private static final Set<ResourceLocation> WARNED_LINK_COUNT_PERIOD = ConcurrentHashMap.newKeySet();

    private final VehicleCore vehicle;
    private final TrackType type;
    private final ResourceLocation typeId;

    private volatile SubPart sprocket;
    private volatile List<SubPart> members = List.of();
    /** 创建时确定的主动轮局部 (Y,Z) 平面内顺时针绕向。 */
    private final boolean clockwise;
    private volatile List<TensionPair> tensionPairs = List.of();

    private final CopyOnWriteArrayList<GearJoint> joints = new CopyOnWriteArrayList<>();
    private volatile boolean rebuildQueued;
    private volatile boolean disposed;
    private int retryCooldown;

    private volatile float durability;
    private volatile float maxDurability;
    private volatile int linkCount = 1;
    private volatile boolean broken;

    // ===== 轮面音效状态，仅客户端主线程读写 =====
    /** 接地报告的容差：连续两 tick 没有报告才算脱离。 */
    private static final int CONTACT_GRACE_TICKS = 2;
    /** 最近一次收到接地报告时的载具端游戏时间；-1 表示从未收到。 */
    private long contactTick = -1;
    /** 该 tick 全部接地成员里，带速与对地速度之差的最大值（m/s）。 */
    private float contactSlip;
    private final Vector3f contactCentroidSum = new Vector3f();
    private int contactCount;
    /** 带速缓存的归属游戏时间：同一 tick 内多条上报共用一次刚体读数。 */
    private long beltTick = -1;
    private float beltCache;
    @Nullable
    private TrackAudio audio;

    private TrackLink(VehicleCore vehicle, TrackType type, ResourceLocation typeId,
                      float durability, boolean broken, boolean clockwise) {
        this.vehicle = vehicle;
        this.type = type;
        this.typeId = typeId;
        this.durability = Math.max(0f, durability);
        this.maxDurability = type.attr().durabilityPerLink();
        this.broken = broken;
        this.clockwise = clockwise;
    }

    /**
     * 从持久状态重建履带（存档加载 / 客户端应用同步）。
     *
     * @param fallbackToDefault 型号缺失时是否回退默认型号：服务端存档加载传 {@code false}
     *                          （缺型号就丢弃该条履带），客户端同步传 {@code true}
     *                          （缺型号只影响本地表现，保留原始型号 id）
     */
    @Nullable
    public static TrackLink fromData(@NotNull VehicleCore vehicle, @NotNull TrackLinkData data,
                                     boolean fallbackToDefault) {
        TrackType type = resolveTrackType(vehicle.level, data.trackType());
        if (type == null && fallbackToDefault) {
            type = resolveTrackType(vehicle.level, TrackLinkData.DEFAULT_TYPE);
        }
        if (type == null) {
            MachineMax.LOGGER.warn("履带型号 {} 不存在或不是履带类型，已跳过", data.trackType());
            return null;
        }
        SubPart sprocket = resolve(vehicle, data.sprocket());
        if (sprocket == null) {
            MachineMax.LOGGER.warn("履带的主动轮 {} 在载具 {} 中不存在，已跳过", data.sprocket(), vehicle.getUuid());
            return null;
        }
        TrackLink link = new TrackLink(vehicle, type, data.trackType(), data.durability(), data.broken(),
                data.clockwise());
        link.sprocket = sprocket;
        List<SubPart> members = new ArrayList<>();
        for (TrackMemberRef ref : data.members()) {
            SubPart member = resolve(vehicle, ref);
            if (member != null && !members.contains(member)) members.add(member);
        }
        if (!members.contains(sprocket)) members.add(0, sprocket);
        // 保证主动轮在首位
        members.remove(sprocket);
        members.add(0, sprocket);
        link.members = List.copyOf(members);
        if (link.members.size() < MIN_MEMBERS || !validateThickness(link, link.members)) {
            // 存档时的有效成员不足或厚度不成形，直接视为解散；不在加载阶段留下半条履带
            return null;
        }
        for (SubPart member : link.members) member.trackLink = link;
        link.rebuildTensionPairs();
        link.refreshEnvelope(data.linkCount());
        if (data.durability() == TrackLinkData.FULL_DURABILITY) {
            link.durability = link.maxDurability;
        } else {
            link.durability = Math.min(link.durability, link.maxDurability);
        }
        return link;
    }

    /**
     * 用一次铺设的结果构造履带，但**不注册到载具、不建约束、不同步**。
     *
     * <p>调用方先 {@link VehicleCore#registerTrackLink(TrackLink)}，再
     * {@link #onAssembled()}，这样首帧同步出去的就是一条已闭合的完整履带。
     * 履带没有名字，注册进载具列表后才获得身份。</p>
     *
     * @param wheels 按铺设顺序排列的下行程轮与上行程轮（含被顶起的负重轮），不含主动轮
     * @return 成员不足 {@link #MIN_MEMBERS} 或厚度不小于任一成员轮半径时返回 {@code null}
     */
    @Nullable
    public static TrackLink assemble(@NotNull VehicleCore vehicle,
                                     @NotNull SubPart sprocket, @NotNull List<SubPart> wheels,
                                     @NotNull TrackType type) {
        List<SubPart> members = new ArrayList<>(wheels.size() + 1);
        members.add(sprocket);
        for (SubPart wheel : wheels) {
            if (wheel == null || wheel == sprocket || members.contains(wheel)) continue;
            // 和 fromData 一样按当前载具拓扑过滤失效成员；蓝图 / 命令调用也要经过这里
            if (wheel.isDestroyed() || wheel.part.assembly != vehicle) continue;
            // 已经在别的履带里的轮子不能被静默抢走，调用方应先移出旧履带
            if (wheel.trackLink != null) continue;
            members.add(wheel);
        }
        if (members.size() < MIN_MEMBERS) return null;
        ResourceLocation typeId = type.getRegistryKey();
        TrackLink link = new TrackLink(vehicle, type, typeId, type.attr().durabilityPerLink(), false,
                TrackEnvelope2D.layingClockwise(members.stream()
                        .map(member -> member.getWorldPositionMatrix(1f)).toList()));
        link.sprocket = sprocket;
        if (!validateThickness(link, members)) return null;
        link.members = List.copyOf(members);
        for (SubPart member : link.members) member.trackLink = link;
        link.rebuildTensionPairs();
        link.refreshEnvelope();
        link.durability = link.maxDurability;
        return link;
    }

    /** 注册之后调用：建立 GearJoint 并把完整状态同步给客户端。 */
    public void onAssembled() {
        if (disposed) return;
        refreshEnvelope();
        rebuildJoints();
        sync();
    }

    /**
     * 查对应侧注册表里的履带型号。
     *
     * @return 型号不存在或不是履带类型时返回 {@code null}
     */
    @Nullable
    private static TrackType resolveTrackType(Level level, ResourceLocation id) {
        FittingType type = MMDynamicRes.getFittingType(level, id);
        return type instanceof TrackType trackType ? trackType : null;
    }

    /**
     * 校验厚度小于每个成员轮的有效半径。
     *
     * <p>厚度来自型号的 {@code thickness_mm}，包络求解按「轮心距减去厚度的一半」扣半径；
     * 厚度不小于轮半径时包络退化成自交折线，因此在装配与载入阶段直接拒绝。</p>
     */
    private static boolean validateThickness(TrackLink link, List<SubPart> members) {
        float thickness = link.attr().thicknessMeters();
        for (SubPart member : members) {
            float radius = member.getMaxWheelRadius();
            if (radius > 0f && thickness >= radius) {
                MachineMax.LOGGER.warn("履带厚度 {} m 不小于成员 {} 的轮半径 {} m，拒绝闭合",
                        thickness, member.name, radius);
                return false;
            }
        }
        return true;
    }

    @Nullable
    private static SubPart resolve(VehicleCore vehicle, TrackMemberRef ref) {
        var part = vehicle.partMap.get(ref.partUuid());
        if (part == null) return null;
        SubPart subPart = part.subParts.get(ref.subPartName());
        if (subPart == null || subPart.isDestroyed()) return null;
        return subPart;
    }

    // ==================== 成员 ====================

    public boolean addMember(@NotNull SubPart subPart) {
        if (disposed) return false;
        if (subPart.isDestroyed() || subPart.part.assembly != vehicle) return false;
        // 履带成员必须是带轮面的 SubPart；没有轮半径的零件无法建立 GearJoint
        if (subPart.getMaxWheelRadius() <= 0f) return false;
        if (members.contains(subPart)) return false;
        // 一个 SubPart 只属于一条履带：先把它从旧履带摘掉
        TrackLink old = subPart.trackLink;
        if (old != null && old != this) old.removeMember(subPart);

        List<SubPart> next = new ArrayList<>(members);
        next.add(subPart);
        members = List.copyOf(next);
        subPart.trackLink = this;
        rebuildTensionPairs();
        refreshEnvelope();
        rebuildJoints();
        sync();
        return true;
    }

    public boolean removeMember(@NotNull SubPart subPart) {
        if (disposed || !members.contains(subPart)) return false;
        if (subPart == sprocket) {
            // 主动轮失效 / 被拆：整条履带解散（设计文档 §7.1）
            dissolve();
            return true;
        }
        List<SubPart> next = new ArrayList<>(members);
        next.remove(subPart);
        members = List.copyOf(next);
        if (subPart.trackLink == this) subPart.trackLink = null;
        if (members.size() < MIN_MEMBERS) {
            dissolve();
            return true;
        }
        rebuildTensionPairs();
        refreshEnvelope();
        rebuildJoints();
        sync();
        return true;
    }

    /**
     * 成员归属变化（销毁 / 拆卸 / 分裂）后的统一刷新入口。
     *
     * <p>离开本装配体的成员视为失效；主动轮失效或有效成员不足 {@link #MIN_MEMBERS}
     * 时整条履带解散。</p>
     */
    public void refreshMembers() {
        if (disposed) return;
        List<SubPart> valid = new ArrayList<>(members.size());
        for (SubPart member : members) {
            if (member == null || member.isDestroyed() || member.part.assembly != vehicle) {
                if (member != null && member.trackLink == this) member.trackLink = null;
                continue;
            }
            valid.add(member);
        }
        SubPart sp = sprocket;
        if (sp == null || sp.isDestroyed() || sp.part.assembly != vehicle
                || valid.size() < MIN_MEMBERS) {
            dissolve();
            return;
        }
        // 保持主动轮在首位
        valid.remove(sp);
        valid.add(0, sp);
        members = List.copyOf(valid);
        for (SubPart member : members) member.trackLink = this;
        rebuildTensionPairs();
        refreshEnvelope();
        rebuildJoints();
        sync();
    }

    /**
     * 成员被销毁时的回调，由 {@link SubPart#destroy()} 在刚体移出世界之前调用。
     */
    public void onMemberDestroyed(@NotNull SubPart subPart) {
        if (subPart.trackLink == this) subPart.trackLink = null;
        if (disposed) return;
        if (vehicle.level.isClientSide()) {
            List<SubPart> next = new ArrayList<>(members);
            next.remove(subPart);
            members = List.copyOf(next);
            if (subPart == sprocket || members.size() < MIN_MEMBERS) {
                disposeClient();
            }
            return;
        }
        refreshMembers();
    }

    // ==================== 约束 ====================

    private void rebuildJoints() {
        if (vehicle.level.isClientSide() || disposed) return;
        queueRebuildJoints();
    }

    private void queueRebuildJoints() {
        if (vehicle.level.isClientSide() || disposed || rebuildQueued) return;
        SubPart sp = sprocket;
        if (sp == null) return;
        rebuildQueued = true;
        SparkLevel.getPhysicsLevel(vehicle.level).submitImmediateTask(PPhase.PRE, () -> {
            rebuildQueued = false;
            rebuildJointsNow();
            return null;
        });
    }

    /**
     * 物理线程：按当前成员重建星形 GearJoint。
     *
     * <p>先摘掉旧约束再建新约束；两端刚体必须已经在碰撞世界里（与
     * {@code AbstractConnector#addToLevel} 的顺序纪律一致）。</p>
     */
    private void rebuildJointsNow() {
        removeJointsNow();
        if (disposed || broken) return;
        SubPart sp = sprocket;
        if (sp == null || sp.isDestroyed() || sp.part.assembly != vehicle) return;
        float sprocketRadius = sp.getMaxWheelRadius();
        if (sprocketRadius <= 0f) {
            MachineMax.LOGGER.warn("主动轮 {} 没有轮半径，无法建立 GearJoint", sp.name);
            return;
        }
        for (SubPart wheel : members) {
            if (wheel == sp || wheel.isDestroyed() || wheel.part.assembly != vehicle) continue;
            float wheelRadius = wheel.getMaxWheelRadius();
            if (wheelRadius <= 0f) {
                MachineMax.LOGGER.warn("成员 {} 没有轮半径，跳过 GearJoint", wheel.name);
                continue;
            }
            if (!sp.body.isInWorld() || !wheel.body.isInWorld()) {
                MachineMax.LOGGER.warn("成员 {} 或主动轮尚未进入物理世界，跳过 GearJoint", wheel.name);
                continue;
            }
            float ratio = -(wheelRadius / sprocketRadius);
            GearJoint joint = new GearJoint(sp.body, wheel.body,
                    new Vector3f(1f, 0f, 0f), new Vector3f(1f, 0f, 0f), ratio);
            joints.add(joint);
            SparkLevel.getPhysicsLevel(vehicle.level).getWorld().addJoint(joint);
        }
    }

    private void removeJointsNow() {
        for (GearJoint joint : joints) {
            try {
                if (joint.getPhysicsSpace() != null) {
                    SparkLevel.getPhysicsLevel(vehicle.level).getWorld().removeJoint(joint);
                }
            } catch (Exception e) {
                MachineMax.LOGGER.warn("移除履带的 GearJoint 失败（主动轮 {}）", label(), e);
            }
        }
        joints.clear();
    }

    private void setJointsEnabledNow(boolean enabled) {
        for (GearJoint joint : joints) {
            try {
                joint.setEnabled(enabled);
            } catch (Exception e) {
                MachineMax.LOGGER.warn("设置履带的 GearJoint 启停失败（主动轮 {}）", label(), e);
            }
        }
    }

    // ==================== 张力 ====================

    private void rebuildTensionPairs() {
        List<TensionPair> pairs = new ArrayList<>(Math.max(0, members.size() - 1));
        for (int i = 0; i + 1 < members.size(); i++) {
            SubPart a = members.get(i);
            SubPart b = members.get(i + 1);
            if (a == null || b == null) continue;
            float rest = a.getPosition().distance(b.getPosition());
            if (rest <= 1e-4f) continue;
            pairs.add(new TensionPair(a, b, rest));
        }
        tensionPairs = List.copyOf(pairs);
    }

    /**
     * 物理线程：每个 substep 对相邻成员施加单边弹簧-阻尼力。
     *
     * <p>只拉不推，作用点在轮心，不产生扭矩；断带 / 解散 / 任一成员失效时跳过。</p>
     */
    public void applyTension() {
        TrackAttr.Tension tension = attr().tension();
        if (!tension.enabled() || broken || disposed || vehicle.level.isClientSide()) return;
        List<TensionPair> pairs = tensionPairs;
        if (pairs.isEmpty()) return;
        SubPart sp = sprocket;
        if (sp == null || sp.isDestroyed()) return;
        float maxForce = tension.maxForce();
        for (TensionPair pair : pairs) {
            SubPart a = pair.a();
            SubPart b = pair.b();
            if (a.isDestroyed() || b.isDestroyed()) continue;
            if (!a.body.isInWorld() || !b.body.isInWorld()) continue;
            Vector3f pa = a.body.getPhysicsLocation(null);
            Vector3f pb = b.body.getPhysicsLocation(null);
            Vector3f delta = pb.subtract(pa);
            float distance = delta.length();
            if (distance <= 1e-4f) continue;
            Vector3f normal = delta.mult(1f / distance);
            float rest = pair.rest() - tension.preload() + tension.slack();
            float stretch = distance - rest;
            if (stretch <= 0f) continue;
            Vector3f va = a.body.getLinearVelocity(null);
            Vector3f vb = b.body.getLinearVelocity(null);
            float separationVelocity = vb.subtract(va).dot(normal);
            float dampingForce = tension.damping() * Math.max(separationVelocity, 0f);
            float force = Math.clamp(tension.stiffness() * stretch + dampingForce, 0f, maxForce);
            if (force <= 0f) continue;
            Vector3f impulse = normal.mult(force);
            a.body.applyCentralForce(impulse);
            b.body.applyCentralForce(impulse.negate());
            vehicle.accumulateTrackTension(force);
        }
    }

    // ==================== 耐久 / 状态 ====================

    /**
     * 主线程：由成员当前位姿重建包络，推导节数 N 与整条带的最大耐久。
     *
     * <p>只在创建 / 载入 / 成员变化时调用。运行中的悬挂行程会改变包络长度，
     * 但 N 必须固定，否则节分布会周期性重排；长度变化由节间距吸收。</p>
     *
     * @param persistedCount 持久化节数（{@link TrackLinkData#linkCount()}）。大于 0 时直接采用：
     *                       按本端位姿重算会在载具刚放出 / 刚载入、轮子位姿尚未就绪时，
     *                       把 N 锁在退化包络（同心圆 → 单轮周长的凸包）上。
     *                       {@link TrackLinkData#UNKNOWN_LINK_COUNT} 表示旧数据，按当前包络推导一次。
     */
    private void refreshEnvelope(int persistedCount) {
        int period = type().linkPeriod();
        if (members.isEmpty()) {
            linkCount = 1;
            maxDurability = attr().maxDurability(1);
            durability = Math.min(durability, maxDurability);
            return;
        }
        if (persistedCount > 0) {
            linkCount = persistedCount;
            warnLinkCountPeriod(persistedCount, period);
        } else {
            TrackEnvelope2D.Result result = envelope(1f);
            int derived = result != null
                    ? result.path().linkCount(attr().lengthPerLink(), period)
                    // 成员缺轮半径 / 位姿尚未就绪：按成员数估一个保守节数，保证耐久上限可用
                    : alignToPeriod(Math.max(1, members.size() * 8), period);
            linkCount = Math.min(TrackLinkData.MAX_LINK_COUNT, derived);
        }
        maxDurability = attr().maxDurability(linkCount);
        durability = Math.min(durability, maxDurability);
    }

    /** 把估算节数对齐到周期 K 的整数倍（闭环要求 N % K == 0，估算也不能例外）。 */
    private static int alignToPeriod(int estimate, int period) {
        int k = Math.max(1, period);
        return Math.max(1, Math.round((float) estimate / k)) * k;
    }

    /**
     * 持久化节数不是周期 K 的整数倍时告警一次，不改写存档、蓝图与模板。
     *
     * <p>玩家可能装着一条按旧型号规则生成的带，或内容包后改了 {@code link_bones}。
     * 改值会让存档里的耐久上限与外形一起变，因此只提示：闭环在第 N − 1 节与第 0 节接缝处
     * 相邻两节同槽。告警按型号去重，避免每台车、每次载入各记一条。</p>
     */
    private void warnLinkCountPeriod(int count, int period) {
        if (period <= 1 || count % period == 0) return;
        if (!WARNED_LINK_COUNT_PERIOD.add(typeId())) return;
        MachineMax.LOGGER.warn("履带型号 {} 的节数 {} 不是周期 {} 的整数倍，保留原值（闭环接缝处会有两节同槽）",
                typeId(), count, period);
    }

    /** 按当前几何重新推导节数：成员变化后带路长度变了，不能沿用旧值。 */
    private void refreshEnvelope() {
        refreshEnvelope(TrackLinkData.UNKNOWN_LINK_COUNT);
    }

    public void damage(float amount) {
        if (amount <= 0f || broken || disposed) return;
        durability = Math.max(0f, durability - amount);
        if (durability <= 0f) {
            broken = true;
            stopJoints();
            // 普通伤害只改服务端数值；只有断带这一刻需要全量同步
            sync();
        }
    }

    public void repair(float amount) {
        if (amount <= 0f || disposed) return;
        durability = Math.min(maxDurability, durability + amount);
        if (broken && durability > 0f) {
            broken = false;
            rebuildJoints();
        }
        sync();
    }

    private void stopJoints() {
        if (vehicle.level.isClientSide()) return;
        SparkLevel.getPhysicsLevel(vehicle.level).submitImmediateTask(PPhase.PRE, () -> {
            setJointsEnabledNow(false);
            removeJointsNow();
            return null;
        });
    }

    // ==================== 生命周期 ====================

    /** 载具入世 / 载入存档后调用；刚体已由 RegularPart 的 addToLevel 入队。 */
    public void onAddToLevel() {
        if (disposed) return;
        if (vehicle.level.isClientSide()) return;
        rebuildJoints();
    }

    /**
     * 主线程每 tick：刚体入世是异步的，若首轮建约束时刚体还没进世界，
     * 这里以 20 tick 的间隔重试，避免日志刷屏。
     */
    public void serverTick() {
        if (disposed || broken || vehicle.level.isClientSide()) return;
        if (retryCooldown > 0) {
            retryCooldown--;
            return;
        }
        int expected = 0;
        for (SubPart member : members) {
            if (member != sprocket && !member.isDestroyed()
                    && member.part.assembly == vehicle && member.getMaxWheelRadius() > 0f) {
                expected++;
            }
        }
        if (joints.size() < expected) {
            rebuildJoints();
            retryCooldown = 20;
        }
    }

    /** 载具销毁 / 卸载前调用：必须在成员刚体被移出世界之前摘掉约束。 */
    public void dispose() {
        if (disposed) return;
        disposed = true;
        stopAudio();
        if (!vehicle.level.isClientSide()) {
            SparkLevel.getPhysicsLevel(vehicle.level).submitImmediateTask(PPhase.PRE, () -> {
                removeJointsNow();
                return null;
            });
        }
        for (SubPart member : members) {
            if (member.trackLink == this) member.trackLink = null;
        }
        members = List.of();
        sprocket = null;
        tensionPairs = List.of();
    }

    private void disposeClient() {
        disposed = true;
        stopAudio();
        for (SubPart member : members) {
            if (member.trackLink == this) member.trackLink = null;
        }
        members = List.of();
        sprocket = null;
        tensionPairs = List.of();
        vehicle.removeTrackLink(this);
    }

    /**
     * 解散：摘约束、清反向引用、从载具移除。测试物品阶段不产生掉落物，
     * 内容包 / 物品回收在后续阶段接入。
     */
    public void dissolve() {
        if (disposed) return;
        if (vehicle.level.isClientSide()) {
            disposeClient();
            return;
        }
        dispose();
        vehicle.removeTrackLink(this);
        vehicle.syncTrackLinks();
    }

    // ==================== 同步 ====================

    private void sync() {
        if (!vehicle.level.isClientSide()) vehicle.syncTrackLinks();
    }

    @Nullable
    public TrackLinkData toData() {
        if (members.isEmpty()) return null;
        SubPart sp = sprocket;
        List<TrackMemberRef> refs = new ArrayList<>(members.size());
        for (SubPart member : members) refs.add(TrackMemberRef.of(member));
        return new TrackLinkData(sp == null ? refs.getFirst() : TrackMemberRef.of(sp),
                refs, durability, broken, clockwise, typeId, linkCount);
    }

    /** 主线程：按创建时确定的铺设绕向求当前包络。 */
    @Nullable
    public TrackEnvelope2D.Result envelope(float partialTick) {
        return TrackEnvelope2D.build(members, sprocket, partialTick, attr().thicknessMeters(), clockwise);
    }

    /** 日志用标签：优先主动轮名；主动轮尚未解析或已清空时退回占位符。 */
    private String label() {
        SubPart sp = sprocket;
        return sp == null ? "?" : sp.name;
    }

    // ==================== 轮面音效 ====================

    /**
     * 主线程 / 客户端：一个接地成员把自己上报给履带。
     *
     * <p>轮面音由履带合成，成员只上报接触快照。同一 tick 内的多条上报合并成
     * 「最大带速偏差 + 接触点形心」，整条履带的判定在 {@link #clientTick()} 做。</p>
     *
     * <p>成员是在主线程 tick 的收尾阶段收到接触快照的，比 {@link VehicleCore#preTick()}
     * 晚一个 tick，所以这里只打时间戳，判定留给 {@link #CONTACT_GRACE_TICKS} 的容差。</p>
     *
     * @param memberSpeed       该成员相对地面的水平速度（m/s）
     * @param worldContactPoint 世界坐标接触点
     */
    public void reportWheelContact(float memberSpeed, @NotNull Vector3f worldContactPoint) {
        long now = vehicle.level.getGameTime();
        float slip = Math.abs(beltSpeed() - memberSpeed);
        if (now != contactTick) {
            contactTick = now;
            contactSlip = slip;
            contactCentroidSum.set(worldContactPoint);
            contactCount = 1;
        } else {
            if (slip > contactSlip) contactSlip = slip;
            contactCentroidSum.addLocal(worldContactPoint);
            contactCount++;
        }
    }

    /**
     * 主线程：主动轮相对车体的局部 X 角速度（rad/s）。
     *
     * <p>与车体做差去掉俯仰 / 侧倾角速度，再转到主动轮自身坐标系取 X 轴分量；
     * 取不到车体一端时退回主动轮自身的局部角速度。音效带速使用此量，
     * 渲染相位用实际轮位姿，并用此量预测角度展开的跨圈方向。</p>
     */
    public float sprocketOmegaX() {
        SubPart sp = sprocket;
        if (sp == null) return 0f;
        Vector3f relative = sp.getAngularVelocity();
        SubPart chassis = findChassis(sp);
        if (chassis != null) {
            relative.subtractLocal(chassis.getAngularVelocity());
        }
        Vector3f local = new Vector3f();
        MyQuaternion.rotateInverse(sp.getRotation(), relative, local);
        return local.x;
    }

    /** 主线程 / 渲染线程：轮位姿的安装参考系；无安装对象时使用世界系。 */
    public org.joml.Matrix4f sprocketReferencePose(float partialTick) {
        SubPart sp = sprocket;
        SubPart chassis = sp == null ? null : findChassis(sp);
        return chassis == null ? new org.joml.Matrix4f() : chassis.getWorldPositionMatrix(partialTick);
    }

    /** 主线程：履带带速（m/s，取绝对值）。同一 tick 内重复调用只读一次刚体。 */
    public float beltSpeed() {
        long now = vehicle.level.getGameTime();
        if (now != beltTick) {
            beltTick = now;
            beltCache = measureBeltSpeed();
        }
        return beltCache;
    }

    private float measureBeltSpeed() {
        SubPart sp = sprocket;
        if (sp == null) return 0f;
        float radius = sp.getMaxWheelRadius();
        if (!Float.isFinite(radius) || radius <= 0f) return 0f;
        float omega = sprocketOmegaX();
        return Float.isFinite(omega) ? Math.abs(omega) * radius : 0f;
    }

    /**
     * 主线程 / 客户端：每 tick 决策一次本条履带的轮面音。
     *
     * <p>断带、解散或主动轮消失时停止；其余情况把「是否有接地、带速、最大偏差、
     * 接触点形心」交给 {@link #audio}。</p>
     */
    public void clientTick() {
        if (!vehicle.level.isClientSide()) return;
        if (disposed || broken || sprocket == null) {
            stopAudio();
            return;
        }
        Level level = vehicle.level;
        long now = level.getGameTime();
        boolean contact = contactTick >= 0 && now - contactTick <= CONTACT_GRACE_TICKS;
        if (audio == null) audio = new TrackAudio(level);
        float inv = contactCount > 0 ? 1f / contactCount : 0f;
        audio.tick(contact, beltSpeed(), contactSlip,
                contactCentroidSum.x * inv, contactCentroidSum.y * inv, contactCentroidSum.z * inv);
    }

    private void stopAudio() {
        TrackAudio current = audio;
        if (current != null) current.stop();
    }

    /** 主动轮在车体上的对偶：本履带成员之外的第一个连接对象。 */
    @Nullable
    private SubPart findChassis(SubPart sp) {
        for (AbstractConnector connector : sp.connectors.values()) {
            AbstractConnector attached = connector.attachedConnector;
            if (attached == null || attached.subPart == null) continue;
            SubPart other = attached.subPart;
            if (other == sp || members.contains(other)) continue;
            return other;
        }
        return null;
    }

    // ==================== 查询 ====================

    public VehicleCore vehicle() {
        return vehicle;
    }

    /** 履带型号；客户端缺型号时是回退得到的默认型号。 */
    public TrackType type() {
        return type;
    }

    /** 原始型号 id：存档、同步与蓝图写回这个值，不回写回退得到的默认 id。 */
    public ResourceLocation typeId() {
        return typeId;
    }

    public TrackAttr attr() {
        return type.attr();
    }

    @Nullable
    public SubPart sprocket() {
        return sprocket;
    }

    public List<SubPart> members() {
        return members;
    }

    public float durability() {
        return durability;
    }

    public float maxDurability() {
        return maxDurability;
    }

    /** 当前耐久比例，供履带装甲的 RHA / 减伤 / 伤害乘数衰减使用。 */
    public float durabilityRatio() {
        return maxDurability > 0f ? Math.clamp(durability / maxDurability, 0f, 1f) : 1f;
    }

    /**
     * 整条履带的节数 N。
     *
     * <p>由服务端在创建 / 成员变化时按包络周长推导一次，随后作为权威值随
     * {@link TrackLinkData#linkCount()} 持久化与同步；客户端直接采用，不按自身帧的位姿重算。
     * 最大耐久与客户端渲染共用这一个值。存档 / 蓝图 / 模板里带 {@code link_count} 时直接采用该值，
     * 因此同一型号套在不同轮系上可以各自记下自己的节数。</p>
     */
    public int linkCount() {
        return linkCount;
    }

    public boolean isBroken() {
        return broken;
    }

    public boolean isDisposed() {
        return disposed;
    }

    public boolean isSprocket(SubPart subPart) {
        return subPart == sprocket;
    }

    private record TensionPair(SubPart a, SubPart b, float rest) {
    }
}
