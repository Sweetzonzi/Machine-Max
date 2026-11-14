package io.github.sweetzonzi.machine_max.common.mech.signal;

import lombok.Getter;

/**
 * 信号基类：一个值加一个越级量。<br>
 * 越级量只由发送方在自己的出口处写入——座位按控制组逐频道取数（{@code ControlGroup#priorities}），
 * 再用 {@link #withPriority(int)} 复制一份带数的信号投递。0 表示不越级；大于 0 的信号排在所有
 * 未越级信号之前，数值之间决定先后。它不进入频道名、不参与持久化、不改变频道键。
 */
@Getter
public abstract class Signal<T> {
    public final T value;

    /** 越级量：0 = 不越级。只影响接收者读取时的排序，不与频道档位相加。 */
    public final int priority;

    public Signal(T value) {
        this(value, 0);
    }

    public Signal(T value, int priority) {
        this.value = value;
        this.priority = priority;
    }

    /**
     * 返回内容相同、越级量为 {@code priority} 的信号；数值与自身相同时返回 {@code this}，不产生副本。<br>
     * 返回类型写成自身类型（协变），出口处逐频道调用时不必转型。
     */
    public abstract Signal<T> withPriority(int priority);

    /**
     * 频道值携带的越级量：不是 {@link Signal} 的值（按键绑定与 GUI 动作投递的裸数值）按 0 参与排序。
     *
     * @param value 频道里的信号值
     * @return 越级量；非 {@link Signal} 值一律为 0
     */
    public static int priorityOf(Object value) {
        return value instanceof Signal<?> signal ? signal.priority : 0;
    }
}
