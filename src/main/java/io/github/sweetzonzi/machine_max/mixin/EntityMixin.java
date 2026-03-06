package io.github.sweetzonzi.machine_max.mixin;

import cn.solarmoon.spark_core.api.SparkLevel;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.body.CollisionGroups;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.PhysicsSweepTestResult;
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.math.Transform;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractControllableSubsystem;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import io.github.sweetzonzi.machine_max.util.MMMath;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

@Mixin(Entity.class)
abstract public class EntityMixin extends AttachmentHolder implements IEntityMixin {
    @Shadow
    public abstract Level level();

    @Shadow
    private boolean onGround;
    @Shadow
    public boolean verticalCollision;
    @Shadow
    public boolean verticalCollisionBelow;

    @Shadow
    protected abstract void checkSupportingBlock(boolean onGround, @Nullable Vec3 movement);

    @Shadow
    public abstract void resetFallDistance();

    @Shadow
    public abstract void setDeltaMovement(Vec3 deltaMovement);

    @Unique
    private AbstractControllableSubsystem machine_Max$controllingSubSystem;
    @Unique
    private CapsuleCollisionShape machine_Max$collideTestShape = null;
    @Unique
    private final Transform machine_Max$sweepTestStart = new Transform();
    @Unique
    private final Transform machine_Max$sweepTestEnd = new Transform();
    @Unique
    private final Vec3 AXIS_YP = new Vec3(0, 1, 0);
    @Unique
    private boolean machine_Max$groundedByPhysicsBody = false;
    @Unique
    private Vec3 machine_Max$physicsGroundNormal = null;

    /**
     * 在调用 maybeBackOffFromEdge 之前修改 pos 变量
     */
    @ModifyArg(
            method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;maybeBackOffFromEdge(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/entity/MoverType;)Lnet/minecraft/world/phys/Vec3;"
            ),
            index = 0
    )
    private Vec3 modifyMovePosBeforeEdgeBackoff(Vec3 originalPos, MoverType moverType) {
        // 获取原版碰撞结果
        if (originalPos.lengthSqr() < 1e-6f) {
            return originalPos; // 无运动时直接返回
        }
        Entity entity = (Entity) (Object) this;
        AABB aabb = entity.getBoundingBox();
        // 跳过非必要的检测对象
        if (entity instanceof BlockAttachedEntity
                || entity instanceof MMPartEntity
                || (aabb.maxX - aabb.minX) * (aabb.maxY - aabb.minY) * (aabb.maxZ - aabb.minZ) < 0.001
                || (entity instanceof Player player && !player.isAffectedByFluids()) // 飞行模式无碰撞
                || ((IEntityMixin) entity).machine_Max$getControllingSubsystem() != null) {
            return originalPos;
        }
        // 调用物理引擎进行碰撞检测
        if (machine_Max$collideTestShape == null) {
            double x = (aabb.maxX - aabb.minX) / 2;
            double z = (aabb.maxZ - aabb.minZ) / 2;
            float radius = (float) Math.sqrt(x * x + z * z);
            float height = (float) (aabb.maxY - aabb.minY - radius * 2);
            machine_Max$collideTestShape = new CapsuleCollisionShape(radius, height > 0 ? height : 0.01f);
        }
        List<PhysicsSweepTestResult> results = new ArrayList<>();
        Vec3 delta = new Vec3(originalPos.x, originalPos.y, originalPos.z);
        Vec3 center = aabb.getCenter();
        double len = delta.length();
        if (len > 1e-6 && len < 0.5)
            delta = delta.normalize();
        else if (len < 1e-6) { // 无运动直接返回
            return originalPos;
        }
        machine_Max$sweepTestStart.setTranslation(PhysicsHelperKt.toBVector3f(center));
        machine_Max$sweepTestEnd.setTranslation(PhysicsHelperKt.toBVector3f(center.add(delta)));
        SparkLevel.getPhysicsLevel(entity.level()).getWorld().getWorldSnapshot().sweepTest(
                machine_Max$collideTestShape,
                machine_Max$sweepTestStart,
                machine_Max$sweepTestEnd, results, 0.05f);
        if (results.isEmpty()) {
            return originalPos;// 无碰撞结果时直接返回
        }
        // === 多刚体顺序约束处理 ===
        Vec3 finalVec = originalPos;
        Vec3 groundNormal = null;
        double minGroundAngle = 90.0;
        Vec3 movement = Vec3.ZERO;

        for (PhysicsSweepTestResult result : results) {
            PhysicsCollisionObject pco = result.getCollisionObject();
            int group = pco.getCollisionGroup();
            if (group != CollisionGroups.PHYSICS_BODY) continue;

            if (!(PhysicsBodyExtensionKt.getOwner(pco) instanceof SubPart subPart)) continue;
            if (subPart.part.getAssemblingProgress() <= 0) continue; // 未组装时不检测碰撞

            Vec3 normal = SparkMathKt.toVec3(result.getHitNormalLocal(null)).normalize();

            // 当前运动方向未朝向该碰撞面，忽略
            double dot = finalVec.dot(normal);
            if (dot > 0) continue;

            // === 顺序投影：移除法线方向分量 ===
            Vec3 normalComponent = normal.scale(dot);
            finalVec = finalVec.subtract(normalComponent);

            // === 记录最接近“地面”的法线用于 grounded 判定 ===
            double angle = Math.acos(
                    Math.clamp(normal.dot(AXIS_YP), -1, 1)
            ) * 180 / Math.PI;

            if (angle < minGroundAngle) {
                minGroundAngle = angle;
                groundNormal = normal;
            }

            // === 记录最近一次接触刚体的运动，用于速度叠加 ===
            movement = SparkMathKt.toVec3(
                    MMMath.worldPointWorldVel(
                            PhysicsHelperKt.toBVector3f(center),
                            subPart.body
                    )
            );
        }

        // === 无有效约束，直接返回原始运动 ===
        if (finalVec == originalPos) {
            return originalPos;
        }

        // === 爬坡 / 地面判定 ===
        if (groundNormal != null && minGroundAngle < 45.0) {
            this.machine_Max$groundedByPhysicsBody = true;
            this.machine_Max$physicsGroundNormal = groundNormal;

            if (originalPos.horizontalDistanceSqr() > 1e-6f) {
                double originalLen = originalPos.horizontalDistance();
                double finalLen = finalVec.horizontalDistance();
                if (finalLen > 1e-6f) {
                    finalVec = finalVec.scale(originalLen / finalLen);
                }
            } else {
                finalVec = Vec3.ZERO;
            }
        }

        // === 叠加刚体运动（使用最近约束的刚体） ===
        movement = movement.scale(0.05); // 速度转为单 tick 位移
        Vec3 deltaWithPart = finalVec.subtract(movement);
        finalVec = finalVec.subtract(deltaWithPart.scale(machine_Max$groundedByPhysicsBody ? 0.1 : 0.05)); // 摩擦使得双方接近同速
        entity.setDeltaMovement(finalVec); // 防止速度无限积累
        return finalVec;
    }


    /**
     * <p>补充 setOnGroundWithMovement 的地面语义判定，使物理刚体斜面能够被识别为“地面”</p>
     * <p>Supplement ground semantics for physics bodies, allowing entities to be considered on-ground
     * when standing on physics-driven surfaces.</p>
     */
    @Inject(
            method = "setOnGroundWithMovement",
            at = @At("HEAD"),
            cancellable = true
    )
    private void machine_Max$setOnGroundWithMovementByPhysics(
            boolean onGround,
            Vec3 movement,
            CallbackInfo ci
    ) {
        Entity entity = (Entity) (Object) this;

        // === 原版已经判定为地面时，完全交由原逻辑处理 ===
        if (onGround) {
            return;
        }

        // 跳过非必要的检测对象
        if (entity instanceof BlockAttachedEntity
                || entity instanceof MMPartEntity
                || (entity instanceof Player player && !player.isAffectedByFluids()) // 飞行模式无碰撞
                || ((IEntityMixin) entity).machine_Max$getControllingSubsystem() != null) {
            return;
        }

        // === 本 tick 未记录物理刚体地面接触，直接回退原版 ===
        if (!this.machine_Max$groundedByPhysicsBody || this.machine_Max$physicsGroundNormal == null) {
            return;
        }

        /*
         * - 即使几何解算中未产生 verticalCollisionBelow
         * - 只要 collide 阶段确认是“可站立斜面”
         * - 即补充 onGround = true
         */
        this.onGround = true;
        this.verticalCollision = true;
        this.verticalCollisionBelow = true;

        // 调用原版支撑方块检测逻辑
        this.checkSupportingBlock(true, movement);

        // 接触物理地面时，视为安全着地，重置跌落距离
        this.resetFallDistance();

        // === 本 tick 状态用完即清 ===
        this.machine_Max$groundedByPhysicsBody = false;
        this.machine_Max$physicsGroundNormal = null;

        // 阻止原版逻辑再次覆盖 onGround
        ci.cancel();
    }


    @Nullable
    @Override
    public AbstractControllableSubsystem machine_Max$getControllingSubsystem() {
        return machine_Max$controllingSubSystem;
    }

    @Override
    public void machine_Max$setControllingSubsystem(AbstractControllableSubsystem subSystem) {
        this.machine_Max$controllingSubSystem = subSystem;
    }

}
