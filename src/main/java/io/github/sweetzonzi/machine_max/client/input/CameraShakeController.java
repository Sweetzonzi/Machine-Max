package io.github.sweetzonzi.machine_max.client.input;

import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileManager;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import io.github.sweetzonzi.machine_max.common.visual.VisualEffectHelper;
import io.github.sweetzonzi.machine_max.network.payload.projectile.ProjectilesHitPayload;
import io.github.sweetzonzi.machine_max.util.SpringDamper;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 客户端相机抖动与偏移控制器（static，与 {@link CameraController} 一致）。
 * <p>
 * 包含三个独立的 {@link SpringDamper} 实例：
 * <ul>
 *   <li>{@link #rotationDamper} / {@link #positionDamper}: System A 投射物抖屏（高k，临界阻尼）</li>
 *   <li>{@link #vehicleChaseDamper}: System B 载具相机惯性追赶（高k，欠阻尼）</li>
 * </ul>
 * <p>
 * 两套刚度必须分开实例，因为挤在一套上会导致抖屏的临界阻尼和追赶的欠阻尼
 * 互相污染——要么抖屏有余振，要么追赶无惯性感。
 * <p>
 * <b>调用线程：</b>渲染线程（CameraController.updateCameraRot 开头统一 tick）；
 * 注意 1.21.1 中渲染线程即主线程。
 * <p>
 * <b>计时方案：</b>使用 tickCount + partialTick 差分计算 dt，
 * 暂停时 tickCount 和 partialTick 均不增长 → dt=0，保证游戏暂停时弹簧不突变。
 * <p>
 * <b>调用时序：</b>tick() 在每帧渲染调用前执行（此时才有 partialTick），
 * onVehicleCameraChase() 在同一渲染调用链中设置目标位置，tick 总是在 setTargetPosition 之后执行。
 */
public class CameraShakeController {

    // ===== System A: 投射物抖屏 =====
    // 高k + 临界阻尼 → 快速一震后立即恢复，无余振
    private static final float SHAKE_STIFFNESS  = 12f;
    private static final float SHAKE_DAMPING    = 6.93f; // 临界阻尼：d = 2·sqrt(k)
    private static final SpringDamper rotationDamper  = new SpringDamper(SHAKE_STIFFNESS, SHAKE_DAMPING);
    private static final SpringDamper positionDamper  = new SpringDamper(SHAKE_STIFFNESS, SHAKE_DAMPING);

    // ===== System B: 载具相机惯性追赶 =====
    // 高k + 欠阻尼 → 迅速追赶，有余振，有惯性感
    private static final float CHASE_STIFFNESS = 175f;
    private static final float CHASE_DAMPING   = 12.5f; // 欠阻尼：d < 2·sqrt(k)
    private static final SpringDamper vehicleChaseDamper = new SpringDamper(CHASE_STIFFNESS, CHASE_DAMPING);

    // ===== 帧计时 =====
    private static int lastTickCount = -1;
    private static float lastPartialTick = 0f;

    // ===== 强度缩放常量 =====
    /** 旋转抖动的全局缩放系数 */
    private static final float ROTATION_SCALE        = 0.015f;
    /** 失色压制效果的全局缩放系数 */
    private static final float SUPPRESSION_SCALE     = 0.3f;
    /** 压制归一化的参考动量（kg·m/s），超过此值的动量不再额外增加压制强度 */
    private static final float MAX_REFERENCE_MOMENTUM = 500f;
    /** 第一人称载具惯性缩放 — 大幅降低幅度，仅保留微弱弹簧感 */
    private static final float FP_VEHICLE_SCALE       = 0.15f;
    /** 最小触发速度（m/s），低于此值不触发抖动 */
    private static final float MIN_SPEED_THRESHOLD    = 0.1f;
    /** 距离衰减系数：1/(1 + d² × 此值) */
    public static final float DISTANCE_DECAY_FACTOR   = 0.01f;
    /** 位置偏移占旋转偏移的比例 */
    private static final float POSITION_SHAKE_RATIO   = 0.3f;

    // ===== 视角模式因子（对外公开供 CameraController 使用） =====
    /** 炮镜模式的旋转抖动缩放因子 */
    public static final float CAMERA_MODE_ROTATION_SCALE  = 0.3f;
    /** 第一人称的旋转抖动缩放因子 */
    public static final float FIRST_PERSON_ROTATION_SCALE = 0.4f;
    /** 第三人称的旋转抖动缩放因子 */
    public static final float THIRD_PERSON_ROTATION_SCALE = 1.0f;
    /** 载具内投射物抖屏缩放因子 */
    private static final float VEHICLE_SHAKE_FACTOR   = 0.4f;
    /** 炮镜模式下附加的抖屏缩放因子 */
    private static final float SIGHT_SHAKE_FACTOR     = 0.3f;
    /** 载具内压制效果缩放因子 */
    private static final float SUPPRESSION_VEHICLE_FACTOR = 0.3f;

    // ===== 视角模式因子 =====

    /**
     * 获取当前视角模式的旋转抖动缩放因子。
     */
    private static float getPerspectiveShakeFactor() {
        if (CameraController.isCameraMode()) return CAMERA_MODE_ROTATION_SCALE;
        if (Minecraft.getInstance().options.getCameraType().isFirstPerson()) return FIRST_PERSON_ROTATION_SCALE;
        return THIRD_PERSON_ROTATION_SCALE;
    }

    /**
     * 获取当前视角模式的位置抖动缩放因子。
     * 第一人称和炮镜模式禁用位置偏移（相机即眼睛/传感器，位移会晕）。
     */
    public static float getPositionShakeFactor() {
        if (CameraController.isCameraMode()) return 0f;
        if (Minecraft.getInstance().options.getCameraType().isFirstPerson()) return 0f;
        return 1.0f;
    }

    // ===== 帧计时 =====

    /**
     * 计算本帧精确 dt（秒）。
     * updateCameraRot 和 updateCameraPos 中均可调用，共享同一计时状态。
     */
    private static float computeDeltaTime(int tickCount, float partialTick) {
        if (lastTickCount < 0) {
            lastTickCount = tickCount;
            lastPartialTick = partialTick;
            return 0.05f; // 首帧用默认值
        }
        float dt = (tickCount - lastTickCount) * 0.05f + (partialTick - lastPartialTick) * 0.05f;
        lastTickCount = tickCount;
        lastPartialTick = partialTick;
        return Math.max(dt, 0.001f); // 防止 dt=0
    }

    // ===== 公开 API =====

    /**
     * 由 ProjectilesHitPayload handler 调用，处理单发投射物命中产生的镜头抖动与失色压制。
     *
     * @param entry 命中条目（含速度、位置、法线）
     * @param player 本地玩家
     * @param pm     投射物管理器
     * @param idx    投射物在 SoA 中的索引（由调用方已查好，避免重复 O(n) 扫描）
     */
    public static void onProjectileHit(ProjectilesHitPayload.HitEntry entry, Player player, ProjectileManager pm, int idx) {
        // ① 从 SoA typeCache 获取投射物质量
        ProjectileType type = pm.getProjectileTypeByIndex(idx);
        float mass = type.getMass();

        // ② 计算命中速度 → 动量
        double speed = Math.sqrt(
                entry.newVelX() * entry.newVelX() +
                        entry.newVelY() * entry.newVelY() +
                        entry.newVelZ() * entry.newVelZ());
        if (speed < MIN_SPEED_THRESHOLD) return;
        float momentum = mass * (float) speed;

        // ③ 距离衰减：1/(1 + d² × DISTANCE_DECAY_FACTOR)
        double distSq = player.distanceToSqr(entry.hitX(), entry.hitY(), entry.hitZ());
        float distanceFactor = 1.0f / (1.0f + (float) distSq * DISTANCE_DECAY_FACTOR);

        // ④ 视角模式因子
        float perspectiveFactor = getPerspectiveShakeFactor();

        // ⑤ 载具因子
        float vehicleFactor = CameraController.isOnBoard() ? VEHICLE_SHAKE_FACTOR : 1.0f;

        // ⑥ 炮镜因子（onProjectileHit 时 activeCamera 可能尚未激活，双重保险）
        float sightFactor = CameraController.isCameraMode() ? SIGHT_SHAKE_FACTOR : 1.0f;

        // ⑦ 最终旋转冲量
        float intensity = momentum * distanceFactor * perspectiveFactor
                * vehicleFactor * sightFactor * ROTATION_SCALE;

        // ⑧ 命中方向（法线）
        Vec3 hitDir = new Vec3(entry.normalX(), entry.normalY(), entry.normalZ());
        double normalLen = hitDir.length();
        if (normalLen < 1e-8) return;
        hitDir = hitDir.scale(1.0 / normalLen);

        // ⑨ 应用冲量
        rotationDamper.applyImpulse(hitDir.scale(intensity));
        positionDamper.applyImpulse(hitDir.scale(-intensity * POSITION_SHAKE_RATIO));

        // ⑩ 手动触发失色压制
        triggerSuppression(distanceFactor, momentum);
    }

    /**
     * 由 CameraController.updateCameraPos 调用（仅乘坐载具时）。
     * 传入载具理想相机位置，SpringDamper 内部追踪目标位移自动产生弹簧阻尼滞后。
     * 第一人称和第三人称均适用，CameraController 侧用 FP_VEHICLE_SCALE 缩放 offset。
     */
    public static void onVehicleCameraChase(Vec3 idealPos, float maxOffset) {
        vehicleChaseDamper.setTargetPosition(idealPos, maxOffset);
    }

    /**
     * 每帧 tick，CameraController.updateCameraRot 开头调用。
     * 参数由 ViewportEvent.ComputeCameraAngles 提供。
     */
    public static void tick(int tickCount, float partialTick) {
        float dt = computeDeltaTime(tickCount, partialTick);
        rotationDamper.tick(dt);
        positionDamper.tick(dt);
        vehicleChaseDamper.tick(dt);
    }

    // ===== 压制效果（System C） =====

    /**
     * 在投射物命中时同步触发失色压制效果。
     * 载具内压制效果应弱化（乘员受装甲保护）。
     */
    private static void triggerSuppression(float distanceFactor, float momentum) {
        float vehicleFactor = CameraController.isOnBoard() ? SUPPRESSION_VEHICLE_FACTOR : 1.0f;
        float suppression = distanceFactor * Math.min(momentum / MAX_REFERENCE_MOMENTUM, 1.0f)
                * SUPPRESSION_SCALE * vehicleFactor;
        // 累加到当前失色程度，上限 0.8
        float newLevel = Math.min(VisualEffectHelper.desaturationLevel + suppression, 0.8f);
        VisualEffectHelper.desaturationLevel = newLevel;
    }

    /**
     * 每 tick 衰减失色程度（在 PostProcessingManager.onClientTick 中调用，固定 20tps）。
     * 使用指数衰减，0.95^20 ≈ 0.358，约 1 秒内恢复到 36%。
     */
    public static void tickSuppression() {
        if (VisualEffectHelper.desaturationLevel > 0.001f) {
            VisualEffectHelper.desaturationLevel *= 0.95f;
        } else {
            VisualEffectHelper.desaturationLevel = 0f;
        }
    }

    // ===== 离开载具清理 =====

    /** 离开载具时调用，清空载具惯性追赶状态，防止下次上车瞬跳 */
    public static void onLeaveVehicle() {
        vehicleChaseDamper.reset();
    }

    // ===== Getter =====

    public static Vec3 getRotationOffset()    { return rotationDamper.getOffset(); }
    public static Vec3 getPositionOffset()     { return positionDamper.getOffset(); }
    public static Vec3 getVehicleChaseOffset() { return vehicleChaseDamper.getOffset(); }
    public static float getFpVehicleScale()   { return FP_VEHICLE_SCALE; }
}
