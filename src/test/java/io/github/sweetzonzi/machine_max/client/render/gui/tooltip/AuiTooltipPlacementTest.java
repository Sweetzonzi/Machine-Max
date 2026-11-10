package io.github.sweetzonzi.machine_max.client.render.gui.tooltip;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.parser.HTML;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 验证 AUI 里“用行内样式移动一个 position:fixed 元素”是否真的生效。
 *
 * <p>tooltip 的定位完全依赖这条链路：渲染器每帧写 {@code left/top} 行内声明，AUI 重算布局后
 * 元素应当出现在新坐标。若这里不通过，说明运行时位置不跟随不是坐标系问题，而是行内样式白写。</p>
 */
class AuiTooltipPlacementTest {

    private static final String PAGE = "test://machine-max-tooltip/placement.html";

    @AfterEach
    void clearViewport() {
        Size.clearViewportOverride();
    }

    @Test
    void cssFixedPositionIsHonoured() {
        withDocument("""
                <html><head><style>
                  html, body { margin: 0; padding: 0; }
                  #fixed { position: fixed; left: 100px; top: 50px; width: 40px; height: 20px; }
                </style></head><body><div id="fixed">x</div></body></html>
                """, document -> {
            Element fixed = document.querySelector("#fixed");
            assertNotNull(fixed);
            Element.DOMRect rect = fixed.getBoundingClientRect();
            assertEquals(100.0d, rect.x, 0.01d);
            assertEquals(50.0d, rect.y, 0.01d);
        });
    }

    @Test
    void inlineStylePropertyMovesFixedElement() {
        withDocument("""
                <html><head><style>
                  html, body { margin: 0; padding: 0; }
                  #tooltip { position: fixed; left: 0; top: 0; width: 320px; }
                </style></head><body><div id="tooltip">x</div></body></html>
                """, document -> {
            Element root = document.querySelector("#tooltip");
            assertNotNull(root);
            // 与渲染器 applyPosition 完全一致的写入方式与顺序。
            root.setInlineStyleProperty("position", "fixed", "important");
            root.setInlineStyleProperty("pointer-events", "none", "important");
            root.setInlineStyleProperty("left", "680.00px", "important");
            root.setInlineStyleProperty("top", "128.00px", "important");

            document.commitStyleRecalc();
            document.commitRenderState();

            Element.DOMRect rect = root.getBoundingClientRect();
            assertEquals(680.0d, rect.x, 0.01d, "行内 left 未生效");
            assertEquals(128.0d, rect.y, 0.01d, "行内 top 未生效");
        });
    }

    @Test
    void plainStyleAttributeMovesFixedElement() {
        withDocument("""
                <html><head><style>
                  html, body { margin: 0; padding: 0; }
                  #tooltip { position: fixed; left: 0; top: 0; width: 320px; }
                </style></head><body><div id="tooltip">x</div></body></html>
                """, document -> {
            Element root = document.querySelector("#tooltip");
            assertNotNull(root);
            // AUI 官方示例的写法：整体替换 style 属性，不带 !important。
            root.setAttribute("style", "left: 680px; top: 128px;");

            document.commitStyleRecalc();
            document.commitRenderState();

            Element.DOMRect rect = root.getBoundingClientRect();
            assertEquals(680.0d, rect.x, 0.01d);
            assertEquals(128.0d, rect.y, 0.01d);
        });
    }

    private void withDocument(String html, java.util.function.Consumer<Document> assertions) {
        Size.setViewportOverride(1920, 1080);
        HTML.putTemple(PAGE, html);
        Document document = Document.create(PAGE);
        assertNotNull(document, "文档创建失败");
        try {
            Document.runWithContext(document, () -> {
                document.commitStyleRecalc();
                document.commitRenderState();
                assertions.accept(document);
            });
        } finally {
            if (!document.isDisposed()) document.remove();
        }
    }
}
