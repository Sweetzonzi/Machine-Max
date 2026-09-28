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

    /**
     * 差速锁驱动偏置(rad/s)：输出端反馈转速落后于同步参考转速时，在其之上额外给出的目标转速，
     * 使车轮的速度伺服持续输出驱动力矩，保证起步与牵引力。
     * 取值沿用旧实现中固定的 +30 rad/s 超速偏置（约等于半米级车轮 50 km/h 的转速当量）。
     */
    private static final float LOCK_DRIVE_BIAS = 30f;
    /**
     * 滑移死区(rad/s)：反馈转速超出同步参考转速不超过该值时，驱动偏置线性衰减到零，
     * 避免在参考转速附近驱动与拖曳反复切换引起抖动。
     */
    private static final float LOCK_SLIP_DEADBAND = 2f;
    /**
     * 开放差速公共扭矩分配分母的下限系数(× |ω_in|)：静止或极低速时各输出端反馈转速趋近 0，
     * 分母直接取 Σ|ω_fb · r| 会使公共扭矩被放大数百倍，故以输入轴转速为基准设下限。
     */
    private static final float OPEN_TORQUE_FLOOR = 0.2f;

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
     * 差速锁功率分配：扭矩再分配 + 目标转速牵引
     * <p>
     * 扭矩侧（模拟摩擦片式限滑差速器的扭矩转移）：
     * <ol>
     *   <li>输入扭矩均分至各输出端：τ_base = τ_in / N</li>
     *   <li>转速偏差驱动扭矩从快端向慢端转移（摩擦离合器效应）：
     *       Δτ_i = k × (ω_avg - ω_fb_i)，偏慢的输出端获得额外扭矩，偏快的释放扭矩</li>
     *   <li>负扭矩钳位至零后归一化，保证 Στ_out = τ_in（扭矩守恒）</li>
     *   <li>输出功率 P_i = τ_i × gearRatio_i × |v_target_i|，使车轮端按 P/|v| 还原出扭矩
     *       τ_i × gearRatio_i（ΣP_i = P_in，能量自动守恒）</li>
     * </ol>
     * 转速侧：每个输出端下发目标转速，牵引与拖曳由车轮端的速度伺服实现，
     * 见 {@link #computeTargetSpeed(float, float, float)}。
     *
     * @param totalPower 输入总功率
     * @param inputSpeed 输入轴转速（正=前进，负=倒车）
     * @param feedbacks  各输出端原始反馈转速（输出端坐标系，未乘减速比）
     */
    private void distributeDiffLock(float totalPower, float inputSpeed, Map<String, Float> feedbacks) {
        int n = feedbacks.size();

        // 输入扭矩 τ_in = P_in / |ω_in|
        float absInputSpeed = Math.abs(inputSpeed);
        if (absInputSpeed < 1e-6f) {
            pushMechPower(MechPower.EMPTY);
            return;
        }
        float tauIn = totalPower / absInputSpeed;
        float tauBase = tauIn / n; // 基础均分扭矩

        // 平均反馈转速（折算至输入端坐标系），用于扭矩再分配
        float avgInputSpeed = 0f;
        for (var entry : feedbacks.entrySet()) {
            Float gearRatio = attr.getPowerOutputs().get(entry.getKey());
            if (gearRatio == null) continue;
            avgInputSpeed += entry.getValue() * gearRatio;
        }
        avgInputSpeed /= n;

        // 耦合刚度 k：控制扭矩转移的激进程度，值越大转速偏差导致的扭矩转移越多
        float k = attr.staticAttribute.diffLockSensitivity;

        // 第一遍：计算各输出端原始扭矩
        Map<String, Float> rawTorques = new HashMap<>();
        float totalPositiveTorque = 0f;

        for (var entry : feedbacks.entrySet()) {
            String name = entry.getKey();
            Float gearRatio = attr.getPowerOutputs().get(name);
            if (gearRatio == null || gearRatio == 0f) continue;
            // 偏差 = 平均转速 - 当前反馈转速（输入端坐标系）
            // 正偏差(+): 该端偏慢（负载重），应获得额外扭矩
            // 负偏差(-): 该端偏快（负载轻/打滑），应释放扭矩
            float deviation = avgInputSpeed - entry.getValue() * gearRatio;
            float torque = tauBase + k * deviation;
            if (torque < 0f) torque = 0f; // 负扭矩钳位：打滑轮不输出动力
            rawTorques.put(name, torque);
            totalPositiveTorque += torque;
        }

        // 第二遍：归一化保证 Στ = τ_in，并下发目标转速
        Map<String, MechPower> outputs = new HashMap<>();
        float normalizer = totalPositiveTorque > 0f ? tauIn / totalPositiveTorque : 1f;

        for (var entry : feedbacks.entrySet()) {
            String name = entry.getKey();
            Float gearRatio = attr.getPowerOutputs().get(name);
            if (gearRatio == null || gearRatio == 0f) continue;

            float torque = rawTorques.getOrDefault(name, 0f) * normalizer; // 归一化保证扭矩守恒
            float refSpeed = inputSpeed / gearRatio;                        // 差速锁同步参考转速
            float targetSpeed = computeTargetSpeed(refSpeed, entry.getValue(), k);
            float power = torque * gearRatio * Math.abs(targetSpeed);       // P = τ × r × |ω|

            outputs.put(name, new MechPower(power, targetSpeed));
        }

        pushMechPower(outputs);
    }

    /**
     * 差速器模式功率分配：各输出端驱动扭矩相等，转速允许自由差速
     * <p>
     * 公共输入扭矩 τ_in = P_in / max(Σ|ω_fb_i × r_i|, {@link #OPEN_TORQUE_FLOOR} × |ω_in|)，
     * 分母下限用于避免静止或极低速时扭矩被无限放大。
     * 各输出端下发相同的驱动扭矩 τ_in × r_i；目标转速由
     * {@link #computeTargetSpeed(float, float, float)} 给出（gain 取 0，不做拖曳）。
     *
     * @param totalPower 输入功率
     * @param inputSpeed 输入转速
     * @param feedbacks 各输出端原始反馈转速（输出端坐标系，未乘减速比）
     */
    private void distributeOpenDiff(float totalPower, float inputSpeed, Map<String, Float> feedbacks) {
        if (totalPower == 0f) {
            pushMechPower(MechPower.EMPTY);
            return;
        }
        float absInputSpeed = Math.abs(inputSpeed);
        //各输出端反馈转速绝对值之和（折算至输入端坐标系）
        float receiverTotalSpeed = 0f;
        for (var entry : feedbacks.entrySet()) {
            Float gearRatio = attr.getPowerOutputs().get(entry.getKey());
            if (gearRatio == null) continue;
            receiverTotalSpeed += Math.abs(entry.getValue() * gearRatio);
        }
        //分母下限：静止时 Σ 反馈转速趋近 0，直接相除会使公共扭矩被放大数百倍
        float denominator = Math.max(receiverTotalSpeed, OPEN_TORQUE_FLOOR * absInputSpeed);
        if (denominator < 1e-6f) {
            pushMechPower(MechPower.EMPTY);
            return;
        }
        float torque = totalPower / denominator;//公共输入扭矩，各输出端扭矩相等

        Map<String, MechPower> outputs = new HashMap<>();
        for (var entry : feedbacks.entrySet()) {
            String name = entry.getKey();
            Float gearRatio = attr.getPowerOutputs().get(name);
            if (gearRatio == null || gearRatio == 0f) continue;
            float refSpeed = inputSpeed / gearRatio;
            float targetSpeed = computeTargetSpeed(refSpeed, entry.getValue(), 0f);
            float power = torque * gearRatio * Math.abs(targetSpeed);
            outputs.put(name, new MechPower(power, targetSpeed));
        }
        pushMechPower(outputs);
    }

    /**
     * 收集所有输出端的原始反馈转速（输出端坐标系，未乘减速比）
     *
     * @return 输出端名称 -&gt; 原始反馈转速
     */
    private Map<String, Float> collectRawFeedbackSpeeds() {
        Map<String, Float> result = new HashMap<>();
        for (var entry : getMechPowerTargets().entrySet()) {
            var consumer = entry.getValue();
            if (consumer != null && consumer.isPowerPathConnected(getName())) {
                result.put(entry.getKey(), consumer.getFeedbackSpeed());
            }
        }
        return result;
    }

    /**
     * 计算某个输出端的目标转速（输出端坐标系，有符号）
     * <p>
     * 目标转速下发给车轮端的速度伺服：目标高于当前转速时电机加速，低于当前转速时电机拖曳制动，
     * 因此限滑能力只通过速度环体现，不需要在物理关节上额外增加约束。
     * <ul>
     *   <li>反馈转速不高于同步参考转速：给出 {@link #LOCK_DRIVE_BIAS} 的驱动偏置，保证起步与牵引力；</li>
     *   <li>超出参考转速但不超过 {@link #LOCK_SLIP_DEADBAND}：偏置线性衰减到零，避免驱动与拖曳来回抖动；</li>
     *   <li>超出死区（打滑或悬空）：按 {@code gain} 的比例向参考转速回拖，
     *       {@code gain = 1} 时目标即同步参考转速（等效刚性锁止），{@code gain = 0} 时不拖曳。</li>
     * </ul>
     * 平衡状态下反馈转速收敛于同步参考转速，与 {@code gain} 的取值无关，{@code gain} 只决定收敛快慢。
     *
     * @param refSpeed  动力链给出的同步参考转速（输出端坐标系，正负表示行进方向）
     * @param selfSpeed 该输出端本 tick 的反馈转速（输出端坐标系）
     * @param gain      限滑强度，内部钳位至 0~1
     * @return 该输出端的目标转速（输出端坐标系）
     */
    private float computeTargetSpeed(float refSpeed, float selfSpeed, float gain) {
        float direction = Math.signum(refSpeed);
        if (direction == 0f) return 0f;//无动力链参考转速时不驱动，避免静止时凭空产生转速
        float refForward = refSpeed * direction;//沿行进方向的参考转速，恒为非负
        float selfForward = selfSpeed * direction;
        float excess = selfForward - refForward;//超出参考转速的部分，即滑移量
        float targetForward;
        if (excess <= 0f) {
            //落后于参考转速：给出驱动偏置，保证车轮能顶到参考转速
            targetForward = refForward + LOCK_DRIVE_BIAS;
        } else if (excess < LOCK_SLIP_DEADBAND) {
            //临界区：驱动偏置线性衰减，避免在参考转速附近驱动与拖曳反复切换
            targetForward = refForward + LOCK_DRIVE_BIAS * (1f - excess / LOCK_SLIP_DEADBAND);
        } else {
            //打滑区：按 gain 比例向参考转速回拖
            targetForward = refForward + excess * (1f - Math.clamp(gain, 0f, 1f));
        }
        return targetForward * direction;
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
