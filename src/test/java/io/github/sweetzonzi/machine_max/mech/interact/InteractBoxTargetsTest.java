package io.github.sweetzonzi.machine_max.mech.interact;

import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalReceiver;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalSender;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalChannel;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.InteractBoxAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.InteractBox;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 交互区的终端解析规则。
 *
 * <p>交互区只发不收：{@code signal_targets} 解析出的终端只保留子系统，其余（部件、总线、
 * 另一个交互区……）一律丢掉。一个终端都解析不出来时交互区完全不激活，点击是空操作。</p>
 *
 * <p>尝试顺序、被占座位只让位不终止遍历这两条需要在真实内容上验证（单位测试里造不出座位实例），
 * 由 {@code InteractBoxGameTest} 在 K17 后舱盖与面包车车门上钉住。</p>
 */
class InteractBoxTargetsTest {

    /** 不是子系统的接收者：它没有资格成为交互目标。 */
    private static final class NonSubsystemReceiver implements ISignalReceiver {
        private final String address;
        private final ConcurrentMap<String, SignalChannel> channels = new ConcurrentHashMap<>();

        NonSubsystemReceiver(String address) {
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
    }

    /** 构造一个不门控、不依赖真实部件的交互区。 */
    private static InteractBox box() {
        return new InteractBox(null, "interact.test",
                new InteractBoxAttr("TestInteract", Map.of("interact", List.of("a", "b")), "fast", "true"));
    }

    @Test
    void nonSubsystemTerminalsAreDropped() {
        InteractBox box = box();
        Map<String, ISignalReceiver> ordered = new LinkedHashMap<>();
        ordered.put("a", new NonSubsystemReceiver("a"));
        ordered.put("b", new NonSubsystemReceiver("b"));
        box.getTargets().put("interact", ordered);

        // 没有宿主部件时首跳表保持注入值，因此这里只验证「终端过滤」这一半。
        box.setTargetFromNames();

        assertTrue(box.getInteractTargets().isEmpty(), "非子系统终端不得进入交互目标表");
        assertFalse(box.canInteract(), "没有可交互终端的交互区必须不激活");
        box.interact(null);// 空操作
    }

    @Test
    void boxWithoutTargetsIsInert() {
        InteractBox box = box();

        assertTrue(box.getInteractTargets().isEmpty(), "未解析时不应有目标");
        assertFalse(box.canInteract(), "没有接收者的交互区必须不激活");
        box.interact(null);// 空操作
    }
}
