package io.github.sweetzonzi.machine_max.client.render.post;

/**
 * 光源来源的优先级常量：数值越小越优先。
 *
 * <p>容量表按 {@code (优先级升序, 投影半径降序)} 保留候选，因此车灯优先于火光、火光优先于曳光。
 * 优先级不是保留名额：车灯占满容量时火光与曳光会被全部排除（设计文档 §5.2）。</p>
 *
 * <p>优先级由来源实例显式声明（{@link ScreenSpaceLightSource#priority()}），不由枚举声明顺序决定；
 * 同一来源需要两种优先级时拆成两个来源实例。本类只提供常量，不持有状态。</p>
 */
public final class ScreenSpaceLightPriority {

    /** 载具车灯（P4/P5 接入） */
    public static final int VEHICLE_LIGHT = 0;
    /** 爆炸火光（P2 接入） */
    public static final int EXPLOSION_LIGHT = 10;
    /** 投射物曳光 */
    public static final int PROJECTILE_TRACER = 20;

    private ScreenSpaceLightPriority() {
    }
}
