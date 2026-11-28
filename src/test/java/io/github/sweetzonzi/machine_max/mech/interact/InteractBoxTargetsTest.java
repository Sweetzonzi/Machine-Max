package io.github.sweetzonzi.machine_max.mech.interact;

import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalBus;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalReceiver;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalSender;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalChannel;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.InteractBoxAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.InteractBox;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 交互区按投递点解析的两档：到达总线的频道是事件线，到达子系统的终端是交互目标。
 *
 * <p>交互区只发不收：点击时事件线经 {@code emitSignal(频道)} 沿接线投递（负载 {@code null}、不写任何输入槽），
 * 子系统仍直接调用 {@code onInteract}。没有宿主部件的单位测试里首跳表由夹具注入，因此这里固定的是
 * 「解析与投递」这一半：频道能到总线才算事件线，能点亮提示；只到叶子（非总线、非子系统）的频道两档都不算，
 * 交互区完全不激活。</p>
 *
 * <p>跨零件的真实端口转译、座椅声明顺序与被占座位让位由 {@code InteractBoxGameTest} 钉住。</p>
 */
class InteractBoxTargetsTest {

    /** 总线：记录自身与订阅者收到的事件，用来断言事件线投递到了哪里。 */
    private static final class RecordingBus implements ISignalBus {
        private final String address;
        private final Set<ISignalReceiver> subscribers = new LinkedHashSet<>();
        private final ConcurrentMap<String, SignalChannel> channels = new ConcurrentHashMap<>();
        final List<String> events = new ArrayList<>();
        final List<Object> payloads = new ArrayList<>();

        RecordingBus(String address) {
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

        @Override
        public void onSignalEvent(String channelName, ISignalSender sender, Object payload) {
            events.add(channelName);
            payloads.add(payload);
        }
    }

    /** 叶子接收者：不是子系统，也没有总线。只用来验证它不会被当成两档中的任何一档。 */
    private static final class Leaf implements ISignalReceiver {
        private final String address;
        private final ConcurrentMap<String, SignalChannel> channels = new ConcurrentHashMap<>();
        final List<String> events = new ArrayList<>();

        Leaf(String address) {
            this.address = address;
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
        public java.util.List<String> getAcceptedChannels() {
            return java.util.List.of("any_channel");
        }

        @Override
        public boolean acceptAllBroadcastInput() {
            return true;
        }

        @Override
        public void onSignalEvent(String channelName, ISignalSender sender, Object payload) {
            events.add(channelName);
        }
    }

    /** 构造一个不门控、不依赖真实部件的交互区。 */
    private static InteractBox box(String channel, List<String> targetNames) {
        return new InteractBox(null, "interact.test",
                new InteractBoxAttr("TestInteract", Map.of(channel, targetNames), "fast", "true"));
    }

    /** 注入首跳表；没有宿主部件时 {@code setTargetFromNames} 只做解析、不重建首跳表。 */
    private static void wire(InteractBox box, String channel, ISignalReceiver... firstHop) {
        Map<String, ISignalReceiver> hop = new LinkedHashMap<>();
        for (ISignalReceiver receiver : firstHop) hop.put(receiver.getSignalAddress(), receiver);
        box.getTargets().put(channel, hop);
    }

    @Test
    void channelReachingBusIsAnEventLine() {
        RecordingBus bus = new RecordingBus("local");
        InteractBox box = box("door_open", List.of("local"));
        wire(box, "door_open", bus);

        box.setTargetFromNames();

        assertEquals(List.of("door_open"), box.getEventChannels(), "到达总线的频道算事件线");
        assertTrue(box.getInteractTargets().isEmpty(), "总线不是子系统，不进入交互目标表");
        assertTrue(box.canInteract(), "只有事件线的交互区也应可用");
        box.interact(null);
        assertEquals(List.of("door_open"), bus.events, "点击把事件投递到总线");
        assertEquals(1, bus.payloads.size());
        assertEquals(null, bus.payloads.get(0), "纯触发的事件负载是 null");
        assertTrue(bus.getSignalInputChannels().isEmpty(), "事件不写总线输入槽");
    }

    @Test
    void eventChannelIsDeliveredAlongTheWholeLine() {
        RecordingBus local = new RecordingBus("local");
        Leaf leaf = new Leaf("hull");
        InteractBox box = box("hood_toggle", List.of("local", "hull"));
        wire(box, "hood_toggle", local, leaf);

        box.setTargetFromNames();
        box.interact(null);

        assertEquals(List.of("hood_toggle"), box.getEventChannels(), "频道里只要有一条到总线，整条频道就是事件线");
        assertEquals(List.of("hood_toggle"), local.events, "总线收到一次");
        assertEquals(List.of("hood_toggle"), leaf.events, "同一频道的叶子接收者也收到");
        assertTrue(leaf.getSignalInputChannels().isEmpty(), "事件不写叶子输入槽");
    }

    @Test
    void channelReachingOnlyLeavesIsNeitherAnEventLineNorAnInteractTarget() {
        Leaf leaf = new Leaf("hull");
        InteractBox box = box("door_open", List.of("hull"));
        wire(box, "door_open", leaf);

        box.setTargetFromNames();

        assertTrue(box.getEventChannels().isEmpty(), "没有到达总线的频道不算事件线");
        assertTrue(box.getInteractTargets().isEmpty(), "非子系统终端不进入交互目标表");
        assertFalse(box.canInteract(), "两档都落空的交互区必须不激活");
        box.interact(null);
        assertTrue(leaf.events.isEmpty(), "非事件线的频道不在点击时投递");
    }

    @Test
    void boxWithoutTargetsIsInert() {
        InteractBox box = box("interact", List.of("a", "b"));

        assertTrue(box.getEventChannels().isEmpty(), "未解析时没有事件线");
        assertTrue(box.getInteractTargets().isEmpty(), "未解析时不应有目标");
        assertFalse(box.canInteract(), "没有接收者的交互区必须不激活");
        box.interact(null);// 空操作
    }

    @Test
    void destroyClearsResolvedChannels() {
        RecordingBus bus = new RecordingBus("global");
        InteractBox box = box("hood_toggle", List.of("global"));
        wire(box, "hood_toggle", bus);
        box.setTargetFromNames();
        assertTrue(box.canInteract(), "解析出事件线后可用");

        box.destroy();

        assertTrue(box.getEventChannels().isEmpty(), "销毁时清除事件线");
        assertTrue(box.getInteractTargets().isEmpty(), "销毁时清除交互目标");
        assertTrue(box.getTargets().isEmpty(), "销毁时丢弃首跳表");
        assertFalse(box.canInteract(), "销毁后的交互区不能继续激活");
        box.interact(null);
        assertTrue(bus.events.isEmpty(), "销毁后点击不再投递");
    }
}
