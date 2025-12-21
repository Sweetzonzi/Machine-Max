package io.github.sweetzonzi.machine_max.client.render.gui.animation;

import io.github.sweetzonzi.machine_max.util.Easing;
import lombok.Getter;

import java.util.function.Function;

/**
 * 可复用的 float 动效
 * <p>提供平滑的浮点数动画过渡效果，支持自定义缓动函数</p>
 */
public class AnimatedFloat {

    /**
     * 当前动画值
     */
    private float current;
    
    /**
     * 动画起始值
     */
    private float start;
    
    /**
     * 动画目标值
     */
    @Getter
    private float target;

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
     * 构造一个 AnimatedFloat 实例
     *
     * @param initial 初始值，同时也是起始的当前值和目标值
     */
    public AnimatedFloat(float initial) {
        this.current = initial;
        this.start = initial;
        this.target = initial;
    }

    /**
     * 设置目标值并开始动画
     *
     * @param target          动画目标值
     * @param durationSeconds 动画持续时间（秒）
     * @param currentTime     当前时间（秒），通常来自系统时间或游戏刻
     */
    public void animateTo(float target, float durationSeconds, float currentTime) {
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
    public AnimatedFloat easing(Function<Float, Float> easing) {
        this.easing = easing;
        return this;
    }

    /**
     * 更新当前值
     * <p>根据经过的时间和缓动函数计算当前动画值</p>
     *
     * @param currentTime 当前时间（秒）
     * @return 当前动画值
     */
    public float update(float currentTime) {
        float t = (currentTime - startTime) / duration;
        t = Math.min(Math.max(t, 0f), 1f);

        float eased = easing.apply(t);
        current = Easing.lerp(start, target, eased);
        return current;
    }

    /**
     * 获取当前动画值
     *
     * @return 当前动画值
     */
    public float get() {
        return current;
    }

    /**
     * 立即设置值并停止动画
     * <p>将当前值、起始值和目标值都设置为指定值，立即停止正在进行的动画</p>
     *
     * @param value 要设置的值
     */
    public void setImmediate(float value) {
        this.current = value;
        this.start = value;
        this.target = value;
    }
}