package io.github.sweetzonzi.machine_max.common.vehicle.subsystem;

import cn.solarmoon.spark_core.sound.IMultiChannelSoundSpreader;
import cn.solarmoon.spark_core.util.SparkMathKt;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.WorkingState;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.dynamic_attr.EngineSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.static_attr.EngineSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.vehicle.energy.IMechPowerConsumer;
import io.github.sweetzonzi.machine_max.common.vehicle.energy.IMechPowerProducer;
import io.github.sweetzonzi.machine_max.common.vehicle.energy.MechPower;
import io.github.sweetzonzi.machine_max.common.vehicle.signal.*;
import io.github.sweetzonzi.machine_max.util.control.PDController;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class EngineSubsystem extends BasicSubsystem implements IMultiChannelSoundSpreader, IMechPowerProducer {
    public final EngineSubsystemAttr attr;
    public final double RED_LINE_SPEED;//红线转速(rad/s)
    public final double MAX_TORQUE_SPEED;//最大扭矩转速(rad/s)
    public final double IDLE_SPEED;//怠速转速(rad/s)
    public final double MAX_TORQUE;//最大扭矩(N·m)
    public final double MIN_IDLE_THROTTLE;//怠速转速下的最小油门
    protected static final EntityDataAccessor<Float> ROT_SPEED_ID = SynchedEntityData.defineId(EngineSubsystem.class, EntityDataSerializers.FLOAT);
    public double throttleInput;//当前油门输入（0~1）
    private static final int RETRIGGER_TICKS = 20;

    /**
     * 声音通道缓存：每个转速档位一个通道。
     */
    private final Map<String, SoundChannel> soundChannels = new LinkedHashMap<>();

    /**
     * 通道键到工况状态的映射，用于按通道计算音高。
     */
    private final Map<String, WorkingState> channelStates = new LinkedHashMap<>();

    private final PDController coupleTorquePD;

    //能量输出目标：子系统名或连接点名 -> 消费者
    private final Map<String, IMechPowerConsumer> energyTargets = new HashMap<>();

    //TODO: 引擎输出功率随曲轴转角周期性变化，气缸数越多输出扭矩越平稳
    public EngineSubsystem(ISubsystemHost owner, String name, EngineSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
        RED_LINE_SPEED = attr.staticAttribute.redLineRpm * Math.PI / 30.0;//红线转速(rad/s)
        MAX_TORQUE_SPEED = attr.staticAttribute.maxTorqueRpm * Math.PI / 30.0;//最大扭矩转速(rad/s)
        IDLE_SPEED = attr.staticAttribute.idleRpm * Math.PI / 30.0;//怠速转速(rad/s)
        MAX_TORQUE = attr.staticAttribute.maxTorque;
        setRotSpeed((float) (IDLE_SPEED + 1));
        double minThrottle = 1.005 * calculateDampingTorque(IDLE_SPEED) / calculateMaxTorque(IDLE_SPEED);
        MIN_IDLE_THROTTLE = Math.min(minThrottle, 1f);
        coupleTorquePD = new PDController(
                1.5 * attr.getStaticAttribute().getInertia(),
                0.5 * attr.getStaticAttribute().getInertia(),
                1.0 / getPhysicsLevel().getTps()
        );
        if (attr.getStaticAttribute().workingStates.isEmpty() && getLevel().isClientSide()) {
            attr.getStaticAttribute().createSounds();
        }
    }

    @Override
    public void onTick() {
        super.onTick();
        Level level = getSubPart().getLevel();
        if (level.isClientSide() && this.isActive()) {
            if (soundChannels.isEmpty()) {
                initSoundChannels();
            }
            double rpm = Math.abs(30 * getRotSpeed() / Math.PI);
            EngineSubsystemStaticAttr.RpmWorkingStates candidates = attr.getStaticAttribute().getAdjacentWorkingStates(rpm);
            if (candidates.center() != EngineSubsystemStaticAttr.EMPTY_WORKING_STATE) {
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
            throttleInput = MIN_IDLE_THROTTLE;
        } else {
            updateThrottleInput();
        }
        double rotSpeed = getRotSpeed();
        if (rotSpeed / IDLE_SPEED < 1.05) throttleInput = Math.clamp(throttleInput, MIN_IDLE_THROTTLE, 1);
        else throttleInput = Math.clamp(throttleInput, 0, 1);
        double engineTorque = throttleInput * calculateMaxTorque(rotSpeed);
        if (!isActive()) engineTorque = 0.0;
        double dampingTorque = calculateDampingTorque(rotSpeed);
        double netTorque = engineTorque - dampingTorque;
        if (feedbacks.isEmpty()) {
            if (!getSubPart().level.isClientSide()) {
                rotSpeed += netTorque / attr.staticAttribute.inertia / getPhysicsLevel().getTps();
                rotSpeed = 0.995 * Math.clamp(rotSpeed, 0.5 * IDLE_SPEED, RED_LINE_SPEED * 1.05) + 0.005 * IDLE_SPEED;
                setRotSpeed((float) rotSpeed);
            }
            pushMechPower(MechPower.ZERO);
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
            ) : 0;
            if (!getSubPart().level.isClientSide()) {
                rotSpeed += (netTorque - coupleTorque) / attr.staticAttribute.inertia / getPhysicsLevel().getTps();
                rotSpeed = Math.clamp(rotSpeed, 0.1 * IDLE_SPEED, RED_LINE_SPEED * 2);
                rotSpeed = 0.95 * Math.clamp(rotSpeed, 0.5 * IDLE_SPEED, RED_LINE_SPEED * 1.05) + 0.05 * avgFeedback;
                setRotSpeed((float) rotSpeed);
            }
            pushMechPower(new MechPower((float) ((netTorque + coupleTorque) * rotSpeed), (float) rotSpeed));
            attr.rpmOutputTargets.keySet().forEach(target -> sendSignalToAllTargets(target, getRotSpeed()));
        }
    }

    @Override
    public Map<String, IMechPowerConsumer> getMechPowerTargets() {
        return energyTargets;
    }

    @Override
    public void rebuildMechPowerTargets() {
        energyTargets.clear();
        String target = attr.getPowerOutputTarget();
        if (target == null || target.isEmpty()) return;
        IMechPowerConsumer consumer = resolveEnergyTarget(target);
        if (consumer != null) {
            energyTargets.put(target, consumer);
        }
    }

    private IMechPowerConsumer resolveEnergyTarget(String targetName) {
        if (getOwner().getSubsystems().containsKey(targetName)) {
            var sub = getOwner().getSubsystems().get(targetName);
            if (sub instanceof IMechPowerConsumer consumer) return consumer;
        }
        if (getSubPart().connectors.containsKey(targetName)) {
            AbstractConnector conn = getSubPart().connectors.get(targetName);
            if (conn.mechanicalEnergyPort != null) return conn.mechanicalEnergyPort;
        }
        return null;
    }

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

    private void updateTwoChannelWeightsByRpm(double rpm, EngineSubsystemStaticAttr.RpmWorkingStates candidates) {
        clearChannelWeights();
        WorkingState center = candidates.center();
        if (center == EngineSubsystemStaticAttr.EMPTY_WORKING_STATE) return;
        WorkingState left = candidates.left();
        WorkingState right = candidates.right();
        WorkingState neighbor = rpm >= center.rpm() ? right : left;
        String centerKey = findChannelKeyByState(center);
        if (centerKey == null) return;
        if (neighbor == EngineSubsystemStaticAttr.EMPTY_WORKING_STATE) {
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
     * 计算给定转速下的最大扭矩
     *
     * @param rotSpeed 转速(rad/s)
     * @return 当前转速下的最大扭矩(N · m)
     */
    private double calculateMaxTorque(double rotSpeed) {
        double result = 0;
        if (rotSpeed <= 0) return result;
        else if (rotSpeed <= IDLE_SPEED) {
            result = rotSpeed / IDLE_SPEED * MAX_TORQUE * attr.getStaticAttribute().getIdleRpmTorqueRatio();
        } else if (rotSpeed <= MAX_TORQUE_SPEED) {//线性上升段：怠速 -> 最大扭矩转速，在怠速扭矩和最大扭矩之间线性插值
            double k = (rotSpeed - IDLE_SPEED) / MAX_TORQUE_SPEED;
            result = k * MAX_TORQUE + (1 - k) * MAX_TORQUE * attr.getStaticAttribute().getRedLineRpmTorqueRatio();
        } else if (rotSpeed <= RED_LINE_SPEED) {//平台段：最大扭矩转速 -> 红线转速，在最大扭矩和全功率扭矩之间线性插值
            double k = (rotSpeed - MAX_TORQUE_SPEED) / (RED_LINE_SPEED - MAX_TORQUE_SPEED);
            result = k * MAX_TORQUE * attr.getStaticAttribute().getRedLineRpmTorqueRatio() + (1 - k) * MAX_TORQUE;
        } else { //超速时动力大幅衰减
            result = Math.pow(5, -10 * (rotSpeed - RED_LINE_SPEED) / RED_LINE_SPEED) * attr.staticAttribute.maxPower / rotSpeed;
        }
        result = Math.min(result, attr.getStaticAttribute().getMaxPower() / rotSpeed);//限制最大输出功率
        result *= 0.3 + 0.7 * Math.sqrt(getDurability() / getMaxDurability());//耐久度影响
        //TODO:扭矩输出根据转速和气缸数周期性变化
        return result;
    }

    /**
     * 计算给定转速下的内部阻力矩
     *
     * @param rotSpeed 转速(rad/s)
     * @return 当前转速下的内部阻力矩(N · m)
     */
    private double calculateDampingTorque(double rotSpeed) {
        double result = 0;
        if (Math.abs(rotSpeed) <= IDLE_SPEED) return result;
        for (int i = 0; i < attr.staticAttribute.dampingFactors.size(); i++) {
            result += attr.staticAttribute.dampingFactors.get(i) * Math.pow(Math.abs(rotSpeed), i);
        }
        return Math.signum(rotSpeed) * result;
    }

    /**
     * 获取油门信号，控制油门开度进而控制发动机输出功率
     */
    private void updateThrottleInput() {
        double powerControlInput = -1;
        for (String inputKey : attr.staticAttribute.throttleInputKeys) {
            SignalChannel signalChannel = getSignalChannel(inputKey);
            Object signal = signalChannel.getFirstSignal();
            if (signal instanceof Float) {
                powerControlInput = (float) signalChannel.getFirstSignal() / 100f;
                break;
            } else if (signal instanceof MoveInputSignal) {
                powerControlInput = Math.abs(((MoveInputSignal) signalChannel.getFirstSignal()).getMoveInput()[2] / 100f);
                break;
            }
        }
        throttleInput = powerControlInput;
    }

    public double getRotSpeed() {
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
        return 0.3f + 0.7f * (float) Math.clamp(Math.abs(throttleInput), 0, 1);
    }

    @Override
    public float getPitch(UUID uuid, SoundEvent event) {
        SoundChannel channel = getChannel(uuid);
        if (channel == null) return 1f;
        WorkingState state = channelStates.get(channel.getChannelKey());
        if (state == null) return 1f;
        double rpm = Math.max(Math.abs(30 * getRotSpeed() / Math.PI), 0.5 * attr.getStaticAttribute().getIdleRpm());
        return (float) Math.clamp(rpm / state.rpm(), 0.5, 2);
    }

    @Override
    public @NotNull Map<String, SoundChannel> getSoundChannels() {
        return soundChannels;
    }
}
