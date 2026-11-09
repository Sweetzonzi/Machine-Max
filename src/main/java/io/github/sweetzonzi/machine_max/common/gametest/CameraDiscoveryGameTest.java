package io.github.sweetzonzi.machine_max.common.gametest;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions;
import io.github.sweetzonzi.machine_max.common.mech.part.PartFactory;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractViewpointSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

/**
 * 用真实 T-90A 炮塔接线验证镜头发现：炮手席经 {@code global} 找到另一子部件上的炮手镜，
 * 外部车长席按名直连找到同日车长镜；查询本身不写镜头输入槽。
 */
@GameTestHolder(MachineMax.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CameraDiscoveryGameTest {

    @GameTest(template = "part_foundation", batch = "mmViewpoints", timeoutTicks = 100)
    public static void seatResolvesSightsWithoutWritingTheirInputs(GameTestHelper helper) {
        var type = PartDefinitions.regular(false).get(ResourceLocation.parse("t90a:t90a_turret"));
        RegularPart part = PartFactory.createRegular(type, type.variants.keySet().iterator().next(), helper.getLevel());
        VehicleCore vehicle = new VehicleCore(helper.getLevel(), part);
        try {
            SubPart turret = part.subParts.get("sub_part.t90a.turret");
            SubPart gun = part.subParts.get("sub_part.t90a.gun");
            var gunnerSeat = (SeatSubsystem) turret.subsystems.get("subsystem.t90a.gunner_seat");
            var gunnerSight = (AbstractViewpointSubsystem) gun.subsystems.get("subsystem.t90a.gunner_sight");
            var commanderSeat = (SeatSubsystem) turret.subsystems.get("subsystem.t90a.external_commander_seat");
            var commanderSight = (AbstractViewpointSubsystem) turret.subsystems.get("subsystem.t90a.commander_day_sight");

            helper.assertTrue(gunnerSeat.getTargets().getOrDefault("gunner_sight", Map.of()).containsKey("global"),
                    "炮手席的镜头发现线路以装配体为首跳");
            helper.assertTrue(List.of(gunnerSight).equals(gunnerSeat.getDiscoveredCameras()),
                    "global 线路必须解析到另一子部件上的炮手镜");
            helper.assertTrue(List.of(commanderSight).equals(commanderSeat.getDiscoveredCameras()),
                    "按名直连的镜头必须命中同一 SubPart 的观察设备");
            helper.assertTrue(gunnerSight.getSignalInputChannels().get("gunner_sight") == null,
                    "只读发现不能为镜头建输入频道");
            helper.assertTrue(commanderSight.getSignalInputChannels().get("commander_day_sight") == null,
                    "直连发现也不能写镜头输入频道");

            vehicle.subSystemController.unsubscribe(gunnerSight);
            gunnerSeat.onVehicleStructureChanged();
            helper.assertTrue(gunnerSeat.getDiscoveredCameras().isEmpty(),
                    "退出装配体订阅的镜头必须离开候选列表");

            vehicle.subSystemController.subscribe(gunnerSight);
            gunnerSeat.onVehicleStructureChanged();
            helper.assertTrue(List.of(gunnerSight).equals(gunnerSeat.getDiscoveredCameras()),
                    "重新订阅后镜头回到候选列表，且只出现一次");
            helper.succeed();
        } finally {
            vehicle.onRemoveFromLevel();
        }
    }
}
