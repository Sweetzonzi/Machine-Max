package io.github.sweetzonzi.machine_max.util;

/**
 * 常用缓动函数集合
 * 输入/输出范围均为 [0, 1]
 */
public class Easing {

    /**
     * 线性缓动函数
     * <p>返回与输入相同的值，产生恒定速度的动画效果</p>
     *
     * @param t 时间参数，取值范围 [0, 1]，0 表示开始，1 表示结束
     * @return 线性插值结果，与输入值相同
     */
    public static float linear(float t) {
        return t;
    }

    /**
     * 渐入缓动函数 (Ease In)
     * <p>动画开始时较慢，然后加速</p>
     *
     * @param t 时间参数，取值范围 [0, 1]，0 表示开始，1 表示结束
     * @return 缓动插值结果，在 [0, 1] 范围内
     */
    public static float easeIn(float t) {
        return t * t;
    }

    /**
     * 渐出缓动函数 (Ease Out)
     * <p>动画开始时较快，然后减速</p>
     *
     * @param t 时间参数，取值范围 [0, 1]，0 表示开始，1 表示结束
     * @return 缓动插值结果，在 [0, 1] 范围内
     */
    public static float easeOut(float t) {
        return 1f - (1f - t) * (1f - t);
    }

    /**
     * 渐入渐出缓动函数 (Ease In Out)
     * <p>动画开始时缓慢加速，中间加速最快，结束前减速</p>
     *
     * @param t 时间参数，取值范围 [0, 1]，0 表示开始，1 表示结束
     * @return 缓动插值结果，在 [0, 1] 范围内
     */
    public static float easeInOut(float t) {
        return t < 0.5f
                ? 2f * t * t
                : 1f - (float)Math.pow(-2f * t + 2f, 2) / 2f;
    }

    /**
     * 三次渐出缓动函数 (Ease Out Cubic)
     * <p>使用三次方函数实现的渐出效果，动画结束前减速更加明显</p>
     *
     * @param t 时间参数，取值范围 [0, 1]，0 表示开始，1 表示结束
     * @return 缓动插值结果，在 [0, 1] 范围内
     */
    public static float easeOutCubic(float t) {
        return 1f - (float)Math.pow(1f - t, 3);
    }

    /**
     * 超调缓动函数 (Overshoot)
     * <p>动画会在目标位置附近超调一下再回到最终位置，产生弹性效果</p>
     *
     * @param t 时间参数，取值范围 [0, 1]，0 表示开始，1 表示结束
     * @return 缓动插值结果，可能会略微超过 [0, 1] 范围
     */
    public static float overshoot(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        return 1f + c3 * (float)Math.pow(t - 1f, 3)
                + c1 * (float)Math.pow(t - 1f, 2);
    }

    /**
     * 线性插值函数
     * <p>在两个浮点数之间进行线性插值</p>
     *
     * @param a 起始值
     * @param b 目标值
     * @param t 插值系数，取值范围 [0, 1]，0 返回 a，1 返回 b
     * @return 在 a 和 b 之间的插值结果
     */
    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /**
     * 颜色线性插值函数
     * <p>在两个ARGB颜色值之间进行线性插值，包括透明度通道</p>
     *
     * @param c1 起始颜色值 (ARGB格式)
     * @param c2 目标颜色值 (ARGB格式)
     * @param t  插值系数，取值范围 [0, 1]，0 返回 c1，1 返回 c2
     * @return 在 c1 和 c2 之间的插值颜色值 (ARGB格式)
     */
    public static int lerpColor(int c1, int c2, float t) {
        int a1 = (c1 >>> 24) & 0xFF;
        int r1 = (c1 >>> 16) & 0xFF;
        int g1 = (c1 >>> 8) & 0xFF;
        int b1 = c1 & 0xFF;

        int a2 = (c2 >>> 24) & 0xFF;
        int r2 = (c2 >>> 16) & 0xFF;
        int g2 = (c2 >>> 8) & 0xFF;
        int b2 = c2 & 0xFF;

        int a = (int)(a1 + (a2 - a1) * t);
        int r = (int)(r1 + (r2 - r1) * t);
        int g = (int)(g1 + (g2 - g1) * t);
        int b = (int)(b1 + (b2 - b1) * t);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * 颜色线性插值函数
     * <p>将一个ARGB颜色值由透明度为 0 到 1 进行线性插值</p>
     * @param c 目标颜色值 (ARGB格式)
     * @param t  插值系数，取值范围 [0, 1]，0 返回 c1，1 返回 c
     * return 透明度为 0 到 1 之间的插值颜色值 (ARGB格式)
     */
    public static int lerpColorFromTransparent(int c, float t) {
        int a2 = (c >>> 24) & 0xFF;
        int r2 = (c >>> 16) & 0xFF;
        int g2 = (c >>> 8) & 0xFF;
        int b2 = c & 0xFF;
        int a = (int)(a2 * t);
        return (a << 24) | (r2 << 16) | (g2 << 8) | b2;
    }
}