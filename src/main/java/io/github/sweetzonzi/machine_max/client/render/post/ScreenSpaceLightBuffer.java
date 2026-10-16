package io.github.sweetzonzi.machine_max.client.render.post;

import io.github.sweetzonzi.machine_max.MachineMax;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * 通用屏幕空间光照的光源表：数值校验、容量选择、扁平数组与逐来源诊断计数，不含任何 GL 调用。
 *
 * <p>每帧的生命周期固定为 {@link #beginFrame} → 每个来源一组 {@link #beginSource} + {@link #submit}
 * → {@link #endFrame}。槽位选择规则是 {@code (来源优先级升序, 投影半径降序)}，同键先到先留；
 * 优先级不是保留名额，高优先级来源占满容量时低优先级来源会被全部排除（设计文档 §5.2）。</p>
 *
 * <p><b>公共剔除做数值校验与影响体剔除。</b>数值侧是有限性、非负、零半径/零强度；几何侧按光源的
 * <b>影响球</b>判定——光心距离超过渲染距离加半径、整个球位于近裁面之后、球与视锥不相交，三条之一
 * 成立才剔除。光心在相机背后或光心投影离屏本身都不构成剔除理由：球的一部分仍可能落在可见表面上，
 * 而这类光源（爆炸火光）正是曳光不需要处理的形态。据此本类不采用“背后光心即丢弃”这条不区分
 * 来源的规则（设计文档 §4.3）。</p>
 *
 * <p>来源特有的剔除留在来源里：曳光的光心背后、渲染距离与屏幕投影下限三条仍由
 * {@link ProjectileTracerLightSource} 执行，因此该来源的候选集与拆出通用通路之前逐一对应
 * （设计文档 §4.3、§7）。</p>
 *
 * <p><b>P1 的阶段能力在提交处显式报告</b>：锥光（{@link ScreenSpaceLight.Shape#SPOT}）与 S3
 * （{@link ScreenSpaceLight.OcclusionMode#SCREEN_RAY}）返回 {@link SubmitResult#UNSUPPORTED}，
 * 不会按球光或按 S2 悄悄渲染；两者接入 GPU 编码后本条判断才放开（设计文档 §4.1、§10 的 P5）。</p>
 *
 * <p>渲染线程独占：本类不持有锁，也不接受物理线程写入提交记录。</p>
 */
public final class ScreenSpaceLightBuffer {

    /** 提交结果：区分接收、数值非法、几何剔除、阶段未支持与容量拒绝。 */
    public enum SubmitResult {
        /** 进入光源表 */
        ACCEPTED,
        /** 字段非法：NaN/Infinity、负值、越界颜色、锥角关系错误，或未设置就提交 */
        INVALID,
        /** 数值合法但不产生贡献：零半径、零强度，或影响球被距离/近裁面/视锥剔除 */
        CULLED,
        /** 形状或遮挡策略在当前阶段没有 GPU 编码 */
        UNSUPPORTED,
        /** 通过了公共剔除，但容量表没有位置给它 */
        CAPACITY_REJECTED
    }

    /** 容量触顶汇总日志的最小间隔（ms）；短暂不触顶不会绕过这个限制 */
    private static final long CAPACITY_LOG_INTERVAL_MS = 5_000L;
    /** 近裁面（m）：影响球与它相交时改用保守评分，避免投影半径成为 Infinity */
    private static final float NEAR_PLANE_M = 0.05f;
    /** 锥光方向被视为单位向量时允许的偏差（对 lengthSquared 的比较） */
    private static final float UNIT_DIRECTION_TOLERANCE = 1e-3f;

    private final int capacity;
    /** 每槽 4 个：视空间光心 xyz、影响半径（m） */
    private final float[] positionRadius;
    /** 每槽 4 个：颜色 rgb、强度（已乘全局倍率） */
    private final float[] colorIntensity;
    /** 每槽 1 个：S2 的绝对容差（m） */
    private final float[] sourceDepthTolerance;
    /** 每槽 1 个：遮挡策略编码，{@code 0} = 不做遮挡判定，{@code 1} = 在光心投影处采一次深度（S2） */
    private final float[] occlusion;
    /** 槽内条目的投影半径（px），只用于排序与“超限时置换最弱一个” */
    private final float[] sortRadiusPx;
    /** 槽内条目的来源优先级，只用于容量比较 */
    private final int[] slotPriority;
    /** 槽内条目的来源身份，只用于“被置换数”的归属 */
    private final ResourceLocation[] slotSourceId;

    private int count;
    private float focalPx = 1f;
    private float globalIntensity = 1f;
    private float screenDiagonal = 1f;
    private float renderDistance = 0f;
    /** 投影矩阵的 m00 / m11 绝对值，只用于构造视锥侧平面；0 表示投影退化 */
    private float projectionM00 = 0f;
    private float projectionM11 = 0f;
    private boolean capped;
    private boolean cappedLastFrame;
    private long lastCapacityLogMs;
    private long lastInvalidLogMs;

    @Nullable
    private ScreenSpaceLightSource currentSource;
    private final Map<ResourceLocation, SourceStats> statsBySource = new HashMap<>();

    /**
     * @param capacity 槽位数，必须为正；数组长度按 {@code capacity * 4} 与 {@code capacity} 派生，
     *                 因此 Java 数组、程序 JSON 的 {@code count} 与 GLSL 展开后的标量数量
     *                 必须与它一致（设计文档 §4.5）
     */
    public ScreenSpaceLightBuffer(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity 必须为正: " + capacity);
        this.capacity = capacity;
        this.positionRadius = new float[capacity * 4];
        this.colorIntensity = new float[capacity * 4];
        this.sourceDepthTolerance = new float[capacity];
        this.occlusion = new float[capacity];
        this.sortRadiusPx = new float[capacity];
        this.slotPriority = new int[capacity];
        this.slotSourceId = new ResourceLocation[capacity];
    }

    /**
     * 开始一帧：清空槽位、重置逐来源的本帧计数，并冻结本帧的投影与渲染距离。
     *
     * @param frame           本帧的只读上下文，提供像素焦距、视口尺寸、投影矩阵与渲染距离
     * @param globalIntensity 全局强度倍率，由本类在写入槽位时乘且只乘一次
     */
    public void beginFrame(LightFrameContext frame, float globalIntensity) {
        count = 0;
        capped = false;
        this.globalIntensity = Float.isFinite(globalIntensity) ? globalIntensity : 1f;
        float focal = frame.focalPx();
        this.focalPx = focal > 0f ? focal : 1f;
        double width = frame.viewportWidth();
        double height = frame.viewportHeight();
        this.screenDiagonal = (float) Math.sqrt(width * width + height * height);
        this.renderDistance = frame.renderDistance();
        this.projectionM00 = Math.abs(frame.projectionMatrix().m00());
        this.projectionM11 = Math.abs(frame.projectionMatrix().m11());
        for (SourceStats stats : statsBySource.values()) stats.resetFrame();
    }

    /**
     * 切换当前来源：随后提交的条目都会盖上它的身份与优先级，计数也归到它名下。
     *
     * @param source 本帧要采集的来源
     */
    public void beginSource(ScreenSpaceLightSource source) {
        this.currentSource = source;
        statsFor(source.id()).priority = source.priority();
    }

    /**
     * 提交一条光源。数值在本调用内被复制，因此调用方可以复用同一个载体。
     *
     * <p>若当前没有经过 {@link #beginSource} 就提交，本条按非法处理且不归入任何来源。</p>
     *
     * @param light 已由 {@code setPoint}/{@code setSpot} 写齐的载体
     * @return 提交结果，同时累加进逐来源计数
     */
    public SubmitResult submit(ScreenSpaceLight light) {
        ScreenSpaceLightSource source = currentSource;
        if (source == null) {
            light.clear();
            return SubmitResult.INVALID;
        }
        light.stamp(source.id(), source.priority());
        SourceStats stats = statsFor(source.id());
        stats.submitted++;
        SubmitResult result = commit(light, source, stats);
        light.clear();
        return result;
    }

    /**
     * 结束一帧：统计各来源的最终入选数，并按限频发汇总日志。
     */
    public void endFrame() {
        for (SourceStats stats : statsBySource.values()) stats.selected = 0;
        for (int slot = 0; slot < count; slot++) {
            ResourceLocation id = slotSourceId[slot];
            if (id != null) statsFor(id).selected++;
        }
        logDiagnostics();
        cappedLastFrame = capped;
    }

    /** @return 本帧入选的光源数；0 表示本帧没有 GPU 工作 */
    public int count() {
        return count;
    }

    /** @return 槽位数（编译期容量） */
    public int capacity() {
        return capacity;
    }

    /**
     * @return 本帧有效候选是否超过容量；发生直接拒绝与发生槽位置换都算，不是只统计实际置换
     */
    public boolean isCapped() {
        return capped;
    }

    /** @return 扁平数组 {@code [capacity * 4]}：每槽 {@code (x, y, z, radius)}，直接作为数组 uniform 上传 */
    public float[] positionRadius() {
        return positionRadius;
    }

    /** @return 扁平数组 {@code [capacity * 4]}：每槽 {@code (r, g, b, intensity)} */
    public float[] colorIntensity() {
        return colorIntensity;
    }

    /** @return 扁平数组 {@code [capacity]}：每槽 S2 的绝对容差（m） */
    public float[] sourceDepthTolerance() {
        return sourceDepthTolerance;
    }

    /** @return 扁平数组 {@code [capacity]}：每槽遮挡策略编码，0 = 不做遮挡判定，1 = S2 */
    public float[] occlusion() {
        return occlusion;
    }

    /**
     * 清空诊断状态：关闭效果、0 候选与退出世界时调用，避免上一帧的触顶状态漏进下一段会话。
     */
    public void resetDiagnostics() {
        for (SourceStats stats : statsBySource.values()) stats.resetFrame();
        capped = false;
        cappedLastFrame = false;
        lastCapacityLogMs = 0L;
        lastInvalidLogMs = 0L;
        count = 0;
    }

    // ========== 内部：提交与容量 ==========

    private SubmitResult commit(ScreenSpaceLight light, ScreenSpaceLightSource source, SourceStats stats) {
        if (!light.isConfigured()) {
            stats.invalid++;
            return SubmitResult.INVALID;
        }

        Vector3f position = light.positionView();
        Vector3f color = light.color();
        float radius = light.radius();
        float intensity = light.intensity();
        float tolerance = light.sourceDepthTolerance();
        float normalWeight = light.normalWeight();
        if (!finite(position) || !finite(color)
                || !Float.isFinite(radius) || !Float.isFinite(intensity)
                || !Float.isFinite(tolerance) || !Float.isFinite(normalWeight)) {
            stats.invalid++;
            return SubmitResult.INVALID;
        }
        if (radius < 0f || intensity < 0f || tolerance < 0f
                || normalWeight < 0f || normalWeight > 1f
                || !normalized(color)) {
            stats.invalid++;
            return SubmitResult.INVALID;
        }
        if (light.shape() == ScreenSpaceLight.Shape.SPOT && !validCone(light)) {
            stats.invalid++;
            return SubmitResult.INVALID;
        }
        if (light.shape() != ScreenSpaceLight.Shape.POINT
                || light.occlusionMode() == ScreenSpaceLight.OcclusionMode.SCREEN_RAY) {
            stats.unsupported++;
            return SubmitResult.UNSUPPORTED;
        }
        if (radius == 0f || intensity == 0f) {
            stats.culled++;
            return SubmitResult.CULLED;
        }
        if (isOutsideInfluenceVolume(position, radius)) {
            stats.culled++;
            return SubmitResult.CULLED;
        }

        stats.candidates++;
        float sortPx = sortRadiusPx(position, radius);
        if (count < capacity) {
            write(count, light, sortPx);
            count++;
            return SubmitResult.ACCEPTED;
        }

        // 容量已满：与当前最弱的一个比较。触顶在这里标记，直接拒绝也算。
        capped = true;
        int weakest = weakestSlot();
        if (!beatsSlot(source.priority(), sortPx, weakest)) {
            stats.capacityRejected++;
            return SubmitResult.CAPACITY_REJECTED;
        }
        ResourceLocation displaced = slotSourceId[weakest];
        if (displaced != null) statsFor(displaced).displaced++;
        write(weakest, light, sortPx);
        return SubmitResult.ACCEPTED;
    }

    private void write(int slot, ScreenSpaceLight light, float sortPx) {
        Vector3f position = light.positionView();
        Vector3f color = light.color();
        int base = slot * 4;
        positionRadius[base] = position.x;
        positionRadius[base + 1] = position.y;
        positionRadius[base + 2] = position.z;
        positionRadius[base + 3] = light.radius();
        colorIntensity[base] = color.x;
        colorIntensity[base + 1] = color.y;
        colorIntensity[base + 2] = color.z;
        colorIntensity[base + 3] = light.intensity() * globalIntensity;
        sourceDepthTolerance[slot] = light.sourceDepthTolerance();
        occlusion[slot] = encodeOcclusion(light.occlusionMode());
        sortRadiusPx[slot] = sortPx;
        slotPriority[slot] = light.priority();
        slotSourceId[slot] = light.sourceId();
    }

    /**
     * 排序半径：影响球不与近裁面相交时用投影半径；相交或光心在相机背后时用有限的屏幕对角线
     * 作为保守评分。屏占比公式在除数接近 0 时会给出 Infinity，那样任何比较都失去意义。
     */
    private float sortRadiusPx(Vector3f position, float radius) {
        float frontDepth = -position.z;
        if (frontDepth - radius > NEAR_PLANE_M) return focalPx * radius / frontDepth;
        return screenDiagonal;
    }

    /**
     * 影响体剔除：距离、近裁面与视锥三条都按影响球判定（设计文档 §4.3）。
     *
     * <p>三条的保守方向一致——只在“整个球都不可能贡献”时剔除，球的一部分仍可能照亮可见表面时保留。
     * 因此光心在相机背后、光心投影离屏都不是剔除理由：爆炸火光的位置经常正好落在这些区域。</p>
     *
     * @param position 视空间光心（米）
     * @param radius   影响半径（m），调用方保证非负
     * @return 整个影响球都不可能对可见像素产生贡献时为 true
     */
    private boolean isOutsideInfluenceVolume(Vector3f position, float radius) {
        // 距离：球上离相机最近的点也在渲染距离之外
        if (position.length() - radius > renderDistance) return true;
        // 近裁面：球的最大前向深度 = -z + R，连它都不到近裁面时整球在相机与近裁面之间
        if (-position.z + radius < NEAR_PLANE_M) return true;
        return !intersectsFrustum(position, radius);
    }

    /**
     * 影响球是否与视锥相交。
     *
     * <p>侧平面由投影矩阵的 {@code m00} / {@code m11} 直接给出：透视投影下视空间点
     * {@code (x, y, z)} 的裁剪坐标满足 {@code x_clip = m00·x}、{@code w_clip = -z}，
     * 于是"在左平面内侧"即 {@code m00·x ≥ z}，对应平面法线 {@code (m00, 0, -1)}。这样取平面
     * 自动跟随 FOV、视口比例与任何改变投影的状态，不需要额外的相机参数。</p>
     *
     * <p>球完全在某个平面外侧的判据是 {@code n·C < -R·|n|}（球在该半空间的最大值仍小于 0）。
     * 用未归一化的法线比较，省掉每次开方；投影退化时不做视锥剔除，退回保守保留。</p>
     */
    private boolean intersectsFrustum(Vector3f position, float radius) {
        if (projectionM00 <= 0f || projectionM11 <= 0f) return true;
        return !outsidePlane(projectionM00, 0f, -1f, position, radius)
                && !outsidePlane(-projectionM00, 0f, -1f, position, radius)
                && !outsidePlane(0f, projectionM11, -1f, position, radius)
                && !outsidePlane(0f, -projectionM11, -1f, position, radius);
    }

    /** @return 影响球是否完全位于半空间 {@code n·p ≥ 0} 之外 */
    private static boolean outsidePlane(float nx, float ny, float nz, Vector3f position, float radius) {
        float norm = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        float center = nx * position.x + ny * position.y + nz * position.z;
        return center < -radius * norm;
    }

    /**
     * 遮挡策略的 GPU 编码：{@code 0} 不做遮挡判定，{@code 1} 在光心投影处采一次深度（S2）。
     *
     * <p>只有 {@code NONE} 与 {@code SOURCE_DEPTH} 会走到这里：{@code SCREEN_RAY} 在
     * {@link #commit} 里已按 {@link SubmitResult#UNSUPPORTED} 拒绝，锥光同理。</p>
     */
    private static float encodeOcclusion(ScreenSpaceLight.OcclusionMode mode) {
        return mode == ScreenSpaceLight.OcclusionMode.SOURCE_DEPTH ? 1f : 0f;
    }

    /** @return 最弱的槽：优先级数值最大者优先，同优先级取投影半径最小者 */
    private int weakestSlot() {
        int weakest = 0;
        for (int slot = 1; slot < count; slot++) {
            if (slotPriority[slot] > slotPriority[weakest]
                    || (slotPriority[slot] == slotPriority[weakest] && sortRadiusPx[slot] < sortRadiusPx[weakest])) {
                weakest = slot;
            }
        }
        return weakest;
    }

    /** 新候选是否强于该槽：优先级更小者胜，同优先级比投影半径，完全同键时先到先留。 */
    private boolean beatsSlot(int priority, float sortPx, int slot) {
        if (priority != slotPriority[slot]) return priority < slotPriority[slot];
        return sortPx > sortRadiusPx[slot];
    }

    // ========== 内部：校验 ==========

    private static boolean finite(Vector3f value) {
        return Float.isFinite(value.x) && Float.isFinite(value.y) && Float.isFinite(value.z);
    }

    private static boolean normalized(Vector3f color) {
        return inUnitRange(color.x) && inUnitRange(color.y) && inUnitRange(color.z);
    }

    private static boolean inUnitRange(float channel) {
        return channel >= 0f && channel <= 1f;
    }

    /** 锥光的必填关系：单位方向、两个余弦有限且 {@code cosInner >= cosOuter}、两者都在 [-1,1]。 */
    private static boolean validCone(ScreenSpaceLight light) {
        Vector3f direction = light.directionView();
        if (!finite(direction)) return false;
        float cosInner = light.cosInner();
        float cosOuter = light.cosOuter();
        if (!Float.isFinite(cosInner) || !Float.isFinite(cosOuter)) return false;
        if (cosInner < cosOuter || cosInner > 1f || cosOuter < -1f) return false;
        return Math.abs(direction.lengthSquared() - 1f) <= UNIT_DIRECTION_TOLERANCE;
    }

    // ========== 内部：诊断 ==========

    private SourceStats statsFor(ResourceLocation id) {
        return statsBySource.computeIfAbsent(id, key -> new SourceStats());
    }

    private void logDiagnostics() {
        long now = System.currentTimeMillis();
        if (capped) {
            // 开始触顶时记一条，连续触顶最多每 5 秒一条
            if (!cappedLastFrame || now - lastCapacityLogMs >= CAPACITY_LOG_INTERVAL_MS) {
                lastCapacityLogMs = now;
                MachineMax.LOGGER.warn("屏幕空间光照容量触顶：容量 {}，本帧 {} 盏，{}", capacity, count, summary());
            }
        } else if (cappedLastFrame) {
            MachineMax.LOGGER.info("屏幕空间光照不再触顶：容量 {}，本帧 {} 盏，{}", capacity, count, summary());
        }
        int invalid = 0;
        for (SourceStats stats : statsBySource.values()) invalid += stats.invalid;
        // 非法数据与容量日志分开，且同样限频，避免逐光源逐帧刷日志
        if (invalid > 0 && now - lastInvalidLogMs >= CAPACITY_LOG_INTERVAL_MS) {
            lastInvalidLogMs = now;
            MachineMax.LOGGER.error("屏幕空间光照：本帧拒绝 {} 条非法提交，{}", invalid, summary());
        }
    }

    private String summary() {
        if (statsBySource.isEmpty()) return "无来源";
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<ResourceLocation, SourceStats> entry : statsBySource.entrySet()) {
            if (!builder.isEmpty()) builder.append(' ');
            builder.append(entry.getValue().summary(entry.getKey()));
        }
        return builder.toString();
    }

    /** 逐来源的本帧计数；只由渲染线程读写。 */
    private static final class SourceStats {
        int priority;
        int submitted;
        int invalid;
        int culled;
        int unsupported;
        int candidates;
        int selected;
        int capacityRejected;
        int displaced;

        void resetFrame() {
            submitted = 0;
            invalid = 0;
            culled = 0;
            unsupported = 0;
            candidates = 0;
            selected = 0;
            capacityRejected = 0;
            displaced = 0;
        }

        String summary(ResourceLocation id) {
            return id + "[prio=" + priority
                    + " submitted=" + submitted
                    + " invalid=" + invalid
                    + " culled=" + culled
                    + " unsupported=" + unsupported
                    + " candidates=" + candidates
                    + " selected=" + selected
                    + " capRejected=" + capacityRejected
                    + " displaced=" + displaced + "]";
        }
    }
}
