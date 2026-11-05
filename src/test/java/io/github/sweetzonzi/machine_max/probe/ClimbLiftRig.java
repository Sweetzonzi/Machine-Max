package io.github.sweetzonzi.machine_max.probe;

import com.jme3.bullet.CollisionConfiguration;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.RotationOrder;
import com.jme3.bullet.SolverInfo;
import com.jme3.bullet.SolverMode;
import com.jme3.bullet.StepFlag;
import com.jme3.bullet.collision.ManifoldPoints;
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

/**
 * 台阶抬升系列的共用试具：承载面、立面、以及「轮 + 悬挂车体」三种刚体布局。
 *
 * <p>求解器用 bulletjme 默认设置（与 {@code PhysicsWorld} 一致：只改 solver mode，不碰
 * {@code SolverInfo}）：{@code splitImpulse=true}、阈值 {@code -0.04}、{@code erp=0.2}、
 * {@code iterations=10}。这三个数字决定了「侵入深度 → 竖直速度」这条映射在 4 cm 处换性质。</p>
 *
 * <p>探针与 {@code ClimbLiftContactTest} 共用本类；改动这里的参数等于改动那条测试的口径。</p>
 */
public final class ClimbLiftRig {

    public static final float RADIUS = 0.3f;
    public static final float MASS = 45f;
    public static final float DT = 0.01f;
    public static final float OVERLAP = 0.01f;
    public static final float LIFT = 0.03f;

    private ClimbLiftRig() {
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

    /** 单个静止盒（顶面 y=0）+ 球，球心高度 = 半径 - 侵入深度。 */
    public static final class SupportRig implements AutoCloseable {
        public final PhysicsSpace space;
        public final PhysicsRigidBody ground;
        public final PhysicsRigidBody ball;

        public SupportRig(float penetration) {
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

        public float advance(int steps) {
            for (int i = 0; i < steps; i++) space.update(DT, 0, StepFlag.contactProcessed);
            return ball.getLinearVelocity(null).y;
        }

        public float bottom() {
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
    public static final class FaceRig implements AutoCloseable {
        public final PhysicsSpace space;
        public final PhysicsRigidBody lower;
        public final PhysicsRigidBody upper;
        public final PhysicsRigidBody ball;
        public final boolean rewriteNormal;
        public final float depth;
        public final boolean bottomPoint;
        public int rewritten;
        public int rewritesThisStep;
        public boolean liveThisStep;
        public float press;

        public FaceRig(boolean rewriteNormal, float depth, float overlap, float clearance) {
            this(rewriteNormal, depth, overlap, clearance, false);
        }

        public FaceRig(boolean rewriteNormal, float depth, float overlap, float clearance, boolean bottomPoint) {
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

        /** 只改写「球 vs 上层、法线水平、有侵入」的接触；承载面与其余接触一律不碰。 */
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
                // 轮底：球心正下方一个半径，竖直法线在这里没有力臂
                Vector3f point = ball.getPhysicsLocation(null);
                point.y -= RADIUS;
                ManifoldPoints.setPositionWorldOnA(id, point);
                ManifoldPoints.setPositionWorldOnB(id, point);
            }
            ManifoldPoints.setCombinedRestitution(id, 0f);
        }

        public float advance(int steps) {
            for (int i = 0; i < steps; i++) {
                rewritesThisStep = 0;
                liveThisStep = false;
                if (press != 0f) ball.applyCentralForce(new Vector3f(press, 0f, 0f));
                space.update(DT, 0, StepFlag.contactProcessed);
            }
            return ball.getLinearVelocity(null).y;
        }

        public float bottom() {
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

    /**
     * 两级台阶 + 轮子 +（可选）车体。车体与轮子之间用 {@link New6Dof} 连接，参数取
     * {@code Machine-Max_Civilian} 的 jeep 悬挂：y 轴 k=55000、c=2500、行程 [-0.5, 1.0]，
     * x/z 平动锁定，滚动轴（z 转动）自由。车体 {@code angularFactor} 归零、不与任何物体碰撞，
     * 因此车体既不撞地形也不会自己转，竖直方向只能被悬挂推着走。
     *
     * @param mode 0 = 不改写接触，1 = 球心侧面，2 = 轮底，3 = 轮底并复刻承载深度钳位
     */
    public static final class LoadRig implements AutoCloseable {
        public static final float BODY_MASS = 1500f;
        public static final float BODY_HALF_X = 0.75f;
        public static final float BODY_HALF_Y = 0.3f;
        public static final float BODY_HALF_Z = 0.9f;
        public static final float SEPARATION = 0.9f;
        public static final float K = 55000f;
        public static final float C = 2500f;

        public final PhysicsSpace space;
        public final PhysicsRigidBody lower;
        public final PhysicsRigidBody upper;
        public final PhysicsRigidBody wheel;
        public final PhysicsRigidBody body;
        public final New6Dof joint;
        public final int mode;
        public final float depth = LIFT;
        public final float k;
        public final float yLower;
        public final float yUpper;
        public float downforce;
        public float press;
        public float overlap;
        public int rewritten;
        public boolean liveThisStep;
        public boolean cappedThisStep;
        public int rawPoints;
        public float rawDistance = Float.NaN;
        public final Vector3f rawNormal = new Vector3f();

        public LoadRig(int mode, boolean withBody, float overlap, float groundClearance) {
            this(mode, withBody, overlap, groundClearance, K, -0.5f, 1.0f);
        }

        public LoadRig(int mode, boolean withBody, float overlap, float groundClearance, float k) {
            this(mode, withBody, overlap, groundClearance, k, -0.5f, 1.0f);
        }

        /**
         * @param overlap 初始立面重叠（负值 = 离立面还有这么大间隙）
         * @param k       悬挂 y 轴刚度
         * @param yLower  y 轴行程下限
         * @param yUpper  y 轴行程上限
         */
        public LoadRig(int mode, boolean withBody, float overlap, float groundClearance,
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
                // 承载接触：mode 3 复刻 CollisionHandler 的抬升垫上限，只有深过一个垫的才钳
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

        public void settle(int steps) {
            advance(steps);
        }

        /** 把轮子（和车体）一起沿 x 平移，使立面重叠变成 {@code newOverlap}。 */
        public void engage(float newOverlap) {
            float dx = newOverlap - overlap;
            Vector3f p = wheel.getPhysicsLocation(null);
            wheel.setPhysicsLocation(new Vector3f(p.x + dx, p.y, p.z));
            if (body != null) {
                Vector3f b = body.getPhysicsLocation(null);
                body.setPhysicsLocation(new Vector3f(b.x + dx, b.y, b.z));
            }
            overlap = newOverlap;
        }

        public void advance(int steps) {
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

        public float bottom() {
            return wheel.getPhysicsLocation(null).y - RADIUS;
        }

        /** 车体到轮心的竖直距离；没有车体时返回 0。 */
        public float suspensionLength() {
            if (body == null) return 0f;
            return body.getPhysicsLocation(null).y - wheel.getPhysicsLocation(null).y;
        }

        /** 弹簧压缩量按 k×压缩量 折算的力；没有车体时返回 0。 */
        public float springForce() {
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
}
