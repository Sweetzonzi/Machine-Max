package io.github.sweetzonzi.machine_max.common.mech.signal;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 载具级信号总线接口。
 * <p>
 * 同时继承 ISignalReceiver 和 ISignalSender：
 * <ul>
 *   <li>作为 ISignalReceiver：可从现有信号路由接收信号（通过 "global" 目标名）</li>
 *   <li>作为 ISignalSender：可被其他组件列为目标，允许回复/回调</li>
 *   <li>broadcast()：将信号转发给所有订阅者中接受此频道的接收者，保留原始发送者身份</li>
 * </ul>
 * <p>
 * 广播由 {@link ISignalReceiver#onSignalUpdated} 直接处理，不再有回调转发。
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

    /**
     * 把一次事件分发给总线自己与接受该频道的订阅者。与 {@link #broadcast} 并列：那条写输入槽，
     * 这条什么都不写。
     * <p>
     * 总线自身先收一次（{@link ISignalReceiver#onSignalEvent}），再按
     * {@link ISignalReceiver#acceptAllBroadcastInput()} 与 {@link ISignalReceiver#getAcceptedChannels()}
     * 筛选订阅者——与值车道的接受条件完全相同，声明一次即可。事件没有消费语义，因此没有返回值，
     * 也不因为某个接收者先收到而停止分发。
     * <p>
     * 订阅者本身是总线时递归分发，是端口时按对侧表继续转发；重访判定由投递会话负责，环路与
     * 菱形接线不会重复投递。本方法自成一个顶层入口，没有外层 emit 时也会从空会话开始。
     *
     * @param channel 到达频道
     * @param sender  原始发送者
     * @param payload 事件负载，可为 {@code null}
     */
    default void broadcastEvent(String channel, ISignalSender sender, Object payload) {
        EventDispatch session = EventDispatch.current();
        session.begin();
        try {
            onSignalEvent(channel, sender, payload);
            for (ISignalReceiver sub : getAllSubscribers()) {
                if (sub.acceptAllBroadcastInput() || sub.getAcceptedChannels().contains(channel)) {
                    EventDispatch.dispatch(sub, channel, sender, payload);
                }
            }
        } finally {
            session.end();
        }
    }

    /** 注册接收者为订阅者 */
    void subscribe(ISignalReceiver subscriber);

    /** 取消接收者的订阅 */
    void unsubscribe(ISignalReceiver subscriber);

    /**
     * ISignalBus 使用 broadcast() 广播信号，不依赖 ISignalSender 的按名称路由机制，
     * 因此 getTargets() 和 getTargetNames() 返回空映射。
     */
    @Override
    default Map<String, Map<String, ISignalReceiver>> getTargets(){
        return Map.of();
    }

    @Override
    default Map<String, List<String>> getTargetNames() {
        return Map.of();
    }
}
