package io.github.sweetzonzi.machine_max.util;

import com.jme3.bullet.CollisionConfiguration;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.bullet.util.NativeLibrary;
import com.jme3.math.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 将分配指令逐器件代回线性推力函数并作用于裸 Bullet 刚体。
 * 不启动 Minecraft，核对平移、力臂力矩与 64 喷口悬停／失效；不宣称已验收完整飞控。
 */
class WrenchAllocatorPhysicsTest {
    private static final float STEP = .01f;
    private static final float MASS = 10;
    private static final WrenchAllocator.Settings SETTINGS = new WrenchAllocator.Settings(1e-8, 1e-6);
    private PhysicsSpace space;
    private PhysicsRigidBody body;

    /** 从 Spark-Core 的资源直接加载引擎；其它测试已加载时复用同一 native。 */
    @BeforeAll
    static void loadNative() throws Exception {
        try {
            NativeLibrary.versionNumber();
            return;
        } catch (UnsatisfiedLinkError unloaded) {
            // 独立执行此测试时没有模组加载器，按当前系统选择实际引擎库。
        }
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        String folder = os.contains("win") ? "windows" : os.contains("mac") ? "macos" : "linux";
        String library = os.contains("win") ? "bulletjme.dll" : os.contains("mac") ? "libbulletjme.dylib" : "libbulletjme.so";
        String architecture = arch.contains("aarch64") || arch.contains("arm64") ? "arm64" : "x86_64";
        for (String variant : new String[]{"bullet_dpmt", "bullet_dp"}) {
            String resource = "natives/" + variant + "/" + folder + "/" + architecture + "/" + library;
            try (InputStream input = WrenchAllocatorPhysicsTest.class.getClassLoader().getResourceAsStream(resource)) {
                if (input == null) continue;
                Path temporary = Files.createTempFile("wrench-bullet-", library.substring(library.lastIndexOf('.')));
                Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
                temporary.toFile().deleteOnExit();
                System.load(temporary.toAbsolutePath().toString());
                return;
            }
        }
        throw new IllegalStateException("找不到 Spark-Core 的 Bullet 双精度 native 资源");
    }

    /** 先从碰撞空间摘下刚体，再释放测试引用，由引擎的 Cleaner 回收 native 对象。 */
    @AfterEach
    void detachBody() {
        if (space != null && body != null) space.removeCollisionObject(body);
        body = null;
        space = null;
    }

    /** 对称喷口的力矩相消，线速度应按实际出力而不是期望出力积分。 */
    @Test
    void symmetricThrustersTranslateWithoutInventingTorque() {
        createBody(Vector3f.ZERO);
        Vector3f[] points = {new Vector3f(1, 0, 0), new Vector3f(-1, 0, 0)};
        var workspace = thrusters(points, 100, new double[]{0, 100, 0, 0, 0, 0}, -1, 1);
        var result = workspace.solve(SETTINGS);
        assertEquals(WrenchAllocator.Status.SOLVED, result.status());
        for (int step = 0; step < 100; step++) {
            applyThrusters(points, 100, result);
            space.update(STEP, 0);
        }
        Vector3f velocity = body.getLinearVelocity(null);
        assertEquals(result.achieved(1) / MASS, velocity.y, .002);
        assertEquals(0, velocity.x, 1e-5);
        assertEquals(0, velocity.z, 1e-5);
        assertEquals(0, body.getAngularVelocity(null).length(), 1e-5);
    }

    /** 反向器件组合为纯力偶，核对 r×F 正负号以及惯量积分。 */
    @Test
    void opposingCommandsProduceTheExpectedAngularImpulse() {
        createBody(Vector3f.ZERO);
        Vector3f[] points = {new Vector3f(1, 0, 0), new Vector3f(-1, 0, 0)};
        var result = thrusters(points, 100, new double[]{0, 0, 0, 0, 0, 20}, -1, 1).solve(SETTINGS);
        assertTrue(result.control(0) > 0 && result.control(1) < 0);
        double expected = body.getInverseInertiaLocal(null).z * result.achieved(5) * STEP;
        applyThrusters(points, 100, result);
        space.update(STEP, 0);
        assertEquals(expected, body.getAngularVelocity(null).z, 1e-5);
        assertEquals(0, body.getLinearVelocity(null).length(), 1e-5);
    }

    /** 64 个单侧喷口共同顶重力，全部失效后刚体应自由下落而非被隐式补力。 */
    @Test
    void sixtyFourThrustersHoverAndLossOfAuthorityFallsUnderGravity() {
        createBody(new Vector3f(0, -9.81f, 0));
        Vector3f[] points = new Vector3f[64];
        for (int index = 0; index < points.length; index++) {
            double angle = 2 * Math.PI * index / points.length;
            points[index] = new Vector3f((float) Math.cos(angle), 0, (float) Math.sin(angle));
        }
        var workspace = thrusters(points, 20, new double[]{0, MASS * 9.81, 0, 0, 0, 0}, 0, 1);
        var result = workspace.solve(SETTINGS);
        for (int step = 0; step < 200; step++) {
            applyThrusters(points, 20, result);
            space.update(STEP, 0);
        }
        assertEquals(0, body.getLinearVelocity(null).y, .005);
        assertEquals(0, body.getPhysicsLocation(null).y, .01);
        assertEquals(0, body.getAngularVelocity(null).length(), .001);

        // 故障只改变器件权限；没有任何额外的目标力或速度赋值掩盖失效。
        for (int index = 0; index < points.length; index++) workspace.disable(index);
        var failed = workspace.solve(SETTINGS);
        assertEquals(WrenchAllocator.Status.NO_AUTHORITY, failed.status());
        assertEquals(MASS * 9.81, failed.residual(1), 1e-10);
        float initialY = body.getLinearVelocity(null).y;
        for (int step = 0; step < 10; step++) {
            applyThrusters(points, 20, failed);
            space.update(STEP, 0);
        }
        assertEquals(initialY - .981, body.getLinearVelocity(null).y, .002);
    }

    /** 保留同一上下文，喷口转向后让裸刚体沿新方向加速，已有动量交给引擎保留。 */
    @Test
    void movingNozzlesUpdateTheSameWorkspaceBetweenPhysicsSteps() {
        createBody(Vector3f.ZERO);
        var workspace = new WrenchAllocator.Workspace(2);
        Vector3f right = new Vector3f(1, 0, 0);
        Vector3f left = new Vector3f(-1, 0, 0);
        Vector3f force = new Vector3f();
        for (int step = 0; step < 100; step++) {
            boolean turned = step >= 50;
            double fy = turned ? 0 : 100;
            double fz = turned ? 100 : 0;
            workspace.forceAtOffset(0, 0, fy, fz, 1, 0, 0)
                    .forceAtOffset(1, 0, fy, fz, -1, 0, 0)
                    .target(0, fy, fz, 0, 0, 0);
            var output = workspace.solve(SETTINGS);
            force.set(0, (float) (fy * output.control(0)), (float) (fz * output.control(0)));
            body.applyForce(force, right);
            force.set(0, (float) (fy * output.control(1)), (float) (fz * output.control(1)));
            body.applyForce(force, left);
            space.update(STEP, 0);
        }
        assertEquals(5, body.getLinearVelocity(null).y, .002);
        assertEquals(5, body.getLinearVelocity(null).z, .002);
        assertEquals(0, body.getAngularVelocity(null).length(), 1e-5);
    }

    /** 建立无接触、无阻尼的动态立方体，将外力积分与其它机制隔离。 */
    private void createBody(Vector3f gravity) {
        space = new PhysicsSpace(new Vector3f(-1000, -1000, -1000), new Vector3f(1000, 1000, 1000),
                PhysicsSpace.BroadphaseType.DBVT, NativeLibrary.countThreads(), new CollisionConfiguration(8192, 1));
        space.setGravity(gravity);
        body = new PhysicsRigidBody(new BoxCollisionShape(.5f), MASS);
        body.setDamping(0, 0);
        body.setSleepingThresholds(0, 0);
        space.addCollisionObject(body);
    }

    /** 根据每个实际安装点组装六维效能列，推力函数为世界 Y 轴的线性力。 */
    private static WrenchAllocator.Workspace thrusters(Vector3f[] points, double fullForce, double[] target,
                                                    double lower, double upper) {
        var inputs = new WrenchAllocator.Workspace(points.length).target(target[0], target[1], target[2], target[3], target[4], target[5]);
        for (int index = 0; index < points.length; index++) {
            inputs.forceAtOffset(index, 0, fullForce, 0, points[index].x, points[index].y, points[index].z)
                    .limits(index, lower, upper);
        }
        for (int row = 0; row < 6; row++) inputs.rowScale(row, .01);
        return inputs;
    }

    /** 逐器件计算实际力，只施加这一条路径，由 Bullet 自动产生相应力矩。 */
    private void applyThrusters(Vector3f[] points, double fullForce, WrenchAllocator.Solution result) {
        for (int index = 0; index < points.length; index++) {
            body.applyForce(new Vector3f(0, (float) (fullForce * result.control(index)), 0), points[index]);
        }
    }
}
