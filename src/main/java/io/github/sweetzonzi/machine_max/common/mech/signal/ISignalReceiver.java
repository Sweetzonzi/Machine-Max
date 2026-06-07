package io.github.sweetzonzi.machine_max.common.mech.signal;

import io.github.sweetzonzi.machine_max.MachineMax;

import java.util.HashSet;
import java.util.List;
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
     * 返回此接收者接受处理的全部信号频道列表。
     * 同时涵盖来自现有信号路由（连接器转发）和来自总线广播的信号。
     * <p>
     * 用途：
     *   1. 总线自动读取此列表，broadcast() 时只转发给声明了该频道的接收者。
     *   2. SignalPort 转发时可检查此列表，实现"接收者声明"维度的过滤。
     *   3. 子系统内部通过 onSignalUpdated(channelName, sender) 中 switch 频道名进行精细分流。
     * <p>
     * 列表顺序表示优先级（靠前的频道优先处理），这也是为何采用 {@link List} 而非 {@link java.util.Set}。
     * 默认返回空列表，表示不接受任何频道。
     */
    default List<String> getAcceptedChannels() {
        return List.of();
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

    // ===== 回调机制 =====

    /**
     * 防止 respondCallbackToSender → sendCallbackToListener → onSignalUpdated → … 形成循环
     */
    ThreadLocal<Set<ISignalSender>> CALLBACK_GUARD = ThreadLocal.withInitial(HashSet::new);

    /**
     * 接收到信号后、onSignalUpdated 之前，按需向发送者回传 callback。
     * <p>
     * 默认行为：若 requiresImmediateCallback=true 且发送者/接收者均为有效的
     * ISignalReceiver/ISignalSender，则通过 sendCallbackToListener 向发送者回传 "callback"。
     * <p>
     * 覆写规则：
     * <ul>
     *   <li>普通子系统：不覆写，默认自动回传</li>
     *   <li>SignalPort：覆写为空（端口仅是信道，不应作为终端发声）</li>
     *   <li>SubsystemController：覆写，转发到所有订阅者</li>
     * </ul>
     *
     * @param channelName              信号频道名
     * @param sender                   原始发送者
     * @param value                    信号值
     * @param requiresImmediateCallback 是否需要即时回传 callback
     * @param callbackReturnsSignalValue true: 回传信号值，false: 回传信号频道名
     */
    default void respondCallbackToSender(
            String channelName, ISignalSender sender, Object value,
            boolean requiresImmediateCallback, boolean callbackReturnsSignalValue) {
        if (!requiresImmediateCallback) return;

        // 不是 ISignalSender 的接收者无法回传 callback，也不需要循环防护
        if (!(this instanceof ISignalSender cbSender)) return;

        // 循环防护：同一线程中同一接收者被二次要求回传时跳过
        Set<ISignalSender> guard = CALLBACK_GUARD.get();
        if (!guard.add((ISignalSender) this)) {
            MachineMax.LOGGER.error(
                    "信号回调循环触发！channel={}, sender={}, receiver={}, guard={}。" +
                            "同一调用链中 {} 被重复要求回传 callback，跳过以避免栈溢出。" +
                            "当前调用栈如下（供定位循环路径）：",
                    channelName, sender.getClass().getSimpleName(), this.getClass().getSimpleName(),
                    guard.stream().map(Object::getClass).map(Class::getSimpleName).toList(),
                    this.getClass().getSimpleName(),
                    new Exception("回调循环调用栈"));
            return;
        }
        try {
            if (sender instanceof ISignalReceiver cbTarget) {
                Object cbValue = callbackReturnsSignalValue ? value : channelName;
                cbSender.sendCallbackToListener("callback", cbTarget, cbValue);
            }
        } finally {
            guard.remove((ISignalSender) this);
        }
    }
}
