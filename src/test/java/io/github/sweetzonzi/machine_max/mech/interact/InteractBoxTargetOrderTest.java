package io.github.sweetzonzi.machine_max.mech.interact;

import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalReceiver;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalSender;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalChannel;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalResult;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.InteractBoxAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.InteractBox;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 交互区多目标遍历规则。
 *
 * <p>交互区按 {@code signal_targets} 中接收者的声明顺序逐个尝试：{@link SignalResult#CONSUME}
 * 结束本次交互，{@link SignalResult#FAIL}（如座位已被占用）只表示该目标本次接不了，继续尝试下一个。
 * 接收者表由 {@code ISignalSender#setTargetFromNames()} 保序写入，因此声明顺序就是尝试顺序；
 * 一扇门通往多个座位的交互区依赖这条规则决定先坐哪一个。</p>
 */
class InteractBoxTargetOrderTest {

    /** 记录调用顺序、返回固定结果的测试接收者。 */
    private static final class FakeReceiver implements ISignalReceiver {
        private final String address;
        private final SignalResult result;
        private final List<String> calls;
        private final ConcurrentMap<String, SignalChannel> channels = new ConcurrentHashMap<>();

        FakeReceiver(String address, SignalResult result, List<String> calls) {
            this.address = address;
            this.result = result;
            this.calls = calls;
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
        public SignalResult onSignalUpdated(String channelName, ISignalSender sender) {
            calls.add(address);
            return result;
        }
    }

    /** 按传入顺序构造接收者表的交互区，模拟 setTargetFromNames 的保序写入。 */
    private static InteractBox interactBoxWith(ISignalReceiver... receivers) {
        InteractBox box = new InteractBox(null, "interact.test",
                new InteractBoxAttr("TestInteract", Map.of("interact", List.of("commander_seat", "gunner_seat")),
                        "fast", "NOR"));
        Map<String, ISignalReceiver> ordered = new LinkedHashMap<>();
        for (ISignalReceiver receiver : receivers) {
            ordered.put(receiver.getSignalAddress(), receiver);
        }
        box.getTargets().put("interact", ordered);
        return box;
    }

    @Test
    void firstConsumingTargetEndsTraversal() {
        List<String> calls = new ArrayList<>();
        InteractBox box = interactBoxWith(
                new FakeReceiver("commander_seat", SignalResult.CONSUME, calls),
                new FakeReceiver("gunner_seat", SignalResult.CONSUME, calls));

        box.interact(null);

        assertEquals(List.of("commander_seat"), calls, "第一个目标接管后不应再尝试后面的目标");
    }

    @Test
    void occupiedSeatFallsThroughToNextTarget() {
        List<String> calls = new ArrayList<>();
        InteractBox box = interactBoxWith(
                new FakeReceiver("commander_seat", SignalResult.FAIL, calls),
                new FakeReceiver("gunner_seat", SignalResult.CONSUME, calls));

        box.interact(null);

        assertEquals(List.of("commander_seat", "gunner_seat"), calls, "座位被占用应继续尝试下一个座位");
    }

    @Test
    void passiveTargetFallsThroughToNextTarget() {
        List<String> calls = new ArrayList<>();
        InteractBox box = interactBoxWith(
                new FakeReceiver("commander_seat", SignalResult.PASS, calls),
                new FakeReceiver("gunner_seat", SignalResult.CONSUME, calls));

        box.interact(null);

        assertEquals(List.of("commander_seat", "gunner_seat"), calls, "目标不处理时继续尝试下一个目标");
    }
}
