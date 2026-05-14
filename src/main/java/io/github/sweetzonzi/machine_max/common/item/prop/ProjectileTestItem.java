package io.github.sweetzonzi.machine_max.common.item.prop;

import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import io.github.sweetzonzi.machine_max.common.mech.projectile.PointProjectile;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import io.github.sweetzonzi.machine_max.common.mech.projectile.RigidProjectile;
import io.github.sweetzonzi.machine_max.common.registry.MMDataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class ProjectileTestItem extends Item {

    public ProjectileTestItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);

        ResourceLocation typeKey = stack.has(MMDataComponents.getPROJECTILE_TYPE())
            ? stack.get(MMDataComponents.getPROJECTILE_TYPE())
            : ResourceLocation.parse("machine_max:20mm_ap");
        ProjectileType type = ProjectileType.get(level, typeKey);
        if (type == null) return InteractionResultHolder.fail(stack);

        var look = player.getLookAngle();
        var spawnPos = player.getEyePosition().add(look.scale(1.5));
        var jmePos = PhysicsHelperKt.toBVector3f(spawnPos);
        var jmeVel = PhysicsHelperKt.toBVector3f(look).multLocal(type.getBaseVelocity());

        if ("point".equals(type.getType())) {
            new PointProjectile(level, type, jmePos, jmeVel);
        } else {
            new RigidProjectile(level, type, jmePos, jmeVel);
        }

        player.getCooldowns().addCooldown(this, 10);
        return InteractionResultHolder.success(stack);
    }
}
