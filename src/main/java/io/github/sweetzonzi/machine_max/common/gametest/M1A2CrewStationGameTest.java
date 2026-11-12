package io.github.sweetzonzi.machine_max.common.gametest;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.control.ControlGroup;
import io.github.sweetzonzi.machine_max.common.mech.control.ControlGroupSet;
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions;
import io.github.sweetzonzi.machine_max.common.mech.part.PartFactory;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractViewpointSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.WeaponControllerSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.AbstractSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPartType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.event.VehicleStructureChangeEvent;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * M1A2 SEP 炮塔的座位编制与枪位归属。
 *
 * <p>真车四人车组：驾驶员、炮手、装填手，车长有舱内与探出两种姿态。炮塔上因此是四个座位——
 * 炮手、车长（舱内）、装填手、车长（探出）。五个武器站里，主炮／M240 同轴／CSAMM 归炮手，
 * CROWS 归舱内车长，指挥塔 M2 归探出车长，装填手舱口 M240 归装填手。</p>
 *
 * <p>本文件固定三件事：座位清单与它们的控制组预设真的存在、每个座位只驱动自己那把枪、
 * 每把舱外枪的座位能看到自己那把枪的机械瞄具而看不到别人的。第一件是原来的失效点——
 * 座位声明了一个不存在的预设时 {@code ControlGroupSet} 会静默停在校准值，座位变成纯乘客。</p>
 */
@GameTestHolder(MachineMax.MOD_ID)
@PrefixGameTestTemplate(false)
public final class M1A2CrewStationGameTest {
    private static final String BATCH = "mmM1A2CrewStation";
    private static final String TURRET = "m1a2_sep:m1a2_turret";

    private static final String GUNNER = "subsystem.m1a2_sep.gunner_seat";
    private static final String COMMANDER = "subsystem.m1a2_sep.commander_seat";
    private static final String LOADER = "subsystem.m1a2_sep.loader_seat";
    private static final String HEAD_OUT = "subsystem.m1a2_sep.aft_mg_seat";

    private M1A2CrewStationGameTest() {
    }

    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void turretSeatsAreTheRealCrewStations(GameTestHelper helper) {
        Fixture fixture = Fixture.build(helper);
        try {
            List<SeatSubsystem> seats = fixture.seats();
            List<String> names = seats.stream().map(seat -> seat.name).toList();
            helper.assertTrue(names.size() == 4, "炮塔上应当有四个座位，实际 " + names);
            for (String expected : List.of(GUNNER, COMMANDER, LOADER, HEAD_OUT)) {
                helper.assertTrue(names.contains(expected), "缺少座位 " + expected + "，实际 " + names);
            }
            for (SeatSubsystem seat : seats) {
                ResourceLocation preset = seat.getAttr().getControlGroupPresetRl();
                helper.assertTrue(!preset.equals(AbstractSubsystemAttr.NO_CONTROL_GROUP_PRESET),
                        seat.name + " 必须声明控制组预设");
                helper.assertTrue(MMDynamicRes.CONTROL_GROUP_PRESETS.containsKey(preset),
                        seat.name + " 的控制组预设必须存在：" + preset);
                helper.assertTrue(seat.getControlGroupSet() != ControlGroupSet.EMPTY,
                        seat.name + " 的控制组预设未解析，座位会变成纯乘客");
            }
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void everySeatDrivesItsOwnWeapon(GameTestHelper helper) {
        Fixture fixture = Fixture.build(helper);
        try {
            assertMainController(helper, fixture, GUNNER, null, "subsystem.m1a2_sep.main_weapon");
            assertMainController(helper, fixture, LOADER, null, "subsystem.m1a2_sep.port_mg_controller");
            assertMainController(helper, fixture, HEAD_OUT, null, "subsystem.m1a2_sep.aft_mg_controller");
            // 舱内车长默认只观察，切到遥控武器站那一组才接管 CROWS
            assertMainController(helper, fixture, COMMANDER, null, null);
            assertMainController(helper, fixture, COMMANDER,
                    "controlGroup.m1a2_sep.commander_rws", "subsystem.m1a2_sep.rws_controller");
            assertMainController(helper, fixture, COMMANDER,
                    "controlGroup.m1a2_sep.commander_override_csamm", "subsystem.m1a2_sep.main_weapon");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void externalGunSeatsSeeTheirOwnIronSight(GameTestHelper helper) {
        Fixture fixture = Fixture.build(helper);
        try {
            assertCameras(helper, fixture, LOADER,
                    List.of("subsystem.m1a2_sep.port_mg_sight"));
            assertCameras(helper, fixture, HEAD_OUT,
                    List.of("subsystem.m1a2_sep.aft_mg_sight"));

            List<String> commander = cameraNames(fixture.seat(helper, COMMANDER));
            helper.assertTrue(commander.contains("subsystem.m1a2_sep.rws_sight"),
                    "舱内车长要能切到遥控武器站的瞄具，实际 " + commander);
            helper.assertTrue(!commander.contains("subsystem.m1a2_sep.port_mg_sight"),
                    "舱内车长不应当看到装填手那把 M240 的瞄具，实际 " + commander);
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    /**
     * 座位当前活动控制组的主武器控制器必须是指定的那一台。
     *
     * <p>{@code groupName} 为 null 表示基础组。主武器控制器决定固定炮镜跟随哪套机构，
     * 因此这条同时约束"座位只驱动自己那把枪"与"炮镜绑在自己那把枪上"。</p>
     */
    private static void assertMainController(GameTestHelper helper, Fixture fixture, String seatName,
                                             @Nullable String groupName, @Nullable String expectedController) {
        SeatSubsystem seat = fixture.seat(helper, seatName);
        activate(seat, groupName);
        WeaponControllerSubsystem controller = seat.getActiveMainWeaponController();
        String where = seatName + (groupName == null ? " 的基础组" : " 的 " + groupName);
        if (expectedController == null) {
            helper.assertTrue(controller == null,
                    where + " 不应当驱动任何主武器，实际 " + (controller == null ? "无" : controller.name));
            return;
        }
        helper.assertTrue(controller != null, where + " 应当驱动 " + expectedController + "，实际没有");
        helper.assertTrue(expectedController.equals(controller.name),
                where + " 应当驱动 " + expectedController + "，实际 " + controller.name);
    }

    private static void assertCameras(GameTestHelper helper, Fixture fixture, String seatName,
                                      List<String> expected) {
        List<String> actual = cameraNames(fixture.seat(helper, seatName));
        helper.assertTrue(actual.size() == expected.size(),
                seatName + " 应当恰好发现 " + expected + "，实际 " + actual);
        helper.assertTrue(actual.containsAll(expected),
                seatName + " 应当发现 " + expected + "，实际 " + actual);
    }

    private static List<String> cameraNames(SeatSubsystem seat) {
        List<String> names = new ArrayList<>();
        for (AbstractViewpointSubsystem camera : seat.getDiscoveredCameras()) {
            names.add(camera.name);
        }
        return names;
    }

    /** 按组名切换座位的活动控制组；null 表示回到基础组。走与面板编辑器同一条入口。 */
    private static void activate(SeatSubsystem seat, @Nullable String groupName) {
        ControlGroupSet current = seat.getControlGroupSet();
        int index = -1;
        if (groupName != null) {
            for (int i = 0; i < current.groups.size(); i++) {
                ControlGroup group = current.groups.get(i);
                if (groupName.equals(group.name)) {
                    index = i;
                    break;
                }
            }
            if (index < 0) {
                throw new IllegalStateException("控制组不存在：" + groupName);
            }
        }
        if (current.getActiveIndex() == index) {
            return;
        }
        seat.setControlGroupSet(new ControlGroupSet(current.baseGroup, current.groups,
                current.getGuiActions(), index));
    }

    /** 只装炮塔这一个零件的装配体；装配体必须先存在，{@code "global"} 才有信号总线。 */
    private record Fixture(VehicleCore vehicle, RegularPart part) {
        static Fixture build(GameTestHelper helper) {
            RegularPartType type = PartDefinitions.regular(false).get(ResourceLocation.parse(TURRET));
            helper.assertTrue(type != null, "M1A2 SEP 炮塔的部件定义必须已加载");
            RegularPart part = PartFactory.createRegular(type, "desert", helper.getLevel());
            VehicleCore vehicle = new VehicleCore(helper.getLevel(), part);
            // 座位在构造期装载控制组预设，此时同一装配体里的其它零件尚未就位，
            // 跨子部件与装配体总线的发现只会落空。结构变化的第二阶段是它们的重做时机。
            vehicle.onStructureChanged(VehicleStructureChangeEvent.Reason.ATTACH);
            return new Fixture(vehicle, part);
        }

        List<SeatSubsystem> seats() {
            List<SeatSubsystem> seats = new ArrayList<>();
            for (var subPart : part.subParts.values()) {
                for (var subsystem : subPart.subsystems.values()) {
                    if (subsystem instanceof SeatSubsystem seat) {
                        seats.add(seat);
                    }
                }
            }
            return seats;
        }

        SeatSubsystem seat(GameTestHelper helper, String name) {
            for (SeatSubsystem seat : seats()) {
                if (name.equals(seat.name)) {
                    return seat;
                }
            }
            helper.assertTrue(false, "座位必须存在：" + name);
            throw new IllegalStateException("座位不存在：" + name);
        }

        void close() {
            vehicle.onRemoveFromLevel();
        }
    }
}
