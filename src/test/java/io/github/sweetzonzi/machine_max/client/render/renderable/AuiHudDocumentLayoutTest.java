package io.github.sweetzonzi.machine_max.client.render.renderable;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.parser.Color;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.style.Background;
import io.github.sweetzonzi.machine_max.common.visual.AuiHudStyle;
import io.github.sweetzonzi.machine_max.common.visual.HudAnchor;
import io.github.sweetzonzi.machine_max.common.visual.HudElement;
import io.github.sweetzonzi.machine_max.common.visual.ScopeBehavior;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuiHudDocumentLayoutTest {
    private static final Path PAGE_DIRECTORY = Path.of("src/main/resources/spark_modules/"
            + "machine_max.builtin/machine_max/documents/weapon_hud_element");
    private AuiHudAnimatable hud;

    @AfterEach
    void disposeDocument() {
        if (hud != null) hud.dispose();
        Size.clearViewportOverride();
    }

    @Test
    void reloadFillKeepsItsHeightAndSixtyTwoPercentWidth() throws Exception {
        Document document = createAmmoHud("reload-geometry");
        Document.runWithContext(document, () -> {
            document.commitStyleRecalc();
            document.commitRenderState();

            var track = document.querySelector(".reload-track");
            var fill = document.querySelector(".reload-track i");
            assertNotNull(track);
            assertNotNull(fill);
            assertEquals(260.0d, track.getOffsetWidth(), 0.01d);
            assertEquals(4.0d, track.getOffsetHeight(), 0.01d);
            assertEquals(track.getOffsetWidth() * 0.62d, fill.getOffsetWidth(), 0.01d);
            assertEquals(track.getOffsetHeight(), fill.getOffsetHeight(), 0.01d);
        });
    }

    @Test
    void resizingTheViewportPreservesTransparentContentSizedBody() throws Exception {
        Document document = createAmmoHud("viewport-resize");
        var body = document.body;
        assertTransparentCard(document);
        for (int[] viewport : new int[][]{{1280, 720}, {854, 480}, {2560, 1440}, {1920, 1080}}) {
            Size.setViewportOverride(viewport[0], viewport[1]);
            document.applyViewport(true);
            assertSame(body, document.body);
            assertTransparentCard(document);
        }
    }

    @Test
    void refreshingStylesPreservesTransparentBodyAndDarkAmmoCard() throws Exception {
        Document document = createAmmoHud("stylesheet-refresh");
        var body = document.body;
        document.refreshStyles();
        assertSame(body, document.body);
        assertTransparentCard(document);
        Document.runWithContext(document, () -> {
            Background card = Background.of(document.querySelector(".el-ammo"));
            assertTrue((new Color(card.color).getValue() >>> 24) > 0,
                    "the ammo card must retain its own dark background");
            assertTrue(card.getLayers().stream().anyMatch(layer -> layer.gradient != null));
        });
    }

    private void assertTransparentCard(Document document) {
        Document.runWithContext(document, () -> {
            document.commitStyleRecalc();
            document.commitRenderState();
            assertEquals(280.0d, document.body.getOffsetWidth(), 0.01d);
            assertTrue(document.body.getOffsetHeight() < 200.0d);
            assertEquals(0, new Color(Background.of(document.body).color).getValue() >>> 24,
                    "the body must not cover the Minecraft world after CSS cache rebuilding");
        });
    }

    private Document createAmmoHud(String caseName) throws Exception {
        Size.setViewportOverride(1920, 1080);
        String css = Files.readString(PAGE_DIRECTORY.resolve("hud.css"), StandardCharsets.UTF_8);
        String html = Files.readString(PAGE_DIRECTORY.resolve("weapon_ammo.html"), StandardCharsets.UTF_8)
                .replace("<link rel=\"stylesheet\" href=\"hud.css\">", "<style>" + css + "</style>");
        String page = "test://machine-max-hud/" + caseName + ".html";
        HTML.putTemple(page, html);
        HudElement element = new HudElement(ResourceLocation.fromNamespaceAndPath("machine_max", "test_ammo"),
                false, ScopeBehavior.SCREEN_FIXED, true, Vec3.ZERO, Vec3.ZERO,
                new Vec3(1, 1, 1), HudAnchor.CENTER);
        hud = new AuiHudAnimatable(element.style(), new AuiHudStyle(page, Map.of()), element);
        var documents = Document.get(page);
        assertEquals(1, documents.size());
        return documents.getFirst();
    }
}
