package io.github.sweetzonzi.machine_max.client.render.renderable;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.JsonOps;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.parser.HTML;
import io.github.sweetzonzi.machine_max.common.visual.AuiHudStyle;
import io.github.sweetzonzi.machine_max.common.visual.HudElement;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuiHudPerspectiveTransformTest {
    private static final Path PACK = Path.of("src/main/resources/spark_modules/machine_max.builtin/machine_max");
    private AuiHudAnimatable hud;

    @AfterEach
    void disposeDocument() {
        if (hud != null) hud.dispose();
        Size.clearViewportOverride();
    }

    @Test
    void prototypeAmmoProjectsFullyInsideTheUpperRightAtBothWindowSizes() throws Exception {
        var elements = HudElement.CODEC.listOf().parse(JsonOps.INSTANCE,
                JsonParser.parseString(Files.readString(PACK.resolve("hud_layouts/seat_dashboard_aui.json"))))
                .getOrThrow();
        HudElement element = elements.stream()
                .filter(candidate -> candidate.style().getPath().equals("aui_weapon_status") && candidate.perspective())
                .findFirst().orElseThrow();
        AuiHudStyle style = AuiHudStyle.CODEC.codec().parse(JsonOps.INSTANCE,
                JsonParser.parseString(Files.readString(PACK.resolve("huds/aui_weapon_status.json"))))
                .getOrThrow();
        Path page = PACK.resolve("documents/weapon_hud_element/weapon_ammo.html");
        String css = Files.readString(page.resolveSibling("hud.css"));
        HTML.putTemple(style.page(), Files.readString(page)
                .replace("<link rel=\"stylesheet\" href=\"hud.css\">", "<style>" + css + "</style>"));
        Size.setViewportOverride(1920, 1080);
        hud = new AuiHudAnimatable(element.style(), style, element);
        assertTrue(hud.isPerspective());
        Document document = Document.get(style.page()).getFirst();
        double[] dimensions = new double[2];
        Document.runWithContext(document, () -> {
            document.commitStyleRecalc();
            document.commitRenderState();
            dimensions[0] = document.body.getOffsetWidth();
            dimensions[1] = document.body.getOffsetHeight();
        });
        assertEquals(280d, dimensions[0], 0.01d);
        assertTrue(dimensions[1] > 80d && dimensions[1] < 200d);

        PoseStack pose = new PoseStack();
        Vector3f offset = hud.getOffset(0f);
        pose.translate(offset.x, -offset.y, offset.z);
        AuiHudPerspectiveTransform.apply(pose, hud.getQuaternion(0f),
                element.scale().x, element.scale().y, element.scale().z, dimensions[0], dimensions[1]);
        for (int[] window : new int[][]{{854, 480}, {1920, 1080}}) {
            Matrix4f projection = new Matrix4f().setPerspective((float) Math.toRadians(70d),
                    (float) window[0] / window[1], 0.1f, 1000f);
            for (double[] corner : new double[][]{{0, 0}, {dimensions[0], 0},
                    {dimensions[0], dimensions[1]}, {0, dimensions[1]}}) {
                Vector4f clip = new Vector4f((float) corner[0], (float) corner[1], 0f, 1f);
                pose.last().pose().transform(clip);
                projection.transform(clip);
                assertTrue(clip.w > 0f && clip.z > -clip.w && clip.z < clip.w);
                assertTrue(clip.x > 0f && clip.x < clip.w, "the ammo card must fit in the right half");
                assertTrue(clip.y > 0f && clip.y < clip.w, "the ammo card must fit in the upper half");
            }
        }
    }

    @Test
    void contentCenterAndAxesFollowModelScaleAndZyxRotation() {
        PoseStack pose = new PoseStack();
        pose.translate(95d, 60d, -135d);
        Quaternionf rotation = AuiHudPerspectiveTransform.rotation(90d, 90d, 90d);
        AuiHudPerspectiveTransform.apply(pose, rotation, 2d, 3d, 4d, 280d, 118d);

        Vector3f center = pose.last().pose().transformPosition(new Vector3f(140f, 59f, 0f));
        assertVector(new Vector3f(95f, 60f, -135f), center);
        Vector3f right = pose.last().pose().transformPosition(new Vector3f(141f, 59f, 0f)).sub(center);
        Vector3f down = pose.last().pose().transformPosition(new Vector3f(140f, 60f, 0f)).sub(center);
        Vector3f front = pose.last().pose().transformPosition(new Vector3f(140f, 59f, 1f)).sub(center);
        assertVector(new Vector3f(0f, 0f, -2f), right);
        assertVector(new Vector3f(0f, -3f, 0f), down);
        assertVector(new Vector3f(4f, 0f, 0f), front);
    }

    private static void assertVector(Vector3f expected, Vector3f actual) {
        assertEquals(expected.x, actual.x, 0.001f);
        assertEquals(expected.y, actual.y, 0.001f);
        assertEquals(expected.z, actual.z, 0.001f);
    }
}
