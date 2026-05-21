package io.github.sweetzonzi.machine_max.common.mech.signal;

import java.util.Set;
import java.util.concurrent.ConcurrentMap;

public interface ISignalReceiver {
    String getName();

    ConcurrentMap<String, SignalChannel> getSignalInputChannels();

    default SignalResult onSignalUpdated(String channelName, ISignalSender sender) {
        return SignalResult.PASS;
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

    /**
     * 返回此接收者接受处理的全部信号频道集合。
     * 同时涵盖来自现有信号路由（连接器转发）和来自总线广播的信号。
     * <p>
     * 用途：
     *   1. 总线在 initAllSubsystems 时自动读取此集合，建立订阅表。
     *      当 broadcast(channel, value) 时，只转发给声明了该频道的接收者。
     *   2. SignalPort 转发时可检查此集合，实现"接收者声明"维度的过滤。
     *   3. 子系统内部通过 onSignalUpdated(channelName, sender) 中 switch 频道名进行精细分流。
     * <p>
     * 默认返回空集，表示不接受任何频道（既不从总线接收，也不从路由接收）。
     */
    default Set<String> getAcceptedChannels() {
        return Set.of();
    }

    /**
     * 是否接受所有经过连接器路由输入的信号。
     * true = 跳过 getAcceptedChannels() 检查，所有路由信号都写入此接收者的 input channel。
     */
    default boolean acceptAllRoutingInput() {
        return false;
    }

    /**
     * 是否接受所有总线广播输入的信号。
     * true = 跳过订阅表检查，每次 broadcast() 都转发给此接收者。
     */
    default boolean acceptAllBroadcastInput() {
        return false;
    }
}
