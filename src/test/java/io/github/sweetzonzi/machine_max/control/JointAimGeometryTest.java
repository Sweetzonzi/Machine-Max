package io.github.sweetzonzi.machine_max.control;

import com.jme3.bullet.CollisionConfiguration;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.RotationOrder;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.joints.New6Dof;
import com.jme3.bullet.joints.motors.MotorParam;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.bullet.util.NativeLibrary;
import com.jme3.math.*;
import io.github.sweetzonzi.machine_max.common.mech.control.aim.JointAimGeometry;
import io.github.sweetzonzi.machine_max.common.mech.control.aim.AimPose;
import io.github.sweetzonzi.machine_max.common.mech.control.aim.AimGeometrySnapshot;
import io.github.sweetzonzi.machine_max.common.mech.control.aim.AimAxisLimit;
import io.github.sweetzonzi.machine_max.common.mech.control.aim.AimTargetState;
import jme3utilities.math.MyMath;
import cn.solarmoon.spark_core.util.SparkMathKt;
import java.nio.file.Path;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 用项目内嵌 native 核对关节轴符号及持久伺服目标的追赶行为。 */
class JointAimGeometryTest {
    @BeforeAll
    static void loadNative() {
        try {
            NativeLibrary.isThreadSafe();
            return;
        } catch (UnsatisfiedLinkError ignored) {
            // 测试进程尚未加载 native 时，使用复合构建内的同一份库。
        }
        String os = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT);
        String platform = os.contains("win") ? "windows" : os.contains("mac") ? "macos" : "linux";
        String file = os.contains("win") ? "bulletjme.dll" : os.contains("mac") ? "libbulletjme.dylib" : "libbulletjme.so";
        String arch = System.getProperty("os.arch").contains("aarch64") ? "arm64" : "x86_64";
        System.load(Path.of("../Spark-Core/src/main/resources/natives/bullet_dpmt", platform, arch, file).toAbsolutePath().normalize().toString());
    }

    @Test
    void jointAnglesUseSemanticPitchAndNegativeSemanticYaw() {
        PhysicsRigidBody hull = new PhysicsRigidBody(new BoxCollisionShape(.1f), 10);
        PhysicsRigidBody turret = new PhysicsRigidBody(new BoxCollisionShape(.1f), 10);
        PhysicsRigidBody barrel = new PhysicsRigidBody(new BoxCollisionShape(.1f), 10);
        Quaternion flip = new Quaternion().fromAngles(0, (float) Math.PI, 0);
        Quaternion yaw = new Quaternion().fromAngles(0, -.4f, 0);
        Quaternion pitch = new Quaternion().fromAngles(.2f, 0, 0);
        turret.setPhysicsRotation(yaw);
        barrel.setPhysicsRotation(yaw.mult(pitch));
        New6Dof y = new New6Dof(turret, hull, Vector3f.ZERO, Vector3f.ZERO,
                flip.toRotationMatrix(), flip.toRotationMatrix(), RotationOrder.YXZ);
        New6Dof p = new New6Dof(turret, barrel, Vector3f.ZERO, Vector3f.ZERO,
                flip.toRotationMatrix(), flip.toRotationMatrix(), RotationOrder.YXZ);
        assertEquals(-.4f, y.getAngles(null).y, 1e-5);
        assertEquals(.2f, p.getAngles(null).x, 1e-5);
        New6Dof both = new New6Dof(barrel, hull, Vector3f.ZERO, Vector3f.ZERO,
                flip.toRotationMatrix(), flip.toRotationMatrix(), RotationOrder.YXZ);
        assertEquals(-.2f, both.getAngles(null).x, 1e-5);
        assertEquals(-.4f, both.getAngles(null).y, 1e-5);
    }

    @Test
    void sampledGeometryReconstructsTheActualOpticFromBothNativeMechanisms() {
        for (boolean integrated : new boolean[]{false, true}) {
            PhysicsRigidBody hull = body();
            PhysicsRigidBody turret = body();
            PhysicsRigidBody barrel = integrated ? turret : body();
            Transform root = new Transform(new Vector3f(10, 2, -4), new Quaternion().fromAngles(.1f, .6f, -.2f));
            Quaternion flip = new Quaternion().fromAngles(0, (float) Math.PI, 0);
            Transform yawA = new Transform(new Vector3f(.1f, .3f, .2f), flip);
            Transform yawB = new Transform(new Vector3f(-.2f, .5f, -.1f), flip);
            Transform pitchA = new Transform(new Vector3f(.2f, .6f, -.7f), flip);
            Transform pitchB = new Transform(new Vector3f(-.1f, .2f, .1f), flip);
            Transform turretWorld = MyMath.combine(yawB, root, null);
            turretWorld = MyMath.combine(new Transform(Vector3f.ZERO,
                    new Quaternion().fromAngleNormalAxis(-.4f, Vector3f.UNIT_Y)), turretWorld, null);
            if (integrated) {
                turretWorld = MyMath.combine(new Transform(Vector3f.ZERO,
                        new Quaternion().fromAngleNormalAxis(-.2f, Vector3f.UNIT_X)), turretWorld, null);
            }
            turretWorld = MyMath.combine(yawA.invert(), turretWorld, null);
            hull.setPhysicsTransform(root);
            turret.setPhysicsTransform(turretWorld);
            if (!integrated) {
                Transform barrelWorld = MyMath.combine(pitchA, turretWorld, null);
                barrelWorld = MyMath.combine(new Transform(Vector3f.ZERO,
                        new Quaternion().fromAngleNormalAxis(-.2f, Vector3f.UNIT_X)), barrelWorld, null);
                barrel.setPhysicsTransform(MyMath.combine(pitchB.invert(), barrelWorld, null));
            }
            New6Dof yaw = joint(turret, hull, yawA, yawB);
            New6Dof pitch = integrated ? yaw : joint(turret, barrel, pitchA, pitchB);
            Transform optic = MyMath.combine(new Transform(new Vector3f(.3f, .2f, -1.4f),
                    new Quaternion().fromAngles(.03f, -.02f, .04f)), barrel.getTransform(null), null);
            var sample = JointAimGeometry.sample(yaw, pitch, optic);
            assertEquals(.2f, sample.actualAngles().x, 1e-5);
            assertEquals(.4f, sample.actualAngles().y, 1e-5);
            AimPose rebuilt = sample.forward(sample.actualAngles());
            AimPose actual = new AimPose(SparkMathKt.toVec3(optic.getTranslation()), optic.getRotation());
            assertTrue(rebuilt.position().distanceTo(actual.position()) < 1e-4);
            assertTrue(rebuilt.forward().distanceTo(actual.forward()) < 1e-5);
        }
    }

    @Test
    void realServosContinueChasingAStoredTargetWithoutNewInput() {
        for (boolean integrated : new boolean[]{false, true}) {
            PhysicsSpace space = new PhysicsSpace(new Vector3f(-100, -100, -100), new Vector3f(100, 100, 100),
                    PhysicsSpace.BroadphaseType.DBVT, NativeLibrary.countThreads(), new CollisionConfiguration(8192, 1));
            space.setGravity(Vector3f.ZERO);
            PhysicsRigidBody hull = new PhysicsRigidBody(new BoxCollisionShape(.1f), 0);
            hull.setContactResponse(false);
            PhysicsRigidBody turret = body();
            PhysicsRigidBody barrel = integrated ? turret : body();
            space.addCollisionObject(hull);
            space.addCollisionObject(turret);
            if (!integrated) {
                space.addCollisionObject(barrel);
            }
            Quaternion flip = new Quaternion().fromAngles(0, (float) Math.PI, 0);
            Transform frame = new Transform(Vector3f.ZERO, flip);
            New6Dof y = joint(turret, hull, frame, frame);
            New6Dof p = integrated ? y : joint(turret, barrel, frame, frame);
            for (New6Dof j : integrated ? java.util.List.of(y) : java.util.List.of(y, p)) {
                for (int i = 3; i < 6; i++) {
                    j.set(MotorParam.LowerLimit, i, 0);
                    j.set(MotorParam.UpperLimit, i, 0);
                }
                j.setCollisionBetweenLinkedBodies(false);
                space.addJoint(j);
            }
            y.set(MotorParam.LowerLimit, 4, 1);
            y.set(MotorParam.UpperLimit, 4, -1);
            p.set(MotorParam.LowerLimit, 3, -1);
            p.set(MotorParam.UpperLimit, 3, 1);
            // 目标只写一次，后续只推进物理，验证伺服不会依赖重复鼠标输入。
            Vec2 desired = new Vec2(.2f, .4f);
            Vector3f servo = new Vector3f(integrated ? -desired.x : desired.x, -desired.y, 0);
            for (int axis : new int[]{0, 1}) {
                var motor = (axis == 0 ? p : y).getRotationMotor(axis);
                motor.setMotorEnabled(true);
                motor.setServoEnabled(true);
                motor.set(MotorParam.ServoTarget, axis == 0 ? servo.x : servo.y);
                motor.set(MotorParam.TargetVelocity, .5f);
                motor.set(MotorParam.MaxMotorForce, 1);
            }
            for (int i = 0; i < 400; i++) {
                space.update(.01f, 0);
            }
            Vec2 actual = new Vec2(p.getAngles(null).x * (integrated ? -1 : 1), -y.getAngles(null).y);
            assertEquals(desired.x, actual.x, .01, "俯仰 integrated=" + integrated);
            assertEquals(desired.y, actual.y, .01, "偏航 integrated=" + integrated);

            // 快速持续输入超过硬件转速时，60°累计余量应保持同向追赶，并跨过整圈。
            turret.setEnableSleep(false);
            barrel.setEnableSleep(false);
            y.getRotationMotor(1).set(MotorParam.TargetVelocity, (float) Math.toRadians(90));
            var input = new AimTargetState(AimTargetState.Mode.RELATIVE, Vec3.ZERO, actual);
            double previousYaw = actual.y;
            double travelled = 0;
            for (int tick = 0; tick < 160; tick++) {
                Vec2 measured = new Vec2(p.getAngles(null).x * (integrated ? -1 : 1), -y.getAngles(null).y);
                var sample = new AimGeometrySnapshot(AimPose.IDENTITY, new Transform(), new Vector3f(0, -1, 0),
                        new Transform(), Vector3f.UNIT_X, new Transform(),
                        AimAxisLimit.CONTINUOUS, AimAxisLimit.CONTINUOUS, measured);
                input.applyMouse(AimPose.IDENTITY, sample, new Vec2(0, (float) Math.toRadians(10)));
                y.getRotationMotor(1).set(MotorParam.ServoTarget,
                        (float) Math.IEEEremainder(-input.getRelativeAim().y, 2 * Math.PI));
                for (int step = 0; step < 5; step++) {
                    space.update(.01f, 0);
                }
                double currentYaw = -y.getAngles(null).y;
                double progress = Math.IEEEremainder(currentYaw - previousYaw, 2 * Math.PI);
                assertTrue(progress > -1e-4, "同向输入不能因跨周或领先而反转，integrated=" + integrated);
                travelled += progress;
                previousYaw = currentYaw;
            }
            assertTrue(travelled > 2 * Math.PI, "累计偏差限幅不能限制炮塔连续回转");
            space.removeJoint(y);
            if (!integrated) {
                space.removeJoint(p);
            }
            space.removeCollisionObject(hull);
            space.removeCollisionObject(turret);
            if (!integrated) {
                space.removeCollisionObject(barrel);
            }
        }
    }

    private static PhysicsRigidBody body() {
        PhysicsRigidBody body = new PhysicsRigidBody(new BoxCollisionShape(.1f), 10);
        body.setContactResponse(false);
        return body;
    }

    private static New6Dof joint(PhysicsRigidBody a, PhysicsRigidBody b, Transform fa, Transform fb) {
        return new New6Dof(a, b, fa.getTranslation(), fb.getTranslation(), fa.getRotation().toRotationMatrix(),
                fb.getRotation().toRotationMatrix(), RotationOrder.YXZ);
    }

}
