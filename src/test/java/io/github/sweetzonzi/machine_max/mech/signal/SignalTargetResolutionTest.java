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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 只读终端查询 {@code ISignalSender#resolveTargets(String)} 的接线展开规则。
 *
 * <p>直连以配置的接收者为终端；{@code global} 按订阅者声明的接受条件展开，且不改变到达频道；
 * 查询不写信号槽、不建频道、不触发 {@code onSignalUpdated}。执行器与端口的真实接线由 GameTest
 * 覆盖，这里固定查询本身的容器与顺序契约。</p>
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
            return "global";
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
}
