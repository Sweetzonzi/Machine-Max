package io.github.sweetzonzi.machine_max.probe;

import io.github.sweetzonzi.machine_max.probe.ClimbLiftRig.FaceRig;
import io.github.sweetzonzi.machine_max.probe.ClimbLiftRig.LoadRig;
import io.github.sweetzonzi.machine_max.probe.ClimbLiftRig.SupportRig;
import com.jme3.bullet.joints.motors.MotorParam;
import com.jme3.math.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.Locale;

import static io.github.sweetzonzi.machine_max.probe.ClimbLiftRig.LIFT;
import static io.github.sweetzonzi.machine_max.probe.ClimbLiftRig.OVERLAP;
import static io.github.sweetzonzi.machine_max.probe.ClimbLiftRig.RADIUS;

/**
 * 台阶抬升系列的实验台：把接触法线、侵入深度与接触点位置改掉以后，刚体在下一个子步拿到多少
 * 竖直速度、被外力压住时抬升还在不在。产出是打印表格，断言部分已经搬到
 * {@code ClimbLiftContactTest}；这里保留同一条试具上的其它场景，方便改参数后重跑对照。
 *
 * <p>只在 Windows 上跑：本机 bulletjme native 是重建件，其它平台的出厂件在近接触与
 * 承载深度上的表现不同，打印出来的表不能直接比对。</p>
 */
@EnabledOnOs(OS.WINDOWS)
class ClimbLiftContactProbe {

    @BeforeAll
    static void loadNative() {
        NativeLibraryLoader.load();
    }

    /**
     * 把接触点挪到轮底（球心正下方一个半径），竖直冲量就没有力臂了：抬升应该全部变成线速度，
     * 自转不再吃掉 7 成。
     */
    @Test
    void movingTheContactPointToTheBottomRemovesTheSpin() {
        float clearance = 0.05f;
        System.out.printf(Locale.ROOT, "%n=== 悬空立面：接触点位置的影响 ===%n");
        System.out.printf(Locale.ROOT, "%-20s %-8s %-10s %-10s %-10s %-10s%n",
                "contactPoint", "depth", "vy@1", "spinZ@1", "vy@3", "bottom@3");
        for (float depth : new float[]{0.01f, 0.02f, 0.03f, 0.039f}) {
            reportBottomPoint("原始(球心侧面)", depth, clearance, false);
            reportBottomPoint("轮底", depth, clearance, true);
        }

        System.out.printf(Locale.ROOT, "%n=== 持续改写 300 步，接触点挪到轮底（推力 0 N） ===%n");
        System.out.printf(Locale.ROOT, "%-5s %-9s %-9s %-9s %-6s%n", "step", "bottom", "vy", "spinZ", "live");
        try (FaceRig rig = new FaceRig(true, 0.03f, 0.02f, -0.005f, true)) {
            for (int step = 1; step <= 300; step++) {
                rig.advance(1);
                if (step <= 5 || step % 20 == 0) {
                    System.out.printf(Locale.ROOT, "%-5d %-9.5f %-9.5f %-9.4f %-6s%n",
                            step, rig.bottom(), rig.ball.getLinearVelocity(null).y,
                            rig.ball.getAngularVelocity(null).z, rig.liveThisStep);
                }
            }
        }

        System.out.printf(Locale.ROOT, "%n=== 同上，但球悬空（离下层 5 cm，只有立面接触） ===%n");
        System.out.printf(Locale.ROOT, "%-5s %-9s %-9s %-9s %-6s%n", "step", "bottom", "vy", "spinZ", "live");
        try (FaceRig rig = new FaceRig(true, 0.03f, 0.02f, 0.05f, true)) {
            for (int step = 1; step <= 120; step++) {
                rig.advance(1);
                if (step <= 5 || step % 20 == 0) {
                    System.out.printf(Locale.ROOT, "%-5d %-9.5f %-9.5f %-9.4f %-6s%n",
                            step, rig.bottom(), rig.ball.getLinearVelocity(null).y,
                            rig.ball.getAngularVelocity(null).z, rig.liveThisStep);
                }
            }
        }
    }

    private void reportBottomPoint(String label, float depth, float clearance, boolean bottomPoint) {
        try (FaceRig rig = new FaceRig(true, depth, 0.01f, clearance, bottomPoint)) {
            float vy1 = rig.advance(1);
            float spin1 = rig.ball.getAngularVelocity(null).z;
            float vy3 = rig.advance(2);
            System.out.printf(Locale.ROOT, "%-20s %-8.3f %-10.5f %-10.4f %-10.5f %-10.5f%n",
                    label, depth, vy1, spin1, vy3, rig.bottom());
        }
    }

    /**
     * 持续维持改写深度时，竖直速度会不会越攒越大？用恒定的水平推力把球一直按在立面上
     * （车顶着台阶的等价物），每步都改写，逐帧看 vy、自转、以及离开立面那一刻的速度。
     */
    @Test
    void sustainedRewriteDoesNotKeepAccelerating() {
        for (float press : new float[]{0f, 100f, 400f}) {
            try (FaceRig rig = new FaceRig(true, 0.03f, 0.02f, -0.005f)) {
                rig.press = press;
                System.out.printf(Locale.ROOT, "%n=== 持续改写 d=-0.03，水平推力 %.0f N ===%n", press);
                System.out.printf(Locale.ROOT, "%-5s %-9s %-9s %-9s %-9s %-6s %-8s%n",
                        "step", "bottom", "vy", "spinZ", "x", "live", "rewrote");
                float peak = Float.NEGATIVE_INFINITY;
                float separationVy = Float.NaN;
                float separationBottom = Float.NaN;
                for (int step = 1; step <= 300; step++) {
                    boolean wasLive = rig.liveThisStep;
                    rig.advance(1);
                    peak = Math.max(peak, rig.ball.getLinearVelocity(null).y);
                    if (wasLive && !rig.liveThisStep && Float.isNaN(separationVy)) {
                        separationVy = rig.ball.getLinearVelocity(null).y;
                        separationBottom = rig.bottom();
                    }
                    if (step <= 5 || step % 20 == 0) {
                        System.out.printf(Locale.ROOT, "%-5d %-9.5f %-9.5f %-9.4f %-9.4f %-6s %-8d%n",
                                step, rig.bottom(), rig.ball.getLinearVelocity(null).y,
                                rig.ball.getAngularVelocity(null).z, rig.ball.getPhysicsLocation(null).x,
                                rig.liveThisStep, rig.rewritten);
                    }
                }
                System.out.printf(Locale.ROOT,
                        "peakVy=%.4f separationVy=%s separationBottom=%s finalBottom=%.4f rewrote=%d%n",
                        peak, Float.isNaN(separationVy) ? "未脱离" : String.format(Locale.ROOT, "%.4f", separationVy),
                        Float.isNaN(separationBottom) ? "—" : String.format(Locale.ROOT, "%.4f", separationBottom),
                        rig.bottom(), rig.rewritten);
            }
        }
    }

    /**
     * 侵入深度 → 竖直速度，不改写。量的是求解器自己的映射：阈值（4 cm）以内一步后的竖直速度是
     * {@code erp × depth / dt}，越过阈值后走分裂冲量，竖直速度恰好为 0，只抬位置。
     *
     * <p>契约版本（带断言）在 {@code ClimbLiftContactTest#supportDepthDecidesTheVerticalVelocity}。</p>
     */
    @Test
    void supportDepthTable() {
        System.out.printf(Locale.ROOT, "%n=== 承载面：深度 → 一步后的竖直速度（不改写） ===%n");
        System.out.printf(Locale.ROOT, "%-8s %-10s %-10s %-10s %-10s %-10s%n",
                "depth", "vyAfter1", "erp*d/dt", "bottom@1", "vy@5", "bottom@5");
        for (float depth : new float[]{0.002f, 0.005f, 0.01f, 0.02f, 0.03f, 0.039f, 0.041f, 0.05f, 0.08f, 0.15f}) {
            try (SupportRig rig = new SupportRig(depth)) {
                float first = rig.advance(1);
                float bottom1 = rig.bottom();
                float expected = 0.2f * depth / ClimbLiftRig.DT;
                System.out.printf(Locale.ROOT, "%-8.3f %-10.5f %-10.5f %-10.5f %-10.5f %-10.5f%n",
                        depth, first, expected, bottom1, rig.advance(4), rig.bottom());
            }
        }
    }

    /**
     * 立面接触：球停在下层上，与上层立面相切侵入 {@code overlap}，把这条接触的法线换成正上方、
     * 深度换成给定值，看竖直速度。{@code rewrite=false} 是对照组（原始水平法线 + 原始深度）。
     */
    @Test
    void faceNormalRewriteDecidesTheVerticalVelocity() {
        float overlap = 0.01f;
        System.out.printf(Locale.ROOT, "%n=== 立面：法线换竖直后的竖直速度（侧面重叠 %.3f m） ===%n", overlap);
        System.out.printf(Locale.ROOT, "%-14s %-8s %-10s %-10s %-10s %-10s %-10s %-10s%n",
                "rewrite", "depth", "vy@1", "vy@2", "vy@5", "bottom@1", "bottom@5", "bottom@20");
        reportFace("none", false, 0f, overlap);
        reportFace("normal-only", true, Float.NaN, overlap);
        for (float depth : new float[]{0.005f, 0.01f, 0.02f, 0.03f, 0.035f, 0.045f, 0.08f}) {
            reportFace("up/-d", true, depth, overlap);
        }
        // 深侵入：整段侧面都被算进接触（旧写法保留原始深度时就是这种量级）
        reportFace("normal-only", true, Float.NaN, 0.18f);
        reportFace("up/-d", true, 0.03f, 0.18f);
    }

    /**
     * 立面接触，但球悬空（不下层承载）：这条垂直接触是球唯一的地形接触，可以直接读出
     * 「换法线 + 换深度」给了多少竖直速度，不受承载面那条约束的干扰。
     */
    @Test
    void floatingFaceContactIsolatesTheRewrite() {
        float clearance = 0.05f;
        System.out.printf(Locale.ROOT, "%n=== 悬空立面接触（离下层顶面 %.2f m，只有立面相接触） ===%n", clearance);
        System.out.printf(Locale.ROOT, "%-14s %-8s %-10s %-10s %-10s %-10s %-10s%n",
                "rewrite", "depth", "vy@1", "vy@2", "vy@5", "bottom@1", "bottom@5");
        reportFace("none", false, 0f, 0.01f, clearance);
        reportFace("normal-only", true, Float.NaN, 0.01f, clearance);
        for (float depth : new float[]{0.005f, 0.01f, 0.02f, 0.03f, 0.039f, 0.045f, 0.08f}) {
            reportFace("up/-d", true, depth, 0.01f, clearance);
        }
    }

    private void reportFace(String label, boolean rewriteNormal, float depth, float overlap) {
        reportFace(label, rewriteNormal, depth, overlap, -0.005f);
    }

    private void reportFace(String label, boolean rewriteNormal, float depth, float overlap, float clearance) {
        try (FaceRig rig = new FaceRig(rewriteNormal, depth, overlap, clearance)) {
            float vy1 = rig.advance(1);
            float bottom1 = rig.bottom();
            float vy2 = rig.advance(1);
            float vy5 = rig.advance(3);
            float bottom5 = rig.bottom();
            rig.advance(15);
            System.out.printf(Locale.ROOT, "%-14s %-8.3f %-10.5f %-10.5f %-10.5f %-10.5f %-10.5f %-10.5f%n",
                    label, depth, vy1, vy2, vy5, bottom1, bottom5, rig.bottom());
        }
    }

    /**
     * 悬挂关节自检：坐实 {@code New6Dof} 的各个参数落到哪里，以及 1500 kg 车体压上去以后
     * 悬挂的静止长度。理论值：k=55000 N/m 承担 1500 kg → 压缩
     * {@code 1500×9.81/55000 ≈ 0.268 m}，静止长度 {@code 0.9 − 0.268 ≈ 0.632 m}。
     */
    @Test
    void suspensionSpringSettlesAtTheStaticSag() {
        System.out.printf(Locale.ROOT, "%n=== 悬挂关节自检（离立面 5 m，不接触） ===%n");
        try (LoadRig rig = new LoadRig(0, true, -5f, 0f)) {
            System.out.printf(Locale.ROOT, "关节轴 0=%s 1=%s 2=%s%n",
                    rig.joint.getAxis(0, null), rig.joint.getAxis(1, null), rig.joint.getAxis(2, null));
            System.out.printf(Locale.ROOT, "Stiffness=%.1f Damping=%.1f Equilibrium=%.2f Lower=%.2f Upper=%.2f%n",
                    rig.joint.get(MotorParam.Stiffness, 1),
                    rig.joint.get(MotorParam.Damping, 1),
                    rig.joint.get(MotorParam.Equilibrium, 1),
                    rig.joint.get(MotorParam.LowerLimit, 1),
                    rig.joint.get(MotorParam.UpperLimit, 1));
            System.out.printf(Locale.ROOT, "%-6s %-9s %-9s %-9s %-9s %-9s %-9s%n",
                    "step", "轮底", "轮vy", "车体y", "车体vy", "悬挂长", "弹簧力");
            for (int step = 1; step <= 400; step++) {
                rig.advance(1);
                if (step <= 5 || step % 25 == 0) {
                    System.out.printf(Locale.ROOT, "%-6d %-9.5f %-9.5f %-9.5f %-9.5f %-9.5f %-9.1f%n",
                            step, rig.bottom(), rig.wheel.getLinearVelocity(null).y,
                            rig.body.getPhysicsLocation(null).y, rig.body.getLinearVelocity(null).y,
                            rig.suspensionLength(), rig.springForce());
                }
            }
            float expected = LoadRig.BODY_MASS * 9.81f / LoadRig.K;
            System.out.printf(Locale.ROOT, "静止悬挂长 %.4f（建关节时 %.4f，按 k 应缩短 %.4f → %.4f）%n",
                    rig.suspensionLength(), LoadRig.SEPARATION, expected, LoadRig.SEPARATION - expected);
            try (LoadRig doubled = new LoadRig(0, true, -5f, 0f, 2f * LoadRig.K)) {
                doubled.settle(400);
                System.out.printf(Locale.ROOT, "刚度翻倍后悬挂长 %.4f（若弹簧承载应变到 %.4f）%n",
                        doubled.suspensionLength(), LoadRig.SEPARATION - 0.5f * expected);
            }
        }
    }

    /**
     * 行程限位参不参与：同样的挂车体抬升，把 {@code y} 行程从 jeep 的 [-0.5, 1.0] 放宽到
     * [-5, 5] 再跑一遍。两条曲线一致就说明抬升没被限位挡住，全部由弹簧传递。
     */
    @Test
    void suspensionTravelLimitDoesNotDecideTheLift() {
        System.out.printf(Locale.ROOT, "%n=== y 行程 [-0.5, 1.0] vs [-5, 5]：同一次挂车体抬升 ===%n");
        for (float[] limits : new float[][]{{-0.5f, 1.0f}, {-5f, 5f}}) {
            try (LoadRig rig = new LoadRig(2, true, -0.05f, 0f, LoadRig.K, limits[0], limits[1])) {
                rig.settle(600);
                rig.engage(OVERLAP);
                System.out.printf(Locale.ROOT, "--- 行程 [%.1f, %.1f]：沉底悬挂长 %.4f ---%n",
                        limits[0], limits[1], rig.suspensionLength());
                System.out.printf(Locale.ROOT, "%-6s %-9s %-9s %-9s %-9s %-9s %-9s%n",
                        "step", "轮底", "轮vy", "车体y", "车体vy", "悬挂长", "弹簧力");
                for (int step = 1; step <= 60; step++) {
                    rig.advance(1);
                    if (step <= 6 || step % 10 == 0) {
                        System.out.printf(Locale.ROOT, "%-6d %-9.5f %-9.5f %-9.5f %-9.5f %-9.5f %-9.1f%n",
                                step, rig.bottom(), rig.wheel.getLinearVelocity(null).y,
                                rig.body.getPhysicsLocation(null).y, rig.body.getLinearVelocity(null).y,
                                rig.suspensionLength(), rig.springForce());
                    }
                }
            }
        }
    }

    /**
     * 轮心越过方块顶面那几步，立面接触到底变成了什么。逐拍打印轮心位置、上层方块那一对接触点里
     * 最深的一个的原始 {@code distance1} 与法线（改写前），以及这条接触有没有被改写。
     *
     * <p>轮子是贴边放的，不是泡在方块里：方块占 x∈[0,8]、y∈[0,1]，轮心在
     * {@code x = 重叠 − 半径}，只有球面前端越过立面 {@code 重叠} 那么一点。</p>
     */
    @Test
    void faceContactTurnsIntoACornerContactAboveTheBlockTop() {
        System.out.printf(Locale.ROOT, "%n=== 轮心越过顶面（y=1）前后的接触点（轮心 x=%.3f，半径 %.2f） ===%n",
                OVERLAP - RADIUS, RADIUS);
        System.out.printf(Locale.ROOT, "%-5s %-8s %-8s %-8s %-6s %-8s %-9s %-9s %-6s%n",
                "step", "轮心x", "轮心y", "轮底", "点数", "distance1", "法线x", "法线y", "改写");
        try (LoadRig rig = new LoadRig(2, false, -0.05f, 0f)) {
            rig.settle(600);
            rig.engage(OVERLAP);
            for (int step = 1; step <= 150; step++) {
                rig.advance(1);
                if (step >= 118 && step <= 140) {
                    Vector3f p = rig.wheel.getPhysicsLocation(null);
                    System.out.printf(Locale.ROOT,
                            "%-5d %-8.4f %-8.4f %-8.4f %-6d %-8.4f %-9.4f %-9.4f %-6s%n",
                            step, p.x, p.y, p.y - RADIUS, rig.rawPoints, rig.rawDistance,
                            rig.rawNormal.x, rig.rawNormal.y, rig.liveThisStep);
                }
            }
        }
    }

    /**
     * 抬升遇到外力时还在不在。三组对照，都先让悬挂/承载沉到底，再把轮子推进立面
     * {@link ClimbLiftRig#OVERLAP}，然后持续改写接触：单轮、单轮 + 下压力、单轮 + 车体。
     * 车体用 jeep 悬挂参数挂在轮子上，{@code angularFactor} 归零、不与地形碰撞。
     */
    @Test
    void liftSurvivesExternalLoad() {
        System.out.printf(Locale.ROOT,
                "%n=== 外力下的抬升：立面侵入 %.2f m，改写 d=%.2f，接触点在轮底 ===%n", OVERLAP, LIFT);
        reportLoad("单轮", 2, false, 0f);
        reportLoad("单轮+下压力14.7kN", 2, false, 14715f);
        reportLoad("单轮+车体(轮底)", 2, true, 0f);
        reportLoad("单轮+车体(侧面点)", 1, true, 0f);
        reportLoad("单轮+车体+前推500N", 2, true, 0f, 500f);
    }

    private void reportLoad(String label, int mode, boolean withBody, float downforce) {
        reportLoad(label, mode, withBody, downforce, 0f);
    }

    private void reportLoad(String label, int mode, boolean withBody, float downforce, float press) {
        try (LoadRig rig = new LoadRig(mode, withBody, -0.05f, 0f)) {
            rig.downforce = downforce;
            rig.settle(600);
            rig.engage(OVERLAP);
            rig.press = press;
            float startX = rig.wheel.getPhysicsLocation(null).x;
            System.out.printf(Locale.ROOT, "%n--- %s：沉底后悬挂长 %.4f m，轮 vy %.4f，车体 vy %s ---%n",
                    label, rig.suspensionLength(), rig.wheel.getLinearVelocity(null).y,
                    rig.body == null ? "—"
                            : String.format(Locale.ROOT, "%.4f", rig.body.getLinearVelocity(null).y));
            System.out.printf(Locale.ROOT, "%-5s %-9s %-9s %-9s %-9s %-9s %-9s %-9s %-6s%n",
                    "step", "轮底", "轮vy", "spinZ", "车体y", "车体vy", "悬挂长", "弹簧力", "live");
            float peakWheel = Float.NEGATIVE_INFINITY;
            float peakBody = Float.NEGATIVE_INFINITY;
            int crestStep = -1;
            for (int step = 1; step <= 400; step++) {
                boolean wasLive = rig.liveThisStep;
                rig.advance(1);
                peakWheel = Math.max(peakWheel, rig.wheel.getLinearVelocity(null).y);
                if (rig.body != null) peakBody = Math.max(peakBody, rig.body.getLinearVelocity(null).y);
                if (wasLive && !rig.liveThisStep && crestStep < 0) crestStep = step;
                if (step <= 10 || step % 20 == 0 || (crestStep > 0 && step - crestStep <= 4)) {
                    System.out.printf(Locale.ROOT, "%-5d %-9.5f %-9.5f %-9.4f %-9s %-9s %-9.5f %-9.1f %-6s%n",
                            step, rig.bottom(), rig.wheel.getLinearVelocity(null).y,
                            rig.wheel.getAngularVelocity(null).z,
                            rig.body == null ? "—"
                                    : String.format(Locale.ROOT, "%.5f", rig.body.getPhysicsLocation(null).y),
                            rig.body == null ? "—"
                                    : String.format(Locale.ROOT, "%.5f", rig.body.getLinearVelocity(null).y),
                            rig.suspensionLength(), rig.springForce(), rig.liveThisStep);
                }
            }
            System.out.printf(Locale.ROOT,
                    "峰值轮vy=%.4f 峰值车体vy=%s 越过台阶 step=%s 末态轮底=%.4f 末态车体y=%s 轮x漂移=%.4f%n",
                    peakWheel,
                    rig.body == null ? "—" : String.format(Locale.ROOT, "%.4f", peakBody),
                    crestStep < 0 ? "未越过" : String.valueOf(crestStep),
                    rig.bottom(),
                    rig.body == null ? "—"
                            : String.format(Locale.ROOT, "%.4f", rig.body.getPhysicsLocation(null).y),
                    rig.wheel.getPhysicsLocation(null).x - startX);
        }
    }
}
