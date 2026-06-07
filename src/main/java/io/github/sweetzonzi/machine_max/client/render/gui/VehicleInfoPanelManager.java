package io.github.sweetzonzi.machine_max.client.render.gui;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.client.render.gui.panel.GroupStripRenderer;
import io.github.sweetzonzi.machine_max.common.mech.control.*;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 车辆信息面板（控制组编辑器）管理器。
 *
 * <p>HTML 模板只保留骨架与编辑页表单。控制组与 GUI 控件的卡片列表由 Java 在
 * {@link #initPanel(Document)} 中动态构建并注入对应容器，使数据与 UI 解耦。
 * </p>
 */
@EventBusSubscriber(modid = MachineMax.MOD_ID, value = Dist.CLIENT)
@OnlyIn(Dist.CLIENT)
public class VehicleInfoPanelManager {

    private static final String PANEL_PATH = "machine_max/vehicle_info_panel.html";
    private static int toastTimer = 0;

    /** 上次初始化的文档 UUID，用于检测文档实例是否变化（ESC 关闭再 TAB 打开时会创建新文档） */
    private static String lastDocUuid = null;

    /** 滑条拖拽状态 */
    private static Element dragTrack = null;
    private static Element dragFill = null;
    private static Element dragVal = null;
    private static double dragStartX = 0;
    private static int    dragStartPct = 50;
    private static final double TRACK_WIDTH = 48.0; // 与 CSS .widget-slider .track width 一致

    /**
     * 用于 GUI 测试的预置控制组集合。<br>
     * 包含 1 个 baseGroup（地面载具模式）、2 个子控制组（combat / cruise）和 3 个 GUI 交互元素。<br>
     * 默认激活 combat 子组（activeIndex = 0）。
     */
    private static ControlGroupSet FOR_GUI_TEST = null;

    /**
     * 获取 baseGroup + 全部子组的列表，用于卡片构建。
     */
    private static List<ControlGroup> getAllControlGroups(ControlGroupSet set) {
        return GroupStripRenderer.getAllGroups(set);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        if (FOR_GUI_TEST == null) {
            var baseGroup = new ControlGroup("base", ControlMode.GROUND,
                    Map.of(
                            "forward", List.of("main_engine"),
                            "steering", List.of("steering_gear")
                    ),
                    Map.of("camera", List.of("main_camera")),
                    Map.of("handbrake", List.of("brake_system")),
                    List.of(
                            new ControlBinding("key.w", BindingAction.PRESS, "forward", List.of("main_engine")),
                            new ControlBinding("key.s", BindingAction.PRESS, "brake", List.of("main_engine")),
                            new ControlBinding("key.a", BindingAction.PRESS, "steering", List.of("steering_gear")),
                            new ControlBinding("key.d", BindingAction.PRESS, "steering", List.of("steering_gear")),
                            new ControlBinding("key.space", BindingAction.HOLD, "handbrake", List.of("brake_system"))
                    )
            );

            var groups = List.of(
                    new ControlGroup("combat", ControlMode.INHERIT,
                            Map.of(), Map.of(), Map.of(),
                            List.of(
                                    new ControlBinding("mouse.left", BindingAction.PRESS, "fire_primary", List.of("turret")),
                                    new ControlBinding("mouse.right", BindingAction.TOGGLE, "aim_mode", List.of("turret")),
                                    new ControlBinding("key.r", BindingAction.PRESS, "reload", List.of("turret")),
                                    new ControlBinding("key.f", BindingAction.PRESS, "cycle_weapon", List.of("turret"))
                            )
                    ),
                    new ControlGroup("cruise", ControlMode.INHERIT,
                            Map.of(), Map.of(), Map.of(),
                            List.of(
                                    new ControlBinding("key.c", BindingAction.TOGGLE, "cruise_control", List.of("main_engine")),
                                    new ControlBinding("key.up", BindingAction.PRESS, "speed_up", List.of("main_engine"))
                            )
                    )
            );

            FOR_GUI_TEST = new ControlGroupSet(baseGroup, groups, List.of(
                    new GuiToggleAction("武器保险", "weapon_safety", List.of("turret")),
                    new GuiSliderAction("瞄准灵敏度", "aim_sensitivity", List.of("turret"),
                            0f, 100f, 1f, 50f),
                    new GuiPulseAction("武器切换", "weapon_cycle", List.of("turret"))
            ), 0);
        }
        List<Document> docs = ApricityUI.getDocument(PANEL_PATH);

        // 文档不存在 → 面板已关闭，重置状态
        if (docs.isEmpty()) {
            lastDocUuid = null;
            return;
        }

        // 玩家不再乘坐载具 → 自动关闭面板
        var player = Minecraft.getInstance().player;
        if (player == null || !(((IEntityMixin) player).machine_Max$getControllingSubsystem() instanceof SeatSubsystem)) {
            ApricityUI.closeScreen();
            lastDocUuid = null;
            return;
        }

        // 取最后一个文档（最新的），避免旧文档残留干扰
        Document doc = docs.getLast();
        if (doc == null || doc.body == null) {
            lastDocUuid = null;
            return;
        }

        // 文档实例变化（ESC 关闭后重新 TAB 打开会创建新文档）→ 重新初始化
        String currentUuid = doc.getUuid().toString();
        if (!currentUuid.equals(lastDocUuid)) {
            initPanel(doc);
            lastDocUuid = currentUuid;
        }

        // Toast 计时
        if (toastTimer > 0) {
            toastTimer--;
            if (toastTimer == 0) {
                hideToast(doc);
            }
        }
    }

    /** 初始化：构建卡片 + 绑定事件 */
    private static void initPanel(Document doc) {
        // --- 动态构建卡片列表 ---
        buildGroupCards(doc, FOR_GUI_TEST);
        buildGuiCards(doc, FOR_GUI_TEST);

        // --- 滑条拖拽：document 级 mousemove / mouseup ---
        doc.body.addEventListener("mousemove", e -> {
            if (dragTrack == null) return;
            var me = (MouseEvent) e;
            double delta = me.clientX - dragStartX;
            int pct = (int) Math.round(dragStartPct + (delta / TRACK_WIDTH) * 100);
            if (pct < 0) pct = 0;
            if (pct > 100) pct = 100;
            dragFill.setAttribute("style", "width:" + pct + "%");
            dragVal.innerText = pct + "%";
        });
        doc.body.addEventListener("mouseup", e -> {
            if (dragTrack != null) {
                showToast(doc, "已设为 " + dragVal.innerText);
            }
            dragTrack = null;
            dragFill = null;
            dragVal = null;
        });

        // --- 主页 ---
        bindClick(doc, "btn-add-group", e -> showToast(doc, "添加控制组（待实现）"));
        bindClick(doc, "btn-add-gui", e -> showToast(doc, "添加GUI控件（待实现）"));
        bindClick(doc, "btn-reset", e -> showToast(doc, "已重置为预设配置"));

        // 控制组卡片 → 编辑页
        for (ControlGroup group : getAllControlGroups(FOR_GUI_TEST)) {
            String name = group.name;
            bindClick(doc, "btn-config-" + name, e -> openGroupEdit(doc, group));
            bindClick(doc, "btn-del-" + name, e -> showToast(doc, "删除控制组: " + name + "（待实现）"));
        }

        // GUI 控件卡片 → 编辑页
        for (AbstractGuiAction action : FOR_GUI_TEST.getGuiActions()) {
            String label = action.label;
            bindClick(doc, "btn-gui-config-" + label, e -> openGuiEdit(doc, action));
            bindClick(doc, "btn-gui-del-" + label, e -> showToast(doc, "删除控件: " + label + "（待实现）"));
        }

        // --- 控制组编辑页 ---
        bindClick(doc, "btn-back-from-group", e -> showPage(doc, "page-main"));
        bindClick(doc, "btn-save-group", e -> { showToast(doc, "已保存"); showPage(doc, "page-main"); });
        bindClick(doc, "btn-cancel-group", e -> showPage(doc, "page-main"));

        // --- GUI 控件编辑页 ---
        bindClick(doc, "btn-back-from-gui", e -> showPage(doc, "page-main"));
        bindClick(doc, "btn-save-gui", e -> { showToast(doc, "已保存"); showPage(doc, "page-main"); });
        bindClick(doc, "btn-cancel-gui", e -> showPage(doc, "page-main"));
    }

    // ===== 动态构建卡片 =====

    /** 根据 ControlGroupSet 在 #group-list 中动态创建控制组卡片 */
    private static void buildGroupCards(Document doc, ControlGroupSet data) {
        Element groupList = doc.getElementById("group-list");
        if (groupList == null) return;

        List<ControlGroup> allGroups = getAllControlGroups(data);

        for (ControlGroup group : allGroups) {
            String name = group.name;
            String mode = group.controlMode.name();
            String desc = buildGroupDesc(group);

            // 卡片容器
            Element card = doc.createElement("div");
            card.setAttribute("class", "hud-card");
            card.setAttribute("id", "card-" + name);

            // 左侧装饰条
            Element leftBar = doc.createElement("div");
            leftBar.setAttribute("class", "left-bar");

            // 卡片主体
            Element body = doc.createElement("div");
            body.setAttribute("class", "hud-card-body");

            // 顶行：名称 + 模式标签
            Element top = doc.createElement("div");
            top.setAttribute("class", "card-top");

            Element nameEl = doc.createElement("div");
            nameEl.setAttribute("class", "card-name");
            nameEl.innerText = name;

            // baseGroup 额外显示"基础"角标
            if (group == data.baseGroup) {
                Element badge = doc.createElement("span");
                badge.setAttribute("class", "base-badge");
                badge.innerText = "基础";
                nameEl.append(badge);
            }

            Element modeEl = doc.createElement("span");
            modeEl.setAttribute("class", "card-mode " + modeToStyle(mode));
            modeEl.innerText = mode;

            top.append(nameEl);
            top.append(modeEl);

            // 描述行
            Element descEl = doc.createElement("div");
            descEl.setAttribute("class", "card-desc");
            descEl.innerText = desc;

            body.append(top);
            body.append(descEl);

            // 右侧按钮区
            Element side = doc.createElement("div");
            side.setAttribute("class", "hud-card-side");

            Element editBtn = doc.createElement("span");
            editBtn.setAttribute("class", "btn-config");
            editBtn.setAttribute("id", "btn-config-" + name);
            editBtn.innerText = "编辑";

            Element delBtn = doc.createElement("span");
            delBtn.setAttribute("class", "btn-icon-del");
            delBtn.setAttribute("id", "btn-del-" + name);
            delBtn.innerText = "×";

            side.append(editBtn);
            side.append(delBtn);

            card.append(leftBar);
            card.append(body);
            card.append(side);
            groupList.append(card);
        }

        // 更新计数
        Element countEl = doc.getElementById("group-count");
        if (countEl != null) countEl.innerText = String.valueOf(allGroups.size());
    }

    /** 构建控制组的描述文字 */
    private static String buildGroupDesc(ControlGroup group) {
        int bindings = group.bindings.size();
        int channels = group.moveTargets.size()
                + group.viewTargets.size()
                + group.regularTargets.size();
        return bindings + " 个绑定 · " + channels + " 个输出频道";
    }

    /** 根据 ControlGroupSet 的 guiActions 在 #gui-list 中动态创建 GUI 控件卡片 */
    private static void buildGuiCards(Document doc, ControlGroupSet data) {
        Element guiList = doc.getElementById("gui-list");
        if (guiList == null) return;

        List<AbstractGuiAction> actions = data.getGuiActions();

        for (AbstractGuiAction action : actions) {
            String label = action.label;
            String channel = action.channel;
            String type = action.type().name().toLowerCase();

            // 卡片容器
            Element card = doc.createElement("div");
            card.setAttribute("class", "ctrl-card");
            card.setAttribute("id", "card-gui-" + label);

            // 控件信息体
            Element body = doc.createElement("div");
            body.setAttribute("class", "ctrl-body");

            Element labelEl = doc.createElement("div");
            labelEl.setAttribute("class", "ctrl-label");
            labelEl.innerText = label;

            Element chEl = doc.createElement("div");
            chEl.setAttribute("class", "ctrl-channel");
            chEl.innerText = channel;

            body.append(labelEl);
            body.append(chEl);

            // 微件预览
            Element widget = doc.createElement("div");
            widget.setAttribute("class", "ctrl-widget");
            buildWidget(doc, widget, type, label, channel);

            // 右侧按钮区
            Element actionsEl = doc.createElement("div");
            actionsEl.setAttribute("class", "ctrl-actions");

            Element editBtn = doc.createElement("span");
            editBtn.setAttribute("class", "btn-config");
            editBtn.setAttribute("id", "btn-gui-config-" + label);
            editBtn.innerText = "编辑";

            Element delBtn = doc.createElement("span");
            delBtn.setAttribute("class", "btn-icon-del");
            delBtn.setAttribute("id", "btn-gui-del-" + label);
            delBtn.innerText = "×";

            actionsEl.append(editBtn);
            actionsEl.append(delBtn);

            card.append(body);
            card.append(widget);
            card.append(actionsEl);
            guiList.append(card);
        }

        // 更新计数
        Element countEl = doc.getElementById("gui-count");
        if (countEl != null) countEl.innerText = String.valueOf(actions.size());
    }

    /** 根据控件类型构建可交互的微件 DOM 子树，追加到 widget 容器中 */
    private static void buildWidget(Document doc, Element widget, String type,
                                    String label, String channel) {
        switch (type) {
            case "toggle" -> {
                Element toggle = doc.createElement("div");
                toggle.setAttribute("class", "widget-toggle on");
                Element knob = doc.createElement("div");
                knob.setAttribute("class", "knob");
                toggle.append(knob);

                // 点击切换开/关
                toggle.addEventListener("mousedown", e -> {
                    String cls = toggle.getAttribute("class");
                    boolean wasOn = cls != null && cls.contains("on");
                    toggle.setAttribute("class", wasOn ? "widget-toggle" : "widget-toggle on");
                    showToast(doc, label + ": " + (wasOn ? "OFF" : "ON"));
                });
                widget.append(toggle);
            }
            case "slider" -> {
                Element slider = doc.createElement("div");
                slider.setAttribute("class", "widget-slider");

                Element track = doc.createElement("div");
                track.setAttribute("class", "track");

                Element fill = doc.createElement("div");
                fill.setAttribute("class", "fill");
                fill.setAttribute("style", "width:65%");
                track.append(fill);

                Element val = doc.createElement("span");
                val.setAttribute("class", "val");
                val.innerText = "65%";

                // mousedown 开始拖拽
                track.addEventListener("mousedown", e -> {
                    var me = (MouseEvent) e;
                    dragTrack = track;
                    dragFill = fill;
                    dragVal = val;
                    dragStartX = me.clientX;
                    // 从当前 fill 宽度反推百分比
                    String style = fill.getAttribute("style");
                    dragStartPct = 65;
                    if (style != null && style.contains("width:")) {
                        try {
                            String p = style.replaceAll("[^0-9]", "");
                            if (!p.isEmpty()) dragStartPct = Integer.parseInt(p);
                        } catch (NumberFormatException ignored) {}
                    }
                });
                slider.append(track);
                slider.append(val);
                widget.append(slider);
            }
            case "pulse" -> {
                Element pulse = doc.createElement("div");
                pulse.setAttribute("class", "widget-pulse");
                pulse.innerText = "发射";

                // 点击发射脉冲
                pulse.addEventListener("mousedown", e -> {
                    showToast(doc, "发送脉冲 → " + channel);
                });
                widget.append(pulse);
            }
        }
    }

    /** 控制模式 → CSS 类名后缀 */
    private static String modeToStyle(String mode) {
        return switch (mode) {
            case "GROUND" -> "ground";
            case "PLANE" -> "plane";
            case "SHIP" -> "ship";
            case "MECH" -> "mech";
            default -> "inherit";
        };
    }

    // ===== 页面路由 =====

    private static void showPage(Document doc, String pageId) {
        Element panelBody = doc.getElementById("panel-body");
        if (panelBody == null) return;

        // 切页前立即滚回顶部，避免过渡期间出现滚动动画
        panelBody.scrollTop = 0;

        for (Element child : panelBody.children) {
            if (child.getClassNames().contains("page")) {
                String cls = child.id.equals(pageId) ? "page active" : "page";
                child.setAttribute("class", cls);
            }
        }
    }

    /** 打开控制组编辑页，填充表单 */
    private static void openGroupEdit(Document doc, ControlGroup group) {
        setValue(doc, "ge-name", group.name);
        setSelect(doc, "ge-mode", group.controlMode.name());
        showPage(doc, "page-group-edit");
    }

    /** 打开 GUI 控件编辑页，填充表单 */
    private static void openGuiEdit(Document doc, AbstractGuiAction action) {
        setValue(doc, "ge-label", action.label);
        setValue(doc, "ge-channel-input", action.channel);
        setSelect(doc, "ge-type", action.type().name().toLowerCase());
        showPage(doc, "page-gui-edit");
    }

    // ===== DOM 操作工具 =====

    private static void bindClick(Document doc, String elementId,
                                  java.util.function.Consumer<com.sighs.apricityui.init.Event> handler) {
        Element el = doc.getElementById(elementId);
        if (el != null) {
            el.addEventListener("mousedown", handler);
        }
    }

    /** 设置 input 元素的 value 属性 */
    private static void setValue(Document doc, String id, String value) {
        Element el = doc.getElementById(id);
        if (el != null) {
            el.setAttribute("value", value);
        }
    }

    /** 设置 select 元素的选中项 */
    private static void setSelect(Document doc, String id, String value) {
        Element el = doc.getElementById(id);
        if (el == null) return;
        // 遍历 option 子元素，匹配 value 的设为 selected
        for (Element opt : el.children) {
            String optVal = opt.getAttribute("value");
            if (optVal == null) optVal = opt.innerText;
            if (optVal.equals(value)) {
                opt.setAttribute("selected", "selected");
            } else {
                opt.removeAttribute("selected");
            }
        }
    }

    // ===== Toast =====

    private static void showToast(Document doc, String message) {
        Element toast = doc.getElementById("toast");
        if (toast == null) return;
        toast.innerText = message;
        toast.setAttribute("class", "toast show");
        toastTimer = 40;
    }

    private static void hideToast(Document doc) {
        if (doc == null) return;
        Element toast = doc.getElementById("toast");
        if (toast != null) {
            toast.setAttribute("class", "toast");
        }
    }
}
