package io.github.sweetzonzi.machine_max.common.gametest;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.control.ControlGroupSet;
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions;
import io.github.sweetzonzi.machine_max.common.mech.part.PartFactory;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.WeaponControllerSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.WeaponControllerSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPartType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.event.VehicleStructureChangeEvent;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MTLB 的车长在基础组里必须既能瞄准 PKT 机枪塔，也能让它开火。
 *
 * <p>PKT 机枪塔是独立零件（{@code machine_max:pkt_turret}），挂在车体的机枪塔轴上，
 * 自己带着武器控制器、方向机、高低机、发射器与供弹链。它没有座位：操作者是宿主的车长，
 * 因此"能不能用"完全由车长座的控制组预设（{@code machine_max:mtlb_commander}）决定——
 * 基础组发得出哪条频道，它就只能响应哪条频道。</p>
 *
 * <p>本用例守的契约是<b>成对</b>的：基础组发出的瞄准频道与开火频道都要落到这台控制器的
 * {@code aim_inputs} / {@code fire_inputs} 上。只有瞄准那条接通时，玩家看到炮塔跟着视线转、
 * 按下去却没有一发弹，而且整条链路没有一处报错——这正是需要在这里拦住的形态。
 * 用例不指定用哪个键（主武器类目或副武器类目都算接通），键位怎么分配由预设自己决定。</p>
 */
@GameTestHolder(MachineMax.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MtlbPktWiringGameTest {
    private static final String BATCH = "mmMtlbPktWiring";

    private static final String HULL = "machine_max:mtlb";
    private static final String TURRET = "machine_max:pkt_turret";
    private static final String COMMANDER_SEAT = "subsystem.machine_max.commander_seat";
    private static final String WEAPON_CONTROLLER = "subsystem.machine_max.weapon";

    private MtlbPktWiringGameTest() {
    }

    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void commanderBaseGroupBothAimsAndFiresThePkt(GameTestHelper helper) {
        RegularPartType hullType = PartDefinitions.regular(false).get(ResourceLocation.parse(HULL));
        helper.assertTrue(hullType != null, HULL + " 的部件定义必须已加载");
        RegularPart hull = PartFactory.createRegular(hullType, "default", helper.getLevel());
        VehicleCore vehicle = new VehicleCore(helper.getLevel(), hull);
        try {
            RegularPart turret = attachPktTurret(helper, vehicle, hull);
            // 座位在部件创建时装载预设，那时机枪塔还没挂上来；接上之后重做一次发现。
            vehicle.onStructureChanged(VehicleStructureChangeEvent.Reason.ATTACH);

            Map<String, AbstractSubsystem> byName = collect(hull);
            byName.putAll(collect(turret));
            AbstractSubsystem seat = require(helper, byName, COMMANDER_SEAT);
            AbstractSubsystem weapon = require(helper, byName, WEAPON_CONTROLLER);
            helper.assertTrue(seat instanceof SeatSubsystem, COMMANDER_SEAT + " 必须是座位");
            helper.assertTrue(weapon instanceof WeaponControllerSubsystem, WEAPON_CONTROLLER + " 必须是武器控制器");

            SeatSubsystem commander = (SeatSubsystem) seat;
            WeaponControllerSubsystem controller = (WeaponControllerSubsystem) weapon;
            ControlGroupSet groups = commander.getControlGroupSet();
            WeaponControllerSubsystemStaticAttr attr = controller.attr.staticAttribute;

            List<String> baseView = sorted(groups.getMergedViewTargets().keySet());
            String aim = firstShared(baseView, attr.getAimInputs());
            helper.assertTrue(aim != null,
                    "车长基础组必须有一条瞄准频道落到 PKT 机枪塔上：基础组发出 " + baseView
                            + "，控制器读 " + attr.getAimInputs());

            List<String> baseFire = sorted(groups.getMergedMainWeaponTargets().keySet());
            baseFire.addAll(sorted(groups.getMergedSecondaryWeaponTargets().keySet()));
            String fire = firstShared(baseFire, attr.getFireInputs());
            helper.assertTrue(fire != null,
                    "车长基础组必须有一条开火频道落到 PKT 机枪塔上，否则只能瞄准没法开火：基础组发出 "
                            + baseFire + "，控制器读 " + attr.getFireInputs());

            // 发现同样要接通：座位按基础组的频道解析武器控制器，HUD 与炮镜都由这份结果驱动。
            helper.assertTrue(commander.getActiveWeaponControllers().contains(controller),
                    "车长基础组必须发现 PKT 机枪塔的武器控制器，实际发现 "
                            + commander.getActiveWeaponControllers().size() + " 台");
            helper.succeed();
        } finally {
            vehicle.onRemoveFromLevel();
        }
    }

    /**
     * 把 PKT 机枪塔挂到车体的机枪塔轴上：车体侧是对外连接点（Simple），塔侧是方位轴（Advanced）。
     */
    private static RegularPart attachPktTurret(GameTestHelper helper, VehicleCore vehicle, RegularPart hull) {
        RegularPartType type = PartDefinitions.regular(false).get(ResourceLocation.parse(TURRET));
        helper.assertTrue(type != null, TURRET + " 的部件定义必须已加载");
        RegularPart turret = PartFactory.createRegular(type, "default", helper.getLevel());
        AbstractConnector advanced = turret.subParts.get("sub_part.machine_max.main")
                .connectors.get("connector.machine_max.pkt_hull_axis");
        AbstractConnector simple = hull.subParts.get("sub_part.machine_max.main")
                .connectors.get("connector.machine_max.hull_pkt_axis");
        helper.assertTrue(advanced != null, "PKT 机枪塔必须有车体轴连接点");
        helper.assertTrue(simple != null, "MTLB 车体必须有机枪塔挂点");
        vehicle.attachConnector(advanced, simple, turret);
        helper.assertTrue(advanced.hasPart(), "PKT 机枪塔必须挂到车体的机枪塔轴上");
        return turret;
    }

    private static Map<String, AbstractSubsystem> collect(RegularPart part) {
        Map<String, AbstractSubsystem> byName = new LinkedHashMap<>();
        part.subParts.values().forEach(subPart -> byName.putAll(subPart.subsystems));
        return byName;
    }

    private static AbstractSubsystem require(GameTestHelper helper,
                                             Map<String, AbstractSubsystem> byName, String name) {
        AbstractSubsystem subsystem = byName.get(name);
        helper.assertTrue(subsystem != null, "子系统必须存在：" + name);
        return subsystem;
    }

    private static List<String> sorted(Set<String> channels) {
        List<String> list = new ArrayList<>(channels);
        list.sort(String::compareTo);
        return list;
    }

    /** 频道名是纯字符串，没有文法可解析，因此"接通"就是整串相等。 */
    private static String firstShared(List<String> emitted, List<String> accepted) {
        for (String channel : emitted) {
            if (accepted.contains(channel)) return channel;
        }
        return null;
    }
}
