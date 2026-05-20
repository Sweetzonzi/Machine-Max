package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import com.jme3.bullet.RotationOrder;
import com.jme3.bullet.joints.New6Dof;
import com.jme3.bullet.joints.motors.MotorParam;
import com.jme3.bullet.joints.motors.RotationMotor;
import com.jme3.bullet.joints.motors.TranslationMotor;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.MotorAttr;
import io.github.sweetzonzi.machine_max.common.mech.energy.IMechPowerConsumer;
import io.github.sweetzonzi.machine_max.common.mech.energy.MechPower;
import io.github.sweetzonzi.machine_max.common.mech.signal.*;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.JointDriverSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AdvancedConnector;
import io.github.sweetzonzi.machine_max.util.data.Axis;
import jme3utilities.math.MyQuaternion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JointDriverSubsystem extends BasicSubsystem implements IMechPowerConsumer {
    public final JointDriverSubsystemAttr attr;
    public final AdvancedConnector connector;
    private final Float[] powerAllocation = new Float[6];
    private final Float[] MAX_FORCE = new Float[6];
    private final Float[] MAX_BRAKE_FORCE = new Float[6];
    private final Float[] MAX_SPEED = new Float[6];
    private final float TOTAL_POWER_WEIGHT;
    private boolean connected = false;

    private MechPower receivedPower = MechPower.ZERO;
    private float feedbackSpeed = 0;

    public JointDriverSubsystem(ISubsystemHost owner, String name, JointDriverSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
        if (owner.getSubPart() != null &&
                owner.getSubPart().connectors.get(this.attr.controlledConnector) instanceof AdvancedConnector advancedConnector) {
            this.connector = advancedConnector;
            float totalPowerWeight = 0f;
            for (Axis axis : this.attr.axisParams.keySet()) {
                MotorAttr axisAttr = this.attr.axisParams.get(axis);
                if (axisAttr.needsPower() && axisAttr.maxForce() > 0.0)
                    totalPowerWeight += axisAttr.maxForce();
                MAX_FORCE[axis.getValue()] = Math.max(0f, axisAttr.maxForce());
                MAX_BRAKE_FORCE[axis.getValue()] = Math.max(0f, axisAttr.maxBrakeForce());
                MAX_SPEED[axis.getValue()] = Math.max(0f, axisAttr.maxSpeed());
            }
            TOTAL_POWER_WEIGHT = Math.max(1f, totalPowerWeight);
        } else {
            this.connector = null;
            TOTAL_POWER_WEIGHT = 1f;
            MachineMax.LOGGER.error("关节驱动子系统 {} 无法找到特殊连接点 {}", name, this.attr.controlledConnector);
        }
    }

    @Override
    public void onTick() {
        super.onTick();
        if (this.connector != null && this.connector.joint instanceof New6Dof joint) {
            //检查并设置关节的旋转顺序
            if (RotationOrder.valueOf(attr.rotationOrder) != joint.getRotationOrder()) {
                joint.setRotationOrder(RotationOrder.valueOf(attr.rotationOrder));
            }
            if (!connected) {
                for (Axis axis : attr.axisParams.keySet()) {//设置关节的各轴弹簧刚度阻尼参数
                    MotorAttr jointAttr = attr.axisParams.get(axis);
                    if (jointAttr.lowerLimit() != null)
                        joint.set(MotorParam.LowerLimit, axis.getValue(), (float) (jointAttr.lowerLimit() * (axis.getValue() <= 2 ? 1 : Math.PI / 180)));
                    if (jointAttr.upperLimit() != null)
                        joint.set(MotorParam.UpperLimit, axis.getValue(), (float) (jointAttr.upperLimit() * (axis.getValue() <= 2 ? 1 : Math.PI / 180)));
                    if (jointAttr.equilibrium() != null)
                        joint.set(MotorParam.Equilibrium, axis.getValue(), (float) (jointAttr.equilibrium() * (axis.getValue() <= 2 ? 1 : Math.PI / 180)));
                    if (jointAttr.stiffness() != null) {
                        joint.set(MotorParam.Stiffness, axis.getValue(), (float) (jointAttr.stiffness() * (axis.getValue() <= 2 ? 1 : Math.PI / 180)));
                        joint.enableSpring(axis.getValue(), true);
                    }
                    if (jointAttr.damping() != null) {
                        joint.set(MotorParam.Damping, axis.getValue(), (float) (jointAttr.damping() * (axis.getValue() <= 2 ? 1 : Math.PI / 180)));
                        joint.enableSpring(axis.getValue(), true);
                    }
                }
                connected = true;
            }
            distributePower();//分配收到的功率至各轴
        } else connected = false;
    }

    @Override
    public void onPrePhysicsTick() {
        super.onPrePhysicsTick();
        if (this.connector != null && this.connector.joint instanceof New6Dof joint) {
            MotorControlSignal controlSignal = getControlInput();
            Float[] speedControl = controlSignal.getValue().getFirst();
            Float[] positionControl = controlSignal.getValue().getSecond();
            //TODO:处理平动控制信号
            TranslationMotor translationMotor = joint.getTranslationMotor();
            Vector3f relativeLinearVel = getRelativeLinearVel();
//            for (int axis : attr.axisParams.keySet()) {
//                if (axis <= 2) {
//                    StaticWheelRollingAxisAttr axisAttr = attr.axisParams.get(axis);
//                    //处理速度控制信号
//                    if (speedControl[axis] == null) {
//                        translationMotor.setMotorEnabled(axis, false);
//                    } else {
//                        translationMotor.setMotorEnabled(axis, true);
//                    }
//                    //处理位置控制信号
//                    if (positionControl[axis] == null) translationMotor.setServoEnabled(axis, false);
//                    else if (powerAllocation[axis] != null) {
//                        translationMotor.setServoEnabled(axis, true);
//                    }
//                } else break;//跳过旋转控制信号
//            }
            //处理转动控制信号
            Vector3f relativeAngularVel = getRelativeAngularVel();
            for (Axis axis : attr.axisParams.keySet()) {
                if (axis.getValue() <= 2) continue;//跳过平动控制信号
                RotationMotor rotationMotor = joint.getRotationMotor(axis.getValue() - 3);
                MotorAttr axisAttr = attr.axisParams.get(axis);
                //处理速度控制信号
                if (speedControl[axis.getValue()] == null) rotationMotor.setMotorEnabled(false);
                else {
                    rotationMotor.setMotorEnabled(true);
                    rotationMotor.set(MotorParam.TargetVelocity, speedControl[axis.getValue()]);
                    float delta = speedControl[axis.getValue()] - relativeAngularVel.get(axis.getValue() - 3);
                    if (!axisAttr.needsPower()) {
                        if (delta * Math.signum(relativeAngularVel.get(axis.getValue() - 3)) < 0)
                            rotationMotor.set(MotorParam.MaxMotorForce, MAX_FORCE[axis.getValue()]);
                        else rotationMotor.set(MotorParam.MaxMotorForce, MAX_BRAKE_FORCE[axis.getValue()]);
                    } else if (powerAllocation[axis.getValue()] != null) {
                        if (delta * Math.signum(relativeAngularVel.get(axis.getValue() - 3)) <= 0) {//加速过程
                            float torque = powerAllocation[axis.getValue()] / Math.max(relativeAngularVel.get(axis.getValue() - 3), 0.25f);
                            rotationMotor.set(MotorParam.MaxMotorForce, Math.clamp(torque, -MAX_FORCE[axis.getValue()], MAX_FORCE[axis.getValue()]));
                        } else {//减速过程
//                            rotationMotor.set(MotorParam.MaxMotorForce, MAX_BRAKE_FORCE[axis.getValue()] - Math.clamp(powerAllocation[axis.getValue()], -MAX_FORCE[axis.getValue()], 0));
                            rotationMotor.set(MotorParam.MaxMotorForce, MAX_BRAKE_FORCE[axis.getValue()]);
                        }
                    }
                }
                //处理位置控制信号
                if (positionControl[axis.getValue()] == null) rotationMotor.setServoEnabled(false);
                else {
                    rotationMotor.setServoEnabled(true);
                    rotationMotor.set(MotorParam.ServoTarget, -positionControl[axis.getValue()]);
                    rotationMotor.set(MotorParam.TargetVelocity, Math.abs(MAX_SPEED[axis.getValue()]));
                }
            }
        }
    }

    @Override
    public void onPostPhysicsTick() {
        super.onPostPhysicsTick();
        if (this.connector != null && this.connector.joint instanceof New6Dof joint) {
            Vector3f relativePos = getRelativePos();
            Vector3f relativeAngle = getRelativeAngle();
            Vector3f relativeLinearVel = getRelativeLinearVel();
            Vector3f relativeAngularVel = getRelativeAngularVel();
            for (Axis axis : attr.axisParams.keySet()) {
                var axisAttr = attr.axisParams.get(axis);
                if (axis.getValue() <= 2) {//一般平动信号
                    for (String signalKey : axisAttr.speedSignalOutputs().keySet())//一般平动速度信号
                        sendSignalToAllTargets(signalKey, relativeLinearVel.get(axis.getValue()));
                    for (String signalKey : axisAttr.positionSignalOutputs().keySet())//一般平动位置信号
                        sendSignalToAllTargets(signalKey, relativePos.get(axis.getValue()));
                } else {//一般转动信号
                    for (String signalKey : axisAttr.speedSignalOutputs().keySet())//一般转动速度信号
                        sendSignalToAllTargets(signalKey, relativeAngularVel.get(axis.getValue() - 3));
                    for (String signalKey : axisAttr.positionSignalOutputs().keySet())//一般转动位置信号
                        sendSignalToAllTargets(signalKey, -relativeAngle.get(axis.getValue() - 3));
                }
            }
            feedbackSpeed = getPrimaryFeedbackSpeed(relativeAngularVel, relativeLinearVel);
        }
    }

    private float getPrimaryFeedbackSpeed(Vector3f relativeAngularVel, Vector3f relativeLinearVel) {
        for (Axis axis : attr.axisParams.keySet()) {
            var axisAttr = attr.axisParams.get(axis);
            if (!axisAttr.needsPower()) continue;
            if (axis.getValue() <= 2) return relativeLinearVel.get(axis.getValue());
            else return relativeAngularVel.get(axis.getValue() - 3);
        }
        return 0;
    }

    private MotorControlSignal getControlInput() {
        Float[] speedInput = new Float[6];
        Float[] positionInput = new Float[6];
        for (Axis axis : attr.axisParams.keySet()) {
            MotorAttr axisAttr = attr.axisParams.get(axis);
            SignalChannel targetSpeed = new SignalChannel();
            SignalChannel targetPosition = new SignalChannel();
            //获取控制信号
            for (String signalKey : axisAttr.targetSpeedSignalKey()) {
                targetSpeed = getSignalChannel(signalKey);//获取目标速度信号(马达，仅控制速度)
                if (!targetSpeed.isEmpty() && !(targetSpeed.getFirstSignal() instanceof EmptySignal)) break;
            }
            for (String signalKey : axisAttr.targetPositionSignalKey()) {
                targetPosition = getSignalChannel(signalKey);//获取目标位置信号(伺服电机，试图达到目标位置)
                if (!targetPosition.isEmpty() && !(targetPosition.getFirstSignal() instanceof EmptySignal)) break;
            }
            //按类型处理控制信号
            //处理速度控制信号
            if (targetSpeed.getFirstSignal() instanceof MoveInputSignal moveInputSignal) {//若输入为原始移动输入信号
                int i = axis.getValue();
                if (axis.getValue() == 3) i = 2;
                float speed = Math.signum(moveInputSignal.getMoveInput()[i]) * 300000;//则不限制转速，只控制旋转方向
                if (speed != 0 || i == 4) speedInput[axis.getValue()] = Math.clamp(speed, -MAX_SPEED[axis.getValue()], MAX_SPEED[axis.getValue()]);
            } else if (targetSpeed.getFirstSignal() instanceof Float speed)
                speedInput[axis.getValue()] = Math.clamp(speed, -MAX_SPEED[axis.getValue()], MAX_SPEED[axis.getValue()]);//若为float信号则设置目标速度
            else speedInput[axis.getValue()] = null;//若为其他任何类型的信号则不对速度进行控制
            //处理位置控制信号
            if (targetPosition.getFirstSignal() instanceof MoveInputSignal moveInputSignal) {
                positionInput[axis.getValue()] = (float) moveInputSignal.getMoveInput()[axis.getValue()] / 100;//若输入为原始移动输入信号则直接设置目标位置
            } else if (targetPosition.getFirstSignal() instanceof Float position)
                positionInput[axis.getValue()] = position;//若为float信号则设置目标位置
            else positionInput[axis.getValue()] = null;//若为其他任何类型的信号则不对位置进行控制
        }
        return new MotorControlSignal(speedInput, positionInput);
    }

    private void distributePower() {
        float totalPower = receivedPower.power();
        if (Float.isNaN(totalPower)) totalPower = 0;
        for (Axis axis : attr.axisParams.keySet()) {
            MotorAttr axisAttr = attr.axisParams.get(axis);
            if (axisAttr.needsPower() && axisAttr.maxForce() > 0.0)
                powerAllocation[axis.getValue()] = totalPower * axisAttr.maxForce() / TOTAL_POWER_WEIGHT;//根据权重分配功率
            else powerAllocation[axis.getValue()] = null;//不需要功率输入或最大值非法则不分配功率
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

    private Vector3f getRelativeAngle() {
        Vector3f result = new Vector3f();
        connector.joint.getAngles(result);
        return result;
    }

    private Vector3f getRelativeAngularVel() {
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

    private Vector3f getRelativePos() {
        Vector3f result = new Vector3f();
        Vector3f posA = connector.joint.getBodyA().getPhysicsLocation(null);
        Vector3f posB = connector.joint.getBodyB().getPhysicsLocation(null);
        Vector3f relativePosInWorld = posB.subtract(posA);
        Quaternion localToWorld = connector.joint.getBodyA().getPhysicsRotation(null);
        MyQuaternion.rotateInverse(localToWorld, relativePosInWorld, result);
        return result;
    }

    private Vector3f getRelativeLinearVel() {
        Vector3f result = new Vector3f();
        Vector3f linearVelA = connector.joint.getBodyA().getLinearVelocity(null);
        Vector3f linearVelB = connector.joint.getBodyB().getLinearVelocity(null);
        Vector3f relativeVelInWorld = linearVelB.subtract(linearVelA);
        Quaternion localToWorld = connector.joint.getBodyA().getPhysicsRotation(null);
        MyQuaternion.rotateInverse(localToWorld, relativeVelInWorld, result);
        return result;
    }

    @Override
    public Map<String, List<String>> getTargetNames() {
        Map<String, List<String>> result = new HashMap<>(6);
        for (Axis axis : attr.axisParams.keySet()) {
            MotorAttr axisAttr = attr.axisParams.get(axis);
            result.putAll(axisAttr.speedSignalOutputs());
            result.putAll(axisAttr.positionSignalOutputs());
        }
        return result;
    }
}
