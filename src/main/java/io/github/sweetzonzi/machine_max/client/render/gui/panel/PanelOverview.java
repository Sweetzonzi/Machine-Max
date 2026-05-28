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

        // 左列：载具状态标题 + 状态条
        if (statusCol != null) {
            appendColHeader(doc, statusCol, "VEHICLE STATUS");
            appendStatBar(doc, statusCol, "DURABILITY", 85, "#C89520");
            appendStatBar(doc, statusCol, "ENERGY", 62, "#2E5A90");
            appendStatBar(doc, statusCol, "THRUST", 40, "#7aacff");
            appendStatBar(doc, statusCol, "SPEED", 30, "#4ac87a");
            appendStatBar(doc, statusCol, "HEADING", 0, "#c8c8d0");
            appendStatBar(doc, statusCol, "ALTITUDE", 0, "#c8c8d0");
        }

        // 中列：警告信息标题 + 警告列表
        if (warningCol != null) {
            appendColHeader(doc, warningCol, "WARNINGS");
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
}
