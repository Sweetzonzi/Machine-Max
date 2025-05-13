package io.github.tt432.machinemax.common.vehicle.subsystem;

import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;
import io.github.tt432.machinemax.common.vehicle.ISubsystemHost;
import io.github.tt432.machinemax.common.vehicle.Part;
import io.github.tt432.machinemax.common.vehicle.signal.ISignalReceiver;
import io.github.tt432.machinemax.common.vehicle.signal.ISignalSender;
import io.github.tt432.machinemax.common.vehicle.attr.subsystem.AbstractSubsystemAttr;
import io.github.tt432.machinemax.common.vehicle.signal.Signals;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Getter
abstract public class AbstractSubsystem {

    public final String name;
    public final AbstractSubsystemAttr subSystemAttr;
    public final ISubsystemHost owner;

    public final Map<String, Map<String, ISignalReceiver>> targets = new HashMap<>();//信号名->接收者名称->接收者
    public final Map<String, Set<ISignalReceiver>> callbackTargets = new HashMap<>();//信号名->回调接收者
    public final ConcurrentMap<String, Signals> signalInputs = new ConcurrentHashMap<>();
    public final ConcurrentMap<String, Float> resourceInputs = new ConcurrentHashMap<>();
    public final ConcurrentMap<String, Float> resourceOutputs = new ConcurrentHashMap<>();

    public volatile boolean active = true;
    public float durability;//子系统耐久度
    public int tickCount = 0;
    public int physicsTickCount = 0;

    protected AbstractSubsystem(ISubsystemHost owner, String name, AbstractSubsystemAttr attr) {
        this.owner = owner;
        this.subSystemAttr = attr;
        this.name = name;
        this.durability = attr.basicDurability;
        if (this instanceof ISignalSender signalSender) {
            signalSender.resetSignalOutputs();
        }
    }

    public void onTick() {
        tickCount++;
    }

    public void onPrePhysicsTick() {

    }

    public void onPostPhysicsTick() {
        physicsTickCount++;
    }

    /**
     * 子系统对应的碰撞箱与另一个刚体发生碰撞时的原始碰撞信息调用。调用于物理线程。<p>
     * Called when the hit-box of the subsystem collides with another rigid body. Called on the physics thread.<p>
     * 推荐使用 {@link AbstractSubsystem#onCollideWithBlock},{@link AbstractSubsystem#onCollideWithPart},{@link AbstractSubsystem#onCollideWithEntity}方法替代。<p>
     * It is recommended to use the {@link AbstractSubsystem#onCollideWithBlock},{@link AbstractSubsystem#onCollideWithPart},{@link AbstractSubsystem#onCollideWithEntity} methods instead.
     */
    @Deprecated//需要手动重新获取各类碰撞信息，不推荐使用
    public void onCollide(
            PhysicsCollisionObject pcoA,
            PhysicsCollisionObject pcoB,
            long manifoldPointId
    ) {
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
            long hitChildShapeNativeId,
            long manifoldPointId) {
    }

    public void onCollideWithPart(
            PhysicsRigidBody subPartBody,
            PhysicsRigidBody otherSubPartBody,
            Vector3f relativeVelocity,
            Vector3f normal,
            Vector3f contactPoint,
            float impartAngle,
            long childShapeNativeId,
            long otherChildShapeNativeId,
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
            long hitChildShapeNativeId,
            long manifoldPointId
    ) {
    }

    public void onAttach() {
    }

    public void onDetach() {
    }

    public void onDisabled() {
    }

    public void onHurt(DamageSource source, float amount) {
    }

    public void onVehicleStructureChanged() {
        if (!this.callbackTargets.isEmpty()) {
            //被清除动态设置的信号传输目标，防止信号传输到已分离部件的子系统
            //使用迭代器的remove方法
            this.callbackTargets.entrySet().removeIf(entry -> entry.getValue() instanceof AbstractSubsystem subsystem && subsystem.getPart().vehicle != this.getPart().vehicle);
            if (this instanceof ISignalSender && this instanceof ISignalReceiver callbackListener)
                callbackListener.clearCallbackSignals();
        }
    }

    public Part getPart() {
        if (owner instanceof Part part) return part;
        else return null;
    }
}
