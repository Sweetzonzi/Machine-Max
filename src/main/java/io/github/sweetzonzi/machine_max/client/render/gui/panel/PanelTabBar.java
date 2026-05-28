package io.github.sweetzonzi.machine_max.client.render.gui.panel;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.function.IntConsumer;

/**
 * Tab 导航栏渲染。<br>
 * 管理 Header Strip 中 4 个 Tab 按钮的激活态切换和点击事件。
 * Tab 04 库存管理暂未实现，按钮为禁用状态。
 * 激活态：白色背景 + 黑色文字 + 大号编号。
 * 非激活态：深色背景 + 白色文字 + 小号编号。
 * 通过内联 style 显式控制 header-content 和 header-number 的显隐与颜色，
 * 作为 CSS 选择器的保底方案。
 */
@OnlyIn(Dist.CLIENT)
public class PanelTabBar {

    private static final String[] TAB_PAGE_IDS = {
            "tab-overview",
            "tab-device",
            "tab-config",
            "tab-inventory"
    };

    /**
     * 初始化 Tab 导航栏：绑定点击事件，Tab 04 跳过。
     */
    public static void init(Document doc, IntConsumer onSwitch) {
        for (int i = 0; i < 4; i++) {
            Element btn = doc.getElementById("tab-btn-" + i);
            if (btn == null) continue;
            if (i == 3) {
                btn.setAttribute("class", "header-item disabled");
                continue;
            }
            int tabIndex = i;
            btn.addEventListener("mousedown", e -> onSwitch.accept(tabIndex));
        }
    }

    /**
     * 更新 Tab 激活态：切换 header-item 的 active 类 + page 的 active 类。<br>
     * 激活态：白色背景 + 大号黑色编号 + 显示标题。<br>
     * 非激活态：深色背景 + 小号白色编号 + 隐藏标题。<br>
     * 同时通过内联 style 控制 header-content 和 header-number 的显隐与颜色，
     * 确保即使 CSS 选择器失效也能正常工作。
     */
    public static void setActive(Document doc, int activeIndex) {
        if (doc == null) return;
        for (int i = 0; i < 4; i++) {
            Element btn = doc.getElementById("tab-btn-" + i);
            if (btn == null) continue;

            // 优先判断禁用状态，禁用态不做任何激活切换
            if (i == 3) {
                btn.setAttribute("class", "header-item disabled");
                btn.setAttribute("style", "opacity:0.3;cursor:default;pointer-events:none;");
                hideContent(doc, i);
                continue;
            }

            if (i == activeIndex) {
                btn.setAttribute("class", "header-item active");
                btn.setAttribute("style", "flex:0 0 auto;background:#FFFFFF;padding:0 16px;gap:10px;justify-content:flex-start;");
                showContent(doc, i);
                setNumberColor(doc, i, "#111111");
            } else {
                btn.setAttribute("class", "header-item");
                btn.setAttribute("style", "flex:0 0 auto;background:#111111;");
                hideContent(doc, i);
                // 清除内联 number 颜色，让 CSS 默认样式生效
                clearNumberStyle(doc, i);
            }

            Element page = doc.getElementById(TAB_PAGE_IDS[i]);
            if (page != null) {
                page.setAttribute("class", i == activeIndex ? "page active" : "page");
            }
        }
    }

    /** 显示指定 Tab 的 header-content */
    private static void showContent(Document doc, int index) {
        Element content = doc.getElementById("tab-content-" + index);
        if (content != null) {
            content.setAttribute("style", "display:flex;flex-direction:column;gap:2px;overflow:hidden;");
        }
    }

    /** 隐藏指定 Tab 的 header-content */
    private static void hideContent(Document doc, int index) {
        Element content = doc.getElementById("tab-content-" + index);
        if (content != null) {
            content.setAttribute("style", "display:none;");
        }
    }

    /** 设置指定 Tab 的 header-number 颜色 */
    private static void setNumberColor(Document doc, int index, String color) {
        Element btn = doc.getElementById("tab-btn-" + index);
        if (btn != null && btn.children != null && !btn.children.isEmpty()) {
            Element number = btn.children.get(0);
            if (number != null) {
                number.setAttribute("style", "color:" + color + ";");
            }
        }
    }

    /** 清除指定 Tab 的 header-number 内联样式 */
    private static void clearNumberStyle(Document doc, int index) {
        Element btn = doc.getElementById("tab-btn-" + index);
        if (btn != null && btn.children != null && !btn.children.isEmpty()) {
            Element number = btn.children.get(0);
            if (number != null) {
                number.removeAttribute("style");
            }
        }
    }
}
