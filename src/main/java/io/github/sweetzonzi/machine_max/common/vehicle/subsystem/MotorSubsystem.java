package io.github.sweetzonzi.machine_max.common.vehicle.subsystem;

import cn.solarmoon.spark_core.sound.ISoundSpreader;
import cn.solarmoon.spark_core.util.SparkMathKt;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.WorkingState;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.dynamic_attr.MotorSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.static_attr.MotorSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.signal.*;
import lombok.Getter;
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

@Getter
public class MotorSubsystem extends AbstractSubsystem implements ISoundSpreader {
    public final MotorSubsystemAttr attr;
    protected static final EntityDataAccessor<Float> ROT_SPEED_ID = SynchedEntityData.defineId(MotorSubsystem.class, EntityDataSerializers.FLOAT);
    public double throttleInput;//当前电门输入（-1~1）
    private WorkingState currentState = null;//当前引擎工况及对应音效
    private UUID currentSoundUUID = UUID.randomUUID();
    private int sinceLastSoundUpdate = 0;
    // 状态记录变量
    private double lastRotSpeed = 0;
    private double lastNetTorque = 0;

    public MotorSubsystem(ISubsystemHost owner, String name, MotorSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
    }

    @Override
    public void onTick() {
        super.onTick();
        //根据转速和油门播放声音
        Level level = getSubPart().getLevel();
        if (level.isClientSide() && this.isActive()) {
            sinceLastSoundUpdate++;
            float rotSpeed = getRotSpeed();
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
                MachineMax.LOGGER.debug("No working state found for rpm: {}, throttleInput: {}", Math.abs(30 * rotSpeed / Math.PI), throttleInput);
                currentState = null;
            }
        } else currentState = null;
    }

    @Override
    public void onPrePhysicsTick() {
        updateThrottleInput();
        double rotSpeed = getRotSpeed();
        //TODO:电门输入与转速方向相反时，发电模式
        double engineTorque = throttleInput * calculateMaxTorque(rotSpeed);//输出扭矩
        double dampingTorque = calculateDampingTorque(rotSpeed);
        double netTorque = engineTorque - dampingTorque;
        Object speedFeedback = null;
        for (Map.Entry<ISignalSender, Object> entry : getSignalChannel("speed_feedback").entrySet()) {
            if (entry.getValue() instanceof EmptySignal || entry.getValue() instanceof Float) {
                speedFeedback = entry.getValue();
            }
            break;
        }
        // 计算角加速度并更新外部阻力矩估计
        double estimatedExternalTorque = estimatedExternalTorque(rotSpeed);
        // 记录当前状态供下一tick使用
        lastRotSpeed = rotSpeed;
        lastNetTorque = netTorque;
        if (speedFeedback instanceof EmptySignal) {
            //挂空挡时，全部输出用于改变发动机转速
            if (!getSubPart().level.isClientSide()) //与转动惯量属性挂钩的转速改变量，客户端计算结果不精确，不应用
                rotSpeed += netTorque / attr.staticAttribute.inertia / 60f;
            lastRotSpeed = rotSpeed;
            sendSignalToTarget("power", attr.getPowerOutputTarget(), EmptySignal.INSTANCE);//空挡不输出功率
            setRotSpeed((float) rotSpeed);
            attr.rpmOutputTargets.keySet().forEach(target -> sendSignalToAllTargets(target, getRotSpeed()));//输出转速
        } else if (speedFeedback instanceof Float feedback) {
            //有转速反馈信号时，根据转速反馈信号控制引擎转速
            feedback = -feedback;
            if (!getSubPart().level.isClientSide()) //与转动惯量属性挂钩的转速改变量，客户端计算结果不精确，不应用
                rotSpeed += (netTorque + estimatedExternalTorque) / attr.staticAttribute.inertia / 60f;
            rotSpeed = 0.95 * rotSpeed + 0.05 * feedback; //额外修正
            rotSpeed = Math.clamp(rotSpeed, -attr.staticAttribute.redLineRPM * Math.PI / 30, attr.staticAttribute.redLineRPM * Math.PI / 30);
            if (!isActive()) {
                rotSpeed = feedback;
                netTorque = 0;
            }
            sendSignalToTarget("power", attr.getPowerOutputTarget(), new MechPowerSignal((float) (netTorque * rotSpeed), (float) rotSpeed));//输出功率
            setRotSpeed((float) rotSpeed);
            attr.rpmOutputTargets.keySet().forEach(target -> sendSignalToAllTargets(target, getRotSpeed()));//输出转速
        } else {
            //没有转速反馈信号时，直接取用引擎转速
            rotSpeed += netTorque / (7 * attr.staticAttribute.inertia) / 60f;
            sendSignalToTarget("power", attr.getPowerOutputTarget(), new MechPowerSignal((float) (netTorque * rotSpeed), (float) rotSpeed));
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
     * 计算给定转速下的扭矩
     *
     * @param rotSpeed 转速(rad/s)
     * @return 当前转速下的最大扭矩(N · m)
     */
    private double calculateMaxTorque(double rotSpeed) {
        double result = 0;
        if (!isActive()) return result;
        result = Math.min(attr.staticAttribute.maxPower / Math.max(Math.abs(rotSpeed), 0.1f), attr.staticAttribute.maxTorque);
        result *= 0.3 + 0.7 * Math.sqrt(getDurability() / getMaxDurability());
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
        if (Math.abs(rotSpeed) <= MotorSubsystemStaticAttr.baseRPM / Math.PI * 30) return result;
        for (int i = 0; i < attr.staticAttribute.dampingFactors.size(); i++) {
            result += attr.staticAttribute.dampingFactors.get(i) * Math.pow(Math.abs(rotSpeed), i);
        }
        return Math.signum(rotSpeed) * result;
    }

    private double estimatedExternalTorque(double rotSpeed) {
        double estimatedExternalTorque = 0;
        if (!getSubPart().level.isClientSide()) {//与转动惯量属性挂钩的转速改变量，客户端计算结果不精确，不应用
            // 计算角加速度并更新外部阻力矩估计
            double angularAcceleration = (rotSpeed - lastRotSpeed) * 60;
            // 根据转动定律：总扭矩 = 转动惯量 * 角加速度
            double totalTorque = angularAcceleration * attr.staticAttribute.inertia;
            // 外部阻力矩 = 总扭矩 - 上一tick的净扭矩
            estimatedExternalTorque = totalTorque - lastNetTorque;
            // 使用低通滤波器平滑估计值，避免突变
            estimatedExternalTorque = 0.7 * estimatedExternalTorque + 0.3 * (totalTorque - lastNetTorque);
        }
        return estimatedExternalTorque;
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
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> dataAccessor) {
        super.onSyncedDataUpdated(dataAccessor);
//        if (dataAccessor == ROT_SPEED_ID && getSubPart().level.isClientSide) {
//            MachineMax.LOGGER.debug("RotSpeed: {}", getRotSpeed());
//        }
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
            if (Math.abs(30 * getRotSpeed() / Math.PI) > MotorSubsystemStaticAttr.baseRPM)
                return 1f;
            else
                return (float) (0.5f + 0.5f * (Math.abs(30 * getRotSpeed() / Math.PI) / MotorSubsystemStaticAttr.baseRPM));
        } else return 0f;
    }

    @Override
    public float getPitch(UUID uuid, SoundEvent event) {
        if (currentState != null) {
            double rpm = Math.max(Math.abs(30 * getRotSpeed() / Math.PI), 0.5 * MotorSubsystemStaticAttr.baseRPM);
            double rpmRatio = rpm / currentState.rpm();
            return (float) Math.clamp(rpmRatio, 0.5, 2);
        } else return 1f;
    }
}
