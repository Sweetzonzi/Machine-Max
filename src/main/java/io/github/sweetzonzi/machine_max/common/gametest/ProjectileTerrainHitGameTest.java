package io.github.sweetzonzi.machine_max.common.gametest;

import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.projectile.BallisticProjectile;
import io.github.sweetzonzi.machine_max.common.mech.projectile.type.BallisticProjectileType;
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

import java.util.List;

@GameTestHolder(MachineMax.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProjectileTerrainHitGameTest {
    @GameTest(template = "part_foundation", batch = "mmProjectileTerrain", timeoutTicks = 40)
    public static void bedrockStopsProjectilesWithoutPoisoningVelocity(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        float armor = ArmorUtil.getBlockArmor(helper.getLevel(), Blocks.BEDROCK.defaultBlockState(), pos);
        helper.assertTrue(Float.isFinite(armor) && armor > 0f, "Bedrock armor must be positive and finite");
        for (String id : List.of("50mm_pzgr39", "30mm_ap")) {
            var type = type(helper, id);
            float speed = type.getBaseVelocity();
            var projectile = new BallisticProjectile(helper.getLevel(), type,
                    new Vector3f(), new Vector3f(0, 0, speed));
            var result = projectile.onTerrainHit(helper.getLevel(), pos, Blocks.BEDROCK.defaultBlockState(),
                    type.getBasePenetration(), speed, Vec3.atCenterOf(pos), new Vec3(0, 1, 0));
            helper.assertTrue(result.destroyed(), "Bedrock must stop " + id + "; result=" + result);
        }
        helper.succeed();
    }

    @GameTest(template = "part_foundation", batch = "mmProjectileTerrain", timeoutTicks = 40)
    public static void breakableTerrainKeepsFinitePenetrationSpeed(GameTestHelper helper) {
        var type = type(helper, "50mm_pzgr39");
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        var velocity = new Vector3f(0, 0, 835);
        var projectile = new BallisticProjectile(helper.getLevel(), type, new Vector3f(), velocity);
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

    private static BallisticProjectileType type(GameTestHelper helper, String id) {
        var type = (BallisticProjectileType) MMDynamicRes.SERVER_PROJECTILE_TYPES.get(
                ResourceLocation.parse("machine_max:" + id));
        helper.assertTrue(type != null, id + " must be loaded");
        return type;
    }
}
