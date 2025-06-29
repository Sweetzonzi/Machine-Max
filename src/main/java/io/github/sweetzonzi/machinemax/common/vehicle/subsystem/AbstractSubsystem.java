package io.github.sweetzonzi.machinemax.common.vehicle.subsystem;

import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machinemax.common.vehicle.HitBox;
import io.github.sweetzonzi.machinemax.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machinemax.common.vehicle.Part;
import io.github.sweetzonzi.machinemax.common.vehicle.attr.subsystem.AbstractSubsystemAttr;
import io.github.sweetzonzi.machinemax.common.vehicle.signal.ISignalReceiver;
import io.github.sweetzonzi.machinemax.common.vehicle.signal.ISignalSender;
import io.github.sweetzonzi.machinemax.common.vehicle.signal.SignalChannel;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Getter
abstract public class AbstractSubsystem implements ISignalReceiver, ISignalSender {

    public final String name;
    public final AbstractSubsystemAttr attr;
    public final ISubsystemHost owner;
    public HitBox hitBox;

    public final Map<String, Map<String, ISignalReceiver>> targets = new HashMap<>();//信号频道名->接收者名称->接收者
    public final Map<String, Set<ISignalReceiver>> callbackTargets = new HashMap<>();//信号频道名->回调接收者
    public final ConcurrentMap<String, SignalChannel> signalInputChannels = new ConcurrentHashMap<>();
    public final ConcurrentMap<String, Float> resourceInputs = new ConcurrentHashMap<>();
    public final ConcurrentMap<String, Float> resourceOutputs = new ConcurrentHashMap<>();

    public volatile boolean active = true;
    public volatile boolean destroyed = false;
    public volatile float durability;//子系统耐久度
    public int tickCount = 0;
    public int physicsTickCount = 0;

    protected AbstractSubsystem(ISubsystemHost owner, String name, AbstractSubsystemAttr attr) {
        this.owner = owner;
        this.attr = attr;
        this.name = name;
        this.durability = attr.basicDurability;
        if (this instanceof ISignalSender signalSender) {
            signalSender.resetSignalOutputs();
        }
    }

    public void onTick() {
        tickCount++;
        if (!this.isDestroyed() && this.durability <= 0) {
            //摧毁耐久度归零的子系统
            this.onDestroyed();
        } else if (this.isDestroyed() && !getPart().isDestroyed() && this.durability >= 0.3 * attr.basicDurability) {
            //重新激活修复到一定程度的子系统
            this.destroyed = false;
        }
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

    public void onHurt(DamageSource source, float amount) {
        this.durability -= amount;
    }

    public void onDestroyed() {
        this.destroyed = true;
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
        if (!this.callbackTargets.isEmpty()) {
            //被清除动态设置的信号传输目标，防止信号传输到已分离部件的子系统
            //使用迭代器的remove方法
            this.callbackTargets.entrySet().removeIf(entry -> entry.getValue() instanceof AbstractSubsystem subsystem && subsystem.getPart().vehicle != this.getPart().vehicle);
            this.clearCallbackChannel();
        }
    }

    public Part getPart() {
        if (owner instanceof Part part) return part;
        else return null;
    }

    public boolean isActive() {
        return active && !this.isDestroyed() && !getPart().isDestroyed();
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
}
