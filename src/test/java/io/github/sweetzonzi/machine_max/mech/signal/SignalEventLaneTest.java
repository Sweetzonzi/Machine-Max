package io.github.sweetzonzi.machine_max.mech.signal;

import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalBus;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalReceiver;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalSender;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalChannel;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 事件车道的投递语义：{@code emitSignal} 发出、{@code broadcastEvent} 分发、{@code onSignalEvent} 接收。
 *
 * <p>钉住的契约：事件不写任何输入槽或存储表；每次 emit 都是一次新事件，相同的负载连发两次到达两次；
 * 总线自身先收到一次，再按接受条件分发给订阅者；重访判定按「接收者 × 到达频道」终止环路，
 * 菱形接线只投一次，同一接收者经不同到达频道各投一次；嵌套 emit 共用同一份会话；回调抛异常后
 * 会话仍会清空。</p>
 *
 * <p>端口的频道转译与跨零件到达属于事件车道的后续阶段，本用例集不覆盖。</p>
 */
class SignalEventLaneTest {

    /** 记录事件到达的频道与负载；值车道的槽与存储表原样保留，用来断言事件不碰它们。 */
    private static final class Recorder implements ISignalReceiver {
        final String address;
        private final List<String> accepted;
        private final boolean acceptAllBroadcast;
        final ConcurrentMap<String, SignalChannel> inputChannels = new ConcurrentHashMap<>();
        final ConcurrentMap<String, Object> storage = new ConcurrentHashMap<>();
        final List<String> events = new ArrayList<>();
        final List<Object> payloads = new ArrayList<>();
        private final List<String> trace;
        int updates;
        /** 置为 true 时 {@link #onSignalEvent} 抛出异常，用来验证会话的异常收尾。 */
        boolean fail;

        Recorder(String address, List<String> accepted) {
            this(address, accepted, false, new ArrayList<>());
        }

        Recorder(String address, List<String> accepted, boolean acceptAllBroadcast, List<String> trace) {
            this.address = address;
            this.accepted = accepted;
            this.acceptAllBroadcast = acceptAllBroadcast;
            this.trace = trace;
        }

        @Override
        public String getSignalAddress() {
            return address;
        }

        @Override
        public ConcurrentMap<String, SignalChannel> getSignalInputChannels() {
            return inputChannels;
        }

        @Override
        public Map<String, Object> getSignalStorage() {
            return storage;
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

        @Override
        public void onSignalEvent(String channelName, ISignalSender sender, Object payload) {
            events.add(channelName);
            payloads.add(payload);
            trace.add(address + ":" + channelName);
            if (fail) throw new IllegalStateException("boom");
        }
    }

    /** 总线：记录自身收到的事件，订阅集合按插入顺序，接受条件由订阅者自己声明。 */
    private static final class TestBus implements ISignalBus {
        private final String address;
        private final Set<ISignalReceiver> subscribers = new LinkedHashSet<>();
        private final ConcurrentMap<String, SignalChannel> inputChannels = new ConcurrentHashMap<>();
        private final List<String> busEvents = new ArrayList<>();
        private final List<String> trace;

        TestBus(String address) {
            this(address, new ArrayList<>());
        }

        TestBus(String address, List<String> trace) {
            this.address = address;
            this.trace = trace;
        }

        List<String> busEvents() {
            return busEvents;
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
            return inputChannels;
        }

        @Override
        public String getSignalAddress() {
            return address;
        }

        @Override
        public void onSignalEvent(String channelName, ISignalSender sender, Object payload) {
            busEvents.add(channelName);
            trace.add(address + ":" + channelName);
        }
    }

    /** 首跳表按配置顺序保留，模拟 {@code setTargetFromNames} 的保序写入。 */
    private static final class TestSender implements ISignalSender {
        private final Map<String, List<String>> targetNames = new LinkedHashMap<>();
        private final Map<String, Map<String, ISignalReceiver>> targets = new LinkedHashMap<>();

        void target(String channel, ISignalReceiver... receivers) {
            Map<String, ISignalReceiver> hop = new LinkedHashMap<>();
            for (ISignalReceiver receiver : receivers) {
                ISignalReceiver previous = hop.put(receiver.getSignalAddress(), receiver);
                if (previous != null) throw new IllegalArgumentException("首跳名重复：" + receiver.getSignalAddress());
            }
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

    /** 既是接收者又是发送者：收到事件后能再 emit，用来构造嵌套投递与回送。 */
    private static final class Relay implements ISignalReceiver, ISignalSender {
        final String address;
        private final List<String> accepted;
        final List<String> events = new ArrayList<>();
        private final Map<String, Map<String, ISignalReceiver>> targets = new LinkedHashMap<>();
        private final ConcurrentMap<String, SignalChannel> inputChannels = new ConcurrentHashMap<>();
        private Runnable reaction;

        Relay(String address, List<String> accepted) {
            this.address = address;
            this.accepted = accepted;
        }

        void target(String channel, ISignalReceiver... receivers) {
            Map<String, ISignalReceiver> hop = new LinkedHashMap<>();
            for (ISignalReceiver receiver : receivers) hop.put(receiver.getSignalAddress(), receiver);
            targets.put(channel, hop);
        }

        @Override
        public String getSignalAddress() {
            return address;
        }

        @Override
        public ConcurrentMap<String, SignalChannel> getSignalInputChannels() {
            return inputChannels;
        }

        @Override
        public List<String> getAcceptedChannels() {
            return accepted;
        }

        @Override
        public Map<String, List<String>> getTargetNames() {
            return Map.of();
        }

        @Override
        public Map<String, Map<String, ISignalReceiver>> getTargets() {
            return targets;
        }

        @Override
        public void onSignalEvent(String channelName, ISignalSender sender, Object payload) {
            events.add(channelName);
            if (reaction != null) reaction.run();
        }
    }

    // ===== 不写槽 =====

    @Test
    void emitDeliversWithoutWritingAnySlot() {
        TestSender sender = new TestSender();
        Recorder leaf = new Recorder("leaf", List.of());
        sender.target("door_open", leaf);
        Object payload = new Object();

        sender.emitSignal("door_open", payload);

        assertEquals(List.of("door_open"), leaf.events);
        assertSame(payload, leaf.payloads.get(0), "负载原样交给接收者");
        assertTrue(leaf.inputChannels.isEmpty(), "事件不写接收者输入槽");
        assertTrue(leaf.storage.isEmpty(), "事件不写接收者存储表");
        assertEquals(0, leaf.updates, "事件不触发值车道回调");
    }

    @Test
    void busDeliveryKeepsBothBusAndSubscriberSlotsEmpty() {
        TestSender sender = new TestSender();
        TestBus bus = new TestBus("global");
        Recorder listener = new Recorder("listener", List.of("door_open"));
        Recorder ignored = new Recorder("ignored", List.of("other_channel"));
        sender.target("door_open", bus);
        bus.subscribe(listener);
        bus.subscribe(ignored);

        sender.emitSignal("door_open", null);

        assertEquals(List.of("door_open"), bus.busEvents(), "总线自身先收到一次");
        assertEquals(List.of("door_open"), listener.events, "接受该频道的订阅者收到");
        assertTrue(ignored.events.isEmpty(), "未声明接受该频道的订阅者不收到");
        assertTrue(bus.inputChannels.isEmpty(), "总线不写输入槽");
        assertTrue(listener.inputChannels.isEmpty(), "订阅者不写输入槽");
        assertTrue(listener.storage.isEmpty(), "订阅者不写存储表");
        assertEquals(0, listener.updates, "不触发值车道回调");
    }

    @Test
    void busHandsTheEventToItselfBeforeItsSubscribers() {
        List<String> trace = new ArrayList<>();
        TestSender sender = new TestSender();
        TestBus bus = new TestBus("global", trace);
        Recorder listener = new Recorder("listener", List.of("door_open"), false, trace);
        sender.target("door_open", bus);
        bus.subscribe(listener);

        sender.emitSignal("door_open", null);

        assertEquals(List.of("global:door_open", "listener:door_open"), trace);
    }

    @Test
    void acceptAllBroadcastInputSubscriberReceivesAnyChannel() {
        TestSender sender = new TestSender();
        TestBus bus = new TestBus("local");
        Recorder any = new Recorder("any", List.of(), true, new ArrayList<>());
        sender.target("door_open", bus);
        bus.subscribe(any);

        sender.emitSignal("door_open", null);

        assertEquals(List.of("door_open"), any.events);
    }

    // ===== 不去重 =====

    @Test
    void sameChannelAndPayloadDeliveredOnEveryEmit() {
        TestSender sender = new TestSender();
        Recorder leaf = new Recorder("leaf", List.of());
        sender.target("door_open", leaf);

        sender.emitSignal("door_open", "same");
        sender.emitSignal("door_open", "same");

        assertEquals(List.of("door_open", "door_open"), leaf.events, "每次 emit 都是一次新事件");
        assertEquals(2, leaf.payloads.size());
    }

    // ===== 重访判定 =====

    @Test
    void diamondWiringDeliversOncePerReceiver() {
        TestSender sender = new TestSender();
        TestBus left = new TestBus("left");
        TestBus right = new TestBus("right");
        Recorder shared = new Recorder("shared", List.of("door_open"));
        sender.target("door_open", left, right);
        left.subscribe(shared);
        right.subscribe(shared);

        sender.emitSignal("door_open", null);

        assertEquals(List.of("door_open"), shared.events, "同一到达频道只投递一次");
        assertEquals(List.of("door_open"), left.busEvents());
        assertEquals(List.of("door_open"), right.busEvents());
    }

    @Test
    void nestedEmitSharesTheSessionAndSuppressesTheEcho() {
        TestSender sender = new TestSender();
        Relay relay = new Relay("relay", List.of("door_open"));
        Recorder downstream = new Recorder("downstream", List.of("chain_next"));
        Recorder sibling = new Recorder("sibling", List.of("door_open"));
        relay.target("chain_next", downstream);
        relay.target("door_open", relay, sibling);
        relay.reaction = () -> {
            relay.emitSignal("chain_next", null);
            relay.emitSignal("door_open", null);
        };
        sender.target("door_open", relay);

        sender.emitSignal("door_open", null);

        assertEquals(List.of("door_open"), relay.events, "回送到上游的同一频道被抑制");
        assertEquals(List.of("chain_next"), downstream.events, "嵌套 emit 的另一条频道照常投递");
        assertEquals(List.of("door_open"), sibling.events, "另一接收者收同一频道不受影响");
    }

    @Test
    void sameReceiverOnAnotherArrivalChannelIsNotSuppressed() {
        TestSender sender = new TestSender();
        Relay relay = new Relay("relay", List.of("door_open"));
        relay.target("chain_next", relay);
        relay.reaction = () -> relay.emitSignal("chain_next", null);
        sender.target("door_open", relay);

        sender.emitSignal("door_open", null);

        assertEquals(List.of("door_open", "chain_next"), relay.events, "到达频道不同时各投一次");
    }

    @Test
    void callbackExceptionDoesNotPoisonTheSession() {
        TestSender sender = new TestSender();
        Recorder thrower = new Recorder("thrower", List.of());
        thrower.fail = true;
        sender.target("door_open", thrower);

        assertThrows(IllegalStateException.class, () -> sender.emitSignal("door_open", null));
        assertThrows(IllegalStateException.class, () -> sender.emitSignal("door_open", null),
                "异常收尾后会话已清空，第二次顶层 emit 仍会到达同一接收者");

        Recorder normal = new Recorder("normal", List.of());
        sender.target("door_open", normal);
        sender.emitSignal("door_open", null);
        assertEquals(List.of("door_open"), normal.events, "车道在异常之后仍可继续投递");
    }

    // ===== 入口与寻址 =====

    @Test
    void broadcastEventCalledDirectlyStartsItsOwnSession() {
        TestSender sender = new TestSender();
        TestBus bus = new TestBus("global");
        Recorder listener = new Recorder("listener", List.of("door_open"));
        bus.subscribe(listener);

        bus.broadcastEvent("door_open", sender, null);
        bus.broadcastEvent("door_open", sender, null);

        assertEquals(List.of("door_open", "door_open"), bus.busEvents());
        assertEquals(List.of("door_open", "door_open"), listener.events,
                "没有外层 emit 时也按空会话开始，不残留上一次的 visited");
    }

    @Test
    void instanceEmitBypassesTargetTable() {
        TestSender sender = new TestSender();
        Recorder leaf = new Recorder("leaf", List.of());

        sender.emitSignal("door_open", leaf, null);

        assertEquals(List.of("door_open"), leaf.events);
        assertTrue(sender.getTargets().isEmpty(), "实例投递不查、也不改首跳表");
    }

    @Test
    void namedEmitReachesOnlyThatFirstHop() {
        TestSender sender = new TestSender();
        Recorder left = new Recorder("left", List.of("door_open"));
        Recorder right = new Recorder("right", List.of("door_open"));
        sender.target("door_open", left, right);

        sender.emitSignal("door_open", "left", null);

        assertEquals(List.of("door_open"), left.events);
        assertTrue(right.events.isEmpty());
    }

    @Test
    void unknownChannelOrTargetIsSilent() {
        TestSender sender = new TestSender();
        Recorder leaf = new Recorder("leaf", List.of());
        sender.target("known", leaf);

        sender.emitSignal("missing", null);
        sender.emitSignal("", null);
        sender.emitSignal("known", "missing_name", null);

        assertTrue(leaf.events.isEmpty());
    }
}
