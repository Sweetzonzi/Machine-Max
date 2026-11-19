package io.github.sweetzonzi.machine_max.common.gametest;

import cn.solarmoon.spark_core.util.PPhase;
import com.google.gson.JsonParser;
import com.jme3.bullet.RotationOrder;
import com.jme3.bullet.joints.New6Dof;
import com.jme3.bullet.joints.motors.MotorParam;
import com.jme3.math.Matrix3f;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.mojang.serialization.JsonOps;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.energy.IMechPowerConsumer;
import io.github.sweetzonzi.machine_max.common.mech.energy.MechPower;
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions;
import io.github.sweetzonzi.machine_max.common.mech.part.PartFactory;
import io.github.sweetzonzi.machine_max.common.mech.signal.TrackControlSignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.WheelControlSignal;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.*;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.*;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.*;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AdvancedConnector;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** 真子系统的动力预算、轴速目标与末端电机指令，原生读写均在物理线程执行。 */
@GameTestHolder(MachineMax.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PowertrainDriveGameTest {
    private static final ResourceLocation OPEN = id("gametest_drive_open");
    private static final ResourceLocation LOCK = id("gametest_drive_lock");
    private static final ResourceLocation GEAR = id("gametest_drive_gear");
    private static final ResourceLocation TRACK = id("gametest_drive_track");

    @GameTest(template = "part_foundation", batch = "mmPowertrainDrive", timeoutTicks = 160)
    public static void budgetsAndShaftSpeedTargets(GameTestHelper helper) {
        MMDynamicRes.SERVER_STATIC_SUBSYSTEM_ATTRS.put(OPEN, TransmissionSubsystemStaticAttr.CODEC.codec()
                .parse(JsonOps.INSTANCE, JsonParser.parseString("{\"diff_lock\":\"false\"}")).getOrThrow());
        MMDynamicRes.SERVER_STATIC_SUBSYSTEM_ATTRS.put(LOCK, TransmissionSubsystemStaticAttr.CODEC.codec()
                .parse(JsonOps.INSTANCE, JsonParser.parseString("{\"diff_lock\":\"true\"}")).getOrThrow());
        MMDynamicRes.SERVER_STATIC_SUBSYSTEM_ATTRS.put(GEAR, GearboxSubsystemStaticAttr.CODEC.codec()
                .parse(JsonOps.INSTANCE, JsonParser.parseString("{\"final_ratio\":1,\"ratios\":[1,-2]}")).getOrThrow());
        MMDynamicRes.SERVER_STATIC_SUBSYSTEM_ATTRS.put(TRACK, TrackDriveSubsystemStaticAttr.CODEC.codec()
                .parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow());
        RegularPart chassis = createPart(helper, "r700_chassis");
        RegularPart wheel = createPart(helper, "r700_wheel");
        var result = new CompletableFuture<Void>();
        chassis.rootSubPart.getPhysicsLevel().submitImmediateTask(PPhase.PRE, () -> {
            try {
                verifyTransmission(helper, chassis.rootSubPart);
                verifyEndDrives(helper, chassis.rootSubPart, wheel.rootSubPart);
                result.complete(null);
            } catch (Throwable error) {
                result.completeExceptionally(error);
            }
            return null;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(result.isDone(), "等待动力链物理线程检查完成");
            try {
                try {
                    result.join();
                } catch (CompletionException error) {
                    helper.fail(error.getCause().toString());
                }
            } finally {
                chassis.discardUnattached();
                wheel.discardUnattached();
                for (var definition : List.of(OPEN, LOCK, GEAR, TRACK)) {
                    MMDynamicRes.SERVER_STATIC_SUBSYSTEM_ATTRS.remove(definition);
                }
            }
        });
    }

    private static RegularPart createPart(GameTestHelper helper, String name) {
        var type = PartDefinitions.regular(false).get(id(name));
        return PartFactory.createRegular(type, type.variants.keySet().iterator().next(), helper.getLevel());
    }

    private static void verifyTransmission(GameTestHelper helper, SubPart host) {
        var left = new Sink("left");
        var right = new Sink("right");
        var ignored = new Sink("ignored");
        var open = new TransmissionSubsystem(host, "open", new TransmissionSubsystemAttr(OPEN,
                Map.of("left", 2f, "right", -2f, "ignored", 0f)));
        open.getMechPowerTargets().putAll(Map.of("left", left, "right", right, "ignored", ignored));
        left.feedback = 3f;
        right.feedback = -3f;
        ignored.feedback = 500f;
        open.onPostPhysicsTick();
        near(helper, open.getFeedbackSpeed(), 6f, "零减速比端不拉低有效轴速反馈");
        for (float power : new float[]{1000f, -1000f, 0f}) {
            open.onMechPowerReceived("source", new MechPower(power, 10f));
            open.onPrePhysicsTick();
            near(helper, left.last.power() + right.last.power(), power, "开放差速预算守恒");
            near(helper, left.last.speed(), 5f, "正减速比折算目标轴速");
            near(helper, right.last.speed(), -5f, "负减速比只翻转轴向");
            helper.assertTrue(ignored.last.isEmpty(), "零减速比端不参与分配");
        }
        var locked = new TransmissionSubsystem(host, "locked",
                new TransmissionSubsystemAttr(LOCK, Map.of("left", 1f, "right", 1f)));
        locked.getMechPowerTargets().putAll(Map.of("left", left, "right", right));
        for (float direction : new float[]{1f, -1f}) {
            left.feedback = direction * 2f;
            right.feedback = direction * 8f;
            locked.onMechPowerReceived("source", new MechPower(1000f, direction));
            locked.onPrePhysicsTick();
            near(helper, left.last.power() + right.last.power(), 1000f, "锁止差速预算守恒");
            helper.assertTrue(left.last.power() > right.last.power(), "正反向均将驱动预算转向慢端");
            near(helper, left.last.speed(), direction, "慢端追随输入轴速");
            near(helper, right.last.speed(), direction, "快端回拖到输入轴速");
            left.feedback = right.feedback = direction * 4f;
            locked.onPrePhysicsTick();
            near(helper, left.last.speed(), direction, "同速超转的各端保留输入轴速目标");
            near(helper, right.last.speed(), direction, "同速超转的各端都需要回拖");
            locked.onMechPowerReceived("source", new MechPower(-1000f, direction));
            locked.onPrePhysicsTick();
            near(helper, left.last.power() + right.last.power(), -1000f, "锁止差速保留反拖预算");
        }
        var gear = new GearboxSubsystem(host, "gear", new GearboxSubsystemAttr(GEAR, "left", Map.of()));
        gear.getMechPowerTargets().put("left", left);
        for (int tick = 0; tick <= Math.ceil(gear.attr.staticAttribute.switchTime * gear.getPhysicsLevel().getTps()); tick++) {
            gear.onPrePhysicsTick();
        }
        gear.setClutched(true);
        gear.setCurrentGear(1);
        gear.onMechPowerReceived("source", new MechPower(1000f, 10f));
        gear.onPrePhysicsTick();
        near(helper, left.last.power(), 1000f, "变速箱保留预算");
        near(helper, left.last.speed(), -5f, "变速箱折算目标轴速");
    }

    private static void verifyEndDrives(GameTestHelper helper, SubPart host, SubPart wheel) {
        var driver = (WheelDriverSubsystem) host.findOwnSubsystem("subsystem.machine_max.back_wheel_driver");
        AdvancedConnector connector = driver.connector;
        var other = wheel.connectors.values().iterator().next();
        Matrix3f frame = new Quaternion().fromAngles(0f, .6f, .2f).toRotationMatrix(new Matrix3f());
        var joint = new New6Dof(host.body, wheel.body, Vector3f.ZERO, Vector3f.ZERO,
                frame, frame, RotationOrder.XZY);
        connector.attachedConnector = other;
        other.attachedConnector = connector;
        connector.joint = other.joint = joint;
        joint.set(MotorParam.LowerLimit, 3, 1f);
        joint.set(MotorParam.UpperLimit, 3, -1f);
        var motor = joint.getRotationMotor(0);
        var track = new TrackDriveSubsystem(host, "track",
                new TrackDriveSubsystemAttr(TRACK, connector.getName(), Map.of(), Map.of()));
        var bike = (MotorbikeControllerSubsystem) host.findOwnSubsystem("subsystem.machine_max.motorbike_controller");
        var slipProbe = new SlipProbe(host, bike.attr);
        for (float direction : new float[]{1f, -1f}) {
            host.body.setAngularVelocity(Vector3f.ZERO);
            wheel.body.setAngularVelocity(joint.getAxis(0, null).mult(direction * 4f));
            near(helper, driver.getRollingSpeed(), direction * 4f, "轮速按真实关节轴读取而非车身 X 分量");
            slipProbe.speed = 4f * driver.getRollingRadius();
            near(helper, slipProbe.slip(driver), 0f, "ABS 与动力链使用同一个关节轴速");
            driver.getSignalChannel("car_control").put(driver, new WheelControlSignal(0f, 0f));
            driver.onMechPowerReceived("source", new MechPower(1000f, direction * 2f));
            driver.onPrePhysicsTick();
            float biasedTarget = -motor.get(MotorParam.TargetVelocity);
            helper.assertTrue(biasedTarget * direction > 2f, "正功率驱动保留输入轴速偏置");
            near(helper, motor.get(MotorParam.MaxMotorForce), 500f, "扭矩按原始轴速还原");
            for (float actual : new float[]{2f, 100f}) {
                wheel.body.setAngularVelocity(joint.getAxis(0, null).mult(direction * actual));
                driver.onPrePhysicsTick();
                near(helper, -motor.get(MotorParam.TargetVelocity), biasedTarget, "达到输入轴速或超速不改变偏置");
            }
            helper.assertTrue(biasedTarget * direction < 100f, "严重超速时目标低于实测轴速，电机可回拖");
            wheel.body.setAngularVelocity(joint.getAxis(0, null).mult(direction * 4f));
            driver.onPostPhysicsTick();
            near(helper, driver.getFeedbackSpeed(), direction * 4f, "动力反馈采用同一实测关节轴速");
            driver.onMechPowerReceived("source", new MechPower(1000f, direction * 6f));
            driver.onPrePhysicsTick();
            helper.assertTrue(-motor.get(MotorParam.TargetVelocity) * direction > 6f,
                    "落后时以输入目标轴速为基点补偿牵引");
            near(helper, motor.get(MotorParam.MaxMotorForce), 1000f / 6f, "偏置不得进入扭矩分母");
            driver.onMechPowerReceived("source", new MechPower(1000f, direction * 4f));
            driver.onPrePhysicsTick();
            helper.assertTrue(-motor.get(MotorParam.TargetVelocity) * direction > 4f,
                    "达到输入轴速时保留完整牵引偏置");
            driver.onMechPowerReceived("source", new MechPower(-1000f, direction * 2f));
            driver.onPrePhysicsTick();
            near(helper, motor.get(MotorParam.TargetVelocity), 0f, "反拖制动目标停在零速");

            track.getSignalChannel("car_control").put(track, new TrackControlSignal(0f, 0f, true));
            track.onMechPowerReceived("source", new MechPower(1000f, direction * 2f));
            track.onPrePhysicsTick();
            helper.assertTrue(-motor.get(MotorParam.TargetVelocity) * direction > 2f,
                    "履带超速时保留基于输入轴速的偏置");
            near(helper, track.getRollingSpeed(), direction * 4f, "履带使用真实关节轴速");
            track.onMechPowerReceived("source", new MechPower(1000f, direction * 6f));
            track.onPrePhysicsTick();
            helper.assertTrue(-motor.get(MotorParam.TargetVelocity) * direction > 6f,
                    "履带牵引以输入轴速为基点");
            track.getSignalChannel("car_control").put(track,
                    new TrackControlSignal(0f, 0f, true, direction * 5f * track.radius()));
            track.onPrePhysicsTick();
            helper.assertTrue(-motor.get(MotorParam.TargetVelocity) * direction <= 5f,
                    "牵引补偿不能越过履带转向限速");
            track.getSignalChannel("car_control").put(track,
                    new TrackControlSignal(0f, 0f, true, direction * 3f * track.radius()));
            track.onPrePhysicsTick();
            near(helper, -motor.get(MotorParam.TargetVelocity), direction * 3f, "履带转向超速追随限速目标");
            near(helper, track.getDriveTorque(), 0f, "转向超速只使用制动预算");
        }
    }

    private static final class SlipProbe extends MotorbikeControllerSubsystem {
        SlipProbe(SubPart owner, MotorbikeControllerSubsystemAttr attr) { super(owner, "slip_probe", attr); }
        float slip(WheelDriverSubsystem wheel) { return calculateSlipRatio(wheel); }
    }

    private static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, name); }

    private static void near(GameTestHelper helper, float actual, float expected, String message) {
        helper.assertTrue(Math.abs(actual - expected) < 1e-3f, message + ": " + actual + " != " + expected);
    }

    private static final class Sink implements IMechPowerConsumer {
        final String name;
        float feedback;
        MechPower last = MechPower.EMPTY;
        Sink(String name) { this.name = name; }
        @Override public String getName() { return name; }
        @Override public void onMechPowerReceived(String producer, MechPower power) { last = power; }
        @Override public float getFeedbackSpeed() { return feedback; }
        @Override public void setFeedbackSpeed(float speed) { feedback = speed; }
    }
}
