package io.github.sweetzonzi.machine_max.util.fl.physics;

import fl.FunctionLathe;
import lombok.Data;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

/**
 * 来自Archer's Helicopter的一个物理计算FL类
 *
 * 快速物理模型 - 为 {@link PhysicalObject} 提供完整的物理模拟能力。
 * <p>
 * 整合了线性物理（位置/速度/加速度/力/冲量/功率/能量）、
 * 旋转物理（Yaw/Pitch/Roll/角速度/扭矩/转动惯量/PID控制）、
 * 曲线/圆周运动（向心力/离心力/轨道运动/切线加速度）、
 * 阻力/阻尼（空气阻力/地面摩擦/角阻尼）、
 * 骨骼绑定（弹性连接/弹簧约束/刚性连接）、
 * 弹性势能（胡克定律/弹簧力/弹性势能）等计算。
 * <p>
 * 同类常量设置已打包为 {@code @Data} 数据类，可通过 {@code getXxxConfig()} 访问：
 * <ul>
 *   <li>{@link LinearPhysicsConfig} — 质量/重力/效率</li>
 *   <li>{@link DragConfig} — 空气阻力/地面摩擦参数</li>
 *   <li>{@link RotationConfig} — 转动惯量/角阻尼/限幅</li>
 *   <li>{@link PIDConfig} — PID 控制器参数</li>
 *   <li>{@link TorqueInertiaConfig} — 扭矩惯量系统参数</li>
 *   <li>{@link ModelTransformConfig} — 模型轴心/缩放</li>
 * </ul>
 *
 * @param <PO> 任意物理对象类型，需实现 {@link PhysicalObject} 接口
 */
public class QuickPhysicsModel<PO extends PhysicalObject> extends FunctionLathe<PO> {

    // ============================================================
    //  Config 数据类 — 同类常量设置
    // ============================================================

    @Data
    public static class LinearPhysicsConfig {
        private double mass = 1.0;
        private double gravity = 9.81;
        private double efficiency = 1.0;
    }

    @Data
    public static class DragConfig {
        private double dragCoefficient = 0.01;
        private double referenceArea = 0.01;
        private double airDensity = 0.5;
        private double groundFriction = 0.8;
        private double groundDragMultiplier = 2.0;
        private Vec3 gravityVector = new Vec3(0, -9.81, 0);
    }

    @Data
    public static class RotationConfig {
        private float momentOfInertia = 2.0f;
        private float angularDamping = 0.8f;
        private float maxAngularVelocity = 180f;
        private float maxTorque = 100f;
        private float maxAngularAcceleration = 720f;
    }

    @Data
    public static class PIDConfig {
        private float kP = 5.0f;
        private float kI = 0.1f;
        private float kD = 2.0f;
    }

    @Data
    public static class TorqueInertiaConfig {
        private float torqueScaleFactor = 100.0f;
        private float minEffectiveTorque = 1.0f;
        private float torqueInertiaDecay = 0.95f;
        private float maxTorqueInertiaDuration = 3.0f;
        private boolean smartTargetEnabled = true;
        private InertiaEndBehavior inertiaEndBehavior = InertiaEndBehavior.RETURN_TO_TARGET;
    }

    @Data
    public static class ModelTransformConfig {
        private Vec3 pivotPos = new Vec3(0, 0, 0);
        private Vec3 modelScale = new Vec3(1, 1, 1);
    }

    // ============================================================
    //  Config 实例
    // ============================================================

    protected final LinearPhysicsConfig linearPhysicsConfig = new LinearPhysicsConfig();
    protected final DragConfig dragConfig = new DragConfig();
    protected final RotationConfig rotationConfig = new RotationConfig();
    protected final PIDConfig pidConfig = new PIDConfig();
    protected final TorqueInertiaConfig torqueInertiaConfig = new TorqueInertiaConfig();
    protected final ModelTransformConfig modelTransformConfig = new ModelTransformConfig();

    // ============================================================
    //  1. 线性物理状态（运行时）
    // ============================================================

    protected Vec3 position = Vec3.ZERO;
    protected Vec3 velocity = Vec3.ZERO;
    protected Vec3 acceleration = Vec3.ZERO;
    protected Vec3 force = Vec3.ZERO;
    protected double power = 0.0;
    protected double energy = 0.0;
    protected boolean disabled = false;

    // ============================================================
    //  2. 旋转物理状态（运行时）
    // ============================================================

    protected float currentYaw = 0.0f;
    protected float currentPitch = 0.0f;
    protected float currentRoll = 0.0f;
    protected float prevYaw = 0.0f;
    protected float prevPitch = 0.0f;
    protected float prevRoll = 0.0f;
    protected final Quaternionf currentRotation = new Quaternionf();
    protected final Quaternionf lastRotation = new Quaternionf();
    protected float targetYaw = 0.0f;
    protected float targetPitch = 0.0f;
    protected float targetRoll = 0.0f;

    // ============================================================
    //  3. 扭矩/角速度系统（运行时）
    // ============================================================

    protected float torqueYaw = 0f;
    protected float torquePitch = 0f;
    protected float torqueRoll = 0f;
    protected float angularVelocityYaw = 0f;
    protected float angularVelocityPitch = 0f;
    protected float angularVelocityRoll = 0f;

    // ============================================================
    //  4. PID 运行时状态
    // ============================================================

    protected float integralYaw = 0f;
    protected float integralPitch = 0f;
    protected float integralRoll = 0f;
    protected float prevErrorYaw = 0f;
    protected float prevErrorPitch = 0f;
    protected float prevErrorRoll = 0f;

    // ============================================================
    //  5. 扭矩惯量运行时状态
    // ============================================================

    protected float torqueInertiaDuration = 0f;
    protected Vec3 torqueInertia = new Vec3(0, 0, 0);
    protected Vec3 inertiaTarget = new Vec3(0, 0, 0);
    protected boolean isTorqueInertiaDominant = false;

    public enum InertiaEndBehavior {
        RETURN_TO_TARGET,
        CONTINUE_MOMENTUM
    }

    // ============================================================
    //  6. 曲线/圆周运动（运行时）
    // ============================================================

    protected Vec3 orbitCenter = Vec3.ZERO;
    protected boolean orbitEnabled = false;
    protected double orbitRadius = 0.0;
    protected double orbitAngularVelocity = 0.0;
    protected double orbitPhase = 0.0;

    // ============================================================
    //  7. 骨骼绑定
    // ============================================================

    protected final List<BoneConstraint> boneConstraints = new ArrayList<>();
    protected final List<ElasticLink> elasticLinks = new ArrayList<>();

    /**
     * 骨骼约束 - 刚性连接
     */
    public static class BoneConstraint {
        public Vec3 localOffsetA;
        public Vec3 localOffsetB;
        public boolean active = true;
        public double maxStretch = 0.01;

        public BoneConstraint(Vec3 localOffsetA, Vec3 localOffsetB) {
            this.localOffsetA = localOffsetA;
            this.localOffsetB = localOffsetB;
        }
    }

    /**
     * 弹性连接 - 弹簧约束，支持胡克定律
     */
    public static class ElasticLink {
        public Vec3 attachPointA;
        public Vec3 attachPointB;
        public double restLength;
        public double springConstant;
        public double dampingCoefficient;
        public boolean active = true;

        public ElasticLink(Vec3 attachPointA, Vec3 attachPointB,
                           double restLength, double springConstant, double dampingCoefficient) {
            this.attachPointA = attachPointA;
            this.attachPointB = attachPointB;
            this.restLength = restLength;
            this.springConstant = springConstant;
            this.dampingCoefficient = dampingCoefficient;
        }
    }

    // ============================================================
    //  8. 时间系统
    // ============================================================

    protected static float TIME_FLOW_RATE = 0.526512f;

    // ============================================================
    //  构造器
    // ============================================================

    public QuickPhysicsModel(PO fl) {
        super(fl);
    }

    // ============================================================
    //  核心更新方法
    // ============================================================

    /**
     * 物理刻度更新（每帧调用）
     *
     * @param tickDelta 渲染帧间隔
     */
    public void physicsTick(float tickDelta) {
        if (disabled || linearPhysicsConfig.mass <= 0.0001) return;

        float deltaTime = getDeltaTime(tickDelta);

        savePreviousState();
        updateTorqueInertia(deltaTime);
        applyCurrentTorqueInertia();
        updateLinearPhysics(deltaTime);
        updatePhysicsRotation(deltaTime);
        updateOrbitMotion(deltaTime);
        updateElasticLinks(deltaTime);
        clearForces();
    }

    protected void savePreviousState() {
        prevYaw = currentYaw;
        prevPitch = currentPitch;
        prevRoll = currentRoll;
        lastRotation.set(currentRotation);
    }

    protected void clearForces() {
        torqueYaw = 0f;
        torquePitch = 0f;
        torqueRoll = 0f;
        this.force = Vec3.ZERO;
    }

    // ============================================================
    //  线性物理
    // ============================================================

    protected void updateLinearPhysics(float deltaTime) {
        acceleration = force.scale(1.0 / linearPhysicsConfig.mass);
        velocity = velocity.add(acceleration.scale(deltaTime));
        position = position.add(velocity.scale(deltaTime));
        power = force.dot(velocity);
        energy += power * deltaTime * linearPhysicsConfig.efficiency;

        applyGravity(deltaTime);
        applyAirResistance(deltaTime);
        applyGroundResistance(deltaTime);
        applyDamping(deltaTime);
    }

    protected void applyGravity(float deltaTime) {
        force = force.add(dragConfig.gravityVector.scale(linearPhysicsConfig.mass));
    }

    protected void applyAirResistance(float deltaTime) {
        double speedSquared = velocity.lengthSqr();
        if (speedSquared > 0.01) {
            double dragMagnitude = 0.5 * dragConfig.airDensity * speedSquared
                    * dragConfig.dragCoefficient * dragConfig.referenceArea;
            Vec3 dragForce = velocity.normalize().scale(-dragMagnitude);
            force = force.add(dragForce);
        }
    }

    protected void applyGroundResistance(float deltaTime) {
        double speedSquared = velocity.lengthSqr();
        if (speedSquared > 0.01) {
            double frictionMagnitude = dragConfig.groundFriction * linearPhysicsConfig.mass
                    * linearPhysicsConfig.gravity * speedSquared * dragConfig.groundDragMultiplier;
            Vec3 frictionForce = velocity.normalize().scale(-frictionMagnitude);
            force = force.add(frictionForce);
        }
    }

    /**
     * 应用一般线性阻尼（与速度成正比：F = -b * v）
     */
    protected void applyDamping(float deltaTime) {
        double dampingCoeff = dragConfig.dragCoefficient * 10.0;
        if (velocity.lengthSqr() > 0.0001) {
            Vec3 dampingForce = velocity.scale(-dampingCoeff);
            force = force.add(dampingForce);
        }
    }

    // ============================================================
    //  旋转物理
    // ============================================================

    protected void updatePhysicsRotation(float deltaTime) {
        updateTorqueInertia(deltaTime);
        applyCurrentTorqueInertia();

        float accelYaw = torqueYaw / rotationConfig.momentOfInertia;
        float accelPitch = torquePitch / rotationConfig.momentOfInertia;
        float accelRoll = torqueRoll / rotationConfig.momentOfInertia;

        accelYaw = Mth.clamp(accelYaw, -rotationConfig.maxAngularAcceleration, rotationConfig.maxAngularAcceleration);
        accelPitch = Mth.clamp(accelPitch, -rotationConfig.maxAngularAcceleration, rotationConfig.maxAngularAcceleration);
        accelRoll = Mth.clamp(accelRoll, -rotationConfig.maxAngularAcceleration, rotationConfig.maxAngularAcceleration);

        angularVelocityYaw += accelYaw * deltaTime;
        angularVelocityPitch += accelPitch * deltaTime;
        angularVelocityRoll += accelRoll * deltaTime;

        angularVelocityYaw *= (1f - rotationConfig.angularDamping * deltaTime);
        angularVelocityPitch *= (1f - rotationConfig.angularDamping * deltaTime);
        angularVelocityRoll *= (1f - rotationConfig.angularDamping * deltaTime);

        angularVelocityYaw = Mth.clamp(angularVelocityYaw, -rotationConfig.maxAngularVelocity, rotationConfig.maxAngularVelocity);
        angularVelocityPitch = Mth.clamp(angularVelocityPitch, -rotationConfig.maxAngularVelocity, rotationConfig.maxAngularVelocity);
        angularVelocityRoll = Mth.clamp(angularVelocityRoll, -rotationConfig.maxAngularVelocity, rotationConfig.maxAngularVelocity);

        float deltaYaw = angularVelocityYaw * deltaTime;
        float deltaPitch = angularVelocityPitch * deltaTime;
        float deltaRoll = angularVelocityRoll * deltaTime;

        if (Math.abs(deltaYaw) > 0.001f || Math.abs(deltaPitch) > 0.001f || Math.abs(deltaRoll) > 0.001f) {
            Quaternionf currentQuat = new Quaternionf();
            currentQuat.rotationYXZ(-currentYaw * Mth.DEG_TO_RAD, currentPitch * Mth.DEG_TO_RAD, currentRoll * Mth.DEG_TO_RAD);

            Quaternionf deltaQuat = new Quaternionf();
            deltaQuat.rotationYXZ(-deltaYaw * Mth.DEG_TO_RAD, deltaPitch * Mth.DEG_TO_RAD, deltaRoll * Mth.DEG_TO_RAD);

            currentQuat.mul(deltaQuat);

            float siny_cosp = 2.0f * (currentQuat.w * currentQuat.y + currentQuat.x * currentQuat.z);
            float cosy_cosp = 1.0f - 2.0f * (currentQuat.y * currentQuat.y + currentQuat.x * currentQuat.x);
            float yaw = (float) Math.atan2(siny_cosp, cosy_cosp);

            float sinp = 2.0f * (currentQuat.w * currentQuat.x - currentQuat.z * currentQuat.y);
            float pitch;
            if (Math.abs(sinp) >= 1) {
                pitch = Math.copySign((float) Math.PI / 2, sinp);
            } else {
                pitch = (float) Math.asin(sinp);
            }

            float sinr_cosp = 2.0f * (currentQuat.w * currentQuat.z + currentQuat.y * currentQuat.x);
            float cosr_cosp = 1.0f - 2.0f * (currentQuat.x * currentQuat.x + currentQuat.z * currentQuat.z);
            float roll = (float) Math.atan2(sinr_cosp, cosr_cosp);

            currentYaw = normalizeAngle(-yaw * Mth.RAD_TO_DEG);
            currentPitch = normalizeAngle(pitch * Mth.RAD_TO_DEG);
            currentRoll = normalizeAngle(roll * Mth.RAD_TO_DEG);
        }

        currentRotation.rotationYXZ(-currentYaw * Mth.DEG_TO_RAD, currentPitch * Mth.DEG_TO_RAD, currentRoll * Mth.DEG_TO_RAD);
    }

    // ============================================================
    //  扭矩惯量系统
    // ============================================================

    protected void updateTorqueInertia(float deltaTime) {
        if (torqueInertiaDuration > 0) {
            torqueInertiaDuration -= deltaTime;
            if (torqueInertiaDuration <= 0) {
                torqueInertiaDuration = 0;
                if (torqueInertiaConfig.smartTargetEnabled) {
                    if (isDefaultTargetPosition()) {
                        setTargetRotation(currentYaw, currentPitch, currentRoll);
                    }
                }
                isTorqueInertiaDominant = false;
            }
        }

        if (torqueInertia.length() > 0.01f) {
            decayTorqueInertia(deltaTime);
        } else {
            torqueInertia = new Vec3(0, 0, 0);
            isTorqueInertiaDominant = false;
        }
    }

    protected void decayTorqueInertia(float deltaTime) {
        float decayRate = torqueInertiaDuration > 0 ? 0.98f : torqueInertiaConfig.torqueInertiaDecay;
        double decayFactor = Math.pow(decayRate, deltaTime * 60f);

        double x = torqueInertia.x() * decayFactor;
        double y = torqueInertia.y() * decayFactor;
        double z = torqueInertia.z() * decayFactor;

        if (Math.abs(x) < 0.5f) x = 0;
        if (Math.abs(y) < 0.5f) y = 0;
        if (Math.abs(z) < 0.5f) z = 0;

        this.torqueInertia = new Vec3(x, y, z);
    }

    protected void applyCurrentTorqueInertia() {
        this.torqueYaw += (float) torqueInertia.x();
        this.torquePitch += (float) torqueInertia.y();
        this.torqueRoll += (float) torqueInertia.z();

        this.torqueYaw = Mth.clamp(this.torqueYaw, -rotationConfig.maxTorque, rotationConfig.maxTorque);
        this.torquePitch = Mth.clamp(this.torquePitch, -rotationConfig.maxTorque, rotationConfig.maxTorque);
        this.torqueRoll = Mth.clamp(this.torqueRoll, -rotationConfig.maxTorque, rotationConfig.maxTorque);
    }

    protected boolean isDefaultTargetPosition() {
        return targetYaw == 0f && targetPitch == 0f && targetRoll == 0f;
    }

    // ============================================================
    //  PID 控制器旋转
    // ============================================================

    /**
     * 使用 PID 控制器旋转到目标角度
     */
    public void rotateToWithPID(float targetYaw, float targetPitch, float targetRoll) {
        this.targetYaw = normalizeAngle(targetYaw);
        this.targetPitch = normalizeAngle(targetPitch);
        this.targetRoll = normalizeAngle(targetRoll);

        if (Math.abs(targetYaw - currentYaw) > 90f) integralYaw = 0f;
        if (Math.abs(targetPitch - currentPitch) > 90f) integralPitch = 0f;
        if (Math.abs(targetRoll - currentRoll) > 90f) integralRoll = 0f;
    }

    /**
     * 单轴 PID 更新
     */
    protected float updateAxisPID(float deltaTime, float current, float target,
                                   float angularVelocity, float integral, float prevError) {
        float error = target - current;
        if (error > 180f) error -= 360f;
        else if (error < -180f) error += 360f;

        integral += error * deltaTime;
        integral = Mth.clamp(integral, -100f, 100f);

        float derivative = (error - prevError) / Math.max(deltaTime, 0.001f);
        return pidConfig.kP * error + pidConfig.kI * integral + pidConfig.kD * derivative;
    }

    // ============================================================
    //  曲线/圆周运动
    // ============================================================

    /**
     * 设置轨道运动（围绕指定中心点做圆周/椭圆运动）
     *
     * @param center         轨道中心
     * @param radius         轨道半径
     * @param angularVelocity 角速度 (rad/s)
     * @param initialPhase   初始相位 (rad)
     */
    public void setOrbitMotion(Vec3 center, double radius, double angularVelocity, double initialPhase) {
        this.orbitCenter = center;
        this.orbitRadius = radius;
        this.orbitAngularVelocity = angularVelocity;
        this.orbitPhase = initialPhase;
        this.orbitEnabled = radius > 0 && Math.abs(angularVelocity) > 0;
    }

    /**
     * 停止轨道运动
     */
    public void stopOrbitMotion() {
        this.orbitEnabled = false;
    }

    protected void updateOrbitMotion(float deltaTime) {
        if (!orbitEnabled) return;

        orbitPhase += orbitAngularVelocity * deltaTime;

        double x = orbitCenter.x() + orbitRadius * Math.cos(orbitPhase);
        double z = orbitCenter.z() + orbitRadius * Math.sin(orbitPhase);

        Vec3 targetPos = new Vec3(x, position.y(), z);
        velocity = targetPos.subtract(position).scale(1.0 / Math.max(deltaTime, 0.001f));
        position = targetPos;

        double centripetalForce = linearPhysicsConfig.mass * orbitRadius * orbitAngularVelocity * orbitAngularVelocity;
        Vec3 centripetalDir = new Vec3(orbitCenter.x() - position.x(), 0, orbitCenter.z() - position.z());
        double dist = centripetalDir.length();
        if (dist > 0.001) {
            Vec3 centripetalForceVec = centripetalDir.normalize().scale(centripetalForce);
            force = force.add(centripetalForceVec);
        }
    }

    /**
     * 计算当前圆周运动的向心力大小 (F = m * v² / r)
     *
     * @return 向心力大小 (N)
     */
    public double calculateCentripetalForce() {
        double speed = velocity.length();
        if (speed == 0 || orbitRadius == 0) return 0;
        return linearPhysicsConfig.mass * speed * speed / orbitRadius;
    }

    /**
     * 计算当前运动的向心加速度 (a = v² / r)
     */
    public double calculateCentripetalAcceleration() {
        double speed = velocity.length();
        if (speed == 0 || orbitRadius == 0) return 0;
        return speed * speed / orbitRadius;
    }

    /**
     * 计算切线加速度（沿速度方向的加速度分量）
     */
    public double calculateTangentialAcceleration() {
        if (velocity.lengthSqr() == 0) return 0;
        Vec3 tangential = velocity.normalize();
        return acceleration.dot(tangential);
    }

    // ============================================================
    //  骨骼绑定 - 弹性连接 / 弹簧约束
    // ============================================================

    /**
     * 添加一个骨骼约束（刚性连接两个局部偏移点）
     */
    public BoneConstraint addBoneConstraint(Vec3 localOffsetA, Vec3 localOffsetB) {
        BoneConstraint constraint = new BoneConstraint(localOffsetA, localOffsetB);
        boneConstraints.add(constraint);
        return constraint;
    }

    /**
     * 添加一个弹性连接（弹簧）
     *
     * @param attachA            连接点 A（局部坐标）
     * @param attachB            连接点 B（局部坐标）
     * @param restLength         弹簧原长
     * @param springConstant     弹簧劲度系数 k (N/m)
     * @param dampingCoefficient 阻尼系数
     */
    public ElasticLink addElasticLink(Vec3 attachA, Vec3 attachB,
                                       double restLength, double springConstant, double dampingCoefficient) {
        ElasticLink link = new ElasticLink(attachA, attachB, restLength, springConstant, dampingCoefficient);
        elasticLinks.add(link);
        return link;
    }

    public void removeBoneConstraint(BoneConstraint constraint) {
        boneConstraints.remove(constraint);
    }

    public void removeElasticLink(ElasticLink link) {
        elasticLinks.remove(link);
    }

    public void clearBoneConstraints() {
        boneConstraints.clear();
    }

    public void clearElasticLinks() {
        elasticLinks.clear();
    }

    /**
     * 更新所有弹性连接（弹簧力计算）
     */
    protected void updateElasticLinks(float deltaTime) {
        for (ElasticLink link : elasticLinks) {
            if (!link.active) continue;

            Vec3 worldA = worldSpaceOf(link.attachPointA);
            Vec3 worldB = worldSpaceOf(link.attachPointB);
            Vec3 displacement = worldB.subtract(worldA);
            double currentLength = displacement.length();

            if (currentLength < 1e-10) continue;

            double stretch = currentLength - link.restLength;
            Vec3 direction = displacement.normalize();

            double springForceMagnitude = link.springConstant * stretch;
            Vec3 springForce = direction.scale(springForceMagnitude);

            Vec3 relVel = velocity;
            double dampingForceMagnitude = link.dampingCoefficient * relVel.dot(direction);
            Vec3 dampingForce = direction.scale(-dampingForceMagnitude);

            Vec3 totalForce = springForce.add(dampingForce);
            force = force.add(totalForce);

            applyTorque(0, 0, (float) (springForceMagnitude * 0.01f));
        }
    }

    /**
     * 计算弹性势能 (E = ½kx²)
     *
     * @return 总弹性势能 (J)
     */
    public double getElasticPotentialEnergy() {
        double total = 0;
        for (ElasticLink link : elasticLinks) {
            if (!link.active) continue;
            Vec3 worldA = worldSpaceOf(link.attachPointA);
            Vec3 worldB = worldSpaceOf(link.attachPointB);
            double stretch = worldB.distanceTo(worldA) - link.restLength;
            if (stretch > 0) {
                total += 0.5 * link.springConstant * stretch * stretch;
            }
        }
        return total;
    }

    /**
     * 将局部坐标转换为世界坐标
     */
    public Vec3 worldSpaceOf(Vec3 localPoint) {
        Quaternionf rot = getRotation();
        Vec3 rotated = rotateVectorByQuaternion(localPoint, rot);
        return position.add(rotated);
    }

    // ============================================================
    //  力 / 冲量 / 扭矩 API
    // ============================================================

    public void addForce(Vec3 force) {
        this.force = this.force.add(force);
    }

    public void addForce(double x, double y, double z) {
        this.force = this.force.add(x, y, z);
    }

    public void setForce(Vec3 force) {
        this.force = force;
    }

    public void applyImpulse(Vec3 impulse) {
        velocity = velocity.add(impulse.scale(1.0 / linearPhysicsConfig.mass));
    }

    public void applyTorque(float torqueYaw, float torquePitch, float torqueRoll) {
        this.torqueYaw += Mth.clamp(torqueYaw, -rotationConfig.maxTorque, rotationConfig.maxTorque);
        this.torquePitch += Mth.clamp(torquePitch, -rotationConfig.maxTorque, rotationConfig.maxTorque);
        this.torqueRoll += Mth.clamp(torqueRoll, -rotationConfig.maxTorque, rotationConfig.maxTorque);

        this.torqueYaw = Mth.clamp(this.torqueYaw, -rotationConfig.maxTorque, rotationConfig.maxTorque);
        this.torquePitch = Mth.clamp(this.torquePitch, -rotationConfig.maxTorque, rotationConfig.maxTorque);
        this.torqueRoll = Mth.clamp(this.torqueRoll, -rotationConfig.maxTorque, rotationConfig.maxTorque);
    }

    public void applyTorqueToAxis(String axis, float torque) {
        torque = Mth.clamp(torque, -rotationConfig.maxTorque, rotationConfig.maxTorque);
        switch (axis.toLowerCase()) {
            case "yaw" -> this.torqueYaw = Mth.clamp(this.torqueYaw + torque, -rotationConfig.maxTorque, rotationConfig.maxTorque);
            case "pitch" -> this.torquePitch = Mth.clamp(this.torquePitch + torque, -rotationConfig.maxTorque, rotationConfig.maxTorque);
            case "roll" -> this.torqueRoll = Mth.clamp(this.torqueRoll + torque, -rotationConfig.maxTorque, rotationConfig.maxTorque);
        }
    }

    /**
     * 应用扭矩惯量
     */
    public void applyTorqueInertia(float torqueYaw, float torquePitch, float torqueRoll, float duration) {
        torqueYaw *= torqueInertiaConfig.torqueScaleFactor;
        torquePitch *= torqueInertiaConfig.torqueScaleFactor;
        torqueRoll *= torqueInertiaConfig.torqueScaleFactor;

        this.torqueInertia = new Vec3(
                this.torqueInertia.x() + Mth.clamp(torqueYaw, -rotationConfig.maxTorque, rotationConfig.maxTorque),
                this.torqueInertia.y() + Mth.clamp(torquePitch, -rotationConfig.maxTorque, rotationConfig.maxTorque),
                this.torqueInertia.z() + Mth.clamp(torqueRoll, -rotationConfig.maxTorque, rotationConfig.maxTorque)
        );

        if (duration > 0) {
            this.torqueInertiaDuration = Math.min(duration, torqueInertiaConfig.maxTorqueInertiaDuration);
        } else {
            float magnitude = (float) this.torqueInertia.length();
            this.torqueInertiaDuration = Math.min(magnitude / 50f, torqueInertiaConfig.maxTorqueInertiaDuration);
        }

        clampTorqueInertia();

        if (torqueInertiaConfig.smartTargetEnabled) {
            inertiaTarget = new Vec3(currentYaw, currentPitch, currentRoll);
            isTorqueInertiaDominant = true;
            setTargetRotation(currentYaw, currentPitch, currentRoll);
        }
    }

    public void applyTorqueInertiaToAxis(String axis, float torque, float duration) {
        double x = torqueInertia.x();
        double y = torqueInertia.y();
        double z = torqueInertia.z();

        torque = Mth.clamp(torque, -rotationConfig.maxTorque, rotationConfig.maxTorque);
        switch (axis.toLowerCase()) {
            case "yaw" -> x += torque;
            case "pitch" -> y += torque;
            case "roll" -> z += torque;
        }

        this.torqueInertia = new Vec3(x, y, z);
        if (duration > 0) {
            this.torqueInertiaDuration = Math.max(this.torqueInertiaDuration,
                    Math.min(duration, torqueInertiaConfig.maxTorqueInertiaDuration));
        }
        clampTorqueInertia();
    }

    private void clampTorqueInertia() {
        double x = Mth.clamp(torqueInertia.x(), -rotationConfig.maxTorque, rotationConfig.maxTorque);
        double y = Mth.clamp(torqueInertia.y(), -rotationConfig.maxTorque, rotationConfig.maxTorque);
        double z = Mth.clamp(torqueInertia.z(), -rotationConfig.maxTorque, rotationConfig.maxTorque);
        this.torqueInertia = new Vec3(x, y, z);
    }

    public void clearTorqueInertia() {
        torqueInertia = new Vec3(0, 0, 0);
        torqueInertiaDuration = 0f;
    }

    /**
     * 急停（立即停止所有运动）
     */
    public void emergencyStop() {
        velocity = Vec3.ZERO;
        angularVelocityYaw = 0f;
        angularVelocityPitch = 0f;
        angularVelocityRoll = 0f;
        integralYaw = 0f;
        integralPitch = 0f;
        integralRoll = 0f;
        torqueYaw = 0f;
        torquePitch = 0f;
        torqueRoll = 0f;
        torqueInertia = new Vec3(0, 0, 0);
        torqueInertiaDuration = 0f;
    }

    // ============================================================
    //  目标角度控制
    // ============================================================

    public void setTargetRotation(float yaw, float pitch, float roll) {
        this.targetYaw = normalizeAngle(yaw);
        this.targetPitch = normalizeAngle(pitch);
        this.targetRoll = normalizeAngle(roll);
    }

    public void setRotationImmediate(float yaw, float pitch, float roll) {
        this.currentYaw = this.prevYaw = normalizeAngle(yaw);
        this.currentPitch = this.prevPitch = normalizeAngle(pitch);
        this.currentRoll = this.prevRoll = normalizeAngle(roll);
        this.targetYaw = currentYaw;
        this.targetPitch = currentPitch;
        this.targetRoll = currentRoll;
    }

    public void rotateRelative(float deltaYaw, float deltaPitch, float deltaRoll) {
        this.targetYaw = normalizeAngle(targetYaw + deltaYaw);
        this.targetPitch = normalizeAngle(targetPitch + deltaPitch);
        this.targetRoll = normalizeAngle(targetRoll + deltaRoll);
    }

    // ============================================================
    //  能量 / 力学量计算
    // ============================================================

    public double calculateKineticEnergy() {
        return 0.5 * linearPhysicsConfig.mass * velocity.lengthSqr();
    }

    public Vec3 calculateMomentum() {
        return velocity.scale(linearPhysicsConfig.mass);
    }

    public float getRotationalKineticEnergy() {
        float vYaw = angularVelocityYaw * Mth.DEG_TO_RAD;
        float vPitch = angularVelocityPitch * Mth.DEG_TO_RAD;
        float vRoll = angularVelocityRoll * Mth.DEG_TO_RAD;
        return 0.5f * rotationConfig.momentOfInertia * (vYaw * vYaw + vPitch * vPitch + vRoll * vRoll);
    }

    public double getTotalKineticEnergy() {
        return calculateKineticEnergy() + getRotationalKineticEnergy();
    }

    public double getMechanicalEnergy() {
        double height = position.y();
        double potential = linearPhysicsConfig.mass * linearPhysicsConfig.gravity * height;
        return getTotalKineticEnergy() + potential + getElasticPotentialEnergy();
    }

    // ============================================================
    //  物理参数设置
    // ============================================================

    public void setPhysicsParameters(double mass, double gravity, double dragCoefficient) {
        linearPhysicsConfig.setMass(Math.max(0.1, mass));
        linearPhysicsConfig.setGravity(Math.max(0, gravity));
        dragConfig.setDragCoefficient(Math.max(0, dragCoefficient));
    }

    public void setRotationPhysicsParameters(float inertia, float damping, float maxVelocity, float maxTorque) {
        rotationConfig.setMomentOfInertia(Math.max(0.1f, inertia));
        rotationConfig.setAngularDamping(Mth.clamp(damping, 0f, 1f));
        rotationConfig.setMaxAngularVelocity(Math.max(1f, maxVelocity));
        rotationConfig.setMaxTorque(Math.max(1f, maxTorque));
    }

    public void setPIDParameters(float p, float i, float d) {
        pidConfig.setKP(Math.max(0f, p));
        pidConfig.setKI(Math.max(0f, i));
        pidConfig.setKD(Math.max(0f, d));
    }

    public void setDragParameters(double dragCoefficient, double referenceArea, double airDensity) {
        dragConfig.setDragCoefficient(Math.max(0, dragCoefficient));
        dragConfig.setReferenceArea(Math.max(0, referenceArea));
        dragConfig.setAirDensity(Math.max(0, airDensity));
    }

    public void setGroundFriction(double friction, double multiplier) {
        dragConfig.setGroundFriction(Math.max(0, friction));
        dragConfig.setGroundDragMultiplier(Math.max(0, multiplier));
    }

    public void setGravityVector(Vec3 gravityVector) {
        dragConfig.setGravityVector(gravityVector);
    }

    public void setTorqueInertiaDecay(float decay) {
        torqueInertiaConfig.setTorqueInertiaDecay(Mth.clamp(decay, 0.1f, 1.0f));
    }

    public void setTorqueScaleFactor(float scale) {
        torqueInertiaConfig.setTorqueScaleFactor(Math.max(1.0f, scale));
    }

    public void setInertiaEndBehavior(InertiaEndBehavior behavior) {
        torqueInertiaConfig.setInertiaEndBehavior(behavior);
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    // ============================================================
    //  Config 访问器
    // ============================================================

    public LinearPhysicsConfig getLinearPhysicsConfig() { return linearPhysicsConfig; }
    public DragConfig getDragConfig() { return dragConfig; }
    public RotationConfig getRotationConfig() { return rotationConfig; }
    public PIDConfig getPidConfig() { return pidConfig; }
    public TorqueInertiaConfig getTorqueInertiaConfig() { return torqueInertiaConfig; }
    public ModelTransformConfig getModelTransformConfig() { return modelTransformConfig; }

    // ============================================================
    //  角度插值（渲染用）
    // ============================================================

    public float getSmartYaw(float partialTick) {
        return smartLerpAngle(prevYaw, currentYaw, partialTick);
    }

    public float getSmartPitch(float partialTick) {
        return smartLerpAngle(prevPitch, currentPitch, partialTick);
    }

    public float getSmartRoll(float partialTick) {
        return smartLerpAngle(prevRoll, currentRoll, partialTick);
    }

    public Quaternionf getRotation() {
        return createSmartRotationQuaternion(0);
    }

    public Quaternionf getRotation(float partialTick) {
        return createSmartRotationQuaternion(partialTick);
    }

    protected Quaternionf createSmartRotationQuaternion(float partialTick) {
        Quaternionf result = new Quaternionf();
        currentRotation.slerp(lastRotation, 1.0f - partialTick, result);
        return result;
    }

    private static float smartLerpAngle(float start, float end, float progress) {
        float diff = end - start;
        if (diff > 180f) diff -= 360f;
        else if (diff < -180f) diff += 360f;
        return start + diff * progress;
    }

    // ============================================================
    //  世界朝向向量
    // ============================================================

    public Vec3 getForwardVector() {
        return getForwardVector(0);
    }

    public Vec3 getForwardVector(float partialTicks) {
        Quaternionf rotation = createSmartRotationQuaternion(partialTicks);
        return rotateVectorByQuaternion(new Vec3(0, 0, 1), rotation);
    }

    public Vec3 getRightVector() {
        return getRightVector(0);
    }

    public Vec3 getRightVector(float partialTicks) {
        Quaternionf rotation = createSmartRotationQuaternion(partialTicks);
        return rotateVectorByQuaternion(new Vec3(1, 0, 0), rotation);
    }

    public Vec3 getUpVector() {
        return getUpVector(0);
    }

    public Vec3 getUpVector(float partialTicks) {
        Quaternionf rotation = createSmartRotationQuaternion(partialTicks);
        return rotateVectorByQuaternion(new Vec3(0, 1, 0), rotation);
    }

    public Vec3 getLeftVector() {
        return getLeftVector(0);
    }

    public Vec3 getLeftVector(float partialTicks) {
        return getRightVector(partialTicks).scale(-1);
    }

    public Vec3 getBackwardVector() {
        return getBackwardVector(0);
    }

    public Vec3 getBackwardVector(float partialTicks) {
        return getForwardVector(partialTicks).scale(-1);
    }

    public Vec3 getDownVector() {
        return getDownVector(0);
    }

    public Vec3 getDownVector(float partialTicks) {
        return getUpVector(partialTicks).scale(-1);
    }

    public Vec3 getWorldOrientationEuler() {
        return new Vec3(currentYaw, currentPitch, currentRoll);
    }

    public Vec3 getWorldOrientationEuler(float partialTicks) {
        return new Vec3(getSmartYaw(partialTicks), getSmartPitch(partialTicks), getSmartRoll(partialTicks));
    }

    // ============================================================
    //  向量旋转工具
    // ============================================================

    public Vec3 rotateVector(Vec3 vector) {
        return rotateVectorByQuaternion(vector, getRotation());
    }

    public static Vec3 rotateVectorByQuaternion(Vec3 vector, Quaternionf rotation) {
        float x = (float) vector.x;
        float y = (float) vector.y;
        float z = (float) vector.z;

        float qx = rotation.x;
        float qy = rotation.y;
        float qz = rotation.z;
        float qw = rotation.w;

        float ix = qw * x + qy * z - qz * y;
        float iy = qw * y + qz * x - qx * z;
        float iz = qw * z + qx * y - qy * x;
        float iw = -qx * x - qy * y - qz * z;

        float resultX = ix * qw + iw * -qx + iy * -qz - iz * -qy;
        float resultY = iy * qw + iw * -qy + iz * -qx - ix * -qz;
        float resultZ = iz * qw + iw * -qz + ix * -qy - iy * -qx;

        return new Vec3(resultX, resultY, resultZ);
    }

    public Vec3 rotateRelativePositionStable(Vec3 relativePos) {
        float yawRad = currentYaw * Mth.DEG_TO_RAD;
        float pitchRad = currentPitch * Mth.DEG_TO_RAD;
        float rollRad = currentRoll * Mth.DEG_TO_RAD;

        Vec3 rotated = relativePos;

        double cosYaw = Math.cos(yawRad);
        double sinYaw = Math.sin(yawRad);
        rotated = new Vec3(
                rotated.x * cosYaw - rotated.z * sinYaw,
                rotated.y,
                rotated.x * sinYaw + rotated.z * cosYaw
        );

        double cosPitch = Math.cos(pitchRad);
        double sinPitch = Math.sin(pitchRad);
        rotated = new Vec3(
                rotated.x,
                rotated.y * cosPitch - rotated.z * sinPitch,
                rotated.y * sinPitch + rotated.z * cosPitch
        );

        double cosRoll = Math.cos(rollRad);
        double sinRoll = Math.sin(rollRad);
        rotated = new Vec3(
                rotated.x * cosRoll - rotated.y * sinRoll,
                rotated.x * sinRoll + rotated.y * cosRoll,
                rotated.z
        );

        return rotated;
    }

    // ============================================================
    //  模型变换
    // ============================================================

    public float scaleX() { return (float) modelTransformConfig.modelScale.x(); }
    public float scaleY() { return (float) modelTransformConfig.modelScale.y(); }
    public float scaleZ() { return (float) modelTransformConfig.modelScale.z(); }
    public float pivotX() { return (float) modelTransformConfig.pivotPos.x(); }
    public float pivotY() { return (float) modelTransformConfig.pivotPos.y(); }
    public float pivotZ() { return (float) modelTransformConfig.pivotPos.z(); }

    public void setScale(float x, float y, float z) {
        this.modelTransformConfig.modelScale = new Vec3(x, y, z);
    }

    public void setPivot(float x, float y, float z) {
        this.modelTransformConfig.pivotPos = new Vec3(x, y, z);
    }

    // ============================================================
    //  状态查询
    // ============================================================

    public boolean isRotating() {
        return currentYaw != targetYaw || currentPitch != targetPitch || currentRoll != targetRoll;
    }

    public Vec3 getRotationDifference() {
        return new Vec3(
                normalizeAngle(targetYaw - currentYaw),
                normalizeAngle(targetPitch - currentPitch),
                normalizeAngle(targetRoll - currentRoll)
        );
    }

    public boolean isTorqueInertiaActive() {
        return torqueInertia.length() > 0.5f;
    }

    public float getTorqueInertiaStrength() {
        return (float) (torqueInertia.length() / rotationConfig.maxTorque);
    }

    public boolean isDisabled() { return disabled; }

    // ============================================================
    //  Getters / Setters（委托到 Config 对象）
    // ============================================================

    public Vec3 getPosition() { return position; }
    public void setPosition(Vec3 position) { this.position = position; }
    public Vec3 getVelocity() { return velocity; }
    public void setVelocity(Vec3 velocity) { this.velocity = velocity; }
    public Vec3 getAcceleration() { return acceleration; }
    public void setAcceleration(Vec3 acceleration) { this.acceleration = acceleration; }
    public Vec3 getForce() { return force; }
    public double getMass() { return linearPhysicsConfig.mass; }
    public void setMass(double mass) { linearPhysicsConfig.setMass(Math.max(0.1, mass)); }
    public double getGravity() { return linearPhysicsConfig.gravity; }
    public void setGravity(double gravity) { linearPhysicsConfig.setGravity(gravity); }
    public double getPower() { return power; }
    public double getEnergy() { return energy; }
    public void setEnergy(double energy) { this.energy = energy; }
    public double getEfficiency() { return linearPhysicsConfig.efficiency; }
    public void setEfficiency(double efficiency) { linearPhysicsConfig.setEfficiency(Mth.clamp(efficiency, 0, 1)); }
    public double getDragCoefficient() { return dragConfig.dragCoefficient; }
    public void setDragCoefficient(double dragCoefficient) { dragConfig.setDragCoefficient(Math.max(0, dragCoefficient)); }
    public double getReferenceArea() { return dragConfig.referenceArea; }
    public double getAirDensity() { return dragConfig.airDensity; }

    public float getCurrentYaw() { return currentYaw; }
    public float getCurrentPitch() { return currentPitch; }
    public float getCurrentRoll() { return currentRoll; }
    public Vec3 getAngularVelocity() { return new Vec3(angularVelocityYaw, angularVelocityPitch, angularVelocityRoll); }

    public void setAngularVelocity(float yawSpeed, float pitchSpeed, float rollSpeed) {
        this.angularVelocityYaw = Mth.clamp(yawSpeed, -rotationConfig.maxAngularVelocity, rotationConfig.maxAngularVelocity);
        this.angularVelocityPitch = Mth.clamp(pitchSpeed, -rotationConfig.maxAngularVelocity, rotationConfig.maxAngularVelocity);
        this.angularVelocityRoll = Mth.clamp(rollSpeed, -rotationConfig.maxAngularVelocity, rotationConfig.maxAngularVelocity);
    }

    public Vec3 getTorqueInertia() { return torqueInertia; }
    public float getTorqueInertiaRemainingTime() { return torqueInertiaDuration; }
    public List<BoneConstraint> getBoneConstraints() { return boneConstraints; }
    public List<ElasticLink> getElasticLinks() { return elasticLinks; }

    // ============================================================
    //  工具方法
    // ============================================================

    public static float normalizeAngle(float angle) {
        angle = angle % 360.0f;
        if (angle > 180.0f) angle -= 360.0f;
        else if (angle < -180.0f) angle += 360.0f;
        return angle;
    }

    public static Vec3 every(double d) {
        return new Vec3(d, d, d);
    }

    public static float getDeltaTime(float tickDelta) {
        float baseDeltaTime = tickDelta * 0.05f;
        return baseDeltaTime * TIME_FLOW_RATE;
    }

    public static void setTimeFlowRate(float rate) {
        TIME_FLOW_RATE = Math.max(0.1f, rate);
    }

    public static float getTimeFlowRate() {
        return TIME_FLOW_RATE;
    }

    public static Vec3 getAcceleration(Vec3 force, double mass) {
        return force.scale(1.0 / mass);
    }

    public static Vec3 getForce(double mass, Vec3 acceleration) {
        return acceleration.scale(mass);
    }

    public static Vec3 getMomentum(double mass, Vec3 speed) {
        return speed.scale(mass);
    }

    public static Vec3 getSpeedFromMomentum(double mass, Vec3 momentum) {
        if (mass == 0) return Vec3.ZERO;
        return momentum.scale(1.0 / mass);
    }

    private static Vec3 filterNaN(Vec3 vector) {
        double x = Double.isNaN(vector.x) ? 0.0 : vector.x;
        double y = Double.isNaN(vector.y) ? 0.0 : vector.y;
        double z = Double.isNaN(vector.z) ? 0.0 : vector.z;
        return new Vec3(x, y, z);
    }

    /**
     * 计算从当前朝向旋转到速度方向（切线方向）的四元数
     */
    public Quaternionf calculateRotationToTangent(Vec3 upDirection, float alpha) {
        Vec3 tangent = getTangentVector();
        if (tangent.equals(Vec3.ZERO)) {
            return new Quaternionf(getRotation());
        }

        Quaternionf targetRotation = new Quaternionf();
        targetRotation.lookAlong(
                (float) tangent.x, (float) tangent.y, (float) tangent.z,
                (float) upDirection.x, (float) upDirection.y, (float) upDirection.z
        );

        return getRotation().slerp(targetRotation, alpha, new Quaternionf());
    }

    public Vec3 getTangentVector() {
        if (velocity.lengthSqr() == 0) return Vec3.ZERO;
        return velocity.normalize();
    }
}
