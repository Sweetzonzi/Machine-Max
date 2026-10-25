package io.github.sweetzonzi.machine_max.common.mech.vehicle.interact;

import io.github.sweetzonzi.machine_max.common.mech.signal.*;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.InteractBoxAttr;
import lombok.Getter;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Getter
public class InteractBox implements ISignalSender, ISignalReceiver {
    public final String name;
    public final InteractBoxAttr attr;
    public final Map<String, List<String>> targetNames;
    public final Map<String, Map<String, ISignalReceiver>> targets = new LinkedHashMap<>();//信号频道名->接收者名称->接收者（保序，顺序即目标声明顺序）
    public final Map<String, Set<ISignalReceiver>> callbackTargets = new HashMap<>();//信号频道名->接收者名称->接收者
    public final ConcurrentMap<String, SignalChannel> signalInputChannels = new ConcurrentHashMap<>();
    public final SubPart subPart;
    public final InteractMode interactMode;
    public boolean enabled = true;//是否处于启用状态的开关

    public enum InteractMode {
        FAST,
        ACCURATE
    }

    public InteractBox(SubPart subPart, String name, InteractBoxAttr attr) {
        this.subPart = subPart;
        this.name = name;
        this.attr = attr;
        this.targetNames = attr.getSignalTargets();
        this.interactMode = InteractMode.valueOf(attr.getMode().toUpperCase());
    }

    /** 交互区以自身名称为寻址名 */
    @Override
    public String getSignalAddress() {
        return name;
    }

    /**
     * 载具结构发生变化时重新确定连接的子系统
     */
    public void onVehicleStructureChanged() {
        this.clearCallbackChannel();
    }

    @Override
    public SignalResult onSignalUpdated(String channelName, ISignalSender sender) {
        ISignalReceiver.super.onSignalUpdated(channelName, sender);
        boolean isSignalValid;
        int trueCount = 0;
        outerLoop:
        switch (attr.condition) {
            case AND:
                //全1出1
                isSignalValid = false;
                for (Map.Entry<String, SignalChannel> entry : signalInputChannels.entrySet()) {
                    SignalChannel signalChannel = entry.getValue();
                    for (Object signal : signalChannel.values()) {
                        if (isFalseSignal(signal)) {
                            break outerLoop;
                        }
                    }
                }
                isSignalValid = true;
                break;
            case OR:
                //有1出1
                isSignalValid = false;
                for (Map.Entry<String, SignalChannel> entry : signalInputChannels.entrySet()) {
                    SignalChannel signalChannel = entry.getValue();
                    for (Object signal : signalChannel.values()) {
                        if (isTrueSignal(signal)) {
                            isSignalValid = true;
                            break outerLoop;
                        }
                    }
                }
                break;
            case NAND:
                //全1出0
                isSignalValid = true;
                for (Map.Entry<String, SignalChannel> entry : signalInputChannels.entrySet()) {
                    SignalChannel signalChannel = entry.getValue();
                    for (Object signal : signalChannel.values()) {
                        if (isFalseSignal(signal)) {
                            break outerLoop;
                        }
                    }
                }
                break;
            case NOR:
                //有1出0
                isSignalValid = true;
                for (Map.Entry<String, SignalChannel> entry : signalInputChannels.entrySet()) {
                    SignalChannel signalChannel = entry.getValue();
                    for (Object signal : signalChannel.values()) {
                        if (isTrueSignal(signal)) {
                            isSignalValid = false;
                            break outerLoop;
                        }
                    }
                }
                break;
            case XOR:
                // XOR: 有奇数个真信号时为真
                for (Map.Entry<String, SignalChannel> entry : signalInputChannels.entrySet()) {
                    SignalChannel signalChannel = entry.getValue();
                    for (Object signal : signalChannel.values()) {
                        if (isTrueSignal(signal)) {
                            trueCount++;
                        }
                    }
                }
                isSignalValid = (trueCount % 2 == 1);
                break;
            case XNOR:
                // XNOR: 有偶数个真信号时为真 (包括0个)
                for (Map.Entry<String, SignalChannel> entry : signalInputChannels.entrySet()) {
                    SignalChannel signalChannel = entry.getValue();
                    for (Object signal : signalChannel.values()) {
                        if (isTrueSignal(signal)) {
                            trueCount++;
                        }
                    }
                }
                isSignalValid = (trueCount % 2 == 0);
                break;
            default:
                isSignalValid = true;
        }
        this.enabled = isSignalValid;
        return enabled ? SignalResult.PASS : SignalResult.PASS;
    }


    /**
     * 按 {@code signal_targets} 中的声明顺序逐个尝试接收者，由第一个真正接管交互的目标结束本次交互。
     * <p>
     * 只有 {@link SignalResult#CONSUME} 结束遍历；{@link SignalResult#PASS}（目标不处理）与
     * {@link SignalResult#FAIL}（目标应处理但当前接不了，如座位已被占用）都继续试下一个目标。
     * 同一交互区列出多个座位的场景（一扇门通往车长位与炮手位、后门通往整排载员位）依赖这条规则：
     * 从列表第一个空位开始坐，前排被占也不影响后排。
     * <p>
     * 顺序由 {@link ISignalSender#setTargetFromNames()} 保序写入的接收者表保证，不能依赖 Map 的哈希顺序。
     */
    public void interact(LivingEntity entity) {
        for (Map.Entry<String, Map<String, ISignalReceiver>> entry : targets.entrySet()) {
            String channelName = entry.getKey();
            Map<String, ISignalReceiver> receivers = entry.getValue();
            for (ISignalReceiver receiver : receivers.values()) {
                SignalResult result = sendSignalToTarget(channelName, receiver.getSignalAddress(), new InteractSignal(entity));
                if (result == SignalResult.CONSUME) {
                    return;
                }
            }
        }
    }

    public void destroy() {
        resetSignalOutputs();
        targets.clear();
    }

    // 辅助方法：判断信号是否为真
    private boolean isTrueSignal(Object signal) {
        if (signal instanceof Float) {
            return ((Float) signal) != 0f;
        } else if (signal instanceof Integer) {
            return ((Integer) signal) != 0;
        } else if (signal instanceof Double) {
            return ((Double) signal) != 0.0;
        } else if (signal instanceof Boolean) {
            return (Boolean) signal;
        } else if (signal instanceof EmptySignal) {
            return false;
        }
        return false;
    }

    // 辅助方法：判断信号是否为假
    private boolean isFalseSignal(Object signal) {
        if (signal instanceof Float) {
            return ((Float) signal) == 0f;
        } else if (signal instanceof Boolean) {
            return !(Boolean) signal;
        } else if (signal instanceof EmptySignal) {
            return true;
        }
        return true;
    }

}
