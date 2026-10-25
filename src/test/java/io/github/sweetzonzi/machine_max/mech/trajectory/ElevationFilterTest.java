package io.github.sweetzonzi.machine_max.mech.trajectory;

import io.github.sweetzonzi.machine_max.common.mech.trajectory.ElevationFilter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ElevationFilter} 的三态与并集语义。
 * <p>
 * 这里不构造 {@code Level}，也不碰 section 换算：入参是索引给出的方块 Y 区间与弹道方块 Y 窗口，
 * 出参是同一个方块 Y 坐标下的交集区间。MC 侧的取样（{@code SparkLevel#getSolidIntervals}）
 * 与 section 换算（{@code SparkLevel#scheduleChunkLoad}）都是薄壳，本类只守判定本身。
 */
class ElevationFilterTest {

    /** 典型主世界一列：基岩到地面（-64..0 全实心），地面以上的建筑层（60..70） */
    private static final short[] TWO_LAYERS = {-64, 0, 60, 70};

    @Test
    @DisplayName("无索引：null 入参给出 null，调用方据此跳过而不是保守预约")
    void noIndexMeansNoReservation() {
        Assertions.assertNull(ElevationFilter.intersect(null, -64, 320));
    }

    @Test
    @DisplayName("有索引但整列无实体方块：空数组同样不预约")
    void emptyColumnMeansAirborne() {
        Assertions.assertNull(ElevationFilter.intersect(new short[0], -64, 320));
    }

    @Test
    @DisplayName("窗口落在某一层内部时被该层裁剪")
    void windowInsideLayerIsClippedToLayer() {
        // 窗口 65..100 只与 60..70 相交
        Assertions.assertArrayEquals(new int[]{65, 70},
                ElevationFilter.intersect(TWO_LAYERS, 65, 100));
    }

    @Test
    @DisplayName("窗口落在两层之间的空隙：确定空中，不预约")
    void windowInGapIsAirborne() {
        Assertions.assertNull(ElevationFilter.intersect(TWO_LAYERS, 10, 50));
        Assertions.assertNull(ElevationFilter.intersect(TWO_LAYERS, 1, 59));
    }

    @Test
    @DisplayName("窗口跨两层：返回首段下界到末段上界的并集，中间空隙一并带上")
    void windowSpanningLayersReturnsUnion() {
        // 预约接口吃一个连续 Y 范围，因此 -64..70 而不是两段；
        // 多激活几个 section 只是多算，漏了才会缺地形
        Assertions.assertArrayEquals(new int[]{-64, 70},
                ElevationFilter.intersect(TWO_LAYERS, -100, 100));
    }

    @Test
    @DisplayName("端点相接算相交")
    void touchingEndpointsCount() {
        short[] layer = {0, 15};
        Assertions.assertArrayEquals(new int[]{15, 15}, ElevationFilter.intersect(layer, 15, 30));
        Assertions.assertArrayEquals(new int[]{0, 0}, ElevationFilter.intersect(layer, -10, 0));
    }

    @Test
    @DisplayName("窗口整体在索引下方或上方都给出 null")
    void windowOutsideAllLayersIsNull() {
        Assertions.assertNull(ElevationFilter.intersect(TWO_LAYERS, -200, -100));
        Assertions.assertNull(ElevationFilter.intersect(TWO_LAYERS, 71, 320));
    }

    @Test
    @DisplayName("上下界颠倒时按交换后的窗口求交")
    void reversedWindowIsNormalized() {
        Assertions.assertArrayEquals(new int[]{65, 70},
                ElevationFilter.intersect(TWO_LAYERS, 100, 65));
    }

    @Test
    @DisplayName("多段区间中只取真正相交的那些")
    void onlyOverlappingLayersContribute() {
        short[] manyLayers = {-64, -40, -20, -10, 5, 40, 90, 120};
        // 窗口 0..100 与 5..40、90..120 相交，与 -64..-40、-20..-10 不相交
        Assertions.assertArrayEquals(new int[]{5, 100},
                ElevationFilter.intersect(manyLayers, 0, 100));
    }
}
