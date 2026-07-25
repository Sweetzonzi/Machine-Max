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
     * 差速锁功率分配：基于摩擦离合器物理模型的扭矩再分配
     * <p>
     * 物理原理（模拟摩擦片式限滑差速器）：
     * <ol>
     *   <li>输入扭矩均分至各输出端：τ_base = τ_in / N</li>
     *   <li>转速偏差驱动扭矩从快端向慢端转移（摩擦离合器效应）：
     *       Δτ_i = k × (ω_avg - ω_fb_i)，偏慢的输出端获得额外扭矩，偏快的释放扭矩</li>
     *   <li>负扭矩钳位至零后归一化，保证 Στ_out = τ_in（扭矩守恒）</li>
     *   <li>输出转速强制为差速锁同步转速：ω_out = ω_in / gearRatio</li>
     *   <li>输出功率 P_i = τ_i × |ω_in|，ΣP_i = P_in（能量自动守恒）</li>
     * </ol>
     *
     * @param totalPower 输入总功率
     * @param inputSpeed 输入轴转速（正=前进，负=倒车）
     * @param feedbacks  各输出端反馈转速（已乘以减速比，折算至输入端坐标系）
     */
    private void distributeDiffLock(float totalPower, float inputSpeed, Map<String, Float> feedbacks) {
        int n = feedbacks.size();

        // 计算平均反馈转速（输入端坐标系）
        float avgSpeed = 0f;
        for (float speed : feedbacks.values()) {
            avgSpeed += speed;
        }
        avgSpeed /= n;

        // 输入扭矩 τ_in = P_in / |ω_in|
        float absInputSpeed = Math.abs(inputSpeed);
        if (absInputSpeed < 1e-6f) {
            pushMechPower(MechPower.EMPTY);
            return;
        }
        float tauIn = totalPower / absInputSpeed;
        float tauBase = tauIn / n; // 基础均分扭矩

        // 耦合刚度 k：控制扭矩转移的激进程度，值越大转速偏差导致的扭矩转移越多
        float k = attr.staticAttribute.diffLockSensitivity;

        // 第一遍：计算各输出端原始扭矩
        Map<String, Float> rawTorques = new HashMap<>();
        float totalPositiveTorque = 0f;

        for (var entry : feedbacks.entrySet()) {
            String name = entry.getKey();
            float fbSpeed = entry.getValue();
            // 偏差 = 平均转速 - 当前反馈转速
            // 正偏差(+): 该端偏慢（负载重），应获得额外扭矩
            // 负偏差(-): 该端偏快（负载轻/打滑），应释放扭矩
            float deviation = avgSpeed - fbSpeed;
            float torque = tauBase + k * deviation;
            if (torque < 0f) torque = 0f; // 负扭矩钳位：打滑轮不输出动力
            rawTorques.put(name, torque);
            totalPositiveTorque += torque;
        }

        // 第二遍：归一化保证 Στ = τ_in，以锁止转速计算输出功率
        Map<String, MechPower> outputs = new HashMap<>();
        float normalizer = totalPositiveTorque > 0f ? tauIn / totalPositiveTorque : 1f;

        for (var entry : feedbacks.entrySet()) {
            String name = entry.getKey();
            Float gearRatio = attr.getPowerOutputs().get(name);
            if (gearRatio == null || gearRatio == 0f) continue;

            float torque = rawTorques.get(name) * normalizer; // 归一化保证扭矩守恒
            float power = torque * absInputSpeed;             // P = τ × |ω|
            float lockSpeed = inputSpeed / gearRatio;          // 差速锁同步转速

            outputs.put(name, new MechPower(power, lockSpeed));
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
            receiverTotalSpeed += speed;
        }
        receiverTotalSpeed += 0.005f * inputSpeed;
        if (receiverTotalSpeed == 0f) receiverTotalSpeed = 0.2f * inputSpeed;
        float torque = totalPower / receiverTotalSpeed;
        Map<String, MechPower> outputs = new HashMap<>();
        for (var entry : feedbacks.entrySet()) {
            String name = entry.getKey();
            Float gearRatio = attr.getPowerOutputs().get(name);
            if (gearRatio == null || gearRatio == 0f) continue;
            float receiverSpeed = 0.8f * entry.getValue() + 0.2f * inputSpeed;
            if (receiverSpeed == 0f) receiverSpeed = 0.2f * inputSpeed;
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
