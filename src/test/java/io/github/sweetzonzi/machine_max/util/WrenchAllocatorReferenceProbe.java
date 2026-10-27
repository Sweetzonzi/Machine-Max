package io.github.sweetzonzi.machine_max.util;

import java.util.Arrays;
import java.util.Random;
import java.lang.management.ManagementFactory;

/**
 * 独立于 JUnit、Minecraft 与第三方 Java 数学库的离线探针。
 * 默认逐行输出输入与 Java 结果，交给 tools/verify_wrench_allocator.py 用 NumPy 对照；
 * --benchmark 测量内核耗时，--allocation 测量线程分配字节，不代替完整游戏施力链验收。
 */
public final class WrenchAllocatorReferenceProbe {
    private static volatile double sink;
    private static volatile WrenchAllocator.Result snapshotSink;
    private static volatile WrenchAllocator.Solution borrowedSink;

    private WrenchAllocatorReferenceProbe() {
    }

    /** 导出固定种子的问题或运行预热后的单线程内核测量。 */
    public static void main(String[] args) {
        if (args.length == 1 && args[0].equals("--allocation")) {
            measureAllocations();
            return;
        }
        if (args.length == 1 && args[0].equals("--benchmark")) {
            for (int count : new int[]{16, 32, 64}) {
                benchmark(count, false);
                benchmark(count, true);
            }
            return;
        }
        emit("scalar", simple(new double[][]{{2}}, new double[]{1}));
        emit("counterexample", simple(new double[][]{{-2, -1}, {2, -1}, {1, 2}}, new double[]{-1.5, 0}));
        emit("saturated", simple(new double[][]{{1}, {1}}, new double[]{3}));
        emit("no_actuators", simple(new double[0][], new double[]{1, 2}));
        emit("no_rows", simple(new double[][]{{}, {}}, new double[0]));
        for (int count : new int[]{16, 32, 64}) {
            for (int seed = 0; seed < 24; seed++) emit("n" + count + "_seed" + seed, layout(count, seed, seed % 4 == 0));
            var zero = layout(count, 30, false);
            Arrays.fill(zero.target(), 0);
            emit("zero_target_" + count, zero);
            var disabled = layout(count, 31, false);
            Arrays.fill(disabled.lowerBounds(), 0);
            Arrays.fill(disabled.upperBounds(), 0);
            emit("disabled_" + count, disabled);
            var zeroColumns = layout(count, 32, false);
            for (double[] column : zeroColumns.columns()) Arrays.fill(column, 0);
            emit("zero_columns_" + count, zeroColumns);
        }
    }

    /** 建立单位缩放、单位代价的解析小场景。 */
    private static WrenchAllocator.Problem simple(double[][] columns, double[] target) {
        double[] low = new double[columns.length];
        double[] high = new double[columns.length];
        double[] costs = new double[columns.length];
        double[] scales = new double[target.length];
        boolean[] active = new boolean[target.length];
        Arrays.fill(high, 1);
        Arrays.fill(costs, 1);
        Arrays.fill(scales, 1);
        Arrays.fill(active, true);
        return new WrenchAllocator.Problem(columns, low, high, target, scales, costs, active);
    }

    /** 生成带混合界、行缩放、差异代价和部分死行的可复现器件布局。 */
    private static WrenchAllocator.Problem layout(int count, int seed, boolean nearlyCollinear) {
        Random random = new Random(seed);
        var problem = simple(new double[count][6], new double[6]);
        for (int row = 0; row < 6; row++) {
            problem.target()[row] = (random.nextDouble() - .5) * count * .6;
            problem.rowScales()[row] = .25 + random.nextDouble() * 2;
            problem.activeRows()[row] = row != 3 || seed % 2 == 0;
        }
        for (int actuator = 0; actuator < count; actuator++) {
            problem.lowerBounds()[actuator] = actuator % 3 == 0 ? -.7 : 0;
            problem.upperBounds()[actuator] = actuator % 13 == 0 ? 0 : .5 + random.nextDouble();
            problem.actuatorCosts()[actuator] = .25 + random.nextDouble() * 4;
            for (int row = 0; row < 6; row++) {
                problem.columns()[actuator][row] = nearlyCollinear
                        ? (row + 1) * .3 + random.nextDouble() * 1e-6 : random.nextDouble() * 2 - 1;
            }
        }
        return problem;
    }

    /** 用基础数组文本组成 JSON；所有导出场景均使用有限数值。 */
    private static void emit(String name, WrenchAllocator.Problem problem) {
        var result = WrenchAllocator.allocate(problem);
        System.out.println("{\"name\":\"" + name + "\",\"columns\":" + Arrays.deepToString(problem.columns())
                + ",\"lower\":" + Arrays.toString(problem.lowerBounds())
                + ",\"upper\":" + Arrays.toString(problem.upperBounds())
                + ",\"target\":" + Arrays.toString(problem.target())
                + ",\"scales\":" + Arrays.toString(problem.rowScales())
                + ",\"costs\":" + Arrays.toString(problem.actuatorCosts())
                + ",\"active\":" + Arrays.toString(problem.activeRows())
                + ",\"controls\":" + Arrays.toString(result.controls())
                + ",\"achieved\":" + Arrays.toString(result.achieved())
                + ",\"residual\":" + Arrays.toString(result.residual())
                + ",\"damping_squared\":" + result.dampingSquared()
                + ",\"ratio\":" + WrenchAllocator.Settings.DEFAULT.regularizationRatio()
                + ",\"floor\":" + WrenchAllocator.Settings.DEFAULT.minimumDamping()
                + ",\"decompositions\":" + result.decompositions()
                + ",\"clamp_rounds\":" + result.clampRounds()
                + ",\"status\":\"" + result.status() + "\"}");
    }

    /** 复用工作区，包含输入校验与结果快照分配，分别记录典型和近共线布局。 */
    private static void benchmark(int count, boolean nearlyCollinear) {
        var problem = layout(count, 13, nearlyCollinear);
        var workspace = new WrenchAllocator.Workspace();
        var settings = WrenchAllocator.Settings.DEFAULT;
        for (int i = 0; i < 5_000; i++) sink += WrenchAllocator.allocate(problem, settings, workspace).control(0);
        long[] elapsed = new long[2_000];
        int decompositions = 0;
        for (int i = 0; i < elapsed.length; i++) {
            long start = System.nanoTime();
            var result = WrenchAllocator.allocate(problem, settings, workspace);
            elapsed[i] = System.nanoTime() - start;
            sink += result.control(0);
            decompositions += result.decompositions();
        }
        Arrays.sort(elapsed);
        System.out.println("{\"actuators\":" + count + ",\"nearly_collinear\":" + nearlyCollinear
                + ",\"warmup\":5000,\"samples\":2000,\"p50_ns\":" + elapsed[1000]
                + ",\"p95_ns\":" + elapsed[1900] + ",\"p99_ns\":" + elapsed[1980]
                + ",\"max_ns\":" + elapsed[1999] + ",\"average_decompositions\":" + decompositions / 2000.0
                + ",\"java\":\"" + Runtime.version() + "\",\"processors\":"
                + Runtime.getRuntime().availableProcessors() + "}");
    }

    /** 按当前线程分配字节计数，对照快照、借用输出及复用／重建输入的完整路径。 */
    private static void measureAllocations() {
        var platform = ManagementFactory.getThreadMXBean();
        if (!(platform instanceof com.sun.management.ThreadMXBean bean) || !bean.isThreadAllocatedMemorySupported()) {
            throw new UnsupportedOperationException("当前 JVM 不支持线程分配字节计数");
        }
        bean.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().threadId();
        for (int count : new int[]{16, 32, 64}) {
            var problem = layout(count, 13, false);
            var workspace = new WrenchAllocator.Workspace();
            for (int i = 0; i < 10_000; i++) {
                snapshotSink = WrenchAllocator.allocate(problem, WrenchAllocator.Settings.DEFAULT, workspace);
            }
            long before = bean.getThreadAllocatedBytes(thread);
            for (int i = 0; i < 20_000; i++) {
                snapshotSink = WrenchAllocator.allocate(problem, WrenchAllocator.Settings.DEFAULT, workspace);
            }
            long bytes = bean.getThreadAllocatedBytes(thread) - before;
            System.out.println("{\"actuators\":" + count + ",\"mode\":\"snapshot\",\"bytes_per_call\":"
                    + bytes / 20_000.0 + ",\"java\":\"" + Runtime.version() + "\"}");

            // 借用结果只保留对同一工作区对象的引用，正常求解不需要按帧建立输出数组。
            for (int i = 0; i < 10_000; i++) borrowedSink = WrenchAllocator.solve(problem, WrenchAllocator.Settings.DEFAULT, workspace);
            before = bean.getThreadAllocatedBytes(thread);
            for (int i = 0; i < 20_000; i++) borrowedSink = WrenchAllocator.solve(problem, WrenchAllocator.Settings.DEFAULT, workspace);
            reportBytes(bean, thread, before, count, "borrowed");

            var inputs = new WrenchAllocator.Workspace(count);
            for (int i = 0; i < 10_000; i++) {
                fillInputs(inputs, i);
                borrowedSink = inputs.solve();
            }
            before = bean.getThreadAllocatedBytes(thread);
            for (int i = 0; i < 20_000; i++) {
                fillInputs(inputs, i);
                borrowedSink = inputs.solve();
            }
            reportBytes(bean, thread, before, count, "context_reused_fill_and_solve");

            // 刻意演示错误使用模式的代价：即使输出复用，按帧重建整个上下文也会产生大量数组。
            for (int i = 0; i < 10_000; i++) {
                var rebuilt = new WrenchAllocator.Workspace(count);
                fillInputs(rebuilt, i);
                borrowedSink = rebuilt.solve();
            }
            before = bean.getThreadAllocatedBytes(thread);
            for (int i = 0; i < 20_000; i++) {
                var rebuilt = new WrenchAllocator.Workspace(count);
                fillInputs(rebuilt, i);
                borrowedSink = rebuilt.solve();
            }
            reportBytes(bean, thread, before, count, "context_rebuilt_each_step");
        }
    }

    /** 重写当前目标和全部安装点效能，模拟一体化上下文的常规物理步使用。 */
    private static void fillInputs(WrenchAllocator.Workspace inputs, int frame) {
        double angle = frame % 2 == 0 ? 0 : .1;
        double y = Math.cos(angle);
        double z = Math.sin(angle);
        inputs.target(0, (100 + frame % 2) * y, (100 + frame % 2) * z, 0, 0, 0);
        for (int actuator = 0; actuator < inputs.actuatorCount(); actuator++) {
            inputs.forceAtOffset(actuator, 0, 20 * y, 20 * z, (actuator % 8 - 3.5) / 4,
                    0, (actuator / 8 - 3.5) / 4);
        }
    }

    /** 在计数区间结束后打印结果，输出字符串的分配不计入被测物理步。 */
    private static void reportBytes(com.sun.management.ThreadMXBean bean, long thread, long before,
                                    int count, String mode) {
        long bytes = bean.getThreadAllocatedBytes(thread) - before;
        System.out.println("{\"actuators\":" + count + ",\"mode\":\"" + mode
                + "\",\"bytes_per_call\":" + bytes / 20_000.0 + "}");
    }
}
