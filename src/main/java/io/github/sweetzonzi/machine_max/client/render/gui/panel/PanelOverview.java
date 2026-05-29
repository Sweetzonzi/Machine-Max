package io.github.sweetzonzi.machine_max.client.render.gui.panel;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import io.github.sweetzonzi.machine_max.common.mech.control.ControlGroupSet;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Tab 01 载具概览面板。<br>
 * 显示控制组切换条、载具状态条、警告信息列表和控制模式。
 */
@OnlyIn(Dist.CLIENT)
public class PanelOverview {

    public static void render(Document doc, ControlGroupSet data) {
        GroupStripRenderer.render(doc, data, "group-strip-0");
        buildStatusBars(doc, data);
    }

    private static void buildStatusBars(Document doc, ControlGroupSet data) {
        Element statusCol = doc.getElementById("status-col");
        Element warningCol = doc.getElementById("warning-col");
        Element extraCol = doc.getElementById("extra-col");

        // 左列：载具状态标题 + 状态条 + 底部数字指标
        if (statusCol != null) {
            appendColHeader(doc, statusCol, "VEHICLE STATUS");
            appendStatBar(doc, statusCol, "DURABILITY", 85, "#C89520");
            appendStatBar(doc, statusCol, "ENERGY", 62, "#2E5A90");
            appendStatBar(doc, statusCol, "THRUST", 40, "#7aacff");
            appendStatBar(doc, statusCol, "SPEED", 30, "#4ac87a");
            appendNumericSection(doc, statusCol);
        }

        // 中列：警告信息标题 + 警告列表
        if (warningCol != null) {
            appendColHeader(doc, warningCol, "WARNINGS");
            appendWarningItem(doc, warningCol, "\u25B2", "LEFT ENGINE DURABILITY LOW", "#D8A631");
            appendWarningItem(doc, warningCol, "\u25B2", "WEAPON SYSTEM NOT RESPONDING", "#D8A631");
            appendWarningItem(doc, warningCol, "\u25A0", "ENERGY LEVEL CRITICAL", "#ff4444");
        }

        // 右列：控制模式标题 + 模式信息
        if (extraCol != null) {
            appendColHeader(doc, extraCol, "CONTROL MODE");
            appendModeLabel(doc, extraCol, "GROUND", "/// WHEELED VEHICLE CONTROL");
            appendModeInfoRow(doc, extraCol, "SIGNAL CHANNELS", "12 ACTIVE");
            appendModeInfoRow(doc, extraCol, "BINDINGS", "8 CONFIGURED");
            appendModeInfoRow(doc, extraCol, "NETWORK SYNC", "ONLINE");
        }
    }

    /**
     * 追加列标题到容器中。
     */
    private static void appendColHeader(Document doc, Element container, String title) {
        Element header = doc.createElement("div");
        header.setAttribute("class", "info-col-header");
        header.innerText = title;
        container.append(header);
    }

    /**
     * 追加控制模式标签到右列中。
     */
    private static void appendModeLabel(Document doc, Element container, String mode, String sublabel) {
        Element label = doc.createElement("div");
        label.setAttribute("class", "mode-label");
        label.innerText = mode;
        container.append(label);

        Element sub = doc.createElement("div");
        sub.setAttribute("class", "mode-sublabel");
        sub.innerText = sublabel;
        container.append(sub);
    }

    /**
     * 追加模式信息行到右列中。
     */
    private static void appendModeInfoRow(Document doc, Element container, String key, String value) {
        Element row = doc.createElement("div");
        row.setAttribute("class", "mode-info-row");

        Element keyEl = doc.createElement("span");
        keyEl.innerText = key;

        Element valEl = doc.createElement("span");
        valEl.setAttribute("class", "mir-value");
        valEl.innerText = value;

        row.append(keyEl);
        row.append(valEl);
        container.append(row);
    }

    /**
     * 追加一个状态条到容器中。
     */
    private static void appendStatBar(Document doc, Element container, String label, int pct, String color) {
        Element bar = doc.createElement("div");
        bar.setAttribute("class", "stat-bar");

        Element labelEl = doc.createElement("div");
        labelEl.setAttribute("class", "sb-label");
        labelEl.innerText = label + " " + pct + "%";

        Element track = doc.createElement("div");
        track.setAttribute("class", "sb-track");

        Element fill = doc.createElement("div");
        fill.setAttribute("class", "sb-fill");
        fill.setAttribute("style", "width:" + pct + "%;background:" + color);

        track.append(fill);
        bar.append(labelEl);
        bar.append(track);
        container.append(bar);
    }

    /**
     * 追加数字指标区域到状态列底部（HEADING / ALTITUDE / MASS）。
     */
    private static void appendNumericSection(Document doc, Element container) {
        Element section = doc.createElement("div");
        section.setAttribute("class", "stat-numeric-section");

        appendNumericItem(doc, section, "HEADING", "234\u00B0");
        appendNumericItem(doc, section, "ALTITUDE", "312M");
        appendNumericItem(doc, section, "MASS", "12.4T");

        container.append(section);
    }

    /**
     * 追加单个数字指标到容器中。
     *
     * @param doc     AUI 文档对象
     * @param container 父容器元素
     * @param label   指标标签（如 "HEADING"）
     * @param value   指标数值（如 "234°"）
     */
    private static void appendNumericItem(Document doc, Element container, String label, String value) {
        Element item = doc.createElement("div");
        item.setAttribute("class", "stat-numeric-item");

        Element labelEl = doc.createElement("div");
        labelEl.setAttribute("class", "stat-numeric-label");
        labelEl.innerText = label;

        Element valueEl = doc.createElement("div");
        valueEl.setAttribute("class", "stat-numeric-value");
        valueEl.innerText = value;

        item.append(labelEl);
        item.append(valueEl);
        container.append(item);
    }

    /**
     * 追加警告项到警告列。
     *
     * @param doc       AUI 文档对象
     * @param container 父容器元素
     * @param icon      警告图标字符（如 "▲" 或 "■"）
     * @param message   警告消息文本
     * @param color     警告颜色（十六进制色值）
     */
    private static void appendWarningItem(Document doc, Element container, String icon, String message, String color) {
        Element item = doc.createElement("div");
        item.setAttribute("class", "warning-item");
        item.setAttribute("style", "border-left-color:" + color + ";color:" + color);

        Element iconEl = doc.createElement("span");
        iconEl.innerText = icon;
        iconEl.setAttribute("style", "color:" + color + ";font-size:10px;flex-shrink:0");

        Element msgEl = doc.createElement("span");
        msgEl.innerText = message;

        item.append(iconEl);
        item.append(msgEl);
        container.append(item);
    }
}
