package io.github.sweetzonzi.machine_max.client.render.renderable;

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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class K17WeaponHudLayoutTest {
    private record Panel(double width, double height, double x, double y, int horizontal) {}
    private record Rect(double x, double y, double right, double bottom) {}

    @Test void configuredPanelsFitAndDoNotOverlapAcrossGuiSizes() throws Exception {
        var packs = Path.of("src/main/resources/spark_modules");
        var pages = packs.resolve("Machine-Max_Builtin/machine_max/documents/weapon_hud_components");
        List<AuiHudAnimatable> instances = new ArrayList<>();
        List<Panel> panels = new ArrayList<>();
        Size.setViewportOverride(1920, 1080);
        try {
            for (String kind : List.of("feed", "tubes", "compact")) {
                String path = "test://k17-placement/" + kind + ".html";
                String html = Files.readString(pages.resolve(kind + ".html"));
                assertTrue(html.contains("mode=browser; width=1600;"));
                HTML.putTemple(path, html.replace("<link rel=\"stylesheet\" href=\"hud.css\">",
                        "<style>" + Files.readString(pages.resolve("hud.css")) + "</style>"));
                var element = new HudElement(ResourceLocation.parse("test:placement"), false, ScopeBehavior.SCREEN_FIXED,
                        true, Vec3.ZERO, Vec3.ZERO, new Vec3(1, 1, 1), HudAnchor.CENTER);
                var runtime = new WeaponHudElement(null, element);
                var hud = new AuiHudAnimatable(element.style(), new AuiHudStyle(path, Map.of()), element, runtime);
                instances.add(hud);
                List<HudWeaponSnapshot.Tube> tubes = new ArrayList<>();
                for (int i = 0; i < (kind.equals("tubes") ? 4 : 1); i++) {
                    tubes.add(new HudWeaponSnapshot.Tube(new SupplierRef(new UUID(0, i), "gun", "launcher"),
                            "9M133 KORNET", i < 2 ? "ready" : "reloading", i < 2 ? "idle" : "loading",
                            i < 2 ? 100 : 30, 2, "2.0s",
                            i < 2 ? "9M133 KORNET" : "", i < 2 ? "" : "KORNET", true));
                }
                runtime.publish(HudPartSnapshot.EMPTY, new HudWeaponSnapshot("Weapon controller", "ready", 12, 30,
                        false, "12/30", "AP", true, "RDY", "test:ap", tubes,
                        List.of(new HudWeaponSnapshot.Belt(List.of(ResourceLocation.parse("test:ap")), "AP", 12, true))), true);
                hud.tickBindings();
                var document = Document.get(path).getFirst();
                double[] size = new double[2];
                Document.runWithContext(document, () -> {
                    document.commitStyleRecalc();
                    document.commitRenderState();
                    size[0] = document.body.getOffsetWidth();
                    size[1] = document.body.getOffsetHeight();
                });
                assertTrue(size[0] > 0 && size[1] > 0);
                var layout = JsonParser.parseString(Files.readString(packs.resolve(
                        "Machine-Max_K17/machine_max/hud_layouts/k17_weapon_" + kind + ".json")))
                        .getAsJsonArray().get(0).getAsJsonObject();
                var offset = layout.getAsJsonArray("offset");
                panels.add(new Panel(size[0], size[1], offset.get(0).getAsDouble(), offset.get(1).getAsDouble(),
                        HudAnchor.valueOf(layout.get("anchor").getAsString().toUpperCase(java.util.Locale.ROOT)).horizontal()));
            }
            for (int[] screen : new int[][]{{320, 240}, {427, 240}, {640, 360}, {800, 450}, {1280, 720}, {1600, 900}, {3840, 2160}}) {
                List<Rect> rectangles = new ArrayList<>();
                for (var panel : panels) {
                    var placement = AuiHudPlacement.resolve(panel.width(), panel.height(), screen[0], screen[1],
                            screen[0] / 1600f, 1, 1, panel.x(), panel.y(), panel.horizontal(), 2);
                    var rect = new Rect(placement.x(), placement.y(),
                            placement.x() + panel.width() * placement.scaleX(),
                            placement.y() + panel.height() * placement.scaleY());
                    assertTrue(rect.x() >= 0 && rect.y() >= 0 && rect.right() <= screen[0] && rect.bottom() <= screen[1],
                            "component outside " + screen[0] + "x" + screen[1] + ": " + rect);
                    for (var other : rectangles) {
                        assertFalse(rect.x() < other.right() && rect.right() > other.x()
                                && rect.y() < other.bottom() && rect.bottom() > other.y(), "overlap: " + rect + " / " + other);
                    }
                    rectangles.add(rect);
                }
            }
        } finally {
            instances.forEach(AuiHudAnimatable::dispose);
            Size.clearViewportOverride();
        }
    }
}
