package io.github.sweetzonzi.machine_max.common.gametest;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.control.ControlGroupSet;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPartType;
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions;
import io.github.sweetzonzi.machine_max.common.mech.part.PartFactory;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.AbstractSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.SeatSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 座位枚举顺序、控制组复位与乘员实体名称读取检查。
 *
 * <p>座次只由结构决定：部件 UUID 升序 → 子零件声明序 → 子系统声明序，不读位姿、名字与
 * {@code SubPart#id}。这里不涉及骑乘：换座的骑乘迁移需要真实实体与物理世界，放在客户端实测验收。
 * 本文件覆盖座位枚举与“下一个座位”的候选集、代理实体可用性、控制组复位，以及乘员改名、缺失和
 * 离座时的名称读数。</p>
 */
@GameTestHolder(MachineMax.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SeatSwitchGameTest {

    private SeatSwitchGameTest() {
    }

    @GameTest(template = "part_foundation", batch = "mmSeatSwitch", timeoutTicks = 100)
    public static void occupantNameFollowsTheEntityAndClearsOnExit(GameTestHelper helper) {
        Selection selected = seatPart();
        RegularPart part = PartFactory.createRegular(selected.type(), selected.variant(), helper.getLevel());
        VehicleCore vehicle = null;
        try {
            vehicle = new VehicleCore(helper.getLevel(), part);
            SeatSubsystem seat = vehicle.getAllSeats().getFirst();
            var passenger = net.minecraft.world.entity.EntityType.PIG.create(helper.getLevel());
            helper.assertTrue(passenger != null, "the passenger entity is available");
            passenger.setCustomName(net.minecraft.network.chat.Component.literal("First name"));
            seat.attachForSwitch(passenger);
            helper.assertTrue(seat.getOccupantName().equals("First name"), "the name comes from the current entity");
            passenger.setCustomName(net.minecraft.network.chat.Component.literal("Updated name"));
            helper.assertTrue(seat.getOccupantName().equals("Updated name"), "renaming is visible without a seat update");
            SeatSubsystem peer = new SeatSubsystem(seat.getOwner(), seat.getName(), seat.getAttr());
            var initial = new java.util.ArrayList<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>>();
            seat.forEachSyncedValue(initial::add);
            peer.getSynchedData().assignValues(initial);
            helper.assertTrue(peer.passenger == null && peer.isOccupied() && peer.getOccupantName().isEmpty(),
                    "an occupied seat without a local passenger reference has an unknown name");
            seat.getSynchedData().packDirty();
            seat.detachForSwitch(passenger);
            helper.assertTrue(seat.getOccupantName().isEmpty(), "leaving clears the local entity name");
            var changes = seat.getSynchedData().packDirty();
            helper.assertTrue(changes != null, "leaving publishes a dirty seat update");
            peer.getSynchedData().assignValues(changes);
            helper.assertTrue(!peer.isOccupied() && peer.getOccupantName().isEmpty(),
                    "the normal delta update clears occupation");
            helper.succeed();
        } finally {
            if (vehicle != null) vehicle.onRemoveFromLevel();
            else part.destroy();
        }
    }

    private record Selection(RegularPartType type, String variant) {
    }

    /**
     * 找一个**恰好一个座位**的普通部件型号，用于构造最小装配体。
     *
     * <p>必须按座位数量过滤：定义表是哈希映射，遍历顺序随机，单座断言因此只对单座型号成立。</p>
     */
    private static Selection seatPart() {
        return PartDefinitions.regular(false).values().stream()
                .flatMap(type -> type.variants.entrySet().stream()
                        .filter(entry -> entry.getValue().subParts.values().stream()
                                .flatMap(sub -> sub.getSubsystems().values().stream())
                                .filter(attr -> attr instanceof SeatSubsystemAttr)
                                .count() == 1)
                        .map(entry -> new Selection(type, entry.getKey())))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("内容包里没有恰好带一个座椅子系统的普通部件"));
    }

    /** 期望的座次：部件 UUID 升序 → 子零件声明序 → 子系统声明序。 */
    private static List<SeatSubsystem> expectedSeatOrder(VehicleCore vehicle) {
        List<UUID> partIds = new ArrayList<>(vehicle.partMap.keySet());
        Collections.sort(partIds);
        List<SeatSubsystem> expected = new ArrayList<>();
        for (UUID partId : partIds) {
            for (SubPart subPart : vehicle.partMap.get(partId).subParts.values()) {
                for (AbstractSubsystem subsystem : subPart.ownSubsystems()) {
                    if (subsystem instanceof SeatSubsystem seat) expected.add(seat);
                }
            }
        }
        return expected;
    }

    @GameTest(template = "part_foundation", batch = "mmSeatSwitch", timeoutTicks = 100)
    public static void seatEnumerationFollowsStructureAndIsRepeatable(GameTestHelper helper) {
        Selection selected = seatPart();
        Level level = helper.getLevel();
        RegularPart part = PartFactory.createRegular(selected.type(), selected.variant(), level);
        VehicleCore vehicle = null;
        try {
            vehicle = new VehicleCore(level, part);
            List<SeatSubsystem> seats = vehicle.getAllSeats();
            helper.assertTrue(!seats.isEmpty(), "装配体里应当至少有一个座位");
            List<SeatSubsystem> expected = expectedSeatOrder(vehicle);
            helper.assertTrue(seats.equals(expected), "座位枚举必须按 部件 UUID 升序 → 子零件声明序 → 子系统声明序");
            helper.assertTrue(vehicle.getAllSeats().equals(seats), "同一台载具反复枚举得到同一顺序");

            SeatSubsystem seat = seats.getFirst();
            helper.assertTrue(vehicle.getFirstSeat() == seat, "第一个座位取结构顺序首位");
            helper.assertTrue(vehicle.getEmptySeats().size() == seats.size(), "未入世时全部座位记为未占用");
            // 部件未入世 → 没有代理实体 → 任何座位都不可换入
            helper.assertTrue(!seat.isAvailable(), "没有代理实体的座位不可用");
            helper.assertTrue(vehicle.nextSeat(seat, true) == null, "没有可用座位时没有下一个可换的座位");
            helper.assertTrue(vehicle.nextSeat(null, true) == null, "当前座位不在本装配体座位表里时没有目标");
            helper.succeed();
        } finally {
            if (vehicle != null) vehicle.onRemoveFromLevel();
            else part.destroy();
        }
    }

    @GameTest(template = "part_foundation", batch = "mmSeatSwitch", timeoutTicks = 100)
    public static void controlGroupResetIsIdempotent(GameTestHelper helper) {
        Selection selected = seatPart();
        Level level = helper.getLevel();
        RegularPart part = PartFactory.createRegular(selected.type(), selected.variant(), level);
        VehicleCore vehicle = null;
        try {
            vehicle = new VehicleCore(level, part);
            SeatSubsystem seat = vehicle.getAllSeats().getFirst();

            int first = seat.resetControlGroupToPreset();
            ControlGroupSet afterFirst = seat.getControlGroupSet();
            int second = seat.resetControlGroupToPreset();
            helper.assertTrue(first == second, "复位目标稳定：两次调用得到同一个索引");
            helper.assertTrue(seat.getControlGroupSet() == afterFirst,
                    "第二次复位是幂等的：不重建集合、不重复清信号");

            ResourceLocation presetId = seat.getAttr().getControlGroupPresetRl();
            int expected = -1;
            if (!presetId.equals(AbstractSubsystemAttr.NO_CONTROL_GROUP_PRESET)) {
                ControlGroupSet preset = MMDynamicRes.CONTROL_GROUP_PRESETS.get(presetId);
                if (preset != null) expected = preset.getActiveIndex();
            }
            helper.assertTrue(first == expected,
                    "复位值取该座位预设的默认活动组，没有预设时为 -1");
            helper.succeed();
        } finally {
            if (vehicle != null) vehicle.onRemoveFromLevel();
            else part.destroy();
        }
    }
}
