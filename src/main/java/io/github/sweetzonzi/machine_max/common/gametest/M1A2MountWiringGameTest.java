package io.github.sweetzonzi.machine_max.common.gametest;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions;
import io.github.sweetzonzi.machine_max.common.mech.part.PartFactory;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalReceiver;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.IAmmoConsumer;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.IAmmoSupplier;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.LauncherSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPartType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
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

/**
 * M1A2 SEP 炮塔上每个机枪挂载的控制器与自动装弹机只连自己那根管。
 *
 * <p>炮塔上并列、车顶、舱门与后部五个挂载里有三根 M2HB、两根 M240，共用同一对开火频道
 * （{@code weapon.mg.m2hb} / {@code weapon.mg.m240}），弹药也按口径共用
 * （{@code ammo_12_7mm} / {@code ammo_7_62mm}）。频道本身区分不了是哪一根枪，
 * 因此区分只能落在接线上了：控制器与装弹机在偏航子部件，发射器在俯仰子部件，
 * 中间那一跳走两者之间的俯仰连接点。</p>
 *
 * <p>用例把整座炮塔装成只有这一个零件的装配体，然后沿实际接线解析终端：开火频道必须恰好到达
 * 本挂载的发射器；供弹频道由发射器自己发起查询，必须恰好到达本挂载的自动装弹机。
 * 装成装配体是必要的——{@code "global"} 指向装配体的信号总线，只有存在装配体时
 * 「广播到同口径全部发射器」才会真的发生，独立零件上这条路径是空的。</p>
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
            new Mount("subsystem.m1a2_sep.coax_controller", "weapon.mg.m2hb",
                    "subsystem.m1a2_sep.coax_launcher",
                    "subsystem.m1a2_sep.coax_autoloader", "ammo_12_7mm"),
            new Mount("subsystem.m1a2_sep.coax_762_controller", "weapon.mg.m240",
                    "subsystem.m1a2_sep.coax_762_launcher",
                    "subsystem.m1a2_sep.coax_762_autoloader", "ammo_7_62mm"),
            new Mount("subsystem.m1a2_sep.rws_controller", "weapon.mg.m2hb",
                    "subsystem.m1a2_sep.rws_launcher",
                    "subsystem.m1a2_sep.rws_autoloader", "ammo_12_7mm"),
            new Mount("subsystem.m1a2_sep.port_mg_controller", "weapon.mg.m240",
                    "subsystem.m1a2_sep.port_mg_launcher",
                    "subsystem.m1a2_sep.port_mg_autoloader", "ammo_7_62mm"),
            new Mount("subsystem.m1a2_sep.aft_mg_controller", "weapon.mg.m2hb",
                    "subsystem.m1a2_sep.aft_mg_launcher",
                    "subsystem.m1a2_sep.aft_mg_autoloader", "ammo_12_7mm"));

    private M1A2MountWiringGameTest() {
    }

    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void everyMountReachesOnlyItsOwnLauncher(GameTestHelper helper) {
        RegularPartType type = PartDefinitions.regular(false)
                .get(ResourceLocation.parse("m1a2_sep:m1a2_turret"));
        helper.assertTrue(type != null, "M1A2 SEP 炮塔的部件定义必须已加载");
        RegularPart part = PartFactory.createRegular(type, "desert", helper.getLevel());
        VehicleCore vehicle = new VehicleCore(helper.getLevel(), part);
        try {
            Map<String, AbstractSubsystem> byName = collectSubsystems(part);
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
            List<IAmmoSupplier> registered = ((IAmmoConsumer) launcher).getSuppliers();
            helper.assertTrue(registered.size() == 1,
                    launcher.name + " 应恰好登记一个供给者，实际 " + registered.size());
            helper.assertTrue(registered.getFirst() == autoloader,
                    launcher.name + " 登记的供给者必须是 " + autoloader.name);
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

    private static Map<String, AbstractSubsystem> collectSubsystems(RegularPart part) {
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

    private static List<String> addresses(List<ISignalReceiver> receivers) {
        return receivers.stream().map(ISignalReceiver::getSignalAddress).toList();
    }
}
