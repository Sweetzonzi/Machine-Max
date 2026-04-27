package io.github.sweetzonzi.machine_max.common.vehicle.energy;

import java.util.HashMap;
import java.util.Map;

public interface IMechPowerProducer {
    String getName();

    Map<String, IMechPowerConsumer> getMechPowerTargets();

    void rebuildMechPowerTargets();

    default Map<String, Float> collectFeedbackSpeeds() {
        Map<String, Float> result = new HashMap<>();
        for (var entry : getMechPowerTargets().entrySet()) {
            var consumer = entry.getValue();
            if (consumer != null && consumer.isPowerPathConnected(getName())) {
                result.put(entry.getKey(), consumer.getFeedbackSpeed());
            }
        }
        return result;
    }

    default void pushMechPower(MechPower power) {
        for (var entry : getMechPowerTargets().entrySet()) {
            pushMechPower(entry.getKey(), power);
        }
    }

    default void pushMechPower(String targetName, MechPower power) {
        var consumer = getMechPowerTargets().get(targetName);
        if (consumer != null) {
            consumer.onMechPowerReceived(getName(), power);
        }
    }

    default void pushMechPower(Map<String, MechPower> powers) {
        for (var entry : powers.entrySet()) {
            pushMechPower(entry.getKey(), entry.getValue());
        }
    }
}
