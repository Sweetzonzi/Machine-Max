package io.github.sweetzonzi.machine_max.common.vehicle;

import io.github.sweetzonzi.machine_max.common.vehicle.attr.InteractBoxAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.signal.ISignalReceiver;
import io.github.sweetzonzi.machine_max.common.vehicle.signal.ISignalSender;
import io.github.sweetzonzi.machine_max.common.vehicle.signal.InteractSignal;
import io.github.sweetzonzi.machine_max.common.vehicle.signal.SignalChannel;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractSubsystem;
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
        this.targetNames = attr.signalTargets();
        this.interactMode = InteractMode.valueOf(attr.mode().toUpperCase());
    }

    /**
     * 载具结构发生变化时重新确定连接的子系统
     */
    public void onVehicleStructureChanged() {
        this.clearCallbackChannel();
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

    @Override
    public Part getPart() {
        return subPart.part;
    }

}
