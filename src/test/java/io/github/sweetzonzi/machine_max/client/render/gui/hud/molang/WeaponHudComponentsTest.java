package io.github.sweetzonzi.machine_max.client.render.gui.hud.molang;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.style.Text;
import io.github.sweetzonzi.machine_max.client.render.renderable.AuiHudAnimatable;
import io.github.sweetzonzi.machine_max.common.mech.ammo.AmmoBelt;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AmmoRef;
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
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class WeaponHudComponentsTest {
    private static final Path PACKS = Path.of("src/main/resources/spark_modules");
    private static final Path BUILTIN = PACKS.resolve("Machine-Max_Builtin/machine_max");
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
        var directory = BUILTIN.resolve("documents/weapon_hud_components");
        String css = Files.readString(directory.resolve("hud.css"));
        String html = Files.readString(directory.resolve(kind + ".html"))
                .replace("<link rel=\"stylesheet\" href=\"hud.css\">", "<style>" + css + "</style>");
        return createPage(html, defaults);
    }

    private Page createPage(String html, Map<String, String> defaults) {
        Size.setViewportOverride(1920, 1080);
        String path = "test://components/" + UUID.randomUUID() + ".html";
        HTML.putTemple(path, html);
        var element = HudMolangContextTest.element();
        var runtime = new WeaponHudElement(null, element);
        var hud = new AuiHudAnimatable(element.style(), new AuiHudStyle(path, defaults), element, runtime);
        instances.add(hud);
        return new Page(runtime, hud, Document.get(path).getFirst());
    }

    private static HudWeaponSnapshot.AmmoOption belt(String key, int remaining, boolean selected) {
        return new HudWeaponSnapshot.AmmoOption(spec("test:" + key.toLowerCase(Locale.ROOT).replace('/', '_')),
                key, remaining, 1000, false, selected);
    }

    private static AmmoBelt spec(String ammo) {
        var id = ResourceLocation.parse(ammo);
        return new AmmoBelt(null, "", List.of(new AmmoBelt.PatternEntry(id, 1)), 1, 1, null);
    }

    private static HudWeaponSnapshot.Tube tube(int key, boolean ready, double fill) {
        var base = HudMolangContextTest.tube(key, fill);
        return new HudWeaponSnapshot.Tube(base.key(), ready ? "AP" : "HE", ready ? "ready" : "reloading",
                ready ? "idle" : "machine", ready ? 0 : fill / 100, ready ? -1 : 2, ready ? "" : "2.0s",
                "idle", 0, -1, "", ready ? "" : "HE", false, true,
                ready ? new HudWeaponSnapshot.Chamber(true, "AP") : HudWeaponSnapshot.Chamber.EMPTY,
                HudWeaponSnapshot.LoadedBelt.EMPTY, base.sourceReserve(), base.aimed());
    }

    private static HudWeaponSnapshot snapshot(int remaining, boolean mismatch,
                                               List<HudWeaponSnapshot.Tube> tubes,
                                               List<HudWeaponSnapshot.AmmoOption> belts) {
        return snapshot(remaining, mismatch, tubes, belts,
                tubes.stream().mapToInt(t -> t.belt().remaining()).sum(),
                (int) tubes.stream().filter(t -> t.chamber().loaded()).count());
    }

    private static HudWeaponSnapshot snapshot(int remaining, boolean mismatch,
                                               List<HudWeaponSnapshot.Tube> tubes,
                                               List<HudWeaponSnapshot.AmmoOption> belts,
                                               int magazineRemaining, int chamberedCount) {
        return new HudWeaponSnapshot("Test weapon", tubes.stream().anyMatch(t -> t.state().equals("reloading"))
                ? "reloading" : "ready", remaining, 30, false, remaining + "/30",
                magazineRemaining, chamberedCount, "AP", mismatch, "Ready", "test:ap", tubes, belts);
    }

    private static void assertNoBindingErrors(Document document) {
        assertFalse(document.body.getTextContent().contains("MOLANG_ERROR"));
        assertTrue(document.querySelectorAll("[data-mm-repeat]").isEmpty());
    }

    @Test void feedKeepsTubeRoundAndLoadingSourceSeparateFromSelectedBelt() throws Exception {
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
        assertNull(document.querySelector(".chamber-note"));
        Document.runWithContext(document, () -> {
            assertTrue(document.body.getOffsetWidth() > 0, "面板必须按内容量出宽度");
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
        assertEquals("0%", document.querySelector(".tube-row.is-ready .row-fill").getInlineStylePropertyValue("--fill"));
        assertEquals(58, Double.parseDouble(document.querySelectorAll(".row-fill").get(2)
                .getInlineStylePropertyValue("--fill").replace("%", "")), 1e-9);
        Document.runWithContext(document, () -> {
            assertTrue(document.body.getOffsetWidth() > 0, "面板必须按内容量出宽度");
            assertTrue(document.body.getOffsetHeight() > 0);
            assertTrue(rows.get(2).getOffsetWidth() > 0);
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

    private static HudWeaponSnapshot.Tube regeneratingTube(boolean loading) {
        return new HudWeaponSnapshot.Tube(new AmmoRef(9, 0),
                "9M133", "reloading", loading ? "machine" : "idle", loading ? 0.4 : 0,
                loading ? 7.5 : -1, loading ? "7.5s" : "",
                "regenerating", 0.25, 15, "15.0s", "KORNET", false, true,
                HudWeaponSnapshot.Chamber.EMPTY, HudWeaponSnapshot.LoadedBelt.EMPTY,
                new HudWeaponSnapshot.Reserve(0, 4, false), false);
    }

    @Test void loadingAndAmmoRegenerationRenderIndependently() throws Exception {
        var page = create("tubes");
        var belts = List.of(belt("kornet", 0, true));
        page.publish(snapshot(0, false, List.of(regeneratingTube(false)), belts), true);
        var document = page.document();
        double[] rowHeights = new double[2];
        assertEquals("WAIT", document.querySelector(".row-time").getTextContent());
        assertEquals("0%", document.querySelector(".row-fill").getInlineStylePropertyValue("--fill"));
        assertEquals("25%", document.querySelector(".regen-fill").getInlineStylePropertyValue("--regen-fill"));
        assertEquals("15.0s", document.querySelector(".regen-time").getTextContent());
        Document.runWithContext(document, () ->
                assertEquals("none", document.querySelector(".loading-source").getComputedStyle().display));
        Document.runWithContext(document, () -> rowHeights[0] = document.querySelector(".tube-row").getOffsetHeight());

        page.publish(snapshot(0, false, List.of(regeneratingTube(true)), belts), true);
        assertEquals("7.5s", document.querySelector(".row-time").getTextContent());
        assertEquals("40%", document.querySelector(".row-fill").getInlineStylePropertyValue("--fill"));
        assertEquals("25%", document.querySelector(".regen-fill").getInlineStylePropertyValue("--regen-fill"));
        assertEquals("15.0s", document.querySelector(".regen-time").getTextContent());
        Document.runWithContext(document, () -> {
            assertEquals("flex", document.querySelector(".loading-source").getComputedStyle().display);
            assertEquals("flex", document.querySelector(".regen-status").getComputedStyle().display);
            assertTrue(document.querySelector(".loading-source").getClassList().contains("is-machine"));
            assertTrue(document.querySelector(".regen-fill").getClassList().contains("is-regenerating"));
            rowHeights[1] = document.querySelector(".tube-row").getOffsetHeight();
        });
        assertEquals(rowHeights[0], rowHeights[1], 1e-6, "adding the loading source must not grow a tube row");

        var empty = new HudWeaponSnapshot.Tube(new AmmoRef(10, 0),
                "AP", "empty", "idle", 0, -1, "", "idle", 0, -1, "", "", false, true,
                HudWeaponSnapshot.Chamber.EMPTY, HudWeaponSnapshot.LoadedBelt.EMPTY,
                HudWeaponSnapshot.Reserve.EMPTY, false);
        page.publish(snapshot(0, false, List.of(empty), List.of()), true);
        assertEquals("EMPTY", document.querySelector(".row-time").getTextContent());
        assertEquals("READY", document.querySelector(".regen-time").getTextContent());
        Document.runWithContext(document, () ->
                assertEquals("flex", document.querySelector(".regen-status").getComputedStyle().display));
        assertNoBindingErrors(document);
    }

    @Test void packagedAuiWeaponPagesBindTheCurrentVocabulary() throws Exception {
        var weaponBinding = Pattern.compile("data-mm-(?:text|num|class|repeat)=[\"'][^\"']*\\b(?:weapon|tube|belt)\\.");
        var data = snapshot(0, false, List.of(regeneratingTube(true)), List.of(belt("kornet", 0, true)));
        int checked = 0;
        try (var files = Files.walk(PACKS)) {
            for (var file : files.filter(it -> it.toString().endsWith(".html")).sorted().toList()) {
                var html = Files.readString(file);
                if (!weaponBinding.matcher(html).find()) continue;
                var page = createPage(html, Map.of());
                page.publish(data, true);
                var document = page.document();
                assertFalse(document.body.getTextContent().contains("MOLANG_ERROR"), file::toString);
                assertTrue(document.querySelectorAll("[data-mm-repeat]").isEmpty(), file::toString);
                if (file.toString().contains("weapon_hud_components") && document.querySelector(".loading-source") != null) {
                    assertNotNull(document.querySelector(".tube-line .loading-source"), file::toString);
                    assertNull(document.querySelector(".chamber-note"), file::toString);
                }
                var regen = document.querySelector("[data-mm-var='--regen-fill']");
                if (regen != null) assertEquals("25%", regen.getInlineStylePropertyValue("--regen-fill"), file::toString);
                checked++;
            }
        }
        assertTrue(checked > 3, "the scan must include pages beyond the shared weapon components");
    }

    @Test void operationalWeaponPanelsOmitStaticDamageAndFireRateCaptions() throws Exception {
        var performanceCaption = Pattern.compile("(?is)<div\\s+class=\"caption\"[^>]*>[^<]*(?:\\bRPM\\b|\\bDMG\\b|\\bPEN\\b)[^<]*</div>");
        try (var files = Files.walk(PACKS)) {
            for (var file : files.filter(it -> it.toString().contains("weapon_hud_components")
                    && it.toString().endsWith(".html")).toList()) {
                var html = Files.readString(file);
                assertFalse(performanceCaption.matcher(html).find(), file::toString);
                assertFalse(html.contains("hud.type59.main_stats"), file::toString);
                assertFalse(html.contains("hud.type59.mg_stats"), file::toString);
            }
        }
    }

    @Test void builtinLiveWeaponKeepsRegenerationSpaceAcrossPhases() throws Exception {
        var html = Files.readString(BUILTIN.resolve("documents/weapon_hud_live/index.html"));
        var page = createPage(html, Map.of());
        var belts = List.of(belt("kornet", 4, true));
        var document = page.document();
        page.publish(snapshot(4, false, List.of(tube(1, true, 100)), belts), true);
        double[] heights = new double[2];
        Document.runWithContext(document, () -> {
            assertEquals("hidden", document.querySelector(".regen-track").getComputedStyle().visibility);
            heights[0] = document.querySelector(".tube").getOffsetHeight();
        });
        page.publish(snapshot(4, false, List.of(regeneratingTube(true)), belts), true);
        Document.runWithContext(document, () -> {
            assertEquals("visible", document.querySelector(".regen-track").getComputedStyle().visibility);
            assertEquals("visible", document.querySelector(".regen-time").getComputedStyle().visibility);
            heights[1] = document.querySelector(".tube").getOffsetHeight();
        });
        assertEquals(heights[0], heights[1], 1e-6, "regeneration must not add a row to the live tube");
    }

    @Test void weaponHudRegenerationTracksUseTheirThemeColor() throws Exception {
        var styles = Map.of(
                BUILTIN.resolve("documents/weapon_hud_components/hud.css"), "--mm-accent",
                BUILTIN.resolve("documents/weapon_hud_live/index.html"), "--mm-accent",
                PACKS.resolve("Machine-Max_T90A/t90a/documents/weapon_hud_components/hud.css"), "--mm-accent",
                PACKS.resolve("Machine-Max_T90A/t90a/documents/1g46/sight.css"), "--mm-accent",
                PACKS.resolve("Machine-Max_M1A2_SEP/m1a2_sep/documents/weapon_hud_components/hud.css"), "--mm-accent",
                PACKS.resolve("Machine-Max_M1A2_SEP/m1a2_sep/documents/m1a2_gps/sight.css"), "--display",
                PACKS.resolve("Machine-Max_Type59/type59/documents/weapon_hud_components/hud.css"), "--phosphor",
                PACKS.resolve("Machine-Max_Sdkfz234/sdkfz234/documents/sdkfz_hud/hud.css"), "--mm-accent");
        for (var entry : styles.entrySet()) {
            var css = Files.readString(entry.getKey());
            var themedTrack = Pattern.compile("(?s)(?:\\.regen-fill|\\.regen-track\\s+i|\\.mg-regen-track\\s+i)"
                    + "[^{}]*\\{[^}]*background(?:-color)?\\s*:\\s*var\\("
                    + Pattern.quote(entry.getValue()) + "\\s*,");
            assertTrue(themedTrack.matcher(css).find(),
                    entry.getKey() + " must color its regeneration track with " + entry.getValue());
        }
    }

    @Test void compactInstancesKeepTheirOwnStockAndSelectedFeed() throws Exception {
        var first = create("compact");
        var second = create("compact");
        first.publish(snapshot(382, false, List.of(tube(1, true, 100)), List.of(belt("ap_tracer", 382, true))), true);
        second.publish(snapshot(7, false, List.of(tube(2, false, 40)), List.of(belt("he", 7, true))), false);
        // 紧凑条读作“待发 / 备弹”：就绪的那根管算 1 发待发，其余是装弹机里的 382/7
        assertEquals("1/382", first.document().querySelector(".compact-head b").getTextContent());
        assertEquals("0/7", second.document().querySelector(".compact-head b").getTextContent());
        assertEquals("ap_tracer", first.document().querySelector(".stock-label span").getTextContent());
        assertEquals("he", second.document().querySelector(".stock-label span").getTextContent());
        assertNoBindingErrors(first.document());
        assertNoBindingErrors(second.document());
    }

    @Test void authorDefaultsControlWidthPaletteModulesAndColumns() throws Exception {
        var page = create("tubes", Map.of("--panel-width", "360px", "--mm-accent", "#ffc16b",
                "--scanline-opacity", "0", "--identity-display", "none", "--tube-columns", "1fr"));
        page.publish(snapshot(0, false, List.of(tube(1, true, 100), regeneratingTube(false)), List.of()), true);
        var document = page.document();
        Document.runWithContext(document, () -> {
            assertEquals(360, document.body.getOffsetWidth(), 0.1);
            assertEquals(0xffffc16b, Box.of(document.querySelector(".hud-panel")).getBorderLeftSide().color().getValue());
            assertEquals("#ffc16b", document.querySelector(".regen-fill").getComputedStyle().backgroundColor);
            assertEquals("none", document.querySelector(".identity").getComputedStyle().display);
            assertEquals("0", document.querySelector(".scanlines").getComputedStyle().opacity);
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

    @Test void richWeaponDataBindsOnAllThreeComponents() throws Exception {
        var feed = create("feed");
        var tubes = create("tubes");
        var compact = create("compact");
        feed.publish(new HudWeaponSnapshot("30 MM / 2A42", "ready", 54, 500, false, "54/500",
                0, 1, "30 MM AP", true, "RDY", "test:ap", List.of(tube(1, true, 100)),
                List.of(belt("AP", 12, false), belt("HE", 54, true))), true);
        tubes.publish(new HudWeaponSnapshot("9M133 / KORNET", "reloading", 0, 4, false, "0/4",
                0, 2, "9M133", false, "2.0s", "test:kornet",
                List.of(tube(1, true, 100), tube(2, true, 100), tube(3, false, 58), tube(4, false, 27)).stream()
                .map(t -> new HudWeaponSnapshot.Tube(t.key(), "9M133", t.state(), t.loadingPhase(), t.loadingProgress(), t.loadingTimeRemaining(),
                                t.loadingTimeText(), t.supplyRefillPhase(), t.supplyRefillProgress(), t.supplyRefillTimeRemaining(), t.supplyRefillTimeText(),
                                t.state().equals("reloading") ? "KORNET" : "",
                                false, true,
                t.state().equals("ready") ? new HudWeaponSnapshot.Chamber(true, "9M133") : HudWeaponSnapshot.Chamber.EMPTY,
                                t.belt(), t.sourceReserve(), t.aimed())).toList(),
                List.of(belt("KORNET", 0, true))), false);
        compact.publish(new HudWeaponSnapshot("PKTM / COAX", "ready", 382, 2000, false, "382/2000",
                0, 1, "7.62 MM", false, "RDY", "test:pktm", List.of(tube(1, true, 100)),
                List.of(belt("AP/TRACER", 382, true))), true);
        for (var page : List.of(feed, tubes, compact)) assertNoBindingErrors(page.document());
    }
}
