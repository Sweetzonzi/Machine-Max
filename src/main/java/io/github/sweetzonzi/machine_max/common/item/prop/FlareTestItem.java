package io.github.sweetzonzi.machine_max.common.item.prop;

import cn.solarmoon.spark_core.api.SpreadingSoundHelper;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.projectile.MuzzleFlashLight;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import io.github.sweetzonzi.machine_max.common.mech.projectile.type.BallisticProjectileType;
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
 * 信号弹测试物品。
 * <p>
 * 开发调试用：右键沿视线打出一发固定的信号弹弹种（{@code machine_max:flare_illum}），弹种自己带
 * 低重力、长寿命与几十米的显式照射半径，因此这一发会在空中飘一段并照亮落点周围。音量与音效同样
 * 来自弹种的 {@code sounds.fire_sounds}（内容包里配的是原版烟花升空音），与发射器走同一条读取路径。
 * <p>
 * 它同时是"显式光源字段"（{@code tracer_light_radius} / {@code tracer_light_color} /
 * {@code tracer_light_enabled}）的最小验收载体：改弹种 JSON 即可改变光斑，不需要动代码。
 * 客户端分支还会按弹种的 {@code visual.muzzle_flash} 登记一次枪口火光，因此它同时也是枪口火光的
 * 无载具验收载体。
 */
public class FlareTestItem extends Item {

    /** 本物品固定发射的弹种；内容包未提供该弹种时右键无效并只报告一次 */
    private static final ResourceLocation FLARE_TYPE =
            ResourceLocation.parse("machine_max:flare_illum");

    /** 出膛位置相对眼睛的前移距离（m），与投射物测试物品同量级 */
    private static final double SPAWN_OFFSET_M = 1.0;
    /** 连发冷却（tick） */
    private static final int COOLDOWN_TICKS = 20;

    /** 弹种缺失只报告一次，避免每次右键都刷日志（主线程读写） */
    private static boolean missingTypeReported;

    public FlareTestItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        ProjectileType type = ProjectileType.get(level, FLARE_TYPE);
        if (!(type instanceof BallisticProjectileType flare)) {
            if (!missingTypeReported) {
                missingTypeReported = true;
                MachineMax.LOGGER.error("信号弹测试物品：内容包未提供弹种 {}，本物品不会发射任何东西", FLARE_TYPE);
            }
            return InteractionResultHolder.fail(stack);
        }

        // 沿视线出膛：方向由玩家给定，速度取弹种的参考速度
        Vec3 look = player.getLookAngle();
        Vec3 spawnPos = player.getEyePosition().add(look.scale(SPAWN_OFFSET_M));

        // 客户端只做表现：枪口火光按弹种参数由本机登记；投射物本身由服务端生成
        if (level.isClientSide) {
            MuzzleFlashLight.emit(level, spawnPos, flare);
            return InteractionResultHolder.success(stack);
        }

        Vector3f jmePos = PhysicsHelperKt.toBVector3f(spawnPos);
        Vector3f jmeVel = PhysicsHelperKt.toBVector3f(look).multLocal(flare.getBaseVelocity());
        flare.create(level, jmePos, jmeVel);

        SoundEvent fireSound = flare.getFireSounds().get("0.0");
        if (fireSound != null) {
            SpreadingSoundHelper.playSpreadingSound(
                    level, fireSound, SoundSource.PLAYERS,
                    new Vec3(jmePos.x, jmePos.y, jmePos.z),
                    Vec3.ZERO, 1.0f, 1.0f);
        }

        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        return InteractionResultHolder.success(stack);
    }
}
