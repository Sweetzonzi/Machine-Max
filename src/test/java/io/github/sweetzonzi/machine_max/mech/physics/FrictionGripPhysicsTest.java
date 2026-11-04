package io.github.sweetzonzi.machine_max.mech.physics;

import com.jme3.bullet.CollisionConfiguration;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
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

/**
 * 切向（库仑）摩擦的纯引擎验证，用于回答「摩擦是否也有滚阻那类问题」。
 *
 * <p>滚阻的问题出在参数单位：它是角冲量上限，被 {@code min(μ·J_n, μ)} 钳制，于是与载荷无关、
 * 与步长成反比。切向摩擦不是这个形状——求解器给摩擦行的界限是
 * {@code ±m_friction·J_n}（{@code btSequentialImpulseConstraintSolver.cpp:1619-1620}），
 * 系数无量纲、直接乘本步法向冲量，没有常数封顶。本测试把这三条性质逐个量出来：
 * 减速度等于 {@code μ·g}、与载荷成正比、与步长无关。</p>
 *
 * <p>球/盒用 {@link PhysicsRigidBody} 的默认求解配置（{@code WarmStart|SIMD}），
 * 与游戏内一样走单条摩擦方向、非缓存方向的分支，摩擦方向由相对切向速度给出。</p>
 */
class FrictionGripPhysicsTest {
    private static final float STEP = 1f / 100f;
    private static final float GRAVITY = 9.81f;
    private static final float MASS = 50f;
    private static final float FRICTION = 0.5f;
    private static final float SPEED = 5f;

    @BeforeAll
    static void loadNative() throws Exception {
        NativeLoader.load();
    }

    /** 滑动减速度应等于 μg：系数就是系数，不掺杂步长与质量。 */
    @Test
    void slidingDecelerationEqualsMuG() {
        Rig rig = new Rig(GRAVITY, FRICTION, STEP);
        rig.settle(100);
        rig.launch();

        float deceleration = rig.deceleration(10, 40);
        float expected = FRICTION * GRAVITY;
        System.out.printf("[friction] g=%.2f mu=%.2f a=%.4f expect=%.4f%n",
                GRAVITY, FRICTION, deceleration, expected);
        assertEquals(expected, deceleration, 0.03f * expected,
                "滑动减速度应为 μg（" + expected + " m/s²），实测 " + deceleration);
    }

    /** 载荷加倍，摩擦力加倍：这正是滚阻做不到的那条性质。 */
    @Test
    void frictionScalesLinearlyWithNormalLoad() {
        float normal = new Rig(1f * GRAVITY, FRICTION, STEP).decelerationAfterSettle();
        float doubled = new Rig(2f * GRAVITY, FRICTION, STEP).decelerationAfterSettle();

        System.out.printf("[friction] g=%.2f a=%.4f / g=%.2f a=%.4f ratio=%.4f%n",
                GRAVITY, normal, 2f * GRAVITY, doubled, doubled / normal);
        assertEquals(2f, doubled / normal, 0.05f,
                "载荷加倍时减速度应加倍（滚阻在这一点上会保持不变）；实测 " + normal + " → " + doubled);
    }

    /** 步长减半，减速度不变：摩擦没有滚阻那类 Δt 反比。 */
    @Test
    void frictionIsIndependentOfStepSize() {
        float coarse = new Rig(GRAVITY, FRICTION, STEP).decelerationAfterSettle();
        float fine = new Rig(GRAVITY, FRICTION, STEP / 2f).decelerationAfterSettle();

        System.out.printf("[friction] dt=%.4f a=%.4f / dt=%.4f a=%.4f%n",
                STEP, coarse, STEP / 2f, fine);
        assertEquals(coarse, fine, 0.05f * coarse,
                "步长减半不应改变滑动减速度（滚阻会加倍）；实测 " + coarse + " → " + fine);
    }

    /**
     * 静止接触的静摩擦：斜坡上 tan θ 小于 μ 时必须站住，且与下坡方向的方位无关。
     *
     * <p>这条针对「每接触点只解一条摩擦方向」的配置（Spark-Core 的求解器模式没有开
     * {@code Use2FrictionDirections}）。切向速度接近零时求解器用 {@code btPlaneSpace1} 取接触平面基轴，
     * 只有那一条轴有摩擦行；若基轴与下坡方向不重合，静摩擦就只挡住一个分量。
     * 水平地面的基轴是世界 X / Z，所以这里按下坡方位逐一试。</p>
     */
    @Test
    void restingBoxHoldsOnSlopeWhateverTheDownhillAzimuth() {
        float slopeDegrees = 20f;
        float tangent = (float) Math.tan(Math.toRadians(slopeDegrees));
        for (float azimuth : new float[]{0f, 45f, 90f}) {
            Rig rig = new Rig(GRAVITY, 0.8f, STEP, slopeDegrees, azimuth);
            rig.settle(200);

            float creep = rig.speedMagnitude();
            System.out.printf("[friction] slope=%.0f° azimuth=%.0f° tan=%.3f mu=0.80 creep=%.5f m/s%n",
                    slopeDegrees, azimuth, tangent, creep);
            assertEquals(0f, creep, 0.02f,
                    "tan θ (" + tangent + ") 远小于 μ=0.8，方位 " + azimuth + "° 的斜坡上也应站住；实测滑速 " + creep);
        }
    }

    /** 一个独立物理空间 + 静止地面 + 可滑动的盒子。 */
    private static final class Rig {
        final PhysicsSpace space;
        final PhysicsRigidBody body;
        final float step;

        Rig(float gravity, float friction, float step) {
            this(gravity, friction, step, 0f);
        }

        Rig(float gravity, float friction, float step, float slopeDegrees) {
            this(gravity, friction, step, slopeDegrees, 0f);
        }

        Rig(float gravity, float friction, float step, float slopeDegrees, float azimuthDegrees) {
            this.step = step;
            space = new PhysicsSpace(
                    new Vector3f(-100f, -100f, -100f),
                    new Vector3f(100f, 100f, 100f),
                    PhysicsSpace.BroadphaseType.DBVT,
                    NativeLibrary.countThreads(),
                    new CollisionConfiguration(8192, 1));
            // 斜面用倾斜重力表示：接触法线仍为 +y，切向分量 g·sin θ 沿给定方位作用。
            double radians = Math.toRadians(slopeDegrees);
            double azimuth = Math.toRadians(azimuthDegrees);
            float downhill = gravity * (float) Math.sin(radians);
            space.setGravity(new Vector3f(
                    downhill * (float) Math.cos(azimuth),
                    -gravity * (float) Math.cos(radians),
                    downhill * (float) Math.sin(azimuth)));

            PhysicsRigidBody ground = new PhysicsRigidBody(
                    new BoxCollisionShape(new Vector3f(20f, 0.5f, 20f)), 0f);
            ground.setPhysicsLocation(new Vector3f(0f, -0.5f, 0f));
            ground.setFriction(1f);
            space.addCollisionObject(ground);

            body = new PhysicsRigidBody(new BoxCollisionShape(new Vector3f(0.5f, 0.5f, 0.5f)), MASS);
            body.setPhysicsLocation(new Vector3f(0f, 0.5f, 0f));
            body.setFriction(friction);
            body.setDamping(0f, 0f);
            body.setSleepingThresholds(0f, 0f);
            space.addCollisionObject(body);
        }

        void step(int count) {
            for (int i = 0; i < count; i++) space.update(step, 0);
        }

        void settle(int count) {
            step(count);
        }

        void launch() {
            body.setLinearVelocity(new Vector3f(SPEED, 0f, 0f));
            body.setAngularVelocity(Vector3f.ZERO.clone());
            body.activate();
        }

        float speed() {
            return body.getLinearVelocity(null).x;
        }

        float speedMagnitude() {
            return body.getLinearVelocity(null).length();
        }

        /** 静置后赋初速，取两点差分得到滑动减速度。 */
        float deceleration(int warmup, int window) {
            step(warmup);
            float before = speed();
            step(window);
            float after = speed();
            return (before - after) / (window * step);
        }

        float decelerationAfterSettle() {
            settle(100);
            launch();
            return deceleration(10, 40);
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
                try (InputStream input = FrictionGripPhysicsTest.class.getClassLoader()
                        .getResourceAsStream(resource)) {
                    if (input == null) continue;
                    Path temporary = Files.createTempFile("friction-bullet-",
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
