package io.github.sweetzonzi.machine_max.common.vehicle.interact;

import io.github.sweetzonzi.machine_max.common.vehicle.Part;
import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.InteractBoxAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.signal.*;
import lombok.Getter;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
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
    public final Map<String, Map<String, ISignalReceiver>> targets = new HashMap<>();//信号频道名->接收者名称->接收者
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

    /**
     * 载具结构发生变化时重新确定连接的子系统
     */
    public void onVehicleStructureChanged() {
        this.clearCallbackChannel();
    }

    @Override
    public void onSignalUpdated(String channelName, ISignalSender sender) {
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
    }


    //TODO:回调？
    public void interact(LivingEntity entity) {
        for (Map.Entry<String, Map<String, ISignalReceiver>> entry : targets.entrySet()) {
            String channelName = entry.getKey();
            sendSignalToAllTargets(channelName, new InteractSignal(entity));
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
