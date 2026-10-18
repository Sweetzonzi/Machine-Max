package io.github.sweetzonzi.machine_max.track;

import com.jme3.bullet.CollisionConfiguration;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.RotationOrder;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.joints.GearJoint;
import com.jme3.bullet.joints.New6Dof;
import com.jme3.bullet.joints.motors.MotorParam;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.bullet.util.NativeLibrary;
import com.jme3.math.Matrix3f;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 履带物理的纯引擎验证（不启动 Minecraft / GameTestServer）。
 *
 * <p>只使用 Spark-Core 内嵌的 jme3 Bullet 层：{@link PhysicsSpace}、{@link PhysicsRigidBody}、
 * {@link GearJoint}。native 从 Spark-Core 的 {@code natives/bullet_dpmt/...} 资源手工加载，
 * 因此测试 JVM 不经过 FML，也不需要 {@code runGameTestServer}。</p>
 *
 * <p>覆盖计划 §9 阶段 1 的物理未知项：ratio 符号、轴 body-local、启停、长时间稳定性，
 * 以及「先摘约束再移刚体」的生命周期纪律。</p>
 */
class GearJointPhysicsTest {
    private static final float STEP = 1f / 100f;
    private final List<PhysicsSpace> spaces = new ArrayList<>();

    @BeforeAll
    static void loadNative() throws Exception {
        NativeLoader.load();
    }

    @AfterEach
    void clear() {
        spaces.clear();
    }

    @Test
    void ratioCouplesAngularVelocityInSameDirection() {
        try (Harness h = new Harness()) {
            PhysicsRigidBody sprocket = h.body(-2f, 0.5f, 10f);
            PhysicsRigidBody roadWheel = h.body(2f, 0.5f, 10f);
            // r_road / r_sprocket = 2；同向滚动要求 ratio 为负
            GearJoint joint = new GearJoint(sprocket, roadWheel,
                    new Vector3f(1, 0, 0), new Vector3f(1, 0, 0), -2f);
            h.space.addJoint(joint);

            sprocket.setAngularVelocity(new Vector3f(10f, 0f, 0f));
            h.step(200);

            float wa = sprocket.getAngularVelocity(null).x;
            float wb = roadWheel.getAngularVelocity(null).x;
            assertEquals(0.5f, wb / wa, 0.02f, "ω_road / ω_sprocket 应为 r_sprocket / r_road");
        }
    }

    @Test
    void gearAxisFollowsBodyRotation() {
        try (Harness h = new Harness()) {
            PhysicsRigidBody sprocket = h.body(-2f, 0.5f, 10f);
            PhysicsRigidBody roadWheel = h.body(2f, 0.5f, 10f);
            Quaternion rot = new Quaternion().fromAngles(0f, 0f, (float) Math.PI / 2f); // local X -> world Y
            sprocket.setPhysicsRotation(rot);
            roadWheel.setPhysicsRotation(rot);
            GearJoint joint = new GearJoint(sprocket, roadWheel,
                    new Vector3f(1, 0, 0), new Vector3f(1, 0, 0), -2f);
            h.space.addJoint(joint);

            sprocket.setAngularVelocity(new Vector3f(0f, 10f, 0f));
            h.step(200);

            Vector3f wa = sprocket.getAngularVelocity(null);
            Vector3f wb = roadWheel.getAngularVelocity(null);
            assertEquals(0.5f, wb.y / wa.y, 0.02f, "GearJoint 轴应随刚体姿态旋转");
            assertEquals(0f, wb.x, 0.05f, "不应泄漏到其它轴");
            assertEquals(0f, wb.z, 0.05f, "不应泄漏到其它轴");
        }
    }

    @Test
    void disabledJointDoesNotCoupleAndCanBeReenabled() {
        try (Harness h = new Harness()) {
            PhysicsRigidBody sprocket = h.body(-2f, 0.5f, 10f);
            PhysicsRigidBody roadWheel = h.body(2f, 0.5f, 10f);
            GearJoint joint = new GearJoint(sprocket, roadWheel,
                    new Vector3f(1, 0, 0), new Vector3f(1, 0, 0), -2f);
            h.space.addJoint(joint);
            sprocket.setAngularVelocity(new Vector3f(10f, 0f, 0f));
            h.step(100);

            joint.setEnabled(false);
            roadWheel.setAngularVelocity(Vector3f.ZERO);
            sprocket.setAngularVelocity(new Vector3f(10f, 0f, 0f));
            h.step(100);
            assertEquals(0f, roadWheel.getAngularVelocity(null).x, 0.01f, "禁用后不应继续耦合");

            joint.setEnabled(true);
            h.step(200);
            float wa = sprocket.getAngularVelocity(null).x;
            float wb = roadWheel.getAngularVelocity(null).x;
            assertEquals(0.5f, wb / wa, 0.05f, "重新启用后应恢复耦合");
        }
    }

    @Test
    void longRunStaysFiniteAndKeepsRatio() {
        try (Harness h = new Harness()) {
            PhysicsRigidBody sprocket = h.body(-2f, 0.5f, 10f);
            PhysicsRigidBody roadWheel = h.body(2f, 0.5f, 10f);
            h.space.addJoint(new GearJoint(sprocket, roadWheel,
                    new Vector3f(1, 0, 0), new Vector3f(1, 0, 0), -2f));
            sprocket.setAngularVelocity(new Vector3f(10f, 0f, 0f));
            h.step(20_000);

            float wa = sprocket.getAngularVelocity(null).x;
            float wb = roadWheel.getAngularVelocity(null).x;
            assertTrue(Float.isFinite(wa) && Float.isFinite(wb), "长跑后不应出现 NaN / Inf");
            assertEquals(0.5f, wb / wa, 0.05f, "长跑后传动比不应漂移");
        }
    }

    @Test
    void removeJointBeforeBodiesDoesNotCrash() {
        try (Harness h = new Harness()) {
            PhysicsRigidBody a = h.body(-2f, 0.5f, 10f);
            PhysicsRigidBody b = h.body(2f, 0.5f, 10f);
            GearJoint joint = new GearJoint(a, b,
                    new Vector3f(1, 0, 0), new Vector3f(1, 0, 0), -2f);
            h.space.addJoint(joint);
            h.step(10);

            joint.setEnabled(false);
            h.space.removeJoint(joint);
            assertFalse(joint.isEnabled(), "移除前已禁用");
            h.space.removeCollisionObject(b);
            h.space.removeCollisionObject(a);
            h.step(10);
        }
    }

    @Test
    void starTopologyWithSixRoadWheelsStaysFinite() {
        try (Harness h = new Harness()) {
            PhysicsRigidBody sprocket = h.body(-5f, 0.4f, 50f);
            sprocket.setAngularVelocity(new Vector3f(20f, 0f, 0f));
            for (int i = 0; i < 6; i++) {
                PhysicsRigidBody wheel = h.body(-3f + i * 1.2f, 0.5f, 40f);
                h.space.addJoint(new GearJoint(sprocket, wheel,
                        new Vector3f(1, 0, 0), new Vector3f(1, 0, 0), -1.25f));
            }
            h.step(5_000);
            assertTrue(Float.isFinite(sprocket.getAngularVelocity(null).x), "星形拓扑不应发散");
        }
    }

    /**
     * 游戏拓扑：车体 + 通过 New6Dof 悬挂的主动轮 / 负重轮，再叠加 GearJoint。
     *
     * <p>只为确认「GearJoint 与既有连接器关节共存」不会让求解器发散；真实悬挂参数
     * 由内容包决定，这里显式锁住除滚动轴以外的自由度。</p>
     */
    @Test
    void gearJointCoexistsWithConnectorSixDof() {
        try (Harness h = new Harness()) {
            PhysicsRigidBody chassis = h.body(0f, 1.0f, 0f); // 静态车体
            PhysicsRigidBody sprocket = h.body(-2f, 0.4f, 50f);
            PhysicsRigidBody roadWheel = h.body(1f, 0.5f, 40f);
            h.space.addJoint(freeRollingJoint(chassis, sprocket, -2f));
            h.space.addJoint(freeRollingJoint(chassis, roadWheel, 1f));
            h.space.addJoint(new GearJoint(sprocket, roadWheel,
                    new Vector3f(1, 0, 0), new Vector3f(1, 0, 0), -1.25f));

            sprocket.setAngularVelocity(new Vector3f(20f, 0f, 0f));
            h.step(2_000);
            float wa = sprocket.getAngularVelocity(null).x;
            float wb = roadWheel.getAngularVelocity(null).x;
            // 这里只验证「GearJoint 与连接器关节共存时不崩、不发散」；精确传动比由
            // 无连接器的 ratio 测试和游戏内阶段 1 覆盖（静态车体 + 纯角速度耦合的
            // 解析关系不等价于真实悬挂拓扑）。
            assertTrue(Float.isFinite(wa) && Float.isFinite(wb), "连接器 + GearJoint 不应发散");
        }
    }

    private static New6Dof freeRollingJoint(PhysicsRigidBody chassis, PhysicsRigidBody wheel, float pivotX) {
        New6Dof joint = new New6Dof(chassis, wheel,
                new Vector3f(pivotX, 0f, 0f), Vector3f.ZERO,
                new Matrix3f(), new Matrix3f(), RotationOrder.XYZ);
        // Spring2 默认各轴自由；显式锁死平移与偏航/侧倾，只放行滚动轴（dof 3）
        for (int i = 0; i < 6; i++) {
            joint.set(MotorParam.LowerLimit, i, 0f);
            joint.set(MotorParam.UpperLimit, i, 0f);
        }
        // 连接器滚转轴放行；其余转动轴锁死，模拟车轮连接器
        joint.set(MotorParam.LowerLimit, 3, 1f);
        joint.set(MotorParam.UpperLimit, 3, -1f);
        return joint;
    }

    /** 一个独立物理空间 + 一组无接触、零重力的测试刚体。 */
    private final class Harness implements AutoCloseable {
        final PhysicsSpace space;

        Harness() {
            space = new PhysicsSpace(
                    new Vector3f(-1000f, -1000f, -1000f),
                    new Vector3f(1000f, 1000f, 1000f),
                    PhysicsSpace.BroadphaseType.DBVT,
                    NativeLibrary.countThreads(),
                    new CollisionConfiguration(8192, 1));
            space.setGravity(Vector3f.ZERO);
            spaces.add(space);
        }

        PhysicsRigidBody body(float x, float halfExtent, float mass) {
            PhysicsRigidBody body = new PhysicsRigidBody(new BoxCollisionShape(halfExtent), mass);
            body.setPhysicsLocation(new Vector3f(x, 0f, 0f));
            space.addCollisionObject(body);
            return body;
        }

        void step(int count) {
            for (int i = 0; i < count; i++) space.update(STEP, 0);
        }

        @Override
        public void close() {
            // PhysicsSpace 没有 close；native 对象由 Spark-Core 的 Physics Cleaner 回收
        }
    }

    /** 从 Spark-Core 资源 / 同目录源码树加载 dpmt native（与游戏内同一求解器路径）。 */
    private static final class NativeLoader {
        private static boolean loaded;

        static synchronized void load() throws Exception {
            if (loaded) return;
            String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
            String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
            String folder;
            String lib;
            if (os.contains("win")) {
                folder = "windows";
                lib = "bulletjme.dll";
            } else if (os.contains("mac")) {
                folder = "macos";
                lib = "libbulletjme.dylib";
            } else {
                folder = "linux";
                lib = "libbulletjme.so";
            }
            String archDir = arch.contains("aarch64") || arch.contains("arm64") ? "arm64" : "x86_64";
            for (String variant : new String[]{"bullet_dpmt", "bullet_dp"}) {
                String resource = "natives/" + variant + "/" + folder + "/" + archDir + "/" + lib;
                try (InputStream in = GearJointPhysicsTest.class.getClassLoader().getResourceAsStream(resource)) {
                    if (in == null) continue;
                    Path temp = Files.createTempFile("bulletjme-", lib.substring(lib.lastIndexOf('.')));
                    Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
                    temp.toFile().deleteOnExit();
                    System.load(temp.toAbsolutePath().toString());
                    loaded = true;
                    return;
                }
            }
            // 复合构建下的开发目录兜底（不依赖 classpath 打包方式）
            for (String base : new String[]{"build/resources/main", "src/main/resources"}) {
                Path fallback = Path.of("..", "Spark-Core", base,
                        "natives", "bullet_dpmt", folder, archDir, lib);
                if (Files.isRegularFile(fallback)) {
                    System.load(fallback.toAbsolutePath().toString());
                    loaded = true;
                    return;
                }
            }
            throw new IllegalStateException("找不到 bulletjme native；检查 Spark-Core 资源是否在测试 classpath 上");
        }
    }
}
