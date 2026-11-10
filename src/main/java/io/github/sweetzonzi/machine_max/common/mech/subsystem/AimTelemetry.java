package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.bullet.joints.New6Dof;
import com.jme3.bullet.joints.motors.MotorParam;
import com.jme3.bullet.joints.motors.RotationMotor;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.common.mech.signal.RotationSignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.ViewInputSignal;
import io.github.sweetzonzi.machine_max.util.QueuedCsvWriter;
import jme3utilities.math.MyMath;
import jme3utilities.math.MyQuaternion;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** 主线程管理 CSV，物理线程按同一步编号配对火控指令、原生关节与炮口读数。 */
final class AimTelemetry {
    private static final String AXIS_COLUMNS = "demand_deg,servo_target_deg,joint_angle_deg,tracking_error_deg,"
            + "relative_axis_speed_deg_s,reference_axis_speed_deg_s,max_speed_deg_s,max_torque_nm,"
            + "lower_limit_deg,upper_limit_deg,pivot_gap_blocks,joint_enabled,motor_enabled,servo_enabled,driver_active,"
            + "feedforward_deg_s";
    private static final String MUZZLE_COLUMNS = "muzzle_x,muzzle_y,muzzle_z,forward_x,forward_y,forward_z,muzzle_error_deg";
    private static final String EMPTY_AXIS = ",".repeat(AXIS_COLUMNS.split(",").length);
    private static final String EMPTY_MUZZLE = ",".repeat(MUZZLE_COLUMNS.split(",").length);
    private static final String HEADER = "physics_step,step_s,wall_ms,aim_mode,target_x,target_y,target_z,"
            + "input_pitch_deg,input_yaw_deg,kind,part_uuid,subpart,subsystem," + AXIS_COLUMNS + "," + MUZZLE_COLUMNS;

    final DriveTelemetry csv;
    private final Map<TurretDriverSubsystem, RotationSignal> demands = new HashMap<>();
    private int step;
    private String stepColumns;
    private Vec3 target;

    AimTelemetry(String subject) throws IOException {
        csv = DriveTelemetry.open(subject, HEADER);
    }

    /** 物理线程在控制分发前保存本步输入。 */
    void beginStep(int step, int tps, @Nullable ViewInputSignal input, @Nullable Vec3 target) {
        this.step = step;
        this.target = target;
        this.stepColumns = QueuedCsvWriter.row(step, 1.0 / tps, csv.elapsedMillis(),
                input != null ? input.mode : target != null ? "WORLD" : "NONE",
                target == null ? null : target.x, target == null ? null : target.y, target == null ? null : target.z,
                input == null ? null : Math.toDegrees(input.relativeAim.x),
                input == null ? null : Math.toDegrees(input.relativeAim.y));
        demands.clear();
    }

    void recordDemand(TurretDriverSubsystem driver, RotationSignal command) {
        demands.put(driver, command);
    }

    /** 物理线程在求解后读取生效的电机参数与实际位姿。 */
    void sampleStep(int step, WeaponControllerSubsystem controller, Collection<TurretDriverSubsystem> drivers,
                    Collection<LauncherSubsystem> launchers) {
        if (stepColumns == null || this.step != step) return;
        csv.offer(rowPrefix("aim", controller) + EMPTY_AXIS + EMPTY_MUZZLE);
        for (TurretDriverSubsystem driver : drivers) {
            if (driver.connector == null || driver.getSubPart().isRemoved) continue;
            New6Dof joint = driver.connector.joint;
            if (joint == null || joint.getPhysicsSpace() == null) continue;
            sampleDriver(driver, joint);
        }
        for (LauncherSubsystem launcher : launchers) {
            if (!launcher.getSubPart().isRemoved && launcher.isActive() && !launcher.isDestroyed()) {
                sampleMuzzle(launcher);
            }
        }
        stepColumns = null;
    }

    private void sampleDriver(TurretDriverSubsystem driver, New6Dof joint) {
        var bodyA = joint.getBodyA();
        var bodyB = joint.getBodyB();
        Vector3f omegaA = bodyA.getAngularVelocity(null);
        Vector3f omegaB = bodyB.getAngularVelocity(null);
        // A − B 沿 Bullet 关节轴的投影与原生角度变化同号。
        Vector3f relativeOmega = omegaA.subtract(omegaB);
        Vector3f pivotA = MyMath.transform(bodyA.getTransform(null), joint.getPivotA(null), null);
        Vector3f pivotB = MyMath.transform(bodyB.getTransform(null), joint.getPivotB(null), null);
        float pivotGap = pivotA.distance(pivotB);
        Vector3f angles = joint.getAngles(null);
        RotationSignal demand = demands.get(driver);
        Vector3f referenceOmega = driver.hasYawAxis() ? omegaB : omegaA;
        boolean driverActive = driver.isActive() && !driver.isDestroyed();
        for (int axis = 0; axis < 2; axis++) {
            boolean pitch = axis == 0;
            if (pitch ? !driver.hasPitchAxis() : !driver.hasYawAxis()) continue;
            RotationMotor motor = joint.getRotationMotor(axis);
            double servoTarget = Math.toDegrees(motor.get(MotorParam.ServoTarget));
            double actual = Math.toDegrees(angles.get(axis));
            Vector3f worldAxis = joint.getAxis(axis, null);
            csv.offer(rowPrefix(pitch ? "pitch" : "yaw", driver) + "," + QueuedCsvWriter.row(
                    demand == null ? null : Math.toDegrees(demand.getValue().get(axis)),
                    servoTarget, actual, Math.IEEEremainder(servoTarget - actual, 360),
                    Math.toDegrees(relativeOmega.dot(worldAxis)), Math.toDegrees(referenceOmega.dot(worldAxis)),
                    Math.toDegrees(motor.get(MotorParam.TargetVelocity)), motor.get(MotorParam.MaxMotorForce),
                    Math.toDegrees(motor.get(MotorParam.LowerLimit)), Math.toDegrees(motor.get(MotorParam.UpperLimit)),
                    pivotGap, joint.isEnabled(), motor.isMotorEnabled(), motor.isServoEnabled(), driverActive,
                    demand == null ? null : Math.toDegrees(pitch ? demand.pitchFeedforward : demand.yawFeedforward))
                    + EMPTY_MUZZLE);
        }
    }

    private void sampleMuzzle(LauncherSubsystem launcher) {
        var muzzle = launcher.getMuzzleWorldTransform();
        Vec3 position = SparkMathKt.toVec3(muzzle.getTranslation());
        Vec3 forward = SparkMathKt.toVec3(MyQuaternion.rotate(muzzle.getRotation(), new Vector3f(0, 0, -1), null));
        Vec3 toTarget = target == null ? Vec3.ZERO : target.subtract(position);
        Double error = toTarget.lengthSqr() == 0 ? null
                : Math.toDegrees(Math.atan2(forward.cross(toTarget).length(), forward.dot(toTarget)));
        csv.offer(rowPrefix("muzzle", launcher) + EMPTY_AXIS + "," + QueuedCsvWriter.row(
                position.x, position.y, position.z, forward.x, forward.y, forward.z, error));
    }

    private String rowPrefix(String kind, AbstractSubsystem subsystem) {
        var host = subsystem.getSubPart();
        return stepColumns + "," + QueuedCsvWriter.row(kind, host.getPart().uuid, host.name, subsystem.getName());
    }
}
