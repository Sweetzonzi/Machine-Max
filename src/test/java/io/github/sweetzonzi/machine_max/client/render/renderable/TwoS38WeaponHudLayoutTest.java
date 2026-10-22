package io.github.sweetzonzi.machine_max.client.render.renderable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.parser.HTML;
import io.github.sweetzonzi.machine_max.client.render.gui.hud.molang.*;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.IAmmoSupplier.SupplierRef;
import io.github.sweetzonzi.machine_max.common.visual.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TwoS38WeaponHudLayoutTest {
    private static final Path PACKS = Path.of("src/main/resources/spark_modules");
    private static final Path SDKFZ = PACKS.resolve("sdkfz/machine_max");
    private static final Path BUILTIN = PACKS.resolve("machine_max.builtin/machine_max");
    private record Page(AuiHudAnimatable hud, WeaponHudElement runtime, Document document, HudElement element) {}
    private record Bounds(double width, double height, double panelHeight) {}

    @Test void rightSideStackFitsAcrossGuiSizesAndWeaponStates() throws Exception {
        var pages = new ArrayList<Page>();
        Size.setViewportOverride(1920, 1080);
        try {
            var part = json(SDKFZ.resolve("parts/2s38_turret.json"));
            var controllers = part.getAsJsonObject("variants").getAsJsonObject("2s38")
                    .getAsJsonObject("sub_parts").getAsJsonObject("sub_part.machine_max.main").getAsJsonObject("subsystems");
            for (String role : List.of("main_weapon", "secondary_weapon")) {
                var controller = controllers.getAsJsonObject("subsystem.machine_max." + role);
                String key = controller.get("first_hud_layout").getAsString();
                for (String view : List.of("third_hud_layout", "scope_hud_layout")) {
                    assertEquals(key, controller.get(view).getAsString());
                }
                pages.add(create(key));
            }
            var main = pages.getFirst();
            var coax = pages.get(1);
            for (String state : List.of("ready", "reloading", "empty")) {
                publish(main, state, true);
                publish(coax, "ready", false);
                assertEquals("AP", main.document().querySelector(".round-label").getTextContent());
                assertEquals("HE", main.document().querySelector(".belt-row.is-selected .belt-label").getTextContent());
                assertEquals("382", coax.document().querySelector(".compact-head b").getTextContent());
                assertEquals("AP/曳光", coax.document().querySelector(".stock-label span").getTextContent());
                assertTrue(main.document().querySelector(".tube-row").getClassList().contains("is-" + state));
                assertEquals(state.equals("reloading") ? "35%" : state.equals("ready") ? "100%" : "0%",
                        main.document().querySelector(".row-fill").getInlineStylePropertyValue("--fill"));
                assertTrue(coax.document().querySelector(".weapon-component").getClassList().contains("is-unselected"));
                Bounds upper = bounds(main), lower = bounds(coax);
                assertTrue(upper.height() - upper.panelHeight() >= 112);
                assertTrue(upper.height() - upper.panelHeight() > lower.height() + 10,
                        "reserved footer must fit the coax panel and its gap");
                for (int[] gui : new int[][]{{320, 240}, {427, 240}, {640, 360}, {800, 450},
                        {854, 240}, {1280, 720}, {1600, 900}, {1920, 540}, {3840, 2160}}) {
                    var a = placement(main, upper, gui);
                    var b = placement(coax, lower, gui);
                    assertTrue(a.y() >= 0 && b.y() >= 0, "top clipping at " + gui[0] + "x" + gui[1] + ": " + upper);
                    assertTrue(a.x() > gui[0] / 2.0 && b.x() > gui[0] / 2.0);
                    assertEquals(gui[0] - 8, a.x() + upper.width() * a.scaleX(), 0.01);
                    assertEquals(gui[0] - 8, b.x() + lower.width() * b.scaleX(), 0.01);
                    assertTrue(a.y() + upper.panelHeight() * a.scaleY() < b.y(), "panels overlap");
                    assertEquals(gui[1] - 52, b.y() + lower.height() * b.scaleY(), 0.01);
                }
            }
        } finally {
            pages.forEach(page -> page.hud().dispose());
            Size.clearViewportOverride();
        }
    }

    private static Page create(String key) throws Exception {
        var layout = JsonParser.parseString(Files.readString(SDKFZ.resolve("hud_layouts/" + key.split(":")[1] + ".json")))
                .getAsJsonArray().get(0).getAsJsonObject();
        assertFalse(layout.get("perspective").getAsBoolean());
        assertTrue(layout.get("ignore_zoom").getAsBoolean());
        assertEquals("screen_fixed", layout.get("scope_behavior").getAsString());
        assertEquals("always", layout.get("selection_visibility").getAsString());
        var styleKey = ResourceLocation.parse(layout.get("style").getAsString());
        String file = "huds/" + styleKey.getPath() + ".json";
        var style = json(Files.exists(SDKFZ.resolve(file)) ? SDKFZ.resolve(file) : BUILTIN.resolve(file));
        assertEquals("machine_max:aui_hud", style.get("type").getAsString());
        var defaults = new HashMap<String, String>();
        style.getAsJsonObject("defaults").entrySet().forEach(e -> defaults.put(e.getKey(), e.getValue().getAsString()));
        String pageFile = "documents/" + style.get("page").getAsString().substring("machine_max/".length());
        var htmlPath = Files.exists(SDKFZ.resolve(pageFile)) ? SDKFZ.resolve(pageFile) : BUILTIN.resolve(pageFile);
        var cssPath = BUILTIN.resolve("documents/weapon_hud_components/hud.css");
        String html = Files.readString(htmlPath).replace("<link rel=\"stylesheet\" href=\"hud.css\">",
                "<style>" + Files.readString(cssPath) + "</style>");
        String path = "test://2s38/" + UUID.randomUUID() + ".html";
        HTML.putTemple(path, html);
        var offset = layout.getAsJsonArray("offset");
        var scale = layout.getAsJsonArray("scale");
        var element = new HudElement(styleKey, false, ScopeBehavior.SCREEN_FIXED, true,
                new Vec3(offset.get(0).getAsDouble(), offset.get(1).getAsDouble(), 0), Vec3.ZERO,
                new Vec3(scale.get(0).getAsDouble(), scale.get(1).getAsDouble(), 1),
                HudAnchor.valueOf(layout.get("anchor").getAsString().toUpperCase(Locale.ROOT)), HudSelectionVisibility.ALWAYS);
        var runtime = new WeaponHudElement(null, element);
        var hud = new AuiHudAnimatable(styleKey, new AuiHudStyle(path, defaults), element, runtime);
        return new Page(hud, runtime, Document.get(path).getFirst(), element);
    }

    private static void publish(Page page, String state, boolean main) {
        var ap = ResourceLocation.parse("machine_max:57mm_ap");
        var he = ResourceLocation.parse("machine_max:57mm_he");
        boolean ready = state.equals("ready"), reloading = state.equals("reloading");
        var tubes = List.of(new HudWeaponSnapshot.Tube(new SupplierRef(new UUID(0, main ? 1 : 2), "gun", "launcher"),
                "AP", state, ready ? 100 : reloading ? 35 : 0, reloading ? 2 : 0, reloading ? "2.0s" : "",
                ready ? "AP" : "", reloading ? "AP" : ""));
        var belts = main ? List.of(new HudWeaponSnapshot.Belt(List.of(ap), "AP", 12, false),
                new HudWeaponSnapshot.Belt(List.of(he), "HE", 8, true))
                : List.of(new HudWeaponSnapshot.Belt(List.of(ap), "AP/曳光", 382, true));
        page.runtime().publish(HudPartSnapshot.EMPTY, new HudWeaponSnapshot(main ? "主武器" : "副武器", state,
                main ? 8 : 382, 500, false, main ? "8/500" : "382/500", "AP", ready, reloading ? "2.0s" : "RDY",
                ap.toString(), tubes, belts), main);
        page.hud().tickBindings();
        assertFalse(page.document().body.getTextContent().contains("MOLANG_ERROR"));
    }

    private static Bounds bounds(Page page) {
        double[] values = new double[3];
        Document.runWithContext(page.document(), () -> {
            page.document().commitStyleRecalc();
            page.document().commitRenderState();
            values[0] = page.document().body.getOffsetWidth();
            values[1] = page.document().body.getOffsetHeight();
            values[2] = page.document().querySelector(".hud-panel").getOffsetHeight();
        });
        return new Bounds(values[0], values[1], values[2]);
    }

    private static AuiHudPlacement placement(Page page, Bounds bounds, int[] screen) {
        var e = page.element();
        return AuiHudPlacement.resolve(bounds.width(), bounds.height(), screen[0], screen[1], screen[0] / 1600f,
                e.scale().x, e.scale().y, e.offset().x, e.offset().y, e.anchor().horizontal(), e.anchor().vertical());
    }

    private static JsonObject json(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
