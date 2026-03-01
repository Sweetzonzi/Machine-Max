package io.github.sweetzonzi.machine_max.common.vehicle.subsystem;

import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.bullet.joints.New6Dof;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.dynamic_attr.MotorbikeControllerSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.AdvancedConnector;
import io.github.sweetzonzi.machine_max.util.MMMath;
import io.github.sweetzonzi.machine_max.util.control.PIDController;
import lombok.Getter;

@Getter
public class MotorbikeControllerSubsystem extends CarControllerSubsystem {
    public final MotorbikeControllerSubsystemAttr attr;
    public float roll = 0.0f;

    private final PIDController rollController;

    public MotorbikeControllerSubsystem(ISubsystemHost owner, String name, MotorbikeControllerSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
        this.rollController = new PIDController(25.0f, 2.0f, 7.0f, 1.0 / PhysicsLevel.TPS, -180, 180);
    }

    @Override
    public void onPrePhysicsTick() {
        this.speed = -getOwner().getSubPart().getLinearVelocityLocal().z;
        this.roll = SparkMathKt.toDegrees(getOwner().getSubPart().getRoll());
        if (isActive() && getOwner().getSubPart().getPart().vehicle.mode == VehicleCore.ControlMode.GROUND) {
            //更新受灵敏度影响的实际控制量，油门与刹车控制在分发控制信号时进行
            if (this.moveInput != null) {
                actualSteering = actualSteering * 0.9f + (moveInput[4]) * 0.1f;
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
        if (moveInput != null && (Math.abs(speed) < 2 || Math.abs(roll) < getAttr().getStaticAttribute().getMaxAngle())) {
            float targetRoll = calculateTargetRoll(
                    speed,
                    getAttr().getStaticAttribute().getSteeringRadiusAtSpeed(speed) / actualSteering * 100,
                    getSubPart().body.getGravity(null).length());
            float rollControl = (float) Math.clamp(rollController.step(0.8 * targetRoll + 0.2 * roll, roll), -2000, 2000);
            // 应用修正力倍率
            rollControl *= correctionForceMultiplier;
            getSubPart().body.applyTorque(MMMath.localVectorToWorldVector(new Vector3f(0, 0, -rollControl), getOwner().getSubPart().body));
        } else if ( // 无人控制车辆，且已停稳，姿态合适时应用修正力
                Math.abs(speed) < 0.5
                        && Math.abs(roll) < 2.5 * getAttr().getStaticAttribute().getParkingAngle()) {
            int wheelCount = 0;
            for (WheelDriverSubsystem wheel : getWheels().keySet()) {
                if (wheel.connector.hasPart()) {
                    wheelCount ++;
                }
            }
            if (wheelCount > 1) { // 未连接轮胎的部件不尝试应用修正力
                float targetRoll = getAttr().getStaticAttribute().getParkingAngle() * (1 - Math.abs(speed));
                float rollControl = (float) Math.clamp(rollController.step(targetRoll, roll), -2250, 2250);
                // 应用修正力倍率
                rollControl *= correctionForceMultiplier;
                getSubPart().body.applyTorque(MMMath.localVectorToWorldVector(new Vector3f(0, 0, -rollControl), getOwner().getSubPart().body));
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
            // 使用动态转向半径映射表，根据当前速度获取合适的转向半径
            float steeringRadius = attr.staticAttribute.getSteeringRadiusAtSpeed(speed) / steeringInput * 100f;//实际转向半径(米) Actual steering radius (m)
            double deltaRadius = pivot.x - attr.staticAttribute.steeringCenter.x;
            deltaRadius *= Math.signum(steeringInput);
            double deltaForward = pivot.z - attr.staticAttribute.steeringCenter.z;
            return (float) Math.atan(deltaForward * Math.cos(Math.toRadians(roll)) / (steeringRadius + deltaRadius));
        }
    }
}
