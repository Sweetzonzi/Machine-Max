package io.github.sweetzonzi.machine_max.common.gametest;

import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.projectile.BallisticProjectile;
import io.github.sweetzonzi.machine_max.common.mech.projectile.type.BallisticProjectileType;
import io.github.sweetzonzi.machine_max.datagen.MMLanguageRegistry;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import io.github.sweetzonzi.machine_max.util.mechanic.ArmorUtil;
import io.github.sweetzonzi.machine_max.util.NonFiniteDiagnostics;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import javax.annotation.Nullable;
import java.util.List;

@GameTestHolder(MachineMax.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProjectileTerrainHitGameTest {
    @GameTest(template = "part_foundation", batch = "mmProjectileTerrain", timeoutTicks = 40)
    public static void bedrockStopsProjectilesWithoutPoisoningVelocity(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        float armor = ArmorUtil.getBlockArmor(helper.getLevel(), Blocks.BEDROCK.defaultBlockState(), pos);
        helper.assertTrue(Float.isFinite(armor) && armor > 0f, "Bedrock armor must be positive and finite");
        for (String id : List.of("50mm_pzgr39_apcbc", "30mm_3ubr6_ap")) {
            var type = type(helper, id);
            float speed = type.getBaseVelocity();
            var projectile = new BallisticProjectile(helper.getLevel(), type,
                    new Vector3f(), new Vector3f(0, 0, speed), null);
            var result = projectile.onTerrainHit(helper.getLevel(), pos, Blocks.BEDROCK.defaultBlockState(),
                    type.getBasePenetration(), speed, Vec3.atCenterOf(pos), new Vec3(0, 1, 0));
            helper.assertTrue(result.destroyed(), "Bedrock must stop " + id + "; result=" + result);
        }
        helper.succeed();
    }

    @GameTest(template = "part_foundation", batch = "mmProjectileTerrain", timeoutTicks = 40)
    public static void breakableTerrainKeepsFinitePenetrationSpeed(GameTestHelper helper) {
        var type = type(helper, "50mm_pzgr39_apcbc");
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        var velocity = new Vector3f(0, 0, 835);
        var projectile = new BallisticProjectile(helper.getLevel(), type, new Vector3f(), velocity, null);
        for (var block : List.of(Blocks.GLASS, Blocks.DIRT, Blocks.STONE, Blocks.OBSIDIAN)) {
            var state = block.defaultBlockState();
            float armor = ArmorUtil.getBlockArmor(helper.getLevel(), state, pos);
            var result = projectile.onTerrainHit(helper.getLevel(), pos, state,
                    55f, 835f, Vec3.atCenterOf(pos), new Vec3(0, 1, 0));
            if (55f > armor) {
                helper.assertTrue(!result.destroyed(), "Penetrable terrain must allow continued flight: " + block);
                float expected = (float) (835 * Math.sqrt(1 - Math.pow(armor / 55.0, 2 / 1.2f)));
                helper.assertTrue(NonFiniteDiagnostics.finite(result.newVelocity())
                                && Math.abs(result.newVelocity().length() - expected) < 0.01f,
                        "Penetration must retain the finite energy-law speed");
            } else {
                helper.assertTrue(result.destroyed(), "Insufficient penetration must stop the projectile");
            }
        }
        helper.succeed();
    }

    /**
     * 归属落点：伤害源的 causing entity 是弹丸 owner，direct entity 是没有代理实体时的 {@code null}。
     *
     * <p>三条：有 owner 时 {@code getEntity()} 返回射手；没有实体投影时 {@code getDirectEntity()} 为
     * {@code null}（不把射手冒充成直接载体）；无 owner 的弹丸两个位置都是 {@code null}，与归属落地前的
     * 行为一致。</p>
     */
    @GameTest(template = "part_foundation", batch = "mmProjectileTerrain", timeoutTicks = 40)
    public static void damageSourceCarriesTheShooterAsCausingEntity(GameTestHelper helper) {
        var type = type(helper, "50mm_pzgr39_apcbc");
        var shooter = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var victim = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var velocity = new Vector3f(0, 0, type.getBaseVelocity());
        var exts = new io.github.sweetzonzi.ballistics_framework.api.BFDamageExtensions();

        var owned = new BallisticProjectile(helper.getLevel(), type, new Vector3f(), velocity, shooter);
        var ctx = owned.buildHurtContext(helper.getLevel(), 10f, Vec3.ZERO, Vec3.ZERO, new Vec3(0, 1, 0), exts);
        helper.assertTrue(owned.getOwner() == shooter, "弹丸应记住发射时写入的归属");
        helper.assertTrue(ctx.source().getEntity() == shooter, "伤害源的 causing entity 应为射手");
        helper.assertTrue(ctx.source().getDirectEntity() == null, "没有实体投影时 direct entity 应为 null");
        helper.assertTrue(MMLanguageRegistry.DEATH_ATTACK_PROJECTILE_PLAYER.tag.equals(
                        deathMessageKey(ctx.source(), victim)),
                "有归属的击杀消息应走带施加者的键，实际 " + deathMessageKey(ctx.source(), victim));

        var ownerless = new BallisticProjectile(helper.getLevel(), type, new Vector3f(), velocity, null);
        var ownerlessCtx = ownerless.buildHurtContext(helper.getLevel(), 10f, Vec3.ZERO, Vec3.ZERO,
                new Vec3(0, 1, 0), exts);
        helper.assertTrue(ownerlessCtx.source().getEntity() == null, "无归属的弹丸不得凭空产生起因实体");
        helper.assertTrue(ownerlessCtx.source().getDirectEntity() == null, "无归属的弹丸同样没有直接载体");
        helper.assertTrue(MMLanguageRegistry.DEATH_ATTACK_PROJECTILE.tag.equals(
                        deathMessageKey(ownerlessCtx.source(), victim)),
                "无归属时交回原版基础键，实际 " + deathMessageKey(ownerlessCtx.source(), victim));

        // 只接管本项目三类伤害：其它类型即使带起因实体也保持原版措辞
        helper.assertTrue("death.attack.generic".equals(
                        deathMessageKey(helper.getLevel().damageSources().source(
                                net.minecraft.world.damagesource.DamageTypes.GENERIC, shooter), victim)),
                "其它伤害类型不得被接管");
        helper.succeed();
    }

    /** 死亡消息的翻译键；不是可翻译组件时返回 {@code null}。 */
    @Nullable
    private static String deathMessageKey(net.minecraft.world.damagesource.DamageSource source,
                                          net.minecraft.world.entity.LivingEntity victim) {
        return source.getLocalizedDeathMessage(victim).getContents()
                instanceof net.minecraft.network.chat.contents.TranslatableContents contents
                ? contents.getKey() : null;
    }

    private static BallisticProjectileType type(GameTestHelper helper, String id) {
        var type = (BallisticProjectileType) MMDynamicRes.SERVER_PROJECTILE_TYPES.get(
                ResourceLocation.parse("machine_max:" + id));
        helper.assertTrue(type != null, id + " must be loaded");
        return type;
    }
}
