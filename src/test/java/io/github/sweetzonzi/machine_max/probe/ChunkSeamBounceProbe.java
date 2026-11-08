package io.github.sweetzonzi.machine_max.probe;

import com.jme3.bullet.CollisionConfiguration;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.SolverMode;
import com.jme3.bullet.StepFlag;
import com.jme3.bullet.collision.ManifoldPoints;
import com.jme3.bullet.collision.PersistentManifolds;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import com.jme3.bullet.collision.shapes.MultiSphere;
import com.jme3.bullet.collision.shapes.SphereCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.bullet.util.NativeLibrary;
import com.jme3.math.Vector3f;
import com.simsilica.mathd.Vec3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 16×16 区块 section 地形 × 单球 compound 轮子：高速过接缝的接触异常复现。
 *
 * <p>游戏内的地形不是一整块板：{@code PhysicsChunkSection} 让每个 16×16×16 section 各自持有一个静态刚体，
 * 刚体原点在 section 中心，子形状是 {@code BlockMerger} 合并出来的方块盒。超平坦下每个 y 层合并成一块
 * 16×1×16 的盒子，相邻 section 的顶面在 {@code x = 16k} 处共面相接，接缝上只剩一条裸露的棱边。</p>
 *
 * <p>轮子是「单球」compound。compound 对 compound 走 {@code btCompoundCompoundCollisionAlgorithm}，
 * 子形状对按 {@code btDefaultCollisionConfiguration::getCollisionAlgorithmCreateFunc} 落到 convex-convex
 * （GJK/EPA，{@code USE_BUGGY_SPHERE_BOX_ALGORITHM} 未定义）。球心离前方 section 顶盒的棱线还有十几厘米时，
 * 球面到棱线的距离已经小于成对接触阈值，GJK 的最近特征点就是那条棱线：法线从棱线指向球心，带水平分量。
 * 求解器沿这条斜法线消掉接近速度 {@code v·sinθ}，冲量的竖直分量把轮子抬起；承载面的竖直接触行只能推、
 * 不能拉，抵不掉这个上升分速。抬升量与速度同阶：19 m/s 读出 +1.85 m/s、18.5 mm。</p>
 *
 * <p>量的是轮子重心的步末竖直速度与抬升量，参数与台架一致：50 kg 轮、12.5 kN 预载、10 ms 子步、摩擦与
 * 恢复系数 0；驱动时每步把水平速度钳到目标值，竖直分量保留求解器写回的值。预载在静止时每个子步先把轮子
 * 按下去 {@code (12500 / 50 + 9.81) × 0.01 = 2.5981 m/s}，承载面再把它清零，所以静止时步末竖直速度约为 0；
 * 过接缝那一步出现的正值就是被弹起的量。</p>
 *
 * <p>六组对照：{@link Layout#SECTIONS}（生产布局，每个 section 一个静态刚体）、{@link Layout#ONE_BODY}
 * （同一批盒子挂到单个刚体的 compound 上）、{@link Layout#ONE_BOX}（一整块 64 m 裸盒）、
 * {@link Layout#ONE_BOX_COMPOUND}（同一块板包进 compound，走与生产相同的 compound 对 compound 路径）、
 * {@link Layout#SECTIONS_OVERLAP}（相邻 section 的顶板在 x 方向重叠 0.5 m：接缝棱边内缩 0.25 m，
 * 但仍然存在）、{@link Layout#LEDGE}（真实台阶：高台顶面 y = 0、到 x = 16 结束，低台顶面 y = -0.5）。
 * 前五组把「刚体拆成多个」「顶面有棱边」「compound 对 compound 路径」三个因素分开；最后一组只改另一侧的高度，
 * 用来校准判据：同一个棱边几何，两侧等高才是伪影。</p>
 *
 * <p>判据（{@link Rewrite#INTERNAL_EDGE}）：棱边两侧地表等高 → 两个同高方块拼出的内部边，几何上不存在凸棱，
 * 法线归到竖直；不等高 → 真实凸棱（台阶、悬崖、洞沿），GJK 的角平分线是对的，保持原样。实现方式是
 * 取接触点最近的整数边界、比较边界两侧的柱顶；本台架用地形矩形表做柱顶查询，游戏侧对应
 * {@code terrainManager.getBlockSnapshotAt}。19 m/s 时平地接缝的斜法线 6 条全部判内部边，台阶的 2 条
 * 全部判真实棱边。</p>
 *
 * <p>棱边接触点在 {@code distance1} 为正时就带着冲量参与求解：本台架里带 20 N·s 以上冲量的斜法线点最远
 * 出现在 {@code +26.6 mm}（19 m/s），与成对接触阈值 29.2 mm 同量级；同一个子步里承载面的竖直行读到
 * 0 冲量。球心越过棱线后接触仍在，法线朝后上方（实测倾角 3°–15°），这条冲量因此同时抬升与刹车。</p>
 *
 * <p><b>探针：</b>{@code gradlew test} 不执行本类。用 {@code ./gradlew probeTest --tests "*ChunkSeamBounce*"}
 * 复核；输出是本类的主要产出，断言只钉住对照关系。</p>
 */
@EnabledOnOs(OS.WINDOWS)
class ChunkSeamBounceProbe {

    /** 轮胎球半径，与 {@link CompoundContactToleranceProbe} 同一颗。 */
    private static final float RADIUS = 0.84375f;
    private static final float MASS = 50f;
    /** K17 单轮静载。 */
    private static final float PRELOAD = 12500f;
    private static final float DT = 0.01f;
    private static final float SECTION = 16f;
    private static final int SECTIONS = 4;
    /** 超平坦的实心层数：最上面一层顶面在 y = 0。 */
    private static final int LAYERS = 4;
    private static final float HALF_Y = 0.5f;
    private static final float HALF_Z = 8f;
    /** {@link Layout#SECTIONS_OVERLAP} 的相邻顶板重叠量。 */
    private static final float OVERLAP = 0.5f;
    /** {@link Layout#LEDGE} 的低台比高台低多少。 */
    private static final float LEDGE_DROP = 0.5f;
    /** 承载面判据，与 {@code CollisionHandler#CLIMB_SUPPORT_NORMAL_Y} 一致。 */
    private static final float CLIMB_SUPPORT_NORMAL_Y = 0.7f;
    /** 接触点离整数方块边界超过这个距离时不判内部边。 */
    private static final float EDGE_BOUNDARY_TOLERANCE = 0.05f;
    private static final float START_X = 2f;
    private static final float END_X = SECTIONS * SECTION - 2f;
    private static final int SETTLE_STEPS = 200;
    private static final int MAX_STEPS = 4000;

    private enum Layout {
        /** 生产布局：每 section 一个静态刚体，compound 子形状是合并出来的方块盒。 */
        SECTIONS,
        /** 同一批盒子挂到单个刚体上：接缝仍在，刚体不拆。 */
        ONE_BODY,
        /** 一整块板：接缝与棱边都不存在。 */
        ONE_BOX,
        /** 一整块板包进 compound：没有棱边，但走 compound 对 compound 的同一条算法路径。 */
        ONE_BOX_COMPOUND,
        /** 每 section 一个刚体，顶板在 x 方向重叠 0.5 m：棱边内缩到接缝前 0.25 m，仍在。 */
        SECTIONS_OVERLAP,
        /**
         * 真实台阶：高台顶面 y = 0、到 x = 16 结束，低台顶面 y = -0.5。
         * 从高台一侧看，棱边几何与平地接缝完全一样，只有另一侧低 0.5 m。
         */
        LEDGE
    }

    /** 法线改写策略。 */
    private enum Rewrite {
        /** 不改写，Bullet 原样。 */
        NONE,
        /** 只在「棱边两侧等高」时改写：平地接缝，真实台阶保留角平分线。 */
        INTERNAL_EDGE,
        /** 只要法线支撑且带水平分量就改写：用来演示无差别改写会吃掉真实台阶的棱边。 */
        ALL_SUPPORTING,
        /** 内部边上的分离点（{@code distance1 ≥ 0}）直接禁用，不再进求解器。 */
        DROP_SEPARATED,
        /** 任何分离点都禁用，不看两侧是否等高：用来核对「等高」这条附加条件是否必要。 */
        DROP_SEPARATED_ALL,
        /**
         * 内部边上的分离点保留，但把接触点挪到轮心正下方的竖轴上：约束还在（能接住一个子步的
         * 接近），力臂没了（{@code ω × r} 不再有竖直分量）。
         */
        PROJECT_SEPARATED
    }

    private enum WheelShape {
        /** 生产轮子的形状：{@code MultiSphere}，单球。 */
        MULTI_SPHERE,
        /** 同半径的裸球包进 compound，用来核对球类型是否影响接触法线。 */
        SPHERE
    }

    @BeforeAll
    static void loadNative() {
        NativeLibraryLoader.load();
    }

    /**
     * 主表：布局 × 轮子形状 × 速度。看三件事——静止时步末竖直速度接近 0；带裸露棱边的三种布局在接缝处
     * 出现上升速度；不带棱边的两种布局在任何速度下都没有。
     */
    @Test
    void table() {
        System.out.println();
        System.out.println("nativeVersion=" + NativeLibrary.versionNumber()
                + "  MM_NATIVE_PATH=" + System.getenv("MM_NATIVE_PATH"));
        for (WheelShape wheelShape : WheelShape.values()) {
            for (Layout layout : new Layout[]{Layout.SECTIONS, Layout.ONE_BODY, Layout.ONE_BOX,
                    Layout.ONE_BOX_COMPOUND, Layout.SECTIONS_OVERLAP}) {
                for (float speed : new float[]{5f, 10f, 15f, 19f, 25f}) {
                    try (Rig rig = new Rig(layout, wheelShape, speed)) {
                        Rig.Run run = rig.drive(speed);
                        System.out.printf(Locale.ROOT,
                                "layout=%-18s wheel=%-12s speed=%4.0f  settled=%+6.3f/%d  "
                                        + "baseVy=%+7.3f  maxVy=%+7.3f @x=%6.2f  "
                                        + "rise=%5.1fmm  diagPts=%3d  diagJ=%7.1f |nx|=%5.3f theta=%4.1fdeg  "
                                        + "uPre=%5.2f  dMax=%5.1fmm dImp=%5.1fmm  airSteps=%3d  kickSteps=%3d%n",
                                layout, wheelShape, speed,
                                rig.settleVy, rig.settlePoints,
                                run.basePostVy, run.maxPostVy, run.maxPostVyX,
                                run.maxRise * 1000f,
                                run.diagonalPoints, run.maxDiagonalImpulse,
                                Math.abs(run.maxDiagonalNormalX), Math.toDegrees(Math.asin(
                                        Math.min(1.0, Math.abs(run.maxDiagonalNormalX)))),
                                run.maxApproach,
                                run.maxDiagonalDistance * 1000f, run.maxDiagonalDistanceWithImpulse * 1000f,
                                run.airSteps, run.kickSteps);
                    }
                }
            }
        }
    }

    /**
     * 造出游戏里那一步的接触明细：19 m/s 下，接缝前最后几个子步里每条接触点的归属、法线、距离与冲量。
     * 这张表用来看棱边接触出现的时机与方向。
     */
    @Test
    void seamContactDetail() {
        for (Layout layout : new Layout[]{Layout.SECTIONS, Layout.SECTIONS_OVERLAP, Layout.ONE_BOX_COMPOUND}) {
            try (Rig rig = new Rig(layout, WheelShape.MULTI_SPHERE, 19f)) {
                rig.traceSeam = true;
                rig.drive(19f);
                System.out.println();
                System.out.println("=== layout=" + layout + " speed=19 第一条接缝前后 ===");
                rig.trace.forEach(System.out::println);
            }
        }
    }

    /**
     * 高速档：加上真实轮子的两个自由度——切向摩擦（{@code racing_tire} 是 3.0）与自转——再扫到实测车速。
     *
     * <p>游戏内的读数（{@code wheel-step-*.csv}）：36 次「单子步竖直速度突增 +4…+16 m/s」全部落在
     * {@code 16k} 边界前后 0.7 m 内；同一步里能看到的接触只有一条 1–3 mm 侵入的竖直承载点与一条
     * 正距离（6–17 mm）的邻块棱点，两者都推不出上百 N·s 的冲量。轮速 31 m/s 时自转是 −74 rad/s，
     * 纯滚动只需 {@code −31 / 0.45 = −69}——本来就是边滚边滑。前面几张表把摩擦与自转都关掉了
     * （{@code setFriction(0)}、{@code setAngularFactor(0)}），只留下法线方向一条通道；这一档把它们
     * 打开，看「归轴」这条规则在高速下还剩多少。</p>
     *
     * @see <a href="docs/plan/进行中/碰撞系统-撞碎与压溃实现计划.md">碰撞系统计划 §2.3</a>
     */
    @Test
    void highSpeedWithFrictionAndSpin() {
        System.out.println();
        System.out.println("speed  spin  mu   rewrite            maxVy   rise(mm)  maxRiseX  airSteps  kick");
        for (float speed : new float[]{19f, 31f, 45f, 60f}) {
            for (float spin : new float[]{0f, 1f, 2f}) {
                for (float mu : new float[]{0f, 3f}) {
                    for (Rewrite rewrite : new Rewrite[]{Rewrite.NONE, Rewrite.INTERNAL_EDGE,
                            Rewrite.DROP_SEPARATED, Rewrite.DROP_SEPARATED_ALL}) {
                        try (Rig rig = new Rig(Layout.SECTIONS, WheelShape.MULTI_SPHERE, speed)) {
                            rig.rewrite = rewrite;
                            rig.configureSurface(mu, spin, 0.4f);
                            Rig.Run run = rig.drive(speed);
                            System.out.printf(Locale.ROOT,
                                    "%5.0f %5.1f %4.1f %-15s %+7.3f %9.1f %9.2f %9d %5d%n",
                                    speed, spin, mu, rewrite,
                                    run.maxPostVy, run.maxRise * 1000f, run.maxPostVyX,
                                    run.airSteps, run.kickSteps);
                        }
                    }
                }
            }
        }
    }

    /**
     * 「丢掉分离点会不会把轮子沉下去」：同一组参数下比五种法线策略的抬升、下沉与平均高度。
     *
     * <p>下沉这一列是这条规则的验收项——native 补丁保住的正是「球面与地面相差 1 µm 仍算接触」的
     * 近接触，游戏层若在回调里把它们一并否决，支撑会整步丢失，轮子每个子步掉 2.6 m/s。
     * {@link Rewrite#DROP_SEPARATED_ALL}（不看等高、不看法线倾斜）就是那种错误做法，用来当反例。</p>
     */
    @Test
    void droppingSeparatedPointsMustNotCostSupport() {
        System.out.println();
        System.out.println("speed  spin  rewrite             maxVy   maxRise   minRise  @x      meanRise  air");
        float[] speeds = {5f, 10f, 19f, 31f, 45f, 60f};
        for (float speed : speeds) {
            for (float spin : new float[]{0f, 2f}) {
                for (Rewrite rewrite : Rewrite.values()) {
                    try (Rig rig = new Rig(Layout.SECTIONS, WheelShape.MULTI_SPHERE, speed)) {
                        rig.rewrite = rewrite;
                        rig.configureSurface(3f, spin, 0.4f);
                        Rig.Run run = rig.drive(speed);
                        System.out.printf(Locale.ROOT,
                                "%5.0f %5.1f %-18s %+7.3f %8.1f %8.1f %6.2f %9.1f %4d%n",
                                speed, spin, rewrite, run.maxPostVy,
                                run.maxRise * 1000f, run.minRise * 1000f, run.minRiseX,
                                run.riseCount == 0 ? Float.NaN : run.riseSum / run.riseCount * 1000f,
                                run.airSteps);
                    }
                }
            }
        }
    }

    /**
     * 高速档的契约：接缝上「分离的内部棱点」就是抬升源，且它在产品里是**挪点**而不是丢掉。
     *
     * <p>参数取自线上录制：轮速 31–60 m/s、自转 2 倍纯滚动、μ 3（{@code racing_tire}）、
     * 恢复系数 0.4。断言钉两件事：只归轴时仍有上升分速；挪到轮心正下方后既不抬升也不下沉。</p>
     */
    @Test
    void separatedPointsOnFlatSeamsAreTheHighSpeedLiftSource() {
        for (float speed : new float[]{31f, 45f, 60f}) {
            try (Rig keep = new Rig(Layout.SECTIONS, WheelShape.MULTI_SPHERE, speed)) {
                keep.rewrite = Rewrite.INTERNAL_EDGE;
                keep.configureSurface(3f, 2f, 0.4f);
                float flattened = keep.drive(speed).maxPostVy;
                try (Rig project = new Rig(Layout.SECTIONS, WheelShape.MULTI_SPHERE, speed)) {
                    project.rewrite = Rewrite.PROJECT_SEPARATED;
                    project.configureSurface(3f, 2f, 0.4f);
                    Rig.Run projected = project.drive(speed);
                    System.out.printf(Locale.ROOT,
                            "%.0f m/s 自转×2 μ3：只归轴 maxVy=%+7.3f ｜ 挪到轮心正下方 maxVy=%+7.3f"
                                    + " rise=%5.1fmm sink=%6.1fmm%n",
                            speed, flattened, projected.maxPostVy,
                            projected.maxRise * 1000f, projected.minRise * 1000f);
                    assertTrue(flattened > 4f, speed + " m/s 只归轴时应当仍被抬起：" + flattened);
                    assertEquals(0f, projected.maxPostVy, 0.6f,
                            speed + " m/s 挪点后不应再有上升分速");
                    assertTrue(projected.minRise > -0.005f,
                            speed + " m/s 挪点后不应下沉超过 5 mm：" + projected.minRise);
                }
            }
        }
    }

    /**
     * 复现：带裸露棱边的布局在接缝处出现上升分速，没有棱边的一整块板没有；刚体是否拆开不影响读数。
     *
     * <p>阈值取 1.0 m/s：游戏日志里 19–23 m/s 过接缝读到 +2.3…+2.6 m/s，量级与 {@code v·sinθ} 一致。</p>
     */
    @Test
    void seamEdgeLiftsTheWheelAndContinuousSurfacesDoNot() {
        Rig.Run sections = drive(Layout.SECTIONS);
        Rig.Run oneBody = drive(Layout.ONE_BODY);
        Rig.Run oneBox = drive(Layout.ONE_BOX);
        Rig.Run oneBoxCompound = drive(Layout.ONE_BOX_COMPOUND);
        Rig.Run overlap = drive(Layout.SECTIONS_OVERLAP);

        assertEquals(sections.maxPostVy, oneBody.maxPostVy, 0.01f,
                "同样一批盒子挂到一个刚体上还是拆到四个刚体上，读数应相同");
        assertEquals(0f, oneBox.maxPostVy, 0.05f, "一整块裸盒上不应有上升分速");
        assertEquals(0f, oneBoxCompound.maxPostVy, 0.05f,
                "一整块板包进 compound（同一条算法路径）也不应有上升分速");
        assertTrue(sections.maxPostVy > 1.0f, "生产布局在 19 m/s 过接缝时应出现上升分速");
        assertTrue(overlap.maxPostVy > 1.0f, "顶板重叠只把棱边内缩，抬升量与 SECTIONS 同级");
        assertTrue(sections.maxRise > 0.005f, "抬升量应有毫米级");
    }

    /**
     * 成因验证：只在「棱边两侧等高」时把斜法线改写成竖直，平地上的抬升消失。承载面本身
     * （法线竖直、正距离）不动，所以这不是「把接触删掉」的结果。
     */
    @Test
    void internalEdgeRewriteRemovesTheFlatSeamLift() {
        Rig.Run plain = drive(Layout.SECTIONS);
        try (Rig rig = new Rig(Layout.SECTIONS, WheelShape.MULTI_SPHERE, 19f)) {
            rig.rewrite = Rewrite.INTERNAL_EDGE;
            Rig.Run rewritten = rig.drive(19f);
            System.out.printf(Locale.ROOT,
                    "19 m/s SECTIONS 原样 maxVy=%+7.3f rise=%5.1fmm ｜ 内部边归轴后 maxVy=%+7.3f rise=%5.1fmm"
                            + "（改写 %d 个，判成真实棱边 %d 个）%n",
                    plain.maxPostVy, plain.maxRise * 1000f,
                    rewritten.maxPostVy, rewritten.maxRise * 1000f, rig.rewritten, rig.keptRealEdges);
            assertTrue(plain.maxPostVy > 1.0f, "原样应抬升");
            assertEquals(0f, rewritten.maxPostVy, 0.05f, "平地接缝归轴后不应再有上升分速");
            assertTrue(rig.rewritten > 0, "应确实改写过平地接缝上的接触点");
        }
    }

    /**
     * 判据本身：同一个位置的同一类斜法线接触，平地接缝判内部边、真实台阶判真实棱边。
     * 台阶布局上，轮子从高台一端驶出，棱边几何与平地接缝完全一样，只有另一侧低 0.5 m。
     */
    @Test
    void sameHeightAcrossTheEdgeSeparatesSeamsFromLedges() {
        System.out.println();
        Rig.Run flat = drive(Layout.SECTIONS, Rewrite.NONE, END_X);
        Rig.Run ledge = drive(Layout.LEDGE, Rewrite.NONE, SECTION + 6f);
        System.out.printf(Locale.ROOT,
                "判据：平地斜法线 %d 条判内部边 / %d 条判真实棱边；台阶 %d 条判内部边 / %d 条判真实棱边%n",
                flat.diagonalInternal, flat.diagonalReal, ledge.diagonalInternal, ledge.diagonalReal);
        assertTrue(flat.diagonalInternal > 0, "平地接缝上的斜法线应判成内部边");
        assertEquals(0, flat.diagonalReal, "平地接缝上不应有判成真实棱边的斜法线");
        assertTrue(ledge.diagonalReal > 0, "台阶棱边上的斜法线应判成真实棱边");
        assertEquals(0, ledge.diagonalInternal, "台阶棱边上不应有判成内部边的斜法线");

        for (Layout layout : new Layout[]{Layout.SECTIONS, Layout.LEDGE}) {
            try (Rig rig = new Rig(layout, WheelShape.MULTI_SPHERE, 19f)) {
                rig.traceSeam = true;
                rig.drive(19f, layout == Layout.LEDGE ? SECTION + 6f : END_X);
                System.out.println("=== " + layout + " 19 m/s 第一条棱边附近（internal = 两侧等高）===");
                rig.trace.forEach(System.out::println);
            }
        }
    }

    /**
     * 规则后的行为：平地接缝归轴，抬升消失；真实台阶的棱边一条都不改。
     *
     * <p>台阶这一档只钉「规则有没有动它」：轮子是从高台驶出往下掉的，棱边接触本身带着 0 冲量，
     * 所以无差别改写也量不出动力学差别（实测 maxVy 0.730 对 0.697）。要量出改写真实棱边的代价，
     * 台架得让轮子蹬上去、棱边承重，那是另一套布局。</p>
     */
    @Test
    void internalEdgeRuleOnlyTouchesFlatSeams() {
        Rig.Run flatPlain = drive(Layout.SECTIONS, Rewrite.NONE, END_X);
        Rig.Run flatRule = drive(Layout.SECTIONS, Rewrite.INTERNAL_EDGE, END_X);
        Rig.Run flatAll = drive(Layout.SECTIONS, Rewrite.ALL_SUPPORTING, END_X);
        Rig.Run ledgePlain = drive(Layout.LEDGE, Rewrite.NONE, SECTION + 6f);
        Rig.Run ledgeRule = drive(Layout.LEDGE, Rewrite.INTERNAL_EDGE, SECTION + 6f);
        Rig.Run ledgeAll = drive(Layout.LEDGE, Rewrite.ALL_SUPPORTING, SECTION + 6f);

        assertTrue(flatPlain.maxPostVy > 1.0f, "原样应抬升");
        assertEquals(0f, flatRule.maxPostVy, 0.05f, "平地接缝归轴后不应再有上升分速");
        assertEquals(0f, flatAll.maxPostVy, 0.05f, "平地上所有棱边都是内部边，两种策略结果相同");

        assertTrue(ledgeRule.rewritten == 0 && ledgeRule.keptRealEdges > 0,
                "内部边规则不应改写真实台阶的棱边，应留下 " + ledgeRule.rewritten
                        + " 改写 / " + ledgeRule.keptRealEdges + " 保留");
        assertTrue(ledgeAll.rewritten > 0, "无差别改写会把真实台阶的棱边也改掉");
        assertEquals(ledgePlain.maxPostVy, ledgeRule.maxPostVy, 1e-3f, "不改写时读数应与原样一致");
    }

    private static Rig.Run drive(Layout layout) {
        try (Rig rig = new Rig(layout, WheelShape.MULTI_SPHERE, 19f)) {
            Rig.Run run = rig.drive(19f);
            System.out.printf(Locale.ROOT,
                    "19 m/s %-18s settled=%+6.3f/%d maxVy=%+7.3f rise=%5.1fmm diagPts=%d dImp=%5.1fmm%n",
                    layout, rig.settleVy, rig.settlePoints, run.maxPostVy, run.maxRise * 1000f,
                    run.diagonalPoints, run.maxDiagonalDistanceWithImpulse * 1000f);
            assertEquals(0f, rig.settleVy, 0.05f, layout + " 静止后轮子应当没有竖直速度");
            assertTrue(rig.settlePoints > 0, layout + " 静止后应保持接触");
            return run;
        }
    }

    private static Rig.Run drive(Layout layout, Rewrite rewrite, float stopX) {
        try (Rig rig = new Rig(layout, WheelShape.MULTI_SPHERE, 19f)) {
            rig.rewrite = rewrite;
            Rig.Run run = rig.drive(19f, stopX);
            System.out.printf(Locale.ROOT,
                    "19 m/s %-9s rewrite=%-13s maxVy=%+7.3f rise=%6.1fmm diagPts=%2d diagJ=%6.1f"
                            + " rewritten=%2d kept=%2d%n",
                    layout, rewrite, run.maxPostVy, run.maxRise * 1000f, run.diagonalPoints,
                    run.maxDiagonalImpulse, rig.rewritten, rig.keptRealEdges);
            return run;
        }
    }

    private static int solverMode() {
        return SolverMode.SIMD | SolverMode.WarmStart
                | SolverMode.CacheFriendly | SolverMode.CacheDirection
                | SolverMode.ArticulatedWarmStart | SolverMode.Interleave;
    }

    private static CollisionShape wheelShape(WheelShape shape) {
        CompoundCollisionShape compound = new CompoundCollisionShape();
        compound.addChildShape(shape == WheelShape.MULTI_SPHERE
                ? new MultiSphere(RADIUS)
                : new SphereCollisionShape(RADIUS), new Vector3f());
        return compound;
    }

    /** 一条接触点在本步的读数。法线已换成「地形 → 轮子」方向。 */
    private record Contact(int plate, float normalX, float normalY, float normalZ,
                           float distance, float impulse, float pointX, float pointY, int lifeTime,
                           boolean internalEdge) {
        boolean diagonal() {
            return Math.abs(normalX) > 0.02f && normalY > 0.5f;
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT,
                    "plate#%d n=(%+.3f,%+.3f,%+.3f) d=%+.4f J=%7.2f p=(%6.3f,%6.3f) life=%d internal=%s",
                    plate, normalX, normalY, normalZ, distance, impulse, pointX, pointY, lifeTime,
                    internalEdge);
        }
    }

    private static final class Rig implements AutoCloseable {
        final PhysicsSpace space;
        final PhysicsRigidBody wheel;
        final List<PhysicsRigidBody> plates = new ArrayList<>();
        final Map<Long, Integer> plateIndex = new HashMap<>();
        /** 地形的顶面矩形，用来判断「棱边两侧是否等高」；台架地形在 z 方向不变。 */
        final List<Rect> ground = new ArrayList<>();
        final List<String> trace = new ArrayList<>();
        boolean traceSeam;
        float restY;
        float basePostVy;
        /** 静止结束时轮子的竖直速度与接触点数，用来确认基线本身是稳的。 */
        float settleVy;
        int settlePoints;

        Rig(Layout layout, WheelShape shape, float speed) {
            space = new PhysicsSpace(new Vector3f(-1000f, -1000f, -1000f),
                    new Vector3f(1000f, 1000f, 1000f), PhysicsSpace.BroadphaseType.DBVT,
                    NativeLibrary.countThreads(), new CollisionConfiguration(8192, 1)) {
                @Override
                public void onContactProcessed(PhysicsCollisionObject pcoA, PhysicsCollisionObject pcoB, long id) {
                    rewriteSeamNormal(pcoA, pcoB, id);
                }
            };
            space.setGravity(new Vector3f(0f, -9.81f, 0f));
            space.getSolverInfo().setMode(solverMode());
            buildTerrain(layout);

            wheel = new PhysicsRigidBody(wheelShape(shape), MASS);
            wheel.setPhysicsLocationDp(new Vec3d(START_X, RADIUS, 8d));
            wheel.setDamping(0f, 0f);
            wheel.setSleepingThresholds(0f, 0f);
            wheel.setFriction(0f);
            wheel.setRestitution(0f);
            // 纯滑动：切向自由度不参与棱边法线上的接近速度，去掉转动让读数只反映法线方向
            wheel.setAngularFactor(new Vector3f(0f, 0f, 0f));
            space.addCollisionObject(wheel);

            settle();
            basePostVy = 0f;
        }

        /** 法线改写策略，用来验证成因（不是产品实现）。 */
        Rewrite rewrite = Rewrite.NONE;
        /** 改写成竖直的点数。 */
        int rewritten;
        /** 判成真实棱边、因而保持原法线的点数。 */
        int keptRealEdges;
        /** 逐点写入的合成摩擦（模拟 {@code setupContactFriction} 写 {@code setCombinedFriction}）。 */
        float wheelFriction;
        /** 逐点写入的合成恢复系数。 */
        float wheelRestitution;
        /** 自转角速度与纯滚动角速度之比：1 = 纯滚动，2 = 边滚边滑（实测轮速 31 m/s 时约 1.07）。 */
        float spinRatio;

        /** 打开切向摩擦与自转：默认的六张对照表是纯滑动、零摩擦的。 */
        void configureSurface(float friction, float spin, float restitution) {
            this.wheelFriction = friction;
            this.spinRatio = spin;
            this.wheelRestitution = restitution;
            // 静止阶段一直是锁死转动的；放开后由 drive 每步写入自转
            wheel.setAngularFactor(new Vector3f(1f, 1f, 1f));
        }

        /** 一段地形的顶面：{@code [minX, maxX)} 之间顶面在 {@code topY}。 */
        record Rect(float minX, float maxX, float topY) {
        }

        /** 某处地表的顶面高度；没有地形时返回 {@link Float#NaN}。 */
        private float columnTop(float x) {
            boolean found = false;
            float top = 0f;
            for (Rect rect : ground) {
                if (x >= rect.minX() && x < rect.maxX()) {
                    top = found ? Math.max(top, rect.topY()) : rect.topY();
                    found = true;
                }
            }
            return found ? top : Float.NaN;
        }

        /**
         * 这条棱边是不是「内部边」：两侧地表等高，说明它是两个同高方块拼出来的接缝，
         * 几何上不存在凸棱，法线本应是竖直的。不等高（台阶、悬崖、洞沿）时角平分线是对的。
         *
         * <p>做法：取水平倾斜的主轴，找接触点最近的整数边界，比较边界两侧的柱顶。
         * 接触点离整数边界超过 5 cm 时不判内部边——内部边的接触点必然落在方块边界上。</p>
         */
        private boolean isInternalEdge(Vector3f point, Vector3f normal) {
            if (Math.abs(normal.x) < Math.abs(normal.z)) return false;   // 本台架只有 x 向棱边
            float coordinate = point.x;
            float boundary = Math.round(coordinate);
            if (Math.abs(coordinate - boundary) > EDGE_BOUNDARY_TOLERANCE) return false;
            float left = columnTop(boundary - 0.5f);
            float right = columnTop(boundary + 0.5f);
            return Float.isFinite(left) && Float.isFinite(right) && Math.abs(left - right) < 1e-3f;
        }

        /**
         * 只改「轮子 vs 地形、法线基本竖直但有水平分量」的接触点。这正是棱边给出的一种：
         * 地面指向上、带水平分量。{@link Rewrite#INTERNAL_EDGE} 只在两侧等高时改，
         * 真实台阶的角平分线保持原样。
         */
        private void rewriteSeamNormal(PhysicsCollisionObject pcoA, PhysicsCollisionObject pcoB, long id) {
            if (wheel == null) return;
            boolean wheelIsA = pcoA.nativeId() == wheel.nativeId();
            PhysicsCollisionObject other = wheelIsA ? pcoB : pcoA;
            if (!plateIndex.containsKey(other.nativeId())) return;
            // 与产品一致：摩擦与恢复系数逐点写进 manifold，不走刚体的合成（地形刚体摩擦为 0，
            // 相乘合成会得到 0，只有逐点写才算得出轮胎的抓地力）
            if (wheelFriction > 0f) {
                ManifoldPoints.setCombinedFriction(id, wheelFriction);
                ManifoldPoints.setCombinedRestitution(id, wheelRestitution);
            }
            if (rewrite == Rewrite.NONE) return;
            Vector3f normal = new Vector3f();
            ManifoldPoints.getNormalWorldOnB(id, normal);
            if (!wheelIsA) normal.negateLocal();
            float distance = ManifoldPoints.getDistance1(id);
            // 分离点：球面根本没碰到那个盒子，Bullet 只是在成对接触阈值的缓存窗口里留着它。
            // 它照样能消掉「这个点上」的接近速度——而轮子自转在离轮心 5~12 cm 处会造出
            // ω·r 量级的竖直速度（实测 176 rad/s × 0.12 m ≈ 21 m/s）。求解器把这个速度清零，
            // 冲量就变成轮心的竖直弹起。禁用这一条等于把不属于承载面的约束摘掉。
            if (rewrite == Rewrite.DROP_SEPARATED_ALL && distance >= 0f) {
                ManifoldPoints.setDistance1(id, 500f);
                rewritten++;
                return;
            }
            if (normal.y <= CLIMB_SUPPORT_NORMAL_Y
                    || Math.abs(normal.x) + Math.abs(normal.z) < 0.02f) {
                return;
            }
            Vector3f point = new Vector3f();
            if (wheelIsA) {
                ManifoldPoints.getPositionWorldOnB(id, point);
            } else {
                ManifoldPoints.getPositionWorldOnA(id, point);
            }
            if (rewrite == Rewrite.PROJECT_SEPARATED) {
                if (distance >= 0f && isInternalEdge(point, normal)) {
                    // 保留这条约束，只把作用点挪到轮心正下方：竖直约束照旧能接住接近速度，
                    // 但 ω × r 的竖直分量随力臂一起归零
                    Vector3f centre = wheel.getPhysicsLocation(null);
                    Vector3f projected = new Vector3f(centre.x, point.y, centre.z);
                    ManifoldPoints.setPositionWorldOnA(id, projected);
                    ManifoldPoints.setPositionWorldOnB(id, projected);
                    ManifoldPoints.setNormalWorldOnB(id, wheelIsA ? Vector3f.UNIT_Y : Vector3f.UNIT_Y.negate());
                    rewritten++;
                } else {
                    keptRealEdges++;
                }
                return;
            }
            if (rewrite == Rewrite.DROP_SEPARATED) {
                // 只在「两侧等高」时才丢：真实棱边（台阶、洞沿）的分离点是轮子正要撞上去的那条，
                // 丢掉它轮子会直接穿过棱边上方。
                if (distance >= 0f && isInternalEdge(point, normal)) {
                    ManifoldPoints.setDistance1(id, 500f);
                    rewritten++;
                } else {
                    keptRealEdges++;
                }
                return;
            }
            if (rewrite == Rewrite.INTERNAL_EDGE) {
                if (!isInternalEdge(point, normal)) {
                    keptRealEdges++;
                    return;
                }
            }
            rewritten++;
            ManifoldPoints.setNormalWorldOnB(id, wheelIsA ? Vector3f.UNIT_Y : Vector3f.UNIT_Y.negate());
        }

        private void buildTerrain(Layout layout) {
            switch (layout) {
                case ONE_BOX -> {
                    PhysicsRigidBody body = new PhysicsRigidBody(new BoxCollisionShape(
                            new Vector3f(SECTIONS * SECTION * 0.5f, HALF_Y, HALF_Z)), 0f);
                    addPlate(body, SECTIONS * SECTION * 0.5f, -HALF_Y, -1);
                    ground.add(new Rect(0f, SECTIONS * SECTION, 0f));
                }
                case ONE_BOX_COMPOUND -> {
                    CompoundCollisionShape compound = new CompoundCollisionShape();
                    compound.addChildShape(new BoxCollisionShape(
                            new Vector3f(SECTIONS * SECTION * 0.5f, HALF_Y, HALF_Z)), new Vector3f(0f, -HALF_Y, 0f));
                    addPlate(new PhysicsRigidBody(compound, 0f), SECTIONS * SECTION * 0.5f, 0f, -1);
                    ground.add(new Rect(0f, SECTIONS * SECTION, 0f));
                }
                case ONE_BODY -> {
                    CompoundCollisionShape compound = new CompoundCollisionShape();
                    for (int section = 0; section < SECTIONS; section++) {
                        for (int layer = 0; layer < LAYERS; layer++) {
                            compound.addChildShape(new BoxCollisionShape(
                                            new Vector3f(SECTION * 0.5f, HALF_Y, HALF_Z)),
                                    new Vector3f(section * SECTION + SECTION * 0.5f - SECTIONS * SECTION * 0.5f,
                                            -HALF_Y - layer, 0f));
                        }
                        ground.add(new Rect(section * SECTION, (section + 1) * SECTION, 0f));
                    }
                    addPlate(new PhysicsRigidBody(compound, 0f), SECTIONS * SECTION * 0.5f, 0f, -1);
                }
                case SECTIONS, SECTIONS_OVERLAP -> {
                    float halfX = layout == Layout.SECTIONS_OVERLAP
                            ? SECTION * 0.5f + OVERLAP * 0.5f
                            : SECTION * 0.5f;
                    for (int section = 0; section < SECTIONS; section++) {
                        CompoundCollisionShape compound = new CompoundCollisionShape();
                        for (int layer = 0; layer < LAYERS; layer++) {
                            compound.addChildShape(new BoxCollisionShape(new Vector3f(halfX, HALF_Y, HALF_Z)),
                                    new Vector3f(0f, -HALF_Y - layer, 0f));
                        }
                        addPlate(new PhysicsRigidBody(compound, 0f), section * SECTION + SECTION * 0.5f, 0f, section);
                        ground.add(new Rect(section * SECTION - halfX + SECTION * 0.5f,
                                section * SECTION + halfX + SECTION * 0.5f, 0f));
                    }
                }
                case LEDGE -> {
                    // 高台顶面 y = 0，到 x = 16 结束；低台顶面 y = -0.5。
                    // 棱边两侧只差高度：接触几何与平地接缝一样，判据必须把它们分开。
                    CompoundCollisionShape upper = new CompoundCollisionShape();
                    upper.addChildShape(new BoxCollisionShape(new Vector3f(12f, HALF_Y, HALF_Z)),
                            new Vector3f(0f, -HALF_Y, 0f));
                    addPlate(new PhysicsRigidBody(upper, 0f), 4f, 0f, -1);
                    ground.add(new Rect(-8f, SECTION, 0f));

                    CompoundCollisionShape lower = new CompoundCollisionShape();
                    lower.addChildShape(new BoxCollisionShape(new Vector3f(24f, HALF_Y, HALF_Z)),
                            new Vector3f(0f, -HALF_Y - LEDGE_DROP, 0f));
                    addPlate(new PhysicsRigidBody(lower, 0f), SECTION + 24f, 0f, -2);
                    ground.add(new Rect(SECTION, SECTION + 48f, -LEDGE_DROP));
                }
            }
        }

        /** 静态地形：刚体原点放在 section 中心，子形状相对中心摆放，与 {@code PhysicsChunkSection} 一致。 */
        private void addPlate(PhysicsRigidBody body, float x, float y, int index) {
            body.setPhysicsLocationDp(new Vec3d(x, y, 8d));
            body.setFriction(0f);
            body.setRestitution(0f);
            space.addCollisionObject(body);
            plates.add(body);
            plateIndex.put(body.nativeId(), index);
        }

        /** 施加预载直到静止，记下轮心高度作为「抬升量」的零点。 */
        private void settle() {
            for (int i = 0; i < SETTLE_STEPS; i++) {
                wheel.clearForces();
                wheel.applyCentralForce(new Vector3f(0f, -PRELOAD, 0f));
                space.update(DT, 0, StepFlag.contactProcessed);
            }
            restY = wheel.getPhysicsLocation(null).y;
            settleVy = wheel.getLinearVelocity(null).y;
            settlePoints = contactsAfterStep().size();
        }

        Run drive(float speed) {
            return drive(speed, END_X);
        }

        Run drive(float speed, float stopX) {
            Run run = new Run();
            float baseSum = 0f;
            int baseCount = 0;
            for (int step = 0; step < MAX_STEPS; step++) {
                Vector3f position = wheel.getPhysicsLocation(null);
                if (position.x > stopX || position.y < -5f) break;

                Vector3f velocity = wheel.getLinearVelocity(null);
                float vyBefore = velocity.y;
                wheel.clearForces();
                wheel.applyCentralForce(new Vector3f(0f, -PRELOAD, 0f));
                wheel.setLinearVelocity(new Vector3f(speed, vyBefore, 0f));
                if (spinRatio != 0f) {
                    // 驱动轴维持自转：绕 z 轴反转（向 +x 滚动），角速度大小 = 比例 × v/R
                    wheel.setAngularVelocity(new Vector3f(0f, 0f, -spinRatio * speed / RADIUS));
                }
                space.update(DT, 0, StepFlag.contactProcessed | StepFlag.contactConceived);

                Vector3f after = wheel.getLinearVelocity(null);
                Vector3f now = wheel.getPhysicsLocation(null);
                List<Contact> contacts = contactsAfterStep();
                boolean wheelOnTerrain = !contacts.isEmpty();

                // 静止基线：还在第一个 section 中段、速度尚未接近接缝的那些子步
                if (now.x < SECTION - 3f && wheelOnTerrain) {
                    baseSum += after.y;
                    baseCount++;
                }

                float rise = now.y - restY;
                if (rise > run.maxRise) {
                    run.maxRise = rise;
                }
                // 下沉量与平均高度：判断「丢掉某些接触」会不会让轮子陷下去，靠这两项而不是 maxRise
                if (rise < run.minRise) {
                    run.minRise = rise;
                    run.minRiseX = now.x;
                }
                run.riseSum += rise;
                run.riseCount++;
                if (after.y > run.maxPostVy) {
                    run.maxPostVy = after.y;
                    run.maxPostVyX = now.x;
                }
                if (!wheelOnTerrain) {
                    run.airSteps++;
                }
                if (after.y > 0.5f) {
                    run.kickSteps++;
                }
                for (Contact contact : contacts) {
                    if (!contact.diagonal()) continue;
                    run.diagonalPoints++;
                    if (contact.internalEdge()) {
                        run.diagonalInternal++;
                    } else {
                        run.diagonalReal++;
                    }
                    run.maxDiagonalImpulse = Math.max(run.maxDiagonalImpulse, contact.impulse());
                    run.maxDiagonalDistance = Math.max(run.maxDiagonalDistance, contact.distance());
                    if (contact.impulse() >= 20f) {
                        run.maxDiagonalDistanceWithImpulse =
                                Math.max(run.maxDiagonalDistanceWithImpulse, contact.distance());
                    }
                    if (Math.abs(contact.normalX()) > Math.abs(run.maxDiagonalNormalX)) {
                        run.maxDiagonalNormalX = contact.normalX();
                    }
                    float approach = -(speed * contact.normalX() + vyBefore * contact.normalY());
                    run.maxApproach = Math.max(run.maxApproach, approach);
                }

                if (traceSeam && Math.abs(now.x - SECTION) < 0.45f) {
                    trace.add(String.format(Locale.ROOT,
                            "  step=%3d x=%6.3f (seam%+6.3f) vyPre=%+6.3f vyPost=%+6.3f rise=%+6.2fmm %s",
                            step, now.x, now.x - SECTION, vyBefore, after.y, rise * 1000f, contacts));
                }
            }
            if (baseCount > 0) basePostVy = baseSum / baseCount;
            run.basePostVy = basePostVy;
            run.rewritten = rewritten;
            run.keptRealEdges = keptRealEdges;
            return run;
        }

        /** 本步求解完成后仍存在的接触点，按「地形 → 轮子」方向给出法线。 */
        private List<Contact> contactsAfterStep() {
            List<Contact> result = new ArrayList<>();
            long wheelId = wheel.nativeId();
            for (long manifold : space.listManifoldIds()) {
                boolean wheelIsA = PersistentManifolds.getBodyAId(manifold) == wheelId;
                if (!wheelIsA && PersistentManifolds.getBodyBId(manifold) != wheelId) continue;
                long otherId = wheelIsA
                        ? PersistentManifolds.getBodyBId(manifold)
                        : PersistentManifolds.getBodyAId(manifold);
                Integer plate = plateIndex.get(otherId);
                if (plate == null) continue;
                int count = PersistentManifolds.countPoints(manifold);
                for (int i = 0; i < count; i++) {
                    long id = PersistentManifolds.getPointId(manifold, i);
                    Vector3f normal = new Vector3f();
                    ManifoldPoints.getNormalWorldOnB(id, normal);
                    if (!wheelIsA) normal.negateLocal();
                    Vector3f point = new Vector3f();
                    if (wheelIsA) {
                        ManifoldPoints.getPositionWorldOnB(id, point);
                    } else {
                        ManifoldPoints.getPositionWorldOnA(id, point);
                    }
                    result.add(new Contact(plate, normal.x, normal.y, normal.z,
                            ManifoldPoints.getDistance1(id), ManifoldPoints.getAppliedImpulse(id),
                            point.x, point.y, ManifoldPoints.getLifeTime(id),
                            isInternalEdge(point, normal)));
                }
            }
            return result;
        }

        @Override
        public void close() {
            space.removeCollisionObject(wheel);
            for (PhysicsRigidBody plate : plates) {
                space.removeCollisionObject(plate);
            }
            space.destroy();
        }

        static final class Run {
            float basePostVy;
            float maxPostVy = -Float.MAX_VALUE;
            float maxPostVyX = Float.NaN;
            float maxRise = -Float.MAX_VALUE;
            /** 整轮跑下来的最低点与平均高度（相对静止高度），负值即下沉。 */
            float minRise = Float.MAX_VALUE;
            float minRiseX = Float.NaN;
            double riseSum;
            int riseCount;
            float maxDiagonalImpulse;
            float maxDiagonalNormalX;
            /** 斜法线接触点里最大的 distance1。 */
            float maxDiagonalDistance;
            /** 带 20 N·s 以上冲量的斜法线接触点里最大的 distance1。 */
            float maxDiagonalDistanceWithImpulse;
            float maxApproach;
            int diagonalPoints;
            /** 斜法线接触里判成内部边的条数（平地接缝）。 */
            int diagonalInternal;
            /** 斜法线接触里判成真实棱边的条数（台阶、悬崖、洞沿）。 */
            int diagonalReal;
            int airSteps;
            int kickSteps;
            /** 本次运行里改写策略改写的点数与判成真实棱边而保留的点数。 */
            int rewritten;
            int keptRealEdges;
        }
    }
}
