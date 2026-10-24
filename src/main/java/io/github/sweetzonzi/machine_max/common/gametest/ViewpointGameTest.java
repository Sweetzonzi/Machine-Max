package io.github.sweetzonzi.machine_max.common.gametest;

import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.util.PPhase;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.bullet.RotationOrder;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.joints.New6Dof;
import com.jme3.bullet.joints.motors.MotorParam;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Matrix3f;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.control.aim.AimPose;
import io.github.sweetzonzi.machine_max.common.mech.control.aim.AimTargetState;
import io.github.sweetzonzi.machine_max.common.mech.control.view.ViewMath;
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions;
import io.github.sweetzonzi.machine_max.common.mech.part.PartFactory;
import io.github.sweetzonzi.machine_max.common.mech.signal.EmptySignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.ViewInputSignal;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.*;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.WeaponControllerSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.SeatSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import jme3utilities.math.MyMath;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** 真实服务端验证设备、观察角与 WC 多执行器握手；原生读取和目标写入安排在物理线程。 */
@GameTestHolder(MachineMax.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ViewpointGameTest {
    @GameTest(template = "part_foundation", batch = "mmViewpoints", timeoutTicks = 100)
    public static void deviceTypesAndOpticalSources(GameTestHelper helper) {
        for (String name : List.of("2s38_turret", "k17_turret", "sdkfz234_turret")) {
            var type = PartDefinitions.regular(false).get(ResourceLocation.parse("machine_max:" + name));
            helper.assertTrue(type != null, "部件定义必须加载：" + name);
            String variant = name.equals("2s38_turret") ? "2s38" : name.equals("sdkfz234_turret") ? "sdkfz234_2" : type.variants.keySet().iterator().next();
            RegularPart part = PartFactory.createRegular(type, variant, helper.getLevel());
            try {
                int fixed = 0;
                int rotatable = 0;
                for (var subsystem : part.getAllSubsystems()) {
                    if (!(subsystem instanceof AbstractViewpointSubsystem view)) {
                        continue;
                    }
                    helper.assertTrue(view.samplePose(1).forward().lengthSqr() > .99, "公共视点必须产出有限单位光轴");
                    if (view instanceof SightSubsystem sight) {
                        fixed++;
                        helper.assertTrue(sight.getSubPart().hasLocator(sight.sightAttr.locator),
                                "炮镜 locator 必须属于其宿主 SubPart");
                        String expectedBone = name.equals("2s38_turret") ? "2A90"
                                : name.equals("k17_turret") ? "2A42_All" : "AllGunShield";
                        helper.assertTrue(sight.getSubPart().attr.startBone.equalsIgnoreCase(expectedBone),
                                "固定炮镜必须直接安装到随炮管俯仰的子部件：" + name);
                    } else if (view instanceof CameraSubsystem camera) {
                        rotatable++;
                        verifyCameraState(helper, camera);
                    }
                }
                helper.assertTrue(fixed == 1, "每个样例应有一台固定炮手镜：" + name);
                helper.assertTrue(rotatable == (name.equals("sdkfz234_turret") ? 0 : 1), "车长镜应属于可转式分支：" + name);
            } finally {
                part.discardUnattached();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "part_foundation", batch = "mmViewpoints", timeoutTicks = 200)
    public static void oneControllerDrivesEveryHandshakeAxis(GameTestHelper helper) {
        var type = PartDefinitions.regular(false).get(ResourceLocation.parse("machine_max:k17_turret"));
        RegularPart part = PartFactory.createRegular(type, type.variants.keySet().iterator().next(), helper.getLevel());
        var main = part.subParts.get("sub_part.machine_max.main");
        var original = (WeaponControllerSubsystem) main.subsystems.get("subsystem.machine_max.main_weapon");
        var drivers = main.subsystems.values().stream().filter(TurretDriverSubsystem.class::isInstance)
                .map(TurretDriverSubsystem.class::cast).toList();
        var names = drivers.stream().map(TurretDriverSubsystem::getName).toList();
        // 测试只填写已有 control_outputs，以普通名称寻址触发真实握手。
        var attr = new WeaponControllerSubsystemAttr(original.attr.getModelName(), Map.of("weapon_control", names),
                original.attr.firstHudLayout, original.attr.thirdHudLayout, original.attr.scopeHudLayout);
        var controller = new WeaponControllerSubsystem(original.getOwner(), "probe_weapon_controller", attr);
        controller.setTargetFromNames();
        controller.onAttach();
        helper.assertTrue(controller.getTurrets().size() == drivers.size() && drivers.size() >= 4,
                "单个 WC 必须握手发现全部方向机和多个高低机");
        var seatAttr = SeatSubsystemAttr.CODEC.codec().parse(com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"definition\":\"machine_max:default_3rd_seat\","
                        + "\"locator\":\"probe\",\"control_group_preset\":\"machine_max:default_gunner\"}")).getOrThrow();
        var insideSeat = new SeatSubsystem(main, "probe_inside_seat", seatAttr);
        var secondary = (WeaponControllerSubsystem) main.subsystems.get("subsystem.machine_max.secondary_weapon");
        var fixedSight = part.getAllSubsystems().stream().filter(SightSubsystem.class::isInstance)
                .map(SightSubsystem.class::cast).findFirst().orElseThrow();
        // 不同宿主模拟座椅安装位置差异；发现列表包含主／副 WC，选择必须依据主武器频道。
        var otherSeat = new SeatSubsystem(fixedSight.getOwner(), "probe_other_seat", insideSeat.attr);
        for (SeatSubsystem seat : List.of(insideSeat, otherSeat)) {
            seat.getWeaponControllersByGroup().put("base", List.of(secondary, original));
            helper.assertTrue(seat.getActiveMainWeaponController() == original,
                    "座椅宿主不能改变主 WC 选择，也不能误选副武器控制器");
        }
        var server = helper.getLevel().getServer();
        Vec3 point = new Vec3(50, 30, -100);
        Vec2 relative = new Vec2(.1f, .2f);
        controller.getSignalChannel("aim_input").put(controller,
                new ViewInputSignal(point, relative, AimTargetState.Mode.RELATIVE));
        CompletableFuture<Void> result = physics(controller, () -> {
            for (var driver : drivers) {
                if (driver.connector.joint != null) {
                    continue;
                }
                // 外部车体未入场时，用未入世界的独立刚体补足轴的 native 参考；不运行求解器。
                var support = new PhysicsRigidBody(new BoxCollisionShape(.1f), 0);
                support.setPhysicsTransform(driver.getSubPart().body.getTransform(null));
                driver.connector.joint = new New6Dof(driver.getSubPart().body, support, Vector3f.ZERO, Vector3f.ZERO,
                        Matrix3f.IDENTITY, Matrix3f.IDENTITY, RotationOrder.YXZ);
            }
            for (var subsystem : part.getAllSubsystems()) {
                if (!(subsystem instanceof SightSubsystem sight)) {
                    continue;
                }
                verifyFixedMount(helper, sight);
                var sample = controller.sampleAimGeometry(sight);
                helper.assertTrue(sample != null, "主 WC 必须从握手机构提供炮镜采样");
                AimPose rebuilt = sample.forward(sample.actualAngles());
                var mount = sight.getSubPart().getLocatorWorldTransform(sight.sightAttr.locator);
                helper.assertTrue(rebuilt.position().distanceTo(SparkMathKt.toVec3(mount.getTranslation())) < 1e-4,
                        "机构实际角的正解必须重建炮镜挂点位置");
                helper.assertTrue(rebuilt.forward().distanceTo(new AimPose(Vec3.ZERO, mount.getRotation()).forward()) < 1e-4,
                        "机构实际角的正解必须重建炮镜光轴");
            }
            for (int step = 0; step < 5; step++) {
                controller.onPrePhysicsTick();
                for (var driver : drivers) {
                    verifyServo(helper, driver,
                            new Vector3f(driver.hasYawAxis() && driver.hasPitchAxis() ? -relative.x : relative.x, -relative.y, 0));
                }
            }
        }).thenComposeAsync(ignored -> {
            controller.getSignalChannel("aim_input_p1").put(drivers.getFirst(),
                    new ViewInputSignal(point, Vec2.ZERO, AimTargetState.Mode.WORLD));
            return physics(controller, () -> {
                controller.onPrePhysicsTick();
                for (var driver : drivers) {
                    verifyServo(helper, driver, driver.computeAimAngles(point));
                }
            });
        }, server).thenComposeAsync(ignored -> {
            controller.getSignalChannel("aim_input_p1").put(drivers.getFirst(),
                    new ViewInputSignal(point, relative, AimTargetState.Mode.PITCH_STABLE));
            return physics(controller, () -> verifyMixedAxes(helper, controller, drivers, point, relative, false));
        }, server).thenComposeAsync(ignored -> {
            controller.getSignalChannel("aim_input_p1").put(drivers.getFirst(),
                    new ViewInputSignal(point, relative, AimTargetState.Mode.YAW_STABLE));
            return physics(controller, () -> verifyMixedAxes(helper, controller, drivers, point, relative, true));
        }, server).thenComposeAsync(ignored -> {
            controller.getSignalChannel("aim_input_p1").put(drivers.getFirst(), EmptySignal.INSTANCE);
            return physics(controller, () -> {
                controller.onPrePhysicsTick();
                for (var driver : drivers) {
                    verifyServo(helper, driver,
                            new Vector3f(driver.hasYawAxis() && driver.hasPitchAxis() ? -relative.x : relative.x, -relative.y, 0));
                }
            });
        }, server).thenComposeAsync(ignored -> {
            controller.getSignalChannel("aim_input").put(controller, EmptySignal.INSTANCE);
            return physics(controller, () -> {
                controller.onPrePhysicsTick();
                for (var driver : drivers) {
                    verifyServo(helper, driver, driver.getRelativeAngle());
                }
            });
        }, server);
        // 成功必须在 GameTest 自己的 tick 内报告，批次完成监听器才会推进下一批。
        helper.succeedWhen(() -> {
            helper.assertTrue(result.isDone(), "等待物理线程检查完成");
            try {
                result.join();
            } finally {
                part.discardUnattached();
            }
        });
    }

    /** 物理线程检查稳定轴各自反解世界点，另一轴接收操作者的绝对累计角。 */
    private static void verifyMixedAxes(GameTestHelper helper, WeaponControllerSubsystem controller,
                                        List<TurretDriverSubsystem> drivers, Vec3 point, Vec2 relative, boolean yawStable) {
        controller.onPrePhysicsTick();
        for (var driver : drivers) {
            Vector3f expected = driver.computeAimAngles(point);
            if (yawStable) {
                expected.x = driver.hasYawAxis() && driver.hasPitchAxis() ? -relative.x : relative.x;
            } else {
                expected.y = -relative.y;
            }
            verifyServo(helper, driver, expected);
        }
    }

    @GameTest(template = "part_foundation", batch = "mmViewpoints", timeoutTicks = 200)
    public static void sdkfzUnstableTargetUsesTheMainMechanism(GameTestHelper helper) {
        var type = PartDefinitions.regular(false).get(ResourceLocation.parse("machine_max:sdkfz234_turret"));
        RegularPart part = PartFactory.createRegular(type, "sdkfz234_2", helper.getLevel());
        var main = part.subParts.get("sub_part.machine_max.main");
        var original = (WeaponControllerSubsystem) main.subsystems.get("subsystem.machine_max.main_weapon");
        var drivers = main.subsystems.values().stream().filter(TurretDriverSubsystem.class::isInstance)
                .map(TurretDriverSubsystem.class::cast).toList();
        var attr = new WeaponControllerSubsystemAttr(original.attr.getModelName(),
                Map.of("weapon_control", drivers.stream().map(TurretDriverSubsystem::getName).toList()),
                original.attr.firstHudLayout, original.attr.thirdHudLayout, original.attr.scopeHudLayout);
        var controller = new WeaponControllerSubsystem(main, "probe_sdkfz_controller", attr);
        controller.setTargetFromNames();
        controller.onAttach();
        var sight = (SightSubsystem) part.subParts.get("sub_part.machine_max.gun_shield")
                .subsystems.get("subsystem.machine_max.gunner_sight");
        var seat = (SeatSubsystem) main.subsystems.get("subsystem.machine_max.gunner_seat");
        seat.getWeaponControllersByGroup().put("base", List.of(controller));
        helper.assertTrue(seat.getActiveMainWeaponController() == controller, "SDKFZ 炮手必须选中主 WC");
        CompletableFuture<Void> result = physics(controller, () -> {
            for (var driver : drivers) {
                if (driver.connector.joint == null) {
                    var support = new PhysicsRigidBody(new BoxCollisionShape(.1f), 0);
                    support.setPhysicsTransform(driver.getSubPart().body.getTransform(null));
                    driver.connector.joint = new New6Dof(driver.getSubPart().body, support, Vector3f.ZERO, Vector3f.ZERO,
                            Matrix3f.IDENTITY, Matrix3f.IDENTITY, RotationOrder.YXZ);
                }
                driver.onPrePhysicsTick();
            }
            var sample = controller.sampleAimGeometry(sight);
            helper.assertTrue(sample != null, "SDKFZ 主 WC 必须提供机构参考");
            var mount = sight.getSubPart().getLocatorWorldTransform(sight.sightAttr.locator);
            AimPose actual = new AimPose(SparkMathKt.toVec3(mount.getTranslation()), mount.getRotation());
            AimPose rebuilt = sample.forward(sample.actualAngles());
            helper.assertTrue(rebuilt.position().distanceTo(actual.position()) < 1e-4
                            && rebuilt.forward().distanceTo(actual.forward()) < 1e-4,
                    "SDKFZ 实际机构角必须正解回炮镜光轴");
            var target = new AimTargetState(AimTargetState.Mode.RELATIVE, actual.position(), sample.actualAngles());
            Vec2 accepted = target.applyMouse(actual, sample, new Vec2(0, (float) Math.toRadians(150)));
            helper.assertTrue(Math.abs(accepted.y - Math.toRadians(60)) < 1e-5,
                    "SDKFZ 无稳偏航输入只能累计到 60°偏差");
            helper.assertTrue(target.getWorldPoint().distanceTo(target.relativePoint(sample)) < 1e-5,
                    "SDKFZ 全无稳瞄准圈与控制世界点必须来自同一累计目标");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(result.isDone(), "等待 SDKFZ 物理采样验证完成");
            try {
                result.join();
            } finally {
                part.discardUnattached();
            }
        });
    }

    private static CompletableFuture<Void> physics(WeaponControllerSubsystem controller, Runnable work) {
        var result = new CompletableFuture<Void>();
        controller.getPhysicsLevel().submitImmediateTask(PPhase.PRE, () -> {
            try {
                work.run();
                result.complete(null);
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
            return null;
        });
        return result;
    }

    @GameTest(template = "part_foundation", batch = "mmViewpoints", timeoutTicks = 200)
    public static void reticleGeometryUsesTheInterpolatedBaseAndOptic(GameTestHelper helper) {
        var turretType = PartDefinitions.regular(false).get(ResourceLocation.parse("machine_max:sdkfz234_turret"));
        var hullType = PartDefinitions.regular(false).get(ResourceLocation.parse("machine_max:sdkfz234_hull"));
        RegularPart turret = PartFactory.createRegular(turretType, "sdkfz234_2", helper.getLevel());
        RegularPart hull = PartFactory.createRegular(hullType, hullType.variants.keySet().iterator().next(), helper.getLevel());
        var main = turret.subParts.get("sub_part.machine_max.main");
        var original = (WeaponControllerSubsystem) main.subsystems.get("subsystem.machine_max.main_weapon");
        var drivers = main.subsystems.values().stream().filter(TurretDriverSubsystem.class::isInstance)
                .map(TurretDriverSubsystem.class::cast).toList();
        var attr = new WeaponControllerSubsystemAttr(original.attr.getModelName(),
                Map.of("weapon_control", drivers.stream().map(TurretDriverSubsystem::getName).toList()),
                original.attr.firstHudLayout, original.attr.thirdHudLayout, original.attr.scopeHudLayout);
        var controller = new WeaponControllerSubsystem(main, "probe_render_controller", attr);
        controller.setTargetFromNames();
        controller.onAttach();
        var sight = (SightSubsystem) turret.subParts.get("sub_part.machine_max.gun_shield")
                .subsystems.get("subsystem.machine_max.gunner_sight");
        sight.setAimController(controller);
        CompletableFuture<Void> result = physics(controller, () -> {
            var baseBody = hull.rootSubPart.body;
            for (var driver : drivers) {
                if (driver.connector.joint == null) {
                    driver.connector.joint = new New6Dof(driver.getSubPart().body, baseBody,
                            Vector3f.ZERO, Vector3f.ZERO, Matrix3f.IDENTITY, Matrix3f.IDENTITY, RotationOrder.YXZ);
                }
            }
            // 缓存端点供主线程式采样读取；测试不把刚体加入物理世界或运行求解器。
            for (var subPart : turret.subParts.values()) {
                PhysicsBodyExtensionKt.stateOf(subPart.body).update();
            }
            var baseState = PhysicsBodyExtensionKt.stateOf(baseBody);
            baseState.update();
            baseBody.setPhysicsRotation(new Quaternion().fromAngles(0, (float) Math.toRadians(1), 0)
                    .mult(baseBody.getPhysicsRotation(null)));
            baseState.update();
            sight.onPostPhysicsTick();
            var physical = sight.getAimGeometry();
            helper.assertTrue(physical != null, "测试须发布物理机构采样");
            Vec2 physicalAngles = physical.actualAngles();
            Vec2 first = controller.sampleAimGeometry(sight, 0).actualAngles();
            Vec2 last = controller.sampleAimGeometry(sight, 1).actualAngles();
            helper.assertTrue(Math.abs(first.y - last.y) > .005, "显示实际角必须随基座插值变化");
            for (float partial : new float[]{0, .25f, .5f, .75f, 1}) {
                var geometry = controller.sampleAimGeometry(sight, partial);
                AimPose optic = sight.samplePose(partial);
                helper.assertTrue(geometry != null && geometry.forward(geometry.actualAngles()).forward()
                        .distanceTo(optic.forward()) < 1e-4, "同一 partial 的机构角须重建可视光轴");
            }
            helper.assertTrue(physicalAngles.equals(physical.actualAngles()), "显示采样不能写物理实际角");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(result.isDone(), "等待可视机构采样验证完成");
            try {
                result.join();
            } finally {
                turret.discardUnattached();
                hull.discardUnattached();
            }
        });
    }

    private static void verifyServo(GameTestHelper helper, TurretDriverSubsystem driver, Vector3f expected) {
        driver.onPrePhysicsTick();
        var joint = (New6Dof) driver.connector.joint;
        if (driver.hasPitchAxis()) {
            helper.assertTrue(Math.abs(joint.getRotationMotor(0).get(MotorParam.ServoTarget) - expected.x) < 1e-5,
                    "每个高低机必须独立收到角目标：" + driver.getName());
        }
        if (driver.hasYawAxis()) {
            helper.assertTrue(Math.abs(joint.getRotationMotor(1).get(MotorParam.ServoTarget) - expected.y) < 1e-5,
                    "每个方向机必须独立收到角目标：" + driver.getName());
        }
    }

    /** 在物理线程移动并旋转挂载体，验证固定炮镜的位置、光轴和插值共同跟随宿主。 */
    private static void verifyFixedMount(GameTestHelper helper, SightSubsystem sight) {
        var body = sight.getSubPart().body;
        var state = PhysicsBodyExtensionKt.stateOf(body);
        var original = body.getTransform(null);
        state.update();
        AimPose before = sight.samplePose(1);
        try {
            var rotation = new Quaternion().fromAngles(.25f, .3f, .1f);
            var moved = original.clone();
            moved.setTranslation(original.getTranslation().add(2, .5f, -3));
            moved.setRotation(rotation.mult(original.getRotation()));
            body.setPhysicsTransform(moved);
            state.update();

            var locator = sight.getSubPart().getLocatorLocalTransform(sight.sightAttr.locator);
            var expected = MyMath.combine(locator, moved, null);
            AimPose actual = sight.samplePose(1);
            Vec3 expectedPosition = SparkMathKt.toVec3(expected.getTranslation());
            Vec3 expectedForward = new AimPose(expectedPosition, expected.getRotation()).forward();
            helper.assertTrue(actual.position().distanceTo(expectedPosition) < 1e-5,
                    "炮镜位置必须随宿主刚体和 locator 完整变换");
            helper.assertTrue(actual.forward().distanceTo(expectedForward) < 1e-5,
                    "炮镜光轴必须随同一宿主与 locator 旋转");
            helper.assertTrue(actual.forward().distanceTo(before.forward()) > .05,
                    "转动宿主必须改变实际光轴");
            var originalPose = MyMath.combine(locator, original, null);
            var expectedMidpoint = SparkMathKt.lerp(originalPose, expected, .5f);
            helper.assertTrue(sight.samplePose(.5f).position().distanceTo(
                    SparkMathKt.toVec3(expectedMidpoint.getTranslation())) < 1e-5,
                    "固定炮镜必须遵循 SubPart 的挂点世界变换插值规则");
        } finally {
            body.setPhysicsTransform(original);
            state.update();
            state.update();
        }
    }

    private static void verifyCameraState(GameTestHelper helper, CameraSubsystem camera) {
        var other = new CameraSubsystem(camera.getOwner(), "probe_camera", camera.attr);
        AimPose mount = camera.samplePose(1);
        Vec3 inherited = mount.position().add(mount.forward().scale(1000));
        camera.enterView(inherited);
        other.enterView(inherited);
        camera.turnView(new Vec2(0, (float) Math.toRadians(10)));
        camera.updateView(.05f);
        float expectedStep = (float) Math.toRadians(Math.min(10, camera.attr.staticAttribute.yawSpeed * .05f));
        helper.assertTrue(Math.abs(camera.getViewAngles().y - expectedStep) < 1e-4,
                "摄像头必须按配置转速追赶目标");
        Vec2 actual = camera.getViewAngles();
        for (int i = 0; i < 100; i++) {
            camera.samplePose(.5f);
        }
        helper.assertTrue(actual.equals(camera.getViewAngles()), "渲染插值不能推进转轴");
        helper.assertTrue(Math.abs(other.getViewAngles().y) < 1e-5, "不同摄像头实例的观察角必须独立");
        for (int i = 0; i < 20; i++) {
            camera.updateView(.05f);
        }
        helper.assertTrue(Math.abs(camera.getViewAngles().y - Math.toRadians(10)) < 1e-4,
                "零后续输入时摄像头必须继续追到目标");
        // 切入时继承前一视点的有限世界点，不能用设备缓存角覆盖它。
        Vec3 next = ViewMath.follow(mount.rotation(), new Vec2(0, (float) Math.toRadians(-10)), mount.position()).worldPoint();
        camera.enterView(next);
        helper.assertTrue(Math.abs(camera.getViewAngles().y - Math.toRadians(-10)) < 1e-4, "切入必须继承目标");
    }
}
