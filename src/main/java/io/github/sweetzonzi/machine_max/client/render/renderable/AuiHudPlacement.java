package io.github.sweetzonzi.machine_max.client.render.renderable;

/** CSS 到 Minecraft GUI 的轴对齐映射；绘制矩阵与裁剪使用相同的四项参数。 */
record AuiHudPlacement(float x, float y, float scaleX, float scaleY) {

    static AuiHudPlacement resolve(
            double contentWidth, double contentHeight,
            int screenWidth, int screenHeight, float viewportScale,
            double elementScaleX, double elementScaleY,
            double offsetX, double offsetY, int horizontalAnchor, int verticalAnchor
    ) {
        float viewport = positiveScale(viewportScale);
        float scaleX = positiveScale(viewport * positiveScale(elementScaleX));
        float scaleY = positiveScale(viewport * positiveScale(elementScaleY));
        float x = aligned(horizontalAnchor, screenWidth, dimension(contentWidth) * scaleX);
        float y = aligned(verticalAnchor, screenHeight, dimension(contentHeight) * scaleY);
        return new AuiHudPlacement(x + finiteOffset(offsetX), y + finiteOffset(offsetY), scaleX, scaleY);
    }

    /** HUD 两轴缩放要求有限正值；无效值按单位缩放处理。 */
    private static float positiveScale(double value) {
        float result = (float) value;
        return Float.isFinite(result) && result > 0f ? result : 1f;
    }

    private static float dimension(double value) {
        float result = (float) value;
        return Float.isFinite(result) && result > 0f ? result : 0f;
    }

    private static float finiteOffset(double value) {
        float result = (float) value;
        return Float.isFinite(result) ? result : 0f;
    }

    /** 0 = 左/上，1 = 居中，2 = 右/下；距离均以 GUI 坐标表示。 */
    private static float aligned(int alignment, float screenSize, float elementSize) {
        return switch (alignment) {
            case 1 -> (screenSize - elementSize) / 2f;
            case 2 -> screenSize - elementSize;
            default -> 0f;
        };
    }
}
