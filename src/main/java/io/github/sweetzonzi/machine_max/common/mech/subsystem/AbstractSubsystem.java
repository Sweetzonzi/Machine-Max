package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import cn.solarmoon.spark_core.api.SparkLevel;
import cn.solarmoon.spark_core.physics.PenetrationKey;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.MMDamageExtensions;
import io.github.sweetzonzi.machine_max.common.mech.energy.IEnergyConsumer;
import io.github.sweetzonzi.machine_max.common.mech.energy.IEnergyProducer;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalReceiver;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalSender;
import io.github.sweetzonzi.machine_max.common.mech.signal.InteractSignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalChannel;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalResult;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.AbstractSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.event.subpart.SubPartDamageEvent;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.HitBox;
import io.github.sweetzonzi.machine_max.network.payload.SubsystemSyncPayload;
import jme3utilities.math.MyQuaternion;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SyncedDataHolder;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

@Getter
abstract public class AbstractSubsystem implements ISignalReceiver, ISignalSender, SyncedDataHolder {

    public final String name;
    public final AbstractSubsystemAttr attr;
    public final ISubsystemHost owner;

    public final Map<String, Map<String, ISignalReceiver>> targets = new HashMap<>();//信号频道名->接收者名称->接收者
    public final Map<String, Set<ISignalReceiver>> callbackTargets = new HashMap<>();//信号频道名->回调接收者
    public final ConcurrentMap<String, SignalChannel> signalInputChannels = new ConcurrentHashMap<>();
    public final ConcurrentMap<String, Float> resourceInputs = new ConcurrentHashMap<>();
    public final ConcurrentMap<String, Float> resourceOutputs = new ConcurrentHashMap<>();

    protected static final EntityDataAccessor<Float> DATA_DURABILITY_ID = SynchedEntityData.defineId(AbstractSubsystem.class, EntityDataSerializers.FLOAT);
    protected static final EntityDataAccessor<Boolean> DATA_ACTIVE_ID = SynchedEntityData.defineId(AbstractSubsystem.class, EntityDataSerializers.BOOLEAN);
    protected static final EntityDataAccessor<Boolean> DATA_DESTROYED_ID = SynchedEntityData.defineId(AbstractSubsystem.class, EntityDataSerializers.BOOLEAN);
    protected final SynchedEntityData synchedData;

    /**
     * 恢复门槛：被摧毁的子系统（或模块）耐久回到自身上限的这个比例才算修好，
     * 在此之前一直保持摧毁。一般子系统按整机耐久判定；模块化子系统按每个模块各自的
     * 最大耐久判定，{@link SubModule#applyRepair} 与 {@link #shouldRecover()} 共用这一条。
     */
    public static final float RECOVERY_RATIO = 0.3f;

    /**
     * 持久化的摧毁状态键。它不能由耐久唯一推出（摧毁后修到恢复门槛之前仍是摧毁），
     * 因此存档保留它，并在 {@link #restoreDestroyed} 里与耐久对照。
     * 模块化子系统的摧毁状态由各模块推导，{@link ModularSubsystem#saveData} 不写这个键。
     */
    protected static final String TAG_DESTROYED = "destroyed";

    /** 当前子系统为其宿主贡献的额外质量（kg），由子类通过 setExtraMass 管理 */
    private float extraMass = 0f;

    /**
     * 最近一次<b>有效</b>受击的定稿事件：本次伤害确实减少了本子系统耐久时才写入。
     * <p>
     * 有效受击以"本次应用确实减少了自身耐久"为准，不以伤害是否继续传导给零件为准。
     * 每次有效受击都更新它，导致耐久归零的那一次自然成为最后一条；零耐久后的追加命中
     * 不再减少耐久，因此不会覆盖它。
     * <p>
     * 保存的是<b>定稿副本</b>：金额、上下文与取消标志在写入后不再变化。命中箱不另存引用，
     * 起爆定位时经 {@link #resolveHitBox} 从事件里的命中箱名回宿主查当前那一个，
     * 因此结构变化替换掉命中箱实例也不会指到旧几何。
     * 新实例初值为 {@code null}，不从存档恢复事件对象引用；脱离生命周期清除。
     */
    @Nullable
    private SubPartDamageEvent.Pre lastDamageEvent;

    public int tickCount = 0;
    public int physicsTickCount = 0;

    protected AbstractSubsystem(ISubsystemHost owner, String name, AbstractSubsystemAttr attr) {
        this.owner = owner;
        this.attr = attr;
        this.name = name;
        SynchedEntityData.Builder syncheddata$builder = new SynchedEntityData.Builder(this);
        syncheddata$builder.define(DATA_DURABILITY_ID, attr.getBasicDurability());
        syncheddata$builder.define(DATA_ACTIVE_ID, true);
        syncheddata$builder.define(DATA_DESTROYED_ID, false);
        this.defineSynchedData(syncheddata$builder);
        this.synchedData = syncheddata$builder.build();
        this.resetSignalOutputs();
    }

    /**
     * 子系统以自身名称为寻址名（虚拟分派，脚本子系统可经 Hook 覆写 getName()）。
     */
    @Override
    public String getSignalAddress() {
        return getName();
    }

    public void onTick() {
        tickCount++;
        if (!getLevel().isClientSide()) {
            boolean dataDestroyed = synchedData.get(DATA_DESTROYED_ID);
            if (!dataDestroyed && shouldDestroy()) {
                // 摧毁判定通过 → 标记摧毁并触发回调。
                synchedData.set(DATA_DESTROYED_ID, true);
                onDestroyed();
            } else if (dataDestroyed && !shouldDestroy() && shouldRecover()) {
                // 已摧毁但已满足恢复条件 → 清理上一生命周期的受击记录，再标记恢复并触发回调
                clearLastDamageRecord();
                synchedData.set(DATA_DESTROYED_ID, false);
                onRecovered();
            }
        }
        if(!getSubPart().getLevel().isClientSide()) syncToClient();
    }

    /**
     * 是否需要判定为摧毁。默认耐久 ≤ 0。
     * 模块化子系统覆写以检查 critical 模块状态。
     */
    protected boolean shouldDestroy() {
        return getDurability() <= 0;
    }

    /**
     * 是否已恢复到可运转状态。默认耐久 ≥ {@link #RECOVERY_RATIO}。
     * 模块化子系统覆写以检查所有 critical 模块是否都已脱离摧毁状态。
     */
    protected boolean shouldRecover() {
        return getDurability() >= getMaxDurability() * RECOVERY_RATIO;
    }

    /**
     * 从摧毁状态恢复时的回调。默认空实现，子类覆写用于重新注册能源网等业务逻辑。
     * 注意：DATA_DESTROYED_ID 已由 onTick() 框架层设置为 false，回调中不需重复操作。
     */
    protected void onRecovered() {}

    public void onPrePhysicsTick() {
    }

    public void onPostPhysicsTick() {
        physicsTickCount++;
    }

    /**
     * 子系统对应的碰撞箱与方块发生碰撞时调用。调用于物理线程。<p>
     * Called when the hit-box of the subsystem collides with block. Called on the physics thread.
     */
    public void onCollideWithBlock(
            PhysicsRigidBody subPartBody,
            PhysicsRigidBody blockBody,
            BlockPos blockPos,
            BlockState blockState,
            Vector3f relativeVelocity,
            Vector3f normal,
            Vector3f contactPoint,
            float impartAngle,
            HitBox hitBox,
            long manifoldPointId) {
    }

    public void onCollideWithPart(
            PhysicsRigidBody subPartBody,
            PhysicsRigidBody otherSubPartBody,
            Vector3f relativeVelocity,
            Vector3f normal,
            Vector3f contactPoint,
            float impartAngle,
            HitBox hitBox,
            HitBox otherHitBox,
            long manifoldPointId
    ) {
    }

    public void onCollideWithEntity(
            PhysicsRigidBody subPartBody,
            PhysicsRigidBody entityBody,
            Vector3f relativeVelocity,
            Vector3f normal,
            Vector3f contactPoint,
            float impartAngle,
            HitBox hitBox,
            long manifoldPointId
    ) {
    }

    public void onAttach() {
        var grid = owner.getEnergyGrid();
        if (grid != null) {
            if (this instanceof IEnergyProducer p) grid.registerProducer(p);
            if (this instanceof IEnergyConsumer c) grid.registerConsumer(c);
        }
    }

    public void onDetach() {
        var grid = owner.getEnergyGrid();
        if (grid != null) {
            if (this instanceof IEnergyProducer p) grid.unregisterProducer(p);
            if (this instanceof IEnergyConsumer c) grid.unregisterConsumer(c);
        }
        clearLastDamageRecord();
    }

    public void onDisabled() {
    }

    public void onActive() {
    }

    /**
     * 默认受击处理：直接扣减自身耐久；只有本次确实减少了耐久时才记为最近有效受击。
     * <p>
     * 记录供 tick 判毁后的起爆定位使用（见 {@link #getLastDamageEvent()} 与
     * {@link #getLastDamageOrigin()}）。{@link BasicSubsystem} 覆写后调用本实现，
     * 因此同样走这条记录；{@link ModularSubsystem} 自行路由伤害，另行比较聚合耐久后记录。
     * <p>
     * <b>调用线程：</b>主线程（命中统一结算阶段）。
     *
     * @param event 本次命中的入口事件，可被子类修改金额或取消
     */
    public void onHurt(SubPartDamageEvent.Pre event) {
        float before = getDurability();
        setDurability(Math.clamp(before - event.getDamageAmount(), 0, getMaxDurability()));
        if (getDurability() < before) {
            recordLastDamage(event);
        }
    }

    /**
     * 记录一次最近有效受击：保存定稿事件副本。
     * <p>
     * 事件在记录前已定稿（金额、上下文与取消标志），保存后不再修改；调用方无需、也不应
     * 再回写原事件。命中箱不在这里固化——{@link #resolveHitBox} 按事件里的命中箱名
     * 回宿主查当前实例，没有该扩展时回退到宿主的最厚命中箱，与当次结算的目标一致。
     * <p>
     * <b>调用线程：</b>主线程。
     *
     * @param event 本次已定稿的入口事件
     */
    protected void recordLastDamage(SubPartDamageEvent.Pre event) {
        this.lastDamageEvent = finalizedCopy(event);
    }

    /** 事件的可长期保存副本：金额、上下文与取消标志定格在当次结算。 */
    protected static SubPartDamageEvent.Pre finalizedCopy(SubPartDamageEvent.Pre event) {
        SubPartDamageEvent.Pre finalized = new SubPartDamageEvent.Pre(
                event.getSubPart(), event.getCtx(), event.getDamageAmount());
        if (event.isCanceled()) finalized.setCanceled(true);
        return finalized;
    }

    /**
     * 把一次受击记录解析成宿主当前的命中箱：从事件上下文取 {@code HIT_BOX} 扩展里的命中箱名，
     * 再用该名向宿主取当前那一个。没有扩展时沿用 SubPart 的回退判定（最厚命中箱）。
     * <p>
     * 只按名字解析，因此结构变化替换掉命中箱实例后不会指到旧几何；名字在宿主上已不存在时返回
     * {@code null}，调用方按"没有可用来源"处理。
     *
     * @param event 受击记录，可为 {@code null}
     * @return 宿主当前的命中箱；无记录、无宿主或名字已失效时为 {@code null}
     */
    @Nullable
    protected HitBox resolveHitBox(@Nullable SubPartDamageEvent.Pre event) {
        SubPart host = getSubPart();
        if (host == null || event == null) return null;
        HitBox hitBox = event.getCtx() == null
                ? null
                : event.getCtx().extensions().get(MMDamageExtensions.HIT_BOX);
        if (hitBox == null) return host.findStrongestHitBox();
        return host.hitBoxes.get(hitBox.getAttr().getId());
    }

    /**
     * 最近一次有效受击的定稿事件；从未有效受击或已清理时为 {@code null}。
     *
     * @return 事件副本，只读使用
     */
    @Nullable
    public SubPartDamageEvent.Pre getLastDamageEvent() {
        return lastDamageEvent;
    }

    /**
     * 清除最近有效受击记录（恢复与脱离生命周期）。
     */
    protected void clearLastDamageRecord() {
        this.lastDamageEvent = null;
    }

    /**
     * 起爆定位与豁免的唯一依据：本子系统最近一次有效受击指认的命中箱。
     * <p>
     * 基类读子系统级的记录（{@link #lastDamageEvent}）；模块化子系统可覆写为读某个模块的记录，
     * 让殉爆只认打到该模块身上的那一击。定位与豁免都只经过这一个方法，
     * 因此"用哪个命中箱定位、豁免哪个命中箱"不会各走一路。
     *
     * @return 命中箱；没有有效受击记录时为 {@code null}
     */
    @Nullable
    public HitBox getLastHitBox() {
        return resolveHitBox(lastDamageEvent);
    }

    /**
     * 起爆定位用的世界坐标：按 {@link #getLastHitBox()} 的命中箱局部形心与宿主
     * <b>已发布的</b>位姿换算。
     * <p>
     * 换算式 {@code worldOrigin = hostPosition + hostRotation × hitBox.localCenter}，
     * 其中位姿取宿主 {@code DestroyableObject#transform}（主线程可读的已发布快照），
     * 不直接读取正在步进的物理刚体。
     * <p>
     * 没有命中箱来源时回退宿主质心（即 {@code hostPosition}）。首期没有延迟殉爆请求队列，
     * 因此起爆当时固定该坐标。
     * <p>
     * <b>调用线程：</b>主线程（子系统 tick 的摧毁边沿）。
     *
     * @return 世界坐标（新实例）；宿主不可用时为零向量
     */
    public Vector3f getLastDamageOrigin() {
        SubPart host = getSubPart();
        if (host == null) return new Vector3f();
        Transform hostPose = host.transform;
        HitBox hitBox = getLastHitBox();
        if (hitBox == null) return hostPose.getTranslation().clone();
        Vector3f localCenter = hitBox.getLocalCenter();
        Vector3f origin = hostPose.getRotation() == null
                ? localCenter.clone()
                : MyQuaternion.rotate(hostPose.getRotation(), localCenter, new Vector3f());
        return origin.addLocal(hostPose.getTranslation());
    }

    /**
     * 殉爆起爆时应被豁免的穿透键：{@link #getLastHitBox()} 指认的那<b>一个</b>命中箱。
     *
     * <p>起爆点由 {@link #getLastDamageOrigin()} 给出，取的是该命中箱的局部形心，因此几何上
     * 必然落在它内部。普通爆炸的射线自该点出发后第一次命中的就是同一个命中箱，命中距离仍在近场
     * 平台内（{@code d < near_radius}，故 {@code I ≈ 1}），穿深按近场满值结算，要么直接把射线拦死、
     * 要么按 η 吃掉相当比例的能量，表现为爆炸威力被自身外壳挡掉。把键交给
     * {@code ExplosionManager#detonate} 的豁免重载即可跳过它。</p>
     *
     * <p>只豁免这一个命中箱：同一零件的其余装甲命中箱仍正常参与穿透与伤害结算，
     * 即"弹从自己肚子里炸开"不额外吃亏，但爆炸仍须穿透其余结构才能外传。</p>
     *
     * <p>没有命中箱来源时返回空集——不知道起爆点在哪个命中箱里，就一个都不豁免；
     * 不把宿主全部命中箱放行，那种豁免等于让整块装甲对这次爆炸透明。</p>
     *
     * <p><b>调用线程：</b>主线程（与起爆调用同一处）。</p>
     *
     * @return 不可变豁免键集合；无来源或宿主不存在时为空集
     */
    @NotNull
    public Set<PenetrationKey> getCookoffIgnoredKeys() {
        SubPart host = getSubPart();
        HitBox hitBox = getLastHitBox();
        if (host == null || hitBox == null) return Set.of();
        return Set.of(new PenetrationKey(host, hitBox.getAttr().getId()));
    }

    public void onDestroyed() {
        synchedData.set(DATA_DESTROYED_ID, true);
    }

    public boolean isDestroyed() {
        return synchedData.get(DATA_DESTROYED_ID);
    }

    @Override
    public SignalResult onSignalUpdated(String channelName, ISignalSender sender) {
        SignalResult result = ISignalReceiver.super.onSignalUpdated(channelName, sender);
        Object signal = getSignalValueFrom(channelName, sender);
        if (signal instanceof InteractSignal interactSignal) {
            // 交互结果原样回传给交互区：CONSUME 表示已接管，PASS/FAIL 表示本次未能接管（如座位被占），
            // 交互区据此决定是否继续尝试下一个目标。
            return onInteract(interactSignal.getEntity());
        }
        return result;
    }

    /**
     * <p>子系统被实体交互时调用，调用于主线程</p>
     * <p>Called when the subsystem is interacted with an entity. Called on the main thread.</p>
     *
     * @param entity 交互的实体
     */
    public SignalResult onInteract(LivingEntity entity) {
        return SignalResult.PASS;
    }

    /**
     * 载具结构变化的第一阶段：清空由拓扑推导出来的缓存。<br>
     * {@link SubsystemController#onVehicleStructureChanged()} 先对所有子系统调用本方法，再统一调用
     * {@link #onVehicleStructureChanged()} 让它们重新握手。两个阶段分开是必要的：某个子系统的清空
     * （例如发射器丢掉旧的供给者登记表）若落在另一个子系统的握手之后，本轮刚发现的结果会被清掉，
     * 并一直保持为空到下一次结构变化。
     * <p>
     * 默认空实现。只有持有"结构变化后必须重建的发现结果"的子系统才覆写本方法。
     * <p>
     * <b>调用线程：</b>主线程。
     */
    public void onVehicleStructurePreChanged() {
    }

    /**
     * 载具结构变化的第二阶段：重建总线订阅之后让子系统重新握手。<br>
     * 由 {@link #onVehicleStructurePreChanged()} 先清空缓存，因此本方法里可以安全地重新发现拓扑；
     * 需要清理的缓存应写在那一个方法里，本方法只负责重新建立关系。
     * <p>
     * <b>调用线程：</b>主线程。
     */
    public void onVehicleStructureChanged() {
        this.clearCallbackChannel();
    }

    public boolean isActive() {
        return synchedData.get(DATA_ACTIVE_ID)
                && !this.isDestroyed()
                && !getOwner().getSubPart().isDestroyed()
                && getSubPart().part.permitsSubsystemWork();
    }

    public void setActive(boolean active) {
        if (active && !synchedData.get(DATA_ACTIVE_ID)) {
            synchedData.set(DATA_ACTIVE_ID, true);
            if (!getLevel().isClientSide()) this.onActive();
        } else if (!active && synchedData.get(DATA_ACTIVE_ID)) {
            synchedData.set(DATA_ACTIVE_ID, false);
            if (!getLevel().isClientSide()) this.onDisabled();
        }
    }

    /**
     * 载入耐久与摧毁状态。<br>
     * 耐久按 {@code durability_ratio} 的比例写入 {@link #setDurability}，因此超出当前上限的比例、
     * 以及非有限的非法值都在这里收敛（非法值记一条诊断后按满耐久载入）。模块化子系统的
     * {@code setDurability} 会把这一笔按比例分摊进模块，旧存档因此不需要单独的迁移分支。
     * <p>
     * 摧毁状态要单独存：它不能由耐久唯一决定——子系统在摧毁后修到恢复门槛（{@link #shouldRecover()}）
     * 之前一直保持摧毁，那段区间里"已摧毁但耐久为正"同样是合法状态。载入时按
     * {@link #restoreDestroyed} 的规则把存档值与当前耐久对照一次，首个 tick 的摧毁边沿
     * 因此不会把历史状态当成一次新的摧毁事件。
     * <p>
     * 模块化子系统的 {@code shouldDestroy()} / {@code shouldRecover()} 要看各模块，而模块在父类这一步
     * 还没读入，因此它在模块读入之后再执行同一规则修正一次。
     */
    public void loadData(CompoundTag data) {
        //加载子系统耐久度（按比例持久化；旧数据缺失该键时视为满耐久）
        float ratio = data.contains("durability_ratio") ? data.getFloat("durability_ratio") : 1f;
        if (!Float.isFinite(ratio)) {
            MachineMax.LOGGER.warn("子系统 '{}' 的存档耐久比例不是有限数（{}），按满耐久载入", getName(), ratio);
            ratio = 1f;
        }
        setDurability(Math.clamp(ratio, 0f, 1f) * getMaxDurability());
        restoreDestroyed(data);
    }

    /**
     * 用存档里的摧毁标记恢复摧毁状态，并与当前耐久对照。<br>
     * 两条都比存档值优先：
     * <ul>
     *   <li>耐久判定为摧毁（{@link #shouldDestroy()}）→ 一律恢复成摧毁，缺该键的旧存档也走这条</li>
     *   <li>耐久已经到恢复门槛（{@link #shouldRecover()}）→ 即便存档写着摧毁也算已恢复，
     *       手改或旧版本写出的"满耐久 + 摧毁"不会留在存档里</li>
     * </ul>
     * 其余情况沿用存档值，因此 0%—30% 之间的恢复锁存能跨存档保留。
     */
    protected void restoreDestroyed(CompoundTag data) {
        boolean stored = data.getBoolean(TAG_DESTROYED);
        synchedData.set(DATA_DESTROYED_ID, shouldDestroy() || (stored && !shouldRecover()));
    }

    public CompoundTag saveData(CompoundTag data) {
        //保存子系统耐久度比例，避免内容包调整耐久上限后存档数值失真
        float maxDurability = getMaxDurability();
        data.putFloat("durability_ratio", maxDurability > 0f
                ? Math.clamp(getDurability() / maxDurability, 0f, 1f)
                : 1f);
        data.putBoolean(TAG_DESTROYED, isDestroyed());
        return data;
    }

    @Override
    public SubPart getSubPart() {
        return getOwner().getSubPart();
    }

    @Override
    public void onSyncedDataUpdated(@NotNull List<SynchedEntityData.DataValue<?>> newData) {}

    @Override
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> dataAccessor) {
        if (getLevel().isClientSide()) {
            if (dataAccessor.equals(DATA_ACTIVE_ID)) {
                if (synchedData.get(DATA_ACTIVE_ID)) this.onActive();
                else this.onDisabled();
            } else if (dataAccessor.equals(DATA_DESTROYED_ID)) {
                if (synchedData.get(DATA_DESTROYED_ID)) this.onDestroyed();
            }
        }
    }

    protected void defineSynchedData(SynchedEntityData.Builder builder){}

    protected void syncToClient() {
        if (!getSubPart().level.isClientSide()) {
            SynchedEntityData synchedentitydata = this.getSynchedData();
            List<SynchedEntityData.DataValue<?>> list = synchedentitydata.packDirty();
            if (list != null) {
                PacketDistributor.sendToPlayersInDimension((ServerLevel) getSubPart().level, new SubsystemSyncPayload(getSubPart().getId(), name, list));
            }
        }
    }

    /**
     * 只读全量快照：按访问器声明顺序遍历本子系统全部同步值，<b>不触碰任何脏标记</b>。
     * <p>
     * 与 {@link #syncToClient()} 的分工：那条路是"变化广播"，只在值真的变过时发得出去，
     * 而且是发给维度内所有玩家；本方法只取值、不发包、不改状态，供
     * {@link SubsystemController#pushAllTo(ServerPlayer)} 给单个玩家补一份权威初值。
     * <p>
     * 两者都不写 {@code DataItem} 的脏标记与全局脏门：补推之后，该值若本来就是脏的，
     * 仍会按原节奏经变化广播发给所有人。
     * <p>
     * <b>调用线程：</b>主线程（服务端）。
     *
     * @param consumer 逐个接收同步值的消费者，通常把值攒成列表
     */
    public void forEachSyncedValue(Consumer<SynchedEntityData.DataValue<?>> consumer) {
        for (SynchedEntityData.DataItem<?> item : getSynchedData().itemsById) {
            // 槽位 0 恒为空：defineId 从 1 开始分配
            if (item != null) consumer.accept(item.value());
        }
    }

    public float getDurability() {
        return synchedData.get(DATA_DURABILITY_ID);
    }

    public void setDurability(float durability) {
        synchedData.set(DATA_DURABILITY_ID, durability);
    }

    public float getMaxDurability() {
        return attr.getBasicDurability();
    }

    public Level getLevel() {
        return getOwner().getLevel();
    }

    public PhysicsLevel getPhysicsLevel() {
        return SparkLevel.getPhysicsLevel(getLevel());
    }

    /**
     * 获取此子系统当前贡献的额外质量。
     * @return 额外质量（kg）
     */
    public float getExtraMass() {
        return extraMass;
    }

    /**
     * 设置此子系统的额外质量，自动同步到宿主映射表并触发质量回调。
     * 子类在内部状态变化时调用（如物品增减、弹药变化、乘员上下）。
     *
     * @param extraMass 新的额外质量值（kg），≤0 表示无额外贡献
     */
    public void setExtraMass(float extraMass) {
        this.extraMass = Math.max(extraMass, 0f);
        if (owner != null) {
            owner.updateExtraMass(this, this.extraMass);
        }
    }

}
