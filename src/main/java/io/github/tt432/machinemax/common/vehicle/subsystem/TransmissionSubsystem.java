package io.github.tt432.machinemax.common.vehicle.subsystem;

import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.vehicle.ISubsystemHost;
import io.github.tt432.machinemax.common.vehicle.attr.subsystem.TransmissionSubsystemAttr;
import io.github.tt432.machinemax.common.vehicle.signal.ISignalReceiver;
import io.github.tt432.machinemax.common.vehicle.signal.ISignalSender;
import io.github.tt432.machinemax.common.vehicle.signal.MechPowerSignal;
import io.github.tt432.machinemax.common.vehicle.signal.Signals;

import java.util.*;

public class TransmissionSubsystem extends AbstractSubsystem implements ISignalReceiver, ISignalSender {
    public final TransmissionSubsystemAttr attr;
    private final float TOTAL_POWER_WEIGHT;

    public TransmissionSubsystem(ISubsystemHost owner, String name, TransmissionSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
        float totalPowerWeight = 0.0f;
        for (Map.Entry<String, Float> entry : attr.powerOutputs.entrySet()) totalPowerWeight += entry.getValue();
        TOTAL_POWER_WEIGHT = totalPowerWeight;
    }

    @Override
    public void onPrePhysicsTick() {
        distributePower();
    }

    @Override
    public void onPostPhysicsTick() {
        updateFeedback();
    }

    @Override
    public void onVehicleStructureChanged() {
        super.onVehicleStructureChanged();
        clearCallbackSignals();
    }

    private void distributePower() {
        double totalPower = 0.0;
        float averageSpeed = 0.0F;
        int count = 0;
        Signals powerSignal = getSignals("power");
        for (Map.Entry<ISignalSender, Object> entry : powerSignal.entrySet()) {
            if (entry.getValue() instanceof MechPowerSignal power) {
                totalPower += power.getPower();//计算收到的总功率
                averageSpeed += power.getSpeed();
                count++;
                ISignalSender sender = entry.getKey();
                if (sender instanceof ISignalReceiver receiver) {//当发送者同时也是接收者时，自动反馈速度到发送者
                    addCallbackTarget("speed_feedback", receiver);
                }
            }
        }
        if(count > 0) averageSpeed /= count;//计算转速平均值
        else return;
        for (Map.Entry<String, Float> target : attr.powerOutputs.entrySet()) {
            String targetName = target.getKey();
            float weight = target.getValue();
            float power = (float) (totalPower * weight / TOTAL_POWER_WEIGHT);
            MechPowerSignal powerSignalToSend = new MechPowerSignal(power, averageSpeed);
            sendSignalToTarget(targetName, "power", powerSignalToSend);//发送功率信号
        }
    }

    private void updateFeedback() {
        float speed = 0;
        int count = 0;
        Signals speedSignal = getSignals("speed_feedback");
        if (!speedSignal.values().isEmpty()) {
            for (Object value : speedSignal.values()) {
                if (value instanceof Float f) {
                    speed += f;
                    count++;
                }
            }
        }
        if (count > 0) speed /= count;
        sendCallbackToAllListeners("speed_feedback", speed);
    }

    @Override
    public Map<String, List<String>> getTargetNames() {
        Map<String, List<String>> targetNames = new HashMap<>(4);
        //添加功率输出目标
        for (Map.Entry<String, Float> entry : attr.powerOutputs.entrySet()) {
            String targetName = entry.getKey();
            String signalName = "power";
            targetNames.computeIfAbsent(signalName, k -> new ArrayList<>()).add(targetName);
        }
        return targetNames;
    }
}
