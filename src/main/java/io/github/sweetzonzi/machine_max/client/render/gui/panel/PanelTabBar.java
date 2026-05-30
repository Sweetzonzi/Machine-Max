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
 * 所有样式由 CSS class 控制，Java 只负责 class 切换。
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
     * 更新 Tab 激活态：仅切换 class，所有样式由 CSS 控制。
     */
    public static void setActive(Document doc, int activeIndex) {
        if (doc == null) return;
        for (int i = 0; i < 4; i++) {
            Element btn = doc.getElementById("tab-btn-" + i);
            if (btn == null) continue;

            if (i == 3) {
                btn.setAttribute("class", "header-item disabled");
                continue;
            }

            btn.setAttribute("class", i == activeIndex ? "header-item active" : "header-item");

            // 切换对应 page 的 active 类
            Element page = doc.getElementById(TAB_PAGE_IDS[i]);
            if (page != null) {
                page.setAttribute("class", i == activeIndex ? "page active" : "page");
            }
        }
    }
}
