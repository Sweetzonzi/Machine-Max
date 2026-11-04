package io.github.sweetzonzi.machine_max.common.mech.vehicle.collision;

import com.jme3.bullet.CollisionConfiguration;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.RotationOrder;
import com.jme3.bullet.SolverInfo;
import com.jme3.bullet.SolverMode;
import com.jme3.bullet.StepFlag;
import com.jme3.bullet.collision.ManifoldPoints;
import com.jme3.bullet.collision.PersistentManifolds;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.SphereCollisionShape;
import com.jme3.bullet.joints.New6Dof;
import com.jme3.bullet.joints.motors.MotorParam;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.bullet.util.NativeLibrary;
import com.jme3.math.Matrix3f;
import com.jme3.math.Vector3f;
import com.simsilica.mathd.Vec3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 改写接触法线与侵入深度对竖直速度的影响。只回答一个问题：{@code m_distance1} 和
 * {@code m_normalWorldOnB} 被改掉以后，刚体在下一个子步里拿到多少竖直速度。
 *
 * <p>两个场景：</p>
 * <ul>
 *   <li>承载面：一个静止盒 + 一个球，球心高度直接决定侵入深度。法线本来就竖直，用来量
 *       “深度 → 竖直速度”这条映射本身。</li>
 *   <li>立面：两个静止盒拼出一级台阶（下层顶面 y=0，上层占 y∈[0,1] 并立在 x=0），球停在下层上、
 *       与上层立面相切侵入。法线原为水平，可换成正上方，用来量“换法线 + 换深度”的效果。</li>
 * </ul>
 *
 * <p>求解器用 bulletjme 默认设置（与 {@code PhysicsWorld} 一致：只改 solver mode，不碰
 * {@code SolverInfo}）：{@code splitImpulse=true}、阈值 {@code -0.04}、{@code erp=0.2}、
 * {@code iterations=10}。这三个数字决定了这条映射在 4 cm 处会换一种性质。</p>
 *
 * <p>只在 Windows 上跑：本机 bulletjme native 是重建件。</p>
 */
@EnabledOnOs(OS.WINDOWS)
class ClimbLiftContactPhysicsTest {
    private static final float RADIUS = 0.3f;
    private static final float MASS = 45f;
    private static final float DT = 0.01f;

    @BeforeAll
    static void loadNative() throws Exception {
        String override = System.getenv("MM_NATIVE_PATH");
        if (override != null && !override.isBlank()) {
            System.load(Path.of(override).toAbsolutePath().toString());
            return;
        }
        try {
            NativeLibrary.versionNumber();
            return;
        } catch (UnsatisfiedLinkError unloaded) {
            // JUnit 进程直接加载 Spark-Core 随附的 native。
        }
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        String folder = os.contains("win") ? "windows" : os.contains("mac") ? "macos" : "linux";
        String library = os.contains("win") ? "bulletjme.dll" : os.contains("mac") ? "bulletjme.dylib" : "libbulletjme.so";
        String architecture = arch.contains("aarch64") || arch.contains("arm64") ? "arm64" : "x86_64";
        for (String variant : new String[]{"bullet_dpmt", "bullet_dp"}) {
            String resource = "natives/" + variant + "/" + folder + "/" + architecture + "/" + library;
            try (InputStream input = ClimbLiftContactPhysicsTest.class.getClassLoader().getResourceAsStream(resource)) {
                if (input == null) continue;
                Path temporary = Files.createTempFile("climb-lift-", library.substring(library.lastIndexOf('.')));
                Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
                temporary.toFile().deleteOnExit();
                System.load(temporary.toAbsolutePath().toString());
                return;
            }
        }
        throw new IllegalStateException("找不到 Spark-Core 的 Bullet 双精度 native 资源");
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
     * 侵入深度 → 竖直速度。球正下方接触，法线本就竖直，没有任何改写：量的是求解器自己的映射。
     *
     * <p>实测：深度浅于 split impulse 阈值（4 cm）时，一步后的竖直速度就是
     * {@code erp × depth / dt}（1:1，与深度成正比）；越过阈值后这条接触的穿透修正改走
     * 分裂冲量，竖直速度**恰好为 0**，只把位置每步抬高 {@code erp × depth}。</p>
     */
    @Test
    void supportDepthDecidesTheVerticalVelocity() {
        System.out.printf(Locale.ROOT, "%n=== 承载面：深度 → 一步后的竖直速度（不改写） ===%n");
        System.out.printf(Locale.ROOT, "%-8s %-10s %-10s %-10s %-10s %-10s%n",
                "depth", "vyAfter1", "erp*d/dt", "bottom@1", "vy@5", "bottom@5");
        for (float depth : new float[]{0.002f, 0.005f, 0.01f, 0.02f, 0.03f, 0.039f, 0.041f, 0.05f, 0.08f, 0.15f}) {
            try (SupportRig rig = new SupportRig(depth)) {
                float first = rig.advance(1);
                float bottom1 = rig.bottom();
                float expected = 0.2f * depth / DT;
                System.out.printf(Locale.ROOT, "%-8.3f %-10.5f %-10.5f %-10.5f %-10.5f %-10.5f%n",
                        depth, first, expected, bottom1, rig.advance(4), rig.bottom());
                if (depth < 0.04f) {
                    assertEquals(expected, first, 1e-3f,
                            "阈值以内：一步后的竖直速度等于 erp*depth/dt（depth=" + depth + "）");
                } else {
                    assertEquals(0f, first, 1e-6f,
                            "越过阈值：分裂冲量不改速度，竖直速度必须是 0（depth=" + depth + "）");
                    assertEquals(-depth * 0.8f, bottom1, 1e-3f,
                            "越过阈值：位置每步被抬高 erp*depth（depth=" + depth + "）");
                }
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
     * 一步之内把速度提到 {@code erp*d/dt} 的能力有个硬上限：深度超过 split impulse 阈值
     * （4 cm）后，穿透修正不再进速度约束，只做位置修正。
     */
    @Test
    void deepPenetrationStopsAddingVelocity() {
        float shallow;
        float deep;
        try (SupportRig rig = new SupportRig(0.03f)) {
            shallow = rig.advance(1);
        }
        try (SupportRig rig = new SupportRig(0.08f)) {
            deep = rig.advance(1);
        }
        System.out.printf(Locale.ROOT, "shallow(d=0.03) vy=%.4f  deep(d=0.08) vy=%.4f%n", shallow, deep);
        assertTrue(shallow > 3f * deep, "3 cm 侵入给的速度应明显高于 8 cm（8 cm 走分裂冲量）");
    }

    /**
     * 立面接触，但球悬空（不下层承载）：这条垂直接触是球唯一的地形接触，可以直接读出
     * “换法线 + 换深度”给了多少竖直速度，不受承载面那条约束的干扰。
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

    /** 单个静止盒（顶面 y=0）+ 球，球心高度 = 半径 - 侵入深度。 */
    private static final class SupportRig implements AutoCloseable {
        final PhysicsSpace space;
        final PhysicsRigidBody ground;
        final PhysicsRigidBody ball;

        SupportRig(float penetration) {
            space = newSpace();
            ground = new PhysicsRigidBody(new BoxCollisionShape(new Vector3f(8f, 0.5f, 8f)), 0f);
            ground.setPhysicsLocation(new Vector3f(0f, -0.5f, 0f));
            ground.setFriction(0f);
            ground.setRestitution(0f);
            space.addCollisionObject(ground);
            ball = new PhysicsRigidBody(new SphereCollisionShape(RADIUS), MASS);
            ball.setPhysicsLocationDp(new Vec3d(0d, (double) RADIUS - penetration, 0d));
            ball.setDamping(0f, 0f);
            ball.setSleepingThresholds(0f, 0f);
            ball.setFriction(0f);
            ball.setRestitution(0f);
            space.addCollisionObject(ball);
        }

        float advance(int steps) {
            for (int i = 0; i < steps; i++) space.update(DT, 0, StepFlag.contactProcessed);
            return ball.getLinearVelocity(null).y;
        }

        float bottom() {
            return ball.getPhysicsLocation(null).y - RADIUS;
        }

        @Override
        public void close() {
            space.removeCollisionObject(ball);
            space.removeCollisionObject(ground);
            space.destroy();
        }
    }

    /**
     * 两级台阶：下层顶面 y=0（球停在这里），上层 y∈[0,1] 立在 x=0。球心到立面的重叠由
     * {@code overlap} 给，{@code distance1} 与法线可在回调里改写。
     */
    private static final class FaceRig implements AutoCloseable {
        final PhysicsSpace space;
        final PhysicsRigidBody lower;
        final PhysicsRigidBody upper;
        final PhysicsRigidBody ball;
        final boolean rewriteNormal;
        final float depth;
        final boolean bottomPoint;
        int rewritten;
        int rewritesThisStep;
        boolean liveThisStep;
        float press;

        FaceRig(boolean rewriteNormal, float depth, float overlap, float clearance) {
            this(rewriteNormal, depth, overlap, clearance, false);
        }

        FaceRig(boolean rewriteNormal, float depth, float overlap, float clearance, boolean bottomPoint) {
            this.rewriteNormal = rewriteNormal;
            this.bottomPoint = bottomPoint;
            this.depth = depth;
            space = new PhysicsSpace(new Vector3f(-1000f, -1000f, -1000f),
                    new Vector3f(1000f, 1000f, 1000f), PhysicsSpace.BroadphaseType.DBVT,
                    NativeLibrary.countThreads(), new CollisionConfiguration(8192, 1)) {
                @Override
                public void onContactProcessed(PhysicsCollisionObject pcoA, PhysicsCollisionObject pcoB, long id) {
                    rewrite(pcoA, pcoB, id);
                }
            };
            space.setGravity(new Vector3f(0f, -9.81f, 0f));
            space.getSolverInfo().setMode(SolverMode.SIMD | SolverMode.WarmStart
                    | SolverMode.CacheFriendly | SolverMode.CacheDirection
                    | SolverMode.ArticulatedWarmStart | SolverMode.Interleave);
            lower = new PhysicsRigidBody(new BoxCollisionShape(new Vector3f(4f, 0.5f, 4f)), 0f);
            lower.setPhysicsLocation(new Vector3f(-4f, -0.5f, 0f));
            lower.setFriction(0f);
            lower.setRestitution(0f);
            space.addCollisionObject(lower);
            upper = new PhysicsRigidBody(new BoxCollisionShape(new Vector3f(4f, 0.5f, 4f)), 0f);
            upper.setPhysicsLocation(new Vector3f(4f, 0.5f, 0f));
            upper.setFriction(0f);
            upper.setRestitution(0f);
            space.addCollisionObject(upper);
            ball = new PhysicsRigidBody(new SphereCollisionShape(RADIUS), MASS);
            ball.setPhysicsLocationDp(new Vec3d((double) overlap - RADIUS, (double) RADIUS + clearance, 0d));
            ball.setDamping(0f, 0f);
            ball.setSleepingThresholds(0f, 0f);
            ball.setFriction(0f);
            ball.setRestitution(0f);
            space.addCollisionObject(ball);
        }

        /**
         * 只改写“球 vs 上层、法线水平、有侵入”的接触；承载面与其余接触一律不碰。
         */
        private void rewrite(PhysicsCollisionObject pcoA, PhysicsCollisionObject pcoB, long id) {
            if (!rewriteNormal || ball == null) return;
            boolean ballIsA = pcoA.nativeId() == ball.nativeId();
            PhysicsCollisionObject other = ballIsA ? pcoB : pcoA;
            if (other.nativeId() != upper.nativeId()) return;
            Vector3f normal = new Vector3f();
            ManifoldPoints.getNormalWorldOnB(id, normal);
            if (!ballIsA) normal.negateLocal();   // 地形 → 球
            if (Math.abs(normal.y) >= 0.7f || ManifoldPoints.getDistance1(id) >= 0f) return;
            rewritten++;
            rewritesThisStep++;
            liveThisStep = true;
            ManifoldPoints.setNormalWorldOnB(id, ballIsA ? Vector3f.UNIT_Y : Vector3f.UNIT_Y.negate());
            if (!Float.isNaN(depth)) ManifoldPoints.setDistance1(id, -depth);
            if (bottomPoint) {
                //轮底：球心正下方一个半径，竖直法线在这里没有力臂
                Vector3f point = ball.getPhysicsLocation(null);
                point.y -= RADIUS;
                ManifoldPoints.setPositionWorldOnA(id, point);
                ManifoldPoints.setPositionWorldOnB(id, point);
            }
            ManifoldPoints.setCombinedRestitution(id, 0f);
        }

        float advance(int steps) {
            for (int i = 0; i < steps; i++) {
                rewritesThisStep = 0;
                liveThisStep = false;
                if (press != 0f) ball.applyCentralForce(new Vector3f(press, 0f, 0f));
                space.update(DT, 0, StepFlag.contactProcessed);
            }
            return ball.getLinearVelocity(null).y;
        }

        float bottom() {
            return ball.getPhysicsLocation(null).y - RADIUS;
        }

        @Override
        public void close() {
            space.removeCollisionObject(ball);
            space.removeCollisionObject(upper);
            space.removeCollisionObject(lower);
            space.destroy();
        }
    }

    // *************************************************************************
    // 有外力时抬升还在不在：单轮 / 单轮+下压力 / 单轮+车体装配

    private static final float OVERLAP = 0.01f;
    private static final float LIFT = 0.03f;

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
                    rig.joint.get(MotorParam.Stiffness, 1), rig.joint.get(MotorParam.Damping, 1),
                    rig.joint.get(MotorParam.Equilibrium, 1), rig.joint.get(MotorParam.LowerLimit, 1),
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
            float expected = 1500f * 9.81f / LoadRig.K;
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
     * <p>轮子是**贴边**放的，不是泡在方块里：方块占 x∈[0,8]、y∈[0,1]，轮心在
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
     * 承载接触的深度上限：{@code mode 2} 不复刻上限（承载深度原样交给求解器），{@code mode 3}
     * 复刻 {@code CollisionHandler} 的“承载深度不超过 {@link #LIFT}”那一条。两组都跑前推
     * 500 N 把轮子推进方块的同一次过程，逐拍看每步抬高与车体速度。
     *
     * <p>契约：钳位以后单步抬高回到“一个垫的量级”（不超过 2 cm），车体竖直速度不再被
     * 位置修正顶到 1 m/s 以上。不钳位的那一组只作对照打印，不断言——它的表现是要修掉的问题。</p>
     */
    @Test
    void deepSupportContactDepthIsCappedByTheLiftPad() {
        for (int mode : new int[]{2, 3}) {
            System.out.printf(Locale.ROOT,
                    "%n=== 前推 500 N 把轮子推进方块：%s ===%n",
                    mode == 2 ? "承载深度原样（无上限）" : "承载深度钳到一个垫高");
            System.out.printf(Locale.ROOT, "%-5s %-9s %-7s %-9s %-9s %-9s %-8s %-5s %-6s %-6s %-9s%n",
                    "step", "轮底", "进vy", "轮vy", "车体vy", "distance1", "法线y", "点数", "改写", "钳位", "每步抬高");
            try (LoadRig rig = new LoadRig(mode, true, -0.05f, 0f)) {
                rig.settle(600);
                rig.engage(OVERLAP);
                rig.press = 500f;
                float previousBottom = rig.bottom();
                float peakBody = Float.NEGATIVE_INFINITY;
                float maxStepLift = 0f;
                for (int step = 1; step <= 160; step++) {
                    float entryVy = rig.wheel.getLinearVelocity(null).y;
                    rig.advance(1);
                    float bottom = rig.bottom();
                    peakBody = Math.max(peakBody, rig.body.getLinearVelocity(null).y);
                    maxStepLift = Math.max(maxStepLift, bottom - previousBottom);
                    if (step >= 120 && step <= 150) {
                        System.out.printf(Locale.ROOT,
                                "%-5d %-9.4f %-7.4f %-9.4f %-9.4f %-9.4f %-8.4f %-5d %-6s %-6s %-9.4f%n",
                                step, bottom, entryVy, rig.wheel.getLinearVelocity(null).y,
                                rig.body.getLinearVelocity(null).y, rig.rawDistance, rig.rawNormal.y,
                                rig.rawPoints, rig.liveThisStep, rig.cappedThisStep, bottom - previousBottom);
                    }
                    previousBottom = bottom;
                }
                System.out.printf(Locale.ROOT, "峰值车体vy=%.4f 最大单步抬高=%.4f%n", peakBody, maxStepLift);
                if (mode == 3) {
                    assertTrue(maxStepLift < 0.02f,
                            "承载深度钳到一个垫高后，单步抬高不应再是位置修正量级（实测 " + maxStepLift + "）");
                    assertTrue(peakBody < 1.0f,
                            "承载深度钳到一个垫高后，车体竖直速度不应被顶到 1 m/s 以上（实测 " + peakBody + "）");
                }
            }
        }
    }

    /**
     * 抬升遇到外力时还在不在。三组对照，都先让悬挂/承载沉到底，再把轮子推进立面
     * {@link #OVERLAP}，然后持续改写接触：
     * <ul>
     *   <li>单轮：只有 45 kg 的轮子。</li>
     *   <li>单轮 + 下压力：每步对轮心 {@code applyCentralForce} 向下 14.7 kN，等同于把整车重量
     *       压在单个轮子上。</li>
     *   <li>单轮 + 车体：1500 kg 车体用 {@link New6Dof} 挂在轮子上（jeep 悬挂参数：y 轴
     *       k=55000 N/m、c=2500 N·s/m、行程 [-0.5, 1.0]、x/z 平动锁定、滚动轴自由），
     *       车体 {@code angularFactor} 归零、不与地形碰撞。</li>
     * </ul>
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

    /**
     * 两级台阶 + 轮子 +（可选）车体。车体与轮子之间用 {@link New6Dof} 连接，参数取
     * {@code Machine-Max_Civilian} 的 jeep 悬挂：{@code y} 轴 k=55000、c=2500、行程 [-0.5, 1.0]，
     * x/z 平动锁定，滚动轴（z 转动）自由。车体 {@code angularFactor} 归零、不与任何物体碰撞，
     * 因此车体既不撞地形也不会自己转，竖直方向只能被悬挂推着走。
     */
    private static final class LoadRig implements AutoCloseable {
        static final float BODY_MASS = 1500f;
        static final float BODY_HALF_X = 0.75f;
        static final float BODY_HALF_Y = 0.3f;
        static final float BODY_HALF_Z = 0.9f;
        static final float SEPARATION = 0.9f;
        static final float K = 55000f;
        static final float C = 2500f;

        final PhysicsSpace space;
        final PhysicsRigidBody lower;
        final PhysicsRigidBody upper;
        final PhysicsRigidBody wheel;
        final PhysicsRigidBody body;
        final New6Dof joint;
        final int mode;
        final float depth = LIFT;
        final float k;
        final float yLower;
        final float yUpper;
        float downforce;
        float press;
        float overlap;
        int rewritten;
        boolean liveThisStep;
        boolean cappedThisStep;
        int rawPoints;
        float rawDistance = Float.NaN;
        final Vector3f rawNormal = new Vector3f();

        LoadRig(int mode, boolean withBody, float overlap, float groundClearance) {
            this(mode, withBody, overlap, groundClearance, K, -0.5f, 1.0f);
        }

        LoadRig(int mode, boolean withBody, float overlap, float groundClearance, float k) {
            this(mode, withBody, overlap, groundClearance, k, -0.5f, 1.0f);
        }

        /**
         * @param mode 0 = 不改写接触，1 = 球心侧面，2 = 轮底
         * @param withBody 是否挂车体
         * @param overlap 初始立面重叠（负值 = 离立面还有这么大间隙）
         * @param k 悬挂 y 轴刚度
         * @param yLower y 轴行程下限
         * @param yUpper y 轴行程上限
         */
        LoadRig(int mode, boolean withBody, float overlap, float groundClearance,
                float k, float yLower, float yUpper) {
            this.mode = mode;
            this.overlap = overlap;
            this.k = k;
            this.yLower = yLower;
            this.yUpper = yUpper;
            space = new PhysicsSpace(new Vector3f(-1000f, -1000f, -1000f),
                    new Vector3f(1000f, 1000f, 1000f), PhysicsSpace.BroadphaseType.DBVT,
                    NativeLibrary.countThreads(), new CollisionConfiguration(8192, 1)) {
                @Override
                public void onContactProcessed(PhysicsCollisionObject pcoA, PhysicsCollisionObject pcoB, long id) {
                    rewrite(pcoA, pcoB, id);
                }
            };
            space.setGravity(new Vector3f(0f, -9.81f, 0f));
            space.getSolverInfo().setMode(SolverMode.SIMD | SolverMode.WarmStart
                    | SolverMode.CacheFriendly | SolverMode.CacheDirection
                    | SolverMode.ArticulatedWarmStart | SolverMode.Interleave);
            lower = new PhysicsRigidBody(new BoxCollisionShape(new Vector3f(4f, 0.5f, 4f)), 0f);
            lower.setPhysicsLocation(new Vector3f(-4f, -0.5f, 0f));
            lower.setFriction(0f);
            lower.setRestitution(0f);
            space.addCollisionObject(lower);
            upper = new PhysicsRigidBody(new BoxCollisionShape(new Vector3f(4f, 0.5f, 4f)), 0f);
            upper.setPhysicsLocation(new Vector3f(4f, 0.5f, 0f));
            upper.setFriction(0f);
            upper.setRestitution(0f);
            space.addCollisionObject(upper);
            float wheelX = overlap - RADIUS;
            float wheelY = RADIUS + groundClearance;
            wheel = new PhysicsRigidBody(new SphereCollisionShape(RADIUS), MASS);
            wheel.setPhysicsLocationDp(new Vec3d((double) wheelX, (double) wheelY, 0d));
            wheel.setDamping(0f, 0f);
            wheel.setSleepingThresholds(0f, 0f);
            wheel.setFriction(0f);
            wheel.setRestitution(0f);
            space.addCollisionObject(wheel);
            if (!withBody) {
                body = null;
                joint = null;
                return;
            }
            body = new PhysicsRigidBody(
                    new BoxCollisionShape(new Vector3f(BODY_HALF_X, BODY_HALF_Y, BODY_HALF_Z)), BODY_MASS);
            body.setPhysicsLocation(new Vector3f(wheelX - BODY_HALF_X, wheelY + SEPARATION, 0f));
            body.setAngularFactor(new Vector3f(0f, 0f, 0f));
            body.setCollideWithGroups(0);
            body.setDamping(0f, 0f);
            body.setSleepingThresholds(0f, 0f);
            body.setFriction(0f);
            body.setRestitution(0f);
            space.addCollisionObject(body);
            joint = new New6Dof(body, wheel,
                    new Vector3f(BODY_HALF_X, -SEPARATION, 0f), new Vector3f(0f, 0f, 0f),
                    new Matrix3f(), new Matrix3f(), RotationOrder.XYZ);
            joint.setCollisionBetweenLinkedBodies(true);
            joint.set(MotorParam.LowerLimit, 1, yLower);
            joint.set(MotorParam.UpperLimit, 1, yUpper);
            joint.set(MotorParam.Equilibrium, 1, 0f);
            joint.set(MotorParam.Stiffness, 1, k);
            joint.set(MotorParam.Damping, 1, C);
            joint.enableSpring(1, true);
            joint.set(MotorParam.LowerLimit, 5, 1f);
            joint.set(MotorParam.UpperLimit, 5, -1f);
            space.addJoint(joint);
        }

        private void rewrite(PhysicsCollisionObject pcoA, PhysicsCollisionObject pcoB, long id) {
            if (mode == 0 || wheel == null) return;
            boolean wheelIsA = pcoA.nativeId() == wheel.nativeId();
            PhysicsCollisionObject other = wheelIsA ? pcoB : pcoA;
            if (other.nativeId() != upper.nativeId()) return;
            Vector3f normal = new Vector3f();
            ManifoldPoints.getNormalWorldOnB(id, normal);
            if (!wheelIsA) normal.negateLocal();
            float distance = ManifoldPoints.getDistance1(id);
            rawPoints++;
            if (Float.isNaN(rawDistance) || distance < rawDistance) {
                rawDistance = distance;
                rawNormal.set(normal);
            }
            if (Math.abs(normal.y) >= 0.7f) {
                //承载接触：mode 3 复刻 CollisionHandler 的抬升垫上限，只有深过一个垫的才钳
                if (mode == 3 && distance < -depth) {
                    cappedThisStep = true;
                    ManifoldPoints.setAppliedImpulse(id, 0f);
                    ManifoldPoints.setDistance1(id, -depth);
                }
                return;
            }
            if (distance >= 0f) return;
            rewritten++;
            liveThisStep = true;
            ManifoldPoints.setAppliedImpulse(id, 0f);
            ManifoldPoints.setAppliedImpulseLateral1(id, 0f);
            ManifoldPoints.setAppliedImpulseLateral2(id, 0f);
            ManifoldPoints.setNormalWorldOnB(id, wheelIsA ? Vector3f.UNIT_Y : Vector3f.UNIT_Y.negate());
            ManifoldPoints.setDistance1(id, -depth);
            if (mode >= 2) {
                Vector3f point = wheel.getPhysicsLocation(null);
                point.y -= RADIUS;
                ManifoldPoints.setPositionWorldOnA(id, point);
                ManifoldPoints.setPositionWorldOnB(id, point);
            }
            ManifoldPoints.setCombinedRestitution(id, 0f);
        }

        void settle(int steps) {
            advance(steps);
        }

        /** 把轮子（和车体）一起沿 x 平移，使立面重叠变成 {@code newOverlap}。 */
        void engage(float newOverlap) {
            float dx = newOverlap - overlap;
            Vector3f p = wheel.getPhysicsLocation(null);
            wheel.setPhysicsLocation(new Vector3f(p.x + dx, p.y, p.z));
            if (body != null) {
                Vector3f b = body.getPhysicsLocation(null);
                body.setPhysicsLocation(new Vector3f(b.x + dx, b.y, b.z));
            }
            overlap = newOverlap;
        }

        void advance(int steps) {
            for (int i = 0; i < steps; i++) {
                liveThisStep = false;
                cappedThisStep = false;
                rawPoints = 0;
                rawDistance = Float.NaN;
                if (downforce != 0f) wheel.applyCentralForce(new Vector3f(0f, -downforce, 0f));
                if (press != 0f) (body != null ? body : wheel).applyCentralForce(new Vector3f(press, 0f, 0f));
                space.update(DT, 0, StepFlag.contactProcessed);
            }
        }

        float bottom() {
            return wheel.getPhysicsLocation(null).y - RADIUS;
        }

        /** 车体到轮心的竖直距离；没有车体时返回 0。 */
        float suspensionLength() {
            if (body == null) return 0f;
            return body.getPhysicsLocation(null).y - wheel.getPhysicsLocation(null).y;
        }

        /** 弹簧压缩量按 k×压缩量 折算的力；没有车体时返回 0。 */
        float springForce() {
            if (body == null) return 0f;
            return k * (SEPARATION - suspensionLength());
        }

        @Override
        public void close() {
            if (joint != null) space.removeJoint(joint);
            if (body != null) space.removeCollisionObject(body);
            space.removeCollisionObject(wheel);
            space.removeCollisionObject(upper);
            space.removeCollisionObject(lower);
            space.destroy();
        }
    }

    private static PhysicsSpace newSpace() {
        PhysicsSpace space = new PhysicsSpace(new Vector3f(-1000f, -1000f, -1000f),
                new Vector3f(1000f, 1000f, 1000f), PhysicsSpace.BroadphaseType.DBVT,
                NativeLibrary.countThreads(), new CollisionConfiguration(8192, 1));
        space.setGravity(new Vector3f(0f, -9.81f, 0f));
        SolverInfo info = space.getSolverInfo();
        info.setMode(SolverMode.SIMD | SolverMode.WarmStart
                | SolverMode.CacheFriendly | SolverMode.CacheDirection
                | SolverMode.ArticulatedWarmStart | SolverMode.Interleave);
        return space;
    }
}
