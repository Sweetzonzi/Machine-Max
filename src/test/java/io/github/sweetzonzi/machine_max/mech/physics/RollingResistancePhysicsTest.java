package io.github.sweetzonzi.machine_max.mech.physics;

import com.jme3.bullet.CollisionConfiguration;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.SphereCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.bullet.util.NativeLibrary;
import com.jme3.math.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 滚动阻力的纯引擎验证（不启动 Minecraft / GameTestServer）。
 *
 * <p>只使用 Spark-Core 内嵌的 jme3 Bullet 层：{@link PhysicsSpace} + 一个静止地面 + 一个球。
 * native 从 Spark-Core 的 {@code natives/bullet_dpmt/...} 资源手工加载，与 {@code GearJointPhysicsTest}
 * 走同一条求解器路径。选球而不是车轮，是因为球在任何方向都能滚、不会倾倒，能把"接触"与"姿态"
 * 两个变量分开——车轮探针一旦倾倒，量到的就不是滚阻。</p>
 *
 * <p><b>为什么先静置、后赋速度。</b>游戏内在载具生成后有一段时间刚体被钉住
 * （{@code VehicleCore.freezePhysicsForTerrainWait}：平动/转动系数归零、速度清零），解除时又会
 * 再清一次速度（{@code unfreezePhysicsForTerrainWait} 里的 {@code setLinearVelocity} /
 * {@code setAngularVelocity} / {@code activate}）。生成瞬间赋的初速会被吞掉，测到的是一条
 * 已被清零的轨迹。所以这里的顺序是：先让刚体在重力下自然静置到接触稳定，再赋
 * {@code v = ωr} 的纯滚动初态，并且把勾选前置条件（真的在滚、没有滑）当成断言。</p>
 *
 * <p><b>锁定的模型。</b>Bullet 的滚动摩擦是逐接触点的<b>角冲量上限</b>，不是"系数 × 正压力"：
 * {@code btSequentialImpulseConstraintSolver.cpp} 把它钳成 {@code min(μ_r·J_n, μ_r)}，
 * 载具的 {@code J_n} 远大于 1，因此上限恒为 {@code μ_r}，等效力矩 {@code τ = μ_r/Δt}。
 * 地面与轮面各自的滚动摩擦按 {@code r0·f1 + r1·f0} 合成
 * （{@code btManifoldResult.cpp:34}）。纯滚动下这个力矩作用在转动上，切向摩擦再把平移与转动
 * 耦合回去，于是线减速度为</p>
 *
 * <pre>a = τ·r / (I + m·r²) = μ_r·r / (Δt·(I + m·r²))</pre>
 *
 * <p>本测试用实测减速度反解 {@code τ = a·(I + m·r²)/r}，断言它等于 {@code μ_r/Δt}，
 * 并分别验证三条推论：与正压力无关、与步长成反比、与地面摩擦按乘积合成。</p>
 *
 * <p><b>与材质字段的关系。</b>上面锁定的三条性质都不适合直接当滚阻用：真实滚阻与载荷成正比、
 * 与步长无关。所以材质里的 {@code rolling_friction} 是常规无量纲系数 {@code C_rr}，
 * 由 {@code CollisionHandler.setupContactFriction} 按 {@code τ = C_rr·N·r} 折成角冲量上限
 * {@code J = C_rr·J_n·r} 之后再交给同一个求解器。本测试固定求解器一侧的事实，
 * 不涉及那次换算。</p>
 */
class RollingResistancePhysicsTest {
    private static final float STEP = 1f / 100f;
    private static final float GRAVITY = 9.81f;
    /** 探针参数：车轮量级（50 kg、半径 0.7 m 上下）。不读内容包，调材质不影响本测试。 */
    private static final float MASS = 50f;
    private static final float RADIUS = 0.672f;
    /**
     * 直接交给 {@code setRollingFriction} 的原始值，不是材质字段 {@code C_rr}。
     * 取 0.2 是为了让 {@code μ_r/Δt} 落在容易测准的区间。
     */
    private static final float ROLLING = 0.2f;
    private static final float SPEED = 2f;

    @BeforeAll
    static void loadNative() throws Exception {
        NativeLoader.load();
    }

    /**
     * 静置后赋纯滚动初态，滚动的运动学关系必须保持。
     *
     * <p>这是所有测量的前置条件：若车体在滑而不是在滚，减速度反映的是滑动摩擦
     * （{@code μg}，比滚阻大两个数量级），后续测量全部作废。</p>
     */
    @Test
    void settlesThenRollsWithoutSlipping() {
        Rig rig = new Rig(MASS, RADIUS, ROLLING, 1f, GRAVITY);
        rig.settle(150);
        assertEquals(0f, rig.speed(), 0.01f, "静置后应静止（前置条件：没有残余速度）");

        rig.launch();
        rig.step(20);

        float speed = rig.speed();
        float contactSpeed = rig.rollRate();
        assertEquals(speed, contactSpeed, Math.max(0.02f, 0.02f * speed),
                "滚动关系 v = ωr 必须保持；偏离即说明接触在滑动而不是滚动");
        assertTrue(speed > 0.9f * SPEED, "赋初速后不应在 0.2 s 内溃散，实测 " + speed);
    }

    /** 由减速度反解出的滚阻力矩应等于 μ_r/Δt。 */
    @Test
    void resistanceTorqueEqualsRollingFrictionOverStep() {
        Rig rig = new Rig(MASS, RADIUS, ROLLING, 1f, GRAVITY);
        rig.settle(150);
        rig.launch();

        float deceleration = rig.deceleration(20, 200);
        float torque = rig.resistanceTorque(deceleration);
        float expected = ROLLING / STEP;

        System.out.printf("[rolling] m=%.1f r=%.3f I=%.3f a=%.4f m/s^2 tau=%.3f N·m expect=%.3f%n",
                rig.mass, rig.radius, rig.inertia, deceleration, torque, expected);
        assertEquals(expected, torque, 0.05f * expected,
                "滚阻力矩应为 μ_r/Δt（" + expected + " N·m），实测 " + torque);
    }

    /** 正压力加倍不应改变滚阻力矩：上限被 μ_r 钳住，而不是随 J_n 增长。 */
    @Test
    void resistanceDoesNotScaleWithNormalLoad() {
        float normal = decelerationUnder(1f * GRAVITY);
        float doubled = decelerationUnder(2f * GRAVITY);

        System.out.printf("[rolling] g=%.2f a=%.4f / g=%.2f a=%.4f%n",
                GRAVITY, normal, 2f * GRAVITY, doubled);
        assertEquals(normal, doubled, 0.05f * normal,
                "正压力加倍后减速度应基本不变（μ_r·J_n 已饱和）；实测 " + normal + " → " + doubled);
    }

    /** 步长减半，等效力矩加倍：τ = μ_r/Δt 的 Δt 依赖。 */
    @Test
    void halvingStepSizeDoublesResistance() {
        float coarse = decelerationUnder(1f * GRAVITY, STEP);
        float fine = decelerationUnder(1f * GRAVITY, STEP / 2f);

        System.out.printf("[rolling] dt=%.4f a=%.4f / dt=%.4f a=%.4f%n",
                STEP, coarse, STEP / 2f, fine);
        assertEquals(2f, fine / coarse, 0.1f,
                "步长减半后减速度应加倍（同一 μ_r 折算出双倍力矩）；实测 " + coarse + " → " + fine);
    }

    /** 地面滚动摩擦按 r0·f1 + r1·f0 合成：地面摩擦减半，滚阻力矩同样减半。 */
    @Test
    void groundFrictionScalesCombinedRollingFriction() {
        float full = decelerationUnder(1f * GRAVITY, STEP, 1f);
        float half = decelerationUnder(1f * GRAVITY, STEP, 0.5f);

        System.out.printf("[rolling] ground f=1.0 a=%.4f / f=0.5 a=%.4f%n", full, half);
        assertEquals(2f, full / half, 0.1f,
                "地面摩擦减半后滚阻力矩应减半（合成式 r0·f1 + r1·f0）；实测 " + full + " → " + half);
    }

    private static float decelerationUnder(float gravity) {
        return decelerationUnder(gravity, STEP, 1f);
    }

    private static float decelerationUnder(float gravity, float step) {
        return decelerationUnder(gravity, step, 1f);
    }

    /** 以指定重力、步长与地面摩擦跑一次静置 + 纯滚动，返回稳定后的线减速度。 */
    private static float decelerationUnder(float gravity, float step, float groundFriction) {
        Rig rig = new Rig(MASS, RADIUS, ROLLING, groundFriction, gravity, step);
        rig.settle(200);
        rig.launch();
        rig.step(20);
        return rig.deceleration(20, 200);
    }

    /** 一个独立物理空间 + 静止地面 + 一个可滚的球。 */
    private static final class Rig {
        final PhysicsSpace space;
        final PhysicsRigidBody ball;
        final float mass;
        final float radius;
        final float inertia;
        final float step;

        Rig(float mass, float radius, float rolling, float groundFriction, float gravity) {
            this(mass, radius, rolling, groundFriction, gravity, STEP);
        }

        Rig(float mass, float radius, float rolling, float groundFriction, float gravity, float step) {
            this.mass = mass;
            this.radius = radius;
            this.step = step;
            space = new PhysicsSpace(
                    new Vector3f(-100f, -100f, -100f),
                    new Vector3f(100f, 100f, 100f),
                    PhysicsSpace.BroadphaseType.DBVT,
                    NativeLibrary.countThreads(),
                    new CollisionConfiguration(8192, 1));
            space.setGravity(new Vector3f(0f, -gravity, 0f));

            PhysicsRigidBody ground = new PhysicsRigidBody(
                    new BoxCollisionShape(new Vector3f(20f, 0.5f, 20f)), 0f);
            ground.setPhysicsLocation(new Vector3f(0f, -0.5f, 0f));
            ground.setFriction(groundFriction);
            ground.setRollingFriction(0f);
            space.addCollisionObject(ground);

            ball = new PhysicsRigidBody(new SphereCollisionShape(radius), mass);
            ball.setPhysicsLocation(new Vector3f(0f, radius, 0f));
            ball.setFriction(1f);
            ball.setRollingFriction(rolling);
            ball.setDamping(0f, 0f);
            ball.setSleepingThresholds(0f, 0f);
            space.addCollisionObject(ball);
            inertia = 1f / ball.getInverseInertiaLocal(new Vector3f()).z;
        }

        void step(int count) {
            for (int i = 0; i < count; i++) space.update(step, 0);
        }

        void settle(int count) {
            step(count);
        }

        /** 赋纯滚动初态：v 沿 +x，ω 绕 -z，满足 v = ωr。 */
        void launch() {
            ball.setLinearVelocity(new Vector3f(SPEED, 0f, 0f));
            ball.setAngularVelocity(new Vector3f(0f, 0f, -SPEED / radius));
            ball.activate();
        }

        float speed() {
            return ball.getLinearVelocity(null).x;
        }

        float spin() {
            return ball.getAngularVelocity(null).z;
        }

        /** 由角速度反推的线速度，纯滚动时应与 {@link #speed()} 相等。 */
        float rollRate() {
            return -spin() * radius;
        }

        /** 稳定后测线减速度：先走一段暖机，再取两点差分。 */
        float deceleration(int warmup, int window) {
            step(warmup);
            float before = speed();
            step(window);
            float after = speed();
            return (before - after) / (window * step);
        }

        /** 由线减速度反解滚阻力矩：纯滚动下 τ = a·(I + m·r²)/r。 */
        float resistanceTorque(float deceleration) {
            return deceleration * (inertia + mass * radius * radius) / radius;
        }
    }

    /** 从 Spark-Core 资源加载 dpmt native（与游戏内同一求解器路径）。 */
    private static final class NativeLoader {
        private static boolean loaded;

        static synchronized void load() throws Exception {
            if (loaded) return;
            try {
                NativeLibrary.versionNumber();
                loaded = true;
                return;
            } catch (UnsatisfiedLinkError unloaded) {
                // 独立执行此测试时没有模组加载器，按当前系统选择实际引擎库。
            }
            String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
            String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
            String folder = os.contains("win") ? "windows" : os.contains("mac") ? "macos" : "linux";
            String library = os.contains("win") ? "bulletjme.dll"
                    : os.contains("mac") ? "libbulletjme.dylib" : "libbulletjme.so";
            String architecture = arch.contains("aarch64") || arch.contains("arm64") ? "arm64" : "x86_64";
            for (String variant : new String[]{"bullet_dpmt", "bullet_dp"}) {
                String resource = "natives/" + variant + "/" + folder + "/" + architecture + "/" + library;
                try (InputStream input = RollingResistancePhysicsTest.class.getClassLoader()
                        .getResourceAsStream(resource)) {
                    if (input == null) continue;
                    Path temporary = Files.createTempFile("rolling-bullet-",
                            library.substring(library.lastIndexOf('.')));
                    Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
                    temporary.toFile().deleteOnExit();
                    System.load(temporary.toAbsolutePath().toString());
                    loaded = true;
                    return;
                }
            }
            throw new IllegalStateException("找不到 Spark-Core 的 Bullet native 资源");
        }
    }
}
