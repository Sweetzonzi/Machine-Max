package io.github.sweetzonzi.machine_max.common.gametest;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions;
import io.github.sweetzonzi.machine_max.common.mech.part.PartFactory;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalReceiver;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalSender;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalChannel;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalPort;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalResult;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.event.VehicleStructureChangeEvent;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Part 信号总线契约：订阅随构造/销毁自动订退，{@code local} 广播与只读发现只命中所属部件，
 * 结构变化只清临时信号，到达处理保留原始发送者，同一来源经 {@code local} 与 {@code global} 共用一个槽。
 */
@GameTestHolder(MachineMax.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PartSignalBusGameTest {

    private static final String CHANNEL = "probe_part_bus";

    /** 夹具部件：T-90A 炮塔包含炮塔与火炮两个 SubPart 的子系统。 */
    private static RegularPart turret(GameTestHelper helper) {
        var type = PartDefinitions.regular(false).get(ResourceLocation.parse("t90a:t90a_turret"));
        return PartFactory.createRegular(type, type.variants.keySet().iterator().next(), helper.getLevel());
    }

    @GameTest(template = "part_foundation", batch = "mmSignal", timeoutTicks = 100)
    public static void constructionSubscribesSubsystemsAndUnsubscribeIsIndependent(GameTestHelper helper) {
        RegularPart part = turret(helper);
        VehicleCore vehicle = new VehicleCore(helper.getLevel(), part);
        try {
            Set<AbstractSubsystem> physical = part.getAllSubsystems();
            helper.assertTrue(!physical.isEmpty(), "夹具部件应带有子系统");
            helper.assertTrue(part.getAllSubscribers().containsAll(physical),
                    "构造完成后全部子系统默认订阅本部件的 local 总线");

            vehicle.onStructureChanged(VehicleStructureChangeEvent.Reason.ATTACH);
            AbstractSubsystem subsystem = physical.iterator().next();
            part.unsubscribe(subsystem);
            helper.assertTrue(!part.getAllSubscribers().contains(subsystem), "退订后不再属于本部件订阅集合");
            helper.assertTrue(vehicle.subSystemController.getAllSubscribers().contains(subsystem),
                    "退订 local 不影响装配体总线订阅");
            helper.assertTrue(part.getAllSubsystems().contains(subsystem), "退订不改变物理归属");

            part.unsubscribe(subsystem);
            helper.assertTrue(!part.getAllSubscribers().contains(subsystem), "重复退订幂等");
            part.subscribe(subsystem);
            part.subscribe(subsystem);
            helper.assertTrue(part.getAllSubscribers().contains(subsystem), "重复订阅幂等且恢复订阅");
            helper.succeed();
        } finally {
            vehicle.onRemoveFromLevel();
        }
    }

    @GameTest(template = "part_foundation", batch = "mmSignal", timeoutTicks = 100)
    public static void localBroadcastReachesOnlyOwnPartSubscribers(GameTestHelper helper) {
        RegularPart first = turret(helper);
        RegularPart second = turret(helper);
        ProbeEndpoint subscriber = new ProbeEndpoint("probe_subscriber", List.of(CHANNEL), null);
        ProbeEndpoint otherPart = new ProbeEndpoint("probe_other_part", List.of(CHANNEL), null);
        ProbeEndpoint sender = new ProbeEndpoint("probe_sender", List.of(), null);
        first.subscribe(subscriber);
        second.subscribe(otherPart);

        sender.sendSignalToTarget(CHANNEL, first, 1f);

        helper.assertTrue(numberOf(first.getSignalStorage().get(CHANNEL)) == 1f,
                "到达 local 后写入部件键值存储");
        SignalChannel channel = subscriber.getSignalInputChannels().get(CHANNEL);
        helper.assertTrue(channel != null && numberOf(channel.get(sender)) == 1f,
                "订阅者按原始发送者分槽收到广播");
        helper.assertTrue(subscriber.lastSender == sender, "回调来源是原始发送者，不是 Part");
        helper.assertTrue(otherPart.getSignalInputChannels().isEmpty(),
                "同一频道的另一个部件订阅者不受影响");
        helper.assertTrue(second.getSignalStorage().isEmpty(), "另一个部件的键值存储不被写");

        sender.getTargets().put(CHANNEL, Map.of("local", first));
        int updates = subscriber.updates;
        List<ISignalReceiver> terminals = sender.resolveTargets(CHANNEL).get(CHANNEL);
        helper.assertTrue(terminals.contains(subscriber), "local 发现覆盖本部件订阅者");
        helper.assertTrue(!terminals.contains(first), "local 不把 Part 本身作为终端");
        helper.assertTrue(!terminals.contains(otherPart), "local 发现不跨到另一个部件");
        helper.assertTrue(subscriber.updates == updates, "只读发现不触发接收者处理");
        first.discardUnattached();
        second.discardUnattached();
        helper.succeed();
    }

    @GameTest(template = "part_foundation", batch = "mmSignal", timeoutTicks = 100)
    public static void crossSubPartSubscribersAreReached(GameTestHelper helper) {
        RegularPart part = turret(helper);
        SubPart turretSubPart = part.subParts.get("sub_part.t90a.turret");
        SubPart gunSubPart = part.subParts.get("sub_part.t90a.gun");
        ProbeEndpoint inTurret = new ProbeEndpoint("probe_turret", List.of(CHANNEL), turretSubPart);
        ProbeEndpoint inGun = new ProbeEndpoint("probe_gun", List.of(CHANNEL), gunSubPart);
        ProbeEndpoint sender = new ProbeEndpoint("probe_sender", List.of(), null);
        part.subscribe(inTurret);
        part.subscribe(inGun);

        sender.sendSignalToTarget(CHANNEL, part, 1f);

        helper.assertTrue(inTurret.getSignalInputChannels().containsKey(CHANNEL)
                && inGun.getSignalInputChannels().containsKey(CHANNEL),
                "同一部件的不同 SubPart 订阅者都收到广播");

        sender.getTargets().put(CHANNEL, Map.of("local", part));
        List<ISignalReceiver> terminals = sender.resolveTargets(CHANNEL).get(CHANNEL);
        helper.assertTrue(terminals.containsAll(List.of(inTurret, inGun)),
                "local 发现覆盖部件全部 SubPart 的订阅者");
        part.discardUnattached();
        helper.succeed();
    }

    @GameTest(template = "part_foundation", batch = "mmSignal", timeoutTicks = 100)
    public static void unsubscribeExcludesFromDiscoveryAndBroadcast(GameTestHelper helper) {
        RegularPart part = turret(helper);
        ProbeEndpoint subscriber = new ProbeEndpoint("probe_subscriber", List.of(CHANNEL), null);
        ProbeEndpoint sender = new ProbeEndpoint("probe_sender", List.of(), null);
        part.subscribe(subscriber);
        part.unsubscribe(subscriber);

        sender.sendSignalToTarget(CHANNEL, part, 1f);

        helper.assertTrue(numberOf(part.getSignalStorage().get(CHANNEL)) == 1f,
                "未订阅不影响部件自身的键值存储更新");
        helper.assertTrue(subscriber.getSignalInputChannels().isEmpty(), "退订后广播不再通知该对象");

        sender.getTargets().put(CHANNEL, Map.of("local", part));
        helper.assertTrue(!sender.resolveTargets(CHANNEL).getOrDefault(CHANNEL, List.of()).contains(subscriber),
                "退订后只读发现也不返回该对象");

        part.subscribe(subscriber);
        sender.sendSignalToTarget(CHANNEL, part, 2f);
        helper.assertTrue(numberOf(subscriber.getSignalInputChannels().get(CHANNEL).get(sender)) == 2f,
                "重新订阅后恢复投递");
        part.discardUnattached();
        helper.succeed();
    }

    @GameTest(template = "part_foundation", batch = "mmSignal", timeoutTicks = 100)
    public static void structureChangeClearsSignalsButKeepsUnsubscribe(GameTestHelper helper) {
        RegularPart part = turret(helper);
        VehicleCore vehicle = new VehicleCore(helper.getLevel(), part);
        try {
            vehicle.onStructureChanged(VehicleStructureChangeEvent.Reason.ATTACH);
            List<AbstractSubsystem> all = new ArrayList<>(part.getAllSubsystems());
            AbstractSubsystem unsubscribed = all.getFirst();
            part.unsubscribe(unsubscribed);
            part.getSignalInputChannels().computeIfAbsent(CHANNEL, key -> new SignalChannel())
                    .put(unsubscribed, 1f);
            part.getSignalStorage().put(CHANNEL, 1f);

            vehicle.onStructureChanged(VehicleStructureChangeEvent.Reason.ATTACH);

            helper.assertTrue(part.getSignalInputChannels().isEmpty(), "结构变化清空 Part 输入频道");
            helper.assertTrue(part.getSignalStorage().isEmpty(), "结构变化清空 Part 键值存储");
            helper.assertTrue(!part.getAllSubscribers().contains(unsubscribed), "显式退订在结构变化后保留");
            helper.assertTrue(part.getAllSubscribers().containsAll(
                            all.stream().filter(subsystem -> subsystem != unsubscribed).toList()),
                    "其余子系统仍在本部件订阅集合中");
            helper.succeed();
        } finally {
            vehicle.onRemoveFromLevel();
        }
    }

    @GameTest(template = "part_foundation", batch = "mmSignal", timeoutTicks = 100)
    public static void destroyAndFailureRecoveryLeaveNoSubscribers(GameTestHelper helper) {
        ProbeEndpoint probe = new ProbeEndpoint("probe_subscriber", List.of(CHANNEL), null);

        RegularPart destroyed = turret(helper);
        destroyed.subscribe(probe);
        destroyed.getSignalStorage().put(CHANNEL, 1f);
        destroyed.destroy();
        helper.assertTrue(destroyed.getAllSubscribers().isEmpty(), "正常销毁释放全部 local 订阅");
        helper.assertTrue(destroyed.getSignalStorage().isEmpty(), "正常销毁清空临时信号");

        RegularPart aborted = turret(helper);
        aborted.subscribe(probe);
        aborted.getSignalInputChannels().computeIfAbsent(CHANNEL, key -> new SignalChannel()).put(probe, 1f);
        aborted.getSignalStorage().put(CHANNEL, 1f);
        aborted.discardUnattached();
        helper.assertTrue(aborted.getAllSubscribers().isEmpty(), "构造失败回收释放全部 local 订阅");
        helper.assertTrue(aborted.getSignalStorage().isEmpty() && aborted.getSignalInputChannels().isEmpty(),
                "构造失败回收清空临时信号");
        helper.succeed();
    }

    @GameTest(template = "part_foundation", batch = "mmSignal", timeoutTicks = 100)
    public static void arrivalAcceptsRoutingAndKeepsOriginalSender(GameTestHelper helper) {
        RegularPart part = turret(helper);
        ProbeEndpoint subscriber = new ProbeEndpoint("probe_subscriber", List.of(CHANNEL), null);
        ProbeEndpoint sender = new ProbeEndpoint("probe_sender", List.of(), null);
        part.subscribe(subscriber);

        helper.assertTrue(part.acceptAllRoutingInput(), "Part 允许经连接器的频道到达 local");
        helper.assertTrue(!part.acceptAllBroadcastInput(), "Part 不把自己作为另一条总线的无条件接收者");

        // 端口转发先按来源写目标输入槽，再调用到达处理；这里复现同一顺序。
        part.getSignalInputChannels().computeIfAbsent(CHANNEL, key -> new SignalChannel()).put(sender, 2f);
        part.onSignalUpdated(CHANNEL, sender);

        helper.assertTrue(numberOf(part.getSignalStorage().get(CHANNEL)) == 2f,
                "端口送达同样更新 Part 键值存储");
        helper.assertTrue(numberOf(subscriber.getSignalInputChannels().get(CHANNEL).get(sender)) == 2f,
                "端口送达向订阅者广播");
        helper.assertTrue(subscriber.lastSender == sender, "广播保留原始发送者");
        part.discardUnattached();
        helper.succeed();
    }

    @GameTest(template = "part_foundation", batch = "mmSignal", timeoutTicks = 100)
    public static void portTranslationReachesLocalBus(GameTestHelper helper) {
        RegularPart part = turret(helper);
        ProbeEndpoint subscriber = new ProbeEndpoint("probe_subscriber", List.of(CHANNEL), null);
        ProbeEndpoint sender = new ProbeEndpoint("probe_sender", List.of(), null);
        part.subscribe(subscriber);

        var near = part.allConnectors.values().stream()
                .filter(connector -> connector.attachedConnector != null)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("夹具部件没有已对接的连接点"));
        SignalPort source = near.signalPort;
        SignalPort far = near.attachedConnector.signalPort;
        far.getTargets().put(CHANNEL, Map.of("local", part));
        source.getSignalInputChannels().computeIfAbsent(CHANNEL, key -> new SignalChannel()).put(sender, 3f);

        SignalResult result = source.onSignalUpdated(CHANNEL, sender);

        helper.assertTrue(result == SignalResult.PASS, "端口转发以接收者结果为结果");
        helper.assertTrue(numberOf(part.getSignalStorage().get(CHANNEL)) == 3f,
                "端口转译后到达 local 并更新 Part 键值存储");
        helper.assertTrue(numberOf(subscriber.getSignalInputChannels().get(CHANNEL).get(sender)) == 3f,
                "端口送达的广播保留原始发送者");
        part.discardUnattached();
        helper.succeed();
    }

    @GameTest(template = "part_foundation", batch = "mmSignal", timeoutTicks = 100)
    public static void localAndGlobalShareOneSlotPerSender(GameTestHelper helper) {
        RegularPart part = turret(helper);
        VehicleCore vehicle = new VehicleCore(helper.getLevel(), part);
        try {
            vehicle.onStructureChanged(VehicleStructureChangeEvent.Reason.ATTACH);
            ProbeEndpoint probe = new ProbeEndpoint("probe_subscriber", List.of(CHANNEL), null);
            ProbeEndpoint sender = new ProbeEndpoint("probe_sender", List.of(), null);
            part.subscribe(probe);
            vehicle.subSystemController.subscribe(probe);

            sender.sendSignalToTarget(CHANNEL, part, 1f);
            sender.sendSignalToTarget(CHANNEL, vehicle.subSystemController, 2f);

            SignalChannel channel = probe.getSignalInputChannels().get(CHANNEL);
            helper.assertTrue(channel.size() == 1 && numberOf(channel.get(sender)) == 2f,
                    "同一来源经两条线路只保留一个槽，最终值为后写入者");
            helper.assertTrue(numberOf(part.getSignalStorage().get(CHANNEL)) == 1f,
                    "Part 存储只记录 local 路径写入的值");
            helper.assertTrue(numberOf(vehicle.subSystemController.getSignalStorage().get(CHANNEL)) == 2f,
                    "装配体存储记录 global 路径写入的值");
            helper.assertTrue(probe.updates == 2, "两条线路各通知一次，不要求回调只发生一次");
            helper.succeed();
        } finally {
            vehicle.onRemoveFromLevel();
        }
    }

    private static float numberOf(Object value) {
        return value instanceof Number number ? number.floatValue() : Float.NaN;
    }

    /** 可配置首跳表的探针端点：既作为订阅者接收广播，也作为发送者查询/投递。 */
    private static final class ProbeEndpoint implements ISignalReceiver, ISignalSender {
        private final String address;
        private final List<String> accepted;
        private final SubPart subPart;
        private final ConcurrentMap<String, SignalChannel> channels = new ConcurrentHashMap<>();
        private final Map<String, List<String>> targetNames = new HashMap<>();
        private final Map<String, Map<String, ISignalReceiver>> targets = new HashMap<>();
        private ISignalSender lastSender;
        private int updates;

        private ProbeEndpoint(String address, List<String> accepted, SubPart subPart) {
            this.address = address;
            this.accepted = accepted;
            this.subPart = subPart;
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
            return accepted;
        }

        @Override
        public SubPart getSubPart() {
            return subPart;
        }

        @Override
        public Map<String, List<String>> getTargetNames() {
            return targetNames;
        }

        @Override
        public Map<String, Map<String, ISignalReceiver>> getTargets() {
            return targets;
        }

        @Override
        public SignalResult onSignalUpdated(String channelName, ISignalSender sender) {
            updates++;
            lastSender = sender;
            return SignalResult.PASS;
        }
    }
}
