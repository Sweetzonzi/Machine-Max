package io.github.sweetzonzi.machine_max.common.gametest;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions;
import io.github.sweetzonzi.machine_max.common.mech.part.PartFactory;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.CouplerSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPartType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AdvancedConnector;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.SimpleConnector;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.InteractBox;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 锁扣的扣合状态输出，以及按它门控的快速上座区。
 *
 * <p>夹具用 Senna：锁扣在车尾车体上、快速上座区在底盘上，两者跨 {@code Part}，连接点名与子系统名
 * 都跨不过去，只能靠 {@code state_outputs} 发布的频道连起来。</p>
 *
 * <p>本类自成一个 batch：这里只验状态发布与 MoLang 读法，不碰关节与物理采样，
 * 与 {@link CouplerGameTest} 那些依赖物理步时序的用例分开跑。</p>
 */
@GameTestHolder(MachineMax.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CouplerStateOutputGameTest {

    private static final String BATCH = "mmCouplerState";
    private static final String HULL = "machine_max:senna_gtr_hull";
    private static final String DOOR = "machine_max:senna_gtr_door";
    private static final String CHASSIS = "machine_max:senna_gtr_chassis";
    private static final String LOCK = "connector.machine_max.left_door_lock";
    private static final String DOOR_LOCK = "connector.machine_max.lock";
    private static final String COUPLER = "subsystem.machine_max.left_front_door_coupler";
    private static final String LATCH_CHANNEL = "senna_left_door_latched";
    private static final String FAST_BOX = "interact.machine_max.board_left_seat";

    private CouplerStateOutputGameTest() {
    }

    /**
     * 声明的状态输出：Senna 车尾车体的左前门锁扣把扣合状态发到装配体信号存储。
     *
     * <p>值是数字 {@code 1}／{@code 0}：{@code global.get} 与 {@code local.get} 只认 {@code Number}，
     * 发布尔会被静默读成 0，门控条件于是恒成立。</p>
     *
     * <p>状态输出排在状态机之前，一次 tick 内完成的扣合变化由下一个 tick 发出去，
     * 所以每处变化之后要再跑一个 tick 才读到新值。</p>
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void stateOutputsPublishLatchBit(GameTestHelper helper) {
        RegularPart hull = createPart(helper, HULL, "back");
        RegularPart door = createPart(helper, DOOR, "left");
        VehicleCore vehicle = new VehicleCore(helper.getLevel(), hull);
        try {
            AbstractSubsystem found = hull.findSubsystem(COUPLER);
            helper.assertTrue(found instanceof CouplerSubsystem, "车尾车体必须安装锁扣 " + COUPLER);
            CouplerSubsystem coupler = (CouplerSubsystem) found;
            helper.assertTrue(coupler.connector != null, "锁扣必须解析到主动连接点");
            AdvancedConnector lock = (AdvancedConnector) connector(helper, hull, LOCK);
            SimpleConnector passive = (SimpleConnector) connector(helper, door, DOOR_LOCK);
            helper.assertTrue(vehicle.attachConnector(lock, passive, door), "锁扣边应能建立");
            // 夹具不走装配期接线，首跳表要自己建，否则状态输出没有目标可发。
            coupler.setTargetFromNames();

            coupler.onTick();
            helper.assertTrue(coupler.isConnected(), "双端互指时对账应补正标记");
            helper.assertTrue(Double.valueOf(0.0).equals(latchValue(vehicle)),
                    "首个 tick 发布的是对账之前的值，实际 " + latchValue(vehicle));

            coupler.onTick();
            helper.assertTrue(Double.valueOf(1.0).equals(latchValue(vehicle)),
                    "扣合后状态输出应为数字 1，实际 " + latchValue(vehicle));

            vehicle.detachConnections(
                    java.util.List.of(com.mojang.datafixers.util.Pair.of((AbstractConnector) lock, passive)),
                    java.util.Map.of());
            coupler.onTick();
            helper.assertFalse(coupler.isConnected(), "解扣后对账应清除标记");
            helper.assertTrue(Double.valueOf(1.0).equals(latchValue(vehicle)),
                    "解扣的首个 tick 发布的仍是扣合值，实际 " + latchValue(vehicle));

            coupler.onTick();
            helper.assertTrue(Double.valueOf(0.0).equals(latchValue(vehicle)),
                    "解扣后状态输出应为数字 0，实际 " + latchValue(vehicle));
            helper.succeed();
        } finally {
            vehicle.onRemoveFromLevel();
        }
    }

    /**
     * 快速上座区随发布的扣合位开关：频道里没有值或为 {@code 0} 时交互区可用，为 {@code 1} 时关闭。
     *
     * <p>夹具只要 Senna 底盘——快速上座区与它的门控条件都在这一件里。锁扣在车尾车体上，
     * 所以这里直接把锁扣会写的那个值放进装配体信号存储，验证条件本身随值翻转。</p>
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void fastSeatZoneFollowsPublishedLatchBit(GameTestHelper helper) {
        RegularPart chassis = createPart(helper, CHASSIS, null);
        VehicleCore vehicle = new VehicleCore(helper.getLevel(), chassis);
        try {
            InteractBox box = chassis.findInteractBox(FAST_BOX);
            helper.assertTrue(box != null, "Senna 底盘必须有 " + FAST_BOX);
            box.setTargetFromNames();
            box.updateCondition();
            helper.assertTrue(box.canInteract(), "频道没有值（未扣合）时快速上座区应可用");

            vehicle.getSubsystemController().getSignalStorage().put(LATCH_CHANNEL, 1f);
            box.updateCondition();
            helper.assertFalse(box.canInteract(), "扣合位为 1 时快速上座区应关闭");
            helper.succeed();
        } finally {
            vehicle.onRemoveFromLevel();
        }
    }

    /**
     * 装配体信号存储里的扣合状态，按 {@code Number} 读。
     *
     * <p>状态输出写成布尔时 {@code global.get} 会静默读到 0，这里同样读不到数字——返回
     * {@code null}，与"频道还没发布过"同档。</p>
     */
    private static Double latchValue(VehicleCore vehicle) {
        Object value = vehicle.getSubsystemController().getSignalStorage().get(LATCH_CHANNEL);
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private static RegularPart createPart(GameTestHelper helper, String id, String variant) {
        RegularPartType type = PartDefinitions.regular(false).get(ResourceLocation.parse(id));
        helper.assertTrue(type != null, id + " 的部件定义必须已加载");
        String resolved = variant != null ? variant : type.getVariants().keySet().iterator().next();
        return PartFactory.createRegular(type, resolved, helper.getLevel());
    }

    private static AbstractConnector connector(GameTestHelper helper, RegularPart part, String name) {
        AbstractConnector found = part.findConnector(name);
        helper.assertTrue(found != null, part.name + " 里必须有连接点 " + name);
        return found;
    }
}
