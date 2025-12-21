package io.github.sweetzonzi.machine_max.client.gui.animation;

import io.github.sweetzonzi.machine_max.util.Easing;

import java.util.function.Function;

/**
 * 可复用的颜色动画类
 * <p>提供平滑的颜色过渡动画效果，支持自定义缓动函数</p>
 */
public class AnimatedColor {

    /**
     * 当前动画颜色值 (ARGB格式)
     */
    private int current;
    
    /**
     * 动画起始颜色值 (ARGB格式)
     */
    private int start;
    
    /**
     * 动画目标颜色值 (ARGB格式)
     */
    private int target;

    /**
     * 动画开始时间
     */
    private float startTime;
    
    /**
     * 动画持续时间（秒）
     */
    private float duration;

    /**
     * 缓动函数，默认为线性缓动
     */
    private Function<Float, Float> easing = Easing::linear;

    /**
     * 构造一个 AnimatedColor 实例
     *
     * @param initial 初始颜色值 (ARGB格式)，同时也是起始的当前值和目标值
     */
    public AnimatedColor(int initial) {
        this.current = initial;
        this.start = initial;
        this.target = initial;
    }

    /**
     * 设置目标颜色并开始动画
     *
     * @param target          动画目标颜色值 (ARGB格式)
     * @param durationSeconds 动画持续时间（秒）
     * @param currentTime     当前时间（秒），通常来自系统时间或游戏刻
     */
    public void animateTo(int target, float durationSeconds, float currentTime) {
        this.start = this.current;
        this.target = target;
        this.duration = Math.max(0.0001f, durationSeconds);
        this.startTime = currentTime;
    }

    /**
     * 设置缓动函数
     *
     * @param easing 缓动函数，接受 [0,1] 区间的浮点数并返回变换后的值
     * @return 当前实例，支持链式调用
     */
    public AnimatedColor easing(Function<Float, Float> easing) {
        this.easing = easing;
        return this;
    }

    /**
     * 更新当前颜色值
     * <p>根据经过的时间和缓动函数计算当前动画颜色值</p>
     *
     * @param currentTime 当前时间（秒）
     * @return 当前动画颜色值 (ARGB格式)
     */
    public int update(float currentTime) {
        float t = (currentTime - startTime) / duration;
        t = Math.min(Math.max(t, 0f), 1f);

        float eased = easing.apply(t);
        current = Easing.lerpColor(start, target, eased);
        return current;
    }

    /**
     * 获取当前动画颜色值
     *
     * @return 当前动画颜色值 (ARGB格式)
     */
    public int get() {
        return current;
    }

    /**
     * 立即设置颜色值并停止动画
     * <p>将当前值、起始值和目标值都设置为指定颜色值，立即停止正在进行的动画</p>
     *
     * @param value 要设置的颜色值 (ARGB格式)
     */
    public void setImmediate(int value) {
        this.current = value;
        this.start = value;
        this.target = value;
    }
}