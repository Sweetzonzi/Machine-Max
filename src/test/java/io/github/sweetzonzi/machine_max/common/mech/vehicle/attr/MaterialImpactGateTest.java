package io.github.sweetzonzi.machine_max.common.mech.vehicle.attr;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 材质撞击资格门（{@code physics.impact_min_speed}）的定义位置与合并规则。
 *
 * <p>该字段属于材质，不属于全局配置：JSON 缺省时取默认值，碰撞箱覆写只改它声明了的
 * 物理字段，未声明的字段（含撞击门）沿用基础材质。</p>
 */
class MaterialImpactGateTest {

    @Test
    void defaultPhysicsUsesTwoMetresPerSecond() {
        assertEquals(2.0f, MaterialAttr.PhysicsAttr.DEFAULT.impactMinSpeed(), 1e-6f);
    }

    @Test
    void codecReadsImpactMinSpeedFromMaterialJson() {
        var json = JsonParser.parseString("""
                { "physics": { "impact_min_speed": 3.0 } }
                """);
        MaterialAttr attr = MaterialAttr.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(3.0f, attr.physics().impactMinSpeed(), 1e-6f);
        // 同一对象里未声明的物理字段仍是默认值
        assertEquals(0.5f, (float) attr.physics().friction().x, 1e-6f);
        assertEquals(1.0f, attr.physics().blockDamageFactor(), 1e-6f);
    }

    @Test
    void codecFallsBackToDefaultWhenFieldIsAbsent() {
        var json = JsonParser.parseString("""
                { "physics": { "block_damage_factor": 0.4 } }
                """);
        MaterialAttr attr = MaterialAttr.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(2.0f, attr.physics().impactMinSpeed(), 1e-6f);
        assertEquals(0.4f, attr.physics().blockDamageFactor(), 1e-6f);
    }

    @Test
    void codecWritesImpactMinSpeed() {
        var json = JsonParser.parseString("""
                { "physics": { "impact_min_speed": 1.5 } }
                """);
        MaterialAttr attr = MaterialAttr.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        var encoded = MaterialAttr.CODEC.encodeStart(JsonOps.INSTANCE, attr)
                .getOrThrow().getAsJsonObject();
        assertEquals(1.5f,
                encoded.getAsJsonObject("physics").get("impact_min_speed").getAsFloat(), 1e-6f);
    }

    @Test
    void hitBoxOverwriteKeepsTheMaterialGate() {
        MaterialAttr base = new MaterialAttr(
                new MaterialAttr.PhysicsAttr(
                        new Vec3(0.5, 0.5, 0.5), 0.5f, MaterialAttr.PhysicsAttr.SlipCurveAttr.DEFAULT,
                        0.2f, 0f, 0.1f, 1.0f, 2.5f),
                MaterialAttr.ArmorAttr.DEFAULT,
                MaterialAttr.MaterialSoundAttr.DEFAULT);
        HitBoxAttr.OverwriteAttr overwrite = new HitBoxAttr.OverwriteAttr(
                Optional.of(new HitBoxAttr.PhysicsOverwriteAttr(
                        Optional.of(new Vec3(0.9, 0.9, 0.9)),
                        Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty(), Optional.empty())),
                Optional.empty(),
                Optional.empty());

        MaterialAttr merged = HitBoxAttr.mergeWithOverwrite(base, overwrite);

        assertEquals(2.5f, merged.physics().impactMinSpeed(), 1e-6f);
        assertEquals(0.9f, (float) merged.physics().friction().x, 1e-6f);
        assertEquals(0.1f, merged.physics().restitution(), 1e-6f);
    }

    @Test
    void hitBoxWithoutOverwriteUsesTheMaterialAsIs() {
        MaterialAttr base = new MaterialAttr(
                new MaterialAttr.PhysicsAttr(
                        new Vec3(0.5, 0.5, 0.5), 0.5f, MaterialAttr.PhysicsAttr.SlipCurveAttr.DEFAULT,
                        0.2f, 0f, 0.1f, 1.0f, 0.8f),
                MaterialAttr.ArmorAttr.DEFAULT,
                MaterialAttr.MaterialSoundAttr.DEFAULT);
        assertEquals(base, HitBoxAttr.mergeWithOverwrite(base, null));
    }
}
