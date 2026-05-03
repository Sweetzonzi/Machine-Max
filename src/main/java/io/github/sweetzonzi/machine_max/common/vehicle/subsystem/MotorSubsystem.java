package io.github.sweetzonzi.machine_max.common.vehicle.subsystem;

import cn.solarmoon.spark_core.sound.IMultiChannelSoundSpreader;
import cn.solarmoon.spark_core.util.SparkMathKt;
import io.github.sweetzonzi.machine_max.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.WorkingState;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.dynamic_attr.MotorSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.static_attr.MotorSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.energy.IMechPowerConsumer;
import io.github.sweetzonzi.machine_max.common.vehicle.energy.IMechPowerProducer;
import io.github.sweetzonzi.machine_max.common.vehicle.energy.MechPower;
import io.github.sweetzonzi.machine_max.common.vehicle.signal.*;
import io.github.sweetzonzi.machine_max.util.control.PDController;
import lombok.Getter;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@Getter
public class MotorSubsystem extends BasicSubsystem implements IMultiChannelSoundSpreader, IMechPowerProducer, ITorqueProvider {
    public final double RED_LINE_SPEED;//红线转速(rad/s)
    public final MotorSubsystemAttr attr;
    protected static final EntityDataAccessor<Float> ROT_SPEED_ID = SynchedEntityData.defineId(MotorSubsystem.class, EntityDataSerializers.FLOAT);
    public double throttleInput;//当前电门输入（-1~1）
    private static final int RETRIGGER_TICKS = 30;

    /**
     * 声音通道缓存：每个转速档位一个通道。
     */
    private final Map<String, SoundChannel> soundChannels = new LinkedHashMap<>();

    /**
     * 通道键到工况状态的映射，用于按通道计算音高。
     */
    private final Map<String, WorkingState> channelStates = new LinkedHashMap<>();

    private final PDController coupleTorquePD;

    @Getter
    private final Map<String, IMechPowerConsumer> mechPowerTargets = new HashMap<>();

    public MotorSubsystem(ISubsystemHost owner, String name, MotorSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
        RED_LINE_SPEED = attr.staticAttribute.redLineRpm * Math.PI / 30.0;//红线转速(rad/s)
        coupleTorquePD = new PDController(
                1.5 * attr.getStaticAttribute().getInertia(), //kp
                0.5 * attr.getStaticAttribute().getInertia(), //kd
                1.0 / getPhysicsLevel().getTps() //step
        );
        if (attr.getStaticAttribute().workingStates.isEmpty() && getLevel().isClientSide()) {
            attr.getStaticAttribute().createSounds();
        }
        initSoundChannels();
    }

    @Override
    public void onTick() {
        super.onTick();
        // 根据转速和油门播放声音
        Level level = getSubPart().getLevel();
        if (level.isClientSide() && this.isActive()) {
            if (soundChannels.isEmpty()) {
                initSoundChannels();
            }
            double rpm = Math.abs(30 * getRotSpeed() / Math.PI);
            MotorSubsystemStaticAttr.RpmWorkingStates candidates = attr.getStaticAttribute().getAdjacentWorkingStates(rpm);
            if (candidates.center() != MotorSubsystemStaticAttr.EMPTY_WORKING_STATE) {
                updateTwoChannelWeightsByRpm(rpm, candidates);
                tickSoundChannels(level, SoundSource.PLAYERS, tickCount, 8, 8);
            } else {
                clearChannelWeights();
            }
        } else {
            clearChannelWeights();
        }
    }

    @Override
    public void onPrePhysicsTick() {
        Map<String, Float> feedbacks = collectFeedbackSpeeds();
        if (feedbacks.isEmpty()) {
            throttleInput = 0;
        } else {
            updateThrottleInput();
        }
        double rotSpeed = getRotSpeed();
        //TODO:电门输入与转速方向相反时，发电模式
        double engineTorque = throttleInput * getTorqueAtSpeed(rotSpeed);//输出扭矩
        double dampingTorque = getDampingTorque(rotSpeed);
        double netTorque = engineTorque - dampingTorque;
        if (feedbacks.isEmpty()) {
            if (!getSubPart().level.isClientSide()) {
                rotSpeed += netTorque / attr.staticAttribute.inertia / getPhysicsLevel().getTps();
                rotSpeed = 0.99 * rotSpeed;//额外修正
                setRotSpeed((float) rotSpeed);
            }
            this.coupleTorquePD.resetError();
            pushMechPower(MechPower.EMPTY);
            attr.rpmOutputTargets.keySet().forEach(target -> sendSignalToAllTargets(target, getRotSpeed()));
        } else {
            float avgFeedback = 0;
            int count = 0;
            for (float speed : feedbacks.values()) {
                avgFeedback += speed;
                count++;
            }
            if (count > 0) avgFeedback /= count;
            avgFeedback = -avgFeedback;
            double speedDiff = rotSpeed + avgFeedback;
            double coupleTorque = Math.abs(speedDiff) < 5 ? Math.clamp(
                    this.isActive() ? this.coupleTorquePD.step(0, Math.abs(speedDiff) < 10 ? speedDiff * speedDiff / 10 : speedDiff) : 0,
                    -0.25 * attr.getStaticAttribute().maxTorque,
                    0.25 * attr.getStaticAttribute().maxTorque
            ) : 0; // 使用耦合扭矩补偿转速差，考虑饱和模拟打滑
            coupleTorque = 0;//TODO: 耦合扭矩会导致停车后车轮乱动，暂时关闭
            //有转速反馈信号时，根据转速反馈信号控制引擎转速
            if (!getSubPart().level.isClientSide()) { //与转动惯量属性挂钩的转速改变量，客户端计算结果不精确，不应用
                rotSpeed += (netTorque - coupleTorque) / attr.staticAttribute.inertia / getPhysicsLevel().getTps();
                rotSpeed = Math.clamp(rotSpeed,
                        -1.1 * attr.staticAttribute.redLineRpm * Math.PI / 30,
                        1.1 * attr.staticAttribute.redLineRpm * Math.PI / 30);
                rotSpeed = 0.95 * Math.clamp(rotSpeed, RED_LINE_SPEED * -1.05, RED_LINE_SPEED * 1.05) + 0.05 * avgFeedback; // 额外修正
                setRotSpeed((float) rotSpeed);
            }
            pushMechPower(new MechPower((float) ((netTorque - coupleTorque) * rotSpeed), (float) rotSpeed));
            attr.rpmOutputTargets.keySet().forEach(target -> sendSignalToAllTargets(target, getRotSpeed()));
        }
    }

    @Override
    public void rebuildMechPowerTargets() {
        mechPowerTargets.clear();
        String target = attr.getPowerOutputTarget();
        if (target == null || target.isEmpty()) return;
        IMechPowerConsumer consumer = resolveEnergyTarget(target);
        if (consumer != null) {
            mechPowerTargets.put(target, consumer);
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

    /**
     * 根据当前 workingStates 重建多声音通道。
     */
    private void initSoundChannels() {
        soundChannels.clear();
        channelStates.clear();
        for (int i = 0; i < attr.getStaticAttribute().workingStates.size(); i++) {
            List<WorkingState> states = attr.getStaticAttribute().workingStates.get(i);
            if (states == null || states.isEmpty()) continue;
            WorkingState state = states.getFirst();
            String key = "rpm_" + i;
            soundChannels.put(key, new SoundChannel(key, state.sound(), RETRIGGER_TICKS));
            channelStates.put(key, state);
        }
    }

    /**
     * 左右两通道平方插值（等功率思路）：
     * 1. 只给 center 和邻近方向通道分配权重；
     * 2. 其它通道权重强制为 0。
     */
    private void updateTwoChannelWeightsByRpm(double rpm, MotorSubsystemStaticAttr.RpmWorkingStates candidates) {
        // 先清零，确保“除左右两通道外全部为0”。
        clearChannelWeights();

        WorkingState center = candidates.center();
        if (center == MotorSubsystemStaticAttr.EMPTY_WORKING_STATE) return;

        WorkingState left = candidates.left();
        WorkingState right = candidates.right();
        WorkingState neighbor = rpm >= center.rpm() ? right : left;

        String centerKey = findChannelKeyByState(center);
        if (centerKey == null) return;

        if (neighbor == MotorSubsystemStaticAttr.EMPTY_WORKING_STATE) {
            // 无邻近通道时，中心通道独占。
            setChannelWeight(centerKey, 1.0f);
            return;
        }

        String neighborKey = findChannelKeyByState(neighbor);
        if (neighborKey == null) {
            setChannelWeight(centerKey, 1.0f);
            return;
        }

        double minRpm = Math.min(center.rpm(), neighbor.rpm());
        double maxRpm = Math.max(center.rpm(), neighbor.rpm());
        if (maxRpm - minRpm < 1e-4) {
            setChannelWeight(centerKey, 1.0f);
            return;
        }

        double t = Math.clamp((rpm - minRpm) / (maxRpm - minRpm), 0, 1);

        // 平方插值：center 与 neighbor 仅两者有权重。
        float centerWeight;
        float neighborWeight;
        if (neighbor.rpm() > center.rpm()) {
            centerWeight = (float) ((1 - t) * (1 - t));
            neighborWeight = (float) (t * t);
        } else {
            centerWeight = (float) (t * t);
            neighborWeight = (float) ((1 - t) * (1 - t));
        }

        setChannelWeight(centerKey, centerWeight);
        setChannelWeight(neighborKey, neighborWeight);
    }

    private void setChannelWeight(String channelKey, float weight) {
        SoundChannel channel = soundChannels.get(channelKey);
        if (channel != null) {
            channel.setWeight(weight);
        }
    }

    private String findChannelKeyByState(WorkingState target) {
        for (Map.Entry<String, WorkingState> entry : channelStates.entrySet()) {
            if (entry.getValue().equals(target)) return entry.getKey();
        }
        return null;
    }

    private void clearChannelWeights() {
        for (SoundChannel channel : soundChannels.values()) {
            channel.setWeight(0.0f);
        }
    }

    /**
     * 计算给定转速下的扭矩（基于红线功率比模型）。<p>
     * 1. 恒扭矩区（|转速| ≤ 基速）：受电流限制，输出 maxTorque。<br>
     * 2. 功率衰减区（基速 < |转速| ≤ 红线）：功率从 maxPower 线性衰减到
     *    redLinePowerRatio × maxPower，扭矩 = P(ω) / ω。<br>
     * 3. 红线外：扭矩快速衰减。<br>
     * 功率衰减使变速箱在高转速区仍有效益。
     *
     * @param rotSpeed 转速(rad/s)
     * @return 当前转速下的最大扭矩(N · m)
     */
    public double getTorqueAtSpeed(double rotSpeed) {
        double result = 0;
        if (!isActive()) return result;
        double absSpeed = Math.abs(rotSpeed);
        // 基速：恒扭矩区到功率衰减区的转折点 (rad/s)，P = T × ω
        double baseSpeed = attr.staticAttribute.maxPower / Math.max(attr.staticAttribute.maxTorque, 0.01f);
        if (absSpeed <= baseSpeed) {
            // 恒扭矩区
            result = attr.staticAttribute.maxTorque;
        } else if (absSpeed <= RED_LINE_SPEED) {
            // 功率衰减区：功率从 maxPower 线性衰减到 redLinePowerRatio × maxPower
            double k = (absSpeed - baseSpeed) / (RED_LINE_SPEED - baseSpeed);
            double power = attr.staticAttribute.maxPower * (1 - k * (1 - attr.staticAttribute.redLinePowerRatio));
            result = power / absSpeed;
        } else {
            // 红线外：功率指数级快速衰减
            double redLinePower = attr.staticAttribute.maxPower * attr.staticAttribute.redLinePowerRatio;
            result = redLinePower / absSpeed * Math.pow(0.5, (absSpeed - RED_LINE_SPEED) / (RED_LINE_SPEED * 0.1));
        }
        result *= 0.3 + 0.7 * Math.sqrt(getDurability() / getMaxDurability());
        return result;
    }

    /**
     * 计算给定转速下的内部阻力矩
     *
     * @param rotSpeed 转速(rad/s)
     * @return 当前转速下的内部阻力矩(N · m)
     */
    public double getDampingTorque(double rotSpeed) {
        double result = 0;
        if (Math.abs(rotSpeed) <= 0.5 * MotorSubsystemStaticAttr.baseRPM / Math.PI * 30) return result;
        for (int i = 0; i < attr.staticAttribute.dampingFactors.size(); i++) {
            result += attr.staticAttribute.dampingFactors.get(i) * Math.pow(Math.abs(rotSpeed), i);
        }
        return Math.signum(rotSpeed) * result;
    }

    /**
     * 获取油门信号，控制油门开度进而控制发动机输出功率
     */
    private void updateThrottleInput() {
        double powerControlInput = 0;
        for (String inputKey : attr.staticAttribute.throttleInputKeys) {
            SignalChannel signalChannel = getSignalChannel(inputKey);
            if (signalChannel.getFirstSignal() instanceof Float) {
                powerControlInput = (float) signalChannel.getFirstSignal() / 100f;
                break;
            } else if (signalChannel.getFirstSignal() instanceof MoveInputSignal) {
                powerControlInput = ((MoveInputSignal) signalChannel.getFirstSignal()).getMoveInput()[2] / 100f;
                break;
            }
        }
        throttleInput = powerControlInput;
    }

    public float getRotSpeed() {
        return getSynchedData().get(ROT_SPEED_ID);
    }

    public void setRotSpeed(float rotSpeed) {
        getSynchedData().set(ROT_SPEED_ID, rotSpeed);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ROT_SPEED_ID, 0f);
    }

    @Override
    public Map<String, List<String>> getTargetNames() {
        Map<String, List<String>> result = new HashMap<>(2);
        result.putAll(attr.rpmOutputTargets);
        return result;
    }

    @Override
    public @NotNull Vec3 getPosition(UUID uuid, SoundEvent event) {
        return SparkMathKt.toVec3(getSubPart().getPosition());
    }

    @Override
    public @NotNull Vec3 getSpeed(UUID uuid, SoundEvent event) {
        return SparkMathKt.toVec3(getSubPart().getLinearVelocity());
    }

    @Override
    public float getMasterVolume(UUID uuid, SoundEvent event) {
        if (getSubPart().isRemoved() || !isActive()) return 0f;
        float throttleFactor = 0.3f + 0.7f * (float) Math.clamp(Math.abs(throttleInput), 0, 1);
        double rpm = Math.abs(30 * getRotSpeed() / Math.PI);
        float lowRpmFactor = rpm < MotorSubsystemStaticAttr.baseRPM
                ? (float) Math.sin(Math.PI * (rpm / MotorSubsystemStaticAttr.baseRPM))
                : 1f;
        return throttleFactor * lowRpmFactor;
    }

    @Override
    public float getPitch(UUID uuid, SoundEvent event) {
        SoundChannel channel = getChannel(uuid);
        if (channel == null) return 1f;
        WorkingState state = channelStates.get(channel.getChannelKey());
        if (state == null) return 1f;
        double rpm = Math.max(Math.abs(30 * getRotSpeed() / Math.PI), 0.5 * MotorSubsystemStaticAttr.baseRPM);
        return (float) Math.clamp(rpm / state.rpm(), 0.5, 2);
    }

    @Override
    public @NotNull Map<String, SoundChannel> getSoundChannels() {
        return soundChannels;
    }
}
