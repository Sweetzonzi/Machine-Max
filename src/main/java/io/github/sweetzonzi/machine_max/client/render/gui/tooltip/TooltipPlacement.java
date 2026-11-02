package io.github.sweetzonzi.machine_max.client.render.gui.tooltip;

/**
 * tooltip 面板的摆放算法。
 *
 * <p>与 AUI 解耦的纯计算：输入输出都是**文档坐标**（CSS 像素）。渲染器负责把 Minecraft 的
 * GUI 坐标换算成文档坐标后再调用这里。</p>
 *
 * <p>摆放优先级与原版一致：光标右侧 → 光标左侧 → 两侧都放不下时在可用区间内滑动。
 * 最后一档必须是滑动而不是固定贴左边距，否则面板会看起来“完全不跟随光标”。</p>
 */
final class TooltipPlacement {

    private TooltipPlacement() {
    }

    /**
     * @param mouseX   光标横坐标（文档坐标）
     * @param mouseY   光标纵坐标（文档坐标）
     * @param viewportW 文档视口宽
     * @param viewportH 文档视口高
     * @param width    面板宽
     * @param height   面板高
     * @param offsetX  水平偏移（正数落在光标右侧）
     * @param offsetY  垂直偏移（正数落在光标上方）
     * @param margin   贴边保留距离
     * @return {@code {left, top}}
     */
    static double[] place(double mouseX, double mouseY,
                          double viewportW, double viewportH,
                          double width, double height,
                          double offsetX, double offsetY, double margin) {
        double minLeft = margin;
        double maxLeft = Math.max(margin, viewportW - margin - width);
        double right = mouseX + offsetX;
        double leftSide = mouseX - offsetX - width;
        double left;
        if (right + width <= viewportW - margin) {
            left = right;
        } else if (leftSide >= minLeft) {
            left = leftSide;
        } else {
            // 两侧都放不下：保持面板在屏内，并让它随光标滑动到被夹住为止。
            // 这里不能直接取 margin，否则光标怎么移动面板都停在最左边。
            left = clamp(right, minLeft, maxLeft);
        }

        double minTop = margin;
        double maxTop = Math.max(margin, viewportH - margin - height);
        double below = mouseY - offsetY;
        double above = mouseY - offsetY - height;
        double top;
        if (below + height <= viewportH - margin) {
            top = below;
        } else if (above >= minTop) {
            top = above;
        } else {
            top = clamp(below, minTop, maxTop);
        }

        return new double[]{left, top};
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
