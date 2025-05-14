package io.github.tt432.machinemax.common.vehicle;

import io.github.tt432.machinemax.common.vehicle.attr.InteractBoxAttr;
import io.github.tt432.machinemax.common.vehicle.signal.ISignalReceiver;
import io.github.tt432.machinemax.common.vehicle.signal.ISignalSender;
import io.github.tt432.machinemax.common.vehicle.signal.InteractSignal;
import lombok.Getter;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
@Getter
public class InteractBox implements ISignalSender {
    public final String name;
    public final Map<String, List<String>> targetNames;
    public final Map<String, Map<String, ISignalReceiver>> targets = new HashMap<>();//信号频道名->接收者名称->接收者
    public final SubPart subPart;
    public final InteractMode interactMode;

    public enum InteractMode {
        FAST,
        ACCURATE
    }

    public InteractBox(SubPart subPart, String name, InteractBoxAttr attr) {
        this.subPart = subPart;
        this.name = name;
        this.targetNames = attr.signalTargets();
        this.interactMode = InteractMode.valueOf(attr.mode().toUpperCase());
    }

    public void interact(LivingEntity entity){
        for (Map.Entry<String, Map<String, ISignalReceiver>> entry : targets.entrySet()){
            String channelName = entry.getKey();
            sendSignalToAllTargets(channelName, new InteractSignal(entity));
        }
    }

    @Override
    public Part getPart() {
        return subPart.part;
    }

}
