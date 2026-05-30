package io.github.sweetzonzi.machine_max.client.render.gui.panel;

import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.mojang.logging.LogUtils;
import io.github.sweetzonzi.machine_max.common.mech.control.*;
import io.github.sweetzonzi.machine_max.common.mech.control.AbstractGuiAction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

import java.util.List;

/**
 * Tab 02 设备控制面板。<br>
 * 显示控制组切换条、ACTIONS 按钮区、TOGGLES 开关区和 AXES 纵向滑条区。
 * 滑条使用纵向布局，从底部向上填充。
 */
@OnlyIn(Dist.CLIENT)
public class PanelDeviceControl {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 滑条拖拽状态（静态字段，同一时间只能拖一个滑条） */
    private static Element dragTrack;
    private static Element dragFill;
    private static Element dragValue;
    private static double dragStartY;
    private static int dragStartPct;
    /** 当前拖拽的滑条对应的 GUI 操作索引，用于发送网络包 */
    private static int dragActionIndex = -1;

    /** 竖向滑条参考高度（px），用于将像素偏移转换为百分比 */
    private static final double VSLIDER_TRACK_HEIGHT = 100.0;

    /** 全局已注册的事件守卫，避免重复绑定 body 级监听器 */
    private static boolean bodyListenersRegistered = false;

    public static void render(Document doc, ControlGroupSet data) {
        GroupStripRenderer.render(doc, data, "group-strip-1", "#2E5A90");
        buildDeviceSections(doc, data);

        // body 级拖拽事件只注册一次
        if (!bodyListenersRegistered && doc.body != null) {
            doc.body.addEventListener("mousemove", PanelDeviceControl::onVSliderMouseMove);
            doc.body.addEventListener("mouseup", PanelDeviceControl::onVSliderMouseUp);
            bodyListenersRegistered = true;
        }
    }

    private static void buildDeviceSections(Document doc, ControlGroupSet data) {
        List<AbstractGuiAction> actions = data.getGuiActions();
        if (actions == null) return;

        Element pulseSection = doc.getElementById("pulse-section");
        Element pulseSectionContent = doc.getElementById("pulse-section-content");
        Element toggleSection = doc.getElementById("toggle-section");
        Element toggleSectionContent = doc.getElementById("toggle-section-content");
        Element sliderSection = doc.getElementById("slider-section");
        Element sliderSectionContent = doc.getElementById("slider-section-content");

        Element sliderAxesRow = null;
        if (sliderSectionContent != null) {
            sliderAxesRow = doc.createElement("div");
            sliderAxesRow.setAttribute("class", "slider-axes-row");
            sliderSectionContent.append(sliderAxesRow);
        }

        for (int i = 0; i < actions.size(); i++) {
            AbstractGuiAction action = actions.get(i);
            String type = action.type().name().toLowerCase();
            switch (type) {
                case "pulse" -> {
                    if (pulseSectionContent != null) {
                        GuiPulseAction pulse = (GuiPulseAction) action;
                        buildPulseButton(doc, pulseSectionContent, pulse, i);
                    }
                }
                case "toggle" -> {
                    if (toggleSectionContent != null) {
                        GuiToggleAction toggle = (GuiToggleAction) action;
                        buildToggleSwitch(doc, toggleSectionContent, toggle, i);
                    }
                }
                case "slider" -> {
                    if (sliderAxesRow != null) {
                        GuiSliderAction slider = (GuiSliderAction) action;
                        buildVerticalSlider(doc, sliderAxesRow, slider, i);
                    }
                }
            }
        }

        // 标题 prepend 到外层容器，不入 col-content
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
     * 构建 PULSE 按钮。<br>
     * 点击时短暂高亮（active class），松开后恢复。
     * TODO: 发送网络包 GuiActionPayload 通知服务端执行脉冲操作
     */
    private static void buildPulseButton(Document doc, Element container, GuiPulseAction action, int index) {
        Element btn = doc.createElement("div");
        btn.setAttribute("class", "btn-pulse");
        btn.innerText = action.label;

        btn.addEventListener("mousedown", e -> {
            if (!(e instanceof MouseEvent me) || me.button != 0) return;
            btn.setAttribute("class", "btn-pulse active");
            LOGGER.debug("[DeviceControl] PULSE #{} '{}'", index, action.label);
            // TODO: 发送网络包 — GuiActionPayload
            // new GuiActionPayload(subPartId, subSystemName, index, GuiActionType.PULSE, 0f)
            // 服务端收到后执行对应脉冲信号
        });
        btn.addEventListener("mouseup", e -> {
            btn.setAttribute("class", "btn-pulse");
        });
        // 鼠标移出按钮时也清除高亮
        btn.addEventListener("mousemove", e -> {
            // AUI 中 mousemove 持续触发，不做复杂判断
        });

        container.append(btn);
    }

    /**
     * 构建 TOGGLE 开关。<br>
     * 点击在 ON/OFF 间切换，通过内联 style 直接控制 knob 位置。
     * <p>
     * AUI 中修改父元素 class 不会自动触发子元素的 CSS 选择器重匹配，
     * 因此 knob 的 left/background 必须用内联 style 直接写入，不可依赖 CSS。
     * </p>
     * TODO: 发送网络包 GuiActionPayload 同步开关状态
     */
    private static void buildToggleSwitch(Document doc, Element container, GuiToggleAction action, int index) {
        Element wrapper = doc.createElement("div");
        wrapper.setAttribute("class", "toggle-row");

        Element label = doc.createElement("span");
        label.setAttribute("class", "toggle-label");
        label.innerText = action.label;

        boolean initiallyOn = action.isActive();
        Element toggle = doc.createElement("div");
        toggle.setAttribute("class", "toggle-switch");
        // 底色由内联 style 控制（AUI class 变化不触发布局重算），配合 CSS transition 做动效
        toggle.setAttribute("style", "background-color:" + (initiallyOn ? "#4A8FD0" : "#0A1424") + ";");

        Element knob = doc.createElement("div");
        knob.setAttribute("class", "knob");
        // knob 位置也用内联 style，CSS transition 对其生效
        knob.setAttribute("style", initiallyOn
                ? "left:17px;background:#fff;"
                : "left:3px;background:#ddd;");
        toggle.append(knob);

        // 绑定在 wrapper 上，避免 knob 拦截点击
        wrapper.addEventListener("mousedown", e -> {
            if (!(e instanceof MouseEvent me) || me.button != 0) return;
            String toggleStyle = toggle.getAttribute("style");
            boolean wasOn = toggleStyle != null && toggleStyle.contains("#4A8FD0");
            toggle.setAttribute("style", "background-color:" + (wasOn ? "#0A1424" : "#4A8FD0") + ";");
            knob.setAttribute("style", wasOn
                    ? "left:3px;background:#ddd;"
                    : "left:17px;background:#fff;");
            LOGGER.debug("[DeviceControl] TOGGLE #{} '{}' {} -> {}",
                    index, action.label, wasOn ? "ON" : "OFF", wasOn ? "OFF" : "ON");
            // TODO: 发送网络包 — GuiActionPayload
            // new GuiActionPayload(subPartId, subSystemName, index, GuiActionType.TOGGLE, wasOn ? 0f : 1f)
            // 服务端收到后更新 ToggleAction 的 isActive 状态
        });

        wrapper.append(label);
        wrapper.append(toggle);
        container.append(wrapper);
    }

    /**
     * 构建纵向 SLIDER 滑条。<br>
     * 每个滑条占据等宽列，从底部向上填充。
     * 使用 mousedown → body mousemove → body mouseup 三段式拖拽。
     * TODO: 发送网络包 GuiActionPayload 同步滑条数值
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
            if (!(e instanceof MouseEvent me) || me.button != 0) return;
            // 从 fill 当前 style 中读取起始百分比
            String fillStyle = fill.getAttribute("style");
            int currentPct = pct;
            if (fillStyle != null && fillStyle.contains("height:")) {
                try {
                    String numStr = fillStyle.replaceAll("[^0-9]", "");
                    if (!numStr.isEmpty()) currentPct = Integer.parseInt(numStr);
                } catch (NumberFormatException ignored) {}
            }
            dragTrack = track;
            dragFill = fill;
            dragValue = valEl;
            dragStartY = me.clientY;
            dragStartPct = currentPct;
            dragActionIndex = index;
        });

        axis.append(labelEl);
        axis.append(track);
        axis.append(valEl);
        container.append(axis);
    }

    /**
     * Body 级鼠标移动事件：更新滑条填充百分比。<br>
     * 使用 VSLIDER_TRACK_HEIGHT 将像素偏移转化为百分比变化。
     */
    private static void onVSliderMouseMove(com.sighs.apricityui.init.Event e) {
        if (dragTrack == null) return;
        MouseEvent me = (MouseEvent) e;
        double delta = dragStartY - me.clientY;
        int pct = (int) Math.round(dragStartPct + (delta / VSLIDER_TRACK_HEIGHT) * 100);
        if (pct < 0) pct = 0;
        if (pct > 100) pct = 100;
        dragFill.setAttribute("style", "height:" + pct + "%");
        if (dragValue != null) {
            dragValue.innerText = String.valueOf(pct);
        }
    }

    /**
     * Body 级鼠标释放事件：结束滑条拖拽。<br>
     * TODO: 发送网络包 GuiActionPayload 同步滑条最终值
     */
    private static void onVSliderMouseUp(com.sighs.apricityui.init.Event e) {
        if (dragTrack != null && dragActionIndex >= 0) {
            // 从 fill 读取最终百分比
            String fillStyle = dragFill.getAttribute("style");
            int finalPct = 50;
            if (fillStyle != null && fillStyle.contains("height:")) {
                try {
                    String numStr = fillStyle.replaceAll("[^0-9]", "");
                    if (!numStr.isEmpty()) finalPct = Integer.parseInt(numStr);
                } catch (NumberFormatException ignored) {}
            }
            LOGGER.debug("[DeviceControl] SLIDER #{} finalPct={}", dragActionIndex, finalPct);
            // TODO: 发送网络包 — GuiActionPayload
            // new GuiActionPayload(subPartId, subSystemName, dragActionIndex, GuiActionType.SLIDER, (float)finalPct)
            // 服务端收到后更新 SliderAction 的 value 字段
        }
        dragTrack = null;
        dragFill = null;
        dragValue = null;
        dragActionIndex = -1;
    }

    /**
     * 重置静态状态，Screen 关闭时调用。<br>
     * 清空拖拽状态和 body 事件注册标志，
     * 避免旧 Document 残留导致新 Document 中事件不触发或拖拽引用悬挂。
     */
    public static void reset() {
        bodyListenersRegistered = false;
        dragTrack = null;
        dragFill = null;
        dragValue = null;
        dragActionIndex = -1;
    }
}
