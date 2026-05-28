package io.github.sweetzonzi.machine_max.client.render.gui.panel;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import io.github.sweetzonzi.machine_max.common.mech.control.ControlGroup;
import io.github.sweetzonzi.machine_max.common.mech.control.ControlGroupSet;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * 控制组切换条渲染器（共享组件）。<br>
 * 横向大卡片列表：baseGroup（始终白色） + 子组列表。
 * 在 Tab 01 和 Tab 02 中共享使用。
 */
@OnlyIn(Dist.CLIENT)
public class GroupStripRenderer {

    /**
     * 在指定容器中渲染控制组切换条。
     */
    public static void render(Document doc, ControlGroupSet data, String containerId) {
        Element container = doc.getElementById(containerId);
        if (container == null) return;

        List<ControlGroup> allGroups = getAllGroups(data);
        for (int i = 0; i < allGroups.size(); i++) {
            ControlGroup group = allGroups.get(i);
            boolean isBase = i == 0;
            boolean isActive = isBase;

            Element card = doc.createElement("div");
            String cardId = containerId + "-card-" + i;
            card.setAttribute("class", isActive ? "group-card active" : "group-card");
            card.setAttribute("id", cardId);

            Element nameEl = doc.createElement("div");
            nameEl.setAttribute("class", "gc-name");
            nameEl.innerText = isBase ? group.name + " (BASE)" : group.name;

            Element numberEl = doc.createElement("div");
            numberEl.setAttribute("class", "gc-number");
            numberEl.innerText = isBase ? "GROUP BASE" : String.format("GROUP %02d", i - 1);

            Element statusEl = doc.createElement("div");
            statusEl.setAttribute("class", "gc-status");
            statusEl.innerText = isActive ? "ACTIVE" : "STAND BY";

            card.append(nameEl);
            card.append(numberEl);
            card.append(statusEl);
            container.append(card);
        }
    }

    /**
     * 获取 baseGroup + 全部子组的列表，用于卡片构建。
     */
    public static List<ControlGroup> getAllGroups(ControlGroupSet set) {
        List<ControlGroup> all = new ArrayList<>(1 + set.groups.size());
        all.add(set.baseGroup);
        all.addAll(set.groups);
        return all;
    }
}
