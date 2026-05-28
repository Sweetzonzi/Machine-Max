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
 * 显示控制组切换条、PULSE 按钮区、TOGGLE 开关区和 SLIDER 滑块区。
 * 控件交互遵循 AUI 能力边界：用 mousedown/mousemove/mouseup 实现拖拽，不依赖 getBoundingClientRect。
 */
@OnlyIn(Dist.CLIENT)
public class PanelDeviceControl {

    private static Element sliderTrack;
    private static Element sliderFill;
    private static Element sliderValue;
    private static double sliderStartX;
    private static int sliderStartPct;
    private static final double SLIDER_WIDTH = 160.0;

    public static void render(Document doc, ControlGroupSet data) {
        GroupStripRenderer.render(doc, data, "group-strip-1");
        buildDeviceSections(doc, data);

        doc.body.addEventListener("mousemove", PanelDeviceControl::onSliderMouseMove);
        doc.body.addEventListener("mouseup", PanelDeviceControl::onSliderMouseUp);
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
                        buildSliderWidget(doc, sliderSection, slider, i);
                    }
                }
            }
        }

        if (pulseSection != null) {
            Element title = doc.createElement("div");
            title.setAttribute("class", "device-section-title");
            title.innerText = "PULSE";
            pulseSection.prepend(title);
        }
        if (toggleSection != null) {
            Element title = doc.createElement("div");
            title.setAttribute("class", "device-section-title");
            title.innerText = "TOGGLE";
            toggleSection.prepend(title);
        }
        if (sliderSection != null) {
            Element title = doc.createElement("div");
            title.setAttribute("class", "device-section-title");
            title.innerText = "SLIDER";
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
            String origBg = btn.getAttribute("style");
            btn.setAttribute("style", "background:rgba(46,90,144,0.4);");
            btn.addEventListener("mouseup", e2 -> {
                btn.setAttribute("style", origBg != null ? origBg : "");
            });
        });

        container.append(btn);
    }

    /**
     * 构建 TOGGLE 开关。
     */
    private static void buildToggleSwitch(Document doc, Element container, GuiToggleAction action, int index) {
        Element wrapper = doc.createElement("div");
        wrapper.setAttribute("style", "display:flex;align-items:center;gap:6px;");

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
     * 构建 SLIDER 滑块。
     */
    private static void buildSliderWidget(Document doc, Element container, GuiSliderAction action, int index) {
        float rawValue = action.getValue();
        int pct = Math.round((rawValue - action.min) / (action.max - action.min) * 100);

        Element widget = doc.createElement("div");
        widget.setAttribute("class", "slider-widget");

        Element header = doc.createElement("div");
        header.setAttribute("class", "slider-header");

        Element labelEl = doc.createElement("span");
        labelEl.innerText = action.label;

        Element valEl = doc.createElement("span");
        valEl.setAttribute("class", "slider-value");
        valEl.innerText = String.valueOf(Math.round(rawValue));

        header.append(labelEl);
        header.append(valEl);

        Element track = doc.createElement("div");
        track.setAttribute("class", "slider-track");

        Element fill = doc.createElement("div");
        fill.setAttribute("class", "slider-fill");
        fill.setAttribute("style", "width:" + pct + "%");

        track.append(fill);

        track.addEventListener("mousedown", e -> {
            MouseEvent me = (MouseEvent) e;
            sliderTrack = track;
            sliderFill = fill;
            sliderValue = valEl;
            sliderStartX = me.clientX;
            String style = fill.getAttribute("style");
            sliderStartPct = pct;
            if (style != null && style.contains("width:")) {
                try {
                    String p = style.replaceAll("[^0-9]", "");
                    if (!p.isEmpty()) sliderStartPct = Integer.parseInt(p);
                } catch (NumberFormatException ignored) {}
            }
        });

        widget.append(header);
        widget.append(track);
        container.append(widget);
    }

    private static void onSliderMouseMove(com.sighs.apricityui.init.Event e) {
        if (sliderTrack == null) return;
        MouseEvent me = (MouseEvent) e;
        double delta = me.clientX - sliderStartX;
        int pct = (int) Math.round(sliderStartPct + (delta / SLIDER_WIDTH) * 100);
        if (pct < 0) pct = 0;
        if (pct > 100) pct = 100;
        sliderFill.setAttribute("style", "width:" + pct + "%");
        if (sliderValue != null) {
            sliderValue.innerText = String.valueOf(pct);
        }
    }

    private static void onSliderMouseUp(com.sighs.apricityui.init.Event e) {
        sliderTrack = null;
        sliderFill = null;
        sliderValue = null;
    }
}
