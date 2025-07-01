package io.github.sweetzonzi.machinemax.common.vehicle.subsystem;

import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machinemax.common.vehicle.HitBox;
import io.github.sweetzonzi.machinemax.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machinemax.common.vehicle.Part;
import io.github.sweetzonzi.machinemax.common.vehicle.SignalTargetsHolder;
import io.github.sweetzonzi.machinemax.common.vehicle.attr.subsystem.AbstractSubsystemAttr;
import io.github.sweetzonzi.machinemax.common.vehicle.attr.subsystem.ScriptableSubsystemAttr;
import io.github.sweetzonzi.machinemax.common.vehicle.signal.ISignalReceiver;
import io.github.sweetzonzi.machinemax.common.vehicle.signal.ISignalSender;
import io.github.sweetzonzi.machinemax.common.vehicle.signal.SignalChannel;
import io.github.sweetzonzi.machinemax.common.vehicle.signal.SignalPort;
import io.github.sweetzonzi.machinemax.external.js.hook.Hook;
import io.github.sweetzonzi.machinemax.network.payload.ScriptablePayload;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;
import java.util.concurrent.ConcurrentMap;


public class ScriptableSubsystem extends AbstractSubsystem implements IControllableSubsystem {
    public final ScriptableSubsystemAttr attr;
    public final String script;
    @Getter
    private UUID vehicleCoreUUID = null;
    private final SignalTargetsHolder signalTargetsHolder = new SignalTargetsHolder(this);
    public ScriptableSubsystem(ISubsystemHost owner, String name, ScriptableSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
        this.script = attr.script;
    }

    @Override
    public SignalTargetsHolder getHolder() {
        return signalTargetsHolder;
    }

    @Override
    public Map<String, List<String>> getTargetNames() {
        return signalTargetsHolder.setUpTargets(new HashMap<>(1));
    }


    public void sendNbt(String to, CompoundTag nbt){
        if (vehicleCoreUUID != null) {
            PacketDistributor.sendToServer(new ScriptablePayload(vehicleCoreUUID, script, to, nbt));
        }
        //考虑以后判断是哪个端，让服务器上的ScriptableSubsystem也有发回的能力
        //PacketDistributor.sendToPlayersInDimension((ServerLevel) player.level(), payload)
    }

    public interface FetchedScriptableSubsystem {
        void doAction(ScriptableSubsystem scriptableSubsystem);
    }
    public void doActionOnScriptable(String scriptName, FetchedScriptableSubsystem action) {
        for (AbstractSubsystem subsystem : getPart().getVehicle().getSubSystemController().getAllSubsystems()) {
            if (subsystem instanceof ScriptableSubsystem sc && sc.script.equals(scriptName)) action.doAction(sc);
        }
    }

    @Override
    public String getName() {
        if (Hook.run(this) instanceof String _name) {
            return _name;
        }
        return super.getName();
    }

    @Override
    public AbstractSubsystemAttr getAttr() {
        if (Hook.run(this) instanceof AbstractSubsystemAttr attr) {
            return attr;
        }
        return super.getAttr();
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
    public void onCollideWithBlock(PhysicsRigidBody subPartBody, PhysicsRigidBody blockBody, BlockPos blockPos, BlockState blockState, Vector3f relativeVelocity, Vector3f normal, Vector3f contactPoint, float impartAngle, HitBox hitBox, long manifoldPointId) {
        Hook.run(this, subPartBody, blockBody, blockPos, blockState, relativeVelocity, normal, contactPoint, impartAngle, hitBox, manifoldPointId);
        super.onCollideWithBlock(subPartBody, blockBody, blockPos, blockState, relativeVelocity, normal, contactPoint, impartAngle, hitBox, manifoldPointId);
    }

    @Override
    public void onCollideWithEntity(PhysicsRigidBody subPartBody, PhysicsRigidBody entityBody, Vector3f relativeVelocity, Vector3f normal, Vector3f contactPoint, float impartAngle, HitBox hitBox, long manifoldPointId) {
        Hook.run(this, subPartBody, entityBody, relativeVelocity, normal, contactPoint, impartAngle, hitBox, manifoldPointId);
        super.onCollideWithEntity(subPartBody, entityBody, relativeVelocity, normal, contactPoint, impartAngle, hitBox, manifoldPointId);
    }

    @Override
    public void onCollideWithPart(PhysicsRigidBody subPartBody, PhysicsRigidBody otherSubPartBody, Vector3f relativeVelocity, Vector3f normal, Vector3f contactPoint, float impartAngle, HitBox hitBox, HitBox otherHitBox, long manifoldPointId) {
        Hook.run(this, subPartBody, otherSubPartBody, relativeVelocity, normal, contactPoint, impartAngle, hitBox, otherHitBox, manifoldPointId);
        super.onCollideWithPart(subPartBody, otherSubPartBody, relativeVelocity, normal, contactPoint, impartAngle, hitBox, otherHitBox, manifoldPointId);
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
    public void onVehicleStructureChanged() {
        Hook.run(this);
        super.onVehicleStructureChanged();
        vehicleCoreUUID = getPart().getVehicle().getUuid();
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
