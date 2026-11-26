package io.github.sweetzonzi.machine_max.client.render.renderer;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PartWearRendererTest {
    @Test
    void insertsStageSuffixBeforePngExtension() {
        var base = ResourceLocation.parse("machine_max:textures/part/mini_ev/mini_ev_ae86.png");
        assertEquals(ResourceLocation.parse("machine_max:textures/part/mini_ev/mini_ev_ae86_wear_01.png"),
                PartWearRenderer.getStageTexture(base, 1));
        assertEquals(ResourceLocation.parse("machine_max:textures/part/mini_ev/mini_ev_ae86_wear_16.png"),
                PartWearRenderer.getStageTexture(base, 16));
    }
}
