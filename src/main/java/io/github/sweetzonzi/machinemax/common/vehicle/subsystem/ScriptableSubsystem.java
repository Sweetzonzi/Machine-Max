package io.github.sweetzonzi.machinemax.common.vehicle.subsystem;

import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machinemax.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machinemax.common.vehicle.Part;
import io.github.sweetzonzi.machinemax.common.vehicle.attr.subsystem.AbstractSubsystemAttr;
import io.github.sweetzonzi.machinemax.common.vehicle.attr.subsystem.ScriptableSubsystemAttr;
import io.github.sweetzonzi.machinemax.common.vehicle.signal.ISignalReceiver;
import io.github.sweetzonzi.machinemax.common.vehicle.signal.ISignalSender;
import io.github.sweetzonzi.machinemax.common.vehicle.signal.SignalChannel;
import io.github.sweetzonzi.machinemax.common.vehicle.signal.SignalPort;
import io.github.sweetzonzi.machinemax.external.js.hook.Hook;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

public class ScriptableSubsystem extends AbstractSubsystem{
    public ScriptableSubsystem(ISubsystemHost owner, String name, ScriptableSubsystemAttr attr) {
        super(owner, name, attr);
    }

    @Override
    public String getName() {
        if (Hook.run(this) instanceof String _name) {
            return _name;
        }
        return super.getName();
    }

    @Override
    public AbstractSubsystemAttr getSubSystemAttr() {
        if (Hook.run(this) instanceof AbstractSubsystemAttr attr) {
            return attr;
        }
        return super.getSubSystemAttr();
    }

    @Override
    public ISubsystemHost getOwner() {
        if (Hook.run(this) instanceof ISubsystemHost owner) {
            return owner;
        }
        return super.getOwner();
    }

    @Override
    public Map<String, Map<String, ISignalReceiver>> getTargets() {
        if (Hook.run(this) instanceof Map targets) {
            return targets;
        }
        return super.getTargets();
    }

    @Override
    public Map<String, Set<ISignalReceiver>> getCallbackTargets() {
        if (Hook.run(this) instanceof Map targets) {
            return targets;
        }
        return super.getCallbackTargets();
    }

    @Override
    public ConcurrentMap<String, SignalChannel> getSignalInputChannels() {
        if (Hook.run(this) instanceof ConcurrentMap signalInputChannels) {
            return signalInputChannels;
        }
        return super.getSignalInputChannels();
    }

    @Override
    public ConcurrentMap<String, Float> getResourceInputs() {
        if (Hook.run(this) instanceof ConcurrentMap resourceInputs) {
            return resourceInputs;
        }
        return super.getResourceInputs();
    }

    @Override
    public ConcurrentMap<String, Float> getResourceOutputs() {
        if (Hook.run(this) instanceof ConcurrentMap resourceOutputs) {
            return resourceOutputs;
        }
        return super.getResourceOutputs();
    }

    @Override
    public boolean isActive() {
        if (Hook.run(this) instanceof Boolean active) {
            return active;
        }
        return super.isActive();
    }

    @Override
    public float getDurability() {
        if (Hook.run(this) instanceof Float durability) {
            return durability;
        }
        return super.getDurability();
    }

    @Override
    public int getTickCount() {
        if (Hook.run(this) instanceof Integer tickCount) {
            return tickCount;
        }
        return super.getTickCount();
    }

    @Override
    public int getPhysicsTickCount() {
        if (Hook.run(this) instanceof Integer physicsTickCount) {
            return physicsTickCount;
        }
        return super.getPhysicsTickCount();
    }

    @Override
    public void onTick() {
        Hook.run(this);
        super.onTick();
    }

    @Override
    public void onPrePhysicsTick() {
        Hook.run(this);
        super.onPrePhysicsTick();
    }

    @Override
    public void onPostPhysicsTick() {
        Hook.run(this);
        super.onPostPhysicsTick();
    }

    @Override
    public void onCollideWithBlock(PhysicsRigidBody subPartBody, PhysicsRigidBody blockBody, BlockPos blockPos, BlockState blockState, Vector3f relativeVelocity, Vector3f normal, Vector3f contactPoint, float impartAngle, long hitChildShapeNativeId, long manifoldPointId) {
        Hook.run(this, subPartBody, blockBody, blockPos, blockState, relativeVelocity, normal, contactPoint, impartAngle, hitChildShapeNativeId, manifoldPointId);
        super.onCollideWithBlock(subPartBody, blockBody, blockPos, blockState, relativeVelocity, normal, contactPoint, impartAngle, hitChildShapeNativeId, manifoldPointId);
    }

    @Override
    public void onCollideWithPart(PhysicsRigidBody subPartBody, PhysicsRigidBody otherSubPartBody, Vector3f relativeVelocity, Vector3f normal, Vector3f contactPoint, float impartAngle, long childShapeNativeId, long otherChildShapeNativeId, long manifoldPointId) {
        Hook.run(this, subPartBody, otherSubPartBody, relativeVelocity, normal, contactPoint, impartAngle, childShapeNativeId, otherChildShapeNativeId, manifoldPointId);
        super.onCollideWithPart(subPartBody, otherSubPartBody, relativeVelocity, normal, contactPoint, impartAngle, childShapeNativeId, otherChildShapeNativeId, manifoldPointId);
    }

    @Override
    public void onCollideWithEntity(PhysicsRigidBody subPartBody, PhysicsRigidBody entityBody, Vector3f relativeVelocity, Vector3f normal, Vector3f contactPoint, float impartAngle, long hitChildShapeNativeId, long manifoldPointId) {
        Hook.run(this, subPartBody, entityBody, relativeVelocity, normal, contactPoint, impartAngle, hitChildShapeNativeId, manifoldPointId);
        super.onCollideWithEntity(subPartBody, entityBody, relativeVelocity, normal, contactPoint, impartAngle, hitChildShapeNativeId, manifoldPointId);
    }

    @Override
    public void onAttach() {
        Hook.run(this);
        super.onAttach();
    }

    @Override
    public void onDetach() {
        Hook.run(this);
        super.onDetach();
    }

    @Override
    public void onDisabled() {
        Hook.run(this);
        super.onDisabled();
    }

    @Override
    public void onHurt(DamageSource source, float amount) {
        Hook.run(this, source, amount);
        super.onHurt(source, amount);
    }

    @Override
    public void onInteract(LivingEntity entity) {
        Hook.run(this, entity);
        super.onInteract(entity);
    }

    @Override
    public void onVehicleStructureChanged() {
        Hook.run(this);
        super.onVehicleStructureChanged();
    }

    @Override
    public Part getPart() {
        if (Hook.run(this) instanceof Part part) {
            return part;
        }
        return super.getPart();
    }

    @Override
    public void onSignalUpdated(String channelName, ISignalSender sender) {
        super.onSignalUpdated(channelName, sender);
        Hook.run(this, channelName, sender);
    }

    @Override
    public SignalChannel getSignalChannel(String channelName) {
        if (Hook.run(this, channelName) instanceof SignalChannel hooked) {
            return hooked;
        }
        return super.getSignalChannel(channelName);
    }

    @Override
    public void clearCallbackChannel() {
        super.clearCallbackChannel();
        Hook.run(this);
    }

    @Override
    public Map<String, List<String>> getTargetNames() {
        if (Hook.run(this) instanceof Map map) {
            return map;
        }
        return Map.of();
    }

    @Override
    public void addCallbackTarget(String signalChannel, ISignalReceiver target) {
        super.addCallbackTarget(signalChannel, target);
        Hook.run(this, signalChannel, target);
    }

    @Override
    public void removeCallbackTarget(String signalChannel, ISignalReceiver target) {
        super.removeCallbackTarget(signalChannel, target);
        Hook.run(this, signalChannel, target);
    }

    @Override
    public void clearCallbackTargets() {
        super.clearCallbackTargets();
        Hook.run(this);
    }

    @Override
    public void resetSignalOutputs() {
        super.resetSignalOutputs();
        Hook.run(this);
    }

    @Override
    public void sendSignalToAllTargets(String signalChannel, Object signalValue) {
        super.sendSignalToAllTargets(signalChannel, signalValue);
        Hook.run(this, signalChannel, signalValue);
    }

    @Override
    public void sendSignalToAllTargetsWithCallback(String signalChannel, Object signalValue, boolean callbackReturnsSignalValue) {
        super.sendSignalToAllTargetsWithCallback(signalChannel, signalValue, callbackReturnsSignalValue);
        Hook.run(this, signalChannel, signalValue, callbackReturnsSignalValue);
    }

    @Override
    public void sendSignalToAllTargets(String signalChannel, Object signalValue, boolean requiresImmediateCallback, boolean callbackReturnsSignalValue) {
        super.sendSignalToAllTargets(signalChannel, signalValue, requiresImmediateCallback, callbackReturnsSignalValue);
        Hook.run(this, signalChannel, signalValue, requiresImmediateCallback, callbackReturnsSignalValue);
    }

    @Override
    public void sendSignalToTarget(String signalChannel, String targetName, Object signalValue) {
        super.sendSignalToTarget(signalChannel, targetName, signalValue);
        Hook.run(this, signalChannel, targetName, signalValue);
    }

    @Override
    public void sendSignalToTargetWithCallback(String signalChannel, String targetName, Object signalValue, boolean callbackReturnsSignalValue) {
        super.sendSignalToTargetWithCallback(signalChannel, targetName, signalValue, callbackReturnsSignalValue);
        Hook.run(this, signalChannel, targetName, signalValue, callbackReturnsSignalValue);
    }

    @Override
    public void sendSignalToTarget(String signalChannel, String targetName, Object signalValue, boolean requiresImmediateCallback, boolean callbackReturnsSignalValue) {
        super.sendSignalToTarget(signalChannel, targetName, signalValue, requiresImmediateCallback, callbackReturnsSignalValue);
        Hook.run(this, signalChannel, targetName, signalValue, requiresImmediateCallback, callbackReturnsSignalValue);
    }

    @Override
    public void sendSignalToTarget(String signalChannel, ISignalReceiver target, Object signalValue) {
        super.sendSignalToTarget(signalChannel, target, signalValue);
        Hook.run(this, signalChannel, target, signalValue);
    }

    @Override
    public void sendSignalToTargetWithCallback(String signalChannel, ISignalReceiver target, Object signalValue, boolean callbackReturnsSignalValue) {
        super.sendSignalToTargetWithCallback(signalChannel, target, signalValue, callbackReturnsSignalValue);
        Hook.run(this, signalChannel, target, signalValue, callbackReturnsSignalValue);
    }

    @Override
    public void sendCallbackToAllListeners(String signalChannel, Object signalValue) {
        super.sendCallbackToAllListeners(signalChannel, signalValue);
        Hook.run(this, signalChannel, signalValue);
    }

    @Override
    public void sendCallbackToListener(String signalChannel, ISignalReceiver receiver, Object signalValue) {
        super.sendCallbackToListener(signalChannel, receiver, signalValue);
        Hook.run(this, signalChannel, receiver, signalValue);
    }

    @Override
    public void setTargetFromNames() {
        super.setTargetFromNames();
        Hook.run(this);
    }

    @Override
    public List<ISignalReceiver> getReceiversFromNames(List<String> targetNames, Part ownerPart, Map<String, AbstractSubsystem> subSystems, Map<String, SignalPort> ports) {
        if (Hook.run(this, targetNames, ownerPart, subSystems, ports) instanceof List hooked) {
            return hooked;
        }
        return super.getReceiversFromNames(targetNames, ownerPart, subSystems, ports);
    }
}
