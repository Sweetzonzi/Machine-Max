package io.github.tt432.machinemax.common.vehicle.signal;

import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

public interface ISignalReceiver {
    String getName();

    ConcurrentMap<String, Signals> getSignalInputs();

    default void onSignalUpdated(String signalKey, ISignalSender sender) {
    }

    default Signals getSignals(String signalKey) {
        return getSignalInputs().computeIfAbsent(signalKey, k -> new Signals());
    }

    default void clearCallbackSignals() {
        if (getSignalInputs().containsKey("callback")) getSignalInputs().get("callback").clear();
        if (getSignalInputs().containsKey("speed_feedback")) getSignalInputs().get("speed_feedback").clear();
    }
}
