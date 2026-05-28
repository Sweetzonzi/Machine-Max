package io.github.sweetzonzi.machine_max.client.render.gui.panel;

import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import io.github.sweetzonzi.machine_max.common.mech.control.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

/**
 * Tab 02 设备控制面板。<br>
 * 显示控制组切换条、ACTIONS 按钮区、TOGGLES 开关区和 AXES 纵向滑条区。
 * 滑条使用纵向布局，从底部向上填充。
 */
@OnlyIn(Dist.CLIENT)
public class PanelDeviceControl {

    private static Element vSliderTrack;
    private static Element vSliderFill;
    private static Element vSliderValue;
    private static double vSliderStartY;
    private static int vSliderStartPct;

    public static void render(Document doc, ControlGroupSet data) {
        GroupStripRenderer.render(doc, data, "group-strip-1", "#2E5A90");
        buildDeviceSections(doc, data);

        doc.body.addEventListener("mousemove", PanelDeviceControl::onVSliderMouseMove);
        doc.body.addEventListener("mouseup", PanelDeviceControl::onVSliderMouseUp);
    }

    private static void buildDeviceSections(Document doc, ControlGroupSet data) {
        List<AbstractGuiAction> actions = data.getGuiActions();
        if (actions == null) return;

        Element pulseSection = doc.getElementById("pulse-section");
        Element toggleSection = doc.getElementById("toggle-section");
        Element sliderSection = doc.getElementById("slider-section");

        for (int i = 0; i < actions.size(); i++) {
            AbstractGuiAction action = actions.get(i);
            String type = action.type().name().toLowerCase();
            switch (type) {
                case "pulse" -> {
                    if (pulseSection != null) {
                        GuiPulseAction pulse = (GuiPulseAction) action;
                        buildPulseButton(doc, pulseSection, pulse, i);
                    }
                }
                case "toggle" -> {
                    if (toggleSection != null) {
                        GuiToggleAction toggle = (GuiToggleAction) action;
                        buildToggleSwitch(doc, toggleSection, toggle, i);
                    }
                }
                case "slider" -> {
                    if (sliderSection != null) {
                        GuiSliderAction slider = (GuiSliderAction) action;
                        buildVerticalSlider(doc, sliderSection, slider, i);
                    }
                }
            }
        }

        if (pulseSection != null) {
            Element title = doc.createElement("div");
            title.setAttribute("class", "device-section-title");
            title.innerText = "ACTIONS";
            pulseSection.prepend(title);
        }
        if (toggleSection != null) {
            Element title = doc.createElement("div");
            title.setAttribute("class", "device-section-title");
            title.innerText = "TOGGLES";
            toggleSection.prepend(title);
        }
        if (sliderSection != null) {
            Element title = doc.createElement("div");
            title.setAttribute("class", "device-section-title");
            title.innerText = "AXES";
            sliderSection.prepend(title);
        }
    }

    /**
     * 构建 PULSE 按钮。
     */
    private static void buildPulseButton(Document doc, Element container, GuiPulseAction action, int index) {
        Element btn = doc.createElement("div");
        btn.setAttribute("class", "btn-pulse");
        btn.innerText = action.label;

        btn.addEventListener("mousedown", e -> {
            btn.setAttribute("style", "background:rgba(46,90,144,0.4);");
        });
        btn.addEventListener("mouseup", e -> {
            btn.setAttribute("style", "");
        });

        container.append(btn);
    }

    /**
     * 构建 TOGGLE 开关，使用 .toggle-row 布局。
     */
    private static void buildToggleSwitch(Document doc, Element container, GuiToggleAction action, int index) {
        Element wrapper = doc.createElement("div");
        wrapper.setAttribute("class", "toggle-row");

        Element label = doc.createElement("span");
        label.setAttribute("class", "toggle-label");
        label.innerText = action.label;

        Element toggle = doc.createElement("div");
        toggle.setAttribute("class", action.isActive() ? "toggle-switch on" : "toggle-switch");

        Element knob = doc.createElement("div");
        knob.setAttribute("class", "knob");
        toggle.append(knob);

        toggle.addEventListener("mousedown", e -> {
            String cls = toggle.getAttribute("class");
            boolean wasOn = cls != null && cls.contains("on");
            toggle.setAttribute("class", wasOn ? "toggle-switch" : "toggle-switch on");
        });

        wrapper.append(label);
        wrapper.append(toggle);
        container.append(wrapper);
    }

    /**
     * 构建纵向 SLIDER 滑条。<br>
     * 每个滑条占据等宽列，从底部向上填充。
     */
    private static void buildVerticalSlider(Document doc, Element container, GuiSliderAction action, int index) {
        float rawValue = action.getValue();
        int pct = Math.round((rawValue - action.min) / (action.max - action.min) * 100);

        Element axis = doc.createElement("div");
        axis.setAttribute("class", "slider-axis");

        Element labelEl = doc.createElement("div");
        labelEl.setAttribute("class", "slider-axis-label");
        labelEl.innerText = action.label;

        Element track = doc.createElement("div");
        track.setAttribute("class", "slider-track-v");

        Element fill = doc.createElement("div");
        fill.setAttribute("class", "slider-fill-v");
        fill.setAttribute("style", "height:" + pct + "%");
        track.append(fill);

        Element valEl = doc.createElement("div");
        valEl.setAttribute("class", "slider-value-v");
        valEl.innerText = String.valueOf(Math.round(rawValue));

        track.addEventListener("mousedown", e -> {
            MouseEvent me = (MouseEvent) e;
            vSliderTrack = track;
            vSliderFill = fill;
            vSliderValue = valEl;
            vSliderStartY = me.clientY;
            vSliderStartPct = pct;
        });

        axis.append(labelEl);
        axis.append(track);
        axis.append(valEl);
        container.append(axis);
    }

    private static void onVSliderMouseMove(com.sighs.apricityui.init.Event e) {
        if (vSliderTrack == null) return;
        MouseEvent me = (MouseEvent) e;
        double delta = vSliderStartY - me.clientY;
        int pct = (int) Math.round(vSliderStartPct + (delta / 100.0) * 100);
        if (pct < 0) pct = 0;
        if (pct > 100) pct = 100;
        vSliderFill.setAttribute("style", "height:" + pct + "%");
        if (vSliderValue != null) {
            vSliderValue.innerText = String.valueOf(pct);
        }
    }

    private static void onVSliderMouseUp(com.sighs.apricityui.init.Event e) {
        vSliderTrack = null;
        vSliderFill = null;
        vSliderValue = null;
    }
}
