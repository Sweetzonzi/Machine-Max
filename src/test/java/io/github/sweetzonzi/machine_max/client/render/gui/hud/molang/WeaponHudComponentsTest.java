package io.github.sweetzonzi.machine_max.client.render.gui.hud.molang;

import com.google.gson.JsonParser;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.style.Text;
import io.github.sweetzonzi.machine_max.client.render.renderable.AuiHudAnimatable;
import io.github.sweetzonzi.machine_max.common.visual.AuiHudStyle;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WeaponHudComponentsTest {
    private static final Path PACKS = Path.of("src/main/resources/spark_modules");
    private static final Path BUILTIN = PACKS.resolve("machine_max.builtin/machine_max");
    private static final Path SDKFZ = PACKS.resolve("sdkfz/machine_max");
    private final List<AuiHudAnimatable> instances = new ArrayList<>();
    private record Page(WeaponHudElement runtime, AuiHudAnimatable hud, Document document) {
        void publish(HudWeaponSnapshot snapshot, boolean selected) {
            runtime.publish(HudPartSnapshot.EMPTY, snapshot, selected);
            hud.tickBindings();
            Document.runWithContext(document, () -> {
                document.commitStyleRecalc();
                document.commitRenderState();
            });
        }
    }

    @AfterEach void close() {
        instances.forEach(AuiHudAnimatable::dispose);
        Size.clearViewportOverride();
    }

    private Page create(String kind) throws Exception {
        return create(kind, Map.of());
    }

    private Page create(String kind, Map<String, String> defaults) throws Exception {
        Size.setViewportOverride(1920, 1080);
        var directory = BUILTIN.resolve("documents/weapon_hud_components");
        String css = Files.readString(directory.resolve("hud.css"));
        String html = Files.readString(directory.resolve(kind + ".html"))
                .replace("<link rel=\"stylesheet\" href=\"hud.css\">", "<style>" + css + "</style>");
        String path = "test://components/" + UUID.randomUUID() + ".html";
        HTML.putTemple(path, html);
        var element = HudMolangContextTest.element();
        var runtime = new WeaponHudElement(null, element);
        var hud = new AuiHudAnimatable(element.style(), new AuiHudStyle(path, defaults), element, runtime);
        instances.add(hud);
        return new Page(runtime, hud, Document.get(path).getFirst());
    }

    private static HudWeaponSnapshot.Belt belt(String key, int remaining, boolean selected) {
        return new HudWeaponSnapshot.Belt(List.of(ResourceLocation.parse("test:" + key.toLowerCase(Locale.ROOT).replace('/', '_'))), key, remaining, selected);
    }

    private static HudWeaponSnapshot.Tube tube(int key, boolean ready, double fill) {
        var base = HudMolangContextTest.tube(key, fill);
        return new HudWeaponSnapshot.Tube(base.key(), ready ? "AP" : "HE", ready ? "ready" : "reloading",
                ready ? 100 : fill, ready ? 0 : 2, ready ? "" : "2.0s", ready ? "AP" : "", ready ? "" : "HE");
    }

    private static HudWeaponSnapshot snapshot(int remaining, boolean mismatch,
                                               List<HudWeaponSnapshot.Tube> tubes,
                                               List<HudWeaponSnapshot.Belt> belts) {
        return new HudWeaponSnapshot("Test weapon", tubes.stream().anyMatch(t -> t.state().equals("reloading"))
                ? "reloading" : "ready", remaining, 30, false, remaining + "/30", "AP",
                mismatch, "Ready", "test:ap", tubes, belts);
    }

    private static void assertNoBindingErrors(Document document) {
        assertFalse(document.body.getTextContent().contains("MOLANG_ERROR"));
        assertTrue(document.querySelectorAll("[data-mm-repeat]").isEmpty());
    }

    @Test void feedKeepsChamberAndLoadingSourceSeparateFromSelectedBelt() throws Exception {
        var page = create("feed");
        page.publish(snapshot(8, true, List.of(tube(1, true, 100)),
                List.of(belt("ap", 12, false), belt("he", 8, true))), true);
        var document = page.document();
        var originalTube = document.querySelector(".tube-row");
        var originalBelt = document.querySelector(".belt-row");
        assertEquals("AP", document.querySelector(".round-label").getTextContent());
        assertEquals("RDY", document.querySelector(".row-time").getTextContent());
        assertEquals("he", document.querySelector(".belt-row.is-selected .belt-label").getTextContent());
        assertEquals("he", document.querySelector(".selected-stock .is-selected span[data-mm-text]").getTextContent());
        assertTrue(document.querySelector(".chamber-note").getClassList().contains("is-mismatch"));
        Document.runWithContext(document, () -> {
            assertEquals(430, document.body.getOffsetWidth(), 0.1);
            assertEquals(0xffa4ff80, Box.of(document.querySelector(".hud-panel")).getBorderLeftSide().color().getValue());
            assertEquals("none", document.querySelector(".loading-source").getComputedStyle().display);
            assertEquals("unset", Text.of(document.querySelector(".identity b")).fontFamily);
        });
        page.publish(snapshot(12, false, List.of(tube(1, false, 35)),
                List.of(belt("ap", 12, true), belt("he", 8, false))), false);
        assertSame(originalTube, document.querySelector(".tube-row"));
        assertSame(originalBelt, document.querySelector(".belt-row"));
        assertEquals("HE", document.querySelector(".round-label").getTextContent());
        assertEquals("HE", document.querySelector(".loading-source span[data-mm-text]").getTextContent());
        assertEquals("ap", document.querySelector(".belt-row.is-selected .belt-label").getTextContent());
        assertEquals("35%", document.querySelector(".row-fill").getInlineStylePropertyValue("--fill"));
        assertTrue(document.querySelector(".weapon-component").getClassList().contains("is-unselected"));
        Document.runWithContext(document, () -> {
            assertEquals("flex", document.querySelector(".loading-source").getComputedStyle().display);
            assertEquals("none", document.querySelector(".chamber-note").getComputedStyle().display);
        });
        assertNoBindingErrors(document);
    }

    @Test void tubeComponentHandlesZeroStockReorderExpansionAndReload() throws Exception {
        var page = create("tubes");
        var tubes = List.of(tube(1, true, 100), tube(2, true, 100), tube(3, false, 58), tube(4, false, 27));
        page.publish(snapshot(0, false, tubes, List.of(belt("kornet", 0, true))), true);
        var document = page.document();
        var rows = document.querySelectorAll(".tube-row");
        assertEquals(4, rows.size());
        assertEquals(2, document.querySelectorAll(".tube-row.is-ready").size());
        assertEquals("0/30", document.querySelector(".caption b").getTextContent());
        assertEquals("58%", document.querySelectorAll(".row-fill").get(2).getInlineStylePropertyValue("--fill"));
        Document.runWithContext(document, () -> {
            assertEquals(430, document.body.getOffsetWidth(), 0.1);
            assertTrue(document.body.getOffsetHeight() > 0);
            assertTrue(rows.get(2).getOffsetWidth() < 220);
            assertTrue(rows.get(2).getOffsetWidth() > 170);
        });
        List<HudWeaponSnapshot.Tube> expanded = new ArrayList<>(List.of(tubes.get(1), tubes.getFirst(), tubes.get(2), tubes.get(3)));
        for (int i = 5; i <= 8; i++) expanded.add(tube(i, false, 10 * i));
        page.publish(snapshot(0, false, expanded, List.of(belt("kornet", 0, true))), true);
        assertEquals(8, document.querySelectorAll(".tube-row").size());
        assertSame(rows.get(1), document.querySelector(".tube-row"));
        assertEquals("1", document.querySelector(".tube-number").getTextContent());
        document.refresh();
        page.publish(snapshot(0, false, List.of(tubes.getFirst()), List.of()), true);
        assertEquals(1, document.querySelectorAll(".tube-row").size());
        assertEquals("RDY", document.querySelector(".row-time").getTextContent());
        assertNoBindingErrors(document);
    }

    @Test void compactInstancesKeepTheirOwnStockAndSelectedFeed() throws Exception {
        var first = create("compact");
        var second = create("compact");
        first.publish(snapshot(382, false, List.of(tube(1, true, 100)), List.of(belt("ap_tracer", 382, true))), true);
        second.publish(snapshot(7, false, List.of(tube(2, false, 40)), List.of(belt("he", 7, true))), false);
        assertEquals("382", first.document().querySelector(".compact-head b").getTextContent());
        assertEquals("7", second.document().querySelector(".compact-head b").getTextContent());
        assertEquals("ap_tracer", first.document().querySelector(".stock-label span").getTextContent());
        assertEquals("he", second.document().querySelector(".stock-label span").getTextContent());
        assertNoBindingErrors(first.document());
        assertNoBindingErrors(second.document());
    }

    @Test void authorDefaultsControlWidthPaletteModulesAndColumns() throws Exception {
        var page = create("tubes", Map.of("--panel-width", "360px", "--phosphor", "#ffc16b",
                "--scanline-opacity", "0", "--identity-display", "none", "--tube-columns", "1fr"));
        page.publish(snapshot(0, false, List.of(tube(1, true, 100), tube(2, false, 35)), List.of()), true);
        var document = page.document();
        Document.runWithContext(document, () -> {
            assertEquals(360, document.body.getOffsetWidth(), 0.1);
            assertEquals(0xffffc16b, Box.of(document.querySelector(".hud-panel")).getBorderLeftSide().color().getValue());
            assertEquals("none", document.querySelector(".identity").getComputedStyle().display);
            assertEquals("0", document.querySelector(".scanlines").getComputedStyle().opacity);
            assertTrue(document.querySelector(".tube-row").getOffsetWidth() > 300);
        });
        assertNoBindingErrors(document);
    }

    @Test void emptyThenReadyDataRepopulatesWithoutPlaceholderErrors() throws Exception {
        var page = create("feed");
        page.publish(HudWeaponSnapshot.EMPTY, false);
        assertTrue(page.document().querySelectorAll(".tube-row, .belt-row").isEmpty());
        assertNoBindingErrors(page.document());
        page.publish(snapshot(1, false, List.of(tube(1, true, 100)), List.of(belt("ap", 1, true))), true);
        assertEquals(1, page.document().querySelectorAll(".tube-row").size());
        assertEquals(1, page.document().querySelectorAll(".belt-row").size());
        assertNoBindingErrors(page.document());
    }

    @Test void compositionExportsBoundDocumentsForVisualReview() throws Exception {
        var feed = create("feed");
        var tubes = create("tubes");
        var compact = create("compact");
        feed.publish(new HudWeaponSnapshot("30 MM / 2A42", "ready", 54, 500, false, "54/500",
                "30 MM AP", true, "RDY", "test:ap", List.of(tube(1, true, 100)),
                List.of(belt("AP", 12, false), belt("HE", 54, true))), true);
        tubes.publish(new HudWeaponSnapshot("9M133 / KORNET", "reloading", 0, 4, false, "0/4",
                "9M133", false, "2.0s", "test:kornet",
                List.of(tube(1, true, 100), tube(2, true, 100), tube(3, false, 58), tube(4, false, 27)).stream()
                        .map(t -> new HudWeaponSnapshot.Tube(t.key(), "9M133", t.state(), t.fill(), t.reloadRemaining(),
                                t.reloadText(), t.state().equals("ready") ? "9M133" : "", t.state().equals("reloading") ? "KORNET" : "")).toList(),
                List.of(belt("KORNET", 0, true))), false);
        compact.publish(new HudWeaponSnapshot("PKTM / COAX", "ready", 382, 2000, false, "382/2000",
                "7.62 MM", false, "RDY", "test:pktm", List.of(tube(1, true, 100)),
                List.of(belt("AP/TRACER", 382, true))), true);
        for (var page : List.of(feed, tubes, compact)) assertNoBindingErrors(page.document());
        var directory = Path.of("build/reports/hud-components");
        Files.createDirectories(directory);
        String css = Files.readString(BUILTIN.resolve("documents/weapon_hud_components/hud.css"));
        String html = "<!doctype html><html><head><meta charset='utf-8'><title>K17 weapon HUD / bound DOM preview</title>"
                + "<style>" + css + "html,body{width:100%;height:100%;display:block;background:#17231a}"
                + ".stage{position:relative;width:1600px;height:900px;background:linear-gradient(145deg,#334831,#101c17)}"
                + ".preview{position:absolute}.feed{right:16px;bottom:104px}.tubes-panel{left:16px;bottom:104px}"
                + ".compact{left:585px;bottom:104px}.notice{position:absolute;top:20px;left:20px;color:#b6c7ad;font:16px sans-serif}"
                + "</style></head><body><main class='stage'><div class='notice'>K17 / AUI-bound fixture data / browser preview</div>"
                + "<div class='preview feed'>" + feed.document().querySelector(".weapon-component").getOuterHTML() + "</div>"
                + "<div class='preview tubes-panel'>" + tubes.document().querySelector(".weapon-component").getOuterHTML() + "</div>"
                + "<div class='preview compact'>" + compact.document().querySelector(".weapon-component").getOuterHTML() + "</div>"
                + "</main></body></html>";
        Files.writeString(directory.resolve("index.html"), html);
    }

    @Test void k17UsesThreeControllerLayoutsAndRetainsStaticComparisonResources() throws Exception {
        var part = JsonParser.parseString(Files.readString(SDKFZ.resolve("parts/k17_turret.json"))).getAsJsonObject();
        var subsystems = part.getAsJsonObject("variants")
                .getAsJsonObject("sub_parts").getAsJsonObject("sub_part.machine_max.main").getAsJsonObject("subsystems");
        var kinds = Map.of("main_weapon", "feed", "secondary_weapon", "compact", "atgm_weapon", "tubes");
        for (var entry : kinds.entrySet()) {
            var wc = subsystems.getAsJsonObject("subsystem.machine_max." + entry.getKey());
            String layoutKey = "k17_weapon_" + entry.getValue();
            for (String field : List.of("first_hud_layout", "third_hud_layout", "scope_hud_layout")) {
                assertEquals("machine_max:" + layoutKey, wc.get(field).getAsString());
            }
            var layout = JsonParser.parseString(Files.readString(SDKFZ.resolve("hud_layouts/" + layoutKey + ".json")))
                    .getAsJsonArray().get(0).getAsJsonObject();
            assertEquals("always", layout.get("selection_visibility").getAsString());
            assertEquals("screen_fixed", layout.get("scope_behavior").getAsString());
            assertTrue(layout.get("ignore_zoom").getAsBoolean());
            var styleKey = layout.get("style").getAsString().split(":")[1];
            var style = JsonParser.parseString(Files.readString(BUILTIN.resolve("huds/" + styleKey + ".json"))).getAsJsonObject();
            String page = style.get("page").getAsString().substring("machine_max/".length());
            assertTrue(Files.isRegularFile(BUILTIN.resolve("documents/" + page)));
        }
        var seat = JsonParser.parseString(Files.readString(SDKFZ.resolve("subsystems/k17/k17_seat.json"))).getAsJsonObject();
        assertEquals("machine_max:k17_vehicle_hud", seat.getAsJsonObject("views").get("third_hud_layout").getAsString());
        for (String sight : List.of("gunner", "commander")) {
            var definition = JsonParser.parseString(Files.readString(SDKFZ.resolve("subsystems/k17/k17_" + sight + "_sight_preview.json"))).getAsJsonObject();
            assertEquals("machine_max:k17_scope_hud", definition.get("scope_hud_layout").getAsString());
        }
        for (String path : List.of("hud_layouts/k17_weapon_preview.json", "hud_layouts/k17_weapon_preview_scope.json",
                "huds/k17_weapon_preview.json", "documents/k17_weapon_preview/index.html", "documents/k17_weapon_preview/hud.css")) {
            assertTrue(Files.isRegularFile(SDKFZ.resolve(path)));
        }
    }
}
