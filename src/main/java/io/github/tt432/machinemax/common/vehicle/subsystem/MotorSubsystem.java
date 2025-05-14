package io.github.tt432.machinemax.common.vehicle.subsystem;

import io.github.tt432.machinemax.common.vehicle.ISubsystemHost;
import io.github.tt432.machinemax.common.vehicle.attr.subsystem.MotorSubsystemAttr;
import io.github.tt432.machinemax.common.vehicle.signal.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MotorSubsystem extends AbstractSubsystem{
    public final MotorSubsystemAttr attr;
    public double rotSpeed;//当前转速(rad/s)
    public double throttleInput;//当前油门输入（0~1）

    public MotorSubsystem(ISubsystemHost owner, String name, MotorSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
    }

    @Override
    public void onTick() {
        super.onTick();
        //TODO:根据转速和油门播放声音
    }

    @Override
    public void onPrePhysicsTick() {
        updateThrottleInput();
        double engineTorque = throttleInput * calculateMaxTorque(rotSpeed);//输出扭矩
        double dampingTorque = calculateDampingTorque(rotSpeed);
        double netTorque = engineTorque - dampingTorque;
        Object speedFeedback = null;
        for (Map.Entry<ISignalSender, Object> entry: getSignalChannel("speed_feedback").entrySet()){
            if (entry.getValue() instanceof EmptySignal || entry.getValue() instanceof Float){
                speedFeedback = entry.getValue();
            }
            break;
        }
        if (speedFeedback instanceof EmptySignal) {
            //挂空挡时，全部输出用于改变发动机转速
            rotSpeed += netTorque / attr.inertia / 60f;
            sendSignalToAllTargets("power", new EmptySignal());//空挡不输出功率
            attr.rpmOutputTargets.keySet().forEach(target -> sendSignalToAllTargets(target, (float) rotSpeed));//输出转速
        } else if (speedFeedback instanceof Float feedback) {
            //有转速反馈信号时，根据转速反馈信号控制引擎转速
            feedback = -feedback;
            //TODO:如何和转动惯量属性挂钩？
            rotSpeed = 0.95 * rotSpeed + 0.05 * feedback;
            sendSignalToAllTargets("power", new MechPowerSignal((float) (netTorque * rotSpeed), (float) rotSpeed));//输出功率
            attr.rpmOutputTargets.keySet().forEach(target -> sendSignalToAllTargets(target, (float) rotSpeed));//输出转速
        } else {
            //没有转速反馈信号时，直接取用引擎转速
            rotSpeed += netTorque / (7 * attr.inertia) / 60f;
            sendSignalToAllTargets("power", new MechPowerSignal((float) (netTorque * rotSpeed), (float) rotSpeed));
            attr.rpmOutputTargets.keySet().forEach(target -> sendSignalToAllTargets(target, (float) rotSpeed));//输出转速
        }
    }

    /**
     * 计算给定转速下的扭矩
     *
     * @param rotSpeed 转速(rad/s)
     * @return 当前转速下的最大扭矩(N · m)
     */
    private double calculateMaxTorque(double rotSpeed) {
        if (rotSpeed >=0)
            return attr.maxPower / Math.max(rotSpeed, 0.1f);
        else
            return attr.maxPower / Math.min(rotSpeed, -0.1f);
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
            result += attr.dampingFactors.get(i) * Math.pow(Math.abs(rotSpeed), i);
        }
        return Math.signum(rotSpeed) * result;
    }

    /**
     * 获取油门信号，控制油门开度进而控制发动机输出功率
     */
    private void updateThrottleInput() {
        double powerControlInput = 0;
        for (String inputKey : attr.throttleInputKeys) {
            SignalChannel signalChannel = getSignalChannel(inputKey);
            if (signalChannel.getFirst() instanceof Float) {
                powerControlInput = (float) signalChannel.getFirst();
                break;
            } else if (signalChannel.getFirst() instanceof MoveInputSignal) {
                powerControlInput = Math.abs(((MoveInputSignal) signalChannel.getFirst()).getMoveInput()[2] / 100f);
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
