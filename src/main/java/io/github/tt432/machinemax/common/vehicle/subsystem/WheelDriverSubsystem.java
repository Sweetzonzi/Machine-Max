package io.github.tt432.machinemax.common.vehicle.subsystem;

import com.jme3.bullet.RotationOrder;
import com.jme3.bullet.joints.New6Dof;
import com.jme3.bullet.joints.motors.MotorParam;
import com.jme3.bullet.joints.motors.RotationMotor;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.mojang.datafixers.util.Pair;
import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.vehicle.ISubsystemHost;
import io.github.tt432.machinemax.common.vehicle.attr.subsystem.WheelDriverSubsystemAttr;
import io.github.tt432.machinemax.common.vehicle.connector.SpecialConnector;
import io.github.tt432.machinemax.common.vehicle.signal.*;
import jme3utilities.math.MyQuaternion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WheelDriverSubsystem extends AbstractSubsystem {
    public final WheelDriverSubsystemAttr attr;
    public final SpecialConnector connector;
    private final float MAX_SPEED;
    private final float MAX_STEERING_SPEED;
    private final float MAX_DRIVE_FORCE;
    private final float MAX_BRAKE_FORCE;
    private final float MAX_HAND_BRAKE_FORCE;
    private final float MAX_STEERING_FORCE;

    public WheelDriverSubsystem(ISubsystemHost owner, String name, WheelDriverSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
        MAX_SPEED = attr.rollingAxis.maxSpeed();
        MAX_STEERING_SPEED = attr.steeringAxis.maxSpeed();
        MAX_DRIVE_FORCE = attr.rollingAxis.maxForce();
        MAX_BRAKE_FORCE = attr.rollingAxis.maxBrakeForce();
        MAX_HAND_BRAKE_FORCE = attr.rollingAxis.maxHandBrakeForce();
        MAX_STEERING_FORCE = attr.steeringAxis.maxForce();
        if (owner.getPart() != null &&
                owner.getPart().allConnectors.get(this.attr.controlledConnector) instanceof SpecialConnector specialConnector) {
            this.connector = specialConnector;
        } else {
            this.connector = null;
            MachineMax.LOGGER.error("轮胎驱动子系统 {} 无法找到特殊对接口 {}", name, this.attr.controlledConnector);
        }
    }

    @Override
    public void onTick() {
        super.onTick();
        if (this.connector != null && this.connector.joint instanceof New6Dof joint) {
            //检查并设置关节的旋转顺序 Check and set joint rotation order
            if (RotationOrder.XYZ != joint.getRotationOrder()) {
                joint.setRotationOrder(RotationOrder.XYZ);
            }
        }
    }

    @Override
    public void onPrePhysicsTick() {
        super.onPrePhysicsTick();
        //计算收到功率 Calculate received power
        float totalPower = 0f;
        float speed = 0f;
        SignalChannel powers = getSignalChannel("power");
        for (Map.Entry<ISignalSender, Object> entry : powers.entrySet()) {
            if (entry.getValue() instanceof MechPowerSignal power) {
                totalPower += power.getPower();//计算收到的总功率，正功率代表加速，负功率代表减速 Calculate total power, positive power means acceleration, negative power means deceleration
                speed += power.getSpeed();
                ISignalSender sender = entry.getKey();
                if (sender instanceof ISignalReceiver receiver) {//当发送者同时也是接收者时，自动反馈速度到发送者 If sender is also receiver, send speed back to sender
                    addCallbackTarget("speed_feedback", receiver);
                }
            }
        }
        if (Float.isNaN(totalPower) || Float.isNaN(speed)) {
            totalPower = 0f;
            speed = 0f;
            MachineMax.LOGGER.error("{}收到机械功率信号不正确，{}", this.getName(), powers);
        }
        if (this.connector != null && this.connector.joint instanceof New6Dof joint) {
            Signal<?> controlSignal = getControlInput();
            RotationMotor rollingMotor = joint.getRotationMotor(0);
            RotationMotor steeringMotor = joint.getRotationMotor(1);

            if (this.isActive() && controlSignal instanceof WheelControlSignal wheelControlSignal) {
                //处理轮胎旋转 Handle rolling
                rollingMotor.setMotorEnabled(true);
                float torque = 0;
                float brakeTorque = wheelControlSignal.getBrakeControl() * MAX_BRAKE_FORCE;
                float handBrakeTorque = wheelControlSignal.getHandBrakeControl() * MAX_HAND_BRAKE_FORCE;
                if (speed != 0) torque = totalPower / Math.abs(speed);//正扭矩代表加速，负扭矩代表减速
                torque = Math.clamp(torque, -MAX_DRIVE_FORCE, MAX_DRIVE_FORCE);//限制最大驱动力 Limit maximum drive force
                torque -= brakeTorque + handBrakeTorque;//施加刹车力矩 Apply braking torque
                rollingMotor.set(MotorParam.MaxMotorForce, Math.abs(torque));
                if (torque > 0) {//加速过程 Accelerating
                    rollingMotor.set(MotorParam.TargetVelocity, Math.signum(speed) * MAX_SPEED);
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
            for (String signalKey : attr.rollingAxis.speedSignalOutputs().keySet())//转动速度信号
                sendSignalToAllTargets(signalKey, relativeAngularVel.get(1));
            for (String signalKey : attr.steeringAxis.positionSignalOutputs().keySet())//转向位置信号
                sendSignalToAllTargets(signalKey, -relativeAngle.get(1));
            sendCallbackToAllListeners("speed_feedback", relativeAngularVel.get(0));//反馈转动速度信号
        }
    }

    private Signal<?> getControlInput() {
        SignalChannel controlChannels = new SignalChannel();
        //获取控制信号
        for (String signalKey : attr.controlSignalKeys) {
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
        else return new EmptySignal();//若为其他任何类型的信号则不对速度进行控制
    }

    private Vector3f getRelativeAngle() {
        Vector3f result = new Vector3f();
        connector.joint.getAngles(result);
        return result;
    }

    private Vector3f getRelativeAngularVel() {
        Vector3f result = new Vector3f();
        Vector3f angularVelA = connector.joint.getBodyA().getAngularVelocity(null);
        Vector3f angularVelB = connector.joint.getBodyB().getAngularVelocity(null);
        Vector3f relativeVelInWorld = angularVelB.subtract(angularVelA);
        Quaternion localToWorld = connector.joint.getBodyA().getPhysicsRotation(null);
        MyQuaternion.rotateInverse(localToWorld, relativeVelInWorld, result);
        return result;
    }

    @Override
    public Map<String, List<String>> getTargetNames() {
        Map<String, List<String>> result = new HashMap<>(4);
        result.putAll(attr.rollingAxis.speedSignalOutputs());
        result.putAll(attr.steeringAxis.positionSignalOutputs());
        return result;
    }
}
