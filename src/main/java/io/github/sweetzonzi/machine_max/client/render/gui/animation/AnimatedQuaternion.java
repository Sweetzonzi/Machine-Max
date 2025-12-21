package io.github.sweetzonzi.machine_max.client.render.gui.animation;

import io.github.sweetzonzi.machine_max.util.Easing;
import lombok.Getter;
import org.joml.Quaternionf;

import java.util.function.Function;

/**
 * 使用 SLERP 的四元数旋转动画
 * 适用于相机 / 3D HUD / 物品 / UI 面板旋转
 */
public class AnimatedQuaternion {

    /** 当前旋转 */
    private final Quaternionf current = new Quaternionf();

    /** 动画起始旋转 */
    private final Quaternionf start = new Quaternionf();

    /** 动画目标旋转 */
    @Getter
    private final Quaternionf target = new Quaternionf();

    /** 动画开始时间（秒） */
    private float startTime;

    /** 动画持续时间（秒） */
    private float duration;

    /** 缓动函数（作用于插值参数 t） */
    private Function<Float, Float> easing = Easing::linear;

    public AnimatedQuaternion(Quaternionf initial) {
        this.current.set(initial);
        this.start.set(initial);
        this.target.set(initial);
    }

    /**
     * 立即设置旋转（不经过动画）
     */
    public void setImmediate(Quaternionf rotation) {
        this.current.set(rotation);
        this.start.set(rotation);
        this.target.set(rotation);
    }

    /**
     * 启动旋转动画
     *
     * @param targetRotation 目标旋转
     * @param durationSec    动画时长（秒）
     * @param currentTime    当前时间（来自 TimeSource）
     */
    public void animateTo(
            Quaternionf targetRotation,
            float durationSec,
            float currentTime
    ) {
        this.start.set(this.current);
        this.target.set(targetRotation);
        this.duration = Math.max(0.0001f, durationSec);
        this.startTime = currentTime;
    }

    /**
     * 设置缓动函数
     */
    public AnimatedQuaternion easing(Function<Float, Float> easing) {
        this.easing = easing;
        return this;
    }

    /**
     * 更新当前旋转
     *
     * @return 当前旋转（注意：返回的是内部引用，请勿修改）
     */
    public Quaternionf update(float currentTime) {
        float t = (currentTime - startTime) / duration;
        t = Math.min(Math.max(t, 0f), 1f);

        float eased = easing.apply(t);

        // SLERP：从 start → target 插值到 current
        start.slerp(target, eased, current);

        return current;
    }

    /**
     * 获取当前旋转（只读）
     */
    public Quaternionf get() {
        return current;
    }
}

