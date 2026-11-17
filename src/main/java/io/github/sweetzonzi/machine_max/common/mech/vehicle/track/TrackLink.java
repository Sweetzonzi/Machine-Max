package io.github.sweetzonzi.machine_max.common.mech.vehicle.track;

import cn.solarmoon.spark_core.api.SparkLevel;
import cn.solarmoon.spark_core.util.PPhase;
import com.jme3.bullet.joints.GearJoint;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.item.prop.FittingItem;
import io.github.sweetzonzi.machine_max.common.item.prop.TrackItem;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.event.track.TrackDamageEvent;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.fitting.FittingType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.fitting.TrackType;
import io.github.sweetzonzi.machine_max.common.registry.MMDataComponents;
import io.github.sweetzonzi.machine_max.common.registry.MMItems;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import io.github.sweetzonzi.machine_max.util.geometry.BeltPath2D;
import io.github.sweetzonzi.machine_max.util.geometry.Vec2;
import jme3utilities.math.MyQuaternion;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 履带：VehicleCore 级的关系 + 状态对象，没有独立刚体。
 *
 * <p>成员是载具内既有的 {@link SubPart}（主动轮 + 下行程负重轮 + 上行程诱导轮 / 托带轮），
 * 所有权仍属于各自 RegularPart。成员顺序就是包络的锚定绕行顺序：主动轮在首位，随后是全部
 * 下行程轮（含被顶起的），再是上行程轮。
 * 成员之间用 {@link GearJoint} 做星形角速度耦合；相邻成员之间用单边弹簧-阻尼力近似
 * 张力。</p>
 *
 * <p><b>耐久是沿带面分段的。</b>整条带沿带面分成 {@code S} 个等长材料扇区（{@code S} = 成员数），
 * 每区 {@code N / S} 节材料；扇区绑在带面材料上，服务端维护一个相位 {@code sector_phase}。
 * 命中时先把命中的成员映射到它此刻压在带面上的位置（{@link #sectorOf(SubPart)}），
 * 伤害只落在那个扇区。同一轮在车开动前后落到不同扇区，停住的车才在同一处累积——
 * 这就是「局部断带」与「损坏跟着带面滚」的同一套机制。状态只有
 * {@link TrackSectors}（每扇区剩余比例 + 断口标记），{@code links}、断带、
 * 整条带点数全部现算。</p>
 *
 * <p><b>「履带是否在位」只有一个判据</b>：{@link #isBuilt()}（材料扇区非空）。未建造的带
 * 只有设计、没有材料，必须同时「不吸收伤害」和「不作为机械件存在」：不建 GearJoint、
 * 不拉张力、不提供轮面摩擦、不接管轮面音。</p>
 *
 * <p>型号（{@link TrackType}）只在创建 / 载入时解析一次；履带同时保留原始型号 id
 * {@link #typeId()}，客户端缺型号时回退默认型号的视觉，但存档与同步仍写回原始 id。</p>
 *
 * <p>线程：主线程负责成员表 / 材料扇区 / 相位 / 状态；{@code GearJoint} 的增删与启停、
 * 张力施力都在物理线程执行。命中线程只读 {@link #view} 这个不可变快照把
 * {@code (扇区, 伤害)} 入队，主线程在 {@link #serverTick()} 开头统一结算。</p>
 */
public class TrackLink {
    /**
     * 结构下限：主动轮 + 至少 1 个负重轮。
     *
     * <p>成员低于该值时履带解散；型号不提供可调下限。</p>
     */
    public static final int MIN_MEMBERS = 2;

    /** 服务端 tick 的名义间隔（秒），相位推进的 {@code dt} 缺省值。 */
    private static final float TICK_SECONDS = 1f / 20f;
    /** 单次相位推进的 {@code dt} 上限（秒）：服务器卡顿后不让相位一次跳过大半个带。 */
    private static final float MAX_PHASE_DT = 0.1f;
    /** 包络长度与「节数 × 节距」的最大相对偏差：超出说明这一帧的位姿或几何不是存档里的那个。 */
    private static final float MAX_ENVELOPE_DRIFT = 0.10f;

    /** 已经报过「持久化节数不是周期整数倍」的型号，按型号去重，避免每台车各记一条。 */
    private static final Set<ResourceLocation> WARNED_LINK_COUNT_PERIOD = ConcurrentHashMap.newKeySet();
    /** 已经报过「扇区长度与成员数不符（已重采样）」的型号。 */
    private static final Set<ResourceLocation> WARNED_SECTOR_RESAMPLE = ConcurrentHashMap.newKeySet();
    /** 已经报过「包络长度与节数对不上」的型号与车型组合。 */
    private static final Set<String> WARNED_ENVELOPE_DRIFT = ConcurrentHashMap.newKeySet();

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

    /** 扇区材料 + 相位 + 成员带面位置的不可变快照；命中线程只读这一个引用。 */
    private volatile SectorView view = SectorView.EMPTY;
    /** 命中入队：入队时记下的扇区索引与最终伤害，主线程按该索引扣减、不重算。 */
    private final ConcurrentLinkedQueue<TrackHit> pendingHits = new ConcurrentLinkedQueue<>();
    /** 相位推进用的上一 tick 游戏时间；{@code < 0} 表示尚未采样。 */
    private long lastPhaseTick = -1L;
    /** 相位推进方向：主动轮接触段的绕向，装配 / 载入时由包络重取一次。 */
    private volatile float sectorSign;

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

    private TrackLink(VehicleCore vehicle, TrackType type, ResourceLocation typeId, boolean clockwise) {
        this.vehicle = vehicle;
        this.type = type;
        this.typeId = typeId;
        this.clockwise = clockwise;
        // 包络不可用时的相位方向：与创建时确定的铺设绕向相反。符号反了只会让损坏区朝反方向滚，
        // 断带判定仍然成立，首轮实机仍需标定（设计 §7 #1）
        this.sectorSign = clockwise ? -1f : 1f;
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
        TrackLink link = new TrackLink(vehicle, type, data.trackType(), data.clockwise());
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
        // 先把存档里的扇区与相位装进快照，再让 refreshState 按解析出的成员数收口
        link.view = new SectorView(data.linkCount(), data.sectors(), data.sectorPhase(),
                link.members.toArray(new SubPart[0]), new float[0]);
        link.refreshState(data.linkCount());
        return link;
    }

    /**
     * 用一次铺设的结果构造履带，但**不注册到载具、不建约束、不同步**。
     *
     * <p>调用方按「装配 → 扣料 → 注册 + {@link #onAssembled()}」的顺序使用本方法：
     * 扣料失败时候选从未注册、没有约束、没有同步，只需把成员的反向引用清掉即可丢弃
     * （见 {@link #abandonCandidate()}）。</p>
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
        TrackLink link = new TrackLink(vehicle, type, typeId,
                TrackEnvelope2D.layingClockwise(members.stream()
                        .map(member -> member.getWorldPositionMatrix(1f)).toList()));
        link.sprocket = sprocket;
        if (!validateThickness(link, members)) return null;
        link.members = List.copyOf(members);
        for (SubPart member : link.members) member.trackLink = link;
        link.rebuildTensionPairs();
        // 手动铺设直接建成满带（§5.5）：先摆好满材料扇区，再让 refreshState 推出 N 与映射
        link.view = new SectorView(TrackLinkData.UNKNOWN_LINK_COUNT, TrackSectors.full(members.size()),
                0f, link.members.toArray(new SubPart[0]), new float[0]);
        link.refreshState(TrackLinkData.UNKNOWN_LINK_COUNT);
        return link;
    }

    /**
     * 丢弃一个尚未注册的装配候选：只清成员的反向引用。
     *
     * <p>候选从未注册、没有约束、没有同步，因此不需要 {@link #dispose()}，也没有材料可返还
     * （料还没扣）。不撤销反向引用会留下「幽灵履带」——{@code hasLiveTrack} 判真，
     * 把轮子的命中吸走。</p>
     */
    public void abandonCandidate() {
        for (SubPart member : members) {
            if (member.trackLink == this) member.trackLink = null;
        }
        members = List.of();
        sprocket = null;
        tensionPairs = List.of();
        view = SectorView.EMPTY;
    }

    /** 注册之后调用：建立 GearJoint 并把完整状态同步给客户端。 */
    public void onAssembled() {
        if (disposed) return;
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

    /**
     * 成员归属变化（销毁 / 拆卸 / 分裂）后的统一刷新入口。
     *
     * <p>离开本装配体的成员视为失效；主动轮失效或有效成员不足 {@link #MIN_MEMBERS}
     * 时整条履带解散，并按剩余材料返还。</p>
     */
    public void refreshMembers() {
        refreshMembers(null);
    }

    /**
     * 成员归属变化后的统一刷新入口，附带一次非撬棍解散的掉落位置。
     *
     * @param dropPos 解散时返还材料的落点；{@code null} 表示取载具呈现位置
     */
    public void refreshMembers(@Nullable Vec3 dropPos) {
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
            // 非撬棍解散：落点取最后一个有效成员，取不到时 dissolve 退回载具位置
            dissolve(null, dropPos != null ? dropPos : lastMemberPosition(valid));
            return;
        }
        // 保持主动轮在首位
        valid.remove(sp);
        valid.add(0, sp);
        members = List.copyOf(valid);
        for (SubPart member : members) member.trackLink = this;
        rebuildTensionPairs();
        refreshState(TrackLinkData.UNKNOWN_LINK_COUNT);
        rebuildJoints();
        sync();
    }

    /** 最后一个仍然有效的成员的呈现位置；没有有效成员时返回 {@code null}。 */
    @Nullable
    private static Vec3 lastMemberPosition(List<SubPart> valid) {
        for (int i = valid.size() - 1; i >= 0; i--) {
            SubPart member = valid.get(i);
            if (member != null && !member.isDestroyed()) return member.getPresentationPosition();
        }
        return null;
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
        refreshMembers(subPart.getPresentationPosition());
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
     * {@code AbstractConnector#addToLevel} 的顺序纪律一致）。未建造与断带都不建约束。</p>
     */
    private void rebuildJointsNow() {
        removeJointsNow();
        if (disposed || !isBuilt() || isBroken()) return;
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
     * <p>只拉不推，作用点在轮心，不产生扭矩；未建造 / 断带 / 解散 / 任一成员失效时跳过。</p>
     */
    public void applyTension() {
        TrackAttr.Tension tension = attr().tension();
        if (!tension.enabled() || !isBuilt() || isBroken() || disposed || vehicle.level.isClientSide()) return;
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

    // ==================== 材料扇区 / 相位 ====================

    /**
     * 纯函数：材料坐标 → 扇区。
     *
     * <p>{@code sector = floorMod(floor(origin / linksPerSector − phase), S)}。等长扇区里
     * {@code floor} 取的就是包含该坐标的那一段；映射只依赖材料坐标与相位，不依赖轮序号——
     * 主动轮与诱导轮隔得远、负重轮挨得近的梯形履带同样成立。</p>
     *
     * @param origin     成员的带面位置 {@code s_w}（节）
     * @param phase      相位，单位扇区
     * @param linkCount  节数 N
     * @param sectors    扇区数 S
     * @return {@code [0, S)}；参数不可用时返回 0
     */
    public static int sectorIndex(float origin, float phase, int linkCount, int sectors) {
        if (sectors <= 0 || linkCount <= 0 || !Float.isFinite(origin)) return 0;
        float linksPerSector = linkCount / (float) sectors;
        if (!(linksPerSector > 0f) || !Float.isFinite(phase)) return 0;
        return Math.floorMod((int) Math.floor(origin / linksPerSector - phase), sectors);
    }

    /**
     * 纯函数：节数的下限与周期对齐。
     *
     * <p>{@code N = ceil(max(M, 估计值) / K) × K}，且不小于成员数 {@code M}。
     * {@code N >= M} 是扇区模型成立的前提（{@code linksPerSector >= 1}）；闭环要求
     * {@code N % K == 0}，而 {@code max(M, …)} 会用 {@code M} 破坏这个对齐，所以取大之后
     * 必须再向上对齐一次。</p>
     */
    public static int normalizeLinkCount(int count, int memberCount, int period) {
        int divisor = Math.max(1, period);
        int base = Math.max(count, Math.max(1, memberCount));
        return Math.max(divisor, ((base + divisor - 1) / divisor) * divisor);
    }

    /**
     * 主线程：由成员当前位姿重建包络、推导节数 N，并按当前成员表重采样材料扇区。
     *
     * <p>只在创建 / 载入 / 成员变化时调用。运行中的悬挂行程会改变包络长度，
     * 但 N 必须固定，否则节分布会周期性重排；长度变化由节间距吸收。
     * 节数持久化仍保留，理由不是「载入时位姿未就绪」（{@code restoreState} 已先写入位姿），
     * 而是「悬挂行程不该改变节数」。</p>
     *
     * @param persistedCount 持久化节数（{@link TrackLinkData#linkCount()}）。大于 0 时直接采用；
     *                       {@link TrackLinkData#UNKNOWN_LINK_COUNT} 表示旧数据，按当前包络推导一次。
     */
    private void refreshState(int persistedCount) {
        SectorView old = view;
        int period = type().linkPeriod();
        int memberCount = members.size();
        int count;
        if (memberCount == 0) {
            count = 1;
        } else if (persistedCount > 0) {
            count = persistedCount;
            warnLinkCountPeriod(persistedCount, period);
        } else {
            count = deriveLinkCount(period);
        }
        count = Math.min(TrackLinkData.MAX_LINK_COUNT, alignLinkCount(count, memberCount, period));

        TrackSectors sectors = old.sectors();
        float phase = old.phase();
        if (sectors.isBuilt()) {
            if (sectors.size() != memberCount) {
                // 成员变化：按材料比例重采样，战损不凭空消失、也不凭空修满
                warnSectorResample(memberCount);
                sectors = TrackSectors.resample(sectors, memberCount);
            }
            phase = rephase(old, count, memberCount, phase);
        } else {
            sectors = TrackSectors.UNBUILT;
            phase = 0f;
        }
        float[] origins = computeSectorOrigins(count, memberCount);
        view = new SectorView(count, sectors, TrackLinkData.wrapPhase(phase, memberCount),
                members.toArray(new SubPart[0]), origins);
    }

    /**
     * 成员变化时保留材料坐标：{@code q = phase × linksPerSector} 是相位在带面上的位置（节），
     * 按新的扇区材料换算回扇区。这样改成员不会把损坏区整体挪位。
     */
    private static float rephase(SectorView old, int count, int sectors, float phase) {
        if (sectors <= 0) return 0f;
        if (!old.sectors().isBuilt() || old.sectors().size() <= 0 || old.linkCount() <= 0) return phase;
        float oldLinks = old.linkCount() / (float) old.sectors().size();
        if (!(oldLinks > 0f)) return phase;
        float newLinks = count / (float) sectors;
        if (!(newLinks > 0f)) return 0f;
        return TrackLinkData.wrapPhase(phase * oldLinks / newLinks, sectors);
    }

    /** 按当前几何推导节数；包络不可用时按成员数估一个保守节数。 */
    private int deriveLinkCount(int period) {
        TrackEnvelope2D.Result result = envelope(1f);
        if (result != null) {
            return result.path().linkCount(attr().lengthPerLink(), period);
        }
        return Math.max(1, members.size() * 8);
    }

    /**
     * 节数下限与周期对齐：{@code N = ceil(max(M, 估计值) / K) × K}，且不小于成员数。
     *
     * <p>持久化节数小于成员数时抬到下限并告警；其余情况直接向上对齐到周期。
     * 见 {@link #normalizeLinkCount(int, int, int)}。</p>
     */
    private int alignLinkCount(int count, int memberCount, int period) {
        int floor = Math.max(1, memberCount);
        if (count < floor && WARNED_LINK_COUNT_PERIOD.add(typeId())) {
            MachineMax.LOGGER.warn("履带型号 {} 的节数 {} 少于成员数 {}，已抬到 {}（节数比轮子少的带在几何上不成立）",
                    typeId(), count, floor, normalizeLinkCount(count, memberCount, period));
        }
        return normalizeLinkCount(count, memberCount, period);
    }

    /**
     * 持久化节数不是周期 K 的整数倍时告警一次，不改写存档、蓝图与模板。
     *
     * <p>玩家可能装着一条按旧型号规则生成的带，或内容包后改了 {@code link_bones}。
     * 改值会让存档里的外形与节数一起变，因此只提示：闭环在第 N − 1 节与第 0 节接缝处
     * 相邻两节同槽。告警按型号去重，避免每台车、每次载入各记一条。</p>
     */
    private void warnLinkCountPeriod(int count, int period) {
        if (period <= 1 || count % period == 0) return;
        if (!WARNED_LINK_COUNT_PERIOD.add(typeId())) return;
        MachineMax.LOGGER.warn("履带型号 {} 的节数 {} 不是周期 {} 的整数倍，保留原值（闭环接缝处会有两节同槽）",
                typeId(), count, period);
    }

    private void warnSectorResample(int memberCount) {
        if (!WARNED_SECTOR_RESAMPLE.add(typeId())) return;
        MachineMax.LOGGER.warn("履带型号 {} 的扇区数与成员数 {} 不符，已按材料比例重采样", typeId(), memberCount);
    }

    /**
     * 主线程：按当前包络算出每个成员的带面位置 {@code s_w}（节）。
     *
     * <p>取成员接触弧中点在闭环路径上的弧长，再以主动轮接触弧中点为参考换算成材料坐标。
     * 包络长度与「节数 × 节距」的偏差超过 10% 时不落缓存（该成员按成员序号取扇区），
     * 并在下一个主线程 tick 再校验一次——不按拍数定型，方案 C 本身仍能断带。
     * 凸包回退是当前位姿的真实包络，只是抹掉了抬轮凹口，照常使用。</p>
     *
     * @return 长度等于成员数的 {@code s_w} 数组；取不到接触的成员为 {@link Float#NaN}
     */
    private float[] computeSectorOrigins(int count, int memberCount) {
        float[] origins = new float[memberCount];
        Arrays.fill(origins, Float.NaN);
        if (count <= 0 || memberCount < MIN_MEMBERS) return origins;
        TrackEnvelope2D.Result result = envelope(1f);
        if (result == null) return origins;
        BeltPath2D path = result.path();
        double length = path.length();
        double expected = (double) count * attr().lengthPerLink();
        if (!(length > 0) || !(expected > 0)
                || Math.abs(length - expected) > expected * MAX_ENVELOPE_DRIFT) {
            warnEnvelopeDrift(count, length, expected);
            return origins;
        }
        double reference = contactDistance(path, 0);
        if (Double.isNaN(reference)) return origins;
        for (int i = 0; i < memberCount; i++) {
            double arc = contactDistance(path, i);
            if (Double.isNaN(arc)) continue;
            origins[i] = wrapMaterial((arc - reference) / length * count, count);
        }
        // 相位方向与布局的相位约定一致：取主动轮接触段的绕向；取不到时保留构造时的绕向回退
        BeltPath2D.ArcContact contact = path.wheelContact(0);
        float sign = contact == null ? 0f : (float) Math.signum(contact.sweep());
        if (sign != 0f) sectorSign = sign;
        return origins;
    }

    /**
     * 成员接触弧中点（或点接触）在闭环路径上的弧长。
     *
     * @return 该成员在包络里没有接触时返回 {@link Double#NaN}
     */
    private static double contactDistance(BeltPath2D path, int wheelIndex) {
        BeltPath2D.ArcContact contact = path.wheelContact(wheelIndex);
        if (contact != null && !contact.contactPoints().isEmpty()) {
            // 接触弧的采样点等角度分布，中间那个采样点就是角度中点
            List<Vec2> points = contact.contactPoints();
            return path.distanceAt(points.get(points.size() / 2));
        }
        for (BeltPath2D.PointContact point : path.pointContacts()) {
            if (point.wheelIndex() == wheelIndex) return path.distanceAt(point.point());
        }
        return Double.NaN;
    }

    /** 材料坐标回绕到 {@code [0, count)}。 */
    private static float wrapMaterial(double material, int count) {
        if (count <= 0 || !Double.isFinite(material)) return 0f;
        double wrapped = material % count;
        if (wrapped < 0) wrapped += count;
        return (float) wrapped;
    }

    private void warnEnvelopeDrift(int count, double length, double expected) {
        if (!WARNED_ENVELOPE_DRIFT.add(typeId() + "/" + vehicle.getUuid())) return;
        MachineMax.LOGGER.warn("履带型号 {} 的包络长度 {} m 与节数 {} × 节距 {} m 相差超过 10%，本次按成员序号取扇区",
                typeId(), length, count, expected / count);
    }

    /**
     * 主线程：推进相位。
     *
     * <p>独立于 {@link #serverTick()} 的约束重试判断：建约束失败会置 20 tick 的冷却，
     * 若把推进放在冷却之后，一次失败就会让相位停 20 tick。相位以节为权威单位，
     * 速率只由冻结的节数与型号节距决定，不用实时包络长度做分母。</p>
     */
    private void advancePhase() {
        SectorView snapshot = view;
        TrackSectors sectors = snapshot.sectors();
        if (!sectors.isBuilt()) return;
        SubPart sp = sprocket;
        if (sp == null) return;
        float radius = sp.getMaxWheelRadius();
        float pitch = attr().lengthPerLink();
        if (!(radius > 0f) || !(pitch > 0f) || snapshot.linkCount() <= 0) return;
        long now = vehicle.level.getGameTime();
        float dt = lastPhaseTick < 0 ? TICK_SECONDS
                : Math.clamp((now - lastPhaseTick) * TICK_SECONDS, 0f, MAX_PHASE_DT);
        lastPhaseTick = now;
        float omega = sprocketOmegaX();
        if (!Float.isFinite(omega)) return;
        float delta = sectorSign * omega * radius * dt * sectors.size()
                / (snapshot.linkCount() * pitch);
        if (delta == 0f) return;
        view = snapshot.withPhase(TrackLinkData.wrapPhase(snapshot.phase() + delta, sectors.size()));
    }

    // ==================== 伤害结算 ====================

    /**
     * 命中线程：把一笔伤害记到指定扇区。
     *
     * <p>只入队，不改状态。扇区索引由调用方在命中当时用 {@link #sectorOf(SubPart)} 算出：
     * 结算与入队之间若发布了新的 {@code s_w} 或推进了相位，重算会把同一笔伤害算到另一个扇区。</p>
     *
     * <p>断带、未建造与已释放的带不再入队：断带后新来的命中改由成员自身耐久承接。</p>
     *
     * @param sector 入队时命中的扇区索引
     * @param amount 现有管线算出的最终伤害（装甲修正之后）
     */
    public void damage(int sector, float amount) {
        if (disposed || !(amount > 0f)) return;
        SectorView snapshot = view;
        TrackSectors sectors = snapshot.sectors();
        if (!sectors.isBuilt() || sectors.isBroken()) return;
        int index = Math.clamp(sector, 0, sectors.size() - 1);
        if (NeoForge.EVENT_BUS.post(new TrackDamageEvent.Pre(this, index, amount)).isCanceled()) return;
        pendingHits.add(new TrackHit(index, amount));
    }

    /**
     * 主线程：结算上一 tick 入队的命中。
     *
     * <p>逐条按**入队时记下的扇区索引**扣减，不重算；材料比例夹到 {@code [0, 1]}`（与修复侧
     * 共用 {@link TrackSectors#clampRatio(float)}），降到 0 时给该扇区置断口标记。
     * 同一批里可能有多个扇区归零，每个各记一个断口；已经是断口的扇区不重复计。
     * 同一批里剩余的命中仍按各自入队的扇区结算，因为它们在命中时履带还活着。</p>
     */
    private void settleDamage() {
        if (pendingHits.isEmpty()) return;
        boolean opened = false;
        while (true) {
            TrackHit hit = pendingHits.poll();
            if (hit == null) break;
            SectorView snapshot = view;
            TrackSectors sectors = snapshot.sectors();
            if (!sectors.isBuilt() || hit.sector() >= sectors.size()) continue;
            if (sectors.isTorn(hit.sector())) continue;
            float sectorMax = sectorMax(snapshot);
            if (!(sectorMax > 0f)) continue;
            float remaining = TrackSectors.clampRatio(sectors.fill(hit.sector()) - hit.amount() / sectorMax);
            TrackSectors updated = sectors.withFill(hit.sector(), remaining);
            boolean torn = remaining <= 0f;
            if (torn) updated = updated.withTorn(hit.sector(), true);
            view = snapshot.withSectors(updated);
            NeoForge.EVENT_BUS.post(new TrackDamageEvent.Post(this, hit.sector(), hit.amount()));
            opened |= torn;
        }
        if (opened && isBroken()) openBreach();
    }

    /** 断带边沿：停约束、冻结相位、同步一次。 */
    private void openBreach() {
        stopJoints();
        sync();
    }

    /** 单个扇区的材料上限（点）：{@code durability_per_link × N / S}。 */
    private float sectorMax(SectorView snapshot) {
        int sectors = snapshot.sectors().size();
        if (sectors <= 0) return 0f;
        return attr().durabilityPerLink() * snapshot.linkCount() / sectors;
    }

    private void stopJoints() {
        if (vehicle.level.isClientSide()) return;
        SparkLevel.getPhysicsLevel(vehicle.level).submitImmediateTask(PPhase.PRE, () -> {
            setJointsEnabledNow(false);
            removeJointsNow();
            return null;
        });
    }

    // ==================== 建造与修复 ====================

    /** 未建造：只有设计、没有材料。蓝图放置出来就是这一态。 */
    public boolean needsBuild() {
        return !isBuilt();
    }

    /** 需要焊：未建造、还有没焊满的断口，或任一扇区材料没到满。 */
    public boolean needsWelding() {
        TrackSectors sectors = view.sectors();
        if (!sectors.isBuilt()) return true;
        if (sectors.isBroken()) return true;
        for (Float ratio : sectors.fill()) {
            if (ratio < 1f) return true;
        }
        return false;
    }

    /**
     * 焊枪能否在这条带上动手：要焊，且所有成员所属部件都已达到工作阈值。
     *
     * <p>成员条件是为了「先组轮」：只对准一个轮子建带，会把仍在骨架形态的其他轮子一起
     * 耦合进去。阈值判据用 {@code permitsSubsystemWork()}（进度，不是线框表现标志）；
     * 手动铺设与已组装完的整车全体成员都过阈值，蓝图车全体停在阈值以下。</p>
     */
    public boolean canWeld() {
        if (!needsWelding()) return false;
        for (SubPart member : members) {
            if (!member.part.permitsSubsystemWork()) return false;
        }
        return true;
    }

    /**
     * 未建造 → 建造：一次扣 N 节同型号履带节，扣成功才把材料写满并建约束。
     *
     * <p>0/N 的带没有既有材料要焊，因此不走扇区逐点恢复，整圈骨架在同一次焊接里变成实体。</p>
     *
     * @param container 扣料的容器；创造模式与客户端不检查、不扣除
     * @return 是否建成；缺料时保持 0/N 并返回 {@code false}
     */
    public boolean buildTrack(@Nullable Container container) {
        if (disposed || !needsBuild()) return false;
        SectorView snapshot = view;
        int count = snapshot.linkCount();
        if (count <= 0 || members.size() < MIN_MEMBERS) return false;
        if (!takeLinks(container, count)) return false;
        view = new SectorView(count, TrackSectors.full(members.size()), 0f,
                snapshot.memberOrder(), snapshot.origins());
        rebuildJoints();
        sync();
        return true;
    }

    /**
     * 维修整条带：断口补 {@code R} 节、非断口战损只花时间。
     *
     * <p>入口在履带自己身上，不经过 {@code SubPart.repair}：任何成员都能修任何扇区，
     * 断口所在材料段没有成员 {@code s_w} 也能修。顺序是先断口后战损——断口按扇区序
     * 一次焊一个，一个清位后下一个才允许开焊；断口清完再按耐久从低到高补非断口受损扇区。
     * 扣料与推进的次序照 {@code RegularPart.assemble}：断口第一次被推进到时先扣 {@code R} 节，
     * 扣成功才把本拍维修量灌进该扇区；扣过的断口由「材料大于 0」记住，不会重复收费。</p>
     *
     * @param container 扣料的容器；创造模式与客户端不检查、不扣除
     * @param amount    本拍维修量（点）
     * @return 是否有进展；整条已满与缺料都返回 {@code false}
     */
    public boolean repair(@Nullable Container container, float amount) {
        if (disposed || !(amount > 0f)) return false;
        SectorView snapshot = view;
        TrackSectors sectors = snapshot.sectors();
        if (!sectors.isBuilt()) return false;
        float sectorMax = sectorMax(snapshot);
        if (!(sectorMax > 0f)) return false;
        int torn = firstTorn(sectors);
        if (torn >= 0) {
            if (sectors.fill(torn) <= 0f
                    && !takeLinks(container, TrackSectors.tornLinks(snapshot.linkCount(), sectors.size()))) {
                // 缺料：停在 0，缺口继续隐藏；提示由调用方按当前断口缺额给出
                return false;
            }
            float filled = TrackSectors.clampRatio(sectors.fill(torn) + amount / sectorMax);
            TrackSectors updated = sectors.withFill(torn, filled);
            if (filled >= 1f) updated = updated.withTorn(torn, false);
            view = snapshot.withSectors(updated);
            sync();
            if (!updated.isBroken()) rebuildJoints();
            return true;
        }
        int target = lowestDamaged(sectors);
        if (target < 0) return false;
        float filled = TrackSectors.clampRatio(sectors.fill(target) + amount / sectorMax);
        view = snapshot.withSectors(sectors.withFill(target, filled));
        return true;
    }

    /** 第一个断口扇区的索引；没有断口时返回 -1。 */
    private static int firstTorn(TrackSectors sectors) {
        for (int j = 0; j < sectors.size(); j++) {
            if (sectors.isTorn(j)) return j;
        }
        return -1;
    }

    /** 材料最少的非断口扇区；没有受损扇区时返回 -1。 */
    private static int lowestDamaged(TrackSectors sectors) {
        int best = -1;
        float lowest = 1f;
        for (int j = 0; j < sectors.size(); j++) {
            if (sectors.isTorn(j)) continue;
            float ratio = sectors.fill(j);
            if (ratio >= 1f) continue;
            if (best < 0 || ratio < lowest) {
                best = j;
                lowest = ratio;
            }
        }
        return best;
    }

    /** 当前断口缺的节数（点式提示用）；没有未上料的断口时为 0。 */
    public int missingTornLinks() {
        TrackSectors sectors = view.sectors();
        if (!sectors.isBuilt()) return 0;
        int torn = firstTorn(sectors);
        if (torn < 0 || sectors.fill(torn) > 0f) return 0;
        return TrackSectors.tornLinks(view.linkCount(), sectors.size());
    }

    /** 容器里现存的同型号履带节数。 */
    public int heldLinks(@Nullable Container container) {
        return countLinks(container, typeId);
    }

    /**
     * 从容器扣 {@code need} 节本型号履带节。
     *
     * <p>先数够再扣，缺料时一件不动。创造模式（{@code hasInfiniteMaterials}）不检查、不扣除。</p>
     */
    private boolean takeLinks(@Nullable Container container, int need) {
        return takeLinks(container, typeId, need);
    }

    /** 容器里有多少节指定型号的履带节。 */
    public static int countLinks(@Nullable Container container, ResourceLocation typeId) {
        if (container == null) return 0;
        int held = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (isSameType(stack, typeId)) held += stack.getCount();
        }
        return held;
    }

    /**
     * 从容器扣 {@code need} 节指定型号的履带节，供手动铺设与焊枪共用。
     *
     * <p>先数够再扣，缺料时一件不动。创造模式（{@code hasInfiniteMaterials}）不检查、不扣除。</p>
     *
     * @return 是否扣成功
     */
    public static boolean takeLinks(@Nullable Container container, ResourceLocation typeId, int need) {
        if (need <= 0) return true;
        if (container instanceof Inventory inventory && inventory.player.hasInfiniteMaterials()) return true;
        if (countLinks(container, typeId) < need) return false;
        int remaining = need;
        for (int slot = 0; slot < container.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = container.getItem(slot);
            if (!isSameType(stack, typeId)) continue;
            int taken = Math.min(remaining, stack.getCount());
            stack.shrink(taken);
            if (stack.isEmpty()) container.setItem(slot, ItemStack.EMPTY);
            remaining -= taken;
        }
        return true;
    }

    /** 一件物品是否是指定型号的履带节。 */
    public static boolean isSameType(ItemStack stack, ResourceLocation typeId) {
        return stack.getItem() instanceof TrackItem && FittingItem.idOf(stack).equals(typeId);
    }

    // ==================== 生命周期 ====================

    /** 载具入世 / 载入存档后调用；刚体已由 RegularPart 的 addToLevel 入队。 */
    public void onAddToLevel() {
        if (disposed) return;
        if (vehicle.level.isClientSide()) return;
        rebuildJoints();
    }

    /**
     * 主线程每 tick：先结算上一 tick 入队的命中，再推进相位，最后在需要时重试建约束。
     *
     * <p>结算早于相位推进：这样一次命中的装甲判定与耐久扣减使用同一份扇区状态，
     * 相位最多滞后一个 tick。约束重试以 20 tick 的间隔进行，避免日志刷屏；相位推进不受
     * 冷却影响。</p>
     */
    public void serverTick() {
        if (disposed || vehicle.level.isClientSide()) return;
        settleDamage();
        if (isBroken()) return;
        advancePhase();
        if (!isBuilt()) return;
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

    /** 载具销毁 / 卸载前调用：必须在成员刚体被移出世界之前摘掉约束。不返还材料。 */
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
        pendingHits.clear();
        view = SectorView.EMPTY;
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
        view = SectorView.EMPTY;
        vehicle.removeTrackLink(this);
    }

    /** 解散：摘约束、清反向引用、从载具移除，不返还（内部路径）。 */
    public void dissolve() {
        dissolve(null, null);
    }

    /**
     * 解散并把剩余材料按比例返还。
     *
     * <p>返还是 {@code round(N × Σ fill / S)} 节同型号履带节，断口扇区按 0 计：满带返还 N 节，
     * 受损按剩余点数折算。有 {@code receiver} 时优先塞进背包、溢出落在 {@code pos}；
     * 没有时直接落在 {@code pos}。创造模式撬棍拆带不返还（避免创造栏反复取放刷掉落物），
     * 非撬棍路径没有玩家上下文，照常掉落。</p>
     *
     * @param receiver 触发拆除的玩家；非撬棍路径传 {@code null}
     * @param pos      返还落点；{@code null} 时取载具呈现位置
     */
    public void dissolve(@Nullable Player receiver, @Nullable Vec3 pos) {
        if (disposed) return;
        if (vehicle.level.isClientSide()) {
            disposeClient();
            return;
        }
        int refund = refundLinks();
        Vec3 drop = pos == null ? vehicle.getPosition() : pos;
        boolean payBack = refund > 0 && (receiver == null || !receiver.isCreative());
        dispose();
        vehicle.removeTrackLink(this);
        vehicle.syncTrackLinks();
        if (payBack) refund(receiver, drop, refund);
    }

    /**
     * 拆下零件之前，把这些子零件所属的履带整条结算并按比例返还。
     *
     * <p>零件被玩家拆下时，若零件内含有履带成员（例如车体与轮子同 Part、不同 SubPart），
     * 必须先整条结算并掉落在零件位置——等 {@code SubPart.destroy()} 里的
     * {@code onMemberDestroyed} 再处理，那条带只会静默解散、材料凭空消失。
     * 零件被击毁的路径不走本方法，交给成员级规则。</p>
     *
     * @param subParts 即将随零件一起拆下的子零件
     * @param receiver 触发拆除的玩家；非撬棍路径传 {@code null}
     * @param pos      返还落点；{@code null} 时取该履带最后一个有效成员的位置
     */
    public static void dissolveTracksIn(Iterable<SubPart> subParts, @Nullable Player receiver, @Nullable Vec3 pos) {
        Set<TrackLink> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (SubPart subPart : subParts) {
            TrackLink track = subPart == null ? null : subPart.trackLink;
            if (track == null || track.isDisposed() || !seen.add(track)) continue;
            track.dissolve(receiver, pos == null ? subPart.getPresentationPosition() : pos);
        }
    }

    /** 拆除返还的节数：{@code round(N × Σ fill / S)}，未建造为 0。 */
    public int refundLinks() {
        SectorView snapshot = view;
        return snapshot.sectors().refundLinks(snapshot.linkCount());
    }

    /** 把返还的履带节逐栈塞进背包，放不下的落在 {@code pos}。 */
    private void refund(@Nullable Player receiver, Vec3 pos, int count) {
        ItemStack template = new ItemStack(MMItems.getTRACK().get());
        template.set(MMDataComponents.getFITTING_TYPE(), typeId);
        int remaining = count;
        while (remaining > 0) {
            int size = Math.min(remaining, template.getMaxStackSize());
            remaining -= size;
            ItemStack stack = template.copyWithCount(size);
            if (receiver != null && receiver.getInventory().add(stack)) continue;
            vehicle.level.addFreshEntity(new ItemEntity(vehicle.level, pos.x, pos.y, pos.z, stack));
        }
    }

    // ==================== 同步 ====================

    private void sync() {
        if (!vehicle.level.isClientSide()) vehicle.syncTrackLinks();
    }

    @Nullable
    public TrackLinkData toData() {
        if (members.isEmpty()) return null;
        SectorView snapshot = view;
        SubPart sp = sprocket;
        List<TrackMemberRef> refs = new ArrayList<>(members.size());
        for (SubPart member : members) refs.add(TrackMemberRef.of(member));
        TrackSectors sectors = snapshot.sectors();
        return new TrackLinkData(sp == null ? refs.getFirst() : TrackMemberRef.of(sp),
                refs, sectors.fill(), sectors.torn(), snapshot.phase(), clockwise, typeId,
                snapshot.linkCount());
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
     * 取不到车体一端时退回主动轮自身的局部角速度。音效带速与相位推进都使用此量，
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
     * <p>未建造、断带、解散或主动轮消失时停止；其余情况把「是否有接地、带速、最大偏差、
     * 接触点形心」交给 {@link #audio}。</p>
     */
    public void clientTick() {
        if (!vehicle.level.isClientSide()) return;
        if (disposed || !isBuilt() || isBroken() || sprocket == null) {
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

    // ==================== 扇区查询 ====================

    /**
     * 命中线程 / 主线程：成员轮此刻压在哪个材料扇区上。
     *
     * <p>只读 {@link #view} 这一个不可变快照：{@code s_w} 与相位同版，O(1)、无几何查询、
     * 不需要 BallisticsFramework 上下文。所以 {@code getRHA}、{@code modifyArmorDamage}、
     * {@code hurt} 各调一次同一个函数即可，不必往扩展里塞扇区键。</p>
     *
     * <p>包络不可用（长度校验不过或返回 {@code null}）时按方案 C 降级：取成员序号的固定扇区，
     * 不需要几何、不会返回无效值，伤害仍落在单个扇区、仍能断带，代价是这段时间里损坏
     * 不随带面滚动。</p>
     *
     * @return {@code [0, S)}；未建造时返回 0
     */
    public int sectorOf(@Nullable SubPart member) {
        SectorView snapshot = view;
        int sectors = snapshot.sectors().size();
        if (sectors <= 0) return 0;
        int index = member == null ? -1 : snapshot.indexOf(member);
        float origin = Float.NaN;
        if (index >= 0 && index < snapshot.origins().length) origin = snapshot.origins()[index];
        if (!Float.isFinite(origin)) return Math.floorMod(Math.max(index, 0), sectors);
        return sectorIndex(origin, snapshot.phase(), snapshot.linkCount(), sectors);
    }

    /**
     * 命中该成员时使用的耐久比例：它所在扇区还剩几成材料。
     *
     * @return {@code [0, 1]}；未建造时返回 1（装甲查询另有「是否在位」的门控）
     */
    public float sectorRatio(@Nullable SubPart member) {
        TrackSectors sectors = view.sectors();
        if (!sectors.isBuilt()) return 1f;
        return sectors.fill(sectorOf(member));
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

    /** 材料扇区快照（每扇区剩余比例 + 断口标记）。未建造时两个列表都为空。 */
    public TrackSectors sectors() {
        return view.sectors();
    }

    /** 相位，单位扇区，范围 {@code [0, S)}。 */
    public float sectorPhase() {
        return view.phase();
    }

    /**
     * 整条履带的节数 N。
     *
     * <p>由服务端在创建 / 成员变化时按包络周长推导一次，随后作为权威值随
     * {@link TrackLinkData#linkCount()} 持久化与同步；客户端直接采用，不按自身帧的位姿重算。
     * 客户端渲染共用这一个值：重算会让节图案按 {@code N_client / N_server} 缩放，
     * 而断口节号是按服务端 N 定的，两套节号会对不上。</p>
     */
    public int linkCount() {
        return view.linkCount();
    }

    /** 已上料节数 {@code links}：{@code N − R × 未上料断口数}，未建造为 0。 */
    public int links() {
        return view.sectors().links(view.linkCount());
    }

    /** 材料扇区是否非空。这是「履带是否在位」的唯一判据。 */
    public boolean isBuilt() {
        return view.sectors().isBuilt();
    }

    /** 是否还有没焊满的断口。 */
    public boolean isBroken() {
        return view.sectors().isBroken();
    }

    public boolean isDisposed() {
        return disposed;
    }

    public boolean isSprocket(SubPart subPart) {
        return subPart == sprocket;
    }

    /** 相位推进方向，供实机标定读数。 */
    public float sectorSign() {
        return sectorSign;
    }

    private record TensionPair(SubPart a, SubPart b, float rest) {
    }

    /** 一笔待结算的命中：入队时的扇区索引与最终伤害。 */
    private record TrackHit(int sector, float amount) {
    }

    /**
     * 扇区映射快照：材料状态、相位、成员带面位置一次发布，命中线程只读这一个引用。
     *
     * @param linkCount   节数 N（权威值）
     * @param sectors     材料扇区
     * @param phase       相位，单位扇区，范围 {@code [0, S)}
     * @param memberOrder 成员表，与 {@code origins} 对齐；命中线程靠它定位成员序号
     * @param origins     每个成员的带面位置 {@code s_w}（节）；NaN 表示包络里取不到接触
     */
    private record SectorView(int linkCount, TrackSectors sectors, float phase,
                              SubPart[] memberOrder, float[] origins) {
        static final SectorView EMPTY = new SectorView(1, TrackSectors.UNBUILT, 0f,
                new SubPart[0], new float[0]);

        int indexOf(SubPart member) {
            for (int i = 0; i < memberOrder.length; i++) {
                if (memberOrder[i] == member) return i;
            }
            return -1;
        }

        SectorView withSectors(TrackSectors next) {
            return new SectorView(linkCount, next, phase, memberOrder, origins);
        }

        SectorView withPhase(float next) {
            return new SectorView(linkCount, sectors, next, memberOrder, origins);
        }
    }
}
