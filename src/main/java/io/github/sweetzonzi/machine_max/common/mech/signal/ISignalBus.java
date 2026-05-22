package io.github.sweetzonzi.machine_max.common.mech.signal;

import java.util.Set;

/**
 * 载具级信号总线接口。
 * <p>
 * 同时继承 ISignalReceiver 和 ISignalSender：
 * <ul>
 *   <li>作为 ISignalReceiver：可从现有信号路由接收信号（通过 "vehicle" 目标名）</li>
 *   <li>作为 ISignalSender：可被其他组件列为目标，允许回复/回调</li>
 *   <li>broadcast()：将信号转发给所有订阅了指定频道的子系统，保留原始发送者身份</li>
 * </ul>
 * <p>
 * 订阅关系由总线自动管理：子系统在初始化时，总线读取其 {@link ISignalReceiver#getAcceptedChannels()}
 * 并自动注册订阅。也可通过 {@link #acceptAllBroadcastInput()} 通配接收所有频道。
 */
public interface ISignalBus extends ISignalReceiver, ISignalSender {

    /**
     * 将信号广播到所有订阅了此频道的子系统。
     * 默认实现使用 {@link #getSubscribers(String)} 获取精确匹配的订阅者，
     * 以及 {@link #getWildcardBroadcastSubscribers()} 获取通配订阅者，
     * 将信号写入每个订阅者的 signalInputChannels 并触发 onSignalUpdated。
     *
     * @param channel        信号频道名
     * @param value          信号值
     * @param originalSender 原始发送者（保留身份，使接收方能区分信号来源）
     */
    default void broadcast(String channel, Object value, ISignalSender originalSender) {
        for (ISignalReceiver sub : getSubscribers(channel)) {
            writeSignalAndNotify(sub, channel, value, originalSender);
        }
        for (ISignalReceiver sub : getWildcardBroadcastSubscribers()) {
            writeSignalAndNotify(sub, channel, value, originalSender);
        }
    }

    /**
     * 将信号写入接收者的输入频道并通知。
     */
    private static void writeSignalAndNotify(ISignalReceiver sub, String channel, Object value, ISignalSender sender) {
        sub.getSignalInputChannels()
                .computeIfAbsent(channel, k -> new SignalChannel())
                .put(sender, value);
        sub.onSignalUpdated(channel, sender);
    }

    /**
     * 获取接受所有广播信号的通配订阅者集合。
     * 默认返回空集，实现类若有通配订阅者可覆盖此方法。
     */
    default Set<ISignalReceiver> getWildcardBroadcastSubscribers() {
        return Set.of();
    }

    /**
     * 注册接收者对指定频道的订阅。
     * 通常在子系统初始化时由总线自动调用。
     */
    void subscribe(ISignalReceiver subscriber, String channel);

    /**
     * 取消接收者对指定频道的订阅。
     */
    void unsubscribe(ISignalReceiver subscriber, String channel);

    /**
     * 取消接收者的所有频道订阅。
     * 通常在子系统 onDetach 时由总线自动调用。
     */
    void unsubscribeAll(ISignalReceiver subscriber);

    /** 获取指定频道的所有订阅者 */
    Set<ISignalReceiver> getSubscribers(String channel);

    /** 获取接收者订阅的所有频道 */
    Set<String> getSubscriptions(ISignalReceiver subscriber);
}
