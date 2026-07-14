package io.github.sweetzonzi.machine_max.util.fl.physics;

import net.minecraft.world.phys.Vec3;

/**
 * 玩家物理身体构建器 — 创建并配置 {@link PlayerPhysicalBodyModel} 的物理参数。
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
public class PlayerPhysicalBodyBuilder {

    // ============================================================
    //  质量/惯量参数
    // ============================================================

    private double bodyMass = 60.0;
    private double headMass = 5.0;
    private double bodyMomentOfInertia = 4.0f;
    private double headMomentOfInertia = 0.7f;

    // ============================================================
    //  初始位置
    // ============================================================

    private Vec3 bodyInitialPosition = new Vec3(0, 1.5, 0);
    private Vec3 headInitialPosition = new Vec3(0, 2.25, 0);

    // ============================================================
    //  阻力/阻尼
    // ============================================================

    private double bodyDragCoefficient = 0.05;
    private double headDragCoefficient = 0.02;
    private float bodyAngularDamping = 8.0f;
    private float headAngularDamping = 0.7f;

    // ============================================================
    //  颈部附着点（局部坐标，相对于各自身体部位中心）
    // ============================================================

    private Vec3 bodyNeckLocal = new Vec3(0, 0.85, 0);
    private Vec3 headNeckLocal = new Vec3(0, -0.25, 0);

    // ============================================================
    //  颈部线性弹性参数
    // ============================================================

    private double neckRestLength = 0.05;
    private double neckVerticalSpringK = 600.0;
    private double neckHorizontalSpringK = 2000.0;
    private double neckLinearDamping = 40.0;

    // ============================================================
    //  颈部旋转弹性参数（球面旋转）
    // ============================================================

    private float neckRotSpringK = 30.0f;
    private float neckRotDamping = 10.0f;
    private float maxNeckYaw = 75.0f;
    private float maxNeckPitch = 55.0f;
    private float maxNeckRoll = 40.0f;

    // ============================================================
    //  冲击响应参数
    // ============================================================

    private float impactRotationScale = 0.20f;
    private float impactMaxAngularVelocity = 250.0f;

    // ============================================================
    //  头部归零 PID 控制器参数
    // ============================================================

    private float headPidKp = 10.0f;
    private float headPidKi = 4.0f;
    private float headPidKd = 6.0f;
    private float headPidIntegralMax = 500.0f;
    private float headPidOutputMax = 500.0f;

    // ============================================================
    //  身体归零 PID 控制器参数
    // ============================================================

    private float bodyPidKp = 8.0f;
    private float bodyPidKi = 12.0f;
    private float bodyPidKd = 6.0f;
    private float bodyPidIntegralMax = 300.0f;
    private float bodyPidOutputMax = 200.0f;

    // ============================================================
    //  质量/惯量
    // ============================================================

    public PlayerPhysicalBodyBuilder withBodyMass(double bodyMass) {
        this.bodyMass = bodyMass;
        return this;
    }

    public PlayerPhysicalBodyBuilder withHeadMass(double headMass) {
        this.headMass = headMass;
        return this;
    }

    public PlayerPhysicalBodyBuilder withBodyMomentOfInertia(double moment) {
        this.bodyMomentOfInertia = moment;
        return this;
    }

    public PlayerPhysicalBodyBuilder withHeadMomentOfInertia(double moment) {
        this.headMomentOfInertia = moment;
        return this;
    }

    // ============================================================
    //  初始位置
    // ============================================================

    public PlayerPhysicalBodyBuilder withBodyInitialPosition(Vec3 pos) {
        this.bodyInitialPosition = pos;
        return this;
    }

    public PlayerPhysicalBodyBuilder withHeadInitialPosition(Vec3 pos) {
        this.headInitialPosition = pos;
        return this;
    }

    // ============================================================
    //  阻力/阻尼
    // ============================================================

    public PlayerPhysicalBodyBuilder withBodyDragCoefficient(double drag) {
        this.bodyDragCoefficient = drag;
        return this;
    }

    public PlayerPhysicalBodyBuilder withHeadDragCoefficient(double drag) {
        this.headDragCoefficient = drag;
        return this;
    }

    public PlayerPhysicalBodyBuilder withBodyAngularDamping(float damping) {
        this.bodyAngularDamping = damping;
        return this;
    }

    public PlayerPhysicalBodyBuilder withHeadAngularDamping(float damping) {
        this.headAngularDamping = damping;
        return this;
    }

    // ============================================================
    //  颈部附着点
    // ============================================================

    public PlayerPhysicalBodyBuilder withNeckAttachmentPoints(Vec3 bodyLocal, Vec3 headLocal) {
        this.bodyNeckLocal = bodyLocal;
        this.headNeckLocal = headLocal;
        return this;
    }

    // ============================================================
    //  颈部线性弹性参数
    // ============================================================

    public PlayerPhysicalBodyBuilder withNeckLinearParams(double restLength, double verticalK, double horizontalK, double damping) {
        this.neckRestLength = restLength;
        this.neckVerticalSpringK = verticalK;
        this.neckHorizontalSpringK = horizontalK;
        this.neckLinearDamping = damping;
        return this;
    }

    // ============================================================
    //  颈部旋转弹性参数
    // ============================================================

    public PlayerPhysicalBodyBuilder withNeckRotationalParams(float springK, float damping, float maxYaw, float maxPitch, float maxRoll) {
        this.neckRotSpringK = springK;
        this.neckRotDamping = damping;
        this.maxNeckYaw = maxYaw;
        this.maxNeckPitch = maxPitch;
        this.maxNeckRoll = maxRoll;
        return this;
    }

    // ============================================================
    //  冲击响应参数
    // ============================================================

    public PlayerPhysicalBodyBuilder withImpactRotationScale(float scale) {
        this.impactRotationScale = scale;
        return this;
    }

    public PlayerPhysicalBodyBuilder withImpactMaxAngularVelocity(float max) {
        this.impactMaxAngularVelocity = max;
        return this;
    }

    // ============================================================
    //  头部 PID 参数
    // ============================================================

    public PlayerPhysicalBodyBuilder withHeadPidParams(float kp, float ki, float kd, float integralMax, float outputMax) {
        this.headPidKp = kp;
        this.headPidKi = ki;
        this.headPidKd = kd;
        this.headPidIntegralMax = integralMax;
        this.headPidOutputMax = outputMax;
        return this;
    }

    // ============================================================
    //  身体 PID 参数
    // ============================================================

    public PlayerPhysicalBodyBuilder withBodyPidParams(float kp, float ki, float kd, float integralMax, float outputMax) {
        this.bodyPidKp = kp;
        this.bodyPidKi = ki;
        this.bodyPidKd = kd;
        this.bodyPidIntegralMax = integralMax;
        this.bodyPidOutputMax = outputMax;
        return this;
    }

    // ============================================================
    //  构建
    // ============================================================

    /**
     * 构建并返回一个已完全配置的 {@link PlayerPhysicalBodyModel}。
     */
    public PlayerPhysicalBodyModel build() {
        PlayerPhysicalBodyModel model = new PlayerPhysicalBodyModel();

        // ---- 创建并配置身体物理模型 ----
        model.bodyModel = new QuickPhysicsModel<>(model);
        model.bodyModel.setMass(bodyMass);
        model.bodyModel.getRotationConfig().setMomentOfInertia((float) bodyMomentOfInertia);
        model.bodyModel.getDragConfig().setDragCoefficient(bodyDragCoefficient);
        model.bodyModel.setPosition(bodyInitialPosition);
        model.bodyModel.getRotationConfig().setAngularDamping(bodyAngularDamping);

        // ---- 创建并配置头部物理模型 ----
        model.headModel = new QuickPhysicsModel<>(model);
        model.headModel.setMass(headMass);
        model.headModel.getRotationConfig().setMomentOfInertia((float) headMomentOfInertia);
        model.headModel.getDragConfig().setDragCoefficient(headDragCoefficient);
        model.headModel.setPosition(headInitialPosition);
        model.headModel.getRotationConfig().setAngularDamping(headAngularDamping);

        // ---- 颈部附着点 ----
        model.bodyNeckLocal = bodyNeckLocal;
        model.headNeckLocal = headNeckLocal;

        // ---- 颈部线性弹性 ----
        model.neckRestLength = neckRestLength;
        model.neckVerticalSpringK = neckVerticalSpringK;
        model.neckHorizontalSpringK = neckHorizontalSpringK;
        model.neckLinearDamping = neckLinearDamping;

        // ---- 颈部旋转弹性 ----
        model.neckRotSpringK = neckRotSpringK;
        model.neckRotDamping = neckRotDamping;
        model.maxNeckYaw = maxNeckYaw;
        model.maxNeckPitch = maxNeckPitch;
        model.maxNeckRoll = maxNeckRoll;

        // ---- 冲击响应 ----
        model.impactRotationScale = impactRotationScale;
        model.impactMaxAngularVelocity = impactMaxAngularVelocity;

        // ---- 头部 PID ----
        model.headPidKp = headPidKp;
        model.headPidKi = headPidKi;
        model.headPidKd = headPidKd;
        model.headPidIntegralMax = headPidIntegralMax;
        model.headPidOutputMax = headPidOutputMax;

        // ---- 身体 PID ----
        model.bodyPidKp = bodyPidKp;
        model.bodyPidKi = bodyPidKi;
        model.bodyPidKd = bodyPidKd;
        model.bodyPidIntegralMax = bodyPidIntegralMax;
        model.bodyPidOutputMax = bodyPidOutputMax;

        return model;
    }
}
