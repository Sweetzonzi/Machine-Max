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
 * 每个 Tab 有独立主题色，切换时同步更新 header 底部边框和数字颜色。
 */
@OnlyIn(Dist.CLIENT)
public class PanelTabBar {

    private static final String[] TAB_PAGE_IDS = {
            "tab-overview",
            "tab-device",
            "tab-config",
            "tab-inventory"
    };

    /** 每个 Tab 的主题强调色 */
    private static final String[] TAB_ACCENT = {
            "#C89520",   // Tab 01: 琥珀
            "#2E5A90",   // Tab 02: 深蓝
            "#8A2A2A",   // Tab 03: 锈红
            "#6850B8"    // Tab 04: 深紫
    };

    /** 每个 Tab 的强调色背景（8% 透明度） */
    private static final String[] TAB_ACCENT_BG = {
            "rgba(200,149,32,0.08)",
            "rgba(46,90,144,0.08)",
            "rgba(138,42,42,0.08)",
            "rgba(104,80,184,0.08)"
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
     * 更新 Tab 激活态：切换 header-item 的 active 类 + page 的 active 类。
     * 同时控制 header-content 的显隐和主题色切换。
     */
    public static void setActive(Document doc, int activeIndex) {
        String accent = TAB_ACCENT[activeIndex];
        String accentBg = TAB_ACCENT_BG[activeIndex];

        for (int i = 0; i < 4; i++) {
            Element btn = doc.getElementById("tab-btn-" + i);
            if (btn != null) {
                String cls = (i == activeIndex) ? "header-item active" : "header-item";
                if (i == 3) cls = "header-item disabled";
                btn.setAttribute("class", cls);
                if (i == activeIndex) {
                    btn.setAttribute("style", "flex:1;border-bottom-color:" + accent + ";background:" + accentBg + ";");
                } else {
                    btn.setAttribute("style", "flex:0 0 auto;");
                }
            }
            Element content = doc.getElementById("tab-content-" + i);
            if (content != null) {
                content.setAttribute("style", i == activeIndex ? "display:flex;" : "display:none;");
            }
            Element number = doc.getElementById("tab-btn-" + i);
            if (number != null) {
                String numStyle = i == activeIndex ? "color:" + accent + ";" : "";
                setChildStyle(number, 0, numStyle);
            }
            Element page = doc.getElementById(TAB_PAGE_IDS[i]);
            if (page != null) {
                String pageCls = (i == activeIndex) ? "page active" : "page";
                page.setAttribute("class", pageCls);
            }
        }
    }

    /**
     * 设置指定子元素的内联 style（用于修改 header-number 的颜色）。
     */
    private static void setChildStyle(Element parent, int childIndex, String style) {
        if (parent.children != null && childIndex < parent.children.size()) {
            Element child = parent.children.get(childIndex);
            if (child != null && !style.isEmpty()) {
                child.setAttribute("style", style);
            }
        }
    }
}
