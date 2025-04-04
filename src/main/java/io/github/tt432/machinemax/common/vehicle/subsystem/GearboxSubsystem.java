package io.github.tt432.machinemax.common.vehicle.subsystem;

import io.github.tt432.machinemax.common.vehicle.ISubsystemHost;
import io.github.tt432.machinemax.common.vehicle.attr.subsystem.GearboxSubsystemAttr;
import io.github.tt432.machinemax.common.vehicle.signal.*;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

@Getter
public class GearboxSubsystem extends AbstractSubsystem implements ISignalReceiver, ISignalSender {
    public final GearboxSubsystemAttr attr;
    public final double[] gearRatios;//各级实际传动比率
    private int currentGear = 1;
    @Setter
    private boolean clutch = false;//离合状态，false为正常传动，true为不传动
    private float remainingSwitchTime = 0.0f;

    public GearboxSubsystem(ISubsystemHost owner, String name, GearboxSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
        this.gearRatios = attr.ratios.stream().mapToDouble(Float::floatValue).map(r -> r * attr.finalRatio).toArray();
        switchGear(0);
    }

    @Override
    public void onPrePhysicsTick() {
        distributePower();
        if (remainingSwitchTime > 0.0f) remainingSwitchTime -= 1 / 60.0f;
    }

    @Override
    public void onPostPhysicsTick() {
        updateFeedback();//更新反馈信号
    }

    public void switchGear(int gear) {
        if (currentGear == gear) return;//当前挡位与目标挡位相同，无需切换
        if (gear >= 0 && gear < gearRatios.length) {//目标挡位有效
            this.currentGear = gear;//更新当前挡位
            if (!clutch) this.remainingSwitchTime = attr.switchTime;//若未踩离合，开始换挡时间倒计时
            //更新挡位信号
            for (Map.Entry<String, List<String>> entry : attr.gearOutputTargets.entrySet()) {
                String signalKey = entry.getKey();
                List<String> targets = entry.getValue();
                for (String targetName : targets) {
                    sendSignalToTarget(targetName, signalKey, currentGear);
                }
            }
        }
    }

    public void upShift() {
        if (currentGear < gearRatios.length - 1) {
            switchGear(currentGear + 1);
        }
    }

    public void downShift() {
        if (currentGear > 0) {
            switchGear(currentGear - 1);
        }
    }

    private void distributePower() {
        if (clutch || remainingSwitchTime > 0.0f) {
            sendSignalToTarget(attr.powerOutputTarget, "power", new EmptySignal());
            return;
        }
        double totalPower = 0.0;
        double averageSpeed = 0.0;
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
        averageSpeed /= count;//计算转速平均值
        MechPowerSignal powerSignalToSend = new MechPowerSignal((float) totalPower, (float) (averageSpeed / gearRatios[currentGear]));
        sendSignalToTarget(attr.powerOutputTarget, "power", powerSignalToSend);//发送功率信号
    }

    private void updateFeedback() {
        if (clutch|| remainingSwitchTime > 0.0f) {//空挡时或正在换挡时，不反馈速度信号
            sendCallbackToAllListeners("speed_feedback", new EmptySignal());
            return;
        }
        float speed;
        Signals speedSignal = getSignals("speed_feedback");
        if (!speedSignal.values().isEmpty()) {
            for (Object value : speedSignal.values()) {
                if (value instanceof Float f) {
                    speed = f;
                    sendCallbackToAllListeners("speed_feedback", (float) (speed * gearRatios[currentGear]));
                    return;
                }
            }
            sendCallbackToAllListeners("speed_feedback", new EmptySignal());//没有收到反馈速度信号时，发送空信号
        }
    }

    @Override
    public Map<String, List<String>> getTargetNames() {
        Map<String, List<String>> result = new HashMap<>(attr.gearOutputTargets);
        result.put("power", List.of(attr.powerOutputTarget));
        return result;
    }
}
