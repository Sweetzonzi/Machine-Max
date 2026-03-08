package io.github.sweetzonzi.machine_max.common.vehicle.subsystem;

import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.bullet.joints.New6Dof;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.attachment.ControlPreference;
import io.github.sweetzonzi.machine_max.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.dynamic_attr.CarControllerSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.AdvancedConnector;
import io.github.sweetzonzi.machine_max.common.vehicle.signal.*;
import io.github.sweetzonzi.machine_max.util.control.PIDController;
import lombok.Getter;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class CarControllerSubsystem extends AbstractSubsystem {
    public final CarControllerSubsystemAttr attr;
    public LivingEntity controller;
    public byte[] moveInput;
    public byte[] moveInputConflict;
    Vector3f[] vs = new Vector3f[6];
    public float speed = 0.0f;
    public float avgSlipRatio = 0.0f;
    private final Map<ISignalReceiver, Float> overrideCountDown = new HashMap<>();

    float avgEngineMaxSpeed = 0f;
    float avgEngineMinSpeed = 0f;
    float avgEngineMaxTorqueSpeed = 0f;
    int engineCount = 0;
    int motorCount = 0;

    private final Map<EngineSubsystem, String> engines = new HashMap<>();//控制的发动机其接收控制的信号频道映射 Control engine and its receiving signal channel mapping
    private final Map<MotorSubsystem, String> motors = new HashMap<>();//控制的电动机其接收控制的信号频道映射 Control engine and its receiving signal channel mapping
    private final Map<GearboxSubsystem, String> gearboxes = new HashMap<>();//控制的变速箱其接收控制的信号频道映射 Control gearbox and its receiving signal channel mapping
    private final Map<WheelDriverSubsystem, String> wheels = new HashMap<>();//控制的车轮其接收控制的信号频道映射 Control wheel and its receiving signal channel mapping

    public boolean handBrake = false;
    public boolean isDrifting = false;
    public float actualThrottle = 0f;
    public float actualBrake = 0f;
    public float actualHandBrake = 0f;
    public float actualSteering = 0f;
    /**
     * 手刹状态，仅用于客户端音效
     */
    private boolean handBrakeEnabled = false;

    private final PIDController driftingPD;
    /**
     * 漂移角，弧度制
     */
    public float driftRad = 0.0f;
    public boolean drifting = false;
    private final float DRIFT_START_RAD = SparkMathKt.toRadians(5.0f);
    private final float DRIFT_END_RAD = SparkMathKt.toRadians(3.0f);
    /**
     * 漂移控制与一般控制的融合权重，0为纯原生控制，1为漂移接管
     */
    protected float driftWeight = 0;
    /**
     * 漂移转向输入，弧度制
     */
    private float driftControl = 0;

    public CarControllerSubsystem(ISubsystemHost owner, String name, CarControllerSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
        this.driftingPD = new PIDController(1.5, 0.2, 0.1, 1.0 / getPhysicsLevel().getTps(), -1, 1);
    }

    @Override
    public void onTick() {
        super.onTick();
        for (Map.Entry<String, List<String>> entry : attr.speedOutputTargets.entrySet()) {
            String signalChannel = entry.getKey();
            List<String> targets = entry.getValue();
            for (String targetName : targets)
                sendSignalToTarget(signalChannel, targetName, this.speed);
        }
        updateMoveInputs();
        if (isActive()) {
            //自动换档与手刹车冷却时间 Auto gear shift cooldown
            for (Map.Entry<ISignalReceiver, Float> entry : overrideCountDown.entrySet()) {
                if (entry.getValue() > 0) {
                    entry.setValue(entry.getValue() - 0.05f);
                }
            }
        }
        // 手刹车音效播放
        var pos = getSubPart().getPosition();
        if (handBrakeEnabled) {
            if (actualHandBrake < 0.1f) {
                handBrakeEnabled = false;
                if (getLevel().isClientSide())
                    getLevel().playLocalSound(
                            pos.x, pos.y, pos.z,
                            getAttr().getStaticAttribute().getHandBrakeOffSound(),
                            SoundSource.NEUTRAL,
                            1.0f,
                            1.0f,
                            false
                    );
            }
        } else if (actualHandBrake >= 0.1f) {
            handBrakeEnabled = true;
            if (getLevel().isClientSide())
                getLevel().playLocalSound(
                        pos.x, pos.y, pos.z,
                        getAttr().getStaticAttribute().getHandBrakeOnSound(),
                        SoundSource.NEUTRAL,
                        1.0f,
                        1.0f,
                        false
                );
        }
    }

    @Override
    public void onPrePhysicsTick() {
        super.onPrePhysicsTick();
        // 获取基础物理数据
        Vector3f localVel = getOwner().getSubPart().getLinearVelocityLocal();
        this.speed = -localVel.z; // 前进速度
        this.driftRad = (float) Math.atan2(localVel.x, Math.abs(localVel.z) + 0.1f);
        float bodyYawRate = getOwner().getSubPart().body.getAngularVelocityLocal(null).y; // 当前角速度
        // 漂移状态判断
        if (drifting && Math.abs(driftRad) < DRIFT_END_RAD) {
            drifting = false;
        } else if (!drifting && Math.abs(driftRad) > DRIFT_START_RAD && speed > 0) {
            drifting = true;
        }
        if (!ControlPreference.shouldDriftAssist(this)) driftWeight = 0.0f;
        else if (actualHandBrake > 0.5f && localVel.lengthSquared() > 4) driftWeight = 1.0f; // 强制手刹起漂时漂移控制权重为 1
        else if (drifting && localVel.lengthSquared() > 4) driftWeight = 1.0f;
        else driftWeight = 0.0f;
        if (isActive() && getOwner().getSubPart().getPart().vehicle.mode == VehicleCore.ControlMode.GROUND) {
            //更新受灵敏度影响的实际控制量，油门与刹车控制在分发控制信号时进行
            if (this.moveInput != null) {
                actualSteering = actualSteering * 0.9f + (moveInput[4] / 100f) * 0.1f;
                // 使用最大漂移角速度作为PD控制器的目标值
                float maxDriftAngularVelocity = attr.staticAttribute.getMaxDriftAngularVelocityAtSpeed(Math.abs(speed));
                if (driftWeight > 1e-4)
                    driftControl = (float) driftingPD.step(actualSteering * maxDriftAngularVelocity, bodyYawRate);
                else {
                    driftControl = 0.0f;
                    driftingPD.resetError();
                }
            }
            actualHandBrake = actualHandBrake * 0.9f + (handBrake ? 1 : 0) * 0.1f;
            distributeControlSignals();
        } else resetSignalOutputs();
    }

    @Override
    public void onPostPhysicsTick() {
        super.onPostPhysicsTick();
        for (Map.Entry<String, List<String>> entry : attr.throttleOutputTargets.entrySet()) {
            String signalChannel = entry.getKey();
            List<String> targets = entry.getValue();
            for (String targetName : targets)
                sendSignalToTarget(signalChannel, targetName, actualThrottle * 0.01f);
        }
        for (Map.Entry<String, List<String>> entry : attr.steeringOutputTargets.entrySet()) {
            String signalChannel = entry.getKey();
            List<String> targets = entry.getValue();
            var steering = actualSteering;
            for (String targetName : targets)
                sendSignalToTarget(signalChannel, targetName, steering);
        }
        for (Map.Entry<String, List<String>> entry : attr.brakeOutputTargets.entrySet()) {
            String signalChannel = entry.getKey();
            List<String> targets = entry.getValue();
            for (String targetName : targets)
                sendSignalToTarget(signalChannel, targetName, actualBrake);
        }
        for (Map.Entry<String, List<String>> entry : attr.handbrakeOutputTargets.entrySet()) {
            String signalChannel = entry.getKey();
            List<String> targets = entry.getValue();
            for (String targetName : targets)
                sendSignalToTarget(signalChannel, targetName, actualHandBrake);
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

    /**
     * 子系统初始化或载具结构发生变化时，发送空信号，根据回调重新建立连接<p>
     * Every time the vehicle structure changes, send an empty signal, and reestablish connections based on callbacks.
     *
     * @see CarControllerSubsystem#onSignalUpdated(String signalKey, ISignalSender sender)
     */
    protected void handShake() {
        for (String signalChannel : attr.engineControlOutputTargets.keySet()) {
            sendSignalToAllTargetsWithCallback(signalChannel, EmptySignal.INSTANCE, false);
        }
        for (String signalChannel : attr.wheelControlOutputTargets.keySet()) {
            sendSignalToAllTargetsWithCallback(signalChannel, EmptySignal.INSTANCE, false);
        }
        for (String signalChannel : attr.gearboxControlOutputTargets.keySet()) {
            sendSignalToAllTargetsWithCallback(signalChannel, EmptySignal.INSTANCE, false);
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
        if (channelName.equals("callback") && signalValue instanceof String controlChannel) {
            if (sender instanceof WheelDriverSubsystem wheel) {
                if (wheel.getOwner().getSubPart().getPart().vehicle != this.getOwner().getSubPart().getPart().vehicle)
                    wheels.remove(wheel);
                else {
                    wheels.put(wheel, controlChannel);
                    addCallbackTarget(controlChannel, wheel);
                }
            } else if (sender instanceof EngineSubsystem engine) {
                if (engine.getOwner().getSubPart().getPart().vehicle != this.getOwner().getSubPart().getPart().vehicle)
                    this.engines.remove(engine);
                else {
                    engines.put(engine, controlChannel);
                    addCallbackTarget(controlChannel, engine);
                    //计算引擎最大转速和最大扭矩转速的平均值 Calculate the average maximum speed and max torque speed of the motor
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
            } else if (sender instanceof MotorSubsystem motor) {
                if (motor.getOwner().getSubPart().getPart().vehicle != this.getOwner().getSubPart().getPart().vehicle)
                    this.motors.remove(motor);
                else {
                    motors.put(motor, controlChannel);
                    addCallbackTarget(controlChannel, motor);
                    //计算引擎最大转速和最大扭矩转速的平均值 Calculate the average maximum speed and max torque speed of the motor
                    avgEngineMinSpeed = 0;
                    avgEngineMaxTorqueSpeed = 0;
                    avgEngineMaxSpeed = 0;
                    for (Map.Entry<MotorSubsystem, String> entry : motors.entrySet()) {
                        float maxTorqueMinRpm = (float) (entry.getKey().attr.staticAttribute.maxPower / entry.getKey().attr.staticAttribute.maxTorque * 30f / Math.PI);
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
            } else if (sender instanceof GearboxSubsystem gearbox) {
                if (gearbox.getOwner().getSubPart().getPart().vehicle != this.getOwner().getSubPart().getPart().vehicle) {
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
                        for (GearboxSubsystem gearbox : gearboxes.keySet())
                            gearbox.setClutched(false);
                    } else {//松离合 Clutch
                        for (GearboxSubsystem gearbox : gearboxes.keySet())
                            gearbox.setClutched(true);
                    }
                    break;
                case UP_SHIFT://升档 Shift up
                    for (ISignalReceiver gearbox : gearboxes.keySet()) {
                        overrideCountDown.put(gearbox, 3f);//手动操作后一段时间内不自动切换 Clutch for a period of time after manual operation
                    }
                    for (GearboxSubsystem gearbox : gearboxes.keySet()) gearbox.upShift();
                    break;
                case DOWN_SHIFT://降档 Shift down
                    for (ISignalReceiver gearbox : gearboxes.keySet()) {
                        overrideCountDown.put(gearbox, 3f);//手动操作后一段时间内不自动切换 Clutch for a period of time after manual operation
                    }
                    for (GearboxSubsystem gearbox : gearboxes.keySet()) gearbox.downShift();
                    break;
                case HAND_BRAKE:
                    handBrake = tickCount == 0;
                    overrideCountDown.put(this, tickCount == 0 ? 100f : 0f);
                    break;
                case TOGGLE_HAND_BRAKE:
                    handBrake = !handBrake;
                    overrideCountDown.put(this, 1f);
                    break;
                default://忽视其他输入 Ignore other inputs
                    break;
            }
        }
    }

    protected void updateMoveInputs() {
        byte[] moveInput = null;
        byte[] moveInputConflict = null;
        boolean hasMoveInput = false;
        for (String inputKey : attr.staticAttribute.controlInputKeys) {//遍历输入信号 Iterate over input signalChannel
            SignalChannel signalChannel = getSignalChannel(inputKey);
            for (Map.Entry<ISignalSender, Object> entry : signalChannel.entrySet()) {
                ISignalSender sender = entry.getKey();
                Object signal = entry.getValue();
                if (signal instanceof MoveInputSignal moveInputSignal) {//找到移动输入信号 Find move input signal
                    moveInput = moveInputSignal.getMoveInput();
                    moveInputConflict = moveInputSignal.getMoveInputConflict();
                    hasMoveInput = true;
                    if (sender instanceof SeatSubsystem seat)
                        this.controller = seat.passenger;
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

    protected void distributeControlSignals() {
        if (this.moveInput != null && moveInputConflict != null) {//前进方向有输入信号 (可为0) Have forward input signal (can be 0)
            float avgEngineSpeed;
            byte[] moveInput = this.moveInput;
            if (moveInput[2] != 0) {//前进方向输入信号不为0 Forward input signal is not 0
                if (moveInput[2] * speed > 0 || (Math.abs(speed) <= 1f)) {//加速行驶 Accelerate
                    actualThrottle = actualThrottle * 0.9f + moveInput[2] * 0.1f;
                    actualBrake = actualBrake * 0.8f + 0 * 0.2f;
                    avgEngineSpeed = calculateAvgSpeedAndControl();
                    //起步时自动松离合和手刹 Auto release hand brake when starting
                    if (ControlPreference.shouldAutoHandBrake(this) && handBrake && overrideCountDown.getOrDefault(this, 0f) <= 0) {
                        handBrake = false;
                        overrideCountDown.put(this, 2f);
                    }
                    for (GearboxSubsystem gearbox : gearboxes.keySet()) {//加速时延迟升档 Delay shifting up when accelerating
                        if (overrideCountDown.getOrDefault(gearbox, 0f) <= 0) {
                            gearbox.switchGear(autoGearShift(gearbox, avgEngineSpeed, 0.4f, 0.75f, moveInput[2]));
                            //起步时自动松离合 Auto engage clutch when starting
                            if (Math.abs(speed) <= 1f) {
                                gearbox.setClutched(true);
                            }
                        }
                    }
                } else if (moveInput[2] * speed < 0) {//减速行驶 Brake
                    actualThrottle = actualThrottle * 0.8f + 0 * 0.2f;
                    actualBrake = actualBrake * 0.9f + 1 * 0.1f;
                    avgEngineSpeed = calculateAvgSpeedAndControl();
                    for (GearboxSubsystem gearbox : gearboxes.keySet()) {//减速时积极降档 Shift down early when braking
                        if (overrideCountDown.getOrDefault(gearbox, 0f) <= 0) {
                            gearbox.switchGear(autoGearShift(gearbox, avgEngineSpeed, 0.4f, 1.0f, moveInput[2]));
                        }
                    }
                }
                float maxSlip = 0;
                for (Map.Entry<WheelDriverSubsystem, String> entry : wheels.entrySet()) {
                    String channel = entry.getValue();
                    WheelDriverSubsystem wheel = entry.getKey();
                    maxSlip = Math.max(maxSlip, calculateSlipRatio(wheel));
                    if (wheel.connector.joint != null) {
                        float steeringInput = steering(actualSteering, wheel.connector);
                        float effectiveBrake = calculateEffectiveBrake(wheel, actualBrake);
                        sendCallbackToListener(channel, wheel, new WheelControlSignal(effectiveBrake, actualHandBrake, steeringInput));
                    }
                }
//                // 简易牵引力控制
//                if (maxSlip > 0.15f) {
//                    if (actualThrottle > 0)
//                        actualThrottle = Math.max(0, actualThrottle - maxSlip * 0.5f);
//                    else
//                        actualThrottle = Math.min(0, actualThrottle + maxSlip * 0.5f);
//                }
            } else {//前进方向输入信号为0 Forward input signal is 0
                actualThrottle = actualThrottle * 0.9f + 0 * 0.1f;
                avgEngineSpeed = calculateAvgSpeedAndControl();
                if (Math.abs(speed) < 1f) {//速度小于一定程度时，刹车 Brake if the speed is too low
                    actualBrake = actualBrake * 0.9f + 1 * 0.1f;
                    if (ControlPreference.shouldAutoHandBrake(this) && overrideCountDown.getOrDefault(this, 0f) <= 0) {
                        handBrake = true;
                        overrideCountDown.put(this, 0.5f);
                    }
                    for (Map.Entry<WheelDriverSubsystem, String> entry : wheels.entrySet()) {
                        String channel = entry.getValue();
                        WheelDriverSubsystem wheel = entry.getKey();
                        if (wheel.connector.joint != null) {
                            float steeringInput = steering(actualSteering, wheel.connector);
                            float effectiveBrake = calculateEffectiveBrake(wheel, actualBrake);
                            sendCallbackToListener(channel, wheel, new WheelControlSignal(effectiveBrake, actualHandBrake, steeringInput));
                        }
                    }
                    for (GearboxSubsystem gearbox : gearboxes.keySet()) {
                        if (overrideCountDown.getOrDefault(gearbox, 0f) <= 0) {
                            gearbox.setClutched(false);//停止传输动力 Stop transmission power
                            gearbox.switchGear(autoGearShift(gearbox, avgEngineSpeed, 0f, 0.5f, moveInput[2]));
                        }
                    }
                } else {//速度大于一定程度时，不刹车 Don't brake if the speed is high enough
                    actualBrake = actualBrake * 0.8f + 0 * 0.1f;
                    for (Map.Entry<WheelDriverSubsystem, String> entry : wheels.entrySet()) {
                        String channel = entry.getValue();
                        WheelDriverSubsystem wheel = entry.getKey();
                        if (wheel.connector.joint != null) {
                            float steeringInput = steering(actualSteering, wheel.connector);
                            float effectiveBrake = calculateEffectiveBrake(wheel, actualBrake);
                            sendCallbackToListener(channel, wheel, new WheelControlSignal(effectiveBrake, actualHandBrake, steeringInput));
                        }
                    }
                    for (GearboxSubsystem gearbox : gearboxes.keySet()) {//溜车时适度降档 Shift down moderately when rolling
                        if (overrideCountDown.getOrDefault(gearbox, 0f) <= 0) {
                            gearbox.switchGear(autoGearShift(gearbox, avgEngineSpeed, 0.3f, 0.6f, moveInput[2]));
                        }
                    }
                }
            }
        } else { //无输入信号 No input signal
            for (Map.Entry<EngineSubsystem, String> entry : engines.entrySet()) {
                sendCallbackToAllListeners(entry.getValue(), EmptySignal.INSTANCE);
            }
            actualBrake = actualBrake * 0.8f + 0 * 0.2f;
            if (ControlPreference.shouldAutoHandBrake(this) && !handBrake) {
                handBrake = true;
                actualHandBrake = 1f;
                overrideCountDown.put(this, 0.5f);
            }
            for (Map.Entry<WheelDriverSubsystem, String> entry : wheels.entrySet()) {
                String channel = entry.getValue();
                WheelDriverSubsystem wheel = entry.getKey();
                if (wheel.connector.joint != null) {
                    float steeringInput = steering(actualSteering, wheel.connector);
                    sendCallbackToListener(channel, wheel, new WheelControlSignal(actualBrake, actualHandBrake, steeringInput));
                }
            }
        }
    }

    private float calculateAvgSpeedAndControl() {
        float avgEngineSpeed = 0f;
        for (Map.Entry<EngineSubsystem, String> entry : engines.entrySet()) {
            sendCallbackToAllListeners(entry.getValue(), Math.abs(actualThrottle));
            avgEngineSpeed += (float) entry.getKey().getRotSpeed();
        }
        for (Map.Entry<MotorSubsystem, String> entry : motors.entrySet()) {
            sendCallbackToAllListeners(entry.getValue(), actualThrottle);
            avgEngineSpeed += entry.getKey().getRotSpeed();
        }
        if (engineCount > 1)
            avgEngineSpeed /= engineCount;
        else if (motorCount > 1)
            avgEngineSpeed /= motorCount;
        return avgEngineSpeed;
    }

    /**
     * 自动变速箱换挡，调整阈值可调整换挡的早晚程度<p>
     * Automatically switch gears of the gearbox, adjust the upShiftThreshold to adjust the timing of gear shifts.<p>
     * 约定变速箱减速比按常规顺序排列，例如-1，-3，3，1，0.5，0.2 <p>
     * Gearbox reduction ratio is arranged in the regular order, such as -1, -3, 3, 1, 0.5, 0.2
     *
     * @param gearbox            变速箱对象 Gearbox object
     * @param engineSpeed        引擎转速 Engine speed
     * @param upShiftThreshold   升挡速度阈值 Gear up-shift speed upShiftThreshold
     * @param downShiftThreshold 降挡速度阈值 Gear down-shift speed downShiftThreshold
     * @param direction          期望运动方向，正向 > 0，反向 < 0 Expected motion direction, forward > 0, reverse < 0
     * @return 换挡档位 Gear shift target
     */
    protected int autoGearShift(GearboxSubsystem gearbox, float engineSpeed, float upShiftThreshold, float downShiftThreshold, byte direction) {
        int gear = gearbox.getCurrentGear();
        if (!ControlPreference.shouldAutoSwitchGear(this))
            return gear;//手动变速箱时不自动换挡 Manual gearbox shifting is not automatic
        int upGear = Math.min(gear + 1, gearbox.gearRatios.length - 1);
        int downGear = Math.max(gear - 1, 0);
        double ratio = gearbox.gearRatios[gear];
        double upGearRatio = gearbox.gearRatios[upGear];
        double downGearRatio = gearbox.gearRatios[downGear];
        double upShiftIndex = (engineSpeed - avgEngineMaxTorqueSpeed) / Math.max(0.1f, avgEngineMaxSpeed - avgEngineMaxTorqueSpeed);
        double downShiftIndex = (engineSpeed - avgEngineMinSpeed) / Math.max(0.1f, avgEngineMaxTorqueSpeed - avgEngineMinSpeed);
        int result;
        if (speed > 0.5f) {//前进时输出正转速，正挡 Forward output positive rotational speed, positive gear
            double upGearDownShiftIndex = (engineSpeed * upGearRatio / ratio - avgEngineMinSpeed) / Math.max(0.1f, avgEngineMaxTorqueSpeed - avgEngineMinSpeed);
            double downGearUpShiftIndex = (engineSpeed * downGearRatio / ratio - avgEngineMaxTorqueSpeed) / Math.max(0.1f, avgEngineMaxSpeed - avgEngineMaxTorqueSpeed);
            if (direction < 0 && overrideCountDown.get(gearbox) <= 0 && speed < 3f) {
                result = gearbox.minNegativeGear; //最低负挡 Lowest negative gear
                gearbox.setClutched(false);//停止传输动力 Stop transmission power
            } else if (engineSpeed * ratio < 0) {
                //当前引擎输出转速与期望运动方向不符时 Current engine output rotational speed does not match the expected motion direction
                result = gearbox.minPositiveGear; //最低正挡 Lowest positive gear
                gearbox.setClutched(true);
            } else if (upShiftIndex > upShiftThreshold && upGearDownShiftIndex > downShiftThreshold) result = upGear;
            else if (downShiftIndex < downShiftThreshold && downGearUpShiftIndex < upShiftThreshold && downGear != gearbox.minNegativeGear) {
                //减速且降档后转速低于最大引擎转速时，降挡 Shift down when braking and the speed is low after gear downshift
                if (Math.abs(downGearRatio * engineSpeed / ratio) < avgEngineMaxSpeed)
                    result = downGear;
                else result = gear;
            } else result = gear;
        } else if (speed < -0.5f) {//后退时输出负转速，倒挡 Reverse output negative rotational speed, reverse gear
            double upGearUpShiftIndex = (engineSpeed * upGearRatio / ratio - avgEngineMaxTorqueSpeed) / Math.max(0.1f, avgEngineMaxSpeed - avgEngineMaxTorqueSpeed);
            double downGearDownShiftIndex = (engineSpeed * downGearRatio / ratio - avgEngineMinSpeed) / Math.max(0.1f, avgEngineMaxTorqueSpeed - avgEngineMinSpeed);
            if (direction > 0 && overrideCountDown.get(gearbox) <= 0 && speed > -3f) {
                result = gearbox.minPositiveGear;//最低正挡 Lowest positive gear
                gearbox.setClutched(false);//停止传输动力 Stop transmission power
            } else if (engineSpeed * ratio > 0) {
                //当前引擎输出转速与期望运动方向不符时 Current engine output rotational speed does not match the expected motion direction
                result = gearbox.minNegativeGear;//最低负挡 Lowest negative gear
                gearbox.setClutched(true);
            } else if (upShiftIndex > upShiftThreshold && downGearDownShiftIndex > downShiftThreshold)
                result = downGear;
            else if (downShiftIndex < downShiftThreshold && upGearUpShiftIndex < upShiftThreshold && upGear != gearbox.minPositiveGear) {
                //减速且降档后转速低于最大引擎转速时，降挡 Shift down when braking and the speed is low after gear downshift
                if (Math.abs(upGearRatio * engineSpeed / ratio) < avgEngineMaxSpeed)
                    result = upGear;
                else result = gear;
            } else result = gear;
        } else {//静止时
            if (direction >= 0)//前进起步，挂最低正挡 Forward start, set to the lowest positive gear
                result = gearbox.minPositiveGear;
            else result = gearbox.minNegativeGear;
        }
        if (result != gear)
            overrideCountDown.put(gearbox, Math.max(0.2f, gearbox.attr.staticAttribute.switchTime + 0.5f));//自动切换后一段时间内不自动切换 Cooldown after automatic gear shift
        return result;
    }

    protected float steering(float steeringInput, AdvancedConnector wheelDrive) {
        if (driftWeight <= 0f) return ackermannSteering(steeringInput, wheelDrive);
        else
            return (1 - driftWeight) * ackermannSteering(steeringInput, wheelDrive) + driftWeight * driftSteering(wheelDrive);
    }

    protected float ackermannSteering(float steeringInput, AdvancedConnector wheelDrive) {
        New6Dof joint = wheelDrive.joint;
        Vector3f pivot = new Vector3f();
        if (wheelDrive.subPart.body == joint.getBodyA()) joint.getPivotA(pivot);
        else joint.getPivotB(pivot);
        if (steeringInput == 0) {
            return 0;
        } else {
            //实际转向半径(米) Actual steering radius (m)
            float steeringRadius = ControlPreference.shouldLimitSpeedTurning(this)
                    ? attr.staticAttribute.getSteeringRadiusAtSpeed(speed) / steeringInput // 使用动态转向半径映射表，根据当前速度获取合适的转向半径
                    : attr.staticAttribute.getMinSteeringRadius() / steeringInput; // 否则使用最小转向半径
            double deltaRadius = pivot.x - attr.staticAttribute.steeringCenter.x;
            deltaRadius *= Math.signum(steeringInput);
            double deltaForward = pivot.z - attr.staticAttribute.steeringCenter.z;
            return (float) Math.atan(deltaForward / (steeringRadius + deltaRadius));
        }
    }

    /**
     * 漂移模式下自动反打方向，并使用PD控制器逼近目标角速度，仅前轮转向
     *
     * @return 轮胎转向角度，以弧度为单位
     */
    protected float driftSteering(AdvancedConnector wheelDrive) {
        New6Dof joint = wheelDrive.joint;
        Vector3f pivot = new Vector3f();
        if (wheelDrive.subPart.body == joint.getBodyA()) joint.getPivotA(pivot);
        else joint.getPivotB(pivot);
        return pivot.z <= getAttr().getStaticAttribute().getSteeringCenter().z() ? 0.5f * driftRad - driftControl : 0;
    }

    @Override
    public Map<String, List<String>> getTargetNames() {
        Map<String, List<String>> result = new HashMap<>(attr.getEngineControlOutputTargets());
        result.putAll(attr.getGearboxControlOutputTargets());
        result.putAll(attr.getWheelControlOutputTargets());
        result.putAll(attr.getSpeedOutputTargets());
        result.putAll(attr.getThrottleOutputTargets());
        result.putAll(attr.getSteeringOutputTargets());
        result.putAll(attr.getBrakeOutputTargets());
        result.putAll(attr.getHandbrakeOutputTargets());
        return result;
    }

    /**
     * 计算考虑ABS的有效刹车力
     *
     * @param wheel    轮胎驱动子系统
     * @param rawBrake 原始刹车力
     * @return 经过ABS调整后的有效刹车力
     */
    protected float calculateEffectiveBrake(WheelDriverSubsystem wheel, float rawBrake) {
        float vehicleSpeed = Math.abs(this.speed);
        // 使用轮驱系统的ABS配置
        if (!wheel.attr.staticAttribute.isAbsEnabled() || rawBrake <= 0 || vehicleSpeed < 0.5f) {
            // 不使用ABS或刹车力为0或车速小于2m/s时，直接返回原始刹车力
            return rawBrake;
        }
        float slipRatio = calculateSlipRatio(wheel);
        // ABS控制逻辑
        float targetSlipRatio = wheel.attr.staticAttribute.getAbsTargetSlipRatio();
        float effectiveBrake = rawBrake;

        if (slipRatio > targetSlipRatio) {
            // 滑移率过高，减少刹车力
            float reductionFactor = Math.clamp(1.0f - (slipRatio - targetSlipRatio) / 0.15f, 0.1f, 1.0f);
            effectiveBrake = rawBrake * reductionFactor;
        }

        return Math.max(0f, Math.min(1f, effectiveBrake));
    }

    protected float calculateSlipRatio(WheelDriverSubsystem wheel) {
        // 获取轮胎角速度
        float angularVelocity = -wheel.getRelativeAngularVel().get(0); // X轴角速度
        // 计算轮胎线速度
        float wheelLinearSpeed = Math.abs(angularVelocity * wheel.attr.staticAttribute.getAbsWheelRadius());
        float absSpeed = Math.abs(speed);
        // 计算滑移率
        return Math.abs(absSpeed - wheelLinearSpeed) / (Math.min(absSpeed, wheelLinearSpeed) + 0.1f);
    }
}
