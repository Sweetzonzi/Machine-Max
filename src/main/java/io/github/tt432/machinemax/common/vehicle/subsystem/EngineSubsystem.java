package io.github.tt432.machinemax.common.vehicle.subsystem;

import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.vehicle.ISubsystemHost;
import io.github.tt432.machinemax.common.vehicle.Part;
import io.github.tt432.machinemax.common.vehicle.attr.subsystem.EngineSubsystemAttr;
import io.github.tt432.machinemax.common.vehicle.signal.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EngineSubsystem extends AbstractSubsystem implements ISignalReceiver, ISignalSender {
    public final EngineSubsystemAttr attr;
    public final float MAX_ROT_SPEED;//最大转速(rad/s)
    public final float BASE_ROT_SPEED;//怠速转速(rad/s)
    public final float MIN_IDLE_THROTTLE;//怠速转速下的最小油门
    public float rotSpeed = 0;//当前转速(rad/s)

    public EngineSubsystem(ISubsystemHost owner, String name, EngineSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
        MAX_ROT_SPEED = attr.maxRpm * 2 * 3.14159265358979323846f / 60;
        BASE_ROT_SPEED = attr.baseRpm * 2 * 3.14159265358979323846f / 60;
        MIN_IDLE_THROTTLE = attr.damping * BASE_ROT_SPEED * BASE_ROT_SPEED //阻力矩的功率
                / (BASE_ROT_SPEED / MAX_ROT_SPEED * attr.maxPower) * 1; //怠速转速下的最大功率
    }

    @Override
    public void onPhysicsTick() {
        super.onPhysicsTick();
        float throttleInput = Math.clamp(getThrottleInput(), MIN_IDLE_THROTTLE, 1);
        float enginePower;
        if (signalInputs.containsKey(attr.speedFeedbackInputKey) &&
                signalInputs.get(attr.speedFeedbackInputKey).getFirst() instanceof EmptySignal) {
            //挂空挡时，全部输出用于改变发动机转速
            send("power", new EmptySignal());//空挡不输出功率
            rotSpeed = Math.max(rotSpeed, BASE_ROT_SPEED / 2);
            float torque = getEnginePower(throttleInput) / rotSpeed - attr.damping * rotSpeed;//总扭矩
            rotSpeed += torque / attr.inertia * 1 / 60f;
        } else if (signalInputs.containsKey(attr.speedFeedbackInputKey) &&
                signalInputs.get(attr.speedFeedbackInputKey).getFirst() instanceof Float rotSpeedFeedback) {
            //有转速反馈信号时，根据转速反馈信号控制引擎转速
            rotSpeed = Math.max(rotSpeedFeedback, BASE_ROT_SPEED / 2);
            enginePower = getEnginePower(throttleInput) - attr.damping * rotSpeed * rotSpeed;
            send("power", enginePower);//输出功率
        } else {
            //没有转速反馈信号时，直接取用引擎转速
            rotSpeed = Math.max(rotSpeed, BASE_ROT_SPEED / 2);
            enginePower = getEnginePower(throttleInput);
            float torque = enginePower / rotSpeed - attr.damping * rotSpeed;//总扭矩
            enginePower = torque * rotSpeed;//输出功率
            rotSpeed += torque / attr.inertia * 1 / 60f;//更新转速
            send("power", enginePower);//输出功率
        }
        //TODO:根据转速播放声音
        MachineMax.LOGGER.info("Engine power: {} W, rot speed: {} rpm",
                signalOutputs.get("power").getFirst(), rotSpeed * 60 / 2 / 3.14159265358979323846f);
    }

    /**
     * 获取油门信号，控制油门开度进而控制发动机输出功率
     *
     * @return 油门开度，0~1
     */
    private float getThrottleInput() {
        float powerControlInput = -1;
        for (String inputKey : attr.throttleInputKeys) {
            Signals signals = signalInputs.getOrDefault(inputKey, new Signals());
            if (signals.getFirst() instanceof Float) {
                powerControlInput = (float) signals.getFirst();
                break;
            } else if (signals.getFirst() instanceof MoveInputSignal) {
                powerControlInput = ((MoveInputSignal) signals.getFirst()).getMoveInput()[0] / 100f;
                break;
            }
        }
        return Math.clamp(powerControlInput, 0, 1);
    }

    /**
     * 计算引擎输出功率(不计内部阻力)
     *
     * @param throttle 油门开度，0~1
     * @return 引擎输出功率(W)
     */
    private float getEnginePower(float throttle) {
        float power = rotSpeed / MAX_ROT_SPEED * throttle * attr.maxPower;
        if (rotSpeed > BASE_ROT_SPEED)
            power = (float) (power * Math.pow(2.7, -(rotSpeed - BASE_ROT_SPEED) / BASE_ROT_SPEED));
        return power;
    }

    @Override
    public Map<String, List<String>> getTargetNames() {
        Map<String, List<String>> result = new HashMap<>(2);
        result.put("power", List.of(attr.powerOutputTarget));
        for (Map.Entry<String, String> entry : attr.rpmOutputTargets.entrySet()) {
            result.put(entry.getKey(), List.of(entry.getValue()));
        }
        return result;
    }

}
