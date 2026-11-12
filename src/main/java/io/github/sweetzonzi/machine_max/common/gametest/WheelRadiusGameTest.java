package io.github.sweetzonzi.machine_max.common.gametest;

import cn.solarmoon.spark_core.animation.model.ModelIndex;
import cn.solarmoon.spark_core.animation.model.origin.*;
import cn.solarmoon.spark_core.util.PPhase;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jme3.bullet.RotationOrder;
import com.jme3.bullet.joints.New6Dof;
import com.jme3.bullet.joints.motors.MotorParam;
import com.jme3.math.Matrix3f;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.mojang.serialization.JsonOps;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.part.AbstractPartType;
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions;
import io.github.sweetzonzi.machine_max.common.mech.part.PartFactory;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.*;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.*;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.*;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.*;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.connector.*;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.*;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/** 自建模型与定义验证分件轮组；所有刚体保持在物理世界外，原生查询在物理线程执行。 */
@GameTestHolder(MachineMax.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WheelRadiusGameTest {
    private static final ResourceLocation MODEL = ResourceLocation.parse("machine_max:gametest_wheel_radius");
    private static final ResourceLocation CONNECTOR = ResourceLocation.parse("machine_max:gametest_wheel_connector");
    private static final ResourceLocation DRIVER = ResourceLocation.parse("machine_max:gametest_wheel_driver");
    private static final ResourceLocation BIKE = ResourceLocation.parse("machine_max:gametest_wheel_bike");

    @GameTest(template = "part_foundation", batch = "mmWheelRadius", timeoutTicks = 160)
    public static void splitWheelsFollowActiveSurfacesAndOneHopLimits(GameTestHelper helper) {
        var bones = new LinkedHashMap<String, OBone>();
        var children = new JsonObject();
        for (var entry : Map.of("chassis", 2f, "hub", .3f, "tire", .5f, "twin", .6f, "deep", .9f).entrySet()) {
            String name = entry.getKey();
            float radius = entry.getValue();
            var cube = new OCube(new Vec3(-.1, -radius, -radius), new Vec3(.2, 2 * radius, 2 * radius),
                    Vec3.ZERO, Vec3.ZERO, 0.0, new OUVUnion(Vec2.ZERO, null), false, 16, 16);
            bones.put(name, new OBone(name, null, Vec3.ZERO, Vec3.ZERO,
                    new LinkedHashMap<>(), List.of(cube), null));
            children.add(name, JsonParser.parseString("""
                    {"start_bone":"%s","mass":10,"hit_boxes":{"%s":
                    {"type":"wheel","condition":"1-local.get('%s_off')"}}}
                    """.formatted(name, name, name)));
        }
        OModel.getORIGINS().put(new ModelIndex("part", MODEL), new OModel(16, 16, bones));
        MMDynamicRes.CONNECTORS.put(CONNECTOR, ConnectorStaticAttr.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"type\":\"advanced\",\"direction\":\"xp\"}")).getOrThrow());
        MMDynamicRes.SERVER_STATIC_SUBSYSTEM_ATTRS.put(DRIVER, WheelDriverSubsystemStaticAttr.CODEC.codec()
                .parse(JsonOps.INSTANCE, JsonParser.parseString("{\"abs_enabled\":true}")).getOrThrow());
        MMDynamicRes.SERVER_STATIC_SUBSYSTEM_ATTRS.put(BIKE, MotorbikeControllerSubsystemStaticAttr.CODEC.codec()
                .parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow());
        var variant = new JsonObject();
        variant.addProperty("model", MODEL.toString());
        variant.add("sub_parts", children);
        var definition = new JsonObject();
        definition.addProperty("share_durability", false);
        definition.add("variants", variant);
        var previousDefinitions = PartDefinitions.all(false);
        RegularPart part;
        try {
            var type = RegularPartType.CODEC.parse(JsonOps.INSTANCE, definition).getOrThrow();
            type.setRegistryKey(MODEL);
            var candidates = new LinkedHashMap<>(previousDefinitions);
            candidates.put(MODEL, type);
            PartDefinitions.validateAndPublish(false, candidates);
            part = PartFactory.createRegular(type, "default", helper.getLevel());
        } catch (Throwable error) {
            clearDefinitions(previousDefinitions);
            throw error;
        }
        var result = new CompletableFuture<Void>();
        part.rootSubPart.getPhysicsLevel().submitImmediateTask(PPhase.PRE, () -> {
            try {
                verify(helper, part);
                result.complete(null);
            } catch (Throwable error) {
                result.completeExceptionally(error);
            }
            return null;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(result.isDone(), "等待轮组查询与 ABS 检查完成");
            try {
                result.join();
            } finally {
                part.discardUnattached();
                clearDefinitions(previousDefinitions);
            }
        });
    }

    private static void verify(GameTestHelper helper, RegularPart part) {
        var codec = WheelDriverSubsystemStaticAttr.CODEC.codec();
        var legacy = codec.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"abs_enabled\":true,\"abs_wheel_radius\":123}")).getOrThrow();
        var encoded = codec.encodeStart(JsonOps.INSTANCE, legacy).getOrThrow().getAsJsonObject();
        helper.assertTrue(legacy.isAbsEnabled() && !encoded.has("abs_wheel_radius"),
                "旧半径字段须可加载且不进入有效配置");
        SubPart chassis = part.subParts.get("chassis"), hub = part.subParts.get("hub");
        SubPart tire = part.subParts.get("tire"), twin = part.subParts.get("twin"), deep = part.subParts.get("deep");
        var drive = connect(chassis, hub, "drive");
        var driver = new WheelDriverSubsystem(chassis, "driver",
                new WheelDriverSubsystemAttr(DRIVER, drive.getName(), Map.of(), Map.of()));
        near(helper, driver.getRollingRadius(), .3f, "整轮自身半径且排除回到车身的连接");
        var mount = connect(hub, tire, "tire_mount");
        mount.joint.set(MotorParam.LowerLimit, 0, -.02f);
        mount.joint.set(MotorParam.UpperLimit, 0, .02f);
        mount.joint.set(MotorParam.LowerLimit, 3, -.1f);
        mount.joint.set(MotorParam.UpperLimit, 3, .1f);
        mount.joint.enableSpring(3, true);
        mount.joint.set(MotorParam.Stiffness, 3, 100f);
        mount.joint.set(MotorParam.Damping, 3, 1f);
        near(helper, driver.getRollingRadius(), .5f, "x 平移与 xr 弹簧允许识别外胎");
        for (int axis : new int[]{1, 2, 4, 5}) {
            mount.joint.set(MotorParam.UpperLimit, axis, .01f);
            near(helper, driver.getRollingRadius(), .3f, "有径向或摆动自由度的连接须排除：" + axis);
            mount.joint.set(MotorParam.UpperLimit, axis, 0f);
        }
        connect(tire, deep, "deep_mount");
        near(helper, driver.getRollingRadius(), .5f, "二跳轮面不参与");
        var twinMount = connect(hub, twin, "twin_mount");
        near(helper, driver.getRollingRadius(), .6f, "直接连接的双胎取最大值");
        active(part, "twin", false);
        near(helper, driver.getRollingRadius(), .5f, "条件关闭的轮面退出候选");

        var bikeAttr = new MotorbikeControllerSubsystemAttr(BIKE, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        var bike = new TestBike(chassis, bikeAttr);
        bike.bind(driver);
        chassis.body.setAngularVelocity(Vector3f.ZERO);
        hub.body.setAngularVelocity(new Vector3f(-20, 0, 0));
        tire.body.setAngularVelocity(Vector3f.ZERO);
        bike.speed = 10f;
        near(helper, bike.brake(driver, .8f), .8f, "ABS 采用轮毂转速乘外胎半径，忽略外胎独立转速");
        bike.sampleHeight();
        near(helper, (float) bike.getComputedSteeringCenter().y, -.5f, "平衡高度采用有效外胎半径");
        active(part, "tire", false);
        near(helper, driver.getRollingRadius(), .3f, "条件爆胎回到轮毂半径");
        bike.sampleHeight();
        near(helper, (float) bike.getComputedSteeringCenter().y, -.3f, "无拓扑变化时平衡高度响应条件爆胎");
        helper.assertTrue(bike.brake(driver, .8f) < .8f, "轮毂尺寸下轮速不足须触发 ABS");
        active(part, "tire", true);
        mount.attachedConnector.attachedConnector = null;
        mount.attachedConnector = null;
        near(helper, driver.getRollingRadius(), .3f, "外胎脱落退出候选");
        active(part, "hub", false);
        near(helper, driver.getRollingRadius(), 0f, "不存在有效轮面时返回零");
        near(helper, bike.brake(driver, .8f), .8f, "无有效轮面时保留普通制动");
        active(part, "twin", true);
        twinMount.attachedConnector.attachedConnector = null;
        near(helper, driver.getRollingRadius(), 0f, "正在断开的一侧连接不参与");
        drive.attachedConnector = null;
        near(helper, driver.getRollingRadius(), 0f, "轮毂断开时没有半径");
    }

    private static AdvancedConnector connect(SubPart from, SubPart to, String name) {
        var attr = new ConnectorAttr("fixture", CONNECTOR, null, "", Map.of(), Map.of(), false);
        var a = new AdvancedConnector(name, attr, from, new Transform());
        var b = new SimpleConnector(name + "_other", attr, to, new Transform());
        var joint = new New6Dof(from.body, to.body, Vector3f.ZERO, Vector3f.ZERO,
                Matrix3f.IDENTITY, Matrix3f.IDENTITY, RotationOrder.XZY);
        for (int axis = 0; axis < 6; axis++) {
            joint.set(MotorParam.LowerLimit, axis, 0f);
            joint.set(MotorParam.UpperLimit, axis, 0f);
        }
        a.attachedConnector = b;
        b.attachedConnector = a;
        a.joint = b.joint = joint;
        from.connectors.put(a.getName(), a);
        to.connectors.put(b.getName(), b);
        return a;
    }

    private static void active(RegularPart part, String name, boolean active) {
        part.getVariables().put(name + "_off", active ? 0.0 : 1.0);
        part.subParts.get(name).hitBoxes.values().forEach(box -> box.updateActive());
    }

    private static void near(GameTestHelper helper, float actual, float expected, String message) {
        helper.assertTrue(Math.abs(actual - expected) < 1e-5f, message + "：" + actual + " != " + expected);
    }

    private static void clearDefinitions(Map<ResourceLocation, AbstractPartType> previousDefinitions) {
        PartDefinitions.validateAndPublish(false, previousDefinitions);
        OModel.getORIGINS().remove(new ModelIndex("part", MODEL));
        MMDynamicRes.CONNECTORS.remove(CONNECTOR);
        MMDynamicRes.SERVER_STATIC_SUBSYSTEM_ATTRS.remove(DRIVER);
        MMDynamicRes.SERVER_STATIC_SUBSYSTEM_ATTRS.remove(BIKE);
    }

    private static final class TestBike extends MotorbikeControllerSubsystem {
        TestBike(SubPart owner, MotorbikeControllerSubsystemAttr attr) { super(owner, "bike", attr); }
        void bind(WheelDriverSubsystem driver) { onActuatorDiscovered(driver, "car_control"); onHandShakeComplete(); }
        float brake(WheelDriverSubsystem driver, float raw) { return calculateEffectiveBrake(driver, raw); }
        void sampleHeight() { onGroundControlBegin(); }
    }
}
