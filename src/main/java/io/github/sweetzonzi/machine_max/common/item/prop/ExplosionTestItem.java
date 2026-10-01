package io.github.sweetzonzi.machine_max.common.item.prop;

import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.explosion.ExplosionManager;
import io.github.sweetzonzi.machine_max.common.mech.explosion.ExplosionParams;
import io.github.sweetzonzi.machine_max.common.registry.MMDamageTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 爆炸系统测试物品。
 * <p>
 * 阶段一开发调试用，右键在准星指向的位置（实体命中优先，否则取方块命中，都未命中则取射程末端）
 * 引爆一次参数硬编码的爆炸。
 * <p>
 * 所有可调参数都以局部常量的形式写在 {@link #use} 内，热重载后立即生效，无需改配置或指令。
 */
public class ExplosionTestItem extends Item {

    public ExplosionTestItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);

        // ==================== 硬编码测试参数 ====================
        final float NEAR_RADIUS = 3.0f;          // 参考距离 / 近场平台半径（m）
        final float MAX_RADIUS = 12.0f;          // 硬截断半径，射线推进终点（m）
        final float BASE_PENETRATION = 60f;     // 参考半径处无介质消耗的穿深（mm）
        final float BASE_DAMAGE = 20f;           // 参考半径处单位迎流面积被完全击穿的伤害
        final float BASE_IMPULSE = 2000f;         // 参考半径处单位迎流面积的冲量
        final float FRONT_SPEED = 20f;           // 波前推进速度（m/s）
        final boolean DESTROY_BLOCKS = true;     // 是否破坏地形
        final boolean DROP_ITEMS = false;        // 摧毁方块是否掉落
        final boolean CAUSES_FIRE = false;       // 是否点燃（首期强制 false）
        // 起爆粒子：与 12 m 的硬截断半径相称的中等特效
        final List<ResourceLocation> PARTICLES =
                List.of(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "blast_medium"));
        final float PARTICLE_SCALE = 1.0f;       // 粒子散布缩放（放大形状半径与飞散距离）
        final double AIM_RANGE = 64.0;           // 瞄准射线长度（m）
        // ======================================================

        // 瞄准点：比较实体命中与方块命中的距离，取较近者
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(AIM_RANGE));

        HitResult blockHit = player.pick(AIM_RANGE, 0.0f, false);
        double blockDist = blockHit.getType() == HitResult.Type.MISS
                ? AIM_RANGE
                : eye.distanceTo(blockHit.getLocation());

        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                player, eye, end,
                player.getBoundingBox().expandTowards(look.scale(AIM_RANGE)).inflate(1.0),
                e -> !e.isSpectator() && e.isPickable(),
                AIM_RANGE * AIM_RANGE);
        double entityDist = entityHit == null ? Double.MAX_VALUE : eye.distanceTo(entityHit.getLocation());

        Vec3 target;
        if (entityDist < blockDist) {
            target = entityHit.getLocation();
        } else if (blockHit.getType() == HitResult.Type.MISS) {
            target = end;
        } else {
            target = blockHit.getLocation();
        }

        ExplosionParams params = new ExplosionParams(
                BASE_PENETRATION, BASE_DAMAGE, BASE_IMPULSE,
                NEAR_RADIUS, MAX_RADIUS, FRONT_SPEED,
                DESTROY_BLOCKS, DROP_ITEMS, CAUSES_FIRE, PARTICLES, PARTICLE_SCALE);
        DamageSource source = level.damageSources().source(MMDamageTypes.BLAST);
        long seed = level.random.nextLong();

        ExplosionManager.get(level).detonate(
                new Vector3f((float) target.x, (float) target.y, (float) target.z),
                params, source, seed);

        player.getCooldowns().addCooldown(this, 20);
        return InteractionResultHolder.success(stack);
    }
}
