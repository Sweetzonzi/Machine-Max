package io.github.sweetzonzi.machine_max.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 核对一体化上下文的力学符号、动态效能列与借用生命周期。 */
class WrenchWorkspaceTest {
    /** 一份力的三个分量与 r×F 一起写入同一列，不能拆成互不相关的自由量。 */
    @Test
    void forceAtOffsetBuildsTheCorrectWrenchAndReusesProblem() {
        var inputs = new WrenchAllocator.Workspace(2);
        var view = inputs.problem();
        assertSame(inputs, inputs.forceAtOffset(0, 2, 3, 5, 7, 11, 13));
        assertSame(view, inputs.problem());
        assertArrayEquals(new double[]{2, 3, 5, 16, -9, -1}, view.columns()[0], 0);
        inputs.pureTorque(0, 8, -4, 2);
        assertArrayEquals(new double[]{0, 0, 0, 8, -4, 2}, view.columns()[0], 0);
        assertEquals(2, inputs.actuatorCount());
        for (double scale : view.rowScales()) assertEquals(1, scale, 0);
        for (boolean active : view.activeRows()) assertTrue(active);
    }

    /** 关闭、几何更新和恢复权限分别操作，更新列不能意外恢复损坏器件。 */
    @Test
    void geometryUpdatesPreserveLimitsCostAndRowConfiguration() {
        var inputs = new WrenchAllocator.Workspace(1).limits(0, -.5, .8).cost(0, 3).rowScale(1, .01).activeRow(4, false);
        inputs.forceAtOffset(0, 0, 100, 0, 1, 0, 0).disable(0);
        inputs.forceAtOffset(0, 0, 50, 0, 2, 0, 0);
        var problem = inputs.problem();
        assertEquals(0, problem.lowerBounds()[0], 0);
        assertEquals(0, problem.upperBounds()[0], 0);
        assertEquals(3, problem.actuatorCosts()[0], 0);
        assertEquals(.01, problem.rowScales()[1], 0);
        assertFalse(problem.activeRows()[4]);
        assertArrayEquals(new double[]{0, 50, 0, 0, 0, 100}, problem.columns()[0], 0);
        inputs.limits(0, 0, 1);
        assertEquals(1, problem.upperBounds()[0], 0);
    }

    /** 每步先覆盖目标再扣已知项，保证静止净受力为零时仍能产生顶重力需求。 */
    @Test
    void overwritingTargetPreventsKnownWrenchFromAccumulatingAcrossFrames() {
        var inputs = new WrenchAllocator.Workspace(1);
        for (int frame = 0; frame < 3; frame++) {
            inputs.target(0, 0, 0, 0, 0, 0).subtractKnown(0, -100, 0, 0, 0, 0);
            assertArrayEquals(new double[]{0, 100, 0, 0, 0, 0}, inputs.problem().target(), 0);
        }
        inputs.target(10, 20, 30, 40, 50, 60).subtractKnown(1, 2, 3, 4, 5, 6);
        assertArrayEquals(new double[]{9, 18, 27, 36, 45, 54}, inputs.problem().target(), 0);
        inputs.clearTarget();
        assertArrayEquals(new double[6], inputs.problem().target(), 0);
    }

    /** 辅助构造的双喷口问题与手写列一致，求解和显式快照使用相同数据。 */
    @Test
    void helperAndBorrowedSolverMatchManualColumns() {
        var inputs = new WrenchAllocator.Workspace(2).forceAtOffset(0, 0, 100, 0, 1, 0, 0)
                .forceAtOffset(1, 0, 100, 0, -1, 0, 0).target(0, 100, 0, 0, 0, 0);
        var manual = WrenchAllocatorTest.problem(new double[][]{{0, 100, 0, 0, 0, 100}, {0, 100, 0, 0, 0, -100}},
                new double[]{0, 100, 0, 0, 0, 0}, 0, 1);
        var expected = WrenchAllocator.allocate(manual);
        var actual = inputs.solve();
        assertArrayEquals(expected.controls(), actual.snapshot().controls(), 1e-12);
        assertEquals(expected.achieved(1), actual.achieved(1), 1e-12);
        assertEquals(expected.residual(5), actual.residual(5), 1e-12);
    }

    /** 错误目标或叉积溢出时保留完整的上一份数据，避免半更新状态。 */
    @Test
    void invalidWritesDoNotPartiallyModifyTargetOrColumn() {
        var inputs = new WrenchAllocator.Workspace(1).target(1, 2, 3, 4, 5, 6).wrench(0, 7, 8, 9, 10, 11, 12);
        assertThrows(IllegalArgumentException.class, () -> inputs.target(99, 98, Double.NaN, 0, 0, 0));
        assertArrayEquals(new double[]{1, 2, 3, 4, 5, 6}, inputs.problem().target(), 0);
        assertThrows(IllegalArgumentException.class, () -> inputs.forceAtOffset(0, 0, Double.MAX_VALUE, 0,
                Double.MAX_VALUE, 0, 0));
        assertArrayEquals(new double[]{7, 8, 9, 10, 11, 12}, inputs.problem().columns()[0], 0);
        inputs.target(Double.MAX_VALUE, 2, 3, 4, 5, 6);
        assertThrows(IllegalArgumentException.class, () -> inputs.subtractKnown(-Double.MAX_VALUE, 0, 0, 0, 0, 0));
        assertEquals(Double.MAX_VALUE, inputs.problem().target()[0], 0);
        assertThrows(IllegalArgumentException.class, () -> inputs.limits(0, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> inputs.cost(0, 0));
        assertThrows(IllegalArgumentException.class, () -> inputs.rowScale(0, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new WrenchAllocator.Workspace(-1));
    }

    /** 下一物理步喷口从朝上改为朝前时，力与力矩整列都须重建，不能沿用旧分解。 */
    @Test
    void changedNozzleDirectionsReplaceForceAndTorqueOnTheNextSolve() {
        var workspace = new WrenchAllocator.Workspace(2)
                .forceAtOffset(0, 0, 100, 0, 1, 0, 0)
                .forceAtOffset(1, 0, 100, 0, -1, 0, 0)
                .target(0, 100, 0, 0, 0, 0);
        var original = workspace.solve().snapshot();
        assertEquals(100, original.achieved(1), .01);

        // 喷口转到世界 Z 方向，X 方向的力臂会产生 Y 力矩；两侧仍相消。
        workspace.forceAtOffset(0, 0, 0, 100, 1, 0, 0)
                .forceAtOffset(1, 0, 0, 100, -1, 0, 0)
                .target(0, 0, 100, 0, 0, 0);
        var turned = workspace.solve();
        assertArrayEquals(new double[]{0, 0, 100, 0, -100, 0}, workspace.problem().columns()[0], 0);
        assertEquals(0, turned.achieved(1), 0);
        assertEquals(100, turned.achieved(2), .01);
        assertEquals(0, turned.achieved(4), 1e-10);
        assertEquals(100, original.achieved(1), .01);

        // 方向变了之后仍要求纯向上，当前器件确实无法完成，不能继承上一拍油门造出升力。
        workspace.target(0, 100, 0, 0, 0, 0);
        var unreachable = workspace.solve();
        assertEquals(0, unreachable.control(0), 0);
        assertEquals(0, unreachable.control(1), 0);
        assertEquals(100, unreachable.residual(1), 0);
    }

    /** 只更新改变的器件即可，其它列、幅值界和配置留在上下文中。 */
    @Test
    void partialColumnUpdateKeepsOtherActuatorsAndUsesNewMatrix() {
        var workspace = new WrenchAllocator.Workspace(2)
                .forceAtOffset(0, 0, 100, 0, 0, 0, 0)
                .forceAtOffset(1, 0, 100, 0, 0, 0, 0)
                .target(0, 100, 0, 0, 0, 0);
        workspace.solve();
        workspace.forceAtOffset(0, 100, 0, 0, 0, 0, 0).target(20, 30, 0, 0, 0, 0);
        var result = workspace.solve();
        assertEquals(.2, result.control(0), 1e-4);
        assertEquals(.3, result.control(1), 1e-4);
        assertArrayEquals(new double[]{0, 100, 0, 0, 0, 0}, workspace.problem().columns()[1], 0);
    }

    /** 低层暂存模式与自带输入模式有明确边界，零器件与指定正则配置也可独立使用。 */
    @Test
    void ownedInputAndExternalProblemModesHaveExplicitLifecycles() {
        var scratch = new WrenchAllocator.Workspace();
        assertThrows(IllegalStateException.class, scratch::problem);
        assertThrows(IllegalStateException.class, scratch::solve);
        var empty = new WrenchAllocator.Workspace(0).target(0, 1, 0, 0, 0, 0);
        assertEquals(WrenchAllocator.Status.NO_AUTHORITY, empty.solve().status());
        var owned = new WrenchAllocator.Workspace(1).wrench(0, 2, 0, 0, 0, 0, 0)
                .target(1, 0, 0, 0, 0, 0);
        var settings = new WrenchAllocator.Settings(0, .1);
        assertEquals(2 / 4.01, owned.solve(settings).control(0), 1e-12);
        assertEquals(.01, owned.solve(settings).dampingSquared(), 1e-15);
    }
}
