package io.github.sweetzonzi.machine_max.common.mech.signal;


import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

public interface ISignalReceiver {
    /**
     * 返回此接收者在信号路由中的寻址名（目标名）。
     * <p>与身份名区分：身份名仅用于日志 / 翻译，不参与任何路由；路由一律使用本方法。
     * 保留地址：{@code "local"}（RegularPart）、{@code "global"}（装配体）。</p>
     */
    String getSignalAddress();

    ConcurrentMap<String, SignalChannel> getSignalInputChannels();

    /**
     * 返回此接收者的信号存储表，供 Molang 的 {@code get / get_str} 查询。
     * <p>无存储的接收者返回 {@code null}，发送端只做一次判空。</p>
     */
    default Map<String, Object> getSignalStorage() {
        return null;
    }

    default SignalResult onSignalUpdated(String channelName, ISignalSender sender) {
        return SignalResult.PASS;
    }

    /**
     * 事件到达。与 {@link #onSignalUpdated} 并列：一个说"这个来源的值变了"，一个说"刚刚发生了一次"。
     * <p>
     * 不写输入槽、不写存储表、没有返回值——事件没有消费语义，也没有排序与释放。同一条频道重复
     * 投递相同负载时每次都到达，去重只发生在投递会话的重访判定（{@code (接收者, 到达频道)}）上，
     * 与值无关。
     * <p>
     * <b>调用线程：</b>发送方的线程。本方法不做线程切换，需要主线程的实现由调用方保证；
     * 按名投递限主线程，物理步只按实例投递给不触碰动画状态机的接收者。
     *
     * @param channelName 到达频道，同时也是动画状态机的事件名（总线实现按此转交给控制器）
     * @param sender      原始发送者
     * @param payload     负载；纯触发传 {@code null}
     */
    default void onSignalEvent(String channelName, ISignalSender sender, Object payload) {
    }

    default SignalChannel getSignalChannel(String channelName) {
        return getSignalInputChannels().computeIfAbsent(channelName, k -> new SignalChannel());
    }

    default Object getSignalValueFrom(String channelName, ISignalSender sender) {
        return getSignalChannel(channelName).get(sender);
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

}
