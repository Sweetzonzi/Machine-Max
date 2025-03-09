package io.github.tt432.machinemax.common.vehicle.subsystem;

import com.mojang.datafixers.util.Pair;
import io.github.tt432.machinemax.common.vehicle.ISubsystemHost;
import io.github.tt432.machinemax.common.vehicle.attr.subsystem.TransmissionSubsystemAttr;
import io.github.tt432.machinemax.common.vehicle.signal.ISignalReceiver;
import io.github.tt432.machinemax.common.vehicle.signal.ISignalSender;
import io.github.tt432.machinemax.common.vehicle.signal.Signals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TransmissionSubsystem extends AbstractSubsystem implements ISignalReceiver, ISignalSender {
    public final TransmissionSubsystemAttr attr;
    private final float TOTAL_POWER_WEIGHT;

    public TransmissionSubsystem(ISubsystemHost owner, String name, TransmissionSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
        float totalPowerWeight = 0.0f;
        for (Pair<String, Float> target : attr.rotationOutputs.values()) totalPowerWeight += target.getSecond();
        TOTAL_POWER_WEIGHT = totalPowerWeight;
    }

    @Override
    public void onTick() {
        distributePower();
        updateSpeedFeedback();
    }

    private void distributePower() {
        double totalPower = 0.0;
        Signals powerSignal = getSignals("power");
        for (Object value : powerSignal.get().values()) {
            if (value instanceof Double power) totalPower += power;//计算收到的总功率
        }
        for (Map.Entry<String, Pair<String, Float>> target : attr.rotationOutputs.entrySet()) {
            String targetName = target.getKey();
            String signalName = target.getValue().getFirst();
            float weight = target.getValue().getSecond();
            float power = (float) (totalPower * weight / TOTAL_POWER_WEIGHT);
            sendSignalToTarget(targetName, signalName, power);//发送功率
        }
    }

    private void updateSpeedFeedback() {
        float speed = 0;
        int count = 0;
        for (String signalName : attr.speedFeedbackInputKeys) {
            Signals speedSignal = getSignals(signalName);
            if (!speedSignal.get().values().isEmpty()) {
                for (Object value : speedSignal.get().values()) {
                    if (value instanceof Float f) {
                        speed += Math.abs(f);
                        count++;
                    }
                }
            }
        }
        if (count > 0) speed /= count;
        for (String signalName : attr.speedFeedbackOutputTargets.keySet()) {
            sendSignalToAllTargets(signalName, speed);
        }
    }

    @Override
    public Map<String, List<String>> getTargetNames() {
        Map<String, List<String>> targetNames = new HashMap<>(4);
        //添加旋转输出目标
        for (Map.Entry<String, Pair<String, Float>> entry : attr.rotationOutputs.entrySet()) {
            String targetName = entry.getKey();
            String signalName = entry.getValue().getFirst();
            targetNames.computeIfAbsent(signalName, k -> new ArrayList<>()).add(targetName);
        }
        //添加转速反馈输出目标
        targetNames.putAll(attr.speedFeedbackOutputTargets);
        return targetNames;
    }
}
