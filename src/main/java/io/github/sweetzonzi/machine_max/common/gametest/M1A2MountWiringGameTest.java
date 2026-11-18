package io.github.sweetzonzi.machine_max.common.gametest;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions;
import io.github.sweetzonzi.machine_max.common.mech.part.PartFactory;
import io.github.sweetzonzi.machine_max.common.mech.control.ControlGroup;
import io.github.sweetzonzi.machine_max.common.mech.control.ControlGroupSet;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalReceiver;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractControllableSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AmmoPath;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.IAmmoConsumer;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.IAmmoSupplier;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.LauncherSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.WeaponControllerSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPartType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * M1A2 SEP 炮塔上每个机枪挂载的控制器与自动装弹机只连自己那根管。
 *
 * <p>炮塔上并列、车顶、舱门与后部五个挂载里有三根 M2HB、两根 M240，共用同一对开火频道
 * （{@code weapon.mg.m2hb} / {@code weapon.mg.m240}），弹药也按口径共用
 * （{@code ammo_12_7mm} / {@code ammo_7_62mm}）。频道本身区分不了是哪一根枪，
 * 因此区分只能落在接线上了：控制器与装弹机在偏航子部件，发射器在俯仰子部件，
 * 中间那一跳走两者之间的俯仰连接点。</p>
 *
 * <p>用例把炮塔与遥控武器站装成同一台装配体：炮塔零件带四个挂载，武器站零件带 CROWS。
 * 沿实际接线解析终端：开火频道必须恰好到达本挂载的发射器；供弹频道由发射器自己发起查询，
 * 必须恰好到达本挂载的自动装弹机。装成装配体是必要的——{@code "global"} 指向装配体的
 * 信号总线，只有存在装配体时「广播到同口径全部发射器」才会真的发生，独立零件上这条路径是空的。</p>
 */
@GameTestHolder(MachineMax.MOD_ID)
@PrefixGameTestTemplate(false)
public final class M1A2MountWiringGameTest {
    private static final String BATCH = "mmM1A2MountWiring";

    /** 一个机枪挂载：控制器用开火频道找发射器，发射器用弹药频道找自己的自动装弹机。 */
    private record Mount(String controller, String fireChannel, String launcher,
                         String autoloader, String ammoChannel) {
    }

    private static final List<Mount> MOUNTS = List.of(
            new Mount("subsystem.m1a2_sep.antimateriel_controller", "weapon.mg.m2hb",
                    "subsystem.m1a2_sep.antimateriel_launcher",
                    "subsystem.m1a2_sep.antimateriel_loader", "ammo_12_7mm"),
            new Mount("subsystem.m1a2_sep.coax_controller", "weapon.mg.m240",
                    "subsystem.m1a2_sep.coax_launcher",
                    "subsystem.m1a2_sep.coax_loader", "ammo_7_62mm"),
            new Mount("subsystem.m1a2_sep.rws_controller", "weapon.mg.m2hb",
                    "subsystem.m1a2_sep.rws_launcher",
                    "subsystem.m1a2_sep.rws_loader", "ammo_12_7mm"),
            new Mount("subsystem.m1a2_sep.loader_mg_controller", "weapon.mg.m240",
                    "subsystem.m1a2_sep.loader_mg_launcher",
                    "subsystem.m1a2_sep.loader_mg_loader", "ammo_7_62mm"),
            new Mount("subsystem.m1a2_sep.aa_controller", "weapon.mg.m2hb",
                    "subsystem.m1a2_sep.aa_launcher",
                    "subsystem.m1a2_sep.aa_loader", "ammo_12_7mm"));

    private M1A2MountWiringGameTest() {
    }

    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void everyMountReachesOnlyItsOwnLauncher(GameTestHelper helper) {
        RegularPartType type = PartDefinitions.regular(false)
                .get(ResourceLocation.parse("m1a2_sep:m1a2_turret"));
        helper.assertTrue(type != null, "M1A2 SEP 炮塔的部件定义必须已加载");
        RegularPart part = PartFactory.createRegular(type, "desert", helper.getLevel());
        VehicleCore vehicle = new VehicleCore(helper.getLevel(), part);
        RegularPart rws = attachRws(helper, vehicle, part);
        try {
            Map<String, AbstractSubsystem> byName = collectSubsystems(part);
            byName.putAll(collectSubsystems(rws));
            List<AbstractSubsystem> launchers = new ArrayList<>();
            for (AbstractSubsystem subsystem : byName.values()) {
                if (subsystem instanceof LauncherSubsystem) launchers.add(subsystem);
            }
            // 只有一根管时频道天然唯一，本用例就失去意义；发射器少于挂载数说明部件结构变了。
            helper.assertTrue(launchers.size() >= MOUNTS.size(),
                    "炮塔上的发射器数量应不少于机枪挂载数，实际 " + launchers.size());

            assertEveryMount(helper, byName, launchers);

            // 结构变化后首跳表整体重建、发现重做，接线结果必须与初次装车一致。
            vehicle.onStructureChanged(VehicleStructureChangeEvent.Reason.ATTACH);
            assertEveryMount(helper, byName, launchers);
            helper.succeed();
        } finally {
            vehicle.onRemoveFromLevel();
        }
    }

    /**
     * 遥控武器站的瞄准与开火走同一对挂点：控制器在炮塔零件里，方位与俯仰驱动器在武器站零件里，
     * 中间那一跳只能落在挂点连接对上。{@code aim_drive_rws} 因此必须恰好到达这两台驱动器——
     * 到达别的驱动器，或者一台都到不了，都会在游戏里表现为「武器站不转」。
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void rwsAimCrossesTheMountConnector(GameTestHelper helper) {
        RegularPartType type = PartDefinitions.regular(false)
                .get(ResourceLocation.parse("m1a2_sep:m1a2_turret"));
        helper.assertTrue(type != null, "M1A2 SEP 炮塔的部件定义必须已加载");
        RegularPart part = PartFactory.createRegular(type, "desert", helper.getLevel());
        VehicleCore vehicle = new VehicleCore(helper.getLevel(), part);
        RegularPart rws = attachRws(helper, vehicle, part);
        try {
            Map<String, AbstractSubsystem> byName = collectSubsystems(part);
            byName.putAll(collectSubsystems(rws));
            AbstractSubsystem controller = require(helper, byName, "subsystem.m1a2_sep.rws_controller");
            AbstractSubsystem yawDriver = require(helper, byName, "subsystem.m1a2_sep.rws_azimuth_driver");
            AbstractSubsystem pitchDriver = require(helper, byName, "subsystem.m1a2_sep.rws_elevation_driver");

            assertAimReachesStation(helper, controller, yawDriver, pitchDriver);
            // 结构变化后首跳表整体重建、发现重做，接线结果必须与初次装车一致。
            vehicle.onStructureChanged(VehicleStructureChangeEvent.Reason.ATTACH);
            assertAimReachesStation(helper, controller, yawDriver, pitchDriver);
            helper.succeed();
        } finally {
            vehicle.onRemoveFromLevel();
        }
    }

    private static void assertAimReachesStation(GameTestHelper helper, AbstractSubsystem controller,
                                                AbstractSubsystem yawDriver, AbstractSubsystem pitchDriver) {
        List<ISignalReceiver> terminals = new ArrayList<>();
        controller.resolveTargets("aim_drive_rws").values().forEach(terminals::addAll);
        helper.assertTrue(terminals.size() == 2,
                controller.name + " 的 aim_drive_rws 应恰好到达两台驱动器，实际 " + addresses(terminals));
        helper.assertTrue(terminals.contains(yawDriver), "aim_drive_rws 必须到达武器站的方位驱动器");
        helper.assertTrue(terminals.contains(pitchDriver), "aim_drive_rws 必须到达武器站的俯仰驱动器");
    }

    /**
     * 火控终端是一个独立的单连接点零件：它没有自己的枪与驱动器，靠广播认领同装配体里接受这些频道的执行器。
     * 用例验零件本身装得起来——几何里的碰撞骨与挂点定位器都在、型号建出武器控制器、两条输出频道都指向装配体总线。
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void terminalPartMountsAndBroadcasts(GameTestHelper helper) {
        RegularPartType type = PartDefinitions.regular(false)
                .get(ResourceLocation.parse("m1a2_sep:m153_crows_terminal"));
        helper.assertTrue(type != null, "M153 CROWS 火控终端的部件定义必须已加载");
        RegularPart part = PartFactory.createRegular(type, null, helper.getLevel());
        VehicleCore vehicle = new VehicleCore(helper.getLevel(), part);
        try {
            SubPart main = part.subParts.get("sub_part.machine_max.main");
            helper.assertTrue(main != null, "火控终端必须只有一个主零件");
            helper.assertTrue(!main.hitBoxes.isEmpty(), "几何里的碰撞骨必须建成碰撞箱");
            helper.assertTrue(main.connectors.containsKey("connector.m1a2_sep.rws_terminal_mount"),
                    "火控终端必须有挂点连接点");
            AbstractSubsystem subsystem = require(helper, collectSubsystems(part),
                    "subsystem.m1a2_sep.rws_terminal_controller");
            helper.assertTrue(subsystem instanceof WeaponControllerSubsystem,
                    "火控终端持有的必须是武器控制器子系统，实际 " + subsystem.getClass().getSimpleName());
            Map<String, List<String>> targets = subsystem.getTargetNames();
            helper.assertTrue(List.of("global").equals(targets.get("aim_drive_rws")),
                    "aim_drive_rws 必须广播到装配体，实际 " + targets.get("aim_drive_rws"));
            helper.assertTrue(List.of("global").equals(targets.get("weapon.mg.m2hb")),
                    "weapon.mg.m2hb 必须广播到装配体，实际 " + targets.get("weapon.mg.m2hb"));
            helper.succeed();
        } finally {
            vehicle.onRemoveFromLevel();
        }
    }

    /**
     * 车长座的三层分工：默认只驱动武器站，备用组连带主炮与并列机枪但不带越级量，超控组带 1 压过炮长。
     *
     * <p>主炮控制器 {@code machine_max:main_weapon_controller} 的瞄准与开火列表只有
     * {@code crew_gunner_*} 与 {@code crew_commander_*}：通用频道一旦回到列表里，
     * 任何座位都能把炮塔带走。因此车长的默认组只发 {@code crew_any_aim} 与
     * {@code crew_any_secondary_fire}——武器站与加装件认这两条；{@code crew_commander_aim}
     * 与 {@code crew_commander_fire} 只出现在备用组与超控组。</p>
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void commanderTiersSplitCrowsBackupAndOverride(GameTestHelper helper) {
        RegularPartType type = PartDefinitions.regular(false)
                .get(ResourceLocation.parse("m1a2_sep:m1a2_turret"));
        helper.assertTrue(type != null, "M1A2 SEP 炮塔的部件定义必须已加载");
        RegularPart part = PartFactory.createRegular(type, "desert", helper.getLevel());
        VehicleCore vehicle = new VehicleCore(helper.getLevel(), part);
        try {
            // 武器站是独立零件：车长预设要带动它，先按挂点装上，同 rwsAimCrossesTheMountConnector。
            RegularPart rws = attachRws(helper, vehicle, part);
            Map<String, AbstractSubsystem> byName = collectSubsystems(part);
            byName.putAll(collectSubsystems(rws));
            AbstractSubsystem seat = require(helper, byName, "subsystem.m1a2_sep.commander_seat");
            WeaponControllerSubsystem mainGun =
                    (WeaponControllerSubsystem) require(helper, byName, "subsystem.m1a2_sep.main_controller");
            WeaponControllerSubsystem crows =
                    (WeaponControllerSubsystem) require(helper, byName, "subsystem.m1a2_sep.rws_controller");

            // 主炮只认本车的炮长与车长：列表里带通用频道时，任何座位都能凭它把炮塔带走。
            helper.assertTrue(!mainGun.getAcceptedChannels().contains("crew_any_aim"),
                    "主炮控制器不得接受 crew_any_aim：那等于把炮塔交给任何座位");
            helper.assertTrue(!mainGun.getAcceptedChannels().contains("crew_any_fire"),
                    "主炮控制器不得接受 crew_any_fire：那等于让任何座位都能开这一炮");

            ControlGroupSet groups = ((AbstractControllableSubsystem) seat).getControlGroupSet();
            ControlGroup base = groups.baseGroup;
            helper.assertTrue(!base.viewTargets.containsKey("crew_commander_aim"),
                    "车长基础组不得发 crew_commander_aim：主炮控制器认这条频道，炮塔会跟着车长的默认视角转");
            assertDrives(helper, "车长基础组", base, crows, true);
            assertDrives(helper, "车长基础组", base, mainGun, false);
            // 加装件只认通用频道，默认组必须把它们广播出去。
            helper.assertTrue(base.viewTargets.containsKey("crew_any_aim"),
                    "车长基础组必须广播 crew_any_aim，加装的武器站靠它跟随");
            helper.assertTrue(base.secondaryWeaponTargets.containsKey("crew_any_secondary_fire"),
                    "车长基础组必须广播 crew_any_secondary_fire，加装的武器站靠它开火");

            // 车长只要三个组：默认 + 备用火控 + 超控，后两个都要能带动全部武器。
            helper.assertTrue(groups.groups.size() == 2,
                    "车长预设只应有备用火控与超控两个子组，实际 " + groups.groups.size());
            WeaponControllerSubsystem coax762 =
                    (WeaponControllerSubsystem) require(helper, byName, "subsystem.m1a2_sep.coax_controller");
            WeaponControllerSubsystem csamm =
                    (WeaponControllerSubsystem) require(helper, byName, "subsystem.m1a2_sep.antimateriel_controller");
            List<WeaponControllerSubsystem> allWeapons = List.of(crows, mainGun, coax762, csamm);

            // 备用火控：武器站与主炮／机枪一起动，不带越级量——炮长在位时由炮长优先。
            ControlGroup backup = group(groups, "controlGroup.m1a2_sep.commander_backup_fire");
            helper.assertTrue(backup != null, "车长预设必须带备用火控组");
            for (WeaponControllerSubsystem weapon : allWeapons) {
                assertDrives(helper, "备用火控组", backup, weapon, true);
            }
            helper.assertTrue(backup.priorities.isEmpty(),
                    "备用火控组不得带越级量：它只在炮长缺席时生效，抢不走在位的炮长");

            // 超控：同样的频道与目标，越级量 1 才压得过炮长。
            ControlGroup override = group(groups, "controlGroup.m1a2_sep.commander_override");
            helper.assertTrue(override != null, "车长预设必须带超控组");
            for (WeaponControllerSubsystem weapon : allWeapons) {
                assertDrives(helper, "超控组", override, weapon, true);
            }
            for (String channel : List.of("crew_commander_aim", "crew_commander_aim_coax762",
                    "crew_commander_aim_csamm", "crew_commander_fire",
                    "crew_commander_secondary_fire_coax762", "crew_commander_secondary_fire_csamm")) {
                helper.assertTrue(override.priorities.getOrDefault(channel, 0) == 1,
                        "超控组必须给 " + channel + " 写越级量 1，否则压不过炮长");
            }
            helper.succeed();
        } finally {
            vehicle.onRemoveFromLevel();
        }
    }

    /** 按控制组名取子组，缺省返回 null。 */
    private static ControlGroup group(ControlGroupSet groups, String name) {
        for (ControlGroup group : groups.groups) {
            if (group.name.equals(name)) {
                return group;
            }
        }
        return null;
    }

    /**
     * 该组发出的瞄准与开火频道是否命中这台控制器的输入列表。
     *
     * <p>只比频道名，不跑信号：控制组的输出表就是它会发的全部频道，接收者列表就是它认的全部频道，
     * 两者交集为空即"这个组永远驱动不了这台控制器"。空表按不相交处理。</p>
     */
    private static void assertDrives(GameTestHelper helper, String label, ControlGroup group,
                                     WeaponControllerSubsystem controller, boolean expected) {
        Set<String> emitted = new LinkedHashSet<>();
        emitted.addAll(group.viewTargets.keySet());
        emitted.addAll(group.mainWeaponTargets.keySet());
        emitted.addAll(group.secondaryWeaponTargets.keySet());
        emitted.retainAll(controller.getAcceptedChannels());
        if (expected) {
            helper.assertTrue(!emitted.isEmpty(),
                    label + " 必须命中 " + controller.name + " 的输入列表，实际没有一条共同的频道");
        } else {
            helper.assertTrue(emitted.isEmpty(),
                    label + " 不得命中 " + controller.name + "，实际共同频道 " + emitted);
        }
    }

    private static void assertEveryMount(GameTestHelper helper, Map<String, AbstractSubsystem> byName,
                                         List<AbstractSubsystem> launchers) {
        for (Mount mount : MOUNTS) {
            AbstractSubsystem launcher = require(helper, byName, mount.launcher());
            assertReachesOnly(helper, require(helper, byName, mount.controller()),
                    mount.fireChannel(), launcher, launchers);
            // 供弹方向由发射器发起查询：它必须恰好找到本挂载的自动装弹机。
            AbstractSubsystem autoloader = require(helper, byName, mount.autoloader());
            assertReachesOnly(helper, launcher, mount.ammoChannel(), autoloader, launchers);
            // 发现结果要真的登记进本管：查询可达与实际取弹来源是两件事。
            List<AmmoPath> routes = ((IAmmoConsumer) launcher).getRoutes();
            helper.assertTrue(!routes.isEmpty(),
                    launcher.name + " 必须至少有一条路");
            for (AmmoPath route : routes) {
                helper.assertTrue(route.transporter() == autoloader,
                        launcher.name + " 的运输者必须是 " + autoloader.name + "，实际是 "
                                + route.transporter());
            }
        }
    }

    /** 该频道到达的终端必须恰好是本挂载的发射器，同口径的其它挂载不得到达。 */
    private static void assertReachesOnly(GameTestHelper helper, AbstractSubsystem sender, String channel,
                                          AbstractSubsystem expected, List<AbstractSubsystem> launchers) {
        List<ISignalReceiver> terminals = new ArrayList<>();
        sender.resolveTargets(channel).values().forEach(terminals::addAll);
        String where = sender.name + " 的 " + channel;
        helper.assertTrue(terminals.contains(expected), where + " 必须到达 " + expected.name);
        helper.assertTrue(terminals.size() == 1,
                where + " 应恰好到达一个终端，实际到达 " + addresses(terminals));
        for (AbstractSubsystem launcher : launchers) {
            if (launcher == expected) continue;
            helper.assertTrue(!terminals.contains(launcher),
                    where + " 不得到达 " + launcher.name + "（同一频道上的其它挂载）");
        }
    }

    /**
     * 把遥控武器站零件接到炮塔上，返回武器站零件。
     *
     * <p>武器站在模板里是独立零件，挂点是一对对外连接（炮塔侧 Simple、武器站侧 Advanced）。
     * 用例按同样的方式把它接上，装配体里才会出现 CROWS 那几个子系统。</p>
     */
    private static RegularPart attachRws(GameTestHelper helper, VehicleCore vehicle, RegularPart turret) {
        RegularPartType type = PartDefinitions.regular(false)
                .get(ResourceLocation.parse("m1a2_sep:m153_crows"));
        helper.assertTrue(type != null, "M1A2 SEP 遥控武器站的部件定义必须已加载");
        RegularPart rws = PartFactory.createRegular(type, "desert", helper.getLevel());
        AbstractConnector advanced = rws.subParts.get("sub_part.m1a2_sep.rws_azimuth")
                .connectors.get("connector.m1a2_sep.rws_turret");
        AbstractConnector simple = turret.subParts.get("sub_part.machine_max.main")
                .connectors.get("connector.m1a2_sep.turret_rws");
        vehicle.attachConnector(advanced, simple, rws);
        helper.assertTrue(advanced.hasPart(), "遥控武器站必须挂到炮塔的武器站挂点上");
        return rws;
    }

    private static Map<String, AbstractSubsystem> collectSubsystems(RegularPart part) {
        Map<String, AbstractSubsystem> byName = new LinkedHashMap<>();
        part.subParts.values().forEach(subPart -> subPart.ownSubsystems().forEach(subsystem -> byName.put(subsystem.getName(), subsystem)));
        return byName;
    }

    private static AbstractSubsystem require(GameTestHelper helper,
                                             Map<String, AbstractSubsystem> byName, String name) {
        AbstractSubsystem subsystem = byName.get(name);
        helper.assertTrue(subsystem != null, "子系统必须存在：" + name);
        return subsystem;
    }

    private static List<String> addresses(List<ISignalReceiver> receivers) {
        return receivers.stream().map(ISignalReceiver::getSignalAddress).toList();
    }
}
