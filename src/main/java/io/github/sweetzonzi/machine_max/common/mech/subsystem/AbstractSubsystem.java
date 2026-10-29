package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import cn.solarmoon.spark_core.api.SparkLevel;
import cn.solarmoon.spark_core.physics.PenetrationKey;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
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
import java.util.LinkedHashSet;
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

    /** 当前子系统为其宿主贡献的额外质量（kg），由子类通过 setExtraMass 管理 */
    private float extraMass = 0f;

    /**
     * 最近一次<b>有效</b>受击的定稿事件：本次伤害确实减少了本子系统耐久时才写入。
     * <p>
     * 有效受击以"本次应用确实减少了自身耐久"为准，不以伤害是否继续传导给零件为准。
     * 每次有效受击都更新它，导致耐久归零的那一次自然成为最后一条；零耐久后的追加命中
     * 不再减少耐久，因此不会覆盖它。
     * <p>
     * 保存的是<b>定稿副本</b>：金额、上下文与取消标志在写入后不再变化，供 tick 判毁后的
     * 起爆定位使用。新实例初值为 {@code null}，不从存档恢复事件对象引用；脱离生命周期清除。
     */
    @Nullable
    private SubPartDamageEvent.Pre lastDamageEvent;

    /**
     * 与 {@link #lastDamageEvent} 配套的命中箱：优先取事件上下文里的 {@code HIT_BOX} 扩展，
     * 没有该扩展时保存 SubPart 回退解析（最厚命中箱）的结果。起爆定位用它读取
     * {@link HitBox#getLocalCenter()}；为 {@code null} 时回退宿主质心。
     */
    @Nullable
    private HitBox lastDamageHitBox;

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
            SubPart host = getOwner().getSubPart();
            boolean hostGone = host == null || host.isDestroyed();
            boolean dataDestroyed = synchedData.get(DATA_DESTROYED_ID);
            if (!dataDestroyed && shouldDestroy() && !hostGone) {
                // 摧毁判定通过 → 标记摧毁并触发回调。
                // 宿主已销毁/已被标记摧毁时不补发 onDestroyed：宿主销毁、卸载与脱离只走各自生命周期
                synchedData.set(DATA_DESTROYED_ID, true);
                onDestroyed();
            } else if (dataDestroyed && !hostGone
                       && !shouldDestroy() && shouldRecover()) {
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
     * 是否已恢复到可运转状态。默认耐久 ≥ 30%。
     * 模块化子系统覆写以检查所有 critical 模块是否已修复至阈值以上。
     */
    protected boolean shouldRecover() {
        return getDurability() >= getMaxDurability() * 0.3f;
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
     * 记录一次最近有效受击：保存定稿事件副本与本次实际指认的命中箱。
     * <p>
     * 事件在记录前已定稿（金额、上下文与取消标志），保存后不再修改；调用方无需、也不应
     * 再回写原事件。命中箱优先取上下文里的 {@code HIT_BOX} 扩展；入口没有该扩展时，
     * 复现 SubPart 的回退判定（最厚命中箱），保证与当次实际结算的目标一致。
     * <p>
     * <b>调用线程：</b>主线程。
     *
     * @param event 本次已定稿的入口事件
     */
    protected void recordLastDamage(SubPartDamageEvent.Pre event) {
        SubPartDamageEvent.Pre finalized = new SubPartDamageEvent.Pre(
                getSubPart(), event.getCtx(), event.getDamageAmount());
        if (event.isCanceled()) finalized.setCanceled(true);
        this.lastDamageEvent = finalized;
        this.lastDamageHitBox = resolveEventHitBox(event);
    }

    /**
     * 解析本次事件实际使用的命中箱：优先取上下文扩展，缺省回退到宿主的最厚命中箱。
     *
     * @param event 本次命中事件
     * @return 命中箱；宿主没有命中箱时为 {@code null}
     */
    @Nullable
    private HitBox resolveEventHitBox(SubPartDamageEvent.Pre event) {
        if (event.getCtx() == null) return null;
        HitBox hitBox = event.getCtx().extensions().get(MMDamageExtensions.HIT_BOX);
        if (hitBox != null) return hitBox;
        return getSubPart().findStrongestHitBox();
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
     * 最近一次有效受击指认的命中箱；无有效来源时为 {@code null}。
     *
     * @return 命中箱，只读使用
     */
    @Nullable
    public HitBox getLastDamageHitBox() {
        return lastDamageHitBox;
    }

    /**
     * 清除最近有效受击记录（恢复与脱离生命周期）。
     */
    protected void clearLastDamageRecord() {
        this.lastDamageEvent = null;
        this.lastDamageHitBox = null;
    }

    /**
     * 起爆定位用的世界坐标：按最近有效受击指认的命中箱局部形心与宿主<b>已发布的</b>位姿换算。
     * <p>
     * 换算式 {@code worldOrigin = hostPosition + hostRotation × hitBox.localCenter}，
     * 其中位姿取宿主 {@code DestroyableObject#transform}（主线程可读的已发布快照），
     * 不直接读取正在步进的物理刚体。
     * <p>
     * 以下情况回退宿主质心（即 {@code hostPosition}）：
     * 没有最近有效事件、事件没有有效命中箱、命中箱已被替换/不属于当前宿主几何，
     * 或宿主当前位姿不可用。首期没有延迟殉爆请求队列，因此起爆当时固定该坐标。
     * <p>
     * <b>调用线程：</b>主线程（子系统 tick 的摧毁边沿）。
     *
     * @return 世界坐标（新实例）；宿主位姿不可用时为零向量
     */
    public Vector3f getLastDamageOrigin() {
        SubPart host = getSubPart();
        if (host == null) return new Vector3f();
        Transform hostPose = host.transform;
        HitBox hitBox = lastDamageHitBox;
        if (hitBox != null && isHitBoxUsable(hitBox)) {
            Vector3f localCenter = hitBox.getLocalCenter();
            Vector3f origin = hostPose.getRotation() == null
                    ? localCenter.clone()
                    : MyQuaternion.rotate(hostPose.getRotation(), localCenter, new Vector3f());
            return origin.addLocal(hostPose.getTranslation());
        }
        return hostPose.getTranslation().clone();
    }

    /**
     * 命中箱的几何身份是否仍然有效：属于当前宿主，且仍是该宿主登记过的命中箱之一。
     *
     * @param hitBox 候选命中箱
     * @return true 表示可以按其局部形心定位
     */
    private boolean isHitBoxUsable(HitBox hitBox) {
        SubPart host = getSubPart();
        return host != null && hitBox.getSubPart() == host && host.hitBoxes.containsValue(hitBox);
    }

    /**
     * 殉爆起爆时应被豁免的穿透键：起爆点所在的那个<b>自身</b>命中箱。
     *
     * <p>起爆点由 {@link #getLastDamageOrigin()} 给出，取的是命中箱的局部形心，因此几何上必然
     * 落在该命中箱内部。普通爆炸的射线自该点出发后第一次命中的就是同一个命中箱，命中距离仍在近场
     * 平台内（{@code d < near_radius}，故 {@code I ≈ 1}），穿深按近场满值结算，要么直接把射线拦死、
     * 要么按 η 吃掉相当比例的能量，表现为爆炸威力被自身外壳挡掉。把键交给
     * {@code ExplosionManager#detonate} 的豁免重载即可跳过它。</p>
     *
     * <p>只豁免定位所依据的那一个命中箱：同一零件其余的装甲命中箱仍正常参与穿透与伤害结算，
     * 即"弹从自己肚子里炸开"不额外吃亏，但爆炸仍须穿透其余结构才能外传。</p>
     *
     * <p>命中箱来源无效（无最近有效事件、命中箱已失效）时，起爆点回退宿主质心，此时无法确定
     * 落在哪个命中箱内，故豁免宿主登记的全部命中箱。</p>
     *
     * <p><b>调用线程：</b>主线程（与起爆调用同一处）。</p>
     *
     * @return 不可变豁免键集合；宿主不存在时为空集
     */
    @NotNull
    public Set<PenetrationKey> getCookoffIgnoredKeys() {
        SubPart host = getSubPart();
        if (host == null || host.hitBoxes.isEmpty()) return Set.of();
        HitBox hitBox = lastDamageHitBox;
        if (hitBox != null && isHitBoxUsable(hitBox)) {
            return Set.of(new PenetrationKey(host, hitBox.getAttr().getId()));
        }
        Set<PenetrationKey> keys = new LinkedHashSet<>();
        for (HitBox box : host.hitBoxes.values()) {
            keys.add(new PenetrationKey(host, box.getAttr().getId()));
        }
        return Set.copyOf(keys);
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

    public void loadData(CompoundTag data) {
        //加载子系统耐久度（按比例持久化；旧数据缺失该键时视为满耐久）
        float ratio = data.contains("durability_ratio") ? data.getFloat("durability_ratio") : 1f;
        setDurability(Math.clamp(ratio, 0f, 1f) * getMaxDurability());
    }

    public CompoundTag saveData(CompoundTag data) {
        //保存子系统耐久度比例，避免内容包调整耐久上限后存档数值失真
        float maxDurability = getMaxDurability();
        data.putFloat("durability_ratio", maxDurability > 0f
                ? Math.clamp(getDurability() / maxDurability, 0f, 1f)
                : 1f);
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
