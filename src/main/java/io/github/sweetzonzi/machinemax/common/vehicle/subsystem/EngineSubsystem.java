package io.github.sweetzonzi.machinemax.common.vehicle.subsystem;

import io.github.sweetzonzi.machinemax.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machinemax.common.vehicle.attr.subsystem.EngineSubsystemAttr;
import io.github.sweetzonzi.machinemax.common.vehicle.signal.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EngineSubsystem extends AbstractSubsystem {
    public final EngineSubsystemAttr attr;
    public final double MAX_ROT_SPEED;//最大转速(rad/s)
    public final double MAX_TORQUE_SPEED;//最大扭矩转速(rad/s)
    public final double BASE_ROT_SPEED;//怠速转速(rad/s)
    public final double MAX_TORQUE;//最大扭矩(N·m)
    public final double MIN_IDLE_THROTTLE;//怠速转速下的最小油门
    public double rotSpeed;//当前转速(rad/s)
    public double throttleInput;//当前油门输入（0~1）

    public EngineSubsystem(ISubsystemHost owner, String name, EngineSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
        // 单位转换（RPM -> rad/s）
        MAX_ROT_SPEED = attr.maxRpm * Math.PI / 30.0;
        MAX_TORQUE_SPEED = attr.maxTorqueRpm * Math.PI / 30.0;
        BASE_ROT_SPEED = attr.baseRpm * Math.PI / 30.0;
        // 计算最大扭矩（基于最大功率点公式 P_max = T_max * ω）
        MAX_TORQUE = attr.maxPower / MAX_TORQUE_SPEED;
        rotSpeed = BASE_ROT_SPEED + 5;
        double minThrottle = 1.02 * calculateDampingTorque(BASE_ROT_SPEED) / calculateMaxTorque(BASE_ROT_SPEED);
        MIN_IDLE_THROTTLE = Math.min(minThrottle, 1f);
    }

    @Override
    public void onTick() {
        super.onTick();
        //TODO:根据转速和油门播放声音
    }

    @Override
    public void onPrePhysicsTick() {
        // 获取并钳位油门输入（自动维持怠速）
        updateThrottleInput();
        if (rotSpeed / BASE_ROT_SPEED < 1.05) throttleInput = Math.clamp(throttleInput, MIN_IDLE_THROTTLE, 1);
        else throttleInput = Math.clamp(throttleInput, 0, 1);

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
        if (speedFeedback instanceof EmptySignal) {
            //挂空挡时，全部输出用于改变发动机转速
            rotSpeed += netTorque / attr.inertia / 60f;
            rotSpeed = Math.max(0.95 * rotSpeed + 0.05 * BASE_ROT_SPEED, 0.1 * BASE_ROT_SPEED);
            if (!isActive()) rotSpeed = 0;
            sendSignalToAllTargets("power", new EmptySignal());//空挡不输出功率
            attr.rpmOutputTargets.keySet().forEach(target -> sendSignalToAllTargets(target, (float) rotSpeed));//输出转速
        } else if (speedFeedback instanceof Float feedback) {
            //有转速反馈信号时，根据转速反馈信号控制引擎转速
            feedback = -feedback;
            //TODO:如何和转动惯量属性挂钩？
            rotSpeed = 0.95 * rotSpeed + 0.05 * feedback;
            rotSpeed = Math.max(rotSpeed, 0.1 * BASE_ROT_SPEED);
            if (!isActive()) rotSpeed = 0;
            sendSignalToAllTargets("power", new MechPowerSignal((float) (netTorque * rotSpeed), (float) rotSpeed));//输出功率信号
            attr.rpmOutputTargets.keySet().forEach(target -> sendSignalToAllTargets(target, (float) rotSpeed));//输出转速信号
        } else {
            //没有转速反馈信号时，直接取用引擎转速
            rotSpeed += netTorque / (7 * attr.inertia) / 60f;
            rotSpeed = Math.max(rotSpeed, 0.8 * BASE_ROT_SPEED);
            if (!isActive()) rotSpeed = 0;
            sendSignalToAllTargets("power", new MechPowerSignal((float) (netTorque * rotSpeed), (float) rotSpeed));
            attr.rpmOutputTargets.keySet().forEach(target -> sendSignalToAllTargets(target, (float) rotSpeed));//输出转速
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
        if (rotSpeed <= 0 || !isActive()) return result;
        else if (rotSpeed <= BASE_ROT_SPEED) {
            result = rotSpeed / BASE_ROT_SPEED * MAX_TORQUE / 3;
        } else if (rotSpeed <= MAX_TORQUE_SPEED) {//线性上升段：怠速 -> 最大扭矩转速
            double k = 2f / 3f * MAX_TORQUE;
            result = MAX_TORQUE / 3f + (rotSpeed - BASE_ROT_SPEED) / MAX_TORQUE_SPEED * k;
        } else if (rotSpeed <= MAX_ROT_SPEED) {//全功率段
            result = attr.maxPower / rotSpeed;
        } else { //超速时动力大幅衰减
            result = Math.pow(2.7, -2.5 * (rotSpeed - MAX_ROT_SPEED) / BASE_ROT_SPEED) * attr.maxPower / rotSpeed;
        }
        result *= 0.3 + 0.7 * Math.sqrt(durability / attr.basicDurability);
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
        for (int i = 0; i < attr.dampingFactors.size(); i++) {
            result += attr.dampingFactors.get(i) * Math.pow(Math.abs(rotSpeed), i + 1);
        }
        return Math.signum(rotSpeed) * result;
    }

    /**
     * 获取油门信号，控制油门开度进而控制发动机输出功率
     */
    private void updateThrottleInput() {
        double powerControlInput = -1;
        for (String inputKey : attr.throttleInputKeys) {
            SignalChannel signalChannel = getSignalChannel(inputKey);
            Object signal = signalChannel.getFirstSignal();
            if (signal instanceof Float) {
                powerControlInput = (float) signalChannel.getFirstSignal();
                break;
            } else if (signal instanceof MoveInputSignal) {
                powerControlInput = Math.abs(((MoveInputSignal) signalChannel.getFirstSignal()).getMoveInput()[2] / 100f);
                break;
            }
        }
        throttleInput = powerControlInput;
    }

    @Override
    public Map<String, List<String>> getTargetNames() {
        Map<String, List<String>> result = new HashMap<>(2);
        result.put("power", List.of(attr.powerOutputTarget));
        result.putAll(attr.rpmOutputTargets);
        return result;
    }

}
