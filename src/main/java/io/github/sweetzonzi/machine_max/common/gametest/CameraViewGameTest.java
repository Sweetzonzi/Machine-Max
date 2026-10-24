package io.github.sweetzonzi.machine_max.common.gametest;

import cn.solarmoon.spark_core.util.SparkMathKt;
import com.google.gson.JsonParser;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.mojang.serialization.JsonOps;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.control.aim.AimPose;
import io.github.sweetzonzi.machine_max.common.mech.control.view.ViewMath;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.CameraSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.CameraSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.CameraSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import jme3utilities.math.MyQuaternion;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** 在游戏注册环境中用可控挂点验证观察约束，不访问 native 刚体。 */
@GameTestHolder(MachineMax.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CameraViewGameTest {
    private static final ResourceLocation DEFINITION = ResourceLocation.parse("machine_max:test_camera_view");
    private static final Vec3 TARGET = new Vec3(0, 0, -1000);

    private static MovingCamera camera(boolean yawStable, boolean pitchStable) {
        var attr = CameraSubsystemStaticAttr.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"horizontal_stabilized\":" + yawStable + ",\"vertical_stabilized\":" + pitchStable
                        + ",\"yaw_limit\":30,\"min_pitch\":-15,\"max_pitch\":15,\"yaw_speed\":90,\"pitch_speed\":90}"
        )).getOrThrow();
        MMDynamicRes.STATIC_SUBSYSTEM_ATTRS.put(DEFINITION, attr);
        try {
            return new MovingCamera(new CameraSubsystemAttr(DEFINITION, ""));
        } finally {
            MMDynamicRes.STATIC_SUBSYSTEM_ATTRS.remove(DEFINITION, attr);
        }
    }

    @GameTest(template = "part_foundation", batch = "mmViewpoints", timeoutTicks = 100)
    public static void stabilizedDirectionUsesTheMountPublishedAfterLogicAndIsReadOnly(GameTestHelper helper) {
        var camera = camera(true, true);
        camera.enterView(TARGET);
        for (double yaw : new double[]{4, 8, 12, 8, 4, 0, -4, -8, -12, -8, -4, 0}) {
            // 模拟子系统 tick 提交观察角后，物理级别发布本刻的挂点快照。
            camera.updateView(.05f);
            camera.publishMount(ViewMath.lookRotation(deg(0, yaw)));
            Vec2 committed = camera.getViewAngles();
            for (float partial : new float[]{1, .25f, .75f, .5f, 1}) {
                assertNear(helper, 0, camera.samplePose(partial).forward().distanceTo(TARGET.normalize()), 2e-6);
            }
            helper.assertTrue(committed.equals(camera.getViewAngles()), "任意次数或顺序的采样都不能推进状态");
        }
        helper.succeed();
    }

    @GameTest(template = "part_foundation", batch = "mmViewpoints", timeoutTicks = 100)
    public static void stabilizationDriftsOnlyByTheCompensationThatExceedsTheSpeedBudget(GameTestHelper helper) {
        var camera = camera(true, true);
        camera.enterView(TARGET);
        camera.updateView(.05f);
        camera.publishMount(ViewMath.lookRotation(deg(0, 10)));
        assertYaw(helper, 5.5, camera.samplePose(1));
        assertYaw(helper, 2.75, camera.samplePose(.5f));
        for (double expected : new double[]{1, 0, 0}) {
            camera.updateView(.05f);
            camera.publishMount(ViewMath.lookRotation(deg(0, 10)));
            assertYaw(helper, expected, camera.samplePose(1));
        }
        helper.succeed();
    }

    @GameTest(template = "part_foundation", batch = "mmViewpoints", timeoutTicks = 100)
    public static void travelLimitBoundsTheViewAndReverseInputHasNoHiddenBacklog(GameTestHelper helper) {
        var camera = camera(true, true);
        camera.enterView(TARGET);
        for (int i = 0; i < 8; i++) {
            camera.updateView(.05f);
            camera.publishMount(ViewMath.lookRotation(deg(0, 20)));
        }
        assertYaw(helper, 5, camera.samplePose(1));
        camera.turnView(deg(0, -100));
        camera.turnView(deg(0, 2));
        camera.updateView(.05f);
        assertYaw(helper, 7, camera.samplePose(1));
        helper.succeed();
    }

    @GameTest(template = "part_foundation", batch = "mmViewpoints", timeoutTicks = 100)
    public static void stabilizedInputAndHorizonUseTheSameWorldViewAsFreeObservation(GameTestHelper helper) {
        var camera = camera(true, true);
        camera.publishMount(new Quaternion().fromAngleNormalAxis((float) Math.toRadians(30), Vector3f.UNIT_Z));
        camera.enterView(TARGET);
        Vec2 input = deg(1, 2);
        camera.turnView(input);
        AimPose expected = ViewMath.free(ViewMath.advanceFree(TARGET, Vec3.ZERO, input), Vec3.ZERO).pose();
        AimPose actual = camera.samplePose(1);
        assertNear(helper, 0, expected.forward().distanceTo(actual.forward()), 2e-6);
        assertNear(helper, 0, MyQuaternion.rotate(expected.rotation(), Vector3f.UNIT_Y, null)
                .distance(MyQuaternion.rotate(actual.rotation(), Vector3f.UNIT_Y, null)), 2e-6);
        helper.succeed();
    }

    @GameTest(template = "part_foundation", batch = "mmViewpoints", timeoutTicks = 100)
    public static void unstabilizedAxesKeepTheirAnglesRelativeToTheMount(GameTestHelper helper) {
        var camera = camera(false, false);
        camera.enterView(TARGET);
        camera.turnView(deg(2, 3));
        camera.updateView(.05f);
        Quaternion mount = ViewMath.lookRotation(deg(4, 8));
        camera.publishMount(mount);
        AimPose expected = ViewMath.follow(mount, deg(2, 3), Vec3.ZERO).pose();
        assertNear(helper, 0, expected.forward().distanceTo(camera.samplePose(1).forward()), 2e-6);
        helper.succeed();
    }

    @GameTest(template = "part_foundation", batch = "mmViewpoints", timeoutTicks = 100)
    public static void singleAxisStabilizationKeepsTheOtherAxisRelative(GameTestHelper helper) {
        for (boolean yawStable : new boolean[]{true, false}) {
            var camera = camera(yawStable, !yawStable);
            camera.enterView(TARGET);
            camera.updateView(.05f);
            Quaternion mount = ViewMath.lookRotation(yawStable ? deg(0, 4) : deg(4, 0));
            camera.publishMount(mount);
            assertNear(helper, 0, camera.samplePose(1).forward().distanceTo(TARGET.normalize()), 2e-6);
        }
        helper.succeed();
    }

    @GameTest(template = "part_foundation", batch = "mmViewpoints", timeoutTicks = 100)
    public static void reticleTargetLeadsTheActualViewAndFollowsItsStabilizationMode(GameTestHelper helper) {
        for (boolean yawStable : new boolean[]{true, false}) {
            for (boolean pitchStable : new boolean[]{true, false}) {
                var camera = camera(yawStable, pitchStable);
                camera.enterView(TARGET);
                camera.turnView(deg(0, 12));
                // 90°/s 的镜头一刻只能转 4.5°，目标圈应保留尚未追上的 7.5°。
                assertYaw(helper, 4.5, camera.samplePose(1));
                Vec3 target = camera.sampleViewTarget(1);
                helper.assertTrue(target != null, "有效观察必须提供目标圈位置");
                assertNear(helper, 12, Math.toDegrees(ViewMath.directionAngles(target.normalize()).y), 2e-4);
                camera.publishMount(ViewMath.lookRotation(deg(0, 4)));
                Vec2 committed = camera.getViewAngles();
                for (float partial : new float[]{.25f, .75f, .5f, 1}) {
                    Vec3 point = camera.sampleViewTarget(partial);
                    helper.assertTrue(point != null, "帧内目标采样必须有效");
                    double expectedYaw = yawStable ? 12 : 12 + 4 * partial;
                    assertNear(helper, expectedYaw, Math.toDegrees(ViewMath.directionAngles(point.normalize()).y), 2e-4);
                }
                helper.assertTrue(committed.equals(camera.getViewAngles()), "目标圈采样不能推进实际观察角");
            }
        }
        helper.succeed();
    }

    private static Vec2 deg(double pitch, double yaw) {
        return new Vec2((float) Math.toRadians(pitch), (float) Math.toRadians(yaw));
    }

    private static void assertNear(GameTestHelper helper, double expected, double actual, double tolerance) {
        helper.assertTrue(Double.isFinite(actual) && Math.abs(expected - actual) <= tolerance,
                "预期 " + expected + "，实际 " + actual + "，容差 " + tolerance);
    }

    private static void assertYaw(GameTestHelper helper, double degrees, AimPose pose) {
        assertNear(helper, degrees, Math.toDegrees(ViewMath.directionAngles(pose.forward()).y), 2e-4);
    }

    private static final class MovingCamera extends CameraSubsystem {
        private Quaternion previousMount = new Quaternion();
        private Quaternion mount = new Quaternion();

        private MovingCamera(CameraSubsystemAttr attr) {
            super(null, "test_camera", attr);
        }

        private void publishMount(Quaternion next) {
            previousMount = mount;
            mount = next;
        }

        @Override
        public Transform getLerpedLocatorWorldTransform(float partialTick) {
            return SparkMathKt.lerp(new Transform(Vector3f.ZERO, previousMount),
                    new Transform(Vector3f.ZERO, mount), partialTick);
        }
    }
}
