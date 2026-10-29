package io.github.sweetzonzi.machine_max.common.entity;

import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.ray.ProjectileHitCoverage;
import io.github.sweetzonzi.machine_max.common.mech.ray.RayQueryContext;
import io.github.sweetzonzi.machine_max.common.mech.ray.SubPartRayHit;
import io.github.sweetzonzi.machine_max.mixin_interface.IProjectileMixin;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * 投射物命中部件时的精确几何补全与单次分发守卫。
 *
 * <p>原版 {@code ProjectileUtil} 的统一求交路径负责选中部件；该路径返回的结果实例在
 * {@link RayQueryContext} 里关联了本次查询的起终点与谓词。实际命中事件发生时，这里用同一输入
 * 再解析一次，确认最近有效部件与事件目标一致后，才把命中点、法线、HitBox 与 SubPart 写入
 * {@link IProjectileMixin} 通道交给伤害管线。复核不命中或目标不一致时取消本次部件命中，
 * 不把伤害转交给另一个目标，也不重复施加伤害。</p>
 *
 * <p>补充查询只对“本 tick 没有进入统一求交、也没有产生原版命中事件”的投射物执行，
 * 使用本 tick 实际运动段并施加方块遮挡约束，避免与统一路径双重触发。</p>
 */
@EventBusSubscriber(modid = MachineMax.MOD_ID)
public class PartHitHandler {

    /**
     * 原版投射物命中事件：对部件目标做同输入复核并补全伤害几何。
     */
    @SubscribeEvent
    private static void onProjectileHit(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        ProjectileHitCoverage.markImpacted(projectile);
        if (!(event.getRayTraceResult() instanceof EntityHitResult entityHitResult)) return;
        if (!(entityHitResult.getEntity() instanceof MMPartEntity target)) return;
        if (target.subPart == null) {
            event.setCanceled(true);
            return;
        }

        RayQueryContext context = RayQueryContext.consume(entityHitResult);
        SubPartRayHit hit = context != null
                ? context.nearestSubPartHitMatching(context.filter())
                : recheckFromMotion(projectile);

        if (hit == null || hit.subPart() != target.subPart) {
            // 复核未命中或最近有效部件不是事件目标：拒绝本次部件命中，不重定向伤害
            event.setCanceled(true);
            return;
        }
        applyHitGeometry(projectile, hit);
    }

    /**
     * 承接统一路径在本 tick 的运动段起点与覆盖状态。
     */
    @SubscribeEvent
    public static void onProjectileTick(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof Projectile projectile) {
            ProjectileHitCoverage.begin(projectile);
        }
    }

    /**
     * 补充命中路径：只对未进入统一求交、也未产生原版命中事件的投射物执行一次。
     */
    @SubscribeEvent
    public static void onProjectileTickPost(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Projectile projectile)) return;
        ProjectileHitCoverage.TickState state = ProjectileHitCoverage.end(projectile);
        if (state == null || projectile.isRemoved() || state.covered() || state.impacted()) return;

        Vec3 start = state.start();
        Vec3 end = projectile.position();
        if (start.distanceToSqr(end) < 1e-8) return;

        // 方块遮挡约束：被方块截短后的射线段才用于部件判定
        HitResult blockHit = projectile.level().clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, projectile));
        if (blockHit.getType() == HitResult.Type.BLOCK) end = blockHit.getLocation();
        if (start.distanceToSqr(end) < 1e-8) return;

        RayQueryContext context = RayQueryContext.begin(projectile.level(), projectile, start, end, null);
        SubPartRayHit hit = context.nearestSubPartHitMatching(PartHitHandler::isCandidatePart);
        if (hit == null) return;
        MMPartEntity target = hit.subPart().getEntity();
        if (target == null) return;

        applyHitGeometry(projectile, hit);
        ((IProjectileMixin) projectile).machine_Max$manualProjectileHit(new EntityHitResult(target, hit.point()));
    }

    /**
     * 没有统一查询上下文时的兜底复核：沿用投射物当前运动段，只做部件形状过滤与方块遮挡。
     */
    @Nullable
    private static SubPartRayHit recheckFromMotion(Projectile projectile) {
        Vec3 motion = projectile.getDeltaMovement();
        if (motion.lengthSqr() < 1e-8) return null;
        Vec3 direction = motion.normalize();
        double size = projectile.getBoundingBox().getSize();
        Vec3 start = projectile.position().subtract(motion.scale(1.5)).subtract(direction.scale(size));
        Vec3 end = projectile.position().add(motion.scale(1.5)).add(direction.scale(size));
        RayQueryContext context = RayQueryContext.begin(projectile.level(), projectile, start, end, null);
        return context.nearestSubPartHitMatching(PartHitHandler::isCandidatePart);
    }

    /**
     * 补充查询的部件候选条件：仍绑定部件的部件实体，且该实体允许被投射物命中。
     */
    private static boolean isCandidatePart(Entity entity) {
        return entity instanceof MMPartEntity part
                && part.subPart != null
                && entity.canBeHitByProjectile();
    }

    /**
     * 把复核得到的命中几何写入既有投射物伤害通道。
     */
    private static void applyHitGeometry(Projectile projectile, SubPartRayHit hit) {
        IProjectileMixin mixinProjectile = (IProjectileMixin) projectile;
        mixinProjectile.machine_Max$setHitPoint(PhysicsHelperKt.toBVector3f(hit.point()));
        mixinProjectile.machine_Max$setHitNormal(PhysicsHelperKt.toBVector3f(hit.normal()));
        mixinProjectile.machine_Max$setHitBox(hit.hitBox());
        mixinProjectile.machine_Max$setHitSubPart(hit.subPart());
    }
}
