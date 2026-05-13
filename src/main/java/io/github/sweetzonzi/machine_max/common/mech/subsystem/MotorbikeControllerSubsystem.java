package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.bullet.joints.New6Dof;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.common.attachment.ControlPreference;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.MotorbikeControllerSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AdvancedConnector;
import io.github.sweetzonzi.machine_max.util.MMMath;
import io.github.sweetzonzi.machine_max.util.control.PIDController;
import lombok.Getter;

@Getter
public class MotorbikeControllerSubsystem extends CarControllerSubsystem {
    public final MotorbikeControllerSubsystemAttr attr;
    public float roll = 0.0f;
    public float omegaRoll = 0.0f;

    private final PIDController rollController; // 外环角度环控制器，输出目标角速度
    private final PIDController omegaController; // 内环角速度环控制器，输出目标控制力矩
    private final PIDController lowSpeedRollController;

    public MotorbikeControllerSubsystem(ISubsystemHost owner, String name, MotorbikeControllerSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
        this.rollController = new PIDController(0.1f, 0.0f, 0.1f, 1.0 / getPhysicsLevel().getTps(), -2, 2);
        this.omegaController = new PIDController(500.0f, 5.0f, 50.0f, 1.0 / getPhysicsLevel().getTps(), -2000, 2000);
        this.lowSpeedRollController = new PIDController(15.0f, 0.2f, 3.0f, 1.0 / getPhysicsLevel().getTps(), -2000, 2000);
    }

    @Override
    public void onPrePhysicsTick() {
        this.speed = -getOwner().getSubPart().getLinearVelocityLocal().z;
        this.driftWeight = 0f;
        this.roll = SparkMathKt.toDegrees(getOwner().getSubPart().getRoll());
        this.omegaRoll = getSubPart().body.getAngularVelocityLocal(null).z;
        if (isActive() && getOwner().getSubPart().getPart().vehicle.mode == VehicleCore.ControlMode.GROUND) {
            //更新受灵敏度影响的实际控制量，油门与刹车控制在分发控制信号时进行
            if (this.moveInput != null) {
                actualSteering = actualSteering * 0.9f + (moveInput[4] / 100f) * 0.1f;
            }
            actualHandBrake = actualHandBrake * 0.9f + (handBrake ? 1 : 0) * 0.1f;
            motorbikeControl();
            balancing();
            distributeControlSignals();
        } else resetSignalOutputs();
    }

    private void motorbikeControl() {
        if (Math.abs(speed) < 2 && moveInput != null) {
            getSubPart().body.applyCentralForce(MMMath.localVectorToWorldVector(new Vector3f(0, 0, -5 * moveInput[2]), getOwner().getSubPart().body));
        }
    }

    private void balancing() {
        // 修正力倍率
        float correctionForceMultiplier = getAttr().getStaticAttribute().getCorrectionForceMultiplier();
        // 有人控制车辆，且未失去平衡或处于低速时应用修正力
        float maxAngle = getAttr().getStaticAttribute().getMaxAngle();
        float gravity = getSubPart().body.getGravity(null).length();
        double massCenterHeight = -getAttr().getStaticAttribute().getSteeringCenter().y;
        if (moveInput != null && (Math.abs(speed) < 2 || Math.abs(roll) < maxAngle)) {
            float targetRoll = Math.clamp(
                    calculateTargetRoll(
                            speed,
                            getAttr().getStaticAttribute().getSteeringRadiusAtSpeed(speed) / actualSteering,
                            gravity),
                    -0.8f * maxAngle, 0.8f * maxAngle);
            float targetOmegaRoll = (float) rollController.step(targetRoll, roll, omegaRoll);
            float rollControl = (float) omegaController.step(targetOmegaRoll, omegaRoll);
            // 应用修正力倍率
            rollControl *= correctionForceMultiplier;
            // 额外补偿理论平衡所需重力矩
            rollControl -= (float) (getSubPart().getEquivalentMass() * gravity * massCenterHeight * Math.sin(Math.toRadians(roll)));
            // 额外补偿向心力带来的倾覆力矩
            if (Math.abs(actualSteering) > 1e-3) {
                // 计算向心力
                float force = getSubPart().getEquivalentMass() * speed * speed / (getAttr().getStaticAttribute().getSteeringRadiusAtSpeed(speed) / actualSteering);
                // 计算倾覆力矩
                rollControl += (float) (force * massCenterHeight * Math.cos(Math.toRadians(targetRoll)));
            }
            getSubPart().body.applyTorque(MMMath.localVectorToWorldVector(new Vector3f(0, 0, rollControl), getSubPart().body));
        } else if ( // 无人控制车辆，且已停稳，姿态合适时应用修正力
                Math.abs(speed) < 0.5
                        && Math.abs(roll) < 10 + 1.5 * getAttr().getStaticAttribute().getParkingAngle()) {
            int wheelCount = 0;
            for (WheelDriverSubsystem wheel : getWheels().keySet()) {
                if (wheel.connector.hasPart()) {
                    wheelCount++;
                }
            }
            if (wheelCount > 1) { // 未连接轮胎的部件不尝试应用修正力
                float targetRoll = getAttr().getStaticAttribute().getParkingAngle() * (1 - Math.abs(speed));
                // 倒立摆使用单级PID更稳定
                float rollControl = (float) lowSpeedRollController.step(targetRoll, roll);
                // 应用修正力倍率
                rollControl *= correctionForceMultiplier;
                // 额外补偿理论平衡所需重力矩
                rollControl -= (float) (0.5 * getSubPart().body.getMass() * gravity * massCenterHeight * Math.sin(Math.toRadians(roll)));
                getSubPart().body.applyTorque(MMMath.localVectorToWorldVector(new Vector3f(0, 0, rollControl), getSubPart().body));
            }
        }
    }

    private static float calculateTargetRoll(float speed, float turningRadius, float gravity) {
        if (Float.isInfinite(turningRadius) || Float.isNaN(turningRadius)) return 0.0f;
        return (float) Math.toDegrees(Math.atan(speed * speed / (gravity * turningRadius)));
    }

    @Override
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
            return (float) Math.atan(deltaForward * Math.cos(Math.toRadians(roll)) / (steeringRadius + deltaRadius));
        }
    }
}
