package io.github.sweetzonzi.machine_max.common.gametest;

import cn.solarmoon.spark_core.util.PPhase;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.simsilica.mathd.Vec3d;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions;
import io.github.sweetzonzi.machine_max.common.mech.part.PartFactory;
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
    /** 轮子落到模板地板后令 Bullet 进入休眠，再在物理步开始时抬离地面。 */
    @GameTest(template = "part_foundation", batch = "mmWheelSupport", timeoutTicks = 180)
    public static void sleepingWheelRetainsSupportAndClearsItOnTheFirstAirborneStep(GameTestHelper helper) {
        var level = helper.getLevel();
        var type = PartDefinitions.regular(false).get(ResourceLocation.parse("machine_max:k17_wheel"));
        var part = PartFactory.createRegular(type, "left", level);
        var wheel = part.rootSubPart;
        var vehicle = new VehicleCore(level, part);
        BlockPos start = helper.absolutePos(new BlockPos(2, 2, 2));
        part.setTransform(new Transform().setTranslation(new Vector3f(start.getX() + .5f, start.getY(), start.getZ() + .5f)));
        ObjectManager.addVehicle(vehicle);
        helper.runAtTickTime(175, () -> {
            if (!vehicle.isRemoved) ObjectManager.removeVehicle(vehicle);
        });

        var airborne = new CompletableFuture<Boolean>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(wheel.isWheelSupported(), "The floor must support the wheel"))
                .thenExecute(() -> wheel.getPhysicsLevel().submitImmediateTask(PPhase.PRE, () -> {
                    wheel.body.setLinearVelocity(Vector3f.ZERO);
                    wheel.body.setAngularVelocity(Vector3f.ZERO);
                    // GameTest 的游戏 tick 可以快于物理步，预置计时让 Bullet 在可用步内进入休眠。
                    wheel.body.setDeactivationTime(10);
                    return null;
                }))
                .thenWaitUntil(() -> helper.assertTrue(!wheel.isActive() && wheel.isWheelSupported(),
                        "The sleeping wheel must retain support"))
                .thenExecute(() -> {
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
                    physics.submitImmediateTask(PPhase.PRE, () -> {
                        Vec3d position = wheel.body.getPhysicsLocationDp(null);
                        position.y += 5;
                        wheel.body.setPhysicsLocationDp(position);
                        wheel.body.activate(true);
                        // 同一物理步的 POST 阶段读取结算态，不等下一次主线程同步。
                        physics.submitImmediateTask(PPhase.POST, () -> {
                            airborne.complete(wheel.body.isActive() && !wheel.isWheelSupported());
                            return null;
                        });
                        return null;
                    });
                })
                .thenWaitUntil(() -> helper.assertTrue(airborne.isDone(), "Wait for the airborne physics step"))
                .thenExecute(() -> {
                    ObjectManager.removeVehicle(vehicle);
                    helper.assertTrue(airborne.join(), "Active and unsupported must be published on the first step");
                })
                .thenSucceed();
    }
}
