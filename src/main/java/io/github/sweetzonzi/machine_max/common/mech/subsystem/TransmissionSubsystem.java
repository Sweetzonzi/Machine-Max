package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.common.mech.energy.IMechPowerConsumer;
import io.github.sweetzonzi.machine_max.common.mech.energy.IMechPowerProducer;
import io.github.sweetzonzi.machine_max.common.mech.energy.MechPower;
import io.github.sweetzonzi.machine_max.common.mech.signal.*;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.TransmissionSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.TransmissionSubsystemStaticAttr;
import lombok.Getter;

import java.util.*;

@Getter
public class TransmissionSubsystem extends BasicSubsystem implements IMechPowerConsumer, IMechPowerProducer {
    public final TransmissionSubsystemAttr attr;
    private MechPower receivedPower = MechPower.ZERO;
    private float feedbackSpeed = 0;
    private final Map<String, IMechPowerConsumer> energyTargets = new HashMap<>();

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
            avgFeedBackSpeed += speed;
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
    public void onMechPowerReceived(String producerName, MechPower power) {
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
    public Map<String, IMechPowerConsumer> getMechPowerTargets() {
        return energyTargets;
    }

    @Override
    public void rebuildMechPowerTargets() {
        energyTargets.clear();
        for (String targetName : attr.getPowerOutputs().keySet()) {
            IMechPowerConsumer consumer = resolveEnergyTarget(targetName);
            if (consumer != null) {
                energyTargets.put(targetName, consumer);
            }
        }
    }

    private IMechPowerConsumer resolveEnergyTarget(String targetName) {
        if (getOwner().getSubsystems().containsKey(targetName)) {
            var sub = getOwner().getSubsystems().get(targetName);
            if (sub instanceof IMechPowerConsumer consumer) return consumer;
        }
        if (getSubPart().connectors.containsKey(targetName)) {
            return getSubPart().connectors.get(targetName).mechanicalEnergyPort;
        }
        return null;
    }

    private void distributePower() {
        var feedbacks = collectFeedbackSpeeds();
        if (feedbacks.isEmpty() || !isActive()) {
            pushMechPower(MechPower.EMPTY);
            return; //无输出目标则不发出功率信号
        }

        float totalPower = receivedPower.power();
        float inputSpeed = receivedPower.speed();
        if (Float.isNaN(totalPower) || Float.isNaN(inputSpeed)) {
            pushMechPower(MechPower.EMPTY);
            return;
        }

        if (diffLock) {//差速锁模式，限制输出端转速相等
            distributeDiffLock(totalPower, inputSpeed, feedbacks);
        } else {//差速器模式，不限制输出端转速，各输出端扭矩相等
            distributeOpenDiff(totalPower, inputSpeed, feedbacks);
        }
    }

    /**
     * 差速锁保证输出端转速相同，按转速差异分配功率
     * @param totalPower 输入功率
     * @param inputSpeed 输入转速
     * @param feedbacks 输出端反馈转速
     */
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
            pushMechPower(MechPower.EMPTY);
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
        pushMechPower(outputs);
    }

    /**
     * 差速器保证输出端扭矩相同，总功率和等于输入功率
     * @param totalPower 输入功率
     * @param inputSpeed 输入转速
     * @param feedbacks 输出端反馈转速
     */
    private void distributeOpenDiff(float totalPower, float inputSpeed, Map<String, Float> feedbacks) {
        if (totalPower == 0f) {
            pushMechPower(MechPower.EMPTY);
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
        pushMechPower(outputs);
    }

    /**
     * 收集所有输出端反馈转速，应用输出端减速比，使其能够直接与输入端比较
     * @return 输出端反馈转速
     */
    @Override
    public Map<String, Float> collectFeedbackSpeeds() {
        Map<String, Float> result = new HashMap<>();
        for (var entry : getMechPowerTargets().entrySet()) {
            var consumer = entry.getValue();
            if (consumer != null && consumer.isPowerPathConnected(getName())) {
                result.put(entry.getKey(), consumer.getFeedbackSpeed() * attr.getPowerOutputs().get(entry.getKey()));
            }
        }
        return result;
    }

    @Override
    public List<String> getAcceptedChannels() {
        return attr.staticAttribute.getManualDiffLockInputChannels();
    }

    @Override
    public Map<String, List<String>> getTargetNames() {
        return Map.of();
    }
}
