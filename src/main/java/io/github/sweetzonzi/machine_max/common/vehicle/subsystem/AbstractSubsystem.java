package io.github.sweetzonzi.machine_max.common.vehicle.subsystem;

import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.dynamic_attr.AbstractSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.data.PartDamageData;
import io.github.sweetzonzi.machine_max.common.vehicle.interact.HitBox;
import io.github.sweetzonzi.machine_max.common.vehicle.signal.ISignalReceiver;
import io.github.sweetzonzi.machine_max.common.vehicle.signal.ISignalSender;
import io.github.sweetzonzi.machine_max.common.vehicle.signal.InteractSignal;
import io.github.sweetzonzi.machine_max.common.vehicle.signal.SignalChannel;
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
    protected final SynchedEntityData synchedData;

    public volatile boolean active = true;
    public volatile boolean destroyed = false;
    public int tickCount = 0;
    public int physicsTickCount = 0;

    protected AbstractSubsystem(ISubsystemHost owner, String name, AbstractSubsystemAttr attr) {
        this.owner = owner;
        this.attr = attr;
        this.name = name;
        SynchedEntityData.Builder syncheddata$builder = new SynchedEntityData.Builder(this);
        syncheddata$builder.define(DATA_DURABILITY_ID, attr.getBasicDurability());
        this.defineSynchedData(syncheddata$builder);
        this.synchedData = syncheddata$builder.build();
        this.resetSignalOutputs();
    }

    public void onTick() {
        tickCount++;
        if (!this.isDestroyed() && this.getDurability() <= 0) {
            //摧毁耐久度归零的子系统
            this.onDestroyed();
        } else if (this.isDestroyed() && !getOwner().getSubPart().isDestroyed() && this.getDurability() >= 0.3 * getMaxDurability()) {
            //重新激活修复到一定程度的子系统
            this.destroyed = false;
        }
        if(!getSubPart().getLevel().isClientSide()) syncToClient();
    }

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
    }

    public void onDetach() {
    }

    public void onDisabled() {
    }

    public void onActive() {
    }

    public void onHurt(float amount, PartDamageData data) {
        setDurability(Math.clamp(getDurability() - amount, 0, getMaxDurability()));
    }

    public void onDestroyed() {
        this.destroyed = true;
    }

    @Override
    public void onSignalUpdated(String channelName, ISignalSender sender) {
        ISignalReceiver.super.onSignalUpdated(channelName, sender);
        Object signal = getSignalValueFrom(channelName, sender);
        if (signal instanceof InteractSignal interactSignal) {
            LivingEntity entity = interactSignal.getEntity();
            onInteract(entity);
        }
    }

    /**
     * <p>子系统被实体交互时调用，调用于主线程</p>
     * <p>Called when the subsystem is interacted with an entity. Called on the main thread.</p>
     *
     * @param entity 交互的实体
     */
    public void onInteract(LivingEntity entity) {
    }

    public void onVehicleStructureChanged() {
        this.clearCallbackChannel();
    }

    public boolean isActive() {
        return active && !this.isDestroyed() && !getOwner().getSubPart().isDestroyed() && getSubPart().part.getAssemblingProgress() >= 1;
    }

    public void setActive(boolean active) {
        if (active && !this.active) {
            this.active = true;
            this.onActive();
        } else if (!active && this.active) {
            this.active = false;
            this.onDisabled();
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
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> dataAccessor) {}

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

}
