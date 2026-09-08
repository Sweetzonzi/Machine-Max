package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import cn.solarmoon.spark_core.api.SparkLevel;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
                // 摧毁判定通过 → 标记摧毁并触发回调
                synchedData.set(DATA_DESTROYED_ID, true);
                onDestroyed();
            } else if (dataDestroyed && !getOwner().getSubPart().isDestroyed()
                       && !shouldDestroy() && shouldRecover()) {
                // 已摧毁但已满足恢复条件 → 标记恢复并触发回调
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
    }

    public void onDisabled() {
    }

    public void onActive() {
    }

    public void onHurt(SubPartDamageEvent.Pre event) {
        setDurability(Math.clamp(getDurability() - event.getDamageAmount(), 0, getMaxDurability()));
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
            onInteract(interactSignal.getEntity());
            return SignalResult.CONSUME;
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

    public void onVehicleStructureChanged() {
        this.clearCallbackChannel();
    }

    public boolean isActive() {
        return synchedData.get(DATA_ACTIVE_ID)
                && !this.isDestroyed()
                && !getOwner().getSubPart().isDestroyed()
                && getSubPart().part.getAssemblingProgress() >= getSubPart().part.type.getFunctionalThreshold();
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
        //加载子系统耐久度
        setDurability(data.getFloat("durability"));
    }

    public CompoundTag saveData(CompoundTag data) {
        //保存子系统耐久度
        data.putFloat("durability", getDurability());
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
