package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.common.attachment.ControlPreference;
import io.github.sweetzonzi.machine_max.common.mech.signal.EmptySignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalReceiver;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalSender;
import io.github.sweetzonzi.machine_max.common.mech.signal.MoveInputSignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.RegularInputSignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalChannel;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalResult;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.BasicSubsystemDynamicAttr;
import lombok.Getter;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 地面载具控制器的共享部分。
 *
 * <p>承载驾驶输入、发动机/电动机/变速箱发现、自动换挡与共享输出；
 * 轮式转向/漂移/ABS 留在 {@link CarControllerSubsystem}，履带的差速分配留在
 * {@code TrackControllerSubsystem}。本类不假设执行器与控制器位于同一个
 * {@code SubPart}：local 名、连接点端口和 {@code global} 广播都是合法目标。</p>
 */
@Getter
public abstract class AbstractGroundControllerSubsystem extends BasicSubsystem {

    public LivingEntity controller;
    public byte[] moveInput;
    public byte[] moveInputConflict;
    public float speed = 0.0f;

    public boolean handBrake = false;
    public float actualThrottle = 0f;
    public float actualBrake = 0f;
    public float actualHandBrake = 0f;
    public float actualSteering = 0f;

    protected final ConcurrentMap<ISignalReceiver, Float> overrideCountDown = new ConcurrentHashMap<>();

    protected final Map<EngineSubsystem, String> engines = new HashMap<>();
    protected final Map<MotorSubsystem, String> motors = new HashMap<>();
    protected final Map<GearboxSubsystem, String> gearboxes = new HashMap<>();

    protected float avgEngineMaxSpeed = 0f;
    protected float avgEngineMinSpeed = 0f;
    protected float avgEngineMaxTorqueSpeed = 0f;
    protected int engineCount = 0;
    protected int motorCount = 0;

    protected AbstractGroundControllerSubsystem(ISubsystemHost owner, String name, BasicSubsystemDynamicAttr attr) {
        super(owner, name, attr);
    }

    // ===== 子类提供的属性/执行器差异 =====

    protected abstract List<String> getControlInputKeys();

    protected abstract Map<String, List<String>> getControlOutputTargets();

    protected abstract Map<String, List<String>> getSpeedOutputTargets();

    protected abstract Map<String, List<String>> getThrottleOutputTargets();

    protected abstract Map<String, List<String>> getBrakeOutputTargets();

    protected abstract Map<String, List<String>> getHandbrakeOutputTargets();

    public abstract boolean isAutoHandBrakeEnabled();

    public abstract boolean isManualGearShift();

    /**
     * 无玩家偏好时是否使用分离油门/刹车。默认沿用意图模式。
     */
    public boolean isSeparateThrottleBrake() {
        return false;
    }

    /**
     * 转向输入平滑系数。默认沿用轮式控制器的 0.1。
     */
    protected float getSteeringSmoothing() {
        return 0.1f;
    }

    /**
     * 油门/刹车输入平滑系数。默认沿用轮式控制器的 0.2。
     */
    protected float getBrakeSmoothing() {
        return 0.2f;
    }

    /**
     * 把当前控制量分发给本类的执行器。
     * 调用点在 {@link #onPrePhysicsTick()} 内，已经完成输入平滑、自动换挡与发动机/电机油门发送。
     */
    protected abstract void distributeDriveControl();

    /**
     * 处理发动机/电机/变速箱之外的执行器回调。
     *
     * @param sender  回调方
     * @param channel 回调携带的控制频道名
     */
    protected void onActuatorDiscovered(ISignalSender sender, String channel) {
    }

    /**
     * 通用握手发送完成后的钩子，用于重建子类自己的执行器绑定。
     */
    protected void onHandShakeComplete() {
    }

    /**
     * 物理 tick 开始、输入平滑之前的钩子。轮式在这里计算漂移状态，摩托车在这里读取倾角。
     */
    protected void onGroundControlBegin() {
    }

    /**
     * 输入平滑之后的钩子。摩托车在这里施加平衡力矩。
     */
    protected void onGroundControlAfterInput() {
    }

    /** 意图模式的纵向动力请求；履带可把转向输入计入动力需求。物理线程调用。 */
    protected float resolveDriveIntent(float longitudinalInput) {
        return longitudinalInput;
    }

    // ===== 生命周期 =====

    @Override
    public void onTick() {
        super.onTick();
        for (Map.Entry<String, List<String>> entry : getSpeedOutputTargets().entrySet()) {
            String signalChannel = entry.getKey();
            for (String targetName : entry.getValue()) {
                sendSignalToTarget(signalChannel, targetName, this.speed);
            }
        }
        updateMoveInputs();
        if (isActive()) {
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
        this.speed = -getOwner().getSubPart().getLinearVelocityLocal().z;
        onGroundControlBegin();

        if (isActive()) {
            if (this.moveInput != null) {
                float smoothing = Math.clamp(getSteeringSmoothing(), 0f, 1f);
                actualSteering = actualSteering * (1f - smoothing) + (moveInput[4] / 100f) * smoothing;
            }
            updateRegularInputs();
            actualHandBrake = actualHandBrake * 0.9f + (handBrake ? 1 : 0) * 0.1f;
            onGroundControlAfterInput();
            distributeControlSignals();
        } else {
            resetSignalOutputs();
        }
    }

    @Override
    public void onPostPhysicsTick() {
        super.onPostPhysicsTick();
        for (Map.Entry<String, List<String>> entry : getThrottleOutputTargets().entrySet()) {
            String signalChannel = entry.getKey();
            for (String targetName : entry.getValue()) {
                sendSignalToTarget(signalChannel, targetName, actualThrottle * 0.01f);
            }
        }
        for (Map.Entry<String, List<String>> entry : getBrakeOutputTargets().entrySet()) {
            String signalChannel = entry.getKey();
            for (String targetName : entry.getValue()) {
                sendSignalToTarget(signalChannel, targetName, actualBrake);
            }
        }
        for (Map.Entry<String, List<String>> entry : getHandbrakeOutputTargets().entrySet()) {
            String signalChannel = entry.getKey();
            for (String targetName : entry.getValue()) {
                sendSignalToTarget(signalChannel, targetName, actualHandBrake);
            }
        }
    }

    @Override
    public void onAttach() {
        super.onAttach();
        handShake();
    }

    @Override
    public void onVehicleStructureChanged() {
        super.onVehicleStructureChanged();
        handShake();
    }

    // ===== 发现与握手 =====

    /**
     * 向所有 {@code control_outputs} 目标广播空信号，由回调重新建立执行器绑定。
     */
    protected void handShake() {
        clearActuatorBindings();
        for (String signalChannel : getControlOutputTargets().keySet()) {
            sendSignalToAllTargetsWithCallback(signalChannel, EmptySignal.INSTANCE, false);
        }
        onHandShakeComplete();
    }

    /**
     * 握手前清空旧绑定。子类覆盖时必须调用 {@code super}。
     */
    protected void clearActuatorBindings() {
        engines.clear();
        motors.clear();
        gearboxes.clear();
        overrideCountDown.clear();
        clearCallbackTargets();
    }

    @Override
    public SignalResult onSignalUpdated(String channelName, ISignalSender sender) {
        Object signalValue = getSignalChannel(channelName).get(sender);
        if (channelName.equals("callback") && signalValue instanceof String controlChannel) {
            if (sender instanceof EngineSubsystem engine) {
                if (!belongsToSameAssembly(engine)) {
                    engines.remove(engine);
                    recalculateEngineAverages();
                } else {
                    engines.put(engine, controlChannel);
                    addCallbackTarget(controlChannel, engine);
                    recalculateEngineAverages();
                }
            } else if (sender instanceof MotorSubsystem motor) {
                if (!belongsToSameAssembly(motor)) {
                    motors.remove(motor);
                    recalculateMotorAverages();
                } else {
                    motors.put(motor, controlChannel);
                    addCallbackTarget(controlChannel, motor);
                    recalculateMotorAverages();
                }
            } else if (sender instanceof GearboxSubsystem gearbox) {
                if (!belongsToSameAssembly(gearbox)) {
                    gearboxes.remove(gearbox);
                    overrideCountDown.remove(gearbox);
                } else {
                    gearboxes.put(gearbox, controlChannel);
                    addCallbackTarget(controlChannel, gearbox);
                    overrideCountDown.put(gearbox, 0f);
                }
            } else {
                onActuatorDiscovered(sender, controlChannel);
            }
        }
        return SignalResult.PASS;
    }

    private boolean belongsToSameAssembly(AbstractSubsystem subsystem) {
        return subsystem.getOwner().getSubPart().getPart().assembly
                == this.getOwner().getSubPart().getPart().assembly;
    }

    private void recalculateEngineAverages() {
        avgEngineMinSpeed = 0;
        avgEngineMaxTorqueSpeed = 0;
        avgEngineMaxSpeed = 0;
        for (Map.Entry<EngineSubsystem, String> entry : engines.entrySet()) {
            avgEngineMinSpeed += entry.getKey().attr.staticAttribute.idleRpm;
            avgEngineMaxTorqueSpeed += entry.getKey().attr.staticAttribute.maxTorqueRpm;
            avgEngineMaxSpeed += entry.getKey().attr.staticAttribute.redLineRpm;
        }
        engineCount = engines.size();
        if (engineCount > 0) {
            avgEngineMinSpeed = (float) (avgEngineMinSpeed * Math.PI / engineCount / 30f);
            avgEngineMaxTorqueSpeed = (float) (avgEngineMaxTorqueSpeed * Math.PI / engineCount / 30f);
            avgEngineMaxSpeed = (float) (avgEngineMaxSpeed * Math.PI / engineCount / 30f);
        } else {
            avgEngineMinSpeed = 0f;
            avgEngineMaxTorqueSpeed = 0f;
            avgEngineMaxSpeed = 0f;
        }
    }

    private void recalculateMotorAverages() {
        avgEngineMinSpeed = 0;
        avgEngineMaxTorqueSpeed = 0;
        avgEngineMaxSpeed = 0;
        for (Map.Entry<MotorSubsystem, String> entry : motors.entrySet()) {
            float maxTorqueMinRpm = (float) (entry.getKey().attr.staticAttribute.maxPower
                    / entry.getKey().attr.staticAttribute.maxTorque * 30f / Math.PI);
            avgEngineMaxTorqueSpeed += 0.9f * maxTorqueMinRpm + 0.1f * entry.getKey().attr.staticAttribute.redLineRpm;
            avgEngineMaxSpeed += entry.getKey().attr.staticAttribute.redLineRpm;
        }
        motorCount = motors.size();
        if (motorCount > 0) {
            avgEngineMaxTorqueSpeed = (float) (avgEngineMaxTorqueSpeed * Math.PI / motorCount / 30f);
            avgEngineMaxSpeed = (float) (avgEngineMaxSpeed * Math.PI / motorCount / 30f);
        } else {
            avgEngineMaxTorqueSpeed = 0f;
            avgEngineMaxSpeed = 0f;
        }
    }

    // ===== 输入 =====

    protected void updateMoveInputs() {
        byte[] moveInput = null;
        byte[] moveInputConflict = null;
        boolean hasMoveInput = false;
        for (String inputKey : getControlInputKeys()) {
            SignalChannel signalChannel = getSignalChannel(inputKey);
            for (Map.Entry<ISignalSender, Object> entry : signalChannel.entrySet()) {
                ISignalSender sender = entry.getKey();
                Object signal = entry.getValue();
                if (signal instanceof MoveInputSignal moveInputSignal) {
                    moveInput = moveInputSignal.getMoveInput();
                    moveInputConflict = moveInputSignal.getMoveInputConflict();
                    hasMoveInput = true;
                    if (sender instanceof SeatSubsystem seat) {
                        this.controller = seat.passenger;
                    }
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
            this.controller = null;
        }
    }

    /**
     * 按 controlInputKeys 优先级解析常规输入信号（离合/手刹/换挡）。
     */
    protected void updateRegularInputs() {
        RegularInputSignal activeRegular = null;
        SignalChannel activeChannel = null;
        ISignalSender activeSender = null;
        for (String inputKey : getControlInputKeys()) {
            SignalChannel channel = getSignalChannel(inputKey);
            for (Map.Entry<ISignalSender, Object> entry : channel.entrySet()) {
                if (entry.getValue() instanceof RegularInputSignal ris) {
                    activeRegular = ris;
                    activeChannel = channel;
                    activeSender = entry.getKey();
                    break;
                }
            }
            if (activeRegular != null) break;
        }

        if (activeRegular != null) {
            int tickCount = activeRegular.getInputTickCount();
            switch (activeRegular.getInputType()) {
                case CLUTCH:
                    for (ISignalReceiver gearbox : gearboxes.keySet()) {
                        overrideCountDown.put(gearbox, 1f);
                    }
                    for (GearboxSubsystem gearbox : gearboxes.keySet()) {
                        gearbox.setClutched(tickCount != 0);
                    }
                    break;
                case UP_SHIFT:
                    if (tickCount == 0) {
                        for (ISignalReceiver gearbox : gearboxes.keySet()) {
                            overrideCountDown.put(gearbox, 3f);
                        }
                        for (GearboxSubsystem gearbox : gearboxes.keySet()) {
                            gearbox.upShift();
                        }
                    }
                    activeChannel.put(activeSender, EmptySignal.INSTANCE);
                    break;
                case DOWN_SHIFT:
                    if (tickCount == 0) {
                        for (ISignalReceiver gearbox : gearboxes.keySet()) {
                            overrideCountDown.put(gearbox, 3f);
                        }
                        for (GearboxSubsystem gearbox : gearboxes.keySet()) {
                            gearbox.downShift();
                        }
                    }
                    activeChannel.put(activeSender, EmptySignal.INSTANCE);
                    break;
                case HAND_BRAKE:
                    handBrake = tickCount == 0;
                    overrideCountDown.put(this, tickCount == 0 ? 100f : 0f);
                    break;
                case TOGGLE_HAND_BRAKE:
                    if (tickCount == 0) {
                        handBrake = !handBrake;
                        overrideCountDown.put(this, 1f);
                    }
                    activeChannel.put(activeSender, EmptySignal.INSTANCE);
                    break;
                default:
                    break;
            }
        }
    }

    // ===== 控制量分配 =====

    protected void distributeControlSignals() {
        if (this.moveInput != null && moveInputConflict != null) {
            byte[] moveInput = this.moveInput;
            byte[] moveInputConflict = this.moveInputConflict;

            if (ControlPreference.shouldRawThrottleBrake(this)) {
                float targetThrottle = moveInput[2] / 100f;
                float targetBrake = moveInputConflict[2] / 100f;

                actualThrottle = actualThrottle * 0.2f + targetThrottle * 0.8f;
                float brakeSmoothing = Math.clamp(getBrakeSmoothing(), 0f, 1f);
                actualBrake = actualBrake * (1f - brakeSmoothing) + targetBrake * brakeSmoothing;

                if (targetThrottle < 0.01f && targetBrake < 0.01f) {
                    if (Math.abs(speed) < 1f && ControlPreference.shouldAutoHandBrake(this)) {
                        handBrake = true;
                    }
                } else if (targetThrottle > 0.05f) {
                    if (ControlPreference.shouldAutoHandBrake(this) && handBrake) {
                        handBrake = false;
                    }
                }

                float avgEngineSpeed = calculateAvgSpeedAndControl();
                byte direction = inferDirectionForAutoShift(targetThrottle);
                autoShiftAll(avgEngineSpeed, direction);
                distributeDriveControl();
            } else {
                float throttleInput = resolveDriveIntent(moveInput[2] / 100f);
                float brakeInput = resolveDriveIntent(moveInputConflict[2] / 100f);

                float targetThrottle;
                float targetBrake;
                byte direction;

                if (Math.abs(speed) < 0.5f) {
                    targetThrottle = throttleInput;
                    targetBrake = 0;
                    direction = throttleInput > 0.01f ? (byte) 1 : (throttleInput < -0.01f ? (byte) -1 : 0);
                } else {
                    boolean forward = speed > 0;
                    targetThrottle = throttleInput;
                    targetBrake = forward ? Math.max(-brakeInput, 0f) : Math.max(brakeInput, 0f);
                    direction = targetThrottle > 0.01f
                            ? (byte) (forward ? 1 : -1)
                            : (byte) (speed >= 0 ? 1 : -1);
                    if (targetThrottle * direction < 0) {
                        targetThrottle = 0;
                    }
                }

                actualThrottle = actualThrottle * 0.2f + targetThrottle * 0.8f;
                float brakeSmoothing = Math.clamp(getBrakeSmoothing(), 0f, 1f);
                actualBrake = actualBrake * (1f - brakeSmoothing) + targetBrake * brakeSmoothing;
                float avgEngineSpeed = calculateAvgSpeedAndControl();

                for (GearboxSubsystem gearbox : gearboxes.keySet()) {
                    if (overrideCountDown.getOrDefault(gearbox, 0f) <= 0) {
                        gearbox.switchGear(autoGearShift(gearbox, avgEngineSpeed, direction));
                        if (Math.abs(speed) <= 1f && Math.abs(targetThrottle) > 0.01f) {
                            gearbox.setClutched(true);
                        }
                    }
                }

                distributeDriveControl();

                if (Math.abs(targetThrottle) > 0.05f && ControlPreference.shouldAutoHandBrake(this) && handBrake
                        && overrideCountDown.getOrDefault(this, 0f) <= 0) {
                    handBrake = false;
                    overrideCountDown.put(this, 2f);
                }
                if (Math.abs(targetThrottle) < 0.01f && Math.abs(targetBrake) < 0.01f && Math.abs(speed) < 1f
                        && ControlPreference.shouldAutoHandBrake(this) && !handBrake
                        && overrideCountDown.getOrDefault(this, 0f) <= 0) {
                    handBrake = true;
                    overrideCountDown.put(this, 0.5f);
                }
            }
        } else {
            for (Map.Entry<EngineSubsystem, String> entry : engines.entrySet()) {
                sendCallbackToListener(entry.getValue(), entry.getKey(), EmptySignal.INSTANCE);
            }
            actualBrake = actualBrake * 0.8f;
            if (ControlPreference.shouldAutoHandBrake(this) && !handBrake) {
                handBrake = true;
                actualHandBrake = 1f;
                overrideCountDown.put(this, 0.5f);
            }
            distributeDriveControl();
        }
    }

    private void autoShiftAll(float avgEngineSpeed, byte direction) {
        for (GearboxSubsystem gearbox : gearboxes.keySet()) {
            if (overrideCountDown.getOrDefault(gearbox, 0f) <= 0) {
                gearbox.switchGear(autoGearShift(gearbox, avgEngineSpeed, direction));
            }
        }
    }

    /**
     * 计算引擎/电动机平均转速，并向每个引擎/电动机单独发送油门控制信号。
     */
    protected float calculateAvgSpeedAndControl() {
        float avgEngineSpeed = 0f;
        for (Map.Entry<EngineSubsystem, String> entry : engines.entrySet()) {
            sendCallbackToListener(entry.getValue(), entry.getKey(), Math.abs(actualThrottle));
            avgEngineSpeed += (float) entry.getKey().getRotSpeed();
        }
        for (Map.Entry<MotorSubsystem, String> entry : motors.entrySet()) {
            sendCallbackToListener(entry.getValue(), entry.getKey(), actualThrottle);
            avgEngineSpeed += entry.getKey().getRotSpeed();
        }
        if (engineCount > 1) {
            avgEngineSpeed /= engineCount;
        } else if (motorCount > 1) {
            avgEngineSpeed /= motorCount;
        }
        return avgEngineSpeed;
    }

    /**
     * 分离模式下推断自动换挡的方向参数。
     */
    private byte inferDirectionForAutoShift(float targetThrottle) {
        if (targetThrottle > 0.05f) {
            for (GearboxSubsystem gearbox : gearboxes.keySet()) {
                if (gearbox.getCurrentGear() < 0) {
                    return -1;
                }
            }
            return 1;
        }
        if (Math.abs(speed) < 0.5f) return 0;
        return (byte) (speed >= 0 ? 1 : -1);
    }

    /**
     * 基于最大输出轴扭矩的自动换挡。
     */
    protected int autoGearShift(GearboxSubsystem gearbox, float engineSpeed, byte direction) {
        int currentGear = gearbox.getCurrentGear();
        if (!ControlPreference.shouldAutoSwitchGear(this)) {
            return currentGear;
        }

        byte actualDirection = direction;
        if (actualDirection == 0 && Math.abs(speed) > 0.5f) {
            actualDirection = (byte) (speed >= 0 ? 1 : -1);
        }

        if (Math.abs(speed) < 0.5f) {
            if (actualDirection == 0) return currentGear;
            return actualDirection > 0 ? gearbox.minPositiveGear : gearbox.minNegativeGear;
        }

        double outputShaftSpeed = engineSpeed / gearbox.gearRatios[currentGear];

        int bestGear = currentGear;
        double bestOutputTorque = -1;
        double currentRatio = gearbox.gearRatios[currentGear];

        for (int i = 0; i < gearbox.gearRatios.length; i++) {
            double ratio = gearbox.gearRatios[i];

            boolean isPureElectric = engineCount == 0 && motorCount > 0;
            if (!isPureElectric && ratio * actualDirection <= 0) continue;

            double predictedSpeed = outputShaftSpeed * ratio;
            if (Math.abs(predictedSpeed) < 0.1) continue;

            double totalTorque = 0;
            for (EngineSubsystem engine : engines.keySet()) {
                totalTorque += engine.getTorqueAtSpeed(predictedSpeed)
                        - Math.abs(engine.getDampingTorque(predictedSpeed));
            }
            for (MotorSubsystem motor : motors.keySet()) {
                totalTorque += motor.getTorqueAtSpeed(predictedSpeed)
                        - Math.abs(motor.getDampingTorque(predictedSpeed));
            }

            double outputTorque = totalTorque * Math.abs(ratio);
            if (outputTorque > bestOutputTorque) {
                bestOutputTorque = outputTorque;
                bestGear = i;
            }
        }

        double currentOutputTorque = 0;
        double curPredictedSpeed = outputShaftSpeed * currentRatio;
        if (Math.abs(curPredictedSpeed) >= 0.1) {
            double curTotalTorque = 0;
            for (EngineSubsystem engine : engines.keySet()) {
                curTotalTorque += engine.getTorqueAtSpeed(curPredictedSpeed)
                        - Math.abs(engine.getDampingTorque(curPredictedSpeed));
            }
            for (MotorSubsystem motor : motors.keySet()) {
                curTotalTorque += motor.getTorqueAtSpeed(curPredictedSpeed)
                        - Math.abs(motor.getDampingTorque(curPredictedSpeed));
            }
            currentOutputTorque = curTotalTorque * Math.abs(currentRatio);
        }

        if (bestGear != currentGear && bestOutputTorque < currentOutputTorque * 1.05) {
            bestGear = currentGear;
        }

        if (bestGear > currentGear + 1) bestGear = currentGear + 1;
        if (bestGear < currentGear - 1) bestGear = currentGear - 1;

        if (bestGear != currentGear) {
            overrideCountDown.put(gearbox, Math.max(0.2f, gearbox.attr.staticAttribute.switchTime + 0.5f));
        }
        return bestGear;
    }

    @Override
    public List<String> getAcceptedChannels() {
        return getControlInputKeys();
    }

    @Override
    public Map<String, List<String>> getTargetNames() {
        Map<String, List<String>> result = new HashMap<>();
        result.putAll(getControlOutputTargets());
        result.putAll(getSpeedOutputTargets());
        result.putAll(getThrottleOutputTargets());
        result.putAll(getBrakeOutputTargets());
        result.putAll(getHandbrakeOutputTargets());
        return result;
    }
}
