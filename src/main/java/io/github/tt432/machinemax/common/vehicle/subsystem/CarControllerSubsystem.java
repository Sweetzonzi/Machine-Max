package io.github.tt432.machinemax.common.vehicle.subsystem;

import com.mojang.datafixers.util.Pair;
import io.github.tt432.machinemax.common.vehicle.ISubsystemHost;
import io.github.tt432.machinemax.common.vehicle.VehicleCore;
import io.github.tt432.machinemax.common.vehicle.attr.subsystem.CarControllerSubsystemAttr;
import io.github.tt432.machinemax.common.vehicle.signal.*;
import lombok.Getter;

import java.util.*;

@Getter
public class CarControllerSubsystem extends AbstractSubsystem implements ISignalReceiver, ISignalSender {
    public final CarControllerSubsystemAttr attr;
    public byte[] moveInput;
    public byte[] moveInputConflict;
    public float speed;
    private float overrideCountDown = 0.0f;

    private final Map<String, ISignalReceiver> engines = new HashMap<>();
    private Map<String, ISignalReceiver> gearboxes = new HashMap<>();
    private final Map<String, ISignalReceiver> wheels = new HashMap<>();

    public CarControllerSubsystem(ISubsystemHost owner, String name, CarControllerSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
    }

    @Override
    public void onTick() {
        super.onTick();
        updateMoveInputs();
        this.speed = getPart().rootSubPart.body.getLinearVelocity(null).z;
        if (active && overrideCountDown > 0) overrideCountDown -= 0.05f;
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
        clearCallbackSignals();
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
    public void onSignalUpdated(String signalKey, ISignalSender sender) {
        ISignalReceiver.super.onSignalUpdated(signalKey, sender);
        Object signalValue = getSignals(signalKey).get(sender);
        if (this.isActive()) {
            if (signalKey.equals("callback") && signalValue instanceof String callbackValue) {
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
                    }
                } else if (sender instanceof GearboxSubsystem gearbox) {
                    if (gearbox.getPart().vehicle != this.getPart().vehicle) this.gearboxes = null;
                    else {
                        gearboxes.put(callbackValue, gearbox);
                        addCallbackTarget(callbackValue, gearbox);
                    }
                }
            } else if (signalValue instanceof RegularInputSignal regularInputSignal) {
                int tickCount = regularInputSignal.getInputTickCount();
                switch (regularInputSignal.getInputType()) {
                    case CLUTCH:
                        overrideCountDown = 2f;//手动操作后一段时间内不自动切换 Clutch for a period of time after manual operation
                        if (tickCount == 0) {//踩离合 Unclutch
//                            for (ISignalReceiver gearbox : gearboxes.values())
//                                ((GearboxSubsystem) gearbox).setClutched(false);
                            System.out.println("Clutch engaged");
                        } else {//松离合 Clutch
//                            for (ISignalReceiver gearbox : gearboxes.values())
//                                ((GearboxSubsystem) gearbox).setClutched(true);
                            System.out.println("Clutch released");
                        }
                        break;
                    case UP_SHIFT://升档 Shift up
                        overrideCountDown = 5f;
//                        for (ISignalReceiver gearbox : gearboxes.values()) ((GearboxSubsystem) gearbox).upShift();
                        System.out.println("Shift up");
                        break;
                    case DOWN_SHIFT://降档 Shift down
                        overrideCountDown = 5f;
//                        for (ISignalReceiver gearbox : gearboxes.values()) ((GearboxSubsystem) gearbox).downShift();
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
        for (String inputKey : attr.controlInputKeys) {//遍历输入信号 Iterate over input signals
            Signals signals = getSignals(inputKey);
            for (Object signal : signals.values()) {
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
        if (moveInput != null && moveInputConflict != null) {//前进方向有输入信号 (可为0) Have forward input signal (can be 0)
            if (moveInput[2] != 0) {//前进方向输入信号不为0 Forward input signal is not 0
                for (Map.Entry<String, ISignalReceiver> entry : wheels.entrySet()) {
                    sendCallbackToAllListeners(entry.getKey(), new WheelControlSignal(Pair.of((float) moveInput[2], (float) moveInput[4])));
                }
                if (moveInput[2] * speed > 0 || Math.abs(speed) <= 0.5f) {//加速行驶 Accelerate
                    for (Map.Entry<String, ISignalReceiver> entry : engines.entrySet()) {
                        sendCallbackToAllListeners(entry.getKey(), 1f);
                    }
                    if (Math.abs(speed) <= 0.5f && overrideCountDown <= 0) {
                        for (ISignalReceiver gearbox : gearboxes.values())
                            ((GearboxSubsystem) gearbox).setClutched(true);
                    }
                } else if (moveInput[2] * speed < 0) {//减速行驶 Brake
                    for (Map.Entry<String, ISignalReceiver> entry : engines.entrySet()) {
                        sendCallbackToAllListeners(entry.getKey(), 0f);
                    }
                }
            } else {//前进方向输入信号为0 Forward input signal is 0
                for (Map.Entry<String, ISignalReceiver> entry : engines.entrySet()) {
                    sendCallbackToAllListeners(entry.getKey(), 0f);
                }
                if (Math.abs(speed) < 0.5f) {//速度小于一定程度时，刹车 Brake if the speed is too low
                    for (Map.Entry<String, ISignalReceiver> entry : wheels.entrySet()) {
                        sendCallbackToAllListeners(entry.getKey(), new WheelControlSignal(Pair.of(0f, (float) moveInput[4])));
                    }
                    if (overrideCountDown <= 0)
                        for (ISignalReceiver gearbox : gearboxes.values()) {
                            ((GearboxSubsystem) gearbox).setClutched(false);
                        }
                } else {//速度大于一定程度时，不刹车 Don't brake if the speed is high enough
                    for (Map.Entry<String, ISignalReceiver> entry : wheels.entrySet()) {
                        sendCallbackToAllListeners(entry.getKey(), new WheelControlSignal(Pair.of(null, (float) moveInput[4])));
                    }
                }
            }
        } else {//无输入信号 No input signal
            for (Map.Entry<String, ISignalReceiver> entry : engines.entrySet()) {
                sendCallbackToAllListeners(entry.getKey(), new EmptySignal());
            }
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
