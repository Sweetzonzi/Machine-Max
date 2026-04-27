package io.github.sweetzonzi.machine_max.common.vehicle.subsystem;

import io.github.sweetzonzi.machine_max.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.dynamic_attr.TransmissionSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.static_attr.TransmissionSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.vehicle.energy.IMechEnergyConsumer;
import io.github.sweetzonzi.machine_max.common.vehicle.energy.IMechEnergyProducer;
import io.github.sweetzonzi.machine_max.common.vehicle.energy.MechPower;
import io.github.sweetzonzi.machine_max.common.vehicle.signal.*;
import lombok.Getter;

import java.util.*;

@Getter
public class TransmissionSubsystem extends BasicSubsystem implements IMechEnergyConsumer, IMechEnergyProducer {
    public final TransmissionSubsystemAttr attr;
    private MechPower receivedPower = MechPower.ZERO;
    private float feedbackSpeed = 0;
    private final Map<String, IMechEnergyConsumer> energyTargets = new HashMap<>();

    private boolean diffLock = false;
    private float avgFeedBackSpeed = 0f;

    public TransmissionSubsystem(ISubsystemHost owner, String name, TransmissionSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
        if (attr.staticAttribute.diffLock == TransmissionSubsystemStaticAttr.diffLockMode.TRUE) diffLock = true;
    }

    @Override
    public void onPrePhysicsTick() {
        super.onPrePhysicsTick();
        //差速锁控制 Differential Lock Control
        updateDiffLock();
        //分配功率到所有已连接的功率传输目标 Distribute power to all connected power transfer targets
        distributePower();
    }

    @Override
    public void onPostPhysicsTick() {
        super.onPostPhysicsTick();
        //更新信号输出目标的平均反馈速度 Update the average feedback speed of the output targets
        updateAvgFeedbackSpeed();
        feedbackSpeed = avgFeedBackSpeed;
    }

    private void updateAvgFeedbackSpeed() {
        avgFeedBackSpeed = 0f;
        var feedbacks = collectFeedbackSpeeds();
        if (feedbacks.isEmpty()) return;
        for (float speed : feedbacks.values()) {
            avgFeedBackSpeed -= speed;
        }
        avgFeedBackSpeed /= feedbacks.size();
    }

    private void updateDiffLock() {
        if (attr.staticAttribute.diffLock == TransmissionSubsystemStaticAttr.diffLockMode.AUTO) {
            //视情况更新自动差速锁状态 Update differential lock status automatically
            diffLock = false;
            var feedbacks = collectFeedbackSpeeds();
            if (feedbacks.size() < 2 || avgFeedBackSpeed == 0) return;
            for (float speed : feedbacks.values()) {
                if (Math.abs((avgFeedBackSpeed + speed) / avgFeedBackSpeed) > attr.staticAttribute.autoDiffLockThreshold / 100f) {
                    diffLock = true;
                    break;
                }
            }
        } else if (attr.staticAttribute.diffLock == TransmissionSubsystemStaticAttr.diffLockMode.MANUAL) {
            //根据信号进行的手动差速锁控制 Manually control the differential lock according to the input signals
            Boolean targetDiffLockStatus = null;
            if (!attr.staticAttribute.manualDiffLockInputChannels.isEmpty()) {
                for (String channelName : attr.staticAttribute.manualDiffLockInputChannels) {
                    SignalChannel channel = getSignalChannel(channelName);
                    for (Object value : channel.values()) {
                        if (value instanceof Boolean b) {
                            targetDiffLockStatus = b;
                            break;
                        }
                    }
                    if (targetDiffLockStatus != null) break;
                }
            }
            if (targetDiffLockStatus != null) diffLock = targetDiffLockStatus;
        }
    }

    @Override
    public void onMechEnergyReceived(String producerName, MechPower power) {
        this.receivedPower = power;
    }

    @Override
    public float getFeedbackSpeed() {
        return feedbackSpeed;
    }

    @Override
    public void setFeedbackSpeed(float speed) {
        this.feedbackSpeed = speed;
    }

    @Override
    public Map<String, IMechEnergyConsumer> getEnergyTargets() {
        return energyTargets;
    }

    @Override
    public void rebuildEnergyTargets() {
        energyTargets.clear();
        for (String targetName : attr.getPowerOutputs().keySet()) {
            IMechEnergyConsumer consumer = resolveEnergyTarget(targetName);
            if (consumer != null) {
                energyTargets.put(targetName, consumer);
            }
        }
    }

    private IMechEnergyConsumer resolveEnergyTarget(String targetName) {
        if (getSubPart().subsystems.containsKey(targetName)) {
            var sub = getSubPart().subsystems.get(targetName);
            if (sub instanceof IMechEnergyConsumer consumer) return consumer;
        }
        if (getSubPart().connectors.containsKey(targetName)) {
            AbstractConnector conn = getSubPart().connectors.get(targetName);
            if (conn.mechanicalEnergyPort != null) return conn.mechanicalEnergyPort;
        }
        return null;
    }

    private void distributePower() {
        var feedbacks = collectFeedbackSpeeds();
        if (feedbacks.isEmpty() || !isActive()) {
            pushMechEnergy(MechPower.EMPTY);
            return; //无输出目标则不发出功率信号
        }

        float totalPower = receivedPower.power();
        float inputSpeed = receivedPower.speed();
        if (Float.isNaN(totalPower) || Float.isNaN(inputSpeed)) {
            pushMechEnergy(MechPower.EMPTY);
            return;
        }
//        totalPower *= Math.signum(inputSpeed);//根据速度方向调整总功率正负

        if (diffLock) {//差速锁模式，限制输出端转速相等
            distributeDiffLock(totalPower, inputSpeed, feedbacks);
        } else {//差速器模式，不限制输出端转速，各输出端扭矩相等
            distributeOpenDiff(totalPower, inputSpeed, feedbacks);
        }
    }

    private void distributeDiffLock(float totalPower, float inputSpeed, Map<String, Float> feedbacks) {
        float totalWeight = 0f;
        Map<String, Float> weights = new HashMap<>();
        for (var entry : feedbacks.entrySet()) {
            String name = entry.getKey();
            float speed = -entry.getValue();
            float weight;
            if (Math.signum(speed * inputSpeed) >= 0) {
                weight = 1f;
            } else {
                weight = -3f;
            }
            weight /= (float) Math.pow(Math.max(1f, Math.abs(speed) + 1f), attr.staticAttribute.diffLockSensitivity);
            weights.put(name, weight);
            totalWeight += Math.abs(weight);
        }
        if (totalWeight == 0) {
            pushMechEnergy(MechPower.EMPTY);
            return;
        }
        Map<String, MechPower> outputs = new HashMap<>();
        for (var entry : feedbacks.entrySet()) {
            String name = entry.getKey();
            Float gearRatio = attr.getPowerOutputs().get(name);
            if (gearRatio == null || gearRatio == 0f) continue;
            float weight = weights.get(name);
            float power = totalPower * weight / totalWeight;
            float speed = -0.995f * entry.getValue() + 0.005f * inputSpeed;
            if (speed == 0f) speed = 0.005f * inputSpeed;
            outputs.put(name, new MechPower(power, speed / gearRatio));
        }
        pushMechEnergy(outputs);
    }

    private void distributeOpenDiff(float totalPower, float inputSpeed, Map<String, Float> feedbacks) {
        if (totalPower == 0f) {
            pushMechEnergy(MechPower.EMPTY);
            return;
        }
        float receiverTotalSpeed = 0.0F;
        for (float speed : feedbacks.values()) {
            receiverTotalSpeed -= speed;
        }
        receiverTotalSpeed += 0.005f * inputSpeed;
        if (receiverTotalSpeed == 0f) receiverTotalSpeed = 0.005f * inputSpeed;
        float torque = totalPower / receiverTotalSpeed;
        Map<String, MechPower> outputs = new HashMap<>();
        for (var entry : feedbacks.entrySet()) {
            String name = entry.getKey();
            Float gearRatio = attr.getPowerOutputs().get(name);
            if (gearRatio == null || gearRatio == 0f) continue;
            float receiverSpeed = -0.99f * entry.getValue() + 0.01f * inputSpeed;
            if (receiverSpeed == 0f) receiverSpeed = 0.01f * inputSpeed;
            float power = torque * receiverSpeed;
            outputs.put(name, new MechPower(power, receiverSpeed / gearRatio));
        }
        pushMechEnergy(outputs);
    }

    @Override
    public Map<String, List<String>> getTargetNames() {
        return Map.of();
    }
}
