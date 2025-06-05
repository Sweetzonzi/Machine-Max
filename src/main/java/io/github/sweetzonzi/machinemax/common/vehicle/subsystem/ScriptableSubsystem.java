package io.github.sweetzonzi.machinemax.common.vehicle.subsystem;

import io.github.sweetzonzi.machinemax.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machinemax.common.vehicle.Part;
import io.github.sweetzonzi.machinemax.common.vehicle.attr.subsystem.AbstractSubsystemAttr;
import io.github.sweetzonzi.machinemax.common.vehicle.attr.subsystem.ScriptableSubsystemAttr;
import io.github.sweetzonzi.machinemax.common.vehicle.signal.ISignalReceiver;
import io.github.sweetzonzi.machinemax.common.vehicle.signal.ISignalSender;
import io.github.sweetzonzi.machinemax.common.vehicle.signal.SignalChannel;
import io.github.sweetzonzi.machinemax.common.vehicle.signal.SignalPort;
import io.github.sweetzonzi.machinemax.external.js.hook.Hook;

import java.util.List;
import java.util.Map;

public class ScriptableSubsystem extends AbstractSubsystem{
    public ScriptableSubsystem(ISubsystemHost owner, String name, ScriptableSubsystemAttr attr) {
        super(owner, name, attr);
        Hook.run(this, owner, name, attr);
    }



    @Override
    public void onSignalUpdated(String channelName, ISignalSender sender) {
        super.onSignalUpdated(channelName, sender);
        Hook.run(this, channelName, sender);
    }

    @Override
    public SignalChannel getSignalChannel(String channelName) {
        SignalChannel supered = super.getSignalChannel(channelName);
        if (Hook.run(this, channelName) instanceof SignalChannel hooked) {
            return hooked;
        }
        return supered;
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
        List<ISignalReceiver> supered = super.getReceiversFromNames(targetNames, ownerPart, subSystems, ports);
        if (Hook.run(this, targetNames, ownerPart, subSystems, ports) instanceof List hooked) {
            return hooked;
        }
        return supered;
    }
}
