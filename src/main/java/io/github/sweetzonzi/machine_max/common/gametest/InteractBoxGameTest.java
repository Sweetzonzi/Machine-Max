package io.github.sweetzonzi.machine_max.common.gametest;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions;
import io.github.sweetzonzi.machine_max.common.mech.part.PartFactory;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalReceiver;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalPort;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPartType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.event.VehicleStructureChangeEvent;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.InteractBox;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

/**
 * 交互区的目标解析与激活判据。
 *
 * <p>交互区在结构变化与载具入世时把 {@code signal_targets} 解析成终端子系统表（{@link InteractBox#getInteractTargets()}），
 * 点击时按这张表的顺序逐个尝试；激活要求「门控成立 ∧ 有动画事件目标（local / global）或至少一个终端可交互」。
 * 这组用例覆盖座椅目标解析、声明顺序与接线缺失时的门控：</p>
 *
 * <ul>
 *   <li>K17 车体的后舱盖一扇门通往车长位与炮手位，终端顺序必须与内容里写的顺序一致，
 *       且一个座位失效不让整扇门失效——全部不可交互时才是空操作。</li>
 *   <li>单独摆在地上的车门（{@code van_door}）解析不到任何座位：终端表为空，交互区因此完全不激活
 *       ——提示不亮、射线也选不中、点击是空操作。</li>
 *   <li>挂在车壳上的门框要沿连接点跨零件解析到座位（AE86）。接线发生在载具入世之前，
 *       那一次补建不能漏：漏掉时跨零件的交互区停在空表上，同零件的交互区却看不出问题。</li>
 * </ul>
 */
@GameTestHolder(MachineMax.MOD_ID)
@PrefixGameTestTemplate(false)
public final class InteractBoxGameTest {
    private static final String BATCH = "mmInteractBox";
    private static final String HATCH = "interact.k17.back_hatch";
    private static final String COMMANDER = "subsystem.machine_max.commander_seat";
    private static final String GUNNER = "subsystem.machine_max.gunner_seat";

    private InteractBoxGameTest() {
    }

    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void hatchKeepsDeclarationOrderAndSurvivesOneInactiveSeat(GameTestHelper helper) {
        RegularPart part = createPart(helper, "k17:k17_hull");
        VehicleCore vehicle = new VehicleCore(helper.getLevel(), part);
        try {
            vehicle.onStructureChanged(VehicleStructureChangeEvent.Reason.ATTACH);
            InteractBox box = findBox(helper, part, HATCH);
            assertOrderAndFallthrough(helper, box);

            // 结构变化后终端表整体重建，顺序与可用性判定必须与初次装车一致。
            vehicle.onStructureChanged(VehicleStructureChangeEvent.Reason.ATTACH);
            assertOrderAndFallthrough(helper, box);
            helper.succeed();
        } finally {
            vehicle.onRemoveFromLevel();
        }
    }

    /**
     * 单独摆放的车门解析不到座位：它的目标是一侧连接点，没接上车体时那条线没有终端。
     * 终端表为空时交互区不激活，点击是空操作。
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void detachedDoorHasNoTargetsAndStaysInactive(GameTestHelper helper) {
        RegularPartType type = PartDefinitions.regular(false)
                .get(ResourceLocation.parse("machine_max:van_door"));
        helper.assertTrue(type != null, "面包车车门的部件定义必须已加载");
        RegularPart part = PartFactory.createRegular(type, "left_door", helper.getLevel());
        VehicleCore vehicle = new VehicleCore(helper.getLevel(), part);
        try {
            vehicle.onStructureChanged(VehicleStructureChangeEvent.Reason.ATTACH);
            InteractBox box = findBox(helper, part, "interact.machine_max.left_front_door");
            helper.assertTrue(box.getInteractTargets().isEmpty(),
                    "未接上车体的车门不应解析出交互目标，实际 " + names(box.getInteractTargets()));
            helper.assertFalse(box.canInteract(), "没有接收者的交互区必须完全不激活");
            box.interact(null);// 空操作：既不能抛异常，也不能落到非子系统的目标上
            helper.succeed();
        } finally {
            vehicle.onRemoveFromLevel();
        }
    }

    /**
     * 跨零件的门：车壳上的门框经连接点解析到另一个零件里的座位。
     *
     * <p>夹具走的是模板载具的真实装配顺序——先建车架、再接线、最后入世：接线发生在入世之前，
     * 那一步会跳过 {@code onStructureChanged}，所以终端表只能靠 {@code onAddToLevel} 补建。
     * 这条用例同时钉住这个顺序，漏掉补建的实现会让跨零件的交互区永久停在空表上。</p>
     *
     * <p>断言把链路摊成四段，因为跨零件的交互区失效时玩家只看到"点不动"，而原因可能是首跳表、
     * 连接点配对、对侧到达频道或接收者的接受列表中的任何一段。</p>
     *
     * <p>夹具取 AE86：车门框长在车壳上，座位是独立零件，所以链路跨三个零件——车壳连接点 →
     * 车架 {@code hull} 的到达频道表 → 车架的座位连接点 → 座位零件的频道转译表。可拆的车门零件
     * （如 {@code van_door}）属于同一类必须跨零件的交互区，换夹具时用它同样成立。</p>
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void hullDoorReachesSeatInOtherPart(GameTestHelper helper) {
        RegularPart chassis = createPart(helper, "machine_max:ae86_chassis");
        RegularPart hull = createPart(helper, "machine_max:ae86_hull");
        RegularPart seatPart = createPart(helper, "machine_max:ae86_seat");
        VehicleCore vehicle = new VehicleCore(helper.getLevel(), chassis);
        try {
            vehicle.attachConnector(
                    connector(helper, chassis, "connector.machine_max.hull"),
                    connector(helper, hull, "connector.machine_max.chassis_connection"),
                    hull);
            vehicle.attachConnector(
                    connector(helper, chassis, "connector.machine_max.copilot_seat"),
                    connector(helper, seatPart, "connector.machine_max.chassis_connection"),
                    seatPart);
            vehicle.onAddToLevel();

            InteractBox box = findBox(helper, hull, "interact.machine_max.left_front_door");
            Map<String, ISignalReceiver> firstHop = box.getTargets().get("interact_left_front_door");
            helper.assertTrue(firstHop != null && firstHop.size() == 1,
                    "首跳表应只有车壳自己的连接点，实际 " + firstHop);
            ISignalReceiver portReceiver = firstHop.values().iterator().next();
            helper.assertTrue(portReceiver instanceof SignalPort,
                    "首跳应是信号端口，实际 " + portReceiver.getClass().getSimpleName());
            SignalPort hullPort = (SignalPort) portReceiver;
            helper.assertTrue(hullPort.getAttachedPort() != null,
                    "车壳的 " + hullPort.name + " 必须与车架的 hull 对上");

            SignalPort chassisPort = hullPort.getAttachedPort();
            Map<String, ISignalReceiver> arrival = chassisPort.getTargets().get("interact_left_front_door");
            helper.assertTrue(arrival != null && arrival.size() == 1,
                    "车架连接点应把到达频道 interact_left_front_door 送给座位连接点，实际 "
                            + chassisPort.getTargets().keySet());
            ISignalReceiver seatPortReceiver = arrival.values().iterator().next();
            helper.assertTrue(seatPortReceiver instanceof SignalPort,
                    "到达频道的目标应是座位零件上的连接点，实际 " + seatPortReceiver.getClass().getSimpleName());
            SignalPort seatPort = ((SignalPort) seatPortReceiver).getAttachedPort();
            helper.assertTrue(seatPort != null, "座位零件必须已接上，否则链条在最后一段断开");
            helper.assertTrue("interact".equals(seatPort.translateChannel("interact_left_front_door")),
                    "座位零件应把车门频道转译成它自己接受的 interact，实际 " + seatPort.getSignalTranslation());

            helper.assertTrue(box.getInteractTargets().size() == 1,
                    "跨零件的左前门应解析出一个座位，实际 " + names(box.getInteractTargets()));
            helper.assertTrue(box.getInteractTargets().get(0) instanceof SeatSubsystem,
                    "解析出的终端应是座位子系统，实际 " + names(box.getInteractTargets()));
            helper.assertTrue(box.canInteract(), "空车的左前门应可用");
            helper.succeed();
        } finally {
            vehicle.onRemoveFromLevel();
        }
    }

    private static void assertOrderAndFallthrough(GameTestHelper helper, InteractBox box) {
        List<AbstractSubsystem> targets = box.getInteractTargets();
        helper.assertTrue(targets.size() == 2,
                HATCH + " 应解析出两个座位，实际 " + names(targets));
        helper.assertTrue(COMMANDER.equals(targets.get(0).getName()) && GUNNER.equals(targets.get(1).getName()),
                HATCH + " 的尝试顺序应与 signal_targets 的声明序一致，实际 " + names(targets));
        helper.assertTrue(box.canInteract(), "空车的后舱盖应可用");

        SeatSubsystem commander = seat(helper, targets.get(0), COMMANDER);
        SeatSubsystem gunner = seat(helper, targets.get(1), GUNNER);
        boolean commanderActive = commander.isActive();
        boolean gunnerActive = gunner.isActive();
        try {
            commander.setActive(false);
            helper.assertTrue(box.canInteract(), "车长位失效后炮手位仍应让后舱盖可用");
            gunner.setActive(false);
            helper.assertFalse(box.canInteract(), "两个座位都不可交互时后舱盖必须不激活");
        } finally {
            commander.setActive(commanderActive);
            gunner.setActive(gunnerActive);
        }
    }

    private static RegularPart createPart(GameTestHelper helper, String id) {
        RegularPartType type = PartDefinitions.regular(false).get(ResourceLocation.parse(id));
        helper.assertTrue(type != null, id + " 的部件定义必须已加载");
        String variant = type.getVariants().keySet().iterator().next();
        return PartFactory.createRegular(type, variant, helper.getLevel());
    }

    private static InteractBox findBox(GameTestHelper helper, RegularPart part, String name) {
        InteractBox found = part.findInteractBox(name);
        helper.assertTrue(found != null, part.name + " 里必须有交互区 " + name);
        return found;
    }

    private static AbstractConnector connector(GameTestHelper helper, RegularPart part, String name) {
        AbstractConnector found = part.findConnector(name);
        helper.assertTrue(found != null, part.name + " 里必须有连接点 " + name);
        return found;
    }

    private static SeatSubsystem seat(GameTestHelper helper, AbstractSubsystem subsystem, String name) {
        helper.assertTrue(subsystem instanceof SeatSubsystem, name + " 必须是座位子系统");
        return (SeatSubsystem) subsystem;
    }

    private static List<String> names(List<AbstractSubsystem> subsystems) {
        return subsystems.stream().map(AbstractSubsystem::getName).toList();
    }
}
