package io.github.sweetzonzi.machine_max.common.gametest;

import cn.solarmoon.spark_core.util.PPhase;
import com.jme3.bullet.collision.Activation;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.simsilica.mathd.Vec3d;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions;
import io.github.sweetzonzi.machine_max.common.mech.part.PartFactory;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.network.payload.SubPartSyncPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.concurrent.CompletableFuture;

/**
 * 验证真实轮面接触经物理步结算后，在休眠时保留支撑，唤醒腾空时清零。
 * 停放阶段同时验证支撑快照的载荷编码往返。
 */
@GameTestHolder(MachineMax.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WheelSupportGameTest {
    // GameTest 的游戏 tick 可以远快于异步物理步；预算还要覆盖入世后的地形等待。
    private static final int TIMEOUT_TICKS = 1200;

    private record AirborneStep(boolean wasSleeping, boolean activeAndUnsupported) {}

    /** 轮子落到模板地板后令 Bullet 进入休眠，再在物理步开始时抬离地面。 */
    @GameTest(template = "part_foundation", batch = "mmWheelSupport", timeoutTicks = TIMEOUT_TICKS)
    public static void sleepingWheelRetainsSupportAndClearsItOnTheFirstAirborneStep(GameTestHelper helper) {
        var level = helper.getLevel();
        var type = PartDefinitions.regular(false).get(ResourceLocation.parse("k17:k17_wheel"));
        var part = PartFactory.createRegular(type, "left", level);
        var wheel = part.rootSubPart;
        var vehicle = new VehicleCore(level, part);
        // 从轮胎静止高度上方落下，避免入世时与地板深度重叠。
        BlockPos start = helper.absolutePos(new BlockPos(2, 3, 2));
        part.setTransform(new Transform().setTranslation(new Vector3f(start.getX() + .5f, start.getY(), start.getZ() + .5f)));
        ObjectManager.addVehicle(vehicle);
        helper.runAtTickTime(TIMEOUT_TICKS, () -> {
            if (!vehicle.isRemoved) ObjectManager.removeVehicle(vehicle);
        });

        var sleepingSupport = new CompletableFuture<Boolean>();
        var airborne = new CompletableFuture<AirborneStep>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(wheel.isWheelSupported(), "The floor must support the wheel"))
                .thenExecute(() -> observeSleepingSupport(wheel, sleepingSupport, false))
                .thenWaitUntil(() -> helper.assertTrue(sleepingSupport.isDone(), "The wheel must enter Bullet sleep"))
                .thenExecute(() -> helper.assertTrue(sleepingSupport.join(), "The sleeping wheel must retain support"))
                .thenWaitUntil(() -> helper.assertTrue(!wheel.isActive(), "The sleeping state must be published"))
                .thenExecute(() -> {
                    helper.assertTrue(Vector3f.ZERO.equals(wheel.getLinearVelocity())
                                    && Vector3f.ZERO.equals(wheel.getAngularVelocity()),
                            "The published sleeping-wheel velocities must be zero");
                    var support = wheel.wheelSupportSnapshot();
                    helper.assertTrue(support != null && Boolean.TRUE.equals(support.syncData().getFirst().value()),
                            "The published sleeping-wheel state must carry support");
                    var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
                    try {
                        SubPartSyncPayload.STREAM_CODEC.encode(buffer, support);
                        var decoded = SubPartSyncPayload.STREAM_CODEC.decode(buffer);
                        helper.assertTrue(decoded.equals(support),
                                "The support value must survive the SubPart sync codec");
                    } finally {
                        buffer.release();
                    }
                    var physics = wheel.getPhysicsLevel();
                    // 至少跨一个物理步，确保主线程发布休眠状态时排入 ALL 的任务已经执行。
                    physics.submitDelayedTask(PPhase.PRE, 1, () -> {
                        boolean wasSleeping = wheel.body.getActivationState() == Activation.sleeping;
                        Vec3d position = wheel.body.getPhysicsLocationDp(null);
                        position.y += 5;
                        wheel.body.setPhysicsLocationDp(position);
                        wheel.body.activate(true);
                        // 同一物理步的 POST 阶段读取结算态，不等下一次主线程同步。
                        physics.submitImmediateTask(PPhase.POST, () -> {
                            airborne.complete(new AirborneStep(wasSleeping,
                                    wheel.body.isActive() && !wheel.isWheelSupported()));
                            return null;
                        });
                        return null;
                    });
                })
                .thenWaitUntil(() -> helper.assertTrue(airborne.isDone(), "Wait for the airborne physics step"))
                .thenExecute(() -> {
                    ObjectManager.removeVehicle(vehicle);
                    var step = airborne.join();
                    helper.assertTrue(step.wasSleeping(), "Publishing zero velocities must not wake the wheel");
                    helper.assertTrue(step.activeAndUnsupported(),
                            "Active and unsupported must be published on the first step");
                })
                .thenSucceed();
    }

    private static void observeSleepingSupport(SubPart wheel, CompletableFuture<Boolean> result, boolean sleptLastStep) {
        var physics = wheel.getPhysicsLevel();
        var body = wheel.body;
        physics.submitDelayedTask(PPhase.POST, 1, () -> {
            if (wheel.isRemoved() || !body.isInWorld()) return null;
            boolean sleeping = body.getActivationState() == Activation.sleeping;
            boolean supported = wheel.isWheelSupported();
            // 既检查进入休眠的那一步，也检查已经休眠的下一步。
            if (sleeping && (!supported || sleptLastStep)) {
                result.complete(supported);
            } else {
                observeSleepingSupport(wheel, result, sleeping);
            }
            return null;
        });
    }
}
