package io.github.sweetzonzi.machine_max.common.vehicle.energy;

import java.util.HashMap;
import java.util.Map;

public interface IMechEnergyProducer {
    String getName();

    Map<String, IMechEnergyConsumer> getEnergyTargets();

    void rebuildEnergyTargets();

    default Map<String, Float> collectFeedbackSpeeds() {
        Map<String, Float> result = new HashMap<>();
        for (var entry : getEnergyTargets().entrySet()) {
            var consumer = entry.getValue();
            if (consumer != null && consumer.isEnergyPathConnected(getName())) {
                result.put(entry.getKey(), consumer.getFeedbackSpeed());
            }
        }
        return result;
    }

    default void pushMechEnergy(MechPower power) {
        for (var entry : getEnergyTargets().entrySet()) {
            pushMechEnergy(entry.getKey(), power);
        }
    }

    default void pushMechEnergy(String targetName, MechPower power) {
        var consumer = getEnergyTargets().get(targetName);
        if (consumer != null) {
            consumer.onMechEnergyReceived(getName(), power);
        }
    }

    default void pushMechEnergy(Map<String, MechPower> powers) {
        for (var entry : powers.entrySet()) {
            pushMechEnergy(entry.getKey(), entry.getValue());
        }
    }
}
