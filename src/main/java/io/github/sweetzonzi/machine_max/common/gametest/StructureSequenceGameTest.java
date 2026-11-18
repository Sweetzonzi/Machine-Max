package io.github.sweetzonzi.machine_max.common.gametest;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions;
import io.github.sweetzonzi.machine_max.common.mech.part.PartFactory;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 线上结构序号契约：子系统与连接点的序号是「声明序位置」，反查是同一个对象的逆映射，
 * 越界一律返回 {@code null}（调用方据此整包丢弃）；序号的作用域是单个子零件，不看部件整体。
 * 交互区不序号化：活表 {@code InteractBoxes} 是 {@code ConcurrentHashMap}，其载荷保持交互区名。
 */
@GameTestHolder(MachineMax.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StructureSequenceGameTest {

    /** 夹具部件：T-90A 炮塔含 5 个子零件、23 个子系统与 11 个连接点。 */
    private static RegularPart turret(GameTestHelper helper) {
        var type = PartDefinitions.regular(false).get(ResourceLocation.parse("t90a:t90a_turret"));
        return PartFactory.createRegular(type, type.variants.keySet().iterator().next(), helper.getLevel());
    }

    @GameTest(template = "part_foundation", batch = "mmSequence", timeoutTicks = 100)
    public static void sequenceAddressesRoundTripWithinEachSubPart(GameTestHelper helper) {
        RegularPart part = turret(helper);
        try {
            int subParts = 0;
            int subsystems = 0;
            int connectors = 0;
            for (SubPart subPart : part.subParts.values()) {
                subParts++;

                int subsystemIndex = 0;
                for (AbstractSubsystem subsystem : subPart.ownSubsystems()) {
                    helper.assertTrue(subPart.subsystemIndex(subsystem) == subsystemIndex,
                            "子系统序号取声明序位置：" + subPart.name + " / " + subsystem.getName());
                    helper.assertTrue(subPart.subsystemAt(subsystemIndex) == subsystem,
                            "序号反查回到同一个子系统：" + subPart.name + " / " + subsystemIndex);
                    subsystemIndex++;
                }
                helper.assertTrue(subPart.subsystemAt(-1) == null, "负序号不解析：" + subPart.name);
                helper.assertTrue(subPart.subsystemAt(subsystemIndex) == null,
                        "越界序号不解析：" + subPart.name + " / " + subsystemIndex);
                subsystems += subsystemIndex;

                int connectorIndex = 0;
                for (AbstractConnector connector : subPart.connectors.values()) {
                    helper.assertTrue(subPart.connectorIndex(connector) == connectorIndex,
                            "连接点序号取声明序位置：" + subPart.name + " / " + connector.getName());
                    helper.assertTrue(subPart.connectorAt(connectorIndex) == connector,
                            "序号反查回到同一个连接点：" + subPart.name + " / " + connectorIndex);
                    connectorIndex++;
                }
                helper.assertTrue(subPart.connectorAt(connectorIndex) == null,
                        "越界连接点序号不解析：" + subPart.name + " / " + connectorIndex);
                connectors += connectorIndex;
            }
            helper.assertTrue(subParts > 1, "夹具部件应有多个子零件");
            helper.assertTrue(subsystems > 0 && connectors > 0,
                    "夹具部件两类序号都应有非空样本");
            helper.succeed();
        } finally {
            part.discardUnattached();
        }
    }

    @GameTest(template = "part_foundation", batch = "mmSequence", timeoutTicks = 100)
    public static void sequenceIsScopedToSubPartNotPart(GameTestHelper helper) {
        RegularPart part = turret(helper);
        try {
            SubPart first = part.subParts.get("sub_part.machine_max.main");
            SubPart second = part.subParts.get("sub_part.t90a.gun");
            helper.assertTrue(first != null && second != null, "夹具部件的两个子零件都应存在");
            AbstractSubsystem firstOfFirst = first.subsystemAt(0);
            AbstractSubsystem firstOfSecond = second.subsystemAt(0);
            helper.assertTrue(firstOfFirst != null && firstOfSecond != null,
                    "两个子零件都应有子系统");
            helper.assertTrue(firstOfFirst != firstOfSecond,
                    "同一个序号在两个子零件上指向不同对象：地址是 (子零件, 序号) 二元组");
            helper.assertTrue(first.subsystemAt(0) == firstOfFirst,
                    "按第一个子零件解析不会跨到第二个子零件");
            helper.succeed();
        } finally {
            part.discardUnattached();
        }
    }
}
