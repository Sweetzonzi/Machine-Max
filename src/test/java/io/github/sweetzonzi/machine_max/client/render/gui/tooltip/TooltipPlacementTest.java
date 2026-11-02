package io.github.sweetzonzi.machine_max.client.render.gui.tooltip;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 摆放算法的回归测试。
 *
 * <p>重点是“两侧都放不下”这一档：面板必须随光标在可用区间内滑动，不能固定贴在左边距。</p>
 */
class TooltipPlacementTest {

    private static final double OFFSET_X = 12.0d;
    private static final double OFFSET_Y = 12.0d;
    private static final double MARGIN = 4.0d;

    @Test
    void prefersRightSideWhenItFits() {
        double[] placed = TooltipPlacement.place(100, 100, 800, 600, 200, 80,
                OFFSET_X, OFFSET_Y, MARGIN);
        assertEquals(112.0d, placed[0], 0.01d);
        assertEquals(88.0d, placed[1], 0.01d);
    }

    @Test
    void flipsToLeftSideWhenRightSideOverflows() {
        double[] placed = TooltipPlacement.place(700, 100, 800, 600, 200, 80,
                OFFSET_X, OFFSET_Y, MARGIN);
        assertEquals(488.0d, placed[0], 0.01d);
    }

    @Test
    void flipsAboveWhenBottomOverflows() {
        double[] placed = TooltipPlacement.place(100, 560, 800, 600, 200, 80,
                OFFSET_X, OFFSET_Y, MARGIN);
        assertEquals(468.0d, placed[1], 0.01d);
    }

    /**
     * 面板宽 320、视口只有 364（guiScale 很小的情形）：两侧都放不下。
     * 旧实现一律贴到 margin，表现为位置完全不跟随；现在应当贴向另一侧并在区间内滑动。
     */
    @Test
    void keepsOnScreenAndStillMovesWhenNeitherSideFits() {
        double[] nearLeft = TooltipPlacement.place(60, 100, 364, 196, 320, 80,
                OFFSET_X, OFFSET_Y, MARGIN);
        double[] nearRight = TooltipPlacement.place(300, 100, 364, 196, 320, 80,
                OFFSET_X, OFFSET_Y, MARGIN);

        assertEquals(40.0d, nearLeft[0], 0.01d, "应贴向屏幕右侧而不是固定左边距");
        assertEquals(40.0d, nearRight[0], 0.01d);
        assertTrue(nearLeft[0] + 320 <= 364 - MARGIN + 0.01d, "面板不得溢出屏幕");
    }

    /**
     * 文档视口与 GUI 空间不等价时的典型参数：renderScale≈0.48。
     * 换算后光标在 473，面板 320，视口 758，应当落在光标左侧而不是被夹到边缘。
     */
    @Test
    void browserViewportPlacesPanelNextToCursor() {
        double[] placed = TooltipPlacement.place(473, 292, 758, 408, 320, 85,
                25, 25, 8);
        assertEquals(128.0d, placed[0], 0.01d);
        assertTrue(placed[0] > 8.0d, "不得被夹到左边距");
    }

    @Test
    void clampsOversizedPanelToTopMargin() {
        double[] placed = TooltipPlacement.place(100, 100, 300, 120, 300, 200,
                OFFSET_X, OFFSET_Y, MARGIN);
        assertEquals(4.0d, placed[0], 0.01d);
        assertEquals(4.0d, placed[1], 0.01d);
    }
}
