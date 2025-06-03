package io.github.sweetzonzi.machinemax.common.vehicle.subsystem;

import com.jme3.bullet.joints.New6Dof;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machinemax.common.entity.MMPartEntity;
import io.github.sweetzonzi.machinemax.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machinemax.common.vehicle.VehicleCore;
import io.github.sweetzonzi.machinemax.common.vehicle.attr.subsystem.CarControllerSubsystemAttr;
import io.github.sweetzonzi.machinemax.common.vehicle.connector.SpecialConnector;
import io.github.sweetzonzi.machinemax.common.vehicle.signal.*;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.*;

@Getter
public class CarControllerSubsystem extends AbstractSubsystem {
    public final CarControllerSubsystemAttr attr;
    public byte[] moveInput;
    public byte[] moveInputConflict;
    public float speed;
    private final Map<ISignalReceiver, Float> overrideCountDown = new HashMap<>();

    float avgEngineMaxSpeed = 0f;
    float avgEngineMaxTorqueSpeed = 0f;
    int engineCount = 0;

    private final Map<ISignalReceiver, String> engines = new HashMap<>();//控制的发动机其接收控制的信号频道映射 Control engine and its receiving signal channel mapping
    private final Map<ISignalReceiver, String> gearboxes = new HashMap<>();//控制的变速箱其接收控制的信号频道映射 Control gearbox and its receiving signal channel mapping
    private final Map<ISignalReceiver, String> wheels = new HashMap<>();//控制的车轮其接收控制的信号频道映射 Control wheel and its receiving signal channel mapping

    public boolean handBrakeControl = true;
    public float actualThrottle = 0f;
    public float actualBrake = 0f;
    public float actualHandBrake = 0f;
    public float actualSteering = 0f;

    public CarControllerSubsystem(ISubsystemHost owner, String name, CarControllerSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
    }

    @Override
    public void onTick() {
        super.onTick();
        if (getPart().level.isClientSide) {
            if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.getVehicle() instanceof MMPartEntity entity && entity.part.vehicle == this.getPart().vehicle) {
                String gear = "NO GEARBOX";
                String handBrake = handBrakeControl ? "ENGAGED" : "OFF";
                for (ISignalReceiver gbx : gearboxes.keySet()) {
                    GearboxSubsystem gearbox = (GearboxSubsystem) gbx;
                    gear = gearbox.gearNames.get(gearbox.getCurrentGear());
                    if (!gearbox.isClutched() || gearbox.getRemainingSwitchTime() > 0.0f) gear = "N";
                    break;
                }
                Object engineSpeed = getPart().vehicle.subSystemController.getSignalChannel("engine_speed").getFirstSignal();
                float engineRPM = engineSpeed instanceof Float f ? (float) (f / Math.PI * 30f) : 0.0f;
                Minecraft.getInstance().player.displayClientMessage(Component.empty().append("Hand brake: " + handBrake + " Gear: " + gear + " Speed: " + String.format("%.1f", getPart().vehicle.getVelocity().length() * 3.6f) + " km/h RPM: " + String.format("%.0f", engineRPM)).withColor(engineRPM > 6500 ? 0xff0000 : 0xffffff), true);
            }
        }
        updateMoveInputs();
        this.speed = -getPart().rootSubPart.body.getLinearVelocityLocal(null).z;
        if (active) {
            //自动换档与手刹车冷却时间 Auto gear shift cooldown
            for (Map.Entry<ISignalReceiver, Float> entry : overrideCountDown.entrySet()) {
                if (entry.getValue() > 0) {
                    entry.setValue(entry.getValue() - 0.05f);
                }
            }
        }
    }

    @Override
    public void onPrePhysicsTick() {
        super.onPrePhysicsTick();
        if (active && getPart().vehicle.mode == VehicleCore.ControlMode.GROUND) {
            //更新受灵敏度影响的实际控制量，油门与刹车控制在分发控制信号时进行
            if (this.moveInput != null) {
                actualSteering = actualSteering * (1 - attr.steeringSensitivity) + (moveInput[4]) * attr.steeringSensitivity;
            }
            actualHandBrake = actualHandBrake * (1 - attr.handBrakeSensitivity) + (handBrakeControl ? 1 : 0) * attr.handBrakeSensitivity;
            distributeControlSignals();
        } else resetSignalOutputs();
    }

    /**
     * 每当载具结构发生变化时，发送空信号，根据回调重新建立连接<p>
     * Every time the vehicle structure changes, send an empty signal, and reestablish connections based on callbacks.
     *
     * @see CarControllerSubsystem#onSignalUpdated(String signalKey, ISignalSender sender)
     */
    @Override
    public void onVehicleStructureChanged() {
        super.onVehicleStructureChanged();
        clearCallbackChannel();
        for (String signalKey : attr.engineControlOutputTargets.keySet()) {
            sendSignalToAllTargetsWithCallback(signalKey, new EmptySignal(),  false);
        }
        for (String signalKey : attr.wheelControlOutputTargets.keySet()) {
            sendSignalToAllTargetsWithCallback(signalKey, new EmptySignal(),  false);
        }
        for (String signalKey : attr.gearboxControlOutputTargets.keySet()) {
            sendSignalToAllTargetsWithCallback(signalKey, new EmptySignal(),  false);
        }
    }

    /**
     * 每当载具结构发生变化时，发送空信号，根据回调重新建立连接<p>
     * Every time the vehicle structure changes, send an empty signal, and reestablish connections based on callbacks.
     *
     * @see CarControllerSubsystem#onVehicleStructureChanged()
     */
    @Override
    public void onSignalUpdated(String channelName, ISignalSender sender) {
        Object signalValue = getSignalChannel(channelName).get(sender);
        if (this.isActive()) {
            if (channelName.equals("callback") && signalValue instanceof String controlChannel) {
                if (sender instanceof WheelDriverSubsystem wheel) {
                    if (wheel.getPart().vehicle != this.getPart().vehicle) wheels.remove(wheel);
                    else {
                        wheels.put(wheel, controlChannel);
                        addCallbackTarget(controlChannel, wheel);
                    }
                } else if (sender instanceof EngineSubsystem engine) {
                    if (engine.getPart().vehicle != this.getPart().vehicle) this.engines.remove(engine);
                    else {
                        engines.put(engine, controlChannel);
                        addCallbackTarget(controlChannel, engine);
                        //计算引擎最大转速和最大扭矩转速的平均值 Calculate the average maximum speed and max torque speed of the engine
                        avgEngineMaxTorqueSpeed = 0;
                        avgEngineMaxSpeed = 0;
                        for (Map.Entry<ISignalReceiver, String> entry : engines.entrySet()) {
                            avgEngineMaxTorqueSpeed += ((EngineSubsystem) entry.getKey()).attr.maxTorqueRpm;
                            avgEngineMaxSpeed += ((EngineSubsystem) entry.getKey()).attr.maxRpm;
                        }
                        engineCount = engines.size();
                        if (engineCount > 0) {
                            avgEngineMaxTorqueSpeed = (float) (avgEngineMaxTorqueSpeed * Math.PI / engineCount / 30f);
                            avgEngineMaxSpeed = (float) (avgEngineMaxSpeed * Math.PI / engineCount / 30f);
                        } else {
                            avgEngineMaxTorqueSpeed = 0f;
                            avgEngineMaxSpeed = 0f;
                        }
                    }
                } else if (sender instanceof GearboxSubsystem gearbox) {
                    if (gearbox.getPart().vehicle != this.getPart().vehicle) {
                        this.gearboxes.remove(gearbox);
                        overrideCountDown.remove(gearbox);
                    } else {
                        gearboxes.put(gearbox, controlChannel);
                        addCallbackTarget(controlChannel, gearbox);
                        overrideCountDown.put(gearbox, 0f);
                    }
                }
            } else if (signalValue instanceof RegularInputSignal regularInputSignal) {//处理按键输入 Handle key input
                int tickCount = regularInputSignal.getInputTickCount();
                switch (regularInputSignal.getInputType()) {
                    case CLUTCH:
                        for (ISignalReceiver gearbox : gearboxes.keySet()) {
                            overrideCountDown.put(gearbox, 1f);//手动操作后一段时间内不自动切换 Clutch for a period of time after manual operation
                        }
                        if (tickCount == 0) {//踩离合 Unclutch
                            for (ISignalReceiver gearbox : gearboxes.keySet())
                                ((GearboxSubsystem) gearbox).setClutched(false);
                        } else {//松离合 Clutch
                            for (ISignalReceiver gearbox : gearboxes.keySet())
                                ((GearboxSubsystem) gearbox).setClutched(true);
                        }
                        break;
                    case UP_SHIFT://升档 Shift up
                        for (ISignalReceiver gearbox : gearboxes.keySet()) {
                            overrideCountDown.put(gearbox, 3f);//手动操作后一段时间内不自动切换 Clutch for a period of time after manual operation
                        }
                        for (ISignalReceiver gearbox : gearboxes.keySet()) ((GearboxSubsystem) gearbox).upShift();
                        break;
                    case DOWN_SHIFT://降档 Shift down
                        for (ISignalReceiver gearbox : gearboxes.keySet()) {
                            overrideCountDown.put(gearbox, 3f);//手动操作后一段时间内不自动切换 Clutch for a period of time after manual operation
                        }
                        for (ISignalReceiver gearbox : gearboxes.keySet()) ((GearboxSubsystem) gearbox).downShift();
                        break;
                    case HAND_BRAKE:
                        handBrakeControl = tickCount == 0;
                        overrideCountDown.put(this, tickCount == 0 ? 9999f : 0f);
                        break;
                    case TOGGLE_HAND_BRAKE:
                        handBrakeControl = !handBrakeControl;
                        overrideCountDown.put(this, 1f);
                        break;
                    default://忽视其他输入 Ignore other inputs
                        break;
                }
            }

        }
    }

    private void updateMoveInputs() {
        byte[] moveInput = null;
        byte[] moveInputConflict = null;
        boolean hasMoveInput = false;
        for (String inputKey : attr.controlInputKeys) {//遍历输入信号 Iterate over input signalChannel
            SignalChannel signalChannel = getSignalChannel(inputKey);
            for (Object signal : signalChannel.values()) {
                if (signal instanceof MoveInputSignal moveInputSignal) {//找到移动输入信号 Find move input signal
                    moveInput = moveInputSignal.getMoveInput();
                    moveInputConflict = moveInputSignal.getMoveInputConflict();
                    hasMoveInput = true;
                    break;
                }
            }
            if (hasMoveInput) break;
        }
        if (hasMoveInput) {
            this.moveInput = moveInput;
            this.moveInputConflict = moveInputConflict;
        } else {
            this.moveInput = null;
            this.moveInputConflict = null;
        }
    }

    private void distributeControlSignals() {
        if (this.moveInput != null && moveInputConflict != null) {//前进方向有输入信号 (可为0) Have forward input signal (can be 0)
            float avgEngineSpeed = 0f;
            byte[] moveInput = this.moveInput;
            if (moveInput[2] != 0) {//前进方向输入信号不为0 Forward input signal is not 0
                if (moveInput[2] * speed > 0 || Math.abs(speed) <= 0.5f) {//加速行驶 Accelerate
                    actualThrottle = actualThrottle * (1 - attr.throttleSensitivity) + (Math.abs(moveInput[2])) * attr.throttleSensitivity;
                    actualBrake = actualBrake * (1 - 2 * attr.brakeSensitivity) + 0 * 2 * attr.brakeSensitivity;
                    for (Map.Entry<ISignalReceiver, String> entry : engines.entrySet()) {
                        sendCallbackToAllListeners(entry.getValue(), actualThrottle);
                        avgEngineSpeed += (float) ((EngineSubsystem) entry.getKey()).rotSpeed;
                    }
                    avgEngineSpeed /= engineCount;
                    //起步时自动松离合和手刹 Auto release hand brake when starting
                    if (attr.autoHandBrake && overrideCountDown.getOrDefault(this, 0f) <= 0) {
                        handBrakeControl = false;
                        overrideCountDown.put(this, 2f);
                    }
                    for (ISignalReceiver gearbox : gearboxes.keySet()) {//加速时延迟升档 Delay shifting up when accelerating
                        if (overrideCountDown.getOrDefault(gearbox, 0f) <= 0) {
                            ((GearboxSubsystem) gearbox).switchGear(autoGearShift((GearboxSubsystem) gearbox, avgEngineSpeed, 0.85f, moveInput[2]));
                            //起步时自动松离合 Auto engage clutch when starting
                            if (Math.abs(speed) <= 1f) {
                                ((GearboxSubsystem) gearbox).setClutched(true);
                            }
                        }
                    }
                } else if (moveInput[2] * speed < 0) {//减速行驶 Brake
                    actualThrottle = actualThrottle * (1 - 2 * attr.throttleSensitivity) + 0 * 2 * attr.throttleSensitivity;
                    actualBrake = actualBrake * (1 - attr.brakeSensitivity) + 1 * attr.brakeSensitivity;
                    for (Map.Entry<ISignalReceiver, String> entry : engines.entrySet()) {
                        sendCallbackToAllListeners(entry.getValue(), actualThrottle);
                        avgEngineSpeed += (float) ((EngineSubsystem) entry.getKey()).rotSpeed;
                    }
                    avgEngineSpeed /= engineCount;
                    for (ISignalReceiver gearbox : gearboxes.keySet()) {//减速时积极降档 Shift down early when braking
                        if (overrideCountDown.getOrDefault(gearbox, 0f) <= 0) {
                            ((GearboxSubsystem) gearbox).switchGear(autoGearShift((GearboxSubsystem) gearbox, avgEngineSpeed, 0.5f, moveInput[2]));
                        }
                    }
                }
                for (Map.Entry<ISignalReceiver, String> entry : wheels.entrySet()) {
                    String channel = entry.getValue();
                    WheelDriverSubsystem wheel = (WheelDriverSubsystem) entry.getKey();
                    if (wheel.connector.joint != null) {
                        float steeringInput = ackermannSteering(actualSteering, wheel.connector);
                        sendCallbackToListener(channel, wheel, new WheelControlSignal(actualBrake, actualHandBrake, steeringInput));
                    }
                }
            } else {//前进方向输入信号为0 Forward input signal is 0
                actualThrottle = actualThrottle * (1 - attr.throttleSensitivity) + 0 * attr.throttleSensitivity;
                for (Map.Entry<ISignalReceiver, String> entry : engines.entrySet()) {
                    sendCallbackToAllListeners(entry.getValue(), actualThrottle);
                    avgEngineSpeed += (float) ((EngineSubsystem) entry.getKey()).rotSpeed;
                }
                avgEngineSpeed /= engineCount;
                if (Math.abs(speed) < 0.5f) {//速度小于一定程度时，刹车 Brake if the speed is too low
                    actualBrake = actualBrake * (1 - attr.brakeSensitivity) + 1 * attr.brakeSensitivity;
                    if (attr.autoHandBrake && overrideCountDown.getOrDefault(this, 0f) <= 0) {
                        handBrakeControl = true;
                        overrideCountDown.put(this, 0.5f);
                    }
                    for (Map.Entry<ISignalReceiver, String> entry : wheels.entrySet()) {
                        String channel = entry.getValue();
                        WheelDriverSubsystem wheel = (WheelDriverSubsystem) entry.getKey();
                        if (wheel.connector.joint != null) {
                            float steeringInput = ackermannSteering(actualSteering, wheel.connector);
                            sendCallbackToListener(channel, wheel, new WheelControlSignal(actualBrake, actualHandBrake, steeringInput));
                        }
                    }
                    for (ISignalReceiver gearbox : gearboxes.keySet()) {
                        if (overrideCountDown.getOrDefault(gearbox, 0f) <= 0) {
                            ((GearboxSubsystem) gearbox).setClutched(false);//停止传输动力 Stop transmission power
                            ((GearboxSubsystem) gearbox).switchGear(autoGearShift((GearboxSubsystem) gearbox, avgEngineSpeed, 0f, moveInput[2]));
                        }
                    }
                } else {//速度大于一定程度时，不刹车 Don't brake if the speed is high enough
                    actualBrake = actualBrake * (1 - 2 * attr.brakeSensitivity) + 0 * 2 * attr.brakeSensitivity;
                    for (Map.Entry<ISignalReceiver, String> entry : wheels.entrySet()) {
                        String channel = entry.getValue();
                        WheelDriverSubsystem wheel = (WheelDriverSubsystem) entry.getKey();
                        if (wheel.connector.joint != null) {
                            float steeringInput = ackermannSteering(actualSteering, wheel.connector);
                            sendCallbackToListener(channel, wheel, new WheelControlSignal(actualBrake, actualHandBrake, steeringInput));
                        }
                    }
                    for (ISignalReceiver gearbox : gearboxes.keySet()) {//溜车时适度降档 Shift down moderately when rolling
                        if (overrideCountDown.getOrDefault(gearbox, 0f) <= 0) {
                            ((GearboxSubsystem) gearbox).switchGear(autoGearShift((GearboxSubsystem) gearbox, avgEngineSpeed, 0f, moveInput[2]));
                        }
                    }
                }
            }
        } else {//无输入信号 No input signal
            for (Map.Entry<ISignalReceiver, String> entry : engines.entrySet()) {
                sendCallbackToAllListeners(entry.getValue(), new EmptySignal());
            }
            for (ISignalReceiver gearbox : gearboxes.keySet()) {
                if (overrideCountDown.get(gearbox) <= 0) {
                    ((GearboxSubsystem) gearbox).setClutched(false);//停止传输动力 Stop transmission power
                }
            }
            if (Math.abs(speed) < 0.5f) {//维持原有信号状态 Maintain the original signal status
                actualBrake = actualBrake * (1 - 2 * attr.brakeSensitivity) + 0 * 2 * attr.brakeSensitivity;
                for (Map.Entry<ISignalReceiver, String> entry : wheels.entrySet()) {
                    String channel = entry.getValue();
                    WheelDriverSubsystem wheel = (WheelDriverSubsystem) entry.getKey();
                    if (wheel.connector.joint != null) {
                        float steeringInput = ackermannSteering(actualSteering, wheel.connector);
                        sendCallbackToListener(channel, wheel, new WheelControlSignal(actualBrake, actualHandBrake, steeringInput));
                    }
                }
            }
        }
    }

    /**
     * 自动变速箱换挡，调整阈值可调整换挡的早晚程度<p>
     * Automatically switch gears of the gearbox, adjust the threshold to adjust the timing of gear shifts.<p>
     * 约定变速箱减速比按常规顺序排列，例如-1，-3，3，1，0.5，0.2 <p>
     * Gearbox reduction ratio is arranged in the regular order, such as -1, -3, 3, 1, 0.5, 0.2
     *
     * @param gearbox     变速箱对象 Gearbox object
     * @param engineSpeed 引擎转速 Engine speed
     * @param threshold   换挡速度阈值 Gear shift speed threshold
     * @param direction   期望运动方向，正向 > 0，反向 < 0 Expected motion direction, forward > 0, reverse < 0
     * @return 换挡档位 Gear shift position
     */
    private int autoGearShift(GearboxSubsystem gearbox, float engineSpeed, float threshold, byte direction) {
        int gear = gearbox.getCurrentGear();
        if (attr.manualGearShift) return gear;//手动变速箱时不自动换挡 Manual gearbox shifting is not automatic
        double ratio = gearbox.gearRatios[gear];
        float index = (engineSpeed - avgEngineMaxTorqueSpeed) / Math.max(0.1f, avgEngineMaxSpeed - avgEngineMaxTorqueSpeed);
        int result;
        if (speed > 0.5f) {//前进时输出正转速，正挡 Forward output positive rotational speed, positive gear
            //当前引擎输出转速与期望运动方向不符时 Current engine output rotational speed does not match the expected motion direction
            if (engineSpeed * ratio < 0)//最低负挡 Lowest negative gear
                result = gearbox.minNegativeGear;
            else if (direction > 0) {//加速且转速过高时，升挡 Shift up when accelerating and the speed is high
                if (index > threshold) result = Math.min(gear + 1, gearbox.gearRatios.length - 1);
                else if (index < 0) result = Math.max(gear - 1, gearbox.minPositiveGear);
                else result = gear;
            } else if (index < threshold) {//减速且降档后转速低于最大引擎转速时，降挡 Shift down when braking and the speed is low after gear downshift
                int targetGear = Math.max(gear - 1, gearbox.minPositiveGear);
                if (Math.abs(targetGear * engineSpeed) < avgEngineMaxSpeed)
                    result = targetGear;
                else result = gear;
            } else result = gear;
        } else if (speed < -0.5f) {//后退时输出负转速，倒挡 Reverse output negative rotational speed, reverse gear
            //当前引擎输出转速与期望运动方向不符时 Current engine output rotational speed does not match the expected motion direction
            if ((engineSpeed * ratio > 0))//最低正挡 Lowest positive gear
                result = gearbox.minPositiveGear;
            else if (direction < 0) {//加速且转速过高时，升挡 Shift up when accelerating and the speed is high
                if (index > threshold) result = Math.min(gear - 1, 0);
                else if (index < 0) result = Math.min(gear + 1, gearbox.minNegativeGear);
                else result = gear;
            } else if (index < threshold) {//减速且降档后转速低于最大引擎转速时，降挡 Shift down when braking and the speed is low after gear downshift
                int targetGear = Math.min(gear + 1, gearbox.minNegativeGear);
                if (Math.abs(targetGear * engineSpeed) < avgEngineMaxSpeed)
                    result = targetGear;
                else result = gear;
            } else result = gear;
        } else {//静止时
            if (direction >= 0)//前进起步，挂最低正挡 Forward start, set to the lowest positive gear
                result = gearbox.minPositiveGear;
            else result = gearbox.minNegativeGear;
        }
        if (result != gear)
            overrideCountDown.put(gearbox, Math.max(0.2f, gearbox.attr.switchTime + 0.05f));//自动切换后一段时间内不自动切换 Cooldown after automatic gear shift
        return result;
    }

    private float ackermannSteering(float steeringInput, SpecialConnector wheelDrive) {
        New6Dof joint = wheelDrive.joint;
        Vector3f pivot = new Vector3f();
        if (wheelDrive.subPart.body == joint.getBodyA()) joint.getPivotA(pivot);
        else joint.getPivotB(pivot);
        if (steeringInput == 0) {
            return 0;
        } else {
            float steeringRadius = attr.steeringRadius / steeringInput * 100f;//实际转向半径(米) Actual steering radius (m)
            double deltaRadius = pivot.x - attr.steeringCenter.x;
            deltaRadius *= Math.signum(steeringInput);
            double deltaForward = pivot.z - attr.steeringCenter.z;
            return (float) Math.atan(deltaForward / (steeringRadius + deltaRadius));
        }
    }

    @Override
    public Map<String, List<String>> getTargetNames() {
        Map<String, List<String>> result = new HashMap<>(attr.getEngineControlOutputTargets());
        result.putAll(attr.getGearboxControlOutputTargets());
        result.putAll(attr.getWheelControlOutputTargets());
        return result;
    }
}
