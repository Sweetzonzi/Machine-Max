package io.github.sweetzonzi.machine_max.common.mech.vehicle.track;

import cn.solarmoon.spark_core.api.SparkLevel;
import cn.solarmoon.spark_core.util.PPhase;
import com.jme3.bullet.joints.GearJoint;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 履带：VehicleCore 级的关系 + 状态对象，没有独立刚体。
 *
 * <p>成员是载具内既有的 {@link SubPart}（主动轮 + 负重轮），所有权仍属于各自 Part。
 * 成员之间用 {@link GearJoint} 做星形角速度耦合；相邻成员之间用单边弹簧-阻尼力近似
 * 张力。履带耐久是独立于成员轮子耐久的一层。</p>
 *
 * <p>线程：主线程负责成员表 / 耐久 / 状态；{@code GearJoint} 的增删与启停、
 * 张力施力都在物理线程执行。</p>
 */
public class TrackLink {
    private final VehicleCore vehicle;
    private final TrackAttr attr;

    private volatile SubPart sprocket;
    private volatile List<SubPart> members = List.of();
    private volatile List<TensionPair> tensionPairs = List.of();

    private final CopyOnWriteArrayList<GearJoint> joints = new CopyOnWriteArrayList<>();
    private volatile boolean rebuildQueued;
    private volatile boolean disposed;
    private int retryCooldown;

    private volatile float durability;
    private volatile boolean broken;

    public TrackLink(VehicleCore vehicle, TrackAttr attr, float durability, boolean broken) {
        this.vehicle = vehicle;
        this.attr = attr;
        this.durability = Math.max(0f, Math.min(durability, attr.durability()));
        this.broken = broken;
    }

    public TrackLink(VehicleCore vehicle) {
        this(vehicle, TrackAttr.DEFAULT, TrackAttr.DEFAULT.durability(), false);
    }

    @Nullable
    public static TrackLink fromData(@NotNull VehicleCore vehicle, @NotNull TrackLinkData data) {
        SubPart sprocket = resolve(vehicle, data.sprocket());
        if (sprocket == null) {
            MachineMax.LOGGER.warn("履带的主动轮 {} 在载具 {} 中不存在，已跳过", data.sprocket(), vehicle.getUuid());
            return null;
        }
        TrackLink link = new TrackLink(vehicle, TrackAttr.DEFAULT, data.durability(), data.broken());
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
        for (SubPart member : link.members) member.trackLink = link;
        link.rebuildTensionPairs();
        if (link.members.size() < link.attr.minEffectiveWheels()) {
            // 存档时的有效成员不足，直接视为解散；不在加载阶段留下半条履带
            for (SubPart member : link.members) member.trackLink = null;
            return null;
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
     * @param wheels 按铺设顺序排列的负重轮，不含主动轮
     */
    @NotNull
    public static TrackLink assemble(@NotNull VehicleCore vehicle,
                                     @NotNull SubPart sprocket, @NotNull List<SubPart> wheels) {
        TrackLink link = new TrackLink(vehicle);
        link.sprocket = sprocket;
        List<SubPart> members = new ArrayList<>(wheels.size() + 1);
        members.add(sprocket);
        for (SubPart wheel : wheels) {
            if (wheel == null || wheel == sprocket || members.contains(wheel)) continue;
            // 和 fromData 一样按当前载具拓扑过滤失效成员；蓝图 / 命令调用也要经过这里
            if (wheel.isDestroyed() || wheel.part.assembly != vehicle) continue;
            // 已经在别的履带里的轮子不能被静默抢走，调用方应先移出旧履带
            if (wheel.trackLink != null && wheel.trackLink != link) continue;
            members.add(wheel);
        }
        link.members = List.copyOf(members);
        for (SubPart member : link.members) member.trackLink = link;
        link.rebuildTensionPairs();
        return link;
    }

    /** 注册之后调用：建立 GearJoint 并把完整状态同步给客户端。 */
    public void onAssembled() {
        if (disposed) return;
        rebuildJoints();
        sync();
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
        if (next.isEmpty()) {
            sprocket = subPart;
        }
        next.add(subPart);
        members = List.copyOf(next);
        subPart.trackLink = this;
        rebuildTensionPairs();
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
        if (members.size() < attr.minEffectiveWheels()) {
            dissolve();
            return true;
        }
        rebuildTensionPairs();
        rebuildJoints();
        sync();
        return true;
    }

    /**
     * 成员归属变化（销毁 / 拆卸 / 分裂）后的统一刷新入口。
     *
     * <p>离开本装配体的成员视为失效；主动轮失效或有效成员不足
     * {@link TrackAttr#minEffectiveWheels()} 时整条履带解散。</p>
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
                || valid.size() < attr.minEffectiveWheels()) {
            dissolve();
            return;
        }
        // 保持主动轮在首位
        valid.remove(sp);
        valid.add(0, sp);
        members = List.copyOf(valid);
        for (SubPart member : members) member.trackLink = this;
        rebuildTensionPairs();
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
            if (subPart == sprocket || members.size() < attr.minEffectiveWheels()) {
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
        TrackAttr.Tension tension = attr.tension();
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
        }
    }

    // ==================== 耐久 / 状态 ====================

    public void damage(float amount) {
        if (amount <= 0f || broken || disposed) return;
        durability = Math.max(0f, durability - amount);
        if (durability <= 0f) {
            broken = true;
            stopJoints();
        }
        sync();
    }

    public void repair(float amount) {
        if (amount <= 0f || disposed) return;
        durability = Math.min(attr.durability(), durability + amount);
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

    /** 载具入世 / 载入存档后调用；刚体已由 Part 的 addToLevel 入队。 */
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
                refs, durability, broken);
    }

    /** 日志用标签：优先主动轮名；主动轮尚未解析或已清空时退回占位符。 */
    private String label() {
        SubPart sp = sprocket;
        return sp == null ? "?" : sp.name;
    }

    // ==================== 查询 ====================

    public VehicleCore vehicle() {
        return vehicle;
    }

    public TrackAttr attr() {
        return attr;
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
        return attr.durability();
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
