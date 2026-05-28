package io.github.sweetzonzi.machine_max.client.render.gui.panel;

import com.sighs.apricityui.init.Document;
import io.github.sweetzonzi.machine_max.common.mech.control.ControlGroupSet;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Tab 03 编辑配置面板。<br>
 * 三栏布局：左栏控制组列表、中栏组详情+绑定列表+GUI列表、右栏详情编辑表单。
 * 最复杂的面板，将在阶段 3 完整实现。
 */
@OnlyIn(Dist.CLIENT)
public class PanelConfigEditor {

    public static void render(Document doc, ControlGroupSet data) {
        buildLeftColumn(doc, data);
        buildMiddleColumn(doc, data);
        buildRightColumn(doc, data);
    }

    private static void buildLeftColumn(Document doc, ControlGroupSet data) {
    }

    private static void buildMiddleColumn(Document doc, ControlGroupSet data) {
    }

    private static void buildRightColumn(Document doc, ControlGroupSet data) {
    }
}
