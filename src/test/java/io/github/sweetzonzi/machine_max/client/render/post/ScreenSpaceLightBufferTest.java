package io.github.sweetzonzi.machine_max.client.render.post;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScreenSpaceLightBufferTest {
    @Test
    void occlusionBudgetOrderUsesPriorityThenProjectedSizeAndKeepsPayloadsTogether() {
        ScreenSpaceLightBuffer buffer = buffer(4);
        submit(buffer, 20, 1, 1);
        submit(buffer, 10, 1, 2);
        submit(buffer, 10, 3, 3);
        submit(buffer, 15, 2, 4);
        buffer.endFrame();

        int[] priorities = {10, 10, 15, 20};
        float[] radii = {3, 1, 2, 1};
        int[] identities = {3, 2, 4, 1};
        for (int slot = 0; slot < 4; slot++) {
            ScreenSpaceLightBuffer reference = buffer(1);
            submit(reference, priorities[slot], radii[slot], identities[slot]);
            reference.endFrame();
            assertSlotEquals(reference, buffer, slot);
        }
    }

    @Test
    void equalKeysPreserveArrivalOrderAfterCapacityReplacement() {
        ScreenSpaceLightBuffer buffer = buffer(3);
        submit(buffer, 20, 1, 1);
        submit(buffer, 10, 2, 2);
        submit(buffer, 10, 2, 3);
        submit(buffer, 5, 1, 4); // 置换最弱来源，最终排序应为 4、2、3。
        buffer.endFrame();
        assertEquals(3, buffer.count());
        assertEquals(4, buffer.colorIntensity()[3]);
        assertEquals(2, buffer.colorIntensity()[7]);
        assertEquals(3, buffer.colorIntensity()[11]);
    }

    @Test
    void replacementIntoAnEarlierSlotDoesNotOvertakeEqualKeys() {
        ScreenSpaceLightBuffer buffer = buffer(3);
        submit(buffer, 20, 1, 1);
        submit(buffer, 10, 2, 2);
        submit(buffer, 10, 2, 3);
        submit(buffer, 10, 2, 4);
        buffer.endFrame();
        assertEquals(2, buffer.colorIntensity()[3]);
        assertEquals(3, buffer.colorIntensity()[7]);
        assertEquals(4, buffer.colorIntensity()[11]);
    }

    private static ScreenSpaceLightBuffer buffer(int capacity) {
        LightFrameContext frame = new LightFrameContext();
        frame.update(null, Vec3.ZERO, new Matrix4f(), new Matrix4f(), 1920, 1080, 900, 128, 0);
        ScreenSpaceLightBuffer buffer = new ScreenSpaceLightBuffer(capacity);
        buffer.beginFrame(frame, 1);
        return buffer;
    }

    private static void submit(ScreenSpaceLightBuffer buffer, int priority, float radius, int identity) {
        buffer.beginSource(new ScreenSpaceLightSource() {
            public ResourceLocation id() { return ResourceLocation.parse("machine_max:test_" + identity); }
            public int priority() { return priority; }
            public void collect(LightFrameContext frame, ScreenSpaceLightBuffer out) { }
        });
        ScreenSpaceLight light = new ScreenSpaceLight();
        light.setPoint(new Vector3f(0, 0, -20), radius,
                new Vector3f(identity * .1f, .2f, .3f), identity,
                identity % 2 == 0 ? ScreenSpaceLight.OcclusionMode.NONE : ScreenSpaceLight.OcclusionMode.SOURCE_DEPTH,
                identity * .05f, .5f);
        light.setMinDistance(.1f + identity * .1f);
        light.setGlow(identity * 10, identity * .2f);
        light.setGlare(identity * .3f);
        assertEquals(ScreenSpaceLightBuffer.SubmitResult.ACCEPTED, buffer.submit(light));
    }

    private static void assertSlotEquals(ScreenSpaceLightBuffer reference, ScreenSpaceLightBuffer actual, int slot) {
        float[][] expected = {reference.positionRadius(), reference.colorIntensity(), reference.sourceDepthTolerance(),
                reference.occlusion(), reference.minDistance(), reference.glow(), reference.glare()};
        float[][] received = {actual.positionRadius(), actual.colorIntensity(), actual.sourceDepthTolerance(),
                actual.occlusion(), actual.minDistance(), actual.glow(), actual.glare()};
        int[] widths = {4, 4, 1, 1, 1, 2, 1};
        for (int field = 0; field < widths.length; field++) for (int k = 0; k < widths[field]; k++) {
            assertEquals(expected[field][k], received[field][slot * widths[field] + k], "上传字段必须与排序后的光源保持配对");
        }
    }
}
