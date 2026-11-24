package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.joints.New6Dof;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.mojang.datafixers.util.Pair;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.CouplerSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.CouplerSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AdvancedConnector;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.ConnectorAlignmentHelper;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.SimpleConnector;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.ConnectionData;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalResult;
import io.github.sweetzonzi.machine_max.network.payload.assembly.ConnectorAttachPayload;
import io.github.sweetzonzi.machine_max.util.MMMath;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * 锁扣：把同一载具内最近的兼容简单连接端扣到本子零件的高级连接点上。
 *
 * <p><b>权威在服务端。</b>候选枚举、生命周期检查、可否决的 Connector 事件与逻辑提交都在服务端
 * 主线程；物理线程只对已冻结的候选施力并在步后采样锁止判据。客户端只读同步来的
 * {@code connected} 与 Connector 包维护的引用。</p>
 *
 * <p><b>{@code connected} 只是逻辑配对的镜像。</b>连接事实由双端 {@code attachedConnector} 互指、
 * 共享关节与 {@code partNet} 承担；标记用于跨端显示、音效与同件存档恢复提示。</p>
 *
 * <p><b>线程：</b>{@link #onTick()} 主线程；{@link #onPrePhysicsTick()} / {@link #onPostPhysicsTick()}
 * 物理线程；{@link #onInteract(LivingEntity)} 主线程（客户端回放不处理）。</p>
 */
public class CouplerSubsystem extends BasicSubsystem {

    /** 跨零件接线到达频道：连接点端口把交互区频道转译成它，锁扣只接受这一条。 */
    public static final String INTERACT_CHANNEL = "interact";

    private static final String TAG_CONNECTED = "connected";
    /** 并列候选的物理容差（米）：两条候选距离差落在这之内视为不可判定，暂停捕获。 */
    private static final float TIE_TOLERANCE = 1e-3f;
    /** 提交失败后的静默期（tick），避免同一样本或同一帧连续触发否决事件。 */
    private static final int RETRY_COOLDOWN_TICKS = 20;
    /** 配对变化后跳过的 tick 数：一个服务端 tick 覆盖一个物理相位，给延迟的关节入世／移除任务留出执行窗口。 */
    private static final int PAIR_CHANGE_GRACE_TICKS = 1;

    private static final EntityDataAccessor<Boolean> DATA_CONNECTED_ID =
            SynchedEntityData.defineId(CouplerSubsystem.class, EntityDataSerializers.BOOLEAN);

    public final CouplerSubsystemAttr attr;
    @Nullable
    public final AdvancedConnector connector;
    private final CouplerSubsystemStaticAttr staticAttr;

    // ===== 服务端运行状态 =====
    private boolean waitingSeparation;
    private boolean restoreAttempted;
    @Nullable
    private SimpleConnector lastCounterpart;
    /** 最近一次真实配对的对侧；用于连接意外消失时仍能测出分离距离。 */
    @Nullable
    private AbstractConnector lastPaired;
    @Nullable
    private volatile FrozenCandidate frozenCandidate;
    @Nullable
    private volatile LockSample lockSample;
    private long nextSequence = 1;
    private long nextSampleId = 1;
    private long consumedSampleId;
    private int retryCooldown;
    /** 最近一次配对状态变化发生在哪个 tick；初值取负一个宽限，让恢复出来的连接从首个 tick 起可巡检。 */
    private int pairChangeTick = -PAIR_CHANGE_GRACE_TICKS;
    private int ambiguousLogTick = -100;

    // ===== 客户端声音基线 =====
    private boolean clientSoundReady;
    private boolean lastClientConnected;
    private long lastClientSoundTick = Long.MIN_VALUE;

    public CouplerSubsystem(ISubsystemHost owner, String name, CouplerSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
        this.staticAttr = attr.getStaticAttribute();
        if (owner.getSubPart() != null
                && owner.getSubPart().connectors.get(attr.getConnector()) instanceof AdvancedConnector advanced) {
            this.connector = advanced;
        } else {
            this.connector = null;
            MachineMax.LOGGER.error("锁扣子系统 {} 找不到高级连接点 {}", name, attr.getConnector());
        }
    }

    // ==================================================================
    // 查询
    // ==================================================================

    public boolean isConnected() {
        return getSynchedData().get(DATA_CONNECTED_ID);
    }

    private void setConnected(boolean value) {
        if (isConnected() == value) return;
        getSynchedData().set(DATA_CONNECTED_ID, value);
        onPairStateChanged();
        if (value) {
            waitingSeparation = false;
            lastCounterpart = null;
        } else {
            waitingSeparation = true;
        }
    }

    private void onPairStateChanged() {
        pairChangeTick = tickCount;
        clearCapture();
    }

    /**
     * 主动端是否由锁扣控制：构造期与装配期的自动配对据此跳过它。
     *
     * @param candidate 待检查的连接点，可为 {@code null}
     * @return 该连接点是某个锁扣的主动端时返回 true
     */
    public static boolean isActiveConnector(@Nullable AbstractConnector candidate) {
        if (candidate == null) return false;
        for (AbstractSubsystem subsystem : candidate.subPart.ownSubsystems()) {
            if (subsystem instanceof CouplerSubsystem coupler
                    && coupler.connector == candidate
                    && coupler.attr.getConnector().equals(candidate.getName())) {
                return true;
            }
        }
        return false;
    }

    /** 跨连接点转发到达的交互频道。 */
    @Override
    public List<String> getAcceptedChannels() {
        return List.of(INTERACT_CHANNEL);
    }

    // ==================================================================
    // 主线程：状态机与逻辑提交
    // ==================================================================

    @Override
    public void onTick() {
        super.onTick();
        if (getLevel().isClientSide()) {
            tickClientSounds();
            return;
        }
        if (connector == null) return;
        if (retryCooldown > 0) retryCooldown--;

        tryRestoreSamePartConnection();
        reconcileConnected();
        if (isConnected()) return;                      // 已扣合：关节承力，无吸力
        if (!isActive()) {                              // 停用／摧毁：保留既有连接，不再捕获
            clearCapture();
            return;
        }
        if (waitingSeparation && updateWaitingSeparation()) return;
        consumeLockSample();
        if (isConnected()) return;
        updateFrozenCandidate();
    }

    /**
     * 同 {@code Part} 的存档恢复：{@code connected} 为真但恢复后没有 Connector 配对时，
     * 只在锁止距离内寻找唯一兼容候选并静默恢复。找不到或有歧义就清除标记。
     */
    private void tryRestoreSamePartConnection() {
        if (restoreAttempted) return;
        restoreAttempted = true;
        if (!isConnected() || connector.hasPart()) return;
        SimpleConnector candidate = findNearestCandidate(staticAttr.getLockDistance(), true);
        if (candidate == null) {
            setConnected(false);
            return;
        }
        if (!attachPair(candidate)) {
            setConnected(false);
        }
    }

    /**
     * 稳定边界对账：{@code connected}、双端引用、共享关节与拓扑互相核对。
     *
     * <p>只核对主动端及其真实对侧，不扫描无关结构。构造／读档后的恢复由
     * {@link #tryRestoreSamePartConnection()} 先跑一步。</p>
     */
    private void reconcileConnected() {
        AbstractConnector other = connector.getAttachedConnector();
        boolean mutual = other != null && other.getAttachedConnector() == connector;
        if (mutual) {
            lastPaired = other;
            if (!isConnected()) setConnected(true);
            repairMissingGraphEdge(other);
            if (jointInvalid(other) && tickCount - pairChangeTick >= PAIR_CHANGE_GRACE_TICKS) {
                // 引用与图都在，但物理约束已失效：走统一断开收尾，不在漂开的当前位置补建关节。
                disconnectUnified();
            }
            return;
        }
        if (other != null) {
            if (tickCount - pairChangeTick >= PAIR_CHANGE_GRACE_TICKS) {
                // 半截引用：让统一断开把两端一起收干净。
                disconnectUnified();
            }
            return;
        }
        // 已经没有配对引用了；若图里还留着主动端的边，说明连接早已失效，删掉陈旧边。
        dropStaleGraphEdge();
        if (isConnected() && restoreAttempted) {
            // 运行时意外断开：留下真实对侧用于测量分离，等门离开重武装距离后才允许再捕获。
            if (lastPaired instanceof SimpleConnector simple) lastCounterpart = simple;
            setConnected(false);
        }
        if (!isConnected() && connector.getJoint() != null
                && tickCount - pairChangeTick >= PAIR_CHANGE_GRACE_TICKS) {
            // 主动端自己创建过的关节在解扣任务执行前仍挂在字段上；没有配对即为孤儿。
            connector.discardOrphanJoint();
        }
    }

    /**
     * 双端引用互指但拓扑里没有对应边时补入，并让客户端走同一条幂等接合路径。
     *
     * <p>跨 {@code Part} 与同 {@code Part} 都写边：同件配对是一条自环边，同样属于拓扑事实。
     * 补边由 {@link VehicleCore#attachConnector} 的幂等分支完成，客户端重放同一个接合包即可收敛。</p>
     */
    private void repairMissingGraphEdge(AbstractConnector other) {
        if (!(other instanceof SimpleConnector simple)) return;
        VehicleCore vehicle = vehicle();
        if (vehicle == null) return;
        RegularPart partA = (RegularPart) connector.getSubPart().part;
        RegularPart partB = (RegularPart) simple.getSubPart().part;
        if (vehicle.getPartNet().edgesConnecting(partA, partB)
                .contains(Pair.of(connector, simple))) {
            return;
        }
        vehicle.attachConnector(connector, simple, null);
        if (!getLevel().isClientSide()) {
            PacketDistributor.sendToPlayersInDimension((ServerLevel) getLevel(), new ConnectorAttachPayload(
                    vehicle.getUuid(),
                    List.of(new ConnectionData(connector, simple)),
                    false,
                    null
            ));
        }
    }

    /**
     * 主动端已经没有配对引用时，删掉图里可能残留的陈旧边。
     *
     * <p>边只可能出自主动端；遍历前取快照，避免删除时修改 Guava 的实时边视图。</p>
     */
    private void dropStaleGraphEdge() {
        VehicleCore vehicle = vehicle();
        if (vehicle == null) return;
        for (Pair<AbstractConnector, SimpleConnector> edge : new java.util.ArrayList<>(vehicle.getPartNet().edges())) {
            if (edge.getFirst() != connector) continue;
            vehicle.dropStaleConnectionEdge(edge.getFirst(), edge.getSecond());
            return;
        }
    }

    /** 双端引用成立时，关节必须存在、由两端共享，且在两端都已入世时属于同一个物理空间。 */
    private boolean jointInvalid(AbstractConnector other) {
        New6Dof joint = connector.getJoint();
        if (joint == null || joint != other.getJoint()) return true;
        SubPart a = connector.getSubPart();
        SubPart b = other.getSubPart();
        if (!a.body.isInWorld() || !b.body.isInWorld()) return false;
        PhysicsSpace space = joint.getPhysicsSpace();
        return space == null || space != a.getPhysicsLevel().getWorld() || space != b.getPhysicsLevel().getWorld();
    }

    /** 通过装配体的统一断开入口收尾，双端引用、关节、图边与同步一起收敛。 */
    private void disconnectUnified() {
        AbstractConnector other = connector.getAttachedConnector();
        if (!(other instanceof SimpleConnector simple)) {
            connector.setAttachedConnector(null);
            setConnected(false);
            return;
        }
        VehicleCore vehicle = vehicle();
        if (vehicle == null) return;
        vehicle.detachConnections(List.of(Pair.of(connector, simple)), java.util.Map.of());
        if (!connector.hasPart()) {
            lastCounterpart = simple;
            setConnected(false);
        }
    }

    /**
     * 等待分离：从上次解扣的对侧端点离开重武装距离后才重新武装。
     *
     * @return 本 tick 是否仍处于等待分离（是则不允许捕获；观察到越界的那一 tick 也不捕获）
     */
    private boolean updateWaitingSeparation() {
        SimpleConnector counterpart = lastCounterpart;
        if (counterpart == null || counterpart.getSubPart().isRemoved() || counterpart.hasPart()) {
            waitingSeparation = false;
            return false;
        }
        Vector3f a = endpoint(connector);
        Vector3f b = endpoint(counterpart);
        float distance = a.distance(b);
        if (!isFinite(distance)) return true;
        if (distance > staticAttr.rearmDistance()) {
            waitingSeparation = false;
            return true; // 当次越界不执行扣合
        }
        return true;
    }

    /** 消费物理线程发布的锁止样本；一份样本只提交一次。 */
    private void consumeLockSample() {
        LockSample sample = lockSample;
        if (sample == null || sample.id() <= consumedSampleId) return;
        consumedSampleId = sample.id();
        FrozenCandidate candidate = frozenCandidate;
        if (candidate == null || sample.sequence() != candidate.sequence()) return;
        if (retryCooldown > 0) return;
        if (!CouplerCriteria.acceptsLock(sample.distance(), sample.angleError(),
                sample.relativeSpeed(), sample.separationSpeed(),
                staticAttr.getLockDistance(), staticAttr.lockAngleToleranceRadians(),
                staticAttr.getMaxLockSpeed())) {
            return;
        }
        if (!attachPair(candidate.passive())) {
            retryCooldown = RETRY_COOLDOWN_TICKS;
        }
    }

    /**
     * 逻辑提交：对齐两端共同的世界参考系后建立真实 Connector 连接。
     *
     * <p>两端都走装配体的连接入口：跨 {@code Part} 写一条普通边，同 {@code Part} 写一条自环边，
     * 结构收尾与接合包因此只有一处。</p>
     */
    private boolean attachPair(SimpleConnector passive) {
        VehicleCore vehicle = vehicle();
        if (vehicle == null || connector.hasPart() || passive.hasPart()) return false;
        if (passive.getSubPart() == connector.getSubPart()) return false;
        if (connector.internal || passive.internal) return false;
        if (passive.getSubPart().part != connector.getSubPart().part
                && passive.getSubPart().part.assembly != vehicle) return false;
        connector.alignActualTransformForJoint(passive);
        boolean attached = vehicle.attachConnector(connector, passive, null);
        if (!attached) {
            // 失败不得残留校正过的安装姿态。
            connector.setActualTransform(connector.offsetFromMassCenter.clone());
            passive.setActualTransform(passive.offsetFromMassCenter.clone());
            return false;
        }
        setConnected(true);
        lastPaired = passive;
        return true;
    }

    /** 冻结本轮的最近唯一候选；候选失效或离开扩展范围才重新选取。 */
    private void updateFrozenCandidate() {
        FrozenCandidate current = frozenCandidate;
        if (current != null) {
            if (isCandidateUsable(current.passive(), staticAttr.rearmDistance(), false)) return;
            clearCapture();
        }
        SimpleConnector found = findNearestCandidate(staticAttr.getCaptureDistance(), false);
        if (found == null) return;
        frozenCandidate = new FrozenCandidate(found, nextSequence++);
    }

    /**
     * 在同一载具内枚举有效简单端并选最近的一个。
     *
     * <p>先排除主动端自身、内部连接点、已占用、同刚体、未入世、跨物理空间、双向条件失败与朝向超出容差的端；
     * 距离并列在物理容差内时暂停捕获并限频诊断，不依赖容器遍历顺序。</p>
     *
     * @param maxDistance 允许的端点距离上限
     * @param restoring   同件存档恢复：允许刚体尚未入世
     */
    @Nullable
    private SimpleConnector findNearestCandidate(float maxDistance, boolean restoring) {
        VehicleCore vehicle = vehicle();
        if (vehicle == null) return null;
        SubPart activeSubPart = connector.getSubPart();
        SimpleConnector best = null;
        float bestDistance = Float.MAX_VALUE;
        float secondDistance = Float.MAX_VALUE;
        for (RegularPart part : vehicle.getPartMap().values()) {
            for (SubPart subPart : part.subParts.values()) {
                if (subPart == activeSubPart || subPart.isRemoved()) continue;
                for (AbstractConnector candidate : subPart.connectors.values()) {
                    if (!(candidate instanceof SimpleConnector simple)) continue;
                    if (simple.internal) continue;
                    if (simple.hasPart()) continue;
                    if (simple.getSubPart().isRemoved()) continue;
                    if (simple.getSubPart().part.assembly != vehicle) continue;
                    if (!connector.conditionCheck(simple.getSubPart().part)) continue;
                    if (!simple.conditionCheck(connector.getSubPart().part)) continue;
                    if (!restoring && !isCandidateUsable(simple, maxDistance, true)) continue;
                    float distance = ConnectorAlignmentHelper.getPositionError(connector, simple);
                    if (!isFinite(distance) || distance > maxDistance) continue;
                    if (ConnectorAlignmentHelper.getOppositeDirectionError(connector, simple)
                            > staticAttr.lockAngleToleranceRadians()) continue;
                    if (distance < bestDistance) {
                        secondDistance = bestDistance;
                        bestDistance = distance;
                        best = simple;
                    } else if (distance < secondDistance) {
                        secondDistance = distance;
                    }
                }
            }
        }
        if (best == null) return null;
        if (secondDistance != Float.MAX_VALUE && secondDistance - bestDistance <= TIE_TOLERANCE) {
            logAmbiguity(bestDistance);
            return null;
        }
        return best;
    }

    private boolean isCandidateUsable(SimpleConnector candidate, float maxDistance, boolean requireInWorld) {
        if (candidate.internal) return false;
        if (candidate.hasPart()) return false;
        if (candidate.getSubPart() == connector.getSubPart()) return false;
        if (candidate.getSubPart().isRemoved()) return false;
        VehicleCore vehicle = vehicle();
        if (vehicle == null || candidate.getSubPart().part.getAssembly() != vehicle) return false;
        if (!connector.conditionCheck(candidate.getSubPart().part)) return false;
        if (!candidate.conditionCheck(connector.getSubPart().part)) return false;
        SubPart a = connector.getSubPart();
        SubPart b = candidate.getSubPart();
        if (requireInWorld && (!a.body.isInWorld() || !b.body.isInWorld())) return false;
        if (a.getPhysicsLevel().getWorld() != b.getPhysicsLevel().getWorld()) return false;
        if (requireInWorld && !a.body.isInWorld()) return false;
        float distance = ConnectorAlignmentHelper.getPositionError(connector, candidate);
        return isFinite(distance) && distance <= maxDistance;
    }

    private void logAmbiguity(float distance) {
        if (tickCount - ambiguousLogTick < 100) return;
        ambiguousLogTick = tickCount;
        MachineMax.LOGGER.warn("锁扣 {} 有距离并列的候选端（{} m），本轮暂停捕获", getName(), distance);
    }

    private void clearCapture() {
        frozenCandidate = null;
        lockSample = null;
    }

    // ==================================================================
    // 物理线程
    // ==================================================================

    /** 物理线程：只对主线程冻结的候选施加合拢力。 */
    @Override
    public void onPrePhysicsTick() {
        if (getLevel().isClientSide() || !isActive()) return;
        FrozenCandidate candidate = frozenCandidate;
        if (candidate == null || isConnected()) return;
        SimpleConnector passive = candidate.passive();
        if (passive.hasPart() || connector.hasPart() || passive.getSubPart().isRemoved()) return;
        SubPart a = connector.getSubPart();
        SubPart b = passive.getSubPart();
        if (!a.body.isInWorld() || !b.body.isInWorld()) return;
        if (a.getPhysicsLevel().getWorld() != b.getPhysicsLevel().getWorld()) return;

        Vector3f pa = MMMath.relPointWorldPos(connector.offsetFromMassCenter.getTranslation(), a.body);
        Vector3f pb = MMMath.relPointWorldPos(passive.offsetFromMassCenter.getTranslation(), b.body);
        Vector3f delta = pb.subtract(pa);
        float distance = delta.length();
        if (!isFinite(distance) || distance >= staticAttr.getCaptureDistance()) return;
        if (distance < 1e-6f) return;
        if (ConnectorAlignmentHelper.getOppositeDirectionError(connector, passive)
                > staticAttr.lockAngleToleranceRadians()) return;

        float magnitude = CouplerCriteria.captureForce(distance, staticAttr.getCaptureDistance(),
                staticAttr.getMaxCaptureForce());
        if (magnitude <= 0f) return;
        Vector3f direction = new Vector3f(delta).normalize();
        Vector3f force = direction.mult(magnitude);
        // 休眠刚体会忽略外力；吸合必须先把两端唤醒。
        a.body.activate();
        b.body.activate();
        a.body.applyForce(force, pa.subtract(a.body.getPhysicsLocation(null)));
        b.body.applyForce(force.negate(), pb.subtract(b.body.getPhysicsLocation(null)));
    }

    /** 物理线程：采样锁止判据，发布最新一份不可变样本。 */
    @Override
    public void onPostPhysicsTick() {
        super.onPostPhysicsTick();
        if (getLevel().isClientSide() || !isActive()) return;
        FrozenCandidate candidate = frozenCandidate;
        if (candidate == null || isConnected()) return;
        SimpleConnector passive = candidate.passive();
        if (passive.hasPart() || connector.hasPart() || passive.getSubPart().isRemoved()) return;
        SubPart a = connector.getSubPart();
        SubPart b = passive.getSubPart();
        if (!a.body.isInWorld() || !b.body.isInWorld()) return;
        if (a.getPhysicsLevel().getWorld() != b.getPhysicsLevel().getWorld()) return;

        Vector3f pa = MMMath.relPointWorldPos(connector.offsetFromMassCenter.getTranslation(), a.body);
        Vector3f pb = MMMath.relPointWorldPos(passive.offsetFromMassCenter.getTranslation(), b.body);
        Vector3f delta = pb.subtract(pa);
        float distance = delta.length();
        Vector3f va = MMMath.relPointWorldVel(connector.offsetFromMassCenter.getTranslation(), a.body);
        Vector3f vb = MMMath.relPointWorldVel(passive.offsetFromMassCenter.getTranslation(), b.body);
        Vector3f relative = vb.subtract(va);
        float relativeSpeed = relative.length();
        float separationSpeed = distance > 1e-6f
                ? relative.dot(new Vector3f(delta).normalize()) : 0f;
        float angle = ConnectorAlignmentHelper.getOppositeDirectionError(connector, passive);
        lockSample = new LockSample(candidate.sequence(), nextSampleId++, distance, angle, relativeSpeed,
                separationSpeed);
    }

    @Override
    public void onDetach() {
        // 停用或卸载不是主动解扣：只丢掉捕获中的待办与吸力，保留仍然有效的机械连接。
        clearCapture();
        frozenCandidate = null;
        super.onDetach();
    }

    // ==================================================================
    // 交互
    // ==================================================================

    /**
     * 玩家点击：存在有效锁扣连接就请求解扣。
     *
     * <p>开门必须解扣；关门时多发的解扣请求无害——解扣瞬时完成，门合拢需要时间。没有连接时
     * 返回 {@link SignalResult#PASS}，让动画由交互区的动画事件照常切换。客户端回放只播动画，
     * 不在这里做任何权威写操作。</p>
     */
    @Override
    public SignalResult onInteract(LivingEntity entity) {
        if (getLevel().isClientSide()) return SignalResult.PASS;
        if (!isConnected() || connector == null || !connector.hasPart()) return SignalResult.PASS;
        AbstractConnector other = connector.getAttachedConnector();
        if (!(other instanceof SimpleConnector simple)) return SignalResult.PASS;
        VehicleCore vehicle = vehicle();
        if (vehicle == null) return SignalResult.PASS;
        vehicle.detachConnections(List.of(Pair.of(connector, simple)), java.util.Map.of());
        if (connector.hasPart()) return SignalResult.PASS; // 预事件否决：引用、关节、图与标记都不变
        lastCounterpart = simple;
        setConnected(false);
        return SignalResult.CONSUME;
    }

    // ==================================================================
    // 存档与同步
    // ==================================================================

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CONNECTED_ID, false);
    }

    @Override
    public void loadData(CompoundTag data) {
        super.loadData(data);
        if (data.contains(TAG_CONNECTED)) {
            getSynchedData().set(DATA_CONNECTED_ID, data.getBoolean(TAG_CONNECTED));
        }
    }

    @Override
    public CompoundTag saveData(CompoundTag data) {
        super.saveData(data);
        data.putBoolean(TAG_CONNECTED, isConnected());
        return data;
    }

    /**
     * 全量补推之前把标记对账到连接事实，完整载入后的首份快照即为真值。
     */
    @Override
    public void refreshSyncBaseline() {
        if (getLevel().isClientSide() || connector == null) return;
        tryRestoreSamePartConnection();
        reconcileConnected();
    }

    /** 客户端主线程：首份含 {@code connected} 的快照只建立声音基线。 */
    @Override
    public void onSyncedDataUpdated(List<SynchedEntityData.DataValue<?>> newData) {
        super.onSyncedDataUpdated(newData);
        if (!getLevel().isClientSide() || clientSoundReady) return;
        for (SynchedEntityData.DataValue<?> value : newData) {
            if (value.id() == DATA_CONNECTED_ID.id()) {
                lastClientConnected = isConnected();
                clientSoundReady = true;
                break;
            }
        }
    }

    /**
     * 客户端主线程：只在连续 tick 观察到 {@code connected} 变化时播放一次连接／断开音。
     * 首次取样、载入与重连补发都只建立基线。
     */
    private void tickClientSounds() {
        if (!clientSoundReady) return;
        long now = getLevel().getGameTime();
        boolean connected = isConnected();
        boolean consecutive = now == lastClientSoundTick + 1;
        if (consecutive && connected != lastClientConnected) {
            var position = getSubPart().getPosition();
            getLevel().playLocalSound(position.x, position.y, position.z,
                    connected ? staticAttr.getSounds().onAttach() : staticAttr.getSounds().onDetach(),
                    SoundSource.NEUTRAL, 1f, 1f, false);
        }
        lastClientConnected = connected;
        lastClientSoundTick = now;
    }

    // ==================================================================
    // 工具
    // ==================================================================

    @Nullable
    private VehicleCore vehicle() {
        if (connector == null) return null;
        return connector.getSubPart().part.getAssembly() instanceof VehicleCore vehicle ? vehicle : null;
    }

    /**
     * 主线程端点：用服务端刚体已发布的位姿快照与连接点局部偏移换算，不读动画 locator，
     * 也不读校正后的 {@code actualTransform}。
     */
    private static Vector3f endpoint(AbstractConnector connector) {
        Transform pose = connector.getSubPart().transform;
        Quaternion rotation = pose.getRotation();
        Vector3f local = connector.offsetFromMassCenter.getTranslation();
        Vector3f world = rotation == null ? local.clone() : MMMath.localVectorToWorldVector(local, rotation);
        return world.addLocal(pose.getTranslation());
    }

    private static boolean isFinite(float value) {
        return Float.isFinite(value);
    }

    /** 主线程冻结给物理侧的候选身份；端点每步按刚体当前位姿重算。 */
    private record FrozenCandidate(SimpleConnector passive, long sequence) {
    }

    /** 物理步后发布的锁止样本。 */
    private record LockSample(long sequence, long id, float distance, float angleError,
                              float relativeSpeed, float separationSpeed) {
    }
}
