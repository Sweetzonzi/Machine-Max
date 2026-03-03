package io.github.sweetzonzi.machine_max.common.vehicle.subsystem;

import cn.solarmoon.spark_core.api.SparkLevel;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.sound.ISoundSpreader;
import cn.solarmoon.spark_core.util.SparkMathKt;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.WorkingState;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.dynamic_attr.EngineSubsystemAttr;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EngineSubsystem extends AbstractSubsystem implements ISoundSpreader {
    public final EngineSubsystemAttr attr;
    public final double RED_LINE_SPEED;//红线转速(rad/s)
    public final double MAX_TORQUE_SPEED;//最大扭矩转速(rad/s)
    public final double IDLE_SPEED;//怠速转速(rad/s)
    public final double MAX_TORQUE;//最大扭矩(N·m)
    public final double MIN_IDLE_THROTTLE;//怠速转速下的最小油门
    protected static final EntityDataAccessor<Float> ROT_SPEED_ID = SynchedEntityData.defineId(EngineSubsystem.class, EntityDataSerializers.FLOAT);
    public double throttleInput;//当前油门输入（0~1）
    private WorkingState currentState = null;//当前引擎工况及对应音效
    private UUID currentSoundUUID = UUID.randomUUID();
    private int sinceLastSoundUpdate = 0;
    private final PDController coupleTorquePD;

    //TODO: 引擎输出功率随曲轴转角周期性变化，气缸数越多输出扭矩越平稳
    public EngineSubsystem(ISubsystemHost owner, String name, EngineSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
        // 单位转换（RPM -> rad/s）
        RED_LINE_SPEED = attr.staticAttribute.redLineRpm * Math.PI / 30.0;//红线转速(rad/s)
        MAX_TORQUE_SPEED = attr.staticAttribute.maxTorqueRpm * Math.PI / 30.0;//最大扭矩转速(rad/s)
        IDLE_SPEED = attr.staticAttribute.idleRpm * Math.PI / 30.0;//怠速转速(rad/s)
        MAX_TORQUE = attr.staticAttribute.maxTorque;
        setRotSpeed((float) (IDLE_SPEED + 1));
        double minThrottle = 1.005 * calculateDampingTorque(IDLE_SPEED) / calculateMaxTorque(IDLE_SPEED);
        MIN_IDLE_THROTTLE = Math.min(minThrottle, 1f);
        coupleTorquePD = new PDController(
                1.5 * attr.getStaticAttribute().getInertia(), //kp
                0.5 * attr.getStaticAttribute().getInertia(), //kd
                1.0 / getPhysicsLevel().getTps() //step
        );
        if (attr.getStaticAttribute().workingStates.isEmpty() && getLevel().isClientSide())
            attr.getStaticAttribute().createSounds();
    }

    @Override
    public void onTick() {
        super.onTick();
        //根据转速和油门播放声音
        Level level = getSubPart().getLevel();
        if (level.isClientSide() && this.isActive()) {
            double rotSpeed = getRotSpeed();
            sinceLastSoundUpdate++;
            WorkingState bestState = attr.getBestMatchWorkingState(
                    Math.abs(30 * rotSpeed / Math.PI), Math.abs(throttleInput));
            if (bestState != null) {
                if ((bestState != currentState && sinceLastSoundUpdate > 8) || sinceLastSoundUpdate > 30) {
                    currentState = bestState;
                    sinceLastSoundUpdate = 0;
                    currentSoundUUID = transitionSound(level, currentSoundUUID,
                            SoundEvent.createFixedRangeEvent(bestState.sound(), 64f),
                            SoundSource.PLAYERS, 8, 8);
                }
            } else {
                MachineMax.LOGGER.debug("No working state found for rpm: {}, throttleInput: {}", rotSpeed * Math.PI / 30, throttleInput);
                currentState = null;
            }
        } else currentState = null;
    }

    @Override
    public void onPrePhysicsTick() {
        Object speedFeedback = EmptySignal.INSTANCE;
        for (Map.Entry<ISignalSender, Object> entry : getSignalChannel("speed_feedback").entrySet()) {
            if (entry.getValue() instanceof EmptySignal || entry.getValue() instanceof Float) {
                speedFeedback = entry.getValue();
            }
            break;
        }
        if (speedFeedback instanceof EmptySignal) throttleInput = MIN_IDLE_THROTTLE; // 空挡时松油门
        else updateThrottleInput(); // 获取并钳位油门输入（自动维持怠速）
        double rotSpeed = getRotSpeed();
        if (rotSpeed / IDLE_SPEED < 1.05) throttleInput = Math.clamp(throttleInput, MIN_IDLE_THROTTLE, 1);
        else throttleInput = Math.clamp(throttleInput, 0, 1);
        // 计算发动机输出扭矩
        double engineTorque = throttleInput * calculateMaxTorque(rotSpeed);//输出扭矩
        if (!isActive()) engineTorque = 0.0;
        double dampingTorque = calculateDampingTorque(rotSpeed);
        double netTorque = engineTorque - dampingTorque;
        if (speedFeedback instanceof EmptySignal) {
            //挂空挡时，全部输出用于改变发动机转速
            if (!getSubPart().level.isClientSide()) { //与转动惯量属性挂钩的转速改变量，客户端计算结果不精确，不应用
                rotSpeed += netTorque / attr.staticAttribute.inertia / getPhysicsLevel().getTps();
                rotSpeed = 0.995 * rotSpeed + 0.005 * IDLE_SPEED;//额外修正
                setRotSpeed((float) rotSpeed);
            }
            sendSignalToAllTargets("power", EmptySignal.INSTANCE);//空挡不输出功率
            attr.rpmOutputTargets.keySet().forEach(target -> sendSignalToAllTargets(target, getRotSpeed()));//输出转速
        } else if (speedFeedback instanceof Float feedback) {
            feedback = -feedback; // 修正方向
            double speedDiff = rotSpeed - feedback;
            double coupleTorque = Math.abs(speedDiff) < 5 ? Math.clamp(
                    this.isActive() ? this.coupleTorquePD.step(0, Math.abs(speedDiff) < 10 ? speedDiff * speedDiff / 10 : speedDiff) : 0,
                    -0.25 * attr.getStaticAttribute().maxTorque,
                    0.25 * attr.getStaticAttribute().maxTorque
            ) : 0; // 使用耦合扭矩补偿转速差，考虑饱和模拟打滑
            //有转速反馈信号时，根据转速反馈信号控制引擎转速
            if (!getSubPart().level.isClientSide()) { //与转动惯量属性挂钩的转速改变量，客户端计算结果不精确，不应用
                rotSpeed += (netTorque - coupleTorque) / attr.staticAttribute.inertia / getPhysicsLevel().getTps();
                rotSpeed = Math.clamp(rotSpeed, 0.1 * IDLE_SPEED, RED_LINE_SPEED * 2);
                rotSpeed = 0.95 * Math.clamp(rotSpeed, 0.1 * IDLE_SPEED, RED_LINE_SPEED * 1.05) + 0.05 * feedback; // 额外修正
                setRotSpeed((float) rotSpeed);
            }
            sendSignalToAllTargets("power", new MechPowerSignal((float) ((netTorque + coupleTorque) * rotSpeed), (float) rotSpeed));//输出功率信号
            attr.rpmOutputTargets.keySet().forEach(target -> sendSignalToAllTargets(target, getRotSpeed()));//输出转速信号
        } else {
            //没有转速反馈信号时，直接取用引擎转速
            rotSpeed += netTorque / (7 * attr.staticAttribute.inertia) / getPhysicsLevel().getTps();
            rotSpeed = Math.max(rotSpeed, 0.8 * IDLE_SPEED);
            if (!isActive()) rotSpeed = 0;
            sendSignalToAllTargets("power", new MechPowerSignal((float) (netTorque * rotSpeed), (float) rotSpeed));
            setRotSpeed((float) rotSpeed);
            attr.rpmOutputTargets.keySet().forEach(target -> sendSignalToAllTargets(target, getRotSpeed()));//输出转速
        }
    }

    @Override
    public void onAttach() {
        super.onAttach();
        sendSignalToTarget("power", attr.getPowerOutputTarget(), MechPowerSignal.ZERO);//发送握手信号建立转速反馈链接
    }

    @Override
    public void onVehicleStructureChanged() {
        super.onVehicleStructureChanged();
        sendSignalToTarget("power", attr.getPowerOutputTarget(), MechPowerSignal.ZERO);//发送握手信号建立转速反馈链接
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
        result.put("power", List.of(attr.powerOutputTarget));
        result.putAll(attr.rpmOutputTargets);
        return result;
    }

    /**
     * 获取声源的实时位置
     *
     * <p>此方法在每个游戏tick都会被调用，用于更新声音波面的发射源位置。
     * 返回的位置将作为声音传播的起点，声音会从此位置以音速向外传播。</p>
     *
     * <p>实现注意事项：</p>
     * <ul>
     *   <li>应返回当前帧声源在世界中的精确位置</li>
     *   <li>位置变化应平滑，避免剧烈跳跃</li>
     *   <li>对于移动声源，建议返回质心或主要发声部位的位置</li>
     * </ul>
     *
     * @param uuid  声源的UUID
     * @param event 当前播放的声音事件
     * @return 声源在当前游戏刻的三维世界坐标，单位：方块
     */
    @Override
    public @NotNull Vec3 getPosition(UUID uuid, SoundEvent event) {
        return SparkMathKt.toVec3(getSubPart().getPosition());
    }

    @Override
    public float getVolume(UUID uuid, SoundEvent event) {
        if (currentState != null && !getSubPart().isRemoved()) {
            return 1f;
        } else return 0f;
    }

    @Override
    public float getPitch(UUID uuid, SoundEvent event) {
        if (currentState != null) {
            double rpm = Math.max(Math.abs(30 * getRotSpeed() / Math.PI), 0.5 * attr.getStaticAttribute().getIdleRpm());
            double rpmRatio = rpm / currentState.rpm();
            return (float) Math.clamp(rpmRatio, 0.5, 2);
        } else return 1f;
//        return 1f;
    }

}
