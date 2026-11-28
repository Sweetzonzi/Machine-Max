package io.github.sweetzonzi.machine_max.mech.signal;

import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalBus;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalReceiver;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalSender;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalChannel;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalResult;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 只读终端查询 {@code ISignalSender#resolveTargets(...)} 的接线展开规则。
 *
 * <p>直连以配置的接收者为终端；{@code global} 按订阅者声明的接受条件展开，且不改变到达频道；
 * 查询不写信号槽、不建频道、不触发 {@code onSignalUpdated}。{@code includeBuses = true} 的一档把途经的
 * 总线实例本身也记为投递点，与只取叶子的一档共用同一趟遍历，差集恰好是总线实例。执行器与端口的真实接线
 * 由 GameTest 覆盖，这里固定查询本身的容器与顺序契约。</p>
 */
class SignalTargetResolutionTest {

    /** 记录被查询的频道与处理次数，便于断言只读性。 */
    private static final class TestReceiver implements ISignalReceiver {
        private final String address;
        private final List<String> accepted;
        private final boolean acceptAllBroadcast;
        private final ConcurrentMap<String, SignalChannel> channels = new ConcurrentHashMap<>();
        private int updates;

        private TestReceiver(String address, List<String> accepted) {
            this(address, accepted, false);
        }

        private TestReceiver(String address, List<String> accepted, boolean acceptAllBroadcast) {
            this.address = address;
            this.accepted = accepted;
            this.acceptAllBroadcast = acceptAllBroadcast;
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
        public boolean acceptAllBroadcastInput() {
            return acceptAllBroadcast;
        }

        @Override
        public SignalResult onSignalUpdated(String channelName, ISignalSender sender) {
            updates++;
            return SignalResult.PASS;
        }
    }

    /** 首跳表按调用顺序保留，模拟 {@code setTargetFromNames} 的保序写入。 */
    private static final class TestSender implements ISignalSender {
        private final Map<String, List<String>> targetNames = new LinkedHashMap<>();
        private final Map<String, Map<String, ISignalReceiver>> targets = new LinkedHashMap<>();

        private void target(String channel, ISignalReceiver... receivers) {
            Map<String, ISignalReceiver> hop = new LinkedHashMap<>();
            for (ISignalReceiver receiver : receivers) hop.put(receiver.getSignalAddress(), receiver);
            targets.put(channel, hop);
        }

        @Override
        public Map<String, List<String>> getTargetNames() {
            return targetNames;
        }

        @Override
        public Map<String, Map<String, ISignalReceiver>> getTargets() {
            return targets;
        }
    }

    /** 订阅集合故意用插入顺序无关的语义，查询必须自行固定候选顺序。 */
    private static final class TestBus implements ISignalBus {
        private final Set<ISignalReceiver> subscribers = new LinkedHashSet<>();
        private final ConcurrentMap<String, SignalChannel> channels = new ConcurrentHashMap<>();
        private final String address;

        private TestBus() {
            this("global");
        }

        private TestBus(String address) {
            this.address = address;
        }

        @Override
        public Set<ISignalReceiver> getAllSubscribers() {
            return subscribers;
        }

        @Override
        public void subscribe(ISignalReceiver subscriber) {
            subscribers.add(subscriber);
        }

        @Override
        public void unsubscribe(ISignalReceiver subscriber) {
            subscribers.remove(subscriber);
        }

        @Override
        public ConcurrentMap<String, SignalChannel> getSignalInputChannels() {
            return channels;
        }

        @Override
        public String getSignalAddress() {
            return address;
        }
    }

    @Test
    void directTargetIsTheTerminal() {
        TestSender sender = new TestSender();
        TestReceiver engine = new TestReceiver("engine", List.of("throttle"));
        sender.target("throttle", engine);

        Map<String, List<ISignalReceiver>> resolved = sender.resolveTargets("throttle");

        assertEquals(Map.of("throttle", List.of(engine)), resolved);
    }

    @Test
    void absentOrEmptyChannelResolvesToNothing() {
        TestSender sender = new TestSender();
        sender.target("throttle");

        assertTrue(sender.resolveTargets("throttle").isEmpty(), "空首跳表没有终端");
        assertTrue(sender.resolveTargets("missing").isEmpty(), "未配置的频道没有终端");
        assertTrue(sender.resolveTargets("").isEmpty(), "空频道名没有终端");
    }

    @Test
    void globalExpandsOnlySubscribersThatAcceptTheArrivalChannel() {
        TestSender seat = new TestSender();
        TestBus bus = new TestBus();
        TestReceiver any = new TestReceiver("a_accept_all", List.of(), true);
        TestReceiver sight = new TestReceiver("c_sight", List.of("gunner_sight"));
        TestReceiver other = new TestReceiver("d_other", List.of("other_channel"));
        seat.target("gunner_sight", bus);
        bus.subscribe(sight);
        bus.subscribe(other);
        bus.subscribe(any);

        Map<String, List<ISignalReceiver>> resolved = seat.resolveTargets("gunner_sight");

        assertEquals(List.of(any, sight), resolved.get("gunner_sight"),
                "只保留声明接受该频道的订阅者，到达频道保持原值");
        assertEquals(Map.of("gunner_sight", List.of(any, sight)), resolved);
    }

    @Test
    void busCandidatesAreSortedByAddress() {
        TestSender seat = new TestSender();
        TestBus bus = new TestBus();
        TestReceiver zulu = new TestReceiver("zulu", List.of("sight"));
        TestReceiver mike = new TestReceiver("mike", List.of("sight"));
        TestReceiver alpha = new TestReceiver("alpha", List.of("sight"));
        seat.target("sight", bus);
        bus.subscribe(zulu);
        bus.subscribe(mike);
        bus.subscribe(alpha);

        assertEquals(List.of(alpha, mike, zulu), seat.resolveTargets("sight").get("sight"),
                "候选顺序不能由订阅集合的迭代顺序决定");
    }

    @Test
    void sameTerminalOnTheSameChannelAppearsOnceAcrossBranches() {
        TestSender sender = new TestSender();
        TestBus first = new TestBus();
        TestBus second = new TestBus();
        TestReceiver shared = new TestReceiver("shared", List.of("control"));
        sender.target("control", first, second);
        first.subscribe(shared);
        second.subscribe(shared);

        assertEquals(List.of(shared), sender.resolveTargets("control").get("control"),
                "同一终端在同一到达频道只出现一次");
    }

    @Test
    void instanceSendBypassesTargetTableAndWritesReceiverSlot() {
        TestSender sender = new TestSender();
        TestReceiver actuator = new TestReceiver("actuator", List.of());

        SignalResult result = sender.sendSignalToTarget("control", actuator, 2f);

        assertEquals(SignalResult.PASS, result);
        assertEquals(1, actuator.updates, "实例直送同步触发接收者处理");
        assertEquals((Object) 2f, actuator.getSignalInputChannels().get("control").get(sender),
                "信号按发送者身份写入目标输入槽");
        assertTrue(sender.getTargets().isEmpty(), "实例直送不查、也不改首跳表");
    }

    @Test
    void queryDoesNotWriteSignalsOrCreateChannels() {
        TestSender sender = new TestSender();
        TestBus bus = new TestBus();
        TestReceiver sight = new TestReceiver("sight", List.of("gunner_sight"));
        sender.target("gunner_sight", bus);
        bus.subscribe(sight);
        Map<String, Map<String, ISignalReceiver>> before = new LinkedHashMap<>(sender.getTargets());

        Map<String, List<ISignalReceiver>> resolved = sender.resolveTargets("gunner_sight");

        assertEquals(0, sight.updates, "查询不能触发接收者处理");
        assertTrue(sight.getSignalInputChannels().isEmpty(), "查询不能为接收者建输入频道");
        assertTrue(bus.getSignalInputChannels().isEmpty(), "查询不能写总线存储");
        assertEquals(before, sender.getTargets(), "查询不能改动首跳表");
        assertThrows(UnsupportedOperationException.class, () -> resolved.put("x", List.of()),
                "结果映射不可变");
        assertThrows(UnsupportedOperationException.class,
                () -> resolved.get("gunner_sight").add(sight), "终端列表不可变");
    }

    @Test
    void localFirstHopExpandsPartSubscribersAndStopsAtTheBus() {
        TestSender sender = new TestSender();
        TestBus partBus = new TestBus("local");
        TestReceiver seat = new TestReceiver("turret_seat", List.of("loader_reload"));
        TestReceiver unrelated = new TestReceiver("unrelated", List.of("other_channel"));
        sender.target("loader_reload", partBus);
        partBus.subscribe(seat);
        partBus.subscribe(unrelated);

        Map<String, List<ISignalReceiver>> resolved = sender.resolveTargets("loader_reload");

        assertEquals(Map.of("loader_reload", List.of(seat)), resolved,
                "local 按订阅者声明展开，Part 本身不作为终端");
        assertFalse(resolved.values().stream().flatMap(List::stream).anyMatch(receiver -> receiver == partBus),
                "总线对象不进入终端列表");
        assertTrue(partBus.getSignalInputChannels().isEmpty(), "查询不写 Part 输入槽");
        assertEquals(0, seat.updates, "查询不触发接收者处理");
    }

    @Test
    void busWithoutSubscribersHasNoTerminal() {
        TestSender sender = new TestSender();
        TestBus idleBus = new TestBus("local");
        sender.target("loader_reload", idleBus);

        assertTrue(sender.resolveTargets("loader_reload").isEmpty(),
                "没有订阅者的 local 总线不产生终端，也不回退为 Part 自身");
    }

    // ===== 投递点一档：总线实例本身算不算终端 =====

    @Test
    void leafOnlyIsTheDefaultAndAlsoTheExplicitFalse() {
        TestSender sender = new TestSender();
        TestBus bus = new TestBus();
        TestReceiver sight = new TestReceiver("sight", List.of("gunner_sight"));
        sender.target("gunner_sight", bus);
        bus.subscribe(sight);

        assertEquals(sender.resolveTargets("gunner_sight"), sender.resolveTargets("gunner_sight", false),
                "缺省等价于 includeBuses = false，现有调用点不受影响");
        assertEquals(List.of(sight), sender.resolveTargets("gunner_sight").get("gunner_sight"));
    }

    @Test
    void includeBusesAddsTheBusBeforeItsSubscribers() {
        TestSender sender = new TestSender();
        TestBus bus = new TestBus("local");
        TestReceiver seat = new TestReceiver("turret_seat", List.of("loader_reload"));
        sender.target("loader_reload", bus);
        bus.subscribe(seat);

        List<ISignalReceiver> deliveryPoints = sender.resolveTargets("loader_reload", true).get("loader_reload");

        assertEquals(List.of(bus, seat), deliveryPoints, "总线先记自己，再按固定顺序展开订阅者");
        assertEquals(List.of(seat), sender.resolveTargets("loader_reload").get("loader_reload"),
                "只取叶子的一档不把总线算进去");
    }

    @Test
    void includeBusesKeepsABusWithoutSubscribers() {
        TestSender sender = new TestSender();
        TestBus idleBus = new TestBus("global");
        sender.target("door_open", idleBus);

        assertEquals(List.of(idleBus), sender.resolveTargets("door_open", true).get("door_open"),
                "事件在空总线上也会产生动作，所以它本身就是投递点");
        assertTrue(sender.resolveTargets("door_open").isEmpty(), "只取叶子的那一档仍然没有终端");
    }

    @Test
    void firstHopWhitelistComposesWithTheBusAxis() {
        TestSender sender = new TestSender();
        TestBus partBus = new TestBus("local");
        TestReceiver leaf = new TestReceiver("hull", List.of("door_open"));
        sender.target("door_open", partBus, leaf);

        assertEquals(List.of(partBus),
                sender.resolveTargets("door_open", List.of("local"), true).get("door_open"),
                "白名单只留下首跳 local，投递点一档把它记成总线实例");
        assertEquals(List.of(leaf),
                sender.resolveTargets("door_open", List.of("hull")).get("door_open"),
                "白名单缺省只取叶子");
        assertTrue(sender.resolveTargets("door_open", List.of(), true).isEmpty(),
                "空白名单表示这条频道在本组不到达任何投递点");
    }

    @Test
    void includeBusesQueryStaysReadOnly() {
        TestSender sender = new TestSender();
        TestBus bus = new TestBus("local");
        TestReceiver seat = new TestReceiver("seat", List.of("loader_reload"));
        sender.target("loader_reload", bus);
        bus.subscribe(seat);

        sender.resolveTargets("loader_reload", true);

        assertEquals(0, seat.updates, "查询不触发接收者处理");
        assertTrue(bus.getSignalInputChannels().isEmpty(), "查询不写总线输入槽");
        assertTrue(seat.getSignalInputChannels().isEmpty(), "查询不写接收者输入槽");
    }
}
