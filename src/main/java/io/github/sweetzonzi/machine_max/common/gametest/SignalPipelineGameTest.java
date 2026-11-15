package io.github.sweetzonzi.machine_max.common.gametest;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.control.ControlGroup;
import io.github.sweetzonzi.machine_max.common.mech.control.ControlGroupSet;
import io.github.sweetzonzi.machine_max.common.mech.control.ControlMode;
import io.github.sweetzonzi.machine_max.common.mech.control.aim.AimTargetState;
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions;
import io.github.sweetzonzi.machine_max.common.mech.part.PartFactory;
import io.github.sweetzonzi.machine_max.common.mech.signal.EmptySignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalReceiver;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalSender;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalChannel;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalResult;
import io.github.sweetzonzi.machine_max.common.mech.signal.ViewInputSignal;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.TrackControllerSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.TrackDriveSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.WeaponControllerSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.event.VehicleStructureChangeEvent;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 结构变化重建首跳表与控制组编辑的接线语义，以及反馈「物理发布、主线程投递」的线程边界。
 */
@GameTestHolder(MachineMax.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SignalPipelineGameTest {

    @GameTest(template = "part_foundation", batch = "mmViewpoints", timeoutTicks = 100)
    public static void controlGroupEditRebuildsTargetsAndUnionsSharedChannels(GameTestHelper helper) {
        var type = PartDefinitions.regular(false).get(ResourceLocation.parse("t90a:t90a_turret"));
        RegularPart part = PartFactory.createRegular(type, type.variants.keySet().iterator().next(), helper.getLevel());
        VehicleCore vehicle = new VehicleCore(helper.getLevel(), part);
        try {
            var turret = part.subParts.get("sub_part.t90a.turret");
            var seat = (SeatSubsystem) turret.findOwnSubsystem("subsystem.t90a.gunner_seat");
            var weapon = (WeaponControllerSubsystem) turret.findOwnSubsystem("subsystem.t90a.main_weapon");
            helper.assertTrue(seat.getTargets().containsKey("crew_gunner_aim"), "预设控制组的频道先进入首跳表");

            Map<String, List<String>> move = Map.of("probe_shared", List.of("subsystem.t90a.azimuth_driver"));
            Map<String, List<String>> weapons = Map.of(
                    "probe_shared", List.of("subsystem.t90a.main_weapon"),
                    "probe_fire", List.of("subsystem.t90a.main_weapon"));
            ControlGroup base = new ControlGroup("probe_base", ControlMode.INHERIT,
                    move, Map.of(), Map.of(), weapons, Map.of(), List.of());
            seat.setControlGroupSet(new ControlGroupSet(base, List.of(), List.of(), -1));

            helper.assertTrue(seat.getTargets().get("probe_shared").keySet().equals(new LinkedHashSet<>(List.of(
                            "subsystem.t90a.azimuth_driver", "subsystem.t90a.main_weapon"))),
                    "同一频道跨类别取有序并集，先移动后武器");
            helper.assertTrue(!seat.getTargets().containsKey("crew_gunner_aim"), "配置删去的频道退出首跳表");
            helper.assertTrue(seat.getWeaponControllersByGroup().getOrDefault("base", List.of()).equals(List.of(weapon)),
                    "按组发现命中主火控，且同一实例只出现一次");
            helper.succeed();
        } finally {
            vehicle.onRemoveFromLevel();
        }
    }

    @GameTest(template = "part_foundation", batch = "mmViewpoints", timeoutTicks = 100)
    public static void controlGroupDeliversOnlyItsOwnTargets(GameTestHelper helper) {
        var type = PartDefinitions.regular(false).get(ResourceLocation.parse("t90a:t90a_turret"));
        RegularPart part = PartFactory.createRegular(type, type.variants.keySet().iterator().next(), helper.getLevel());
        VehicleCore vehicle = new VehicleCore(helper.getLevel(), part);
        try {
            var turret = part.subParts.get("sub_part.t90a.turret");
            var seat = (SeatSubsystem) turret.findOwnSubsystem("subsystem.t90a.gunner_seat");
            var weapon = (WeaponControllerSubsystem) turret.findOwnSubsystem("subsystem.t90a.main_weapon");
            var azimuth = turret.findOwnSubsystem("subsystem.t90a.azimuth_driver");
            String aim = "probe_shared_aim";
            String fire = "probe_shared_fire";

            // 第一段：同一开火频道在两组里指向不同接收者，发现按组各自成立。
            ControlGroup fireBase = new ControlGroup("probe_fire_base", ControlMode.INHERIT, Map.of(), Map.of(),
                    Map.of(), Map.of(fire, List.of("subsystem.t90a.main_weapon")), Map.of(), List.of());
            ControlGroup fireSub = new ControlGroup("probe_fire_sub", ControlMode.INHERIT, Map.of(), Map.of(),
                    Map.of(), Map.of(fire, List.of("subsystem.t90a.azimuth_driver")), Map.of(), List.of());
            seat.setControlGroupSet(new ControlGroupSet(fireBase, List.of(fireSub), List.of(), -1));
            helper.assertTrue(seat.getWeaponControllersByGroup().getOrDefault("base", List.of()).equals(List.of(weapon)),
                    "基础组的开火频道指向主火控，发现必须命中它");
            helper.assertTrue(seat.getWeaponControllersByGroup().getOrDefault("probe_fire_sub", List.of()).isEmpty(),
                    "子组的同名频道只指向方向机，不能靠并集再发现主火控");

            // 第二段：同一视角频道在两组里指向不同接收者，投递按组各自成立。
            ControlGroup aimBase = new ControlGroup("probe_aim_base", ControlMode.INHERIT, Map.of(),
                    Map.of(aim, List.of("subsystem.t90a.azimuth_driver")), Map.of(), Map.of(), Map.of(), List.of());
            ControlGroup aimSub = new ControlGroup("probe_aim_sub", ControlMode.INHERIT, Map.of(),
                    Map.of(aim, List.of("subsystem.t90a.main_weapon")), Map.of(), Map.of(), Map.of(), List.of());

            seat.setControlGroupSet(new ControlGroupSet(aimBase, List.of(aimSub), List.of(), -1));
            helper.assertTrue(seat.setViewInputSignal(
                            new ViewInputSignal(new Vec3(1, 2, 3), Vec2.ZERO, AimTargetState.Mode.WORLD), -1, ""),
                    "座椅必须处于可工作状态");
            helper.assertTrue(delivered(azimuth, aim, seat) instanceof ViewInputSignal,
                    "基础组声明的接收者拿到视角目标");
            helper.assertTrue(delivered(weapon, aim, seat) == null,
                    "同一频道在基础组没有指向主火控，不得投递");

            seat.setControlGroupSet(new ControlGroupSet(aimBase, List.of(aimSub), List.of(), 0));
            helper.assertTrue(seat.setViewInputSignal(
                            new ViewInputSignal(new Vec3(4, 5, 6), Vec2.ZERO, AimTargetState.Mode.WORLD), -1, ""),
                    "切到子组后仍可发信");
            helper.assertTrue(delivered(weapon, aim, seat) instanceof ViewInputSignal,
                    "子组声明的接收者拿到视角目标");
            helper.assertTrue(delivered(azimuth, aim, seat) == EmptySignal.INSTANCE,
                    "切组清空把基础组的接收者置空，切换后不再收到");
            helper.succeed();
        } finally {
            vehicle.onRemoveFromLevel();
        }
    }

    /** 读某接收者在指定频道上由该发送者写入的值；频道或槽位不存在时为 {@code null}。 */
    @Nullable
    private static Object delivered(ISignalReceiver receiver, String channel, ISignalSender sender) {
        SignalChannel slot = receiver.getSignalInputChannels().get(channel);
        return slot == null ? null : slot.get(sender);
    }

    @GameTest(template = "part_foundation", batch = "mmViewpoints", timeoutTicks = 100)
    public static void feedbackIsPublishedInPhysicsAndDeliveredOnMainTick(GameTestHelper helper) {
        var type = PartDefinitions.regular(false).get(ResourceLocation.parse("t90a:t90a_hull"));
        RegularPart part = PartFactory.createRegular(type, type.variants.keySet().iterator().next(), helper.getLevel());
        VehicleCore vehicle = new VehicleCore(helper.getLevel(), part);
        try {
            TrackDriveSubsystem track = part.getAllSubsystems().stream()
                    .filter(TrackDriveSubsystem.class::isInstance)
                    .map(TrackDriveSubsystem.class::cast)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("T-90A 车体没有履带驱动"));
            String channel = track.attr.speedOutputs.keySet().iterator().next();
            ProbeReceiver probe = new ProbeReceiver("probe_feedback", Set.of(channel));
            vehicle.subSystemController.subscribe(probe);

            track.onPostPhysicsTick();
            helper.assertTrue(probe.getSignalInputChannels().get(channel) == null,
                    "物理步只发布采样，不能直接写入接收者");
            track.onTick();
            SignalChannel delivered = probe.getSignalInputChannels().get(channel);
            helper.assertTrue(delivered != null && delivered.get(track) instanceof Float,
                    "主线程 tick 把发布值经配置线路投递到接收者");
            helper.succeed();
        } finally {
            vehicle.onRemoveFromLevel();
        }
    }

    @GameTest(template = "part_foundation", batch = "mmViewpoints", timeoutTicks = 100)
    public static void structureChangeClearsSignalStorageBeforeDiscovery(GameTestHelper helper) {
        var type = PartDefinitions.regular(false).get(ResourceLocation.parse("t90a:t90a_turret"));
        RegularPart part = PartFactory.createRegular(type, type.variants.keySet().iterator().next(), helper.getLevel());
        VehicleCore vehicle = new VehicleCore(helper.getLevel(), part);
        try {
            var turret = part.subParts.get("sub_part.t90a.turret");
            var gun = part.subParts.get("sub_part.t90a.gun");
            var seat = (SeatSubsystem) turret.findOwnSubsystem("subsystem.t90a.gunner_seat");
            var sight = gun.findOwnSubsystem("subsystem.t90a.gunner_sight");
            var port = part.allConnectors.values().iterator().next().signalPort;

            seat.getSignalInputChannels().computeIfAbsent("probe_input", k -> new SignalChannel()).put(seat, 1f);
            vehicle.subSystemController.getSignalInputChannels()
                    .computeIfAbsent("probe_bus", k -> new SignalChannel()).put(seat, 1f);
            part.getSignalInputChannels().computeIfAbsent("probe_part", k -> new SignalChannel()).put(seat, 1f);
            port.getSignalInputChannels().computeIfAbsent("probe_port", k -> new SignalChannel()).put(seat, 1f);

            vehicle.onStructureChanged(VehicleStructureChangeEvent.Reason.ATTACH);

            helper.assertTrue(seat.getSignalInputChannels().get("probe_input") == null, "子系统输入槽被清空");
            helper.assertTrue(vehicle.subSystemController.getSignalInputChannels().get("probe_bus") == null, "总线临时频道被清空");
            helper.assertTrue(part.getSignalInputChannels().get("probe_part") == null, "Part 临时存储被清空");
            helper.assertTrue(port.getSignalInputChannels().get("probe_port") == null, "端口缓冲被清空");
            helper.assertTrue(seat.getDiscoveredCameras().equals(List.of(sight)),
                    "清空后仍按当前接线重新发现镜头");
            helper.succeed();
        } finally {
            vehicle.onRemoveFromLevel();
        }
    }

    @GameTest(template = "part_foundation", batch = "mmViewpoints", timeoutTicks = 100)
    public static void groundControllerBindsActuatorsFromWiring(GameTestHelper helper) {
        var type = PartDefinitions.regular(false).get(ResourceLocation.parse("t90a:t90a_hull"));
        RegularPart part = PartFactory.createRegular(type, type.variants.keySet().iterator().next(), helper.getLevel());
        VehicleCore vehicle = new VehicleCore(helper.getLevel(), part);
        try {
            TrackControllerSubsystem controller = part.getAllSubsystems().stream()
                    .filter(TrackControllerSubsystem.class::isInstance)
                    .map(TrackControllerSubsystem.class::cast)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("T-90A 车体没有履带控制器"));
            helper.assertTrue(controller.getEngines().size() == 1, "按 control_outputs 发现发动机");
            helper.assertTrue(controller.getGearboxes().size() == 1, "发现变速箱");
            helper.assertTrue(controller.getTracks().size() == 2, "发现左右两条履带");
            helper.assertTrue(controller.getTracks().values().stream().allMatch("car_control"::equals),
                    "绑定记录到达频道");

            vehicle.onStructureChanged(VehicleStructureChangeEvent.Reason.ATTACH);
            helper.assertTrue(controller.getEngines().size() == 1
                            && controller.getGearboxes().size() == 1
                            && controller.getTracks().size() == 2,
                    "结构变化清空后重新发现，绑定数量精确一致");
            helper.succeed();
        } finally {
            vehicle.onRemoveFromLevel();
        }
    }

    /** 只接受指定频道、记录总线路由结果的探针接收者。 */
    private static final class ProbeReceiver implements ISignalReceiver {
        private final String address;
        private final Set<String> accepted;
        private final ConcurrentMap<String, SignalChannel> channels = new ConcurrentHashMap<>();

        private ProbeReceiver(String address, Set<String> accepted) {
            this.address = address;
            this.accepted = accepted;
        }

        @Override
        public String getSignalAddress() {
            return address;
        }

        @Override
        public ConcurrentMap<String, SignalChannel> getSignalInputChannels() {
            return channels;
        }

        @Override
        public List<String> getAcceptedChannels() {
            return List.copyOf(accepted);
        }

        @Override
        public SignalResult onSignalUpdated(String channelName, ISignalSender sender) {
            return SignalResult.PASS;
        }
    }
}
