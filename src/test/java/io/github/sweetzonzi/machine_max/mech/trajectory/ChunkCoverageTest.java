package io.github.sweetzonzi.machine_max.mech.trajectory;

import io.github.sweetzonzi.machine_max.common.mech.trajectory.ChunkCoverage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * {@link ChunkCoverage} 的覆盖回归。
 * <p>
 * <b>所有坐标都是方块坐标。</b>把端点按区块坐标写时，{@code (15,14) → (18,18)} 会落在区块
 * {@code (15,14)} 与 {@code (18,18)}，与期望集合完全不相交——断言因此必然失败，
 * 坐标系写错不会被静默放过。外扩半径 {@code r} 的期望值依赖具体的 {@code r}，
 * 所以每条断言都把 {@code r} 钉死。
 */
class ChunkCoverageTest {

    /** 一格区块坐标 */
    private record Cell(int x, int z) {
    }

    @Test
    @DisplayName("浅斜线段覆盖中段格：端点只覆盖两格，中段第三格必须补上")
    void coversMiddleCellOfShallowDiagonal() {
        // 方块 (15,14) → (18,18)：端点落在区块 (0,0) 与 (1,1)，
        // 中段在 y=16 处穿过 x=16.5，从 (16,15.33) 到 (16.5,16) 有约 0.83 m 走在区块 (1,0) 里。
        // 8 m 步长两端采样必然漏掉它，这正是“覆盖不能由步长负责”的反例。
        Assertions.assertEquals(
                Set.of(new Cell(0, 0), new Cell(1, 0), new Cell(1, 1)),
                cells(15.0, 14.0, 18.0, 18.0, 0.0));
    }

    @Test
    @DisplayName("外扩按垂直距离：到角点垂距 0.4 m，r 跨过 0.4 才带上第四格")
    void radiusThresholdFollowsPerpendicularDistance() {
        // 该线段到角点 (16,16) 的垂距是 |1·4 − 2·3| / 5 = 0.4 m。
        // 若按轴对齐正方形放宽（而不是按距离），门槛会提前到 r ≈ 0.286，这两条断言就有一条会失败。
        Assertions.assertEquals(
                Set.of(new Cell(0, 0), new Cell(1, 0), new Cell(1, 1)),
                cells(15.0, 14.0, 18.0, 18.0, 0.39),
                "r = 0.39 < 0.4，第四格 (0,1) 不该出现");

        Assertions.assertEquals(
                Set.of(new Cell(0, 0), new Cell(1, 0), new Cell(1, 1), new Cell(0, 1)),
                cells(15.0, 14.0, 18.0, 18.0, 0.41),
                "r = 0.41 ≥ 0.4，第四格 (0,1) 必须出现");
    }

    @Test
    @DisplayName("擦角弦覆盖四格：长 0.14 m 的弦，两侧格都要取")
    void cornerChordCoversAllFourCells() {
        // 从 (15.95,15.95) 到 (16.05,16.05)，穿过角点 (16,16)。
        // 任何步长大于 0.14 m 的端点采样都采不到任何一格；不做超覆盖的逐格遍历只给两格。
        Assertions.assertEquals(
                Set.of(new Cell(0, 0), new Cell(1, 0), new Cell(0, 1), new Cell(1, 1)),
                cells(15.95, 15.95, 16.05, 16.05, 0.0));
    }

    @Test
    @DisplayName("坐标正好落在区块分界线上时按 floor 归属右侧区块，与 SectionPos 一致")
    void coordinateExactlyOnChunkBoundaryBelongsToUpperCell() {
        // x = 16 是区块 0 与 1 的分界。方块 16 属于区块 1（SectionPos.blockToSectionCoord(16) == 1），
        // 这里沿用同一条 floor 约定，不做“两侧都算”的闭合放宽——放宽会让每条从整格坐标起飞的
        // 线段都多覆盖一格，而真机上物理坐标不会正好落在格线上，测度为零的情形不值得付这份预算。
        Assertions.assertEquals(
                Set.of(new Cell(1, 0)),
                cells(16.0, 0.0, 16.0, 10.0, 0.0));

        // 跨过分界线时两侧都在，这才是真机上唯一会出现的形态
        Assertions.assertEquals(
                Set.of(new Cell(0, 0), new Cell(1, 0)),
                cells(15.9, 0.0, 16.1, 10.0, 0.0));
    }

    @Test
    @DisplayName("负坐标按向下取整落格，不会错一格")
    void negativeCoordinatesUseFloorDivision() {
        // 从区块 (-1,-1) 穿过原点角点到区块 (0,0)，四格都与线段距离为 0
        Assertions.assertEquals(
                Set.of(new Cell(-1, -1), new Cell(0, -1), new Cell(-1, 0), new Cell(0, 0)),
                cells(-1.0, -1.0, 1.0, 1.0, 0.0));
    }

    @Test
    @DisplayName("长线段逐格连续，不因跨度大而漏格")
    void longSegmentCoversEveryCellInBetween() {
        Set<Cell> expected = new LinkedHashSet<>();
        for (int cx = 0; cx <= 6; cx++) {
            expected.add(new Cell(cx, 0));
        }
        // 0 → 100 横穿 7 个区块；z = 8 在区块 0 内部，不触及区块 ±1
        Assertions.assertEquals(expected, cells(0.0, 8.0, 100.0, 8.0, 0.0));
    }

    @Test
    @DisplayName("零长线段只覆盖所在格")
    void degeneratePointCoversContainingCell() {
        Assertions.assertEquals(Set.of(new Cell(0, 0)), cells(5.0, 5.0, 5.0, 5.0, 0.0));
    }

    /** 收集线段覆盖的区块集合 */
    private static Set<Cell> cells(double ax, double az, double bx, double bz, double radius) {
        Set<Cell> out = new LinkedHashSet<>();
        ChunkCoverage.cellsCrossedBySegment(ax, az, bx, bz, radius, (cx, cz) -> out.add(new Cell(cx, cz)));
        return out;
    }
}
