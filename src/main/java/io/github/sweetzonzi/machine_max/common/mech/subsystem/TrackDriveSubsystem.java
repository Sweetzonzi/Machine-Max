package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import com.jme3.bullet.RotationOrder;
import com.jme3.bullet.joints.New6Dof;
import com.jme3.bullet.joints.motors.MotorParam;
import com.jme3.bullet.joints.motors.RotationMotor;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.energy.IMechPowerConsumer;
import io.github.sweetzonzi.machine_max.common.mech.energy.MechPower;
import io.github.sweetzonzi.machine_max.common.mech.signal.EmptySignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalChannel;
import io.github.sweetzonzi.machine_max.common.mech.signal.TrackControlSignal;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.TrackDriveSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AdvancedConnector;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackMath;
import jme3utilities.math.MyQuaternion;
import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 履带驱动子系统：只操作主动轮关节的滚动轴。
 *
 * <p>支持多路 {@link MechPower} 汇合（主传动 + 可选转向传动），反馈按各 producer 的
 * 最近一次输入转速比例分摊。本类不持有 {@code TrackLink}，不检查主动轮是否属于某条履带；
 * 只要求连接器已连接、被驱动轮有有限正半径。</p>
 */
public class TrackDriveSubsystem extends BasicSubsystem implements IMechPowerConsumer {
    @Getter
    public final TrackDriveSubsystemAttr attr;
    @Getter
    public final AdvancedConnector connector;
    private final float MAX_SPEED;
    private final float MAX_DRIVE_TORQUE;
    private final float MAX_BRAKE_TORQUE;
    private final float MAX_HAND_BRAKE_TORQUE;

    /** 目标转速趋近 0 时反推扭矩的分母下限，避免扭矩被无限放大。 */
    private static final float MIN_SPEED_FOR_TORQUE = 0.25f;
    private static final TrackControlSignal FAIL_SAFE_CONTROL =
            new TrackControlSignal(1f, 0f, 0f);

    /** producer 名 -> 最近一次收到的机械功率。物理线程写入/读取。 */
    private final Map<String, MechPower> received = new ConcurrentHashMap<>();

    private volatile float feedbackSpeed = 0f;
    private volatile float driveTorque = 0f;

    public TrackDriveSubsystem(ISubsystemHost owner, String name, TrackDriveSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
        this.MAX_SPEED = attr.staticAttribute.maxSpeed;
        this.MAX_DRIVE_TORQUE = attr.staticAttribute.maxDriveTorque;
        this.MAX_BRAKE_TORQUE = attr.staticAttribute.maxBrakeTorque;
        this.MAX_HAND_BRAKE_TORQUE = attr.staticAttribute.maxHandBrakeTorque;

        AbstractConnector candidate = owner.getSubPart().connectors.get(attr.controlledConnector);
        if (candidate instanceof AdvancedConnector advancedConnector) {
            this.connector = advancedConnector;
        } else {
            this.connector = null;
            MachineMax.LOGGER.error("履带驱动子系统 {} 无法找到特殊连接点 {}", name, attr.controlledConnector);
        }
    }

    @Override
    public void onTick() {
        super.onTick();
        if (this.connector != null && this.connector.joint instanceof New6Dof joint) {
            if (RotationOrder.XZY != joint.getRotationOrder()) {
                joint.setRotationOrder(RotationOrder.XZY);
            }
        }
    }

    @Override
    public void onPrePhysicsTick() {
        super.onPrePhysicsTick();
        if (this.connector == null || !(this.connector.joint instanceof New6Dof joint)) {
            this.driveTorque = 0f;
            return;
        }
        if (this.connector.attachedConnector == null || !isDriveUsable()) {
            joint.getRotationMotor(0).setMotorEnabled(false);
            this.driveTorque = 0f;
            return;
        }

        TrackControlSignal control = getControlInput();

        RotationMotor rollingMotor = joint.getRotationMotor(0);
        if (!isActive() || isDestroyed()) {
            rollingMotor.setMotorEnabled(false);
            this.driveTorque = 0f;
            return;
        }

        float omega = inputSpeed();
        float torque = 0f;
        for (Map.Entry<String, MechPower> entry : received.entrySet()) {
            if (!isPowerPathConnected(entry.getKey())) continue;
            MechPower power = entry.getValue();
            if (power == null || power.isEmpty()) continue;
            float speed = power.speed();
            if (!Float.isFinite(speed)) continue;
            torque += TrackMath.torqueFromPower(power.power(), speed, MIN_SPEED_FOR_TORQUE);
        }

        torque = Math.clamp(torque, -MAX_DRIVE_TORQUE, MAX_DRIVE_TORQUE)
                * Math.clamp(control.clutchEngagement(), 0f, 1f);
        float brakeTorque = control.brakeControl() > 0.01f ? control.brakeControl() * MAX_BRAKE_TORQUE : 0f;
        float handBrakeTorque = control.handBrakeControl() > 0.01f
                ? control.handBrakeControl() * MAX_HAND_BRAKE_TORQUE : 0f;
        float targetOmega = Float.isFinite(control.targetSpeed()) ? control.targetSpeed() / radius() : Float.NaN;
        TrackMath.MotorCommand command = TrackMath.singleFlowMotor(
                Math.clamp(omega, -MAX_SPEED, MAX_SPEED), torque, -getRelativeAngularVel().x,
                targetOmega, brakeTorque + handBrakeTorque, MAX_BRAKE_TORQUE);
        this.driveTorque = command.driveTorque();
        rollingMotor.setMotorEnabled(command.maxTorque() > 0f);
        rollingMotor.set(MotorParam.MaxMotorForce, command.maxTorque());
        rollingMotor.set(MotorParam.TargetVelocity, -command.speed());
    }

    @Override
    public void onPostPhysicsTick() {
        super.onPostPhysicsTick();
        if (this.connector == null || !(this.connector.joint instanceof New6Dof)) {
            this.feedbackSpeed = 0f;
            this.driveTorque = 0f;
            for (String signalKey : attr.speedOutputs.keySet()) {
                sendSignalToAllTargets(signalKey, 0f);
            }
            for (String signalKey : attr.forceOutputs.keySet()) {
                sendSignalToAllTargets(signalKey, 0f);
            }
            return;
        }
        Vector3f relativeAngularVel = getRelativeAngularVel();
        this.feedbackSpeed = -relativeAngularVel.get(0);

        float radius = radius();
        for (String signalKey : attr.speedOutputs.keySet()) {
            sendSignalToAllTargets(signalKey, linearSpeed());
        }
        for (String signalKey : attr.forceOutputs.keySet()) {
            sendSignalToAllTargets(signalKey, radius > 0f ? driveTorque / radius : 0f);
        }
    }

    @Override
    public void onVehicleStructureChanged() {
        super.onVehicleStructureChanged();
        received.clear();
        feedbackSpeed = 0f;
        driveTorque = 0f;
    }

    @Override
    public void onMechPowerReceived(String producerName, MechPower power) {
        if (producerName == null) return;
        if (power == null) {
            received.remove(producerName);
        } else {
            received.put(producerName, power);
        }
    }

    @Override
    public float getFeedbackSpeed() {
        return feedbackSpeed;
    }

    @Override
    public float getFeedbackSpeed(String producerName) {
        if (producerName == null) {
            return feedbackSpeed;
        }
        float actual = feedbackSpeed;
        float total = 0f;
        float absTotal = 0f;
        for (Map.Entry<String, MechPower> entry : received.entrySet()) {
            if (!isPowerPathConnected(entry.getKey())) continue;
            MechPower power = entry.getValue();
            if (power == null || power.isEmpty() || !Float.isFinite(power.speed())) continue;
            total += power.speed();
            absTotal += Math.abs(power.speed());
        }
        MechPower own = received.get(producerName);
        if (own == null || own.isEmpty() || !Float.isFinite(own.speed())) {
            return 0f;
        }
        return TrackMath.feedbackForProducer(actual, own.speed(), total, absTotal, MAX_SPEED);
    }

    @Override
    public void setFeedbackSpeed(float speed) {
        this.feedbackSpeed = speed;
    }

    @Override
    public boolean isPowerPathConnected(String producerName) {
        return getControlInput().clutchEngagement() > 0f && isDriveUsable();
    }

    public boolean isDriveUsable() {
        if (isDestroyed()) return false;
        if (connector == null || connector.joint == null || connector.attachedConnector == null) {
            return false;
        }
        SubPart driven = drivenSubPart();
        return driven != null && !driven.isDestroyed() && Float.isFinite(radius()) && radius() > 0f;
    }

    /**
     * 连接器另一侧的被驱动轮。
     */
    public SubPart drivenSubPart() {
        if (connector == null || connector.attachedConnector == null) return null;
        SubPart other = connector.attachedConnector.subPart;
        if (other == null || other == connector.subPart) return null;
        return other;
    }

    public float radius() {
        SubPart driven = drivenSubPart();
        if (driven == null) return 0f;
        float value = driven.getMaxWheelRadius();
        return Float.isFinite(value) && value > 0f ? value : 0f;
    }

    public float linearSpeed() {
        return feedbackSpeed * radius();
    }

    /** 物理线程：动力链折算到主动轮的输入转速，符号包含倒挡。 */
    public float inputSpeed() {
        float speed = 0f;
        for (Map.Entry<String, MechPower> entry : received.entrySet()) {
            if (!isPowerPathConnected(entry.getKey())) continue;
            MechPower power = entry.getValue();
            if (power != null && !power.isEmpty() && Float.isFinite(power.speed())) speed += power.speed();
        }
        return speed;
    }

    public float linearMaxSpeed() {
        return MAX_SPEED * radius();
    }

    public float linearMaxDriveForce() {
        return radius() > 0f ? MAX_DRIVE_TORQUE / radius() : 0f;
    }

    public float linearMaxBrakeForce() {
        return radius() > 0f ? MAX_BRAKE_TORQUE / radius() : 0f;
    }

    private TrackControlSignal getControlInput() {
        for (String signalKey : attr.staticAttribute.controlInputKeys) {
            SignalChannel channel = getSignalChannel(signalKey);
            Object signal = channel.getFirstSignal();
            if (signal == null || signal instanceof EmptySignal) continue;
            if (signal instanceof TrackControlSignal control) {
                return control;
            }
        }
        return FAIL_SAFE_CONTROL;
    }

    public Vector3f getRelativeAngularVel() {
        Vector3f result = new Vector3f();
        if (connector.joint != null) {
            Vector3f angularVelA = connector.joint.getBodyA().getAngularVelocity(null);
            Vector3f angularVelB = connector.joint.getBodyB().getAngularVelocity(null);
            Vector3f relativeVelInWorld = angularVelB.subtract(angularVelA);
            Quaternion localToWorld = connector.joint.getBodyA().getPhysicsRotation(null);
            MyQuaternion.rotateInverse(localToWorld, relativeVelInWorld, result);
        }
        return result;
    }

    @Override
    public List<String> getAcceptedChannels() {
        return attr.staticAttribute.getControlInputKeys();
    }

    @Override
    public Map<String, List<String>> getTargetNames() {
        Map<String, List<String>> result = new HashMap<>(4);
        result.putAll(attr.speedOutputs);
        result.putAll(attr.forceOutputs);
        return result;
    }
}
