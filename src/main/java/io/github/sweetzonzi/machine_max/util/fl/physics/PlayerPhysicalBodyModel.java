package io.github.sweetzonzi.machine_max.util.fl.physics;

import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

/**
 * todo 也许未来还可以用于乘坐载具时，转弯、轰油门、刹车、滚转、过载机动时视角的模拟
 *
 * 玩家物理身体模型 — 管理两个物理化的身体部位（玩家身体、玩家头部），
 * 通过弹性颈部连接。物理参数通过 {@link PlayerPhysicalBodyBuilder} 配置。
 * <p>
 * 颈部特性：
 * <ul>
 *   <li><b>上下自由度很大</b> — 模拟人类头部不易被生生扯断（暂不会断开连接）</li>
 *   <li><b>球面旋转自由</b> — 绕脖子有一定自由度的球面旋转 (Yaw/Pitch/Roll)</li>
 *   <li><b>冲击韧性</b> — 受到较大冲击力时头部会朝冲量方向产生弹性形变转动</li>
 * </ul>
 * <p>
 * 物理结构：
 * <pre>
 *   ┌──────────┐     颈部旋转弹簧       ┌──────────┐
 *   │  头部    │◄──────────────────────►│  身体    │
 *   │ 5kg      │  各向异性线性弹簧       │ 60kg     │
 *   └──────────┘  (Y软 XZ硬)            └──────────┘
 * </pre>
 * <p>
 * 使用方法：
 * <pre>
 * PlayerPhysicalBodyModel model = new PlayerPhysicalBodyBuilder()
 *     .withBodyMass(60.0)
 *     .withHeadMass(5.0)
 *     .build();
 * </pre>
 */
public class PlayerPhysicalBodyModel implements PhysicalObject {

    // ============================================================
    //  物理模型（由 Builder 在 build() 中创建并赋值）
    // ============================================================

    QuickPhysicsModel<PlayerPhysicalBodyModel> bodyModel;
    QuickPhysicsModel<PlayerPhysicalBodyModel> headModel;

    // ============================================================
    //  颈部附着点（局部坐标，相对于各自身体部位中心）
    // ============================================================

    Vec3 bodyNeckLocal = new Vec3(0, 0.85, 0);
    Vec3 headNeckLocal = new Vec3(0, -0.25, 0);

    // ============================================================
    //  颈部线性弹性参数
    // ============================================================

    double neckRestLength = 0.05;
    double neckVerticalSpringK = 600.0;
    double neckHorizontalSpringK = 2000.0;
    double neckLinearDamping = 40.0;

    // ============================================================
    //  颈部旋转弹性参数（球面旋转）
    // ============================================================

    float neckRotSpringK = 30.0f;
    float neckRotDamping = 10.0f;
    float maxNeckYaw = 75.0f;
    float maxNeckPitch = 55.0f;
    float maxNeckRoll = 40.0f;

    // ============================================================
    //  冲击响应参数
    // ============================================================

    float impactRotationScale = 0.20f;
    float impactMaxAngularVelocity = 250.0f;

    // ============================================================
    //  头部归零 PID 控制器（消除颈部弹簧对抗导致的稳态误差）
    // ============================================================

    float headPidKp = 10.0f;
    float headPidKi = 4.0f;
    float headPidKd = 6.0f;
    float headPidIntegralMax = 500.0f;
    float headPidOutputMax = 500.0f;

    // ============================================================
    //  身体归零 PID 控制器（中弹后恢复身体姿态）
    // ============================================================

    /** 身体 PID 比例系数 — 较头部弱，允许身体有更大的自然晃动空间 */
    float bodyPidKp = 8.0f;
    /** 身体 PID 积分系数 — 消除稳态误差 */
    float bodyPidKi = 12.0f;
    /** 身体 PID 微分系数 — 阻尼振荡 */
    float bodyPidKd = 6.0f;
    /** 身体 PID 积分上限 */
    float bodyPidIntegralMax = 300.0f;
    /** 身体 PID 输出上限 */
    float bodyPidOutputMax = 200.0f;

    // ============================================================
    //  运行时状态（私有）
    // ============================================================

    private boolean disabled = false;

    private float relAngVelYaw;
    private float relAngVelPitch;
    private float relAngVelRoll;

    private float headPidIntegralYaw;
    private float headPidIntegralPitch;
    private float headPidIntegralRoll;

    private float bodyPidIntegralYaw;
    private float bodyPidIntegralPitch;
    private float bodyPidIntegralRoll;

    // ============================================================
    //  构造 — 包级私有，由 {@link PlayerPhysicalBodyBuilder#build()} 创建
    // ============================================================

    public PlayerPhysicalBodyModel() {}

    // ============================================================
    //  公开访问器 — 返回底层模型，供外部直接操作
    // ============================================================

    public QuickPhysicsModel<PlayerPhysicalBodyModel> getBodyModel() { return bodyModel; }
    public QuickPhysicsModel<PlayerPhysicalBodyModel> getHeadModel() { return headModel; }

    public boolean isDisabled() { return disabled; }
    public void setDisabled(boolean disabled) { this.disabled = disabled; }

    // ============================================================
    //  位置/速度快捷访问
    // ============================================================

    public Vec3 getBodyPosition() { return bodyModel.getPosition(); }
    public void setBodyPosition(Vec3 pos) { bodyModel.setPosition(pos); }

    public Vec3 getHeadPosition() { return headModel.getPosition(); }
    public void setHeadPosition(Vec3 pos) { headModel.setPosition(pos); }

    public Vec3 getBodyVelocity() { return bodyModel.getVelocity(); }
    public Vec3 getHeadVelocity() { return headModel.getVelocity(); }

    public float getBodyYaw()   { return bodyModel.getCurrentYaw(); }
    public float getBodyPitch() { return bodyModel.getCurrentPitch(); }
    public float getBodyRoll()  { return bodyModel.getCurrentRoll(); }

    public float getHeadYaw()   { return headModel.getCurrentYaw(); }
    public float getHeadPitch() { return headModel.getCurrentPitch(); }
    public float getHeadRoll()  { return headModel.getCurrentRoll(); }

    // ============================================================
    //  力学快捷访问
    // ============================================================

    public void addForceToBody(Vec3 force) { bodyModel.addForce(force); }
    public void addForceToHead(Vec3 force) { headModel.addForce(force); }

    public void applyTorqueToBody(float yaw, float pitch, float roll) {
        bodyModel.applyTorque(yaw, pitch, roll);
    }

    public void applyTorqueToHead(float yaw, float pitch, float roll) {
        headModel.applyTorque(yaw, pitch, roll);
    }

    // ============================================================
    //  冲击 API
    // ============================================================

    /**
     * 对头部施加冲击力（在指定世界坐标点）
     *
     * @param force      冲击力向量
     * @param worldPoint 世界空间中的受力点
     */
    public void applyImpactToHead(Vec3 force, Vec3 worldPoint) {
        resetHeadPid();
        headModel.addForce(force);

        Vec3 r = worldPoint.subtract(headModel.getPosition());
        float tYaw   = (float) (r.y * force.z - r.z * force.y) * impactRotationScale;
        float tPitch = (float) (r.z * force.x - r.x * force.z) * impactRotationScale;
        float tRoll  = (float) (r.x * force.y - r.y * force.x) * impactRotationScale;

        tYaw   = Mth.clamp(tYaw,   -impactMaxAngularVelocity, impactMaxAngularVelocity);
        tPitch = Mth.clamp(tPitch, -impactMaxAngularVelocity, impactMaxAngularVelocity);
        tRoll  = Mth.clamp(tRoll,  -impactMaxAngularVelocity, impactMaxAngularVelocity);

        headModel.applyTorque(tYaw, tPitch, tRoll);
    }

    /**
     * 对身体施加冲击力（在指定世界坐标点）
     */
    public void applyImpactToBody(Vec3 force, Vec3 worldPoint) {
        resetBodyPid();
        bodyModel.addForce(force);

        Vec3 r = worldPoint.subtract(bodyModel.getPosition());
        float tYaw   = (float) (r.y * force.z - r.z * force.y) * 0.02f;
        float tPitch = (float) (r.z * force.x - r.x * force.z) * 0.02f;
        float tRoll  = (float) (r.x * force.y - r.y * force.x) * 0.02f;

        bodyModel.applyTorque(tYaw, tPitch, tRoll);
    }

    /**
     * 对整个身体系统施加统一的冲击力
     */
    public void applyUniformImpact(Vec3 force) {
        bodyModel.addForce(force.scale(0.9));
        headModel.addForce(force);
    }

    // ============================================================
    //  PID 归零积分重置
    // ============================================================

    /**
     * 重置头部 PID 积分项（每次冲击命中后调用，使积分从零重新开始）
     */
    public void resetHeadPid() {
        headPidIntegralYaw = 0;
        headPidIntegralPitch = 0;
        headPidIntegralRoll = 0;
    }

    /**
     * 重置身体 PID 积分项（身体中弹后调用）
     */
    public void resetBodyPid() {
        bodyPidIntegralYaw = 0;
        bodyPidIntegralPitch = 0;
        bodyPidIntegralRoll = 0;
    }

    // ============================================================
    //  工具
    // ============================================================

    private static Vec3 rotateVec3ByQuaternion(Vec3 vec, Quaternionf quat) {
        float x = (float) vec.x, y = (float) vec.y, z = (float) vec.z;
        float qx = quat.x, qy = quat.y, qz = quat.z, qw = quat.w;
        float ix = qw * x + qy * z - qz * y;
        float iy = qw * y + qz * x - qx * z;
        float iz = qw * z + qx * y - qy * x;
        float iw = -qx * x - qy * y - qz * z;
        return new Vec3(
            ix * qw + iw * -qx + iy * -qz - iz * -qy,
            iy * qw + iw * -qy + iz * -qx - ix * -qz,
            iz * qw + iw * -qz + ix * -qy - iy * -qx
        );
    }

    // ============================================================
    //  物理步进
    // ============================================================

    /**
     * 每帧调用，驱动物理模拟。
     * <p>
     * 物理顺序：
     * <ol>
     *   <li>计算颈部附着点世界坐标</li>
     *   <li>各向异性线性弹簧（Y轴软、XZ轴硬）</li>
     *   <li>偏置扭矩（τ = r × F，弹簧力不在 CoM 上）</li>
     *   <li>颈部旋转弹簧/阻尼（球面旋转约束）</li>
     *   <li>将力和扭矩分别应用到身体和头部</li>
     *   <li>Tick 两个身体部位的 QuickPhysicsModel</li>
     * </ol>
     *
     * @param tickDelta 渲染帧间隔
     */
    public void physicsTick(float tickDelta) {
        if (disabled) return;

        // ---- 1. 计算颈部附着点世界坐标 ----
        Vec3 bodyNeckWorld = bodyModel.getPosition().add(
            rotateVec3ByQuaternion(bodyNeckLocal, bodyModel.getRotation())
        );
        Vec3 headNeckWorld = headModel.getPosition().add(
            rotateVec3ByQuaternion(headNeckLocal, headModel.getRotation())
        );

        // ---- 2. 各向异性线性弹簧 ----
        Vec3 displacement = headNeckWorld.subtract(bodyNeckWorld);
        double currentLength = displacement.length();
        if (currentLength < 1e-10) {
            bodyModel.physicsTick(tickDelta);
            headModel.physicsTick(tickDelta);
            return;
        }

        double stretchY = displacement.y - neckRestLength;
        Vec3 horizontalDisp = new Vec3(displacement.x, 0, displacement.z);
        double horizontalDist = horizontalDisp.length();

        double forceY = -neckVerticalSpringK * stretchY;

        Vec3 horizontalForce = Vec3.ZERO;
        if (horizontalDist > 0.001) {
            Vec3 horizontalDir = horizontalDisp.scale(1.0 / horizontalDist);
            double fXZ = -neckHorizontalSpringK * horizontalDist;
            horizontalForce = horizontalDir.scale(fXZ);
        }

        Vec3 totalSpringForce = new Vec3(horizontalForce.x, forceY, horizontalForce.z);

        Vec3 relVel = headModel.getVelocity().subtract(bodyModel.getVelocity());
        Vec3 neckDirection = displacement.scale(1.0 / currentLength);
        double dampMagnitude = neckLinearDamping * relVel.dot(neckDirection);
        Vec3 dampingForce = neckDirection.scale(-dampMagnitude);

        Vec3 neckForce = totalSpringForce.add(dampingForce);

        // ---- 3. 偏置扭矩（弹簧力作用在颈部附着点，而非 CoM） ----
        Vec3 rHead = headNeckWorld.subtract(headModel.getPosition());
        Vec3 rBody = bodyNeckWorld.subtract(bodyModel.getPosition());

        float headTorqueYaw   = (float) (rHead.y * neckForce.z - rHead.z * neckForce.y);
        float headTorquePitch = (float) (rHead.z * neckForce.x - rHead.x * neckForce.z);
        float headTorqueRoll  = (float) (rHead.x * neckForce.y - rHead.y * neckForce.x);

        Vec3 reactionForce = neckForce.scale(-1);
        float bodyTorqueYaw   = (float) (rBody.y * reactionForce.z - rBody.z * reactionForce.y);
        float bodyTorquePitch = (float) (rBody.z * reactionForce.x - rBody.x * reactionForce.z);
        float bodyTorqueRoll  = (float) (rBody.x * reactionForce.y - rBody.y * reactionForce.x);

        // ---- 4. 颈部旋转弹簧/阻尼（球面旋转） ----
        float relYaw = headModel.getCurrentYaw() - bodyModel.getCurrentYaw();
        float relPitch = headModel.getCurrentPitch() - bodyModel.getCurrentPitch();
        float relRoll = headModel.getCurrentRoll() - bodyModel.getCurrentRoll();

        relYaw = normalizeAngle(relYaw);
        relPitch = normalizeAngle(relPitch);
        relRoll = normalizeAngle(relRoll);

        relYaw   = Mth.clamp(relYaw,   -maxNeckYaw,   maxNeckYaw);
        relPitch = Mth.clamp(relPitch, -maxNeckPitch, maxNeckPitch);
        relRoll  = Mth.clamp(relRoll,  -maxNeckRoll,  maxNeckRoll);

        Vec3 headAngVel = headModel.getAngularVelocity();
        Vec3 bodyAngVel = bodyModel.getAngularVelocity();

        this.relAngVelYaw   = (float) (headAngVel.x - bodyAngVel.x);
        this.relAngVelPitch = (float) (headAngVel.y - bodyAngVel.y);
        this.relAngVelRoll  = (float) (headAngVel.z - bodyAngVel.z);

        float neckTorqueYaw   = -neckRotSpringK * relYaw   - neckRotDamping * relAngVelYaw;
        float neckTorquePitch = -neckRotSpringK * relPitch - neckRotDamping * relAngVelPitch;
        float neckTorqueRoll  = -neckRotSpringK * relRoll  - neckRotDamping * relAngVelRoll;

        // ---- 4.5 头部姿态归零 PID 控制器（身体相对坐标） ----
        // 误差使用身体相对旋转角，与颈部弹簧同一参考系，避免两控制器对抗振荡。
        // 积分项温和消除残余稳态误差，归零后头部自然跟随身体转动。
        float errYaw   = normalizeAngle(headModel.getCurrentYaw()   - bodyModel.getCurrentYaw());
        float errPitch = normalizeAngle(headModel.getCurrentPitch() - bodyModel.getCurrentPitch());
        float errRoll  = normalizeAngle(headModel.getCurrentRoll()  - bodyModel.getCurrentRoll());

        headPidIntegralYaw   += errYaw   * tickDelta;
        headPidIntegralPitch += errPitch * tickDelta;
        headPidIntegralRoll  += errRoll  * tickDelta;
        headPidIntegralYaw   = Mth.clamp(headPidIntegralYaw,   -headPidIntegralMax, headPidIntegralMax);
        headPidIntegralPitch = Mth.clamp(headPidIntegralPitch, -headPidIntegralMax, headPidIntegralMax);
        headPidIntegralRoll  = Mth.clamp(headPidIntegralRoll,  -headPidIntegralMax, headPidIntegralMax);

        // 微分使用相对角速度（与误差定义一致）
        float centerTorqueYaw   = -headPidKp * errYaw   - headPidKi * headPidIntegralYaw   - headPidKd * relAngVelYaw;
        float centerTorquePitch = -headPidKp * errPitch - headPidKi * headPidIntegralPitch - headPidKd * relAngVelPitch;
        float centerTorqueRoll  = -headPidKp * errRoll  - headPidKi * headPidIntegralRoll  - headPidKd * relAngVelRoll;
        centerTorqueYaw   = Mth.clamp(centerTorqueYaw,   -headPidOutputMax, headPidOutputMax);
        centerTorquePitch = Mth.clamp(centerTorquePitch, -headPidOutputMax, headPidOutputMax);
        centerTorqueRoll  = Mth.clamp(centerTorqueRoll,  -headPidOutputMax, headPidOutputMax);

        // ---- 4.6 身体姿态归零 PID 控制器（中弹后身体恢复初始旋转） ----
        float bodyErrYaw   = bodyModel.getCurrentYaw();
        float bodyErrPitch = bodyModel.getCurrentPitch();
        float bodyErrRoll  = bodyModel.getCurrentRoll();

        bodyPidIntegralYaw   += bodyErrYaw   * tickDelta;
        bodyPidIntegralPitch += bodyErrPitch * tickDelta;
        bodyPidIntegralRoll  += bodyErrRoll  * tickDelta;
        bodyPidIntegralYaw   = Mth.clamp(bodyPidIntegralYaw,   -bodyPidIntegralMax, bodyPidIntegralMax);
        bodyPidIntegralPitch = Mth.clamp(bodyPidIntegralPitch, -bodyPidIntegralMax, bodyPidIntegralMax);
        bodyPidIntegralRoll  = Mth.clamp(bodyPidIntegralRoll,  -bodyPidIntegralMax, bodyPidIntegralMax);

        float bodyCenterTorqueYaw   = -bodyPidKp * bodyErrYaw   - bodyPidKi * bodyPidIntegralYaw   - bodyPidKd * (float) bodyAngVel.x;
        float bodyCenterTorquePitch = -bodyPidKp * bodyErrPitch - bodyPidKi * bodyPidIntegralPitch - bodyPidKd * (float) bodyAngVel.y;
        float bodyCenterTorqueRoll  = -bodyPidKp * bodyErrRoll  - bodyPidKi * bodyPidIntegralRoll  - bodyPidKd * (float) bodyAngVel.z;
        bodyCenterTorqueYaw   = Mth.clamp(bodyCenterTorqueYaw,   -bodyPidOutputMax, bodyPidOutputMax);
        bodyCenterTorquePitch = Mth.clamp(bodyCenterTorquePitch, -bodyPidOutputMax, bodyPidOutputMax);
        bodyCenterTorqueRoll  = Mth.clamp(bodyCenterTorqueRoll,  -bodyPidOutputMax, bodyPidOutputMax);

        // ---- 5. 应用力和扭矩 ----
        bodyModel.addForce(reactionForce);
        headModel.addForce(neckForce);

        bodyModel.applyTorque(
            bodyTorqueYaw   - neckTorqueYaw   * 0.3f   + bodyCenterTorqueYaw,
            bodyTorquePitch - neckTorquePitch * 0.3f   + bodyCenterTorquePitch,
            bodyTorqueRoll  - neckTorqueRoll  * 0.3f   + bodyCenterTorqueRoll
        );
        headModel.applyTorque(
            headTorqueYaw   + neckTorqueYaw   + centerTorqueYaw,
            headTorquePitch + neckTorquePitch + centerTorquePitch,
            headTorqueRoll  + neckTorqueRoll  + centerTorqueRoll
        );

        // ---- 6. Tick 两个身体部位 ----
        bodyModel.physicsTick(tickDelta);
        headModel.physicsTick(tickDelta);
    }

    /**
     * 将角度归一化到 [-180, 180] 范围
     */
    private static float normalizeAngle(float angle) {
        while (angle > 180f) angle -= 360f;
        while (angle < -180f) angle += 360f;
        return angle;
    }
}
