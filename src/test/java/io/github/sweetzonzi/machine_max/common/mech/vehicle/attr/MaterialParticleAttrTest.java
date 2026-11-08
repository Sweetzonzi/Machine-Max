package io.github.sweetzonzi.machine_max.common.mech.vehicle.attr;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 材质表现粒子的契约：键是表现用途（{@code hit_pen} / {@code hit_un_pen}），
 * 碰撞箱覆写按键叠加而不是整表替换。
 */
class MaterialParticleAttrTest {

    private static final ResourceLocation SPALL = ResourceLocation.parse("machine_max:armor_spall");
    private static final ResourceLocation DEFLECT = ResourceLocation.parse("machine_max:armor_deflect");
    private static final ResourceLocation SPECIAL = ResourceLocation.parse("machine_max:armor_spall_heavy");

    @Test
    void readsParticlesByUseKey() {
        MaterialAttr attr = MaterialAttr.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                        {"particles": {"hit_pen": "machine_max:armor_spall",
                                       "hit_un_pen": "machine_max:armor_deflect"}}
                        """))
                .getOrThrow();
        assertEquals(SPALL, attr.particles().hitPen());
        assertEquals(DEFLECT, attr.particles().hitUnPen());
    }

    /** 没写 particles 段的材质不出粒子，不做任何回退。 */
    @Test
    void materialWithoutParticlesHasNone() {
        MaterialAttr attr = MaterialAttr.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow();
        assertNull(attr.particles().hitPen());
        assertNull(attr.particles().hitUnPen());
    }

    @Test
    void hitBoxOverwriteReplacesOnlyTheKeysItNames() {
        MaterialAttr base = new MaterialAttr(
                MaterialAttr.PhysicsAttr.DEFAULT,
                MaterialAttr.ArmorAttr.DEFAULT,
                MaterialAttr.MaterialSoundAttr.DEFAULT,
                new MaterialAttr.MaterialParticleAttr(Map.of(
                        MaterialAttr.MaterialParticleAttr.HIT_PEN, SPALL,
                        MaterialAttr.MaterialParticleAttr.HIT_UN_PEN, DEFLECT)));
        HitBoxAttr.OverwriteAttr overwrite = new HitBoxAttr.OverwriteAttr(
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(new MaterialAttr.MaterialParticleAttr(
                        Map.of(MaterialAttr.MaterialParticleAttr.HIT_PEN, SPECIAL))));

        MaterialAttr merged = HitBoxAttr.mergeWithOverwrite(base, overwrite);
        assertEquals(SPECIAL, merged.particles().hitPen(), "碰撞箱写了的键被替换");
        assertEquals(DEFLECT, merged.particles().hitUnPen(), "没写的键继续继承材质");
    }
}
