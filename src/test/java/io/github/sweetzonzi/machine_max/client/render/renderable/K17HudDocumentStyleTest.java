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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 武器 HUD 页面的主题变量管线：边框、装填条与扫描线都从 {@code --mm-accent} /
 * {@code --scanline-opacity} 取值，作者改一个变量就能换掉整套配色。
 *
 * <p>断言只钉「同一个主题色是否贯穿全部装饰」「变量覆写是否生效」与「扫描线是否只盖住自己的
 * 面板」。具体色值、像素尺寸、元素份数都属于页面样式，由作者随时调整，不进用例。</p>
 */
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
    void everyDecorationTakesItsColorFromTheSameAccentToken() throws Exception {
        Document document = createHud("visible-decoration", Map.of());
        Document.runWithContext(document, () -> {
            commit(document);
            for (var leaf : document.querySelectorAll("span, b")) {
                assertEquals("unset", Text.of(leaf).fontFamily,
                        "HUD text must use the native Minecraft font rendering path");
            }

            Set<Integer> accents = new LinkedHashSet<>();
            for (var panel : document.querySelectorAll(".hud-panel")) {
                var border = Box.of(panel).getBorderLeftSide();
                assertTrue(border.size() > 0, "面板必须有左边框");
                accents.add(border.color().getValue());
            }
            for (String selector : new String[]{".station-id", ".belt-row", ".tube-row", ".chamber-note"}) {
                var nodes = document.querySelectorAll(selector);
                assertFalse(nodes.isEmpty(), "页面缺少 " + selector);
                for (var node : nodes) {
                    accents.add(Box.of(node).getBorderTopSide().color().getValue());
                }
            }
            accents.add(Box.of(document.querySelector(".identity")).getBorderBottomSide().color().getValue());
            accents.add(new Color(Background.of(document.querySelector(".belt-row .row-fill")).color).getValue());

            assertEquals(1, accents.size(), "边框与装填条应共用同一个主题色，实为 " + accents);
            int accent = accents.iterator().next();
            assertTrue((accent >>> 24) != 0,
                    "主题色必须不透明，否则装饰在游戏里不可见：" + Integer.toHexString(accent));

            var loading = Background.of(document.querySelector(".is-loading .row-fill")).gradient;
            assertNotNull(loading, "装填中的装填条必须是条纹渐变");
            assertTrue(loading.repeating());
            assertTrue(loading.stops().stream().anyMatch(stop -> stop.color == accent),
                    "条纹渐变里必须出现主题色");

            var row = document.querySelector(".is-loading");
            var fill = document.querySelector(".is-loading .row-fill");
            assertTrue(fill.getOffsetHeight() > 0, "装填条必须有高度");
            assertTrue(fill.getOffsetWidth() > 0 && fill.getOffsetWidth() <= row.getOffsetWidth(),
                    "装填条不能超出所在行");
        });
    }

    @Test
    void scanlinesCoverOnlyTheirPanelsAndKeepTheBodyContentSized() throws Exception {
        Document document = createHud("scanline-geometry", Map.of());
        Document.runWithContext(document, () -> {
            commit(document);
            var panels = document.querySelectorAll(".hud-panel");
            var masks = document.querySelectorAll(".scanlines");
            assertFalse(masks.isEmpty(), "页面必须有扫描线遮罩");
            assertTrue(masks.size() <= panels.size(),
                    "扫描线遮罩只应挂在面板内：遮罩 " + masks.size() + " / 面板 " + panels.size());

            assertEquals(0, new Color(Background.of(document.body).color).getValue() >>> 24,
                    "body 不能自己铺背景色，否则会盖住世界");
            assertTrue(document.body.getOffsetWidth() > 0 && document.body.getOffsetHeight() > 0,
                    "body 必须按内容撑开");

            for (int i = 0; i < masks.size(); i++) {
                var mask = masks.get(i);
                var panel = panels.get(i);
                var gradient = Background.of(mask).gradient;
                assertNotNull(gradient, "扫描线必须是条纹渐变");
                assertTrue(gradient.repeating());
                assertTrue(Double.parseDouble(mask.getComputedStyle().opacity) > 0,
                        "默认主题下扫描线必须可见");
                assertTrue(mask.getOffsetWidth() > panel.getOffsetWidth() * 0.95, "遮罩必须盖住面板宽度");
                assertTrue(mask.getOffsetHeight() > panel.getOffsetHeight() * 0.95, "遮罩必须盖住面板高度");
            }
        });
    }

    @Test
    void authorDefaultsCanChangeTheWholePaletteAndDisableScanlines() throws Exception {
        Document document = createHud("amber-no-scanlines",
                Map.of("--mm-accent", "#ffc16b", "--scanline-opacity", "0"));
        Document.runWithContext(document, () -> {
            commit(document);
            assertEquals(0xffffc16b,
                    Box.of(document.querySelector(".hud-panel")).getBorderLeftSide().color().getValue());
            assertEquals(0xffffc16b,
                    new Color(Background.of(document.querySelector(".belt-row .row-fill")).color).getValue());
            for (var mask : document.querySelectorAll(".scanlines")) {
                assertEquals(0.0, Double.parseDouble(mask.getComputedStyle().opacity), 0.001,
                        "作者把 --scanline-opacity 设为 0 后扫描线必须完全消失");
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
