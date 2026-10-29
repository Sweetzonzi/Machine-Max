package io.github.sweetzonzi.machine_max.mech.collision;

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
import com.jme3.bullet.collision.shapes.SphereCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.bullet.util.NativeLibrary;
import com.jme3.math.Vector3f;
import com.simsilica.mathd.Vec3d;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.collision.CollisionImpactModel;
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
 * compound 子形状筛除的接触容差：50 kg 轮胎、12.5 kN 向下载荷、10 ms 子步。
 *
 * <p>{@code btCompoundCollisionAlgorithm::ProcessChildShape} 与
 * {@code btCompoundCompoundCollisionAlgorithm::MycollideTT}/{@code Process} 先做子形状 AABB 重叠检查。
 * 凸形体的 AABB 是紧贴几何的，所以这些检查必须显式加上窄相接受的接触容差
 * （{@code gContactBreakingThreshold}），否则外层 broadphase 已经接受的近接触会在
 * 任何窄相算法执行之前被丢弃，轮子失去一整个子步的支撑。</p>
 *
 * <p>本机 native 的构建方式见 {@code build/native/build-native.bat}：source 为 Libbulletjme
 * 23.1.2，静态 CRT（{@code /MT}）、双精度、多线程、Release。</p>
 *
 * <p><b>只在 Windows 上跑：</b>补丁只重建了
 * {@code natives/bullet_dpmt/windows/x86_64/bulletjme.dll}，其余十个二进制是上游 23.1.0
 * 出厂件、不含修正。在 Linux/macOS 上这组断言必然失败（stock native 会丢掉近接触），
 * 所以整类按平台跳过，避免 CI 把"补丁没覆盖的平台"当成回归。</p>
 */
@EnabledOnOs(OS.WINDOWS)
class CompoundContactToleranceTest {
    private static final float RADIUS = 0.84375f;
    private static final float MASS = 50f;
    private static final float LOAD = 12500f;
    private static final float DT = 0.01f;

    @BeforeAll
    static void loadNative() throws Exception {
        //指向任意一份 native（例如出厂构件），用于在同一套 rig 下对比不同构建：
        //  MM_NATIVE_PATH=<path to bulletjme.dll> gradlew test --tests ...CompoundContactToleranceTest
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
            try (InputStream input = CompoundContactToleranceTest.class.getClassLoader().getResourceAsStream(resource)) {
                if (input == null) continue;
                Path temporary = Files.createTempFile("contact-tolerance-", library.substring(library.lastIndexOf('.')));
                Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
                temporary.toFile().deleteOnExit();
                System.load(temporary.toAbsolutePath().toString());
                return;
            }
        }
        throw new IllegalStateException("找不到 Spark-Core 的 Bullet 双精度 native 资源");
    }

    @Test
    void compoundCullingMatchesBareShapesWithinTheContactBreakingThreshold() {
        //覆盖到成对阈值（2.92 cm）两侧：2.0～2.9 cm 这一段是"按全局 0.02 扩展"漏掉的区间
        double[] gaps = {-1e-6, 0d, 1e-6, 1e-3, 0.01, 0.015, 0.02, 0.021, 0.025, 0.029, 0.035};
        for (boolean wheelCompound : new boolean[]{false, true}) {
            for (boolean terrainCompound : new boolean[]{false, true}) {
                if (!wheelCompound && !terrainCompound) continue;
                for (double gap : gaps) {
                    try (Rig compound = new Rig(wheelCompound, terrainCompound, gap);
                         Rig bare = new Rig(false, false, gap)) {
                        Sample actual = compound.advance();
                        Sample expected = bare.advance();
                        System.out.printf(Locale.ROOT,
                                "wheelCompound=%-5s terrainCompound=%-5s gap=%+9.6f  points=%d/%d dist=%+.6f/%+.6f impulse=%8.3f/%8.3f%n",
                                wheelCompound, terrainCompound, gap,
                                actual.points, expected.points, actual.distance, expected.distance,
                                actual.impulse, expected.impulse);
                        assertEquals(expected.points(), actual.points(), "compound 与裸形状的接触点数必须一致");
                        if (expected.points() > 0) {
                            assertEquals(expected.distance(), actual.distance(), 1e-6f, "接触距离必须一致");
                            assertEquals(expected.impulse(), actual.impulse(), 1e-2f, "支撑冲量必须一致");
                        }
                    }
                }
            }
        }
        for (boolean compound : new boolean[]{false, true}) {
            try (Rig rig = new Rig(compound, compound, 0.04)) {
                int points = rig.advance().points();
                System.out.printf(Locale.ROOT, "compound=%-5s gap=+0.040000 points=%d%n", compound, points);
                assertEquals(0, points, "超出成对接触容差（2.92 cm）后两侧都不应有接触");
            }
        }
    }

    /**
     * 2.1 cm 间隙是实测缺口：裸形状仍有接触并传递约 24.9 N·s，按全局 0.02 扩展的 compound 没有接触。
     * 失去这一步支撑后，预载把轮子一步按进地面 2.6 m/s、4.98 mm，正好穿过 2 mm 深度门与 2 m/s 速度门。
     */
    @Test
    void nearContactJustBeyondTwoCentimetresStillSupportsTheWheel() {
        try (Rig compound = new Rig(true, true, 0.021); Rig bare = new Rig(false, false, 0.021)) {
            Sample actual = compound.advance();
            Sample expected = bare.advance();
            System.out.printf(Locale.ROOT, "gap=+0.021000 bare=%s compound=%s%n", expected, actual);
            assertEquals(1, expected.points(), "裸形状在 2.1 cm 间隙仍有接触");
            assertEquals(expected.points(), actual.points(), "compound 不能在这里比裸形状少");
            assertEquals(expected.impulse(), actual.impulse(), 1e-2f, "支撑冲量必须一致");
            assertTrue(expected.impulse() > 20f, "2.1 cm 间隙的支撑冲量应有 20 N·s 量级");
        }
    }

    @Test
    void supportGapBeyondTheContactToleranceFallsOneStepAndTheDepthGateRejectsIt() {
        try (Rig rig = new Rig(true, true, 0.04)) {
            Sample unsupported = rig.advance();
            Sample landing = rig.advance();
            System.out.printf(Locale.ROOT, "gap=+0.040000 unsupported=%s landing=%s u=%f%n",
                    unsupported, landing, rig.deepApproach);
            assertEquals(0, unsupported.points(), "超出接触容差后支撑点应当消失");
            assertEquals(0f, unsupported.impulse());
            assertEquals(-(LOAD / MASS + 9.81f) * DT, unsupported.verticalVelocity(), 1e-3f,
                    "失去支撑的一步只受载荷与重力");
            assertEquals(1, landing.points(), "恢复接触后本步必须有求解点");
            assertTrue(landing.distance() > -0.002f, "4 cm 间隙只够掉一步，重新接触时仍未侵入 2 mm");
            assertEquals(0f, rig.deepApproach, 1e-6f, "本步没有深接触，速度门不会被评估");
            assertTrue(landing.impulse() > 100f, "正距离接触仍传递部分支撑冲量");
        }
    }

    /**
     * 地面由 1 m 小方块拼成时，物体级形状（整个 compound）的 disc 是 11.34 m、子形状只有 0.866 m。
     * 成对阈值必须按物体级形状算，否则 20～29.2 mm 这一段仍然丢接触；按子形状算的窗口是 17.3 mm。
     */
    @Test
    void nearContactsSurviveOnACompoundGroundOfSmallBoxes() {
        for (double gap : new double[]{0.001, 0.015, 0.02, 0.021, 0.025}) {
            for (boolean wheelCompound : new boolean[]{false, true}) {
                try (Rig rig = new Rig(wheelCompound, true, gap, true)) {
                    Sample sample = rig.advance();
                    System.out.printf(Locale.ROOT, "smallBoxGround wheelCompound=%-5s gap=%+.6f %s%n",
                            wheelCompound, gap, sample);
                    assertTrue(sample.points() > 0,
                            "间隙 " + gap + " 在成对阈值（29.2 mm）内，必须有接触点");
                }
            }
        }
        try (Rig rig = new Rig(true, true, 0.035, true)) {
            assertEquals(0, rig.advance().points(), "超出成对阈值后不应有接触");
        }
    }

    private static CollisionShape wrap(CollisionShape shape, boolean compound) {
        if (!compound) return shape;
        CompoundCollisionShape result = new CompoundCollisionShape();
        result.addChildShape(shape, new Vector3f());
        return result;
    }

    /**
     * 地面形状。{@code smallBoxGround} 用 256 个 1 m 方块拼出与整块大盒相同的 16×1×16 m 板，
     * 这是真实地形的形态：物体级 disc 是 11.34 m，而子形状只有 0.866 m。
     */
    private static CollisionShape groundShape(boolean compound, boolean smallBoxGround) {
        if (!smallBoxGround) {
            return wrap(new BoxCollisionShape(new Vector3f(8f, 0.5f, 8f)), compound);
        }
        CompoundCollisionShape result = new CompoundCollisionShape();
        BoxCollisionShape unit = new BoxCollisionShape(new Vector3f(0.5f, 0.5f, 0.5f));
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                result.addChildShape(unit, new Vector3f(x - 7.5f, 0f, z - 7.5f));
            }
        }
        return result;
    }

    /**
     * 轮子 compound：只包一个球。
     *
     * <p>成对接触阈值是 {@code min(两种形状的 angular motion disc) × 0.02}，而 disc 取 AABB
     * 对角线的一半（加中心偏移）。包一个球的 compound 与裸球 AABB 相同，阈值同为
     * {@code 0.84375 × √3 × 0.02 = 29.2 mm}，因此两侧可以逐点比较；生产轮子（球 + 轮体）
     * 的 disc 也在同一量级，所以这里覆盖的是同一段区间。</p>
     */
    private static CollisionShape wheelShape(boolean compound) {
        if (!compound) return new SphereCollisionShape(RADIUS);
        CompoundCollisionShape result = new CompoundCollisionShape();
        result.addChildShape(new SphereCollisionShape(RADIUS), new Vector3f());
        return result;
    }

    private record Sample(int points, float distance, float impulse, float verticalVelocity) {
    }

    private static final class Rig implements AutoCloseable {
        final PhysicsSpace space;
        final PhysicsRigidBody ground;
        final PhysicsRigidBody wheel;
        float deepApproach;

        Rig(boolean wheelCompound, boolean terrainCompound, double gap) {
            this(wheelCompound, terrainCompound, gap, false);
        }

        Rig(boolean wheelCompound, boolean terrainCompound, double gap, boolean smallBoxGround) {
            space = new PhysicsSpace(new Vector3f(-1000f, -1000f, -1000f),
                    new Vector3f(1000f, 1000f, 1000f), PhysicsSpace.BroadphaseType.DBVT,
                    NativeLibrary.countThreads(), new CollisionConfiguration(8192, 1)) {
                @Override
                public void onContactProcessed(PhysicsCollisionObject a, PhysicsCollisionObject b, long id) {
                    if (ManifoldPoints.getDistance1(id) > -0.002f) return;
                    Vector3f normal = new Vector3f();
                    ManifoldPoints.getNormalWorldOnB(id, normal);
                    if (a.nativeId() != wheel.nativeId()) normal.negateLocal();
                    deepApproach = Math.max(deepApproach,
                            CollisionImpactModel.approachSpeed(wheel.getLinearVelocity(null), normal));
                }
            };
            space.setGravity(new Vector3f(0f, -9.81f, 0f));
            space.getSolverInfo().setMode(SolverMode.SIMD | SolverMode.WarmStart
                    | SolverMode.CacheFriendly | SolverMode.CacheDirection
                    | SolverMode.ArticulatedWarmStart | SolverMode.Interleave);
            ground = new PhysicsRigidBody(groundShape(terrainCompound, smallBoxGround), 0f);
            ground.setPhysicsLocation(new Vector3f(0f, -0.5f, 0f));
            ground.setFriction(0f);
            ground.setRestitution(0f);
            space.addCollisionObject(ground);
            wheel = new PhysicsRigidBody(wheelShape(wheelCompound), MASS);
            wheel.setPhysicsLocationDp(new Vec3d(0d, RADIUS + gap, 0d));
            wheel.setDamping(0f, 0f);
            wheel.setSleepingThresholds(0f, 0f);
            wheel.setFriction(0f);
            wheel.setRestitution(0f);
            space.addCollisionObject(wheel);
        }

        Sample advance() {
            wheel.applyCentralForce(new Vector3f(0f, -LOAD, 0f));
            space.update(DT, 0, StepFlag.contactProcessed);
            int points = 0;
            float distance = Float.POSITIVE_INFINITY;
            float impulse = 0f;
            for (long manifold : space.listManifoldIds()) {
                for (int i = 0; i < PersistentManifolds.countPoints(manifold); i++) {
                    long id = PersistentManifolds.getPointId(manifold, i);
                    points++;
                    distance = Math.min(distance, ManifoldPoints.getDistance1(id));
                    impulse += ManifoldPoints.getAppliedImpulse(id);
                }
            }
            return new Sample(points, distance, impulse, wheel.getLinearVelocity(null).y);
        }

        @Override
        public void close() {
            space.removeCollisionObject(wheel);
            space.removeCollisionObject(ground);
            space.destroy();
        }
    }
}
