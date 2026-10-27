package io.github.sweetzonzi.machine_max.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** 验证批量基线的力学契约与失败出口，不把有界最优性当作该算法的保证。 */
class WrenchAllocatorTest {
    private static final WrenchAllocator.Settings SETTINGS = new WrenchAllocator.Settings(1e-4, 1e-6);

    /** 单轴阻尼解有解析值，可以独立核对缩放、代价和阻尼的放置位置。 */
    @Test
    void scalarDampingAndEffortMatchAnalyticSolution() {
        var problem = problem(new double[][]{{2}}, new double[]{1}, -1, 1);
        problem.rowScales()[0] = 3;
        problem.actuatorCosts()[0] = 4;
        var settings = new WrenchAllocator.Settings(0, .1);
        var result = WrenchAllocator.allocate(problem, settings, new WrenchAllocator.Workspace());
        // 目标函数为 9(2u−1)² + 0.01·4u²，导数给出 u=18/36.04。
        assertEquals(18 / 36.04, result.control(0), 1e-12);
        assertEquals(.01, result.dampingSquared(), 1e-15);
        assertEquals(2 * result.control(0), result.achieved(0), 1e-12);
        assertEquals(1 - result.achieved(0), result.residual(0), 1e-12);
        assertEquals(1, result.decompositions());
        assertEquals(0, result.clampRounds());
    }

    /** 对称列等分，改变器件代价后按同一目标函数重新分担。 */
    @Test
    void equalColumnsShareAndCostChangesTheShare() {
        var problem = problem(new double[][]{{1}, {1}}, new double[]{.8}, 0, 1);
        var equal = WrenchAllocator.allocate(problem);
        assertEquals(equal.control(0), equal.control(1), 1e-12);
        problem.actuatorCosts()[1] = 4;
        var weighted = WrenchAllocator.allocate(problem);
        assertEquals(4, weighted.control(0) / weighted.control(1), 1e-10);
        assertEquals(.8, weighted.achieved(0), 1e-4);
    }

    /** 先钉住饱和器件，再验证剩余需求由其它器件接手，且本次阻尼始终固定。 */
    @Test
    void saturatedColumnIsSubtractedBeforeRedistribution() {
        var problem = problem(new double[][]{{1}, {1}}, new double[]{1}, 0, 2);
        problem.upperBounds()[0] = .2;
        problem.actuatorCosts()[1] = 9;
        var result = WrenchAllocator.allocate(problem);
        assertEquals(.2, result.control(0), 0);
        double damping = 1e-4 * (1 + 1.0 / 9);
        assertEquals(damping, result.dampingSquared(), 1e-15);
        assertEquals(.8 / (1 + damping * 9), result.control(1), 1e-12);
        assertEquals(2, result.decompositions());
        assertEquals(1, result.clampRounds());
        assertEquals(WrenchAllocator.BoundState.UPPER, result.boundState(0));
    }

    /** 不可达需求必须留下真实差额，全部钉界后不能继续分解空系统。 */
    @Test
    void allSaturatedColumnsLeaveTheDemandInResidual() {
        var problem = problem(new double[][]{{1}, {1}}, new double[]{3}, 0, 1);
        var result = WrenchAllocator.allocate(problem);
        assertArrayEquals(new double[]{1, 1}, result.controls());
        assertEquals(1, result.residual(0), 0);
        assertEquals(1, result.clampRounds());
        assertEquals(1, result.decompositions());
        assertEquals(WrenchAllocator.Status.SOLVED, result.status());
    }

    /** 固定保留批量钉界的已知反例，防止误把基线包装成可达即达成的求解器。 */
    @Test
    void batchClampCounterexampleReportsItsKnownLoss() {
        var problem = problem(new double[][]{{-2, -1}, {2, -1}, {1, 2}}, new double[]{-1.5, 0}, 0, 1);
        var result = WrenchAllocator.allocate(problem);
        assertEquals(.6 / 1.00015, result.control(0), 1e-11);
        assertEquals(0, result.control(1), 0);
        assertEquals(0, result.control(2), 0);
        assertEquals(Math.sqrt(.45), Math.hypot(result.residual(0), result.residual(1)), 1e-6);
        assertEquals(2, result.decompositions());
        // 已知合法组合 A=1、C=0.5 能达成目标；测试明确记录算法损失而非硬件无权限。
        assertEquals(-1.5, -2 * 1 + 1 * .5, 0);
        assertEquals(0, -1 * 1 + 2 * .5, 0);
    }

    /** 零目标、无器件、全零列与关闭器件都是正常运行状态，不应产生无效数值。 */
    @Test
    void emptyZeroAndDisabledAuthorityHaveExplicitExits() {
        var workspace = new WrenchAllocator.Workspace();
        var zero = WrenchAllocator.allocate(problem(new double[][]{{1}}, new double[]{0}, 0, 1), SETTINGS, workspace);
        assertEquals(0, zero.control(0), 0);
        assertEquals(0, zero.decompositions());
        for (var problem : new WrenchAllocator.Problem[]{
                problem(new double[0][], new double[]{2}, 0, 1),
                problem(new double[][]{{0}, {0}}, new double[]{2}, 0, 1),
                problem(new double[][]{{1}}, new double[]{2}, 0, 0)}) {
            var result = WrenchAllocator.allocate(problem, SETTINGS, workspace);
            assertEquals(WrenchAllocator.Status.NO_AUTHORITY, result.status());
            assertEquals(2, result.residual(0), 0);
            assertEquals(0, result.decompositions());
            for (double value : result.controls()) assertEquals(0, value, 0);
        }
        var noRows = WrenchAllocator.allocate(problem(new double[][]{{}}, new double[0], 0, 1));
        assertEquals(WrenchAllocator.Status.NO_ACTIVE_ROWS, noRows.status());
    }

    /** 只在死行有贡献的器件不应被启用，也不应产生控制器无法处理的虚假残差。 */
    @Test
    void activeMaskMatchesRemovingRowsAndIgnoresDeadOnlyColumns() {
        var masked = problem(new double[][]{{1, 100}, {0, 1}}, new double[]{.5, 900}, -1, 1);
        masked.activeRows()[1] = false;
        var result = WrenchAllocator.allocate(masked);
        var reduced = WrenchAllocator.allocate(problem(new double[][]{{1}, {0}}, new double[]{.5}, -1, 1));
        assertArrayEquals(reduced.controls(), result.controls(), 1e-12);
        assertEquals(0, result.control(1), 0);
        assertEquals(0, result.achieved(1), 0);
        assertEquals(0, result.residual(1), 0);
        Arrays.fill(masked.activeRows(), false);
        var inactive = WrenchAllocator.allocate(masked);
        assertEquals(WrenchAllocator.Status.NO_ACTIVE_ROWS, inactive.status());
        assertArrayEquals(new double[]{0, 0}, inactive.residual());
    }

    /** 同步改变物理单位与行缩放后，控制量与正则强度应保持一致。 */
    @Test
    void unitConversionPreservesCommandsAndDamping() {
        var original = problem(new double[][]{{1, 2}, {2, -1}}, new double[]{.2, .3}, -1, 1);
        var converted = problem(new double[][]{{1000, 2}, {2000, -1}}, new double[]{200, .3}, -1, 1);
        converted.rowScales()[0] = .001;
        var first = WrenchAllocator.allocate(original);
        var second = WrenchAllocator.allocate(converted);
        assertArrayEquals(first.controls(), second.controls(), 1e-12);
        assertEquals(first.dampingSquared(), second.dampingSquared(), 1e-15);
        assertEquals(first.residual(0) * 1000, second.residual(0), 1e-12);
    }

    /** 混合正反向、零权限及不对称界，并覆盖 16／32／64 器件和近共线列。 */
    @Test
    void dozensOfActuatorsStayFiniteBoundedDeterministicAndWithinIterationBudget() {
        var workspace = new WrenchAllocator.Workspace();
        for (int count : new int[]{16, 32, 64}) {
            for (int seed = 0; seed < 30; seed++) {
                var problem = randomProblem(count, seed, seed % 3 == 0);
                double[][] columnsBefore = Arrays.stream(problem.columns()).map(double[]::clone).toArray(double[][]::new);
                var first = WrenchAllocator.allocate(problem, SETTINGS, workspace);
                var second = WrenchAllocator.allocate(problem, SETTINGS, workspace);
                assertEquals(WrenchAllocator.Status.SOLVED, first.status());
                assertArrayEquals(first.controls(), second.controls(), 0);
                assertTrue(first.decompositions() <= count + 1);
                assertTrue(first.clampRounds() <= count);
                for (int actuator = 0; actuator < count; actuator++) {
                    double command = first.control(actuator);
                    assertTrue(Double.isFinite(command));
                    assertTrue(command >= problem.lowerBounds()[actuator] && command <= problem.upperBounds()[actuator]);
                    assertArrayEquals(columnsBefore[actuator], problem.columns()[actuator], 0);
                }
                for (int row = 0; row < 6; row++) {
                    double expected = 0;
                    for (int actuator = 0; actuator < count; actuator++) {
                        expected += problem.columns()[actuator][row] * first.control(actuator);
                    }
                    if (!problem.activeRows()[row]) expected = 0;
                    assertEquals(expected, first.achieved(row), 1e-12);
                    assertEquals(problem.activeRows()[row] ? problem.target()[row] - expected : 0,
                            first.residual(row), 1e-12);
                }
            }
        }
    }

    /** 后续调用、输入修改和输出副本修改都不能污染已经返回的快照。 */
    @Test
    void resultsAreIndependentOfWorkspaceAndBorrowedInputs() {
        var workspace = new WrenchAllocator.Workspace();
        var problem = problem(new double[][]{{1}}, new double[]{.5}, 0, 1);
        var result = WrenchAllocator.allocate(problem, SETTINGS, workspace);
        double before = result.control(0);
        result.controls()[0] = 99;
        result.achieved()[0] = 99;
        result.residual()[0] = 99;
        problem.columns()[0][0] = 2;
        problem.target()[0] = 10;
        WrenchAllocator.allocate(problem, SETTINGS, workspace);
        assertEquals(before, result.control(0), 0);
        assertEquals(before, result.achieved(0), 0);
        assertEquals(.5 - before, result.residual(0), 0);
    }

    /** 借用结果复用同一对象，但显式快照和复制出的控制量不能随下一次求解改变。 */
    @Test
    void borrowedSolutionIsReusedWhileExplicitSnapshotsRemainIndependent() {
        var workspace = new WrenchAllocator.Workspace();
        var problem = problem(new double[][]{{1}}, new double[]{.5}, 0, 1);
        var first = WrenchAllocator.solve(problem, SETTINGS, workspace);
        var snapshot = first.snapshot();
        double previous = first.control(0);
        double[] copied = {-1, -1, -1};
        first.copyControlsTo(copied, 1);
        assertArrayEquals(new double[]{-1, previous, -1}, copied, 0);
        problem.target()[0] = .9;
        var second = WrenchAllocator.solve(problem, SETTINGS, workspace);
        assertSame(first, second);
        assertNotEquals(previous, first.control(0));
        assertEquals(previous, snapshot.control(0), 0);
        assertEquals(previous, copied[1], 0);
        assertEquals(1, second.actuatorCount());
        assertEquals(1, second.rowCount());
        assertEquals(WrenchAllocator.Status.SOLVED, second.status());
    }

    /** 复用大容量缓冲处理小问题时，只暴露本次输入尺寸，不能读取历史尾部。 */
    @Test
    void reusedViewRejectsIndicesOutsideCurrentProblem() {
        var workspace = new WrenchAllocator.Workspace();
        WrenchAllocator.solve(randomProblem(64, 3, false), SETTINGS, workspace);
        var output = WrenchAllocator.solve(problem(new double[][]{{1}}, new double[]{.5}, 0, 1), SETTINGS, workspace);
        assertThrows(IndexOutOfBoundsException.class, () -> output.control(1));
        assertThrows(IndexOutOfBoundsException.class, () -> output.boundState(1));
        assertThrows(IndexOutOfBoundsException.class, () -> output.achieved(1));
        assertThrows(IndexOutOfBoundsException.class, () -> output.residual(1));
        var failure = WrenchAllocator.solve(problem(new double[][]{{Double.MAX_VALUE}}, new double[]{1}, 0, 1), SETTINGS, workspace);
        assertSame(output, failure);
        assertEquals(WrenchAllocator.Status.NUMERICAL_FAILURE, failure.status());
        assertEquals(0, failure.control(0), 0);
        assertEquals(0, failure.achieved(0), 0);
        assertEquals(1, failure.residual(0), 0);
    }

    /** 区分非法配置和正常能力不足，避免将 NaN 或不包含零的界送进引擎。 */
    @Test
    void rejectsInvalidDimensionsNonfiniteInputsAndInvalidSettings() {
        assertThrows(IllegalArgumentException.class, () -> new WrenchAllocator.Settings(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> new WrenchAllocator.Settings(1, 0));
        assertThrows(IllegalArgumentException.class, () -> new WrenchAllocator.Settings(1, Double.MIN_VALUE));
        assertThrows(IllegalArgumentException.class, () -> WrenchAllocator.allocate(problem(new double[][]{{1}}, new double[]{1}, .1, 1)));
        var malformed = problem(new double[][]{{1, 2}}, new double[]{1}, 0, 1);
        assertThrows(IllegalArgumentException.class, () -> WrenchAllocator.allocate(malformed));
        var nan = problem(new double[][]{{Double.NaN}}, new double[]{1}, 0, 1);
        assertThrows(IllegalArgumentException.class, () -> WrenchAllocator.allocate(nan));
        var badCost = problem(new double[][]{{1}}, new double[]{1}, 0, 1);
        badCost.actuatorCosts()[0] = 0;
        assertThrows(IllegalArgumentException.class, () -> WrenchAllocator.allocate(badCost));
        var badScale = problem(new double[][]{{1}}, new double[]{1}, 0, 1);
        badScale.rowScales()[0] = Double.POSITIVE_INFINITY;
        assertThrows(IllegalArgumentException.class, () -> WrenchAllocator.allocate(badScale));
    }

    /** 有限输入也可能在归一化或矩阵运算中溢出，必须有有限的失败输出。 */
    @Test
    void arithmeticOverflowReturnsZeroCommandsAndOriginalDemand() {
        var problem = problem(new double[][]{{Double.MAX_VALUE}}, new double[]{1}, 0, 1);
        var result = WrenchAllocator.allocate(problem);
        assertEquals(WrenchAllocator.Status.NUMERICAL_FAILURE, result.status());
        assertEquals(0, result.control(0), 0);
        assertEquals(0, result.achieved(0), 0);
        assertEquals(1, result.residual(0), 0);
        assertTrue(Double.isFinite(result.dampingSquared()));
    }

    /** 建立没有隐式机型参数的小型问题，供解析与边界用例复用。 */
    static WrenchAllocator.Problem problem(double[][] columns, double[] target, double lower, double upper) {
        double[] lows = new double[columns.length];
        double[] highs = new double[columns.length];
        double[] costs = new double[columns.length];
        double[] scales = new double[target.length];
        boolean[] active = new boolean[target.length];
        Arrays.fill(lows, lower);
        Arrays.fill(highs, upper);
        Arrays.fill(costs, 1);
        Arrays.fill(scales, 1);
        Arrays.fill(active, true);
        return new WrenchAllocator.Problem(columns, lows, highs, target, scales, costs, active);
    }

    /** 可复现的混合器件布局；一部分种子刻意构造近共线列以覆盖数值退化。 */
    static WrenchAllocator.Problem randomProblem(int count, int seed, boolean collinear) {
        Random random = new Random(seed);
        var problem = problem(new double[count][6], new double[6], 0, 1);
        for (int row = 0; row < 6; row++) {
            problem.target()[row] = (random.nextDouble() - .5) * count;
            problem.rowScales()[row] = .2 + random.nextDouble();
            problem.activeRows()[row] = row != 4 || seed % 2 == 0;
        }
        for (int actuator = 0; actuator < count; actuator++) {
            problem.lowerBounds()[actuator] = actuator % 3 == 0 ? -.4 : 0;
            problem.upperBounds()[actuator] = actuator % 11 == 0 ? 0 : .3 + random.nextDouble();
            problem.actuatorCosts()[actuator] = .5 + random.nextDouble() * 5;
            for (int row = 0; row < 6; row++) {
                problem.columns()[actuator][row] = collinear
                        ? row + 1 + random.nextDouble() * 1e-7 : random.nextDouble() * 2 - 1;
            }
        }
        return problem;
    }
}
