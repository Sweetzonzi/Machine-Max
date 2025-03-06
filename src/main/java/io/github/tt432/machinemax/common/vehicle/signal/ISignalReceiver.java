package io.github.tt432.machinemax.common.vehicle.signal;

import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

public interface ISignalReceiver {
    ConcurrentMap<String, Signals> getSignalInputs();

    default void onSignalUpdated(String signalKey, Object signalValue){}

    default Signals getSignals(String signalKey){
        return getSignalInputs().getOrDefault(signalKey, new Signals());
    }
}
