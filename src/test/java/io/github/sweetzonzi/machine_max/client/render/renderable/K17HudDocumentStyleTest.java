package io.github.sweetzonzi.machine_max.client.render.renderable;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.parser.Color;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.style.Background;
import com.sighs.apricityui.style.Text;
import io.github.sweetzonzi.machine_max.common.visual.AuiHudStyle;
import io.github.sweetzonzi.machine_max.common.visual.HudAnchor;
import io.github.sweetzonzi.machine_max.common.visual.HudElement;
import io.github.sweetzonzi.machine_max.common.visual.ScopeBehavior;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class K17HudDocumentStyleTest {
    private static final Path PAGE_DIRECTORY = Path.of("src/main/resources/spark_modules/"
            + "Machine-Max_K17/machine_max/documents/k17_weapon_preview");
    private AuiHudAnimatable hud;

    @AfterEach
    void disposeDocument() {
        if (hud != null) hud.dispose();
        Size.clearViewportOverride();
    }

    @Test
    void bordersAndReloadFillsResolveToVisiblePhosphor() throws Exception {
        Document document = createHud("visible-decoration", Map.of());
        Document.runWithContext(document, () -> {
            commit(document);
            for (var leaf : document.querySelectorAll("span, b")) {
                assertEquals("unset", Text.of(leaf).fontFamily,
                        "HUD text must use the native Minecraft font rendering path");
            }
            for (var panel : document.querySelectorAll(".hud-panel")) {
                var border = Box.of(panel).getBorderLeftSide();
                assertTrue(border.size() > 0);
                assertEquals(0xffa4ff80, border.color().getValue());
            }
            for (String selector : new String[]{".station-id", ".belt-row", ".tube-row", ".chamber-note"}) {
                for (var node : document.querySelectorAll(selector)) {
                    assertEquals(0xffa4ff80, Box.of(node).getBorderTopSide().color().getValue(), selector);
                }
            }
            assertEquals(0xffa4ff80,
                    Box.of(document.querySelector(".identity")).getBorderBottomSide().color().getValue());
            assertEquals(0xffa4ff80,
                    new Color(Background.of(document.querySelector(".belt-row .row-fill")).color).getValue());
            var loading = Background.of(document.querySelector(".is-loading .row-fill")).gradient;
            assertTrue(loading != null && loading.repeating());
            assertTrue(loading.stops().stream().anyMatch(stop -> stop.color == 0xffa4ff80));
            var row = document.querySelector(".is-loading");
            var fill = document.querySelector(".is-loading .row-fill");
            assertTrue(fill.getOffsetHeight() > 0);
            assertEquals((row.getOffsetWidth() - Box.of(row).getBorderHorizontal()) * 0.58,
                    fill.getOffsetWidth(), 0.1);
        });
    }

    @Test
    void scanlinesCoverOnlyTheirPanelsAndKeepTheBodyContentSized() throws Exception {
        Document document = createHud("scanline-geometry", Map.of());
        Document.runWithContext(document, () -> {
            commit(document);
            var panels = document.querySelectorAll(".hud-panel");
            var masks = document.querySelectorAll(".scanlines");
            assertEquals(3, masks.size());
            assertEquals(430.0, document.body.getOffsetWidth(), 0.01);
            assertEquals(395.0, document.body.getOffsetHeight(), 0.01);
            assertEquals(0, new Color(Background.of(document.body).color).getValue() >>> 24);
            for (int i = 0; i < masks.size(); i++) {
                var mask = masks.get(i);
                var panel = panels.get(i);
                var gradient = Background.of(mask).gradient;
                assertTrue(gradient != null && gradient.repeating());
                assertEquals(4.0f, gradient.repeatLengthPx());
                assertEquals(0.18, Double.parseDouble(mask.getComputedStyle().opacity), 0.001);
                assertTrue(mask.getOffsetWidth() > panel.getOffsetWidth() * 0.95);
                assertTrue(mask.getOffsetHeight() > panel.getOffsetHeight() * 0.95);
            }
        });
    }

    @Test
    void authorDefaultsCanChangeTheWholePaletteAndDisableScanlines() throws Exception {
        Document document = createHud("amber-no-scanlines",
                Map.of("--phosphor", "#ffc16b", "--scanline-opacity", "0"));
        Document.runWithContext(document, () -> {
            commit(document);
            assertEquals(0xffffc16b,
                    Box.of(document.querySelector(".hud-panel")).getBorderLeftSide().color().getValue());
            assertEquals(0xffffc16b,
                    new Color(Background.of(document.querySelector(".belt-row .row-fill")).color).getValue());
            for (var mask : document.querySelectorAll(".scanlines")) {
                assertEquals(0.0, Double.parseDouble(mask.getComputedStyle().opacity), 0.001);
            }
        });
    }

    private static void commit(Document document) {
        document.commitStyleRecalc();
        document.commitRenderState();
    }

    private Document createHud(String caseName, Map<String, String> defaults) throws Exception {
        Size.setViewportOverride(1920, 1080);
        String css = Files.readString(PAGE_DIRECTORY.resolve("hud.css"));
        String html = Files.readString(PAGE_DIRECTORY.resolve("index.html"))
                .replace("<link rel=\"stylesheet\" href=\"hud.css\">", "<style>" + css + "</style>");
        String page = "test://machine-max-hud/k17-" + caseName + ".html";
        HTML.putTemple(page, html);
        HudElement element = new HudElement(ResourceLocation.fromNamespaceAndPath("machine_max", "k17_weapon_preview"),
                false, ScopeBehavior.SCREEN_FIXED, true, Vec3.ZERO, Vec3.ZERO,
                new Vec3(1, 1, 1), HudAnchor.BOTTOM_LEFT);
        hud = new AuiHudAnimatable(element.style(), new AuiHudStyle(page, defaults), element);
        return Document.get(page).getFirst();
    }
}
