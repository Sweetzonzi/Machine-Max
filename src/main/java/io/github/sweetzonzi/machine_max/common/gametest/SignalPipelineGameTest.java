package io.github.sweetzonzi.machine_max.common.gametest;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.control.ControlGroup;
import io.github.sweetzonzi.machine_max.common.mech.control.ControlGroupSet;
import io.github.sweetzonzi.machine_max.common.mech.control.ControlMode;
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions;
import io.github.sweetzonzi.machine_max.common.mech.part.PartFactory;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalReceiver;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalSender;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalChannel;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalResult;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.TrackDriveSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.WeaponControllerSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.event.VehicleStructureChangeEvent;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

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
            var seat = (SeatSubsystem) turret.subsystems.get("subsystem.t90a.gunner_seat");
            var weapon = (WeaponControllerSubsystem) turret.subsystems.get("subsystem.t90a.main_weapon");
            helper.assertTrue(seat.getTargets().containsKey("aim_input_p2"), "预设控制组的频道先进入首跳表");

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
            helper.assertTrue(!seat.getTargets().containsKey("aim_input_p2"), "配置删去的频道退出首跳表");
            helper.assertTrue(seat.getWeaponControllersByGroup().getOrDefault("base", List.of()).equals(List.of(weapon)),
                    "按组发现命中主火控，且同一实例只出现一次");
            helper.succeed();
        } finally {
            vehicle.onRemoveFromLevel();
        }
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
            var seat = (SeatSubsystem) turret.subsystems.get("subsystem.t90a.gunner_seat");
            var sight = gun.subsystems.get("subsystem.t90a.gunner_sight");
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
