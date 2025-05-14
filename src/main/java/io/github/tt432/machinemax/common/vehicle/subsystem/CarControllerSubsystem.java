package io.github.tt432.machinemax.common.vehicle.subsystem;

import com.mojang.datafixers.util.Pair;
import io.github.tt432.machinemax.common.vehicle.ISubsystemHost;
import io.github.tt432.machinemax.common.vehicle.VehicleCore;
import io.github.tt432.machinemax.common.vehicle.attr.subsystem.CarControllerSubsystemAttr;
import io.github.tt432.machinemax.common.vehicle.signal.*;
import lombok.Getter;

import java.util.*;

@Getter
public class CarControllerSubsystem extends AbstractSubsystem{
    public final CarControllerSubsystemAttr attr;
    public byte[] moveInput;
    public byte[] moveInputConflict;
    public float speed;
    private final Map<ISignalReceiver, Float> overrideCountDown = new HashMap<>();

    float avgEngineMaxSpeed = 0f;
    float avgEngineMaxTorqueSpeed = 0f;
    int engineCount = 0;

    private final Map<String, ISignalReceiver> engines = new HashMap<>();//控制的发动机接受的信号名和发动机对象映射 Controlled engine receives signal name and engine object mapping
    private final Map<String, ISignalReceiver> gearboxes = new HashMap<>();//控制的变速箱接受的信号名和变速箱对象映射 Controlled gearbox receives signal name and gearbox object mapping
    private final Map<String, ISignalReceiver> wheels = new HashMap<>();//控制的轮子接受的信号名和轮子对象映射 Controlled wheel receives signal name and wheel object mapping

    public CarControllerSubsystem(ISubsystemHost owner, String name, CarControllerSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
    }

    @Override
    public void onTick() {
        super.onTick();
        updateMoveInputs();
        this.speed = -getPart().rootSubPart.body.getLinearVelocityLocal(null).z;
        if (active) {
            //自动换档冷却时间 Auto gear shift cooldown
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
            sendSignalToAllTargets(signalKey, new EmptySignal(), true);
        }
        for (String signalKey : attr.wheelControlOutputTargets.keySet()) {
            sendSignalToAllTargets(signalKey, new EmptySignal(), true);
        }
        for (String signalKey : attr.gearboxControlOutputTargets.keySet()) {
            sendSignalToAllTargets(signalKey, new EmptySignal(), true);
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
            if (channelName.equals("callback") && signalValue instanceof String callbackValue) {
                if (sender instanceof WheelDriverSubsystem wheel) {
                    if (wheel.getPart().vehicle != this.getPart().vehicle) wheels.remove(signalValue);
                    else {
                        wheels.put(callbackValue, wheel);
                        addCallbackTarget(callbackValue, wheel);
                    }
                } else if (sender instanceof EngineSubsystem engine) {
                    if (engine.getPart().vehicle != this.getPart().vehicle) this.engines.remove(signalValue);
                    else {
                        engines.put(callbackValue, engine);
                        addCallbackTarget(callbackValue, engine);
                        //计算引擎最大转速和最大扭矩转速的平均值 Calculate the average maximum speed and max torque speed of the engine
                        avgEngineMaxTorqueSpeed = 0;
                        avgEngineMaxSpeed = 0;
                        for (Map.Entry<String, ISignalReceiver> entry : engines.entrySet()) {
                            avgEngineMaxTorqueSpeed += ((EngineSubsystem) entry.getValue()).attr.maxTorqueRpm;
                            avgEngineMaxSpeed += ((EngineSubsystem) entry.getValue()).attr.maxRpm;
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
                        this.gearboxes.remove(signalValue);
                        overrideCountDown.remove(gearbox);
                    } else {
                        gearboxes.put(callbackValue, gearbox);
                        addCallbackTarget(callbackValue, gearbox);
                        overrideCountDown.put(gearbox, 0f);
                    }
                }
            } else if (signalValue instanceof RegularInputSignal regularInputSignal) {//处理按键输入 Handle key input
                int tickCount = regularInputSignal.getInputTickCount();
                switch (regularInputSignal.getInputType()) {
                    case CLUTCH:
                        for (ISignalReceiver gearbox : gearboxes.values()) {
                            overrideCountDown.put(gearbox, 2f);//手动操作后一段时间内不自动切换 Clutch for a period of time after manual operation
                        }
                        if (tickCount == 0) {//踩离合 Unclutch
                            for (ISignalReceiver gearbox : gearboxes.values())
                                ((GearboxSubsystem) gearbox).setClutched(false);
                            System.out.println("Clutch engaged");
                        } else {//松离合 Clutch
                            for (ISignalReceiver gearbox : gearboxes.values())
                                ((GearboxSubsystem) gearbox).setClutched(true);
                            System.out.println("Clutch released");
                        }
                        break;
                    case UP_SHIFT://升档 Shift up
                        for (ISignalReceiver gearbox : gearboxes.values()) {
                            overrideCountDown.put(gearbox, 5f);//手动操作后一段时间内不自动切换 Clutch for a period of time after manual operation
                        }
                        for (ISignalReceiver gearbox : gearboxes.values()) ((GearboxSubsystem) gearbox).upShift();
                        System.out.println("Shift up");
                        break;
                    case DOWN_SHIFT://降档 Shift down
                        for (ISignalReceiver gearbox : gearboxes.values()) {
                            overrideCountDown.put(gearbox, 5f);//手动操作后一段时间内不自动切换 Clutch for a period of time after manual operation
                        }
                        for (ISignalReceiver gearbox : gearboxes.values()) ((GearboxSubsystem) gearbox).downShift();
                        System.out.println("Shift down");
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
                for (Map.Entry<String, ISignalReceiver> entry : wheels.entrySet()) {
                    //TODO:阿克曼转向
                    sendCallbackToAllListeners(entry.getKey(), new WheelControlSignal(Pair.of((float) moveInput[2], (float) moveInput[4])));
                }
                if (moveInput[2] * speed > 0 || Math.abs(speed) <= 0.5f) {//加速行驶 Accelerate
                    for (Map.Entry<String, ISignalReceiver> entry : engines.entrySet()) {
                        sendCallbackToAllListeners(entry.getKey(), 1f);
                        avgEngineSpeed += (float) ((EngineSubsystem) entry.getValue()).rotSpeed;
                    }
                    avgEngineSpeed /= engineCount;

                    for (ISignalReceiver gearbox : gearboxes.values()) {//加速时延迟升档 Delay shifting up when accelerating
                        if (overrideCountDown.get(gearbox) <= 0) {
                            ((GearboxSubsystem) gearbox).switchGear(autoGearShift((GearboxSubsystem) gearbox, avgEngineSpeed, 0.85f, moveInput[2]));
                            if (Math.abs(speed) <= 0.5f)//起步时自动松离合 Auto release clutch when starting
                                ((GearboxSubsystem) gearbox).setClutched(true);
                        }
                    }
                } else if (moveInput[2] * speed < 0) {//减速行驶 Brake
                    for (Map.Entry<String, ISignalReceiver> entry : engines.entrySet()) {
                        sendCallbackToAllListeners(entry.getKey(), 0f);
                        avgEngineSpeed += (float) ((EngineSubsystem) entry.getValue()).rotSpeed;
                    }
                    avgEngineSpeed /= engineCount;

                    for (ISignalReceiver gearbox : gearboxes.values()) {//减速时积极降档 Shift down early when braking
                        if (overrideCountDown.get(gearbox) <= 0) {
                            ((GearboxSubsystem) gearbox).switchGear(autoGearShift((GearboxSubsystem) gearbox, avgEngineSpeed, 0.5f, moveInput[2]));
                        }
                    }
                }
            } else {//前进方向输入信号为0 Forward input signal is 0
                for (Map.Entry<String, ISignalReceiver> entry : engines.entrySet()) {
                    sendCallbackToAllListeners(entry.getKey(), 0f);
                    avgEngineSpeed += (float) ((EngineSubsystem) entry.getValue()).rotSpeed;
                }
                avgEngineSpeed /= engineCount;
                if (Math.abs(speed) < 0.5f) {//速度小于一定程度时，刹车 Brake if the speed is too low
                    for (Map.Entry<String, ISignalReceiver> entry : wheels.entrySet()) {
                        sendCallbackToAllListeners(entry.getKey(), new WheelControlSignal(0f, moveInput[4]));
                    }
                    for (ISignalReceiver gearbox : gearboxes.values()) {
                        if (overrideCountDown.get(gearbox) <= 0) {
                            ((GearboxSubsystem) gearbox).setClutched(false);//停止传输动力 Stop transmission power
                            ((GearboxSubsystem) gearbox).switchGear(autoGearShift((GearboxSubsystem) gearbox, avgEngineSpeed, 0f, moveInput[2]));
                        }
                    }

                } else {//速度大于一定程度时，不刹车 Don't brake if the speed is high enough
                    for (Map.Entry<String, ISignalReceiver> entry : wheels.entrySet()) {
                        sendCallbackToAllListeners(entry.getKey(), new WheelControlSignal(Pair.of(null, (float) moveInput[4])));
                    }
                    for (ISignalReceiver gearbox : gearboxes.values()) {//溜车时适度降档 Shift down moderately when rolling
                        if (overrideCountDown.get(gearbox) <= 0) {
                            ((GearboxSubsystem) gearbox).switchGear(autoGearShift((GearboxSubsystem) gearbox, avgEngineSpeed, 0f, moveInput[2]));
                        }
                    }
                }
            }
        } else {//无输入信号 No input signal
            for (Map.Entry<String, ISignalReceiver> entry : engines.entrySet()) {
                sendCallbackToAllListeners(entry.getKey(), new EmptySignal());
            }
            if (Math.abs(speed) < 0.5f) {//速度小于一定程度时，刹车 Brake if the speed is too low
                for (Map.Entry<String, ISignalReceiver> entry : wheels.entrySet()) {
                    sendCallbackToAllListeners(entry.getKey(), new WheelControlSignal(0f, 0f));
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
            else if (direction > 0 && index > threshold) {//加速且转速过高时，升挡 Shift up when accelerating and the speed is high
                result = Math.min(gear + 1, gearbox.gearRatios.length - 1);
            } else if (direction <= 0 && index < threshold) {//减速且降档后转速低于最大引擎转速时，降挡 Shift down when braking and the speed is low after gear downshift
                int targetGear = Math.max(gear - 1, gearbox.minPositiveGear);
                if (Math.abs(targetGear * engineSpeed) < avgEngineMaxSpeed)
                    result = targetGear;
                else result = gear;
            } else result = gear;
        } else if (speed < -0.5f) {//后退时输出负转速，倒挡 Reverse output negative rotational speed, reverse gear
            //当前引擎输出转速与期望运动方向不符时 Current engine output rotational speed does not match the expected motion direction
            if ((engineSpeed * ratio > 0))//最低正挡 Lowest positive gear
                result = gearbox.minPositiveGear;
            else if (direction < 0 && index > threshold) {//加速且转速过高时，升挡 Shift up when accelerating and the speed is high
                result = Math.max(gear - 1, 0);
            } else if (direction >= 0 && index < threshold) {//减速且降档后转速低于最大引擎转速时，降挡 Shift down when braking and the speed is low after gear downshift
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
            overrideCountDown.put(gearbox, Math.max(0.5f, gearbox.attr.switchTime + 0.05f));//自动切换后一段时间内不自动切换 Cooldown after automatic gear shift
        return result;
    }

    @Override
    public Map<String, List<String>> getTargetNames() {
        Map<String, List<String>> result = new HashMap<>(attr.getEngineControlOutputTargets());
        result.putAll(attr.getGearboxControlOutputTargets());
        result.putAll(attr.getWheelControlOutputTargets());
        return result;
    }
}
