package io.github.sweetzonzi.machine_max.client.render.gui.panel;

import com.sighs.apricityui.init.Document;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.control.ControlGroupSet;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Tab 04 库存管理面板。<br>
 * 当前未实现，仅输出日志占位。HTML 中保留空 page div。
 */
@OnlyIn(Dist.CLIENT)
public class PanelInventory {

    public static void render(Document doc, ControlGroupSet data) {
        MachineMax.LOGGER.info("PanelInventory: not implemented yet");
    }
}
