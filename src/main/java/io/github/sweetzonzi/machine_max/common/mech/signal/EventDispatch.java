package io.github.sweetzonzi.machine_max.common.mech.signal;

import io.github.sweetzonzi.machine_max.MachineMax;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 一次事件投递的会话：记录已经投递过的 {@code (接收者, 到达频道)} 组合，重复到达时跳过。
 *
 * <p>值车道靠"值不变就不转发"终止，事件车道没有值可比较，因此用重访检测终止环路。键的语义与
 * 只读发现的 visited 相同：同一接收者在同一到达频道上只投一次，经不同到达频道到达时各投一次。
 *
 * <p>会话按线程常驻、每次顶层 emit 清空：{@link #begin()} 把深度加一，由 0 变 1 时清表；
 * {@link #end()} 在 {@code finally} 里减一，归零时再清一次。容器本身留在线程上复用，因此每次
 * emit 只做清表、不新建映射。嵌套 emit（接收者在回调里发出另一条频道）深度大于 1，共用同一份
 * visited：另一条频道照常投递，把同一频道发回上游则被抑制。回调抛异常时 {@code finally} 仍会
 * 减深度，下一次顶层 emit 从空表开始，脏数据不跨 emit 存活。
 *
 * <p>{@link #current()} 按需创建会话，因此 {@link ISignalBus#broadcastEvent} 这类入口即使没有外层
 * emit 也不会拿到 {@code null}。
 *
 * <p>投递上界是一次 emit 触及的「接收者 × 到达频道」。到达频道来自翻译表（有限静态映射）与调用方
 * 传入的字符串，一条频道能派生出的频道名因此有限，任何环路都会在有限步内重复同一个组合，
 * 所以不需要额外的深度上限。
 *
 * <p><b>线程：</b>会话挂在线程上、不跨线程共享；回调在调用方线程上同步执行。按名 emit 与状态机
 * 事件的线程约定见 {@link ISignalSender#emitSignal(String, Object)}。
 */
final class EventDispatch {

    private static final ThreadLocal<EventDispatch> CURRENT = new ThreadLocal<>();

    private final Map<ISignalReceiver, Set<String>> visited = new IdentityHashMap<>();
    private int depth;

    private EventDispatch() {
    }

    /** 当前线程的投递会话；没有外层 emit 时按需创建。 */
    static EventDispatch current() {
        EventDispatch session = CURRENT.get();
        if (session == null) {
            session = new EventDispatch();
            CURRENT.set(session);
        }
        return session;
    }

    /**
     * 把一次事件投递给一个接收者：首次到达时按角色分派，重访直接返回。
     * <p>
     * 总线先收下再分发给订阅者（{@link ISignalBus#broadcastEvent}）；端口与叶子走各自的
     * {@link ISignalReceiver#onSignalEvent}——端口的事件转发是它自己的覆写，分派不需要按类型分支。
     */
    static void dispatch(ISignalReceiver target, String channel, ISignalSender sender, Object payload) {
        if (!current().visit(target, channel)) {
            MachineMax.LOGGER.debug("事件重访已抑制：接收者 {} 频道 {}（发送者 {}）",
                    target.getSignalAddress(), channel,
                    sender instanceof ISignalReceiver receiver ? receiver.getSignalAddress() : sender.getClass().getSimpleName());
            return;
        }
        if (target instanceof ISignalBus bus) bus.broadcastEvent(channel, sender, payload);
        else target.onSignalEvent(channel, sender, payload);
    }

    /** 首次到达返回 {@code true}；同一 {@code (接收者, 频道)} 的第二次到达返回 {@code false}。 */
    boolean visit(ISignalReceiver receiver, String channel) {
        return visited.computeIfAbsent(receiver, k -> new HashSet<>(2)).add(channel);
    }

    /** 顶层 emit 开始时清表；嵌套 emit 只加深深度。调用点包在 {@code try/finally} 里。 */
    void begin() {
        if (depth++ == 0) visited.clear();
    }

    /** 与 {@link #begin()} 配对；深度归零时清表，脏数据不跨 emit 存活。 */
    void end() {
        if (--depth == 0) visited.clear();
    }
}
