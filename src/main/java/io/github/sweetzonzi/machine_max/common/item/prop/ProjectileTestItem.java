package io.github.sweetzonzi.machine_max.common.item.prop;

import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.api.SpreadingSoundHelper;
import io.github.sweetzonzi.machine_max.common.mech.projectile.MuzzleFlashLight;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import io.github.sweetzonzi.machine_max.common.mech.projectile.type.BallisticProjectileType;
import io.github.sweetzonzi.machine_max.common.registry.MMDataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 投射物测试物品。
 * <p>
 * 阶段一开发调试用，右键发射指定类型的投射物。
 * 默认发射 {@code machine_max:20mm_ap}，可通过 DataComponent 切换弹种：
 * <pre>
 * /give @s machine_max:projectile_test[machine_max:projectile_type="machine_max:120mm_apfsds"]
 * </pre>
 * <p>
 * 客户端只做表现：按弹种的 {@code visual.muzzle_flash} 登记一次枪口火光，投射物本身由服务端生成。
 * 这条分支让它同时充当枪口火光的无载具验收载体——不必组装带发射器的载具也能看到光斑。
 * <p>
 * 后续集成至 {@code LauncherSubsystem} 后移除或改造。
 */
public class ProjectileTestItem extends Item {

    public ProjectileTestItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // 获取投射物类型，默认为 20mm AP
        ResourceLocation typeKey = stack.has(MMDataComponents.getPROJECTILE_TYPE())
            ? stack.get(MMDataComponents.getPROJECTILE_TYPE())
            : ResourceLocation.parse("machine_max:20mm_ap");
        ProjectileType type = ProjectileType.get(level, typeKey);
        // 本测试物品只发射飞行弹丸（Kinetic）
        if (!(type instanceof BallisticProjectileType kineticType)) return InteractionResultHolder.fail(stack);

        // 计算发射位置和初始速度
        var look = player.getLookAngle();
        var spawnPos = player.getEyePosition().add(look.scale(1.5));
        var jmePos = PhysicsHelperKt.toBVector3f(spawnPos);
        var jmeVel = PhysicsHelperKt.toBVector3f(look).multLocal(kineticType.getBaseVelocity());

        // 客户端只做表现：枪口火光按弹种参数由本机登记；投射物本身由服务端生成
        if (level.isClientSide) {
            MuzzleFlashLight.emit(level, spawnPos, kineticType);
            return InteractionResultHolder.success(stack);
        }

        // 创建投射物并加入世界
        kineticType.create(level, jmePos, jmeVel);

        // 播放单发开火音效
        SoundEvent fireSound = kineticType.getFireSounds().get("0.0");
        if (fireSound != null) {
            SpreadingSoundHelper.playSpreadingSound(
                level, fireSound, SoundSource.NEUTRAL,
                new Vec3(jmePos.x, jmePos.y, jmePos.z),
                Vec3.ZERO, 1.0f, 1.0f);
        }

        player.getCooldowns().addCooldown(this, 10);
        return InteractionResultHolder.success(stack);
    }
}
