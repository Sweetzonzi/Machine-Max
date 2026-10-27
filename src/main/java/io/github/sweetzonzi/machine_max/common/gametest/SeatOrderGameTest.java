package io.github.sweetzonzi.machine_max.common.gametest;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.control.ControlGroupSet;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPartType;
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions;
import io.github.sweetzonzi.machine_max.common.mech.part.PartFactory;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.AbstractSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.SeatSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.seat.SeatOrder;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * 座位几何顺序与控制组复位的纯逻辑检查。
 *
 * <p>这里不涉及骑乘：换座的骑乘迁移需要真实实体与物理世界，放在客户端实测验收。
 * 本文件覆盖三件容易回归的纯函数约定——单个座位没有“下一个”、没有代理实体的座位不可用、
 * 控制组复位是幂等的且落在预设的默认值上。</p>
 */
@GameTestHolder(MachineMax.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SeatOrderGameTest {

    private SeatOrderGameTest() {
    }

    private record Selection(RegularPartType type, String variant) {
    }

    /** 找一个带座椅子系统的普通部件型号，用于构造最小装配体 */
    private static Selection seatPart() {
        return PartDefinitions.regular(false).values().stream()
                .flatMap(type -> type.variants.entrySet().stream()
                        .filter(entry -> entry.getValue().subParts.values().stream()
                                .anyMatch(sub -> sub.subsystems.values().stream()
                                        .anyMatch(attr -> attr instanceof SeatSubsystemAttr)))
                        .map(entry -> new Selection(type, entry.getKey())))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("内容包里没有带座椅子系统的普通部件"));
    }

    @GameTest(template = "part_foundation", batch = "mmSeatOrder", timeoutTicks = 100)
    public static void loneSeatHasNoNextTarget(GameTestHelper helper) {
        Selection selected = seatPart();
        Level level = helper.getLevel();
        RegularPart part = PartFactory.createRegular(selected.type(), selected.variant(), level);
        VehicleCore vehicle = null;
        try {
            vehicle = new VehicleCore(level, part);
            List<SeatSubsystem> seats = SeatOrder.seats(vehicle);
            helper.assertTrue(!seats.isEmpty(), "装配体里应当至少有一个座位");
            SeatSubsystem seat = seats.getFirst();

            helper.assertTrue(SeatOrder.layout(vehicle, seat).indexOf(seat) == 0,
                    "只有一个座位时它自己排在第一位");
            helper.assertTrue(SeatOrder.next(vehicle, seat, true) == null,
                    "只有一个座位时没有下一个可换的座位");
            helper.assertTrue(SeatOrder.order(vehicle, seat).size() == seats.size(),
                    "排序不丢座位");
            // 部件未入世 → 没有代理实体 → 任何座位都不可换入
            helper.assertTrue(!SeatOrder.available(seat),
                    "没有代理实体的座位不可用");
            helper.succeed();
        } finally {
            if (vehicle != null) vehicle.onRemoveFromLevel();
            else part.destroy();
        }
    }

    @GameTest(template = "part_foundation", batch = "mmSeatOrder", timeoutTicks = 100)
    public static void controlGroupResetIsIdempotent(GameTestHelper helper) {
        Selection selected = seatPart();
        Level level = helper.getLevel();
        RegularPart part = PartFactory.createRegular(selected.type(), selected.variant(), level);
        VehicleCore vehicle = null;
        try {
            vehicle = new VehicleCore(level, part);
            SeatSubsystem seat = SeatOrder.seats(vehicle).getFirst();

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
