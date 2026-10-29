package io.github.sweetzonzi.machine_max.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import io.github.sweetzonzi.machine_max.common.mech.ray.ProjectileHitCoverage;
import io.github.sweetzonzi.machine_max.common.mech.ray.RayQueryContext;
import io.github.sweetzonzi.machine_max.common.mech.ray.SubPartRayHit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * 把原版实体射线查询中“部件候选”的求交替换为部件有效碰撞形状的精确求交。
 *
 * <p>只包装两条执行候选循环的 {@code getEntityHitResult} 重载；委托重载
 * （默认膨胀量 0.3）通过转调自动获得同样的判定，不重复注入。候选枚举、原版谓词、
 * 距离比较与乘客规则仍由原版循环负责：</p>
 *
 * <ul>
 *   <li>{@code AABB.clip(Vec3, Vec3)}：普通实体走 {@code original.call}；部件走共享查询，
 *       返回本部件最近有效形状的真实命中点；</li>
 *   <li>{@code AABB.contains(Vec3)}：普通实体走 {@code original.call}；部件返回 false，
 *       禁用“位于整个部件 AABB 内就距离 0”的快捷路径；</li>
 *   <li>float 重载在原版返回 {@code new EntityHitResult(entity)} 时改成为优胜部件返回
 *       {@code new EntityHitResult(entity, precisePoint)}。</li>
 * </ul>
 *
 * <p>共享射线查询由 {@link RayQueryContext} 用 {@code @Share} 保存在本次方法调用内：
 * 首次遇到部件候选时向只读物理快照发射一次射线并解析，其余候选只查索引；不放在静态可变
 * 字段里，客户端与服务端调用各自隔离。返回给投射物的结果与该上下文按实例身份关联，
 * 供实际命中事件做同输入复核。</p>
 */
@Mixin(ProjectileUtil.class)
public abstract class ProjectileUtilMixin {

    /** {@code getEntityHitResult(Entity, Vec3, Vec3, AABB, Predicate, double)}。 */
    @Unique
    private static final String DOUBLE_TARGET =
            "getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;"
                    + "Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;";

    /** {@code getEntityHitResult(Level, Entity, Vec3, Vec3, AABB, Predicate, float)}。 */
    @Unique
    private static final String FLOAT_TARGET =
            "getEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;"
                    + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;"
                    + "Ljava/util/function/Predicate;F)Lnet/minecraft/world/phys/EntityHitResult;";

    /* ==================== double 重载 ==================== */

    @Inject(method = DOUBLE_TARGET, at = @At("HEAD"))
    private static void machine_max$beginDoubleQuery(
            Entity shooter, Vec3 startVec, Vec3 endVec, AABB boundingBox, Predicate<Entity> filter, double distance, CallbackInfoReturnable<EntityHitResult> cir, @Share("query") LocalRef<RayQueryContext> query) {
        query.set(RayQueryContext.begin(shooter.level(), shooter, startVec, endVec, filter));
    }

    @WrapOperation(
            method = DOUBLE_TARGET,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"
            )
    )
    private static AABB machine_max$captureDoubleCandidate(Entity instance, Operation<AABB> original,
                                                           @Share("candidate") LocalRef<Entity> candidate) {
        candidate.set(instance);
        return original.call(instance);
    }

    @WrapOperation(
            method = DOUBLE_TARGET,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/AABB;clip(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Ljava/util/Optional;"
            )
    )
    private static Optional<Vec3> machine_max$clipDoubleCandidate(
            AABB box, Vec3 from, Vec3 to, Operation<Optional<Vec3>> original,
            @Share("candidate") LocalRef<Entity> candidateRef,
            @Share("query") LocalRef<RayQueryContext> queryRef) {
        Entity candidate = candidateRef.get();
        if (candidate instanceof MMPartEntity part && part.subPart != null) {
            RayQueryContext query = queryRef.get();
            SubPartRayHit hit = query == null ? null : query.nearestSubPartHit(part.subPart);
            // 部件不使用 AABB 宽容量；没有精确数据时不退回整块 AABB 精选
            return hit == null ? Optional.empty() : Optional.of(hit.point());
        }
        return original.call(box, from, to);
    }

    @WrapOperation(
            method = DOUBLE_TARGET,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/AABB;contains(Lnet/minecraft/world/phys/Vec3;)Z"
            )
    )
    private static boolean machine_max$disablePartContainShortcut(
            AABB box, Vec3 point, Operation<Boolean> original,
            @Share("candidate") LocalRef<Entity> candidateRef) {
        if (candidateRef.get() instanceof MMPartEntity) return false;
        return original.call(box, point);
    }

    @Inject(method = DOUBLE_TARGET, at = @At("RETURN"))
    private static void machine_max$endDoubleQuery(
            Entity shooter, Vec3 startVec, Vec3 endVec, AABB searchBox,
            Predicate<Entity> filter, double distance,
            CallbackInfoReturnable<EntityHitResult> cir,
            @Share("query") LocalRef<RayQueryContext> queryRef) {
        RayQueryContext query = queryRef.get();
        if (query == null) return;
        ProjectileHitCoverage.markCovered(query.source());
        EntityHitResult result = cir.getReturnValue();
        if (result != null && result.getEntity() instanceof MMPartEntity) query.rememberResult(result);
    }

    /* ==================== float 重载 ==================== */

    @Inject(method = FLOAT_TARGET, at = @At("HEAD"))
    private static void machine_max$beginFloatQuery(
            Level level, Entity projectile, Vec3 startVec, Vec3 endVec, AABB boundingBox, Predicate<Entity> filter, float inflationAmount, CallbackInfoReturnable<EntityHitResult> cir, @Share("query") LocalRef<RayQueryContext> query) {
        query.set(RayQueryContext.begin(level, projectile, startVec, endVec, filter));
    }

    @WrapOperation(
            method = FLOAT_TARGET,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"
            )
    )
    private static AABB machine_max$captureFloatCandidate(Entity instance, Operation<AABB> original,
                                                          @Share("candidate") LocalRef<Entity> candidate) {
        candidate.set(instance);
        return original.call(instance);
    }

    @WrapOperation(
            method = FLOAT_TARGET,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/AABB;clip(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Ljava/util/Optional;"
            )
    )
    private static Optional<Vec3> machine_max$clipFloatCandidate(
            AABB box, Vec3 from, Vec3 to, Operation<Optional<Vec3>> original,
            @Share("candidate") LocalRef<Entity> candidateRef,
            @Share("query") LocalRef<RayQueryContext> queryRef) {
        Entity candidate = candidateRef.get();
        if (candidate instanceof MMPartEntity part && part.subPart != null) {
            RayQueryContext query = queryRef.get();
            SubPartRayHit hit = query == null ? null : query.nearestSubPartHit(part.subPart);
            // 该重载传入的膨胀量只适用于普通实体的原版 AABB；部件以真实表面为准
            return hit == null ? Optional.empty() : Optional.of(hit.point());
        }
        return original.call(box, from, to);
    }

    @Inject(method = FLOAT_TARGET, at = @At("RETURN"), cancellable = true)
    private static void machine_max$endFloatQuery(
            Level level, Entity projectile, Vec3 startVec, Vec3 endVec, AABB searchBox,
            Predicate<Entity> filter, float inflationAmount,
            CallbackInfoReturnable<EntityHitResult> cir,
            @Share("query") LocalRef<RayQueryContext> queryRef) {
        RayQueryContext query = queryRef.get();
        EntityHitResult result = cir.getReturnValue();
        if (result != null && query != null && result.getEntity() instanceof MMPartEntity part && part.subPart != null) {
            SubPartRayHit hit = query.nearestSubPartHit(part.subPart);
            if (hit != null) {
                // 原版该重载的返回位置是实体位置，这里换成优胜部件的真实表面命中点
                EntityHitResult precise = new EntityHitResult(part, hit.point());
                cir.setReturnValue(precise);
                result = precise;
            }
        }
        if (query != null) {
            ProjectileHitCoverage.markCovered(query.source());
            if (result != null && result.getEntity() instanceof MMPartEntity) query.rememberResult(result);
        }
    }
}
