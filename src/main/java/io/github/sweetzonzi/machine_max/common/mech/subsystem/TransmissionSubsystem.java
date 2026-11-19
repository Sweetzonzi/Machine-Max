package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.MachineMax;
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

    /** 输出端超过目标轴速的同步死区(rad/s)。 */
    private static final float LOCK_SLIP_DEADBAND = 2f;
    /**
     * 输入轴转速的数值下限(rad/s)：低于该值时功率链视为没有确定的扭矩，直接下发空功率。
     */
    private static final float MIN_INPUT_SPEED = 1e-6f;

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
                //相对偏差 = |本端反馈转速 - 平均反馈转速| / |平均反馈转速|
                if (Math.abs(speed - avgFeedBackSpeed)
                        > Math.abs(avgFeedBackSpeed) * attr.staticAttribute.autoDiffLockThreshold / 100f) {
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
        var sub = getOwner().findOwnSubsystem(targetName);
        if (sub instanceof IMechPowerConsumer consumer) return consumer;
        if (getSubPart().connectors.containsKey(targetName)) {
            return getSubPart().connectors.get(targetName).mechanicalEnergyPort;
        }
        return null;
    }

    private void distributePower() {
        var feedbacks = collectRawFeedbackSpeeds();
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
     * 差速锁按实测轴速差将正驱动预算从快端转移到慢端，各输出功率份额之和等于输入预算。
     * 负功率按端数均分，交给末端反拖制动。目标轴速按减速比折算；
     * 快端按限滑强度向这个轴速回拖，牵引补偿由末端执行器处理。
     */
    private void distributeDiffLock(float totalPower, float inputSpeed, Map<String, Float> feedbacks) {
        int count = countDrivenTargets(feedbacks);
        if (count == 0 || Math.abs(inputSpeed) < MIN_INPUT_SPEED) {
            pushMechPower(MechPower.EMPTY);
            return;
        }
        float baseTorque = totalPower / Math.abs(inputSpeed) / count;
        float meanSpeed = 0f;
        for (var entry : feedbacks.entrySet()) {
            Float ratio = attr.getPowerOutputs().get(entry.getKey());
            if (ratio != null && ratio != 0f) meanSpeed += entry.getValue() * ratio;
        }
        meanSpeed /= count;

        float sensitivity = attr.staticAttribute.diffLockSensitivity;
        Map<String, Float> weights = new HashMap<>();
        float totalWeight = 0f;
        for (var entry : feedbacks.entrySet()) {
            Float ratio = attr.getPowerOutputs().get(entry.getKey());
            if (ratio == null || ratio == 0f) continue;
            float deviation = Math.signum(inputSpeed) * (meanSpeed - entry.getValue() * ratio);
            float weight = totalPower > 0f ? Math.max(0f, baseTorque + sensitivity * deviation) : 1f;
            weights.put(entry.getKey(), weight);
            totalWeight += weight;
        }

        Map<String, MechPower> outputs = new HashMap<>();
        for (var entry : feedbacks.entrySet()) {
            String name = entry.getKey();
            Float ratio = attr.getPowerOutputs().get(name);
            if (ratio == null || ratio == 0f) continue;
            float share = totalWeight > 0f ? weights.get(name) / totalWeight : 1f / count;
            float reference = inputSpeed / ratio;
            float target = totalPower > 0f ? computeTargetSpeed(reference, entry.getValue(), sensitivity) : reference;
            outputs.put(name, new MechPower(totalPower * share, target));
        }
        pushMechPower(outputs);
    }

    /** 开放差速按有效端数均分功率，目标轴速按各端减速比折算。 */
    private void distributeOpenDiff(float totalPower, float inputSpeed, Map<String, Float> feedbacks) {
        int count = countDrivenTargets(feedbacks);
        if (count == 0 || Math.abs(inputSpeed) < MIN_INPUT_SPEED) {
            pushMechPower(MechPower.EMPTY);
            return;
        }
        Map<String, MechPower> outputs = new HashMap<>();
        for (String name : feedbacks.keySet()) {
            Float ratio = attr.getPowerOutputs().get(name);
            if (ratio == null || ratio == 0f) continue;
            outputs.put(name, new MechPower(totalPower / count, inputSpeed / ratio));
        }
        pushMechPower(outputs);
    }

    private int countDrivenTargets(Map<String, Float> feedbacks) {
        int count = 0;
        for (String name : feedbacks.keySet()) {
            Float ratio = attr.getPowerOutputs().get(name);
            if (ratio != null && ratio != 0f) count++;
        }
        return count;
    }

    /** 物理线程：读取输出端原始反馈(rad/s)，不乘减速比。 */
    private Map<String, Float> collectRawFeedbackSpeeds() {
        Map<String, Float> result = new HashMap<>();
        for (var entry : getMechPowerTargets().entrySet()) {
            var consumer = entry.getValue();
            if (consumer != null && consumer.isPowerPathConnected(getName())) {
                result.put(entry.getKey(), consumer.getFeedbackSpeed(getName()));
            }
        }
        return result;
    }

    /** 快端超过目标轴速死区时按强度回拖；其余情况直接追随输入轴速。 */
    private float computeTargetSpeed(float reference, float selfSpeed, float gain) {
        float direction = Math.signum(reference);
        float excess = (selfSpeed - reference) * direction;
        if (direction == 0f || excess <= LOCK_SLIP_DEADBAND) return reference;
        return reference + direction * excess * (1f - Math.clamp(gain, 0f, 1f));
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
            Float ratio = attr.getPowerOutputs().get(entry.getKey());
            if (consumer != null && ratio != null && ratio != 0f && consumer.isPowerPathConnected(getName())) {
                result.put(entry.getKey(), consumer.getFeedbackSpeed(getName()) * ratio);
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
