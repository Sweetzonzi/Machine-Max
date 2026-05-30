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
 * 支持点击切换激活子组并同步另一条 strip。
 */
@OnlyIn(Dist.CLIENT)
public class GroupStripRenderer {

    /** 注册的所有 strip 容器 ID，用于跨 strip 同步激活态 */
    private static final List<String> STRIP_IDS = new ArrayList<>();

    /**
     * 在指定容器中渲染控制组切换条（使用默认主题色）。<br>
     * BASE 组始终为白底黑字，标记"始终激活"。
     */
    public static void render(Document doc, ControlGroupSet data, String containerId) {
        render(doc, data, containerId, null);
    }

    /**
     * 在指定容器中渲染控制组切换条（可指定主题色）。<br>
     * BASE 组始终为白底黑字，标记"始终激活"。<br>
     * 非基础组激活态使用 accent 颜色，未激活使用较深的 accent 颜色。
     *
     * @param accent 主题色（如 "#2E5A90"），null 则使用 CSS 默认值
     */
    public static void render(Document doc, ControlGroupSet data, String containerId, String accent) {
        Element container = doc.getElementById(containerId);
        if (container == null) return;

        if (!STRIP_IDS.contains(containerId)) {
            STRIP_IDS.add(containerId);
        }

        List<ControlGroup> allGroups = getAllGroups(data);

        int groupIndex = 0;
        for (int i = 0; i < allGroups.size(); i++) {
            ControlGroup group = allGroups.get(i);
            boolean isBase = i == 0;
            // 使用 data.activeIndex 判断激活态，而非硬编码 i == 1
            boolean isActive = !isBase && data.getActiveIndex() == (i - 1);

            Element card = doc.createElement("div");
            String cardId = containerId + "-card-" + i;
            card.setAttribute("id", cardId);

            if (isBase) {
                card.setAttribute("class", "group-card base");
            } else if (isActive) {
                card.setAttribute("class", "group-card active");
            } else {
                card.setAttribute("class", "group-card");
            }

            Element nameEl = doc.createElement("div");
            nameEl.setAttribute("class", "gc-name");
            nameEl.innerText = isBase ? "BASE" : group.name;

            Element numberEl = doc.createElement("div");
            numberEl.setAttribute("class", "gc-number");
            if (isBase) {
                numberEl.innerText = "GROUP 00";
            } else {
                groupIndex++;
                numberEl.innerText = String.format("GROUP %02d", groupIndex);
            }

            Element statusEl = doc.createElement("div");
            String statusId = cardId + "-status";
            statusEl.setAttribute("id", statusId);
            statusEl.setAttribute("class", "gc-status");
            if (isBase) {
                statusEl.innerText = "ALWAYS ACTIVE";
            } else if (isActive) {
                statusEl.innerText = "ACTIVE";
                statusEl.setAttribute("data-status", "active");
            } else {
                statusEl.innerText = "STAND BY";
                statusEl.setAttribute("data-status", "standby");
            }

            // 非 BASE 卡片添加点击切换事件
            if (!isBase) {
                int subIndex = i - 1;
                card.addEventListener("mousedown", e -> {
                    // 同一子组再次点击 → 取消激活（-1）
                    int newIndex = (data.getActiveIndex() == subIndex) ? -1 : subIndex;
                    // 更新所有 strip 中卡片的激活态：外层 class + 内部状态文字 + data-status
                    for (String sid : STRIP_IDS) {
                        for (int j = 0; j < allGroups.size(); j++) {
                            Element sibling = doc.getElementById(sid + "-card-" + j);
                            if (sibling == null) continue;
                            if (j == 0) {
                                sibling.setAttribute("class", "group-card base");
                            } else if (newIndex >= 0 && (j - 1) == newIndex) {
                                sibling.setAttribute("class", "group-card active");
                            } else {
                                sibling.setAttribute("class", "group-card");
                            }
                            // 替换 gc-status 子元素以确保文本重渲染
                            // AUI 的 innerText 直接赋值在某些场景不会触发重绘，
                            // 必须移除旧元素并用新文本创建全新元素（保证新引用）
                            Element oldStatus = doc.getElementById(sid + "-card-" + j + "-status");
                            if (oldStatus != null) {
                                Element cardParent = oldStatus.parentElement;
                                if (cardParent != null) {
                                    String newText;
                                    String newDataStatus;
                                    if (j == 0) {
                                        newText = "ALWAYS ACTIVE";
                                        newDataStatus = null;
                                    } else if (newIndex >= 0 && (j - 1) == newIndex) {
                                        newText = "ACTIVE";
                                        newDataStatus = "active";
                                    } else {
                                        newText = "STAND BY";
                                        newDataStatus = "standby";
                                    }
                                    oldStatus.remove();
                                    Element newStatus = doc.createElement("div");
                                    newStatus.setAttribute("id", sid + "-card-" + j + "-status");
                                    newStatus.setAttribute("class", "gc-status");
                                    newStatus.innerText = newText;
                                    if (newDataStatus != null) {
                                        newStatus.setAttribute("data-status", newDataStatus);
                                    }
                                    cardParent.append(newStatus);
                                }
                            }
                        }
                    }
                    // 更新数据模型的 activeIndex
                    data.activate(newIndex);
                    // TODO: 发送网络包同步控制组切换
                    // 需要新建 ControlGroupSetEditPayload.subPartId 等字段
                    // 这里更新了本地 data.activeIndex，应通过 ControlDataAccessor.saveControlSet() 同步到服务端
                });
            }

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

    /**
     * 重置静态状态，Screen 关闭时调用。<br>
     * 清空 STRIP_IDS，避免旧 Document 的 ID 残留导致新 Document 中误操作。
     */
    public static void reset() {
        STRIP_IDS.clear();
    }
}
