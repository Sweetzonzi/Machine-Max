package io.github.sweetzonzi.machine_max.client.render.gui;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.List;

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
    private static boolean initialized = false;
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

    // 控制组名称 → 编辑页表单数据的映射
    private static final String[][] GROUP_DATA = {
            {"base",   "GROUND",  "始终激活 · 2 个绑定 · 3 个输出频道"},
            {"combat", "INHERIT", "战斗模式 · 4 个绑定 · 2 个输出频道"},
            {"cruise", "INHERIT", "巡航模式 · 2 个绑定 · 1 个输出频道"}
    };

    // GUI 控件名称 → 编辑页表单数据的映射
    private static final String[][] GUI_DATA = {
            {"武器保险",   "weapon_safety",   "toggle"},
            {"瞄准灵敏度", "aim_sensitivity", "slider"},
            {"武器切换",   "weapon_cycle",    "pulse"}
    };

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        List<Document> docs = ApricityUI.getDocument(PANEL_PATH);

        // 文档不存在 → 面板已关闭，重置状态
        if (docs == null || docs.isEmpty()) {
            initialized = false;
            lastDocUuid = null;
            return;
        }

        // 玩家不再乘坐载具 → 自动关闭面板
        var player = Minecraft.getInstance().player;
        if (player == null || !(((IEntityMixin) player).machine_Max$getControllingSubsystem() instanceof SeatSubsystem)) {
            ApricityUI.closeScreen();
            initialized = false;
            lastDocUuid = null;
            return;
        }

        // 取最后一个文档（最新的），避免旧文档残留干扰
        Document doc = docs.get(docs.size() - 1);
        if (doc == null || doc.body == null) {
            initialized = false;
            lastDocUuid = null;
            return;
        }

        // 文档实例变化（ESC 关闭后重新 TAB 打开会创建新文档）→ 重新初始化
        String currentUuid = doc.getUuid().toString();
        if (!currentUuid.equals(lastDocUuid)) {
            initPanel(doc);
            initialized = true;
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
        buildGroupCards(doc);
        buildGuiCards(doc);

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
        for (String[] g : GROUP_DATA) {
            String name = g[0];
            bindClick(doc, "btn-config-" + name, e -> openGroupEdit(doc, name));
            bindClick(doc, "btn-del-" + name, e -> showToast(doc, "删除控制组: " + name + "（待实现）"));
        }

        // GUI 控件卡片 → 编辑页
        for (String[] g : GUI_DATA) {
            String name = g[0];
            bindClick(doc, "btn-gui-config-" + name, e -> openGuiEdit(doc, name));
            bindClick(doc, "btn-gui-del-" + name, e -> showToast(doc, "删除控件: " + name + "（待实现）"));
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

    /** 根据 GROUP_DATA 在 #group-list 中动态创建控制组卡片 */
    private static void buildGroupCards(Document doc) {
        Element groupList = doc.getElementById("group-list");
        if (groupList == null) return;

        for (String[] g : GROUP_DATA) {
            String name = g[0];
            String mode = g[1];
            String desc = g[2];

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

            // base 组额外显示"基础"角标
            if ("base".equals(name)) {
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
        if (countEl != null) countEl.innerText = String.valueOf(GROUP_DATA.length);
    }

    /** 根据 GUI_DATA 在 #gui-list 中动态创建 GUI 控件卡片 */
    private static void buildGuiCards(Document doc) {
        Element guiList = doc.getElementById("gui-list");
        if (guiList == null) return;

        for (String[] g : GUI_DATA) {
            String label = g[0];
            String channel = g[1];
            String type = g[2];

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
            Element actions = doc.createElement("div");
            actions.setAttribute("class", "ctrl-actions");

            Element editBtn = doc.createElement("span");
            editBtn.setAttribute("class", "btn-config");
            editBtn.setAttribute("id", "btn-gui-config-" + label);
            editBtn.innerText = "编辑";

            Element delBtn = doc.createElement("span");
            delBtn.setAttribute("class", "btn-icon-del");
            delBtn.setAttribute("id", "btn-gui-del-" + label);
            delBtn.innerText = "×";

            actions.append(editBtn);
            actions.append(delBtn);

            card.append(body);
            card.append(widget);
            card.append(actions);
            guiList.append(card);
        }

        // 更新计数
        Element countEl = doc.getElementById("gui-count");
        if (countEl != null) countEl.innerText = String.valueOf(GUI_DATA.length);
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
            case "INHERIT" -> "inherit";
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

    private static void openGroupEdit(Document doc, String groupName) {
        // 查找控制组数据并填充表单
        for (String[] g : GROUP_DATA) {
            if (g[0].equals(groupName)) {
                setValue(doc, "ge-name", g[0]);
                setSelect(doc, "ge-mode", g[1]);
                break;
            }
        }
        showPage(doc, "page-group-edit");
    }

    private static void openGuiEdit(Document doc, String guiName) {
        for (String[] g : GUI_DATA) {
            if (g[0].equals(guiName)) {
                setValue(doc, "ge-label", g[0]);
                setValue(doc, "ge-channel-input", g[1]);
                setSelect(doc, "ge-type", g[2]);
                break;
            }
        }
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
