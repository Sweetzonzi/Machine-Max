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
import io.github.sweetzonzi.machine_max.common.mech.signal.*;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.WheelDriverSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AdvancedConnector;
import jme3utilities.math.MyQuaternion;
import lombok.Getter;
import net.minecraft.sounds.SoundSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WheelDriverSubsystem extends BasicSubsystem implements IMechPowerConsumer {
    @Getter
    public final WheelDriverSubsystemAttr attr;
    @Getter
    public final AdvancedConnector connector;
    private final float MAX_SPEED;
    private final float MAX_STEERING_SPEED;
    private final float MAX_DRIVE_FORCE;
    private final float MAX_BRAKE_FORCE;
    private final float MAX_HAND_BRAKE_FORCE;
    private final float MAX_STEERING_FORCE;
    private volatile boolean isBraking = false;
    private boolean wasBraking = false;

    private MechPower receivedPower = MechPower.ZERO;
    private float feedbackSpeed = 0;

    public WheelDriverSubsystem(ISubsystemHost owner, String name, WheelDriverSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
        MAX_SPEED = attr.staticAttribute.rollingAxis.maxSpeed();
        MAX_STEERING_SPEED = attr.staticAttribute.steeringAxis.maxSpeed();
        MAX_DRIVE_FORCE = attr.staticAttribute.rollingAxis.maxForce();
        MAX_BRAKE_FORCE = attr.staticAttribute.rollingAxis.maxBrakeForce();
        MAX_HAND_BRAKE_FORCE = attr.staticAttribute.rollingAxis.maxHandBrakeForce();
        MAX_STEERING_FORCE = attr.staticAttribute.steeringAxis.maxForce();
        if (owner.getSubPart() != null &&
                owner.getSubPart().connectors.get(this.attr.controlledConnector) instanceof AdvancedConnector advancedConnector) {
            this.connector = advancedConnector;
        } else {
            this.connector = null;
            MachineMax.LOGGER.error("轮胎驱动子系统 {} 无法找到特殊连接点 {}", name, this.attr.controlledConnector);
        }
    }

    @Override
    public void onTick() {
        super.onTick();
        if (this.connector != null && this.connector.joint instanceof New6Dof joint) {
            //检查并设置关节的旋转顺序 Check and set joint rotation order
            if (RotationOrder.XZY != joint.getRotationOrder()) {
                joint.setRotationOrder(RotationOrder.XZY);
            }
        }
        // 播放音效
        if (getLevel().isClientSide()) {
            Vector3f pos = getSubPart().getPosition();
            if (isBraking && !wasBraking) {
                wasBraking = true;
                getLevel().playLocalSound(
                        pos.x, pos.y, pos.z,
                        getAttr().getStaticAttribute().getBrakeOnSound(),
                        SoundSource.NEUTRAL,
                        1f,
                        1f,
                        false
                );
            } else if (!isBraking && wasBraking) {
                wasBraking = false;
                getLevel().playLocalSound(
                        pos.x, pos.y, pos.z,
                        getAttr().getStaticAttribute().getBrakeOffSound(),
                        SoundSource.NEUTRAL,
                        1f,
                        1f,
                        false
                );
            }
        }
    }

    @Override
    public void onPrePhysicsTick() {
        super.onPrePhysicsTick();
        //计算收到的机械功率 Calculate received mechanical power
        float totalPower = receivedPower.power();//计算收到的总功率，正功率代表加速，负功率代表减速 Calculate total power, positive power means acceleration, negative power means deceleration
        float speed = receivedPower.speed();
        if (Float.isNaN(totalPower) || Float.isNaN(speed)) {
            totalPower = 0f;
            speed = 0f;
        }
        if (this.connector != null && this.connector.joint instanceof New6Dof joint) {
            Signal<?> controlSignal = getControlInput();
            RotationMotor rollingMotor = joint.getRotationMotor(0);
            RotationMotor steeringMotor = joint.getRotationMotor(1);

            if (this.isActive() && !isDestroyed() && controlSignal instanceof WheelControlSignal wheelControlSignal) {
                //处理轮胎旋转 Handle rolling
                rollingMotor.setMotorEnabled(true);
                float torque = 0;
                float brakeTorque = wheelControlSignal.getBrakeControl() * MAX_BRAKE_FORCE;
                if (brakeTorque > 0.05 * MAX_BRAKE_FORCE && !isBraking) {
                    isBraking = true;
                } else if (brakeTorque < 0.05 * MAX_BRAKE_FORCE && isBraking) {
                    isBraking = false;
                }
                float handBrakeTorque = wheelControlSignal.getHandBrakeControl() * MAX_HAND_BRAKE_FORCE;
                if (speed != 0) torque = totalPower / Math.abs(speed);//正扭矩代表加速，负扭矩代表减速
                torque = Math.clamp(torque, -MAX_DRIVE_FORCE, MAX_DRIVE_FORCE);//限制最大驱动力 Limit maximum drive force
                torque -= brakeTorque + handBrakeTorque;//施加刹车力矩 Apply braking torque
                rollingMotor.set(MotorParam.MaxMotorForce, Math.abs(torque));
                if (torque > 0) {//加速过程 Accelerating
                    rollingMotor.set(MotorParam.TargetVelocity, Math.signum(speed) * Math.min(30 + Math.abs(speed), MAX_SPEED));
                } else {//减速过程 Decelerating
                    rollingMotor.set(MotorParam.TargetVelocity, 0);
                }
                //处理转向 Handle steering
                steeringMotor.setMotorEnabled(true);
                steeringMotor.setServoEnabled(true);
                steeringMotor.set(MotorParam.MaxMotorForce, MAX_STEERING_FORCE);
                steeringMotor.set(MotorParam.ServoTarget, wheelControlSignal.getSteeringControl());
                steeringMotor.set(MotorParam.TargetVelocity, MAX_STEERING_SPEED);
            } else {
                rollingMotor.setMotorEnabled(false);
                steeringMotor.setMotorEnabled(true);
                steeringMotor.setServoEnabled(true);
                steeringMotor.set(MotorParam.TargetVelocity, 0f);
                rollingMotor.set(MotorParam.MaxMotorForce, MAX_STEERING_FORCE);
            }
        }
    }

    @Override
    public void onPostPhysicsTick() {
        super.onPostPhysicsTick();
        if (this.connector != null && this.connector.joint instanceof New6Dof) {
            Vector3f relativeAngle = getRelativeAngle();
            Vector3f relativeAngularVel = getRelativeAngularVel();
            for (String signalKey : attr.rollingSpeedOutputs.keySet())//转动速度信号
                sendSignalToAllTargets(signalKey, relativeAngularVel.get(0));
            for (String signalKey : attr.steeringAngleOutputs.keySet())//转向位置信号
                sendSignalToAllTargets(signalKey, -relativeAngle.get(1));
            feedbackSpeed = relativeAngularVel.get(0);//反馈转动速度信号
        }
    }

    @Override
    public void onMechPowerReceived(String producerName, MechPower power) {
        this.receivedPower = power;
    }

    @Override
    public float getFeedbackSpeed() {
        return feedbackSpeed;
    }

    @Override
    public void setFeedbackSpeed(float speed) {
        this.feedbackSpeed = speed;
    }

    private Signal<?> getControlInput() {
        SignalChannel controlChannels = new SignalChannel();
        //获取控制信号
        for (String signalKey : attr.staticAttribute.controlSignalKeys) {
            controlChannels = getSignalChannel(signalKey);//获取目标速度信号(马达，仅控制速度)
            if (!controlChannels.isEmpty() && !(controlChannels.getFirstSignal() instanceof EmptySignal)) break;
        }
        if (controlChannels.getFirstSignal() instanceof WheelControlSignal wheelControlSignal) {//若输入为原始移动输入信号
            return wheelControlSignal;
        } else if (controlChannels.getFirstSignal() instanceof MoveInputSignal moveInputSignal)
            return new WheelControlSignal(
                    moveInputSignal.getMoveInput()[2] / 100f,
                    moveInputSignal.getMoveInput()[4]
            );
        else return EmptySignal.INSTANCE;//若为其他任何类型的信号则不对速度进行控制
    }

    public Vector3f getRelativeAngle() {
        Vector3f result = new Vector3f();
        connector.joint.getAngles(result);
        return result;
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
    public Map<String, List<String>> getTargetNames() {
        Map<String, List<String>> result = new HashMap<>(4);
        result.putAll(attr.rollingSpeedOutputs);
        result.putAll(attr.steeringAngleOutputs);
        return result;
    }
}
