package io.github.sweetzonzi.machine_max.util;

import java.util.Arrays;
import java.util.Objects;

/**
 * 与物理引擎无关的批量控制分配基线：阻尼伪逆、批量钉界、剩余列重算。
 * <p>每列表示一个器件单位指令的力／力矩增量，最多六行。一次同时固定所有越界指令，
 * 本次调用内不释放，因此结果有界但不保证所有可达目标均能达成。
 * 实际施力应由调用方将指令经过响应模型后代回器件力函数，不能直接施加期望 wrench。</p>
 * <p>本类无全局可变状态。静态求解入口借用外部输入，不修改、不保留；调用期间调用方不得修改。
 * Workspace(器件数) 则拥有一份输入，由调用方在两次求解之间通过其更新方法修改。
 * 工作区应由同一物理线程独占；solve 返回借用结果供本线程立即消费，
 * allocate 或显式 snapshot 则产生拥有独立数组的不可变结果。</p>
 */
public final class WrenchAllocator {
    private static final int MAX_ROWS = 6;

    private WrenchAllocator() {
    }

    /**
     * 一次分配的输入，所有数组均为调用方持有的借用数据。
     *
     * @param columns 按器件排列的列，每列长度等于目标行数；所有数据采用同一坐标系和参考质心
     * @param lowerBounds 指令下界，须有限且不大于零
     * @param upperBounds 指令上界，须有限且不小于零；上下界均为零表示关闭
     * @param target 作动器需要提供的 wrench 增量，已知外力／气动基准由调用方预先扣除
     * @param rowScales 各行的正缩放，用于量纲归一化与软优先级
     * @param actuatorCosts 各器件的正努力代价，组成对角矩阵 W
     * @param activeRows 有效行掩码；被关闭行的目标不参与求解和残差
     * @see Workspace 可直接持有并更新六维输入的求解上下文
     */
    public record Problem(double[][] columns, double[] lowerBounds, double[] upperBounds,
                          double[] target, double[] rowScales, double[] actuatorCosts,
                          boolean[] activeRows) {
    }

    /**
     * 归一化空间的正则配置；一次分配内固定阻尼强度。
     *
     * @param regularizationRatio 相对正则比例，允许零，此时仅使用绝对正下限
     * @param minimumDamping 阻尼 λ 的正下限，其平方必须可表示为有限正数
     */
    public record Settings(double regularizationRatio, double minimumDamping) {
        public static final Settings DEFAULT = new Settings(1e-4, 1e-6);

        /** 校验正则参数，避免下限平方上溢或下溢使正定性失去前提。 */
        public Settings {
            require(Double.isFinite(regularizationRatio) && regularizationRatio >= 0,
                    "正则比例必须为有限非负数");
            double square = minimumDamping * minimumDamping;
            require(Double.isFinite(minimumDamping) && minimumDamping > 0
                            && Double.isFinite(square) && square > 0,
                    "阻尼下限及其平方必须为有限正数");
        }
    }

    /** 求解状态不表示目标已经达成，是否完成需求应读取残差。 */
    public enum Status {
        SOLVED, NO_AUTHORITY, NO_ACTIVE_ROWS, NUMERICAL_FAILURE
    }

    /** 指令在最终幅值区间中的位置；FIXED 表示器件被配置为零权限。 */
    public enum BoundState {
        FREE, LOWER, UPPER, FIXED
    }

    /**
     * 输入与数值暂存统一的物理线程上下文，只允许单线程、非重入使用。
     * 用 Workspace(器件数) 一次创建六维输入，按步更新目标与器件列，再直接 solve。
     * 无参构造仅供外部 Problem 的低层求解使用，不持有调用方的输入。
     * 矩阵缓冲始终为 6×6，容量稳定后更新与求解不分配对象，不保存跨步暖启动状态。
     */
    public static final class Workspace {
        private final Problem problem;
        private double[] normalizedColumns = new double[0];
        private double[] inverseCosts = new double[0];
        private double[] controls = new double[0];
        private BoundState[] bounds = new BoundState[0];
        private boolean[] free = new boolean[0];
        private final int[] rows = new int[MAX_ROWS];
        private final double[] normalizedTarget = new double[MAX_ROWS];
        private final double[] matrix = new double[MAX_ROWS * MAX_ROWS];
        private final double[] rightHand = new double[MAX_ROWS];
        private final double[] solution = new double[MAX_ROWS];
        private final double[] achieved = new double[MAX_ROWS];
        private final double[] residual = new double[MAX_ROWS];
        private final Solution output = new Solution(this);

        /** 创建仅含求解暂存的工作区，供接受外部 Problem 的静态入口使用。 */
        public Workspace() {
            problem = null;
        }

        /**
         * 为固定数量器件创建一体化上下文，分量顺序为 Fx、Fy、Fz、Tx、Ty、Tz。
         * 初始列与目标为零，行全部启用，代价／缩放为一，幅值界为 [0,1]。
         * 数量改变时重建上下文；方向、作用点、推力与权限改变只需覆盖对应数据。
         *
         * @param actuatorCount 器件数量，允许零
         */
        public Workspace(int actuatorCount) {
            require(actuatorCount >= 0, "器件数量不能为负");
            double[] upper = new double[actuatorCount];
            double[] costs = new double[actuatorCount];
            double[] scales = new double[MAX_ROWS];
            boolean[] active = new boolean[MAX_ROWS];
            Arrays.fill(upper, 1);
            Arrays.fill(costs, 1);
            Arrays.fill(scales, 1);
            Arrays.fill(active, true);
            problem = new Problem(new double[actuatorCount][MAX_ROWS], new double[actuatorCount], upper,
                    new double[MAX_ROWS], scales, costs, active);
            ensureCapacity(actuatorCount);
        }

        /** 获取自带输入的器件数；无参暂存工作区不提供此接口。 */
        public int actuatorCount() { return problem().columns.length; }

        /**
         * 获取自带输入的同一个借用视图，用于低层互操作或诊断；普通调用直接更新本工作区即可。
         * 视图随更新改变，不能作为历史快照，也不能在求解期间并发修改。
         */
        public Problem problem() {
            if (problem == null) throw new IllegalStateException("更新自带输入需使用 Workspace(器件数) 构造");
            return problem;
        }

        /** 按默认配置求解自带输入，返回下一次求解会覆盖的借用结果。 */
        public Solution solve() { return solve(Settings.DEFAULT); }

        /**
         * 物理线程按指定配置求解自带输入。每次重新构造归一化矩阵并清空临时指令／钉界标记，
         * 喷口方向变化立即进入本次计算，不复用上一物理步的解或矩阵分解。
         */
        public Solution solve(Settings settings) { return WrenchAllocator.solve(problem(), settings, this); }

        /**
         * 覆盖本步目标 wrench；各分量必须与器件列采用同一坐标系和参考质心。
         * 先写净需求，再按需要调用 subtractKnown 扣除已知基准。
         */
        public Workspace target(double fx, double fy, double fz, double tx, double ty, double tz) {
            requireFiniteWrench(fx, fy, fz, tx, ty, tz);
            double[] target = problem().target();
            target[0] = fx; target[1] = fy; target[2] = fz;
            target[3] = tx; target[4] = ty; target[5] = tz;
            return this;
        }

        /** 清空目标供累积构造，不改变配装列、界、代价或行配置。 */
        public Workspace clearTarget() {
            Arrays.fill(problem().target(), 0);
            return this;
        }

        /**
         * 从当前目标扣除一份已知 wrench，得到作动器需要承担的剩余需求。
         * 此方法会累积扣除，每步应先调用 target 或 clearTarget，避免把上一帧的减项重复计入。
         */
        public Workspace subtractKnown(double fx, double fy, double fz, double tx, double ty, double tz) {
            requireFiniteWrench(fx, fy, fz, tx, ty, tz);
            double[] target = problem().target();
            // 复用 target 的整体校验，任何差值溢出时都不写入半份目标。
            return target(target[0] - fx, target[1] - fy, target[2] - fz,
                    target[3] - tx, target[4] - ty, target[5] - tz);
        }

        /** 覆盖一列当前单位指令的 wrench，适用于已有割线列或带固有力矩的器件。 */
        public Workspace wrench(int actuator, double fx, double fy, double fz, double tx, double ty, double tz) {
            requireFiniteWrench(fx, fy, fz, tx, ty, tz);
            double[] column = problem().columns()[actuator];
            column[0] = fx; column[1] = fy; column[2] = fz;
            column[3] = tx; column[4] = ty; column[5] = tz;
            return this;
        }

        /**
         * 用单位指令的力与相对质心的作用点构造效能列，自动计算 r×F。
         * 力和位移必须已经转入同一坐标系；rx／ry／rz 是位移而非世界绝对位置。
         * 本方法只更新列，保留器件既有幅值界和代价。
         */
        public Workspace forceAtOffset(int actuator, double fx, double fy, double fz,
                                       double rx, double ry, double rz) {
            require(Double.isFinite(rx) && Double.isFinite(ry) && Double.isFinite(rz), "相对质心位移必须有限");
            // 同一份力同时决定平移与旋转，禁止把它们当成独立可调的两列。
            return wrench(actuator, fx, fy, fz, ry * fz - rz * fy, rz * fx - rx * fz, rx * fy - ry * fx);
        }

        /** 覆盖纯力矩列并清零力分量；幅值界保留，双向器件需用 limits 单独设置 [-1,1]。 */
        public Workspace pureTorque(int actuator, double tx, double ty, double tz) {
            return wrench(actuator, 0, 0, 0, tx, ty, tz);
        }

        /** 设置包含零的有限幅值界；正向推进器可设 [0,1]，双向器件可设 [-1,1]。 */
        public Workspace limits(int actuator, double lower, double upper) {
            require(Double.isFinite(lower) && lower <= 0 && Double.isFinite(upper) && upper >= 0,
                    "幅值界必须有限且包含零");
            problem().lowerBounds()[actuator] = lower;
            problem().upperBounds()[actuator] = upper;
            return this;
        }

        /** 关闭指定器件，保留其列数据；恢复时显式调用 limits 设置正常权限。 */
        public Workspace disable(int actuator) { return limits(actuator, 0, 0); }

        /** 设置一个器件的有限正努力代价，不改变它的物理能力。 */
        public Workspace cost(int actuator, double cost) {
            require(Double.isFinite(cost) && cost > 0, "器件代价必须有限且为正");
            problem().actuatorCosts()[actuator] = cost;
            return this;
        }

        /** 设置指定分量的有限正行缩放，行序为 Fx、Fy、Fz、Tx、Ty、Tz。 */
        public Workspace rowScale(int row, double scale) {
            require(Double.isFinite(scale) && scale > 0, "行缩放必须有限且为正");
            problem().rowScales()[row] = scale;
            return this;
        }

        /**
         * 设置一行是否参与分配；该值应从调用方实际自由度导出，上下文不另建姿态门控表。
         * 关闭一行不表示器件在该轴没有真实力或力矩。
         */
        public Workspace activeRow(int row, boolean active) {
            problem().activeRows()[row] = active;
            return this;
        }

        /** 不使用 varargs 校验六个原始数值，避免每次写列或写目标创建参数数组。 */
        private static void requireFiniteWrench(double fx, double fy, double fz, double tx, double ty, double tz) {
            require(Double.isFinite(fx) && Double.isFinite(fy) && Double.isFinite(fz)
                    && Double.isFinite(tx) && Double.isFinite(ty) && Double.isFinite(tz), "力与力矩分量必须有限");
        }

        /** 按本次器件数量扩容，避免每轮钉界都建立新矩阵或临时列。 */
        private void ensureCapacity(int count) {
            if (controls.length < count) {
                controls = new double[count];
                bounds = new BoundState[count];
                free = new boolean[count];
                inverseCosts = new double[count];
                normalizedColumns = new double[Math.multiplyExact(count, MAX_ROWS)];
            }
            Arrays.fill(controls, 0, count, 0);
            Arrays.fill(free, 0, count, false);
        }
    }

    /**
     * 工作区上的只读借用结果，供物理线程立即消费。
     * <p>下一次使用同一工作区求解会覆盖该对象及其数据；禁止将它直接跨线程发布或保存为历史值。
     * 需要保存时调用 snapshot，或复制到调用方持有的目标数组。容量稳定后的 solve 与标量访问不创建对象。</p>
     */
    public static final class Solution {
        private final Workspace workspace;
        private int actuatorCount;
        private int rowCount;
        private Status status = Status.NO_ACTIVE_ROWS;
        private double dampingSquared;
        private int decompositions;
        private int clampRounds;

        /** 绑定工作区，输出数据及其有效期由工作区管理。 */
        private Solution(Workspace workspace) { this.workspace = workspace; }

        /** 获取本次有效的器件数量，不暴露工作区容量。 */
        public int actuatorCount() { return actuatorCount; }

        /** 获取输入分量数量，含被掩码关闭的行。 */
        public int rowCount() { return rowCount; }

        /** 读取器件指令，返回值只对本次求解有效。 */
        public double control(int actuator) { return workspace.controls[Objects.checkIndex(actuator, actuatorCount)]; }

        /** 读取原物理单位的有效行预测出力，死行恒为零。 */
        public double achieved(int row) { return workspace.achieved[Objects.checkIndex(row, rowCount)]; }

        /** 读取原物理单位的有效行残差，死行恒为零。 */
        public double residual(int row) { return workspace.residual[Objects.checkIndex(row, rowCount)]; }

        /** 读取器件最终的边界位置。 */
        public BoundState boundState(int actuator) { return workspace.bounds[Objects.checkIndex(actuator, actuatorCount)]; }

        /** 读取求解状态，SOLVED 不表示目标精确达成。 */
        public Status status() { return status; }

        /** 读取一次级联内固定的 λ²。 */
        public double dampingSquared() { return dampingSquared; }

        /** 读取本次小矩阵分解尝试次数。 */
        public int decompositions() { return decompositions; }

        /** 读取本次批量钉界轮数。 */
        public int clampRounds() { return clampRounds; }

        /** 将本次指令复制到调用方预分配的数组，不建立中间数组。 */
        public void copyControlsTo(double[] destination, int offset) {
            System.arraycopy(workspace.controls, 0, destination, offset, actuatorCount);
        }

        /**
         * 按需建立独立不可变快照；该方法会分配对象与数组。
         * 跨线程发布由调用方使用自己的安全发布通道完成。
         */
        public Result snapshot() {
            return new Result(Arrays.copyOf(workspace.controls, actuatorCount),
                    Arrays.copyOf(workspace.achieved, rowCount), Arrays.copyOf(workspace.residual, rowCount),
                    Arrays.copyOf(workspace.bounds, actuatorCount), status, dampingSquared, decompositions, clampRounds);
        }
    }

    /** 独立于工作区的不可变输出；有效行的预测出力与残差保留原物理单位。 */
    public static final class Result {
        private final double[] controls;
        private final double[] achieved;
        private final double[] residual;
        private final BoundState[] bounds;
        private final Status status;
        private final double dampingSquared;
        private final int decompositions;
        private final int clampRounds;

        /** 仅由分配器接收新建数组，不向外暴露其可变引用。 */
        private Result(double[] controls, double[] achieved, double[] residual, BoundState[] bounds,
                       Status status, double dampingSquared, int decompositions, int clampRounds) {
            this.controls = controls;
            this.achieved = achieved;
            this.residual = residual;
            this.bounds = bounds;
            this.status = status;
            this.dampingSquared = dampingSquared;
            this.decompositions = decompositions;
            this.clampRounds = clampRounds;
        }

        /** 获取指定器件的指令，不产生数组副本。 */
        public double control(int actuator) { return controls[actuator]; }

        /** 获取指定有效行的模型预测出力；死行恒为零，不代表真实器件在该轴没有分量。 */
        public double achieved(int row) { return achieved[row]; }

        /** 获取有效行上的 target − achieved；死行恒为零。 */
        public double residual(int row) { return residual[row]; }

        /** 获取指令与边界的关系，不能仅凭此判断整个目标可达与否。 */
        public BoundState boundState(int actuator) { return bounds[actuator]; }

        /** 获取求解状态，SOLVED 仍可能具有非零残差。 */
        public Status status() { return status; }

        /** 获取本次固定使用的 λ²；无需求或无权限的快捷出口返回零。 */
        public double dampingSquared() { return dampingSquared; }

        /** 获取小矩阵分解的尝试次数，用于验证每步计算预算。 */
        public int decompositions() { return decompositions; }

        /** 获取实际发生批量钉界的轮数。 */
        public int clampRounds() { return clampRounds; }

        /** 获取所有器件指令的独立副本，供快照或离线诊断使用。 */
        public double[] controls() { return controls.clone(); }

        /** 获取全部有效行预测出力的独立副本。 */
        public double[] achieved() { return achieved.clone(); }

        /** 获取全部有效行原始分配残差的独立副本。 */
        public double[] residual() { return residual.clone(); }
    }

    /**
     * 使用默认配置与独立工作区分配，适合测试和低频调用。
     * 高频物理步应复用 {@link Workspace} 并调用 solve，按发布频率显式生成快照。
     *
     * @param problem 借用输入
     * @return 独立的有限、有界指令快照
     * @throws IllegalArgumentException 输入尺寸、范围或有限性不符合契约
     */
    public static Result allocate(Problem problem) {
        return allocate(problem, Settings.DEFAULT, new Workspace());
    }

    /**
     * 批量阻尼求解：一次固定所有越界项，随后对自由列重新分配剩余需求。
     * <p>不修改输入；最多进行器件数次钉界，分解维度不超过六。
     * 数值失败时丢弃整组候选，返回零指令、完整有效行需求与明确失败状态。</p>
     *
     * @param problem 借用输入，调用期间不得修改其数组
     * @param settings 正则配置
     * @param workspace 调用线程独占的复用工作区
     * @return 可跨线程发布的独立快照，残差包含级联与正则化损失
     */
    public static Result allocate(Problem problem, Settings settings, Workspace workspace) {
        return solve(problem, settings, workspace).snapshot();
    }

    /**
     * 物理线程中的复用求解入口，容量稳定后的正常、无权限与数值失败路径均不分配堆对象。
     * 数学流程与 allocate 相同，区别仅在输出的所有权和生命周期。
     *
     * @param problem 借用输入，调用期间不得修改其数组
     * @param settings 可复用的正则配置
     * @param workspace 当前线程独占、非重入使用的暂存区
     * @return 工作区中的同一个借用对象，下一次求解会覆盖；保存或跨线程发布前须生成快照
     */
    public static Solution solve(Problem problem, Settings settings, Workspace workspace) {
        validate(problem);
        Objects.requireNonNull(settings, "正则配置");
        Objects.requireNonNull(workspace, "工作区");
        int count = problem.columns.length;
        workspace.ensureCapacity(count);
        int rowCount = 0;
        boolean zeroTarget = true;
        for (int row = 0; row < problem.target.length; row++) {
            if (problem.activeRows[row]) {
                workspace.rows[rowCount++] = row;
                zeroTarget &= problem.target[row] == 0;
            }
        }
        if (rowCount == 0) return finish(problem, workspace, Status.NO_ACTIVE_ROWS, 0, 0, 0);
        if (zeroTarget) return finish(problem, workspace, Status.SOLVED, 0, 0, 0);

        // 只把活行带入小矩阵；关闭或完全无效的列保持零指令。
        for (int row = 0; row < rowCount; row++) {
            int source = workspace.rows[row];
            double value = problem.target[source] * problem.rowScales[source];
            if (!Double.isFinite(value)) return failure(problem, workspace, 0, 0, 0);
            workspace.normalizedTarget[row] = value;
        }
        double trace = 0;
        int freeCount = 0;
        for (int actuator = 0; actuator < count; actuator++) {
            double inverseCost = 1 / problem.actuatorCosts[actuator];
            if (!Double.isFinite(inverseCost) || inverseCost == 0) {
                return failure(problem, workspace, 0, 0, 0);
            }
            workspace.inverseCosts[actuator] = inverseCost;
            boolean nonzero = false;
            double columnTrace = 0;
            for (int row = 0; row < rowCount; row++) {
                int source = workspace.rows[row];
                double value = problem.columns[actuator][source] * problem.rowScales[source];
                if (!Double.isFinite(value)) return failure(problem, workspace, 0, 0, 0);
                workspace.normalizedColumns[actuator * MAX_ROWS + row] = value;
                nonzero |= value != 0;
                columnTrace += (value * inverseCost) * value;
            }
            if (nonzero && problem.lowerBounds[actuator] != problem.upperBounds[actuator]) {
                trace += columnTrace;
                workspace.free[actuator] = true;
                freeCount++;
            }
        }
        if (freeCount == 0) return finish(problem, workspace, Status.NO_AUTHORITY, 0, 0, 0);
        double dampingSquared = Math.max(settings.minimumDamping * settings.minimumDamping,
                settings.regularizationRatio * (trace / rowCount));
        if (!Double.isFinite(trace) || !Double.isFinite(dampingSquared)) {
            return failure(problem, workspace, 0, 0, 0);
        }

        int decompositions = 0;
        int clampRounds = 0;
        while (freeCount > 0) {
            // 固定列只从目标扣一次，自由列组成 A W^-1 A^T；不构造 N×N 矩阵。
            Arrays.fill(workspace.matrix, 0);
            for (int row = 0; row < rowCount; row++) {
                workspace.matrix[row * MAX_ROWS + row] = dampingSquared;
                workspace.rightHand[row] = workspace.normalizedTarget[row];
            }
            for (int actuator = 0; actuator < count; actuator++) {
                int offset = actuator * MAX_ROWS;
                for (int row = 0; row < rowCount; row++) {
                    double a = workspace.normalizedColumns[offset + row];
                    if (workspace.free[actuator]) {
                        for (int column = 0; column <= row; column++) {
                            workspace.matrix[row * MAX_ROWS + column] += a
                                    * workspace.inverseCosts[actuator]
                                    * workspace.normalizedColumns[offset + column];
                        }
                    } else {
                        workspace.rightHand[row] -= a * workspace.controls[actuator];
                    }
                }
            }
            decompositions++;
            if (!solvePositiveDefinite(workspace, rowCount)) {
                return failure(problem, workspace, dampingSquared, decompositions, clampRounds);
            }

            // 先得到整批自由指令，再把所有越界项一起固定，本次调用内不释放。
            int newlyFixed = 0;
            for (int actuator = 0; actuator < count; actuator++) {
                if (!workspace.free[actuator]) continue;
                double value = 0;
                for (int row = 0; row < rowCount; row++) {
                    value += workspace.normalizedColumns[actuator * MAX_ROWS + row]
                            * workspace.solution[row];
                }
                value *= workspace.inverseCosts[actuator];
                if (!Double.isFinite(value)) {
                    return failure(problem, workspace, dampingSquared, decompositions, clampRounds);
                }
                double bounded = Math.clamp(value, problem.lowerBounds[actuator], problem.upperBounds[actuator]);
                workspace.controls[actuator] = bounded;
                if (bounded != value) {
                    workspace.free[actuator] = false;
                    newlyFixed++;
                }
            }
            if (newlyFixed == 0) break;
            clampRounds++;
            freeCount -= newlyFixed;
        }
        return finish(problem, workspace, Status.SOLVED, dampingSquared, decompositions, clampRounds);
    }

    /** 原地 Cholesky 分解下三角，再前代／回代；任何非有限值或非正主元均明确失败。 */
    private static boolean solvePositiveDefinite(Workspace workspace, int size) {
        double[] matrix = workspace.matrix;
        for (int row = 0; row < size; row++) {
            for (int column = 0; column <= row; column++) {
                double value = matrix[row * MAX_ROWS + column];
                for (int k = 0; k < column; k++) {
                    value -= matrix[row * MAX_ROWS + k] * matrix[column * MAX_ROWS + k];
                }
                if (!Double.isFinite(value) || (row == column && value <= 0)) return false;
                matrix[row * MAX_ROWS + column] = row == column
                        ? Math.sqrt(value) : value / matrix[column * MAX_ROWS + column];
                if (!Double.isFinite(matrix[row * MAX_ROWS + column])) return false;
            }
            double value = workspace.rightHand[row];
            for (int k = 0; k < row; k++) value -= matrix[row * MAX_ROWS + k] * workspace.solution[k];
            workspace.solution[row] = value / matrix[row * MAX_ROWS + row];
            if (!Double.isFinite(workspace.solution[row])) return false;
        }
        for (int row = size - 1; row >= 0; row--) {
            double value = workspace.solution[row];
            for (int k = row + 1; k < size; k++) value -= matrix[k * MAX_ROWS + row] * workspace.solution[k];
            workspace.solution[row] = value / matrix[row * MAX_ROWS + row];
            if (!Double.isFinite(workspace.solution[row])) return false;
        }
        return true;
    }

    /** 失败候选整组归零，保证调用方能按明确状态执行停机／滞后响应规则。 */
    private static Solution failure(Problem problem, Workspace workspace, double dampingSquared,
                                  int decompositions, int clampRounds) {
        Arrays.fill(workspace.controls, 0, problem.columns.length, 0);
        return finish(problem, workspace, Status.NUMERICAL_FAILURE,
                dampingSquared, decompositions, clampRounds);
    }

    /** 在原物理单位重算输出，不把缩放后的残差误传给施力或诊断层。 */
    private static Solution finish(Problem problem, Workspace workspace, Status status,
                                 double dampingSquared, int decompositions, int clampRounds) {
        int count = problem.columns.length;
        int rows = problem.target.length;
        double[] controls = workspace.controls;
        double[] achieved = workspace.achieved;
        double[] residual = workspace.residual;
        Arrays.fill(achieved, 0);
        Arrays.fill(residual, 0);
        for (int row = 0; row < rows; row++) {
            if (!problem.activeRows[row]) continue;
            for (int actuator = 0; actuator < count; actuator++) {
                achieved[row] += problem.columns[actuator][row] * controls[actuator];
            }
            residual[row] = problem.target[row] - achieved[row];
            if (!Double.isFinite(achieved[row]) || !Double.isFinite(residual[row])) {
                return failure(problem, workspace, dampingSquared, decompositions, clampRounds);
            }
        }
        for (int actuator = 0; actuator < count; actuator++) {
            workspace.bounds[actuator] = problem.lowerBounds[actuator] == problem.upperBounds[actuator] ? BoundState.FIXED
                    : controls[actuator] == problem.lowerBounds[actuator] ? BoundState.LOWER
                    : controls[actuator] == problem.upperBounds[actuator] ? BoundState.UPPER : BoundState.FREE;
        }
        Solution output = workspace.output;
        output.actuatorCount = count;
        output.rowCount = rows;
        output.status = status;
        output.dampingSquared = dampingSquared;
        output.decompositions = decompositions;
        output.clampRounds = clampRounds;
        return output;
    }

    /** 一次线性扫描校验输入，死行数据也要求有限，防止错误配置静默进入后续施力链。 */
    private static void validate(Problem problem) {
        Objects.requireNonNull(problem, "分配问题");
        Objects.requireNonNull(problem.columns, "效能列");
        Objects.requireNonNull(problem.target, "目标");
        Objects.requireNonNull(problem.lowerBounds, "下界");
        Objects.requireNonNull(problem.upperBounds, "上界");
        Objects.requireNonNull(problem.rowScales, "行缩放");
        Objects.requireNonNull(problem.actuatorCosts, "器件代价");
        Objects.requireNonNull(problem.activeRows, "行掩码");
        int rows = problem.target.length;
        int count = problem.columns.length;
        require(rows <= MAX_ROWS, "最多接受六个力／力矩分量");
        require(problem.rowScales.length == rows && problem.activeRows.length == rows, "行数据尺寸不一致");
        require(problem.lowerBounds.length == count && problem.upperBounds.length == count
                && problem.actuatorCosts.length == count, "器件数据尺寸不一致");
        for (int row = 0; row < rows; row++) {
            require(Double.isFinite(problem.target[row]), "目标必须有限");
            require(Double.isFinite(problem.rowScales[row]) && problem.rowScales[row] > 0, "行缩放必须有限且为正");
        }
        for (int actuator = 0; actuator < count; actuator++) {
            require(problem.columns[actuator] != null && problem.columns[actuator].length == rows, "效能列尺寸不一致");
            require(Double.isFinite(problem.lowerBounds[actuator]) && problem.lowerBounds[actuator] <= 0
                    && Double.isFinite(problem.upperBounds[actuator]) && problem.upperBounds[actuator] >= 0,
                    "幅值界必须有限且包含零");
            require(Double.isFinite(problem.actuatorCosts[actuator]) && problem.actuatorCosts[actuator] > 0,
                    "器件代价必须有限且为正");
            for (double value : problem.columns[actuator]) require(Double.isFinite(value), "效能列必须有限");
        }
    }

    /** 将配置错误与正常的能力不足分开，输入错误由调用方明确处理。 */
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
