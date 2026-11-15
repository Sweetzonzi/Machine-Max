package io.github.sweetzonzi.machine_max.client.render.post;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 游戏内截取与独立回放共用的诊断参数。负的shadowRange表示沿用输入帧的范围。 */
record ScreenSpaceLightDiagnosticPreset(String name, int rayBudget, int pixelBudget, int samples, float thickness,
                                       boolean legacy, boolean mask, int traversal, boolean normalThickness,
                                       boolean occlusionOff, int onlyLight, float endExclusion, float shadowRange) {
    Map<String, Float> uniforms(float frameRange) {
        Map<String, Float> values = new LinkedHashMap<>();
        values.put("ShadowRange", shadowRange < 0 ? frameRange : shadowRange);
        values.put("CaptureRayBudget", (float) rayBudget);
        values.put("CapturePixelBudget", (float) pixelBudget);
        values.put("CaptureSamples", (float) samples);
        values.put("CaptureThickness", thickness);
        values.put("CaptureLegacy", legacy ? 1f : 0f);
        values.put("CaptureTraversal", (float) traversal);
        values.put("CaptureNormalThickness", normalThickness ? 1f : 0f);
        values.put("CaptureOcclusionOff", occlusionOff ? 1f : 0f);
        values.put("CaptureOnlyLight", (float) onlyLight);
        values.put("CaptureEndExclusion", endExclusion);
        return values;
    }

    static List<ScreenSpaceLightDiagnosticPreset> forFrame(int width, int height, int lights) {
        int full = width + height + 2;
        List<ScreenSpaceLightDiagnosticPreset> result = new ArrayList<>(List.of(
                new ScreenSpaceLightDiagnosticPreset("01_current", 0, 0, 0, 0, false, true, 0, false, false, 0, 0, -1),
                new ScreenSpaceLightDiagnosticPreset("02_dda512", 512, 512, 0, 0, false, false, 1, false, false, 0, 0, -1),
                new ScreenSpaceLightDiagnosticPreset("03_uniform64", 64, 64 * lights, 64, 0, false, true, 1, false, false, 0, 0, -1),
                new ScreenSpaceLightDiagnosticPreset("04_uniform128", 128, 128 * lights, 128, 0, false, false, 1, false, false, 0, 0, -1),
                new ScreenSpaceLightDiagnosticPreset("05_full", full, full * lights, 0, 0, false, true, 2, false, false, 0, 0, -1),
                new ScreenSpaceLightDiagnosticPreset("06_full_thickness1m", full, full * lights, 0, 1, false, false, 2, false, false, 0, 0, -1),
                new ScreenSpaceLightDiagnosticPreset("07_legacy7", 0, 0, 0, 0, true, false, 1, false, false, 0, 0, -1),
                new ScreenSpaceLightDiagnosticPreset("08_hierarchy128", 128, 128, 0, 0, false, true, 0, false, false, 0, 0, -1),
                new ScreenSpaceLightDiagnosticPreset("09_hierarchy_full", full * 32, full * 32 * lights, 0, 0, false, true, 0, false, false, 0, 0, -1),
                new ScreenSpaceLightDiagnosticPreset("10_normal_thickness", full, full * lights, 0, 0, false, true, 2, true, false, 0, 0, -1),
                new ScreenSpaceLightDiagnosticPreset("11_without_surface_shadow", 0, 0, 0, 0, false, false, 0, false, true, 0, 0, -1),
                new ScreenSpaceLightDiagnosticPreset("13_end05", 0, 0, 0, 0, false, true, 0, false, false, 0, .5f, -1),
                new ScreenSpaceLightDiagnosticPreset("14_end10", 0, 0, 0, 0, false, true, 0, false, false, 0, 1f, -1),
                new ScreenSpaceLightDiagnosticPreset("15_range8", 0, 0, 0, 0, false, false, 0, false, false, 0, 0, 8),
                new ScreenSpaceLightDiagnosticPreset("16_range16", 0, 0, 0, 0, false, false, 0, false, false, 0, 0, 16),
                new ScreenSpaceLightDiagnosticPreset("17_range64", 0, 0, 0, 0, false, false, 0, false, false, 0, 0, 64)));
        for (int i = 0; i < lights; i++) {
            result.add(new ScreenSpaceLightDiagnosticPreset("12_light" + i, 0, 0, 0, 0, false, true, 0, false, false, i + 1, 0, -1));
        }
        return result;
    }
}
