package io.github.sweetzonzi.machine_max.part;

import cn.solarmoon.spark_core.particle.client.ParticleEffectDeserializer;
import cn.solarmoon.spark_core.particle.common.data.ParticleEffectDefinition;
import cn.solarmoon.spark_core.particle.common.data.component.lifetime.EmitterLifetimeEvents;
import cn.solarmoon.spark_core.particle.common.data.event.EventNode;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 命中火花的组合契约：击穿与未击穿两套火花都要把闪光与烟尘作为子发射器带出来，
 * 且子效果在命中点就地创建。
 *
 * <p>粒子 JSON 里拼错的组件键会被 Spark-Core 静默忽略，表现上就是"只有火花，没有闪光和烟尘"，
 * 因此这里直接解析随包发布的定义来固定这条组合。</p>
 */
class ParticleHitEffectCompositionTest {

    private static final Path PARTICLE_DIR =
            Path.of("src/main/resources/spark_modules/Machine-Max_Builtin/machine_max/particles");

    private static ParticleEffectDefinition load(String name) throws IOException {
        String text = Files.readString(PARTICLE_DIR.resolve(name + ".particle.json"), StandardCharsets.UTF_8);
        return ParticleEffectDeserializer.deserialize(
                ResourceLocation.fromNamespaceAndPath("machine_max", name), JsonParser.parseString(text));
    }

    @Test
    void hitSparksSpawnFlashAndDustAtTheHitPoint() throws IOException {
        for (String effect : List.of("armor_spall", "armor_deflect")) {
            ParticleEffectDefinition def = load(effect);

            EmitterLifetimeEvents lifetime = def.getEmitterPreset().findLifetimeEvents();
            assertNotNull(lifetime, effect + " 缺少 emitter_lifetime_events");
            assertEquals("hit", lifetime.getTimeline().get(0f), effect + " 没有在 0 秒触发 hit 事件");

            List<EventNode> events = def.getEvents().get("hit");
            assertNotNull(events, effect + " 缺少 hit 事件");
            List<String> spawned = new ArrayList<>();
            for (EventNode node : events) {
                if (node instanceof EventNode.ParticleEffectEvent child) {
                    assertEquals(EventNode.ParticleEffectType.EMITTER, child.getParticleEffectType(),
                            effect + " 的子效果应在命中点就地创建，不跟粒子走");
                    spawned.add(child.getEffect());
                }
            }
            assertEquals(List.of("machine_max:metal_hit_flash", "machine_max:metal_dust"), spawned, effect);
        }
    }

    /** 两个子效果自己也要能解析：它们是独立的粒子定义文件。 */
    @Test
    void childEffectsParse() throws IOException {
        assertNotNull(load("metal_hit_flash").getParticlePreset());
        assertNotNull(load("metal_dust").getParticlePreset());
    }
}
