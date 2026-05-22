package io.github.sweetzonzi.machine_max.common.mech.signal;

import java.util.Set;

/**
 * 载具级信号总线接口。
 * <p>
 * 同时继承 ISignalReceiver 和 ISignalSender：
 * <ul>
 *   <li>作为 ISignalReceiver：可从现有信号路由接收信号（通过 "vehicle" 目标名）</li>
 *   <li>作为 ISignalSender：可被其他组件列为目标，允许回复/回调</li>
 *   <li>broadcast()：将信号转发给所有订阅者中接受此频道的接收者，保留原始发送者身份</li>
 * </ul>
 * <p>
 * 订阅由总线自动管理：子系统初始化时自动注册为订阅者。
 * broadcast() 在运行时检查每个订阅者的 {@link ISignalReceiver#acceptAllBroadcastInput()}
 * 和 {@link ISignalReceiver#getAcceptedChannels()}，决定是否转发。
 */
public interface ISignalBus extends ISignalReceiver, ISignalSender {

    /**
     * 将信号广播给所有接受此频道的订阅者。
     * 遍历所有订阅者，检查其声明的接受条件后决定是否转发。
     *
     * @param channel        信号频道名
     * @param value          信号值
     * @param originalSender 原始发送者（保留身份，使接收方能区分信号来源）
     */
    default void broadcast(String channel, Object value, ISignalSender originalSender) {
        for (ISignalReceiver sub : getAllSubscribers()) {
            if (sub.acceptAllBroadcastInput() || sub.getAcceptedChannels().contains(channel)) {
                sub.getSignalInputChannels()
                        .computeIfAbsent(channel, k -> new SignalChannel())
                        .put(originalSender, value);
                sub.onSignalUpdated(channel, originalSender);
            }
        }
    }

    /** 获取所有已注册的订阅者 */
    Set<ISignalReceiver> getAllSubscribers();

    /** 注册接收者为订阅者 */
    void subscribe(ISignalReceiver subscriber);

    /** 取消接收者的订阅 */
    void unsubscribe(ISignalReceiver subscriber);
}
