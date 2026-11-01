package io.github.sweetzonzi.machine_max.util;

import org.joml.Quaternionf;

/**
 * 一阶旋转低通：以固定时间常数指数追上目标姿态。
 * <p>
 * 每次 {@link #advance} 用距上次调用实际经过的秒数计算衰减，结果与调用频率无关；
 * {@link #sample} 在上一次与本次结果之间插值，供渲染按刻内比例读取而不推进状态。
 * 时间常数取目标与当前姿态夹角衰减到 1/e 所需的秒数，越小越跟手。
 * <p>
 * 调用线程：客户端主线程推进，渲染阶段在同一线程采样，不做跨线程同步。
 */
public final class RotationDamper {
    /** 单次推进允许的最大时间跨度，避免暂停或卡顿后一次追平所有残差。 */
    private static final double MAX_STEP_SECONDS = .25;

    private final double seconds;
    /** 上一次与本次推进的结果，渲染在这两份姿态之间插值。 */
    private final Quaternionf previous = new Quaternionf();
    private final Quaternionf current = new Quaternionf();
    private long lastNanos;

    /**
     * @param seconds 时间常数（秒），必须为正
     */
    public RotationDamper(double seconds) {
        if (!(seconds > 0)) {
            throw new IllegalArgumentException("seconds must be positive");
        }
        this.seconds = seconds;
    }

    /** 以目标姿态重建状态、清除既有滞后；进入跟随或切换参考体时调用。 */
    public void reset(Quaternionf target, long nowNanos) {
        previous.set(target);
        current.set(target);
        lastNanos = nowNanos;
    }

    /** 推进一次。目标姿态应取自本 tick 的同一采样时刻，不在渲染阶段调用。 */
    public void advance(Quaternionf target, long nowNanos) {
        double elapsed = Math.clamp((nowNanos - lastNanos) / 1e9, 0, MAX_STEP_SECONDS);
        lastNanos = nowNanos;
        previous.set(current);
        current.slerp(target, (float) (1 - Math.exp(-elapsed / seconds)));
    }

    /**
     * 在上一次与本次推进结果之间按刻内比例插值，返回新姿态，不改变内部状态。
     *
     * @param partialTick 刻内比例，超出 0..1 时钳位
     */
    public Quaternionf sample(float partialTick) {
        return new Quaternionf(previous).slerp(current, Math.clamp(partialTick, 0, 1));
    }
}
