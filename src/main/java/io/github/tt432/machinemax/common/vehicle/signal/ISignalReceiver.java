package io.github.tt432.machinemax.common.vehicle.signal;

import java.util.concurrent.ConcurrentMap;

public interface ISignalReceiver {
    String getName();

    ConcurrentMap<String, SignalChannel> getSignalInputChannels();

    default void onSignalUpdated(String channelName, ISignalSender sender) {
    }

    default SignalChannel getSignalChannel(String channelName) {
        return getSignalInputChannels().computeIfAbsent(channelName, k -> new SignalChannel());
    }

    default Object getSignalValueFrom(String channelName, ISignalSender sender) {
        return getSignalChannel(channelName).get(sender);
    }

    default void clearCallbackChannel() {
        if (getSignalInputChannels().containsKey("callback")) getSignalInputChannels().get("callback").clear();
        if (getSignalInputChannels().containsKey("speed_feedback")) getSignalInputChannels().get("speed_feedback").clear();
    }
}
