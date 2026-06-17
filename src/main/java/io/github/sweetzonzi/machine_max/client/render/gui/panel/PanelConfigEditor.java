package io.github.sweetzonzi.machine_max.client.render.gui.panel;

import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.mojang.logging.LogUtils;
import io.github.sweetzonzi.machine_max.common.mech.control.*;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Tab 03 编辑配置面板。<br>
 * 三栏布局：左栏控制组列表（CRUD）、中栏组详情+绑定列表+GUI列表、右栏详情编辑表单。<br>
 * 所有编辑操作使用内部可变副本，在保存时构造不可变的 ControlGroupSet 发送到服务端。
 */
@OnlyIn(Dist.CLIENT)
public class PanelConfigEditor {

    private static final Logger LOGGER = LogUtils.getLogger();

    // ============================================================
    //  可变工作副本（由于 ControlGroup/ControlBinding 的不可变性，
    //  用内部数据类存储编辑器中的变更）
    // ============================================================

    /** 当前选中的控制组索引，0 = baseGroup，1+ = subGroups */
    private static int selGroup = 0;
    /** 当前选中的按键绑定索引，-1 = 无 */
    private static int selBinding = -1;
    /** 当前选中的 GUI 交互元素索引，-1 = 无 */
    private static int selAction = -1;

    /** 编辑器级别的工作数据副本 */
    private static List<GroupEditData> groupList;
    private static List<ActionEditData> actionList;
    private static int editActiveIndex;

    /** 是否已初始化（从 ControlGroupSet 加载了一次数据） */
    private static boolean initialized = false;

    // ============================================================
    //  内部可变数据类
    // ============================================================

    /** 控制组可变副本 */
    private static class GroupEditData {
        String name;
        String controlMode;         // "INHERIT"/"GROUND"/"FLIGHT"/"SPACE"/"NAUTICAL"
        List<BindingEditData> bindings = new ArrayList<>();

        GroupEditData(String name, String controlMode) {
            this.name = name;
            this.controlMode = controlMode;
        }
    }

    /** 按键绑定可变副本 */
    private static class BindingEditData {
        String trigger;
        String action;              // "PRESS"/"HOLD"/"TOGGLE"
        String channel;
        List<String> targets;
        boolean toggleState;

        BindingEditData(String trigger, String action, String channel, List<String> targets) {
            this.trigger = trigger;
            this.action = action;
            this.channel = channel;
            this.targets = new ArrayList<>(targets);
        }
    }

    /** GUI 交互元素可变副本 */
    private static class ActionEditData {
        String type;                // "PULSE"/"TOGGLE"/"SLIDER"
        String label;
        String channel;
        List<String> targets;
        boolean active;             // TOGGLE 专用
        float value;                // SLIDER 专用
        float min;
        float max;
        float step;

        ActionEditData(String type, String label, String channel, List<String> targets) {
            this.type = type;
            this.label = label;
            this.channel = channel;
            this.targets = new ArrayList<>(targets);
        }
    }

    // ============================================================
    //  初始化：从 ControlGroupSet 构建可变副本
    // ============================================================

    /**
     * 从 ControlGroupSet 加载数据到内部可变副本。<br>
     * 只在首次渲染时执行一次，后续编辑在副本上操作。
     */
    private static void ensureInitialized(ControlGroupSet data) {
        if (initialized) return;
        groupList = new ArrayList<>();
        actionList = new ArrayList<>();

        // baseGroup
        GroupEditData base = new GroupEditData(
                data.getBaseGroup().name,
                data.getBaseGroup().controlMode.name()
        );
        for (ControlBinding b : data.getBaseGroup().bindings) {
            base.bindings.add(new BindingEditData(
                    b.trigger, b.action.name(), b.channel, b.targets
            ));
        }
        groupList.add(base);

        // sub groups
        for (ControlGroup g : data.getGroups()) {
            GroupEditData ged = new GroupEditData(g.name, g.controlMode.name());
            for (ControlBinding b : g.bindings) {
                ged.bindings.add(new BindingEditData(
                        b.trigger, b.action.name(), b.channel, b.targets
                ));
            }
            groupList.add(ged);
        }

        // GUI actions
        for (AbstractGuiAction a : data.getGuiActions()) {
            ActionEditData aed = new ActionEditData(
                    a.type().name(), a.label, a.channel, a.targets
            );
            if (a instanceof GuiToggleAction ta) {
                aed.active = ta.isActive();
            } else if (a instanceof GuiSliderAction sa) {
                aed.min = sa.min;
                aed.max = sa.max;
                aed.step = sa.step;
                aed.value = sa.getValue();
            }
            actionList.add(aed);
        }

        editActiveIndex = data.getActiveIndex();
        initialized = true;
    }

    // ============================================================
    //  主入口
    // ============================================================

    public static void render(Document doc, ControlGroupSet data) {
        ensureInitialized(data);
        buildLeftColumn(doc);
        buildMiddleColumn(doc);
        buildRightColumn(doc);
    }

    // ============================================================
    //  左栏：控制组列表（CRUD）
    // ============================================================

    /**
     * 左栏：控制组列表。<br>
     * - header: "GROUPS" + 添加按钮<br>
     * - 列表：baseGroup（蓝色左边框标志）+ 子组列表（hover 显示删除按钮）<br>
     * - footer: "RESET PRESET" 重置按钮
     */
    private static void buildLeftColumn(Document doc) {
        Element left = doc.getElementById("config-left");
        if (left == null) return;
        // 清空
        while (!left.children.isEmpty()) left.children.getFirst().remove();

        // ── 标题行 ──
        Element titleRow = doc.createElement("div");
        titleRow.setAttribute("class", "config-left-title-row");
        Element title = doc.createElement("span");
        title.setAttribute("class", "config-left-title");
        title.innerText = "GROUPS";
        Element addBtn = doc.createElement("div");
        addBtn.setAttribute("class", "config-left-btn-add");
        addBtn.innerText = "+";
        addBtn.addEventListener("mousedown", e -> {
            if (!(e instanceof MouseEvent me) || me.button != 0) return;
            onAddGroup();
            render(doc, null);
        });
        titleRow.append(title);
        titleRow.append(addBtn);
        left.append(titleRow);

        // ── 控制组列表 ──
        Element listWrap = doc.createElement("div");
        listWrap.setAttribute("class", "config-group-list");
        for (int i = 0; i < groupList.size(); i++) {
            GroupEditData g = groupList.get(i);
            boolean isBase = i == 0;
            boolean isSelected = i == selGroup;

            Element item = doc.createElement("div");
            String itemClass = "group-list-item";
            if (isBase) itemClass += " base-item";
            if (isSelected) itemClass += " selected";
            item.setAttribute("class", itemClass);
            int fi = i;

            // 上排行：名称 + 编号 + 删除按钮
            Element topRow = doc.createElement("div");
            topRow.setAttribute("class", "gli-top-row");

            Element nameEl = doc.createElement("span");
            nameEl.setAttribute("class", "gli-name");
            nameEl.innerText = g.name;

            Element numEl = doc.createElement("span");
            numEl.setAttribute("class", "gli-number");
            numEl.innerText = isBase ? "BASE" : "GROUP " + String.format("%02d", i - 1);

            // 删除按钮（base 组没有）
            Element delBtn = doc.createElement("div");
            delBtn.setAttribute("class", "gli-del-btn");
            delBtn.innerText = "×";
            if (!isBase) {
                delBtn.addEventListener("mousedown", ev -> {
                    if (!(ev instanceof MouseEvent me) || me.button != 0) return;
                    ev.stopPropagation();
                    onDeleteGroup(fi);
                    render(doc, null);
                });
            }

            topRow.append(nameEl);
            topRow.append(numEl);
            if (!isBase) topRow.append(delBtn);
            item.append(topRow);

            // 下排元信息
            Element metaEl = doc.createElement("div");
            metaEl.setAttribute("class", "gli-meta");
            if (isBase) {
                Element badge = doc.createElement("span");
                badge.setAttribute("class", "gli-base-badge");
                badge.innerText = "ALWAYS ACTIVE";
                metaEl.append(badge);
            } else {
                metaEl.innerText = g.bindings.size() + " BINDINGS  ·  " + g.controlMode;
            }
            item.append(metaEl);

            // 选中回调
            item.addEventListener("mousedown", ev -> {
                if (!(ev instanceof MouseEvent me) || me.button != 0) return;
                selGroup = fi;
                selBinding = -1;
                selAction = -1;
                render(doc, null);
            });

            listWrap.append(item);
        }
        left.append(listWrap);

        // ── 底部重置按钮 ──
        Element footer = doc.createElement("div");
        footer.setAttribute("class", "config-left-footer");
        Element resetBtn = doc.createElement("div");
        resetBtn.setAttribute("class", "config-reset-btn");
        resetBtn.innerText = "RESET PRESET";
        resetBtn.addEventListener("mousedown", e -> {
            if (!(e instanceof MouseEvent me) || me.button != 0) return;
            onResetDefaults();
            render(doc, null);
        });
        footer.append(resetBtn);
        left.append(footer);
    }

    // ============================================================
    //  中栏：组详情 + 绑定列表 + GUI 交互元素列表
    // ============================================================

    /**
     * 中栏：组详情 + 两个配置项列表。<br>
     * - 上半部：组名称/控制模式/描述 表单<br>
     * - 下半部上区：KEY BINDINGS 列表 + 添加按钮<br>
     * - 下半部下区：CONFIGURATION ITEMS 列表 + 添加按钮
     */
    private static void buildMiddleColumn(Document doc) {
        Element middle = doc.getElementById("config-middle");
        if (middle == null) return;
        while (!middle.children.isEmpty()) middle.children.getFirst().remove();

        // ── 顶部保存工具栏 ──
        Element saveBar = doc.createElement("div");
        saveBar.setAttribute("class", "config-middle-savebar");
        Element saveBtn = doc.createElement("div");
        saveBtn.setAttribute("class", "config-save-btn");
        saveBtn.innerText = "SAVE TO VEHICLE";
        saveBtn.addEventListener("mousedown", e -> {
            if (!(e instanceof MouseEvent me) || me.button != 0) return;
            onSaveConfig(doc);
        });
        saveBar.append(saveBtn);
        middle.append(saveBar);

        if (selGroup < 0 || selGroup >= groupList.size()) return;
        GroupEditData g = groupList.get(selGroup);

        // ── 组详情表单 ──
        Element detailForm = doc.createElement("div");
        detailForm.setAttribute("class", "group-detail-form");
        // 名称
        Element nameRow = doc.createElement("div");
        nameRow.setAttribute("class", "form-row");
        Element nameLabel = doc.createElement("label");
        nameLabel.innerText = "NAME";
        Element nameInput = doc.createElement("input");
        nameInput.setAttribute("type", "text");
        nameInput.setAttribute("value", g.name);
        nameInput.addEventListener("change", e -> {
            g.name = nameInput.value;
            render(doc, null);
        });
        nameRow.append(nameLabel);
        nameRow.append(nameInput);
        detailForm.append(nameRow);

        // 控制模式
        Element modeRow = doc.createElement("div");
        modeRow.setAttribute("class", "form-row");
        Element modeLabel = doc.createElement("label");
        modeLabel.innerText = "MODE";
        Element modeSelect = doc.createElement("select");
        String[] modes = {"INHERIT", "GROUND", "FLIGHT", "SPACE", "NAUTICAL"};
        for (String m : modes) {
            Element opt = doc.createElement("option");
            opt.setAttribute("value", m);
            opt.innerText = m;
            if (m.equals(g.controlMode)) {
                opt.setAttribute("selected", "true");
            }
            modeSelect.append(opt);
        }
        modeSelect.addEventListener("change", e -> {
            g.controlMode = modeSelect.value;
            render(doc, null);
        });
        modeRow.append(modeLabel);
        modeRow.append(modeSelect);
        detailForm.append(modeRow);
        middle.append(detailForm);

        // ── KEY BINDINGS 区块 ──
        Element kbSection = doc.createElement("div");
        kbSection.setAttribute("class", "config-items-section");
        kbSection.setAttribute("id", "config-kb-section");
        int targetGroupIdx = selGroup;

        // KB 标题行 + 添加按钮
        Element kbHeader = doc.createElement("div");
        kbHeader.setAttribute("class", "config-items-header");
        Element kbTitle = doc.createElement("span");
        kbTitle.setAttribute("class", "config-items-title");
        kbTitle.innerText = "KEY BINDINGS  (" + g.bindings.size() + ")";
        Element kbAddBtn = doc.createElement("span");
        kbAddBtn.setAttribute("class", "config-items-btn");
        kbAddBtn.innerText = "+ ADD";
        kbAddBtn.addEventListener("mousedown", e -> {
            if (!(e instanceof MouseEvent me) || me.button != 0) return;
            onAddBinding(targetGroupIdx);
            render(doc, null);
        });
        kbHeader.append(kbTitle);
        kbHeader.append(kbAddBtn);
        kbSection.append(kbHeader);

        // KB 列表
        Element kbList = doc.createElement("div");
        kbList.setAttribute("class", "config-items-list");
        if (g.bindings.isEmpty()) {
            Element empty = doc.createElement("div");
            empty.setAttribute("class", "config-items-empty");
            empty.innerText = "NO BINDINGS";
            kbList.append(empty);
        } else {
            for (int i = 0; i < g.bindings.size(); i++) {
                BindingEditData b = g.bindings.get(i);
                boolean isSel = i == selBinding && selAction < 0;
                Element row = buildItemRow(doc, b.trigger, b.action, b.channel, isSel);
                int fi = i;
                row.addEventListener("mousedown", ev -> {
                    if (!(ev instanceof MouseEvent me) || me.button != 0) return;
                    selBinding = fi;
                    selAction = -1;
                    render(doc, null);
                });
                // 删除按钮
                Element delBtn = findChild(row, "ci-del-btn");
                if (delBtn != null) {
                    delBtn.addEventListener("mousedown", ev -> {
                        if (!(ev instanceof MouseEvent me) || me.button != 0) return;
                        ev.stopPropagation();
                        onDeleteBinding(targetGroupIdx, fi);
                        render(doc, null);
                    });
                }
                kbList.append(row);
            }
        }
        kbSection.append(kbList);
        middle.append(kbSection);

        // ── CONFIGURATION ITEMS 区块 ──
        Element ciSection = doc.createElement("div");
        ciSection.setAttribute("class", "config-items-section");
        ciSection.setAttribute("id", "config-ci-section");

        // CI 标题行 + 添加按钮
        Element ciHeader = doc.createElement("div");
        ciHeader.setAttribute("class", "config-items-header");
        Element ciTitle = doc.createElement("span");
        ciTitle.setAttribute("class", "config-items-title");
        ciTitle.innerText = "GUI ACTIONS  (" + actionList.size() + ")";
        Element ciAddBtn = doc.createElement("span");
        ciAddBtn.setAttribute("class", "config-items-btn");
        ciAddBtn.innerText = "+ ADD";
        ciAddBtn.addEventListener("mousedown", e -> {
            if (!(e instanceof MouseEvent me) || me.button != 0) return;
            onAddGuiAction();
            render(doc, null);
        });
        ciHeader.append(ciTitle);
        ciHeader.append(ciAddBtn);
        ciSection.append(ciHeader);

        // CI 列表
        Element ciList = doc.createElement("div");
        ciList.setAttribute("class", "config-items-list");
        if (actionList.isEmpty()) {
            Element empty = doc.createElement("div");
            empty.setAttribute("class", "config-items-empty");
            empty.innerText = "NO GUI ACTIONS";
            ciList.append(empty);
        } else {
            for (int i = 0; i < actionList.size(); i++) {
                ActionEditData a = actionList.get(i);
                boolean isSel = i == selAction && selBinding < 0;
                Element row = buildItemRow(doc, a.label, a.type, a.channel, isSel);
                int fi = i;
                row.addEventListener("mousedown", ev -> {
                    if (!(ev instanceof MouseEvent me) || me.button != 0) return;
                    selAction = fi;
                    selBinding = -1;
                    render(doc, null);
                });
                Element delBtn = findChild(row, "ci-del-btn");
                if (delBtn != null) {
                    delBtn.addEventListener("mousedown", ev -> {
                        if (!(ev instanceof MouseEvent me) || me.button != 0) return;
                        ev.stopPropagation();
                        onDeleteGuiAction(fi);
                        render(doc, null);
                    });
                }
                ciList.append(row);
            }
        }
        ciSection.append(ciList);
        middle.append(ciSection);
    }

    // ============================================================
    //  右栏：详情编辑表单
    // ============================================================

    /**
     * 右栏：根据选中项类型展示不同的编辑表单。<br>
     * - 选中 binding → 显示 trigger/action/channel/targets 表单<br>
     * - 选中 GUI action → 根据 PULSE/TOGGLE/SLIDER 显示不同表单<br>
     * - 无选中 → 显示 "NO ITEM SELECTED"
     */
    private static void buildRightColumn(Document doc) {
        Element right = doc.getElementById("config-right");
        if (right == null) return;
        while (!right.children.isEmpty()) right.children.getFirst().remove();

        // 标题
        Element header = doc.createElement("div");
        header.setAttribute("class", "config-right-header");
        Element title = doc.createElement("span");
        title.setAttribute("class", "config-right-title");
        if (selBinding >= 0) {
            title.innerText = "EDIT BINDING";
        } else if (selAction >= 0) {
            title.innerText = "EDIT GUI ACTION";
        } else {
            title.innerText = "DETAILS";
        }
        header.append(title);
        right.append(header);

        // 表单体
        Element body = doc.createElement("div");
        body.setAttribute("class", "config-right-body");
        body.setAttribute("id", "config-right-body");

        if (selBinding >= 0) {
            // ── 按键绑定编辑表单 ──
            if (selGroup >= 0 && selGroup < groupList.size()
                    && selBinding < groupList.get(selGroup).bindings.size()) {
                buildBindingForm(doc, body, groupList.get(selGroup).bindings.get(selBinding));
            }
        } else if (selAction >= 0 && selAction < actionList.size()) {
            // ── GUI 交互元素编辑表单 ──
            buildActionForm(doc, body, actionList.get(selAction));
        } else {
            Element empty = doc.createElement("div");
            empty.setAttribute("class", "config-right-empty");
            empty.innerText = "NO ITEM SELECTED";
            body.append(empty);
        }
        right.append(body);

        // ── 底部删除按钮（有选中时显示） ──
        if (selBinding >= 0 || selAction >= 0) {
            Element footer = doc.createElement("div");
            footer.setAttribute("class", "config-right-footer");
            Element delBtn = doc.createElement("div");
            delBtn.setAttribute("class", "config-del-btn");
            if (selBinding >= 0) {
                delBtn.innerText = "DELETE BINDING";
                delBtn.addEventListener("mousedown", e -> {
                    if (!(e instanceof MouseEvent me) || me.button != 0) return;
                    onDeleteBinding(selGroup, selBinding);
                    selBinding = -1;
                    render(doc, null);
                });
            } else {
                delBtn.innerText = "DELETE GUI ACTION";
                delBtn.addEventListener("mousedown", e -> {
                    if (!(e instanceof MouseEvent me) || me.button != 0) return;
                    onDeleteGuiAction(selAction);
                    selAction = -1;
                    render(doc, null);
                });
            }
            footer.append(delBtn);
            right.append(footer);
        }
    }

    // ── 按键绑定详情表单 ──

    /**
     * 构建按键绑定编辑表单：trigger / action / channel / targets。
     */
    private static void buildBindingForm(Document doc, Element body, BindingEditData b) {
        Element form = doc.createElement("div");
        form.setAttribute("class", "detail-form");

        // trigger
        addDetailField(doc, form, "TRIGGER", b.trigger, val -> b.trigger = val);
        // action (select)
        addDetailSelect(doc, form, "ACTION", b.action,
                new String[]{"PRESS", "HOLD", "TOGGLE"},
                val -> b.action = val);
        // channel
        addDetailField(doc, form, "CHANNEL", b.channel, val -> b.channel = val);
        // targets
        addDetailTags(doc, form, "TARGETS", b.targets);

        body.append(form);
    }

    // ── GUI 交互元素详情表单 ──

    /**
     * 构建 GUI 交互元素编辑表单。<br>
     * 根据 type 决定表单内容：<br>
     * - PULSE：label / channel / targets<br>
     * - TOGGLE：label / channel / targets / active<br>
     * - SLIDER：label / channel / targets / value / min / max / step
     */
    private static void buildActionForm(Document doc, Element body, ActionEditData a) {
        Element form = doc.createElement("div");
        form.setAttribute("class", "detail-form");

        String typeLabel = "TYPE: " + a.type;
        Element typeInfo = doc.createElement("div");
        typeInfo.setAttribute("class", "detail-field-label");
        typeInfo.innerText = typeLabel;
        form.append(typeInfo);

        // label
        addDetailField(doc, form, "LABEL", a.label, val -> a.label = val);
        // channel
        addDetailField(doc, form, "CHANNEL", a.channel, val -> a.channel = val);
        // targets
        addDetailTags(doc, form, "TARGETS", a.targets);

        switch (a.type) {
            case "TOGGLE" -> {
                // active toggle
                addDetailToggle(doc, form, "ACTIVE", a.active, val -> a.active = val);
            }
            case "SLIDER" -> {
                // value
                addDetailSlider(doc, form, "VALUE", a.min, a.max, a.value,
                        val -> a.value = val);
                // min
                addDetailField(doc, form, "MIN", String.valueOf((int) a.min),
                        val -> { try { a.min = Float.parseFloat(val); } catch (NumberFormatException ignored) {} });
                // max
                addDetailField(doc, form, "MAX", String.valueOf((int) a.max),
                        val -> { try { a.max = Float.parseFloat(val); } catch (NumberFormatException ignored) {} });
                // step
                addDetailField(doc, form, "STEP", String.valueOf((int) a.step),
                        val -> { try { a.step = Float.parseFloat(val); } catch (NumberFormatException ignored) {} });
            }
        }

        body.append(form);
    }

    // ============================================================
    //  表单辅助方法
    // ============================================================

    /** 添加一个文本输入字段 */
    private static void addDetailField(Document doc, Element parent,
                                        String label, String value,
                                        java.util.function.Consumer<String> onChange) {
        Element field = doc.createElement("div");
        field.setAttribute("class", "detail-field");
        Element lbl = doc.createElement("span");
        lbl.setAttribute("class", "detail-field-label");
        lbl.innerText = label;
        Element input = doc.createElement("input");
        input.setAttribute("type", "text");
        input.setAttribute("value", value);
        if (onChange != null) {
            input.addEventListener("change", e -> onChange.accept(input.value));
        }
        field.append(lbl);
        field.append(input);
        parent.append(field);
    }

    /** 添加一个选择字段 */
    private static void addDetailSelect(Document doc, Element parent,
                                         String label, String currentValue,
                                         String[] options,
                                         java.util.function.Consumer<String> onChange) {
        Element field = doc.createElement("div");
        field.setAttribute("class", "detail-field");
        Element lbl = doc.createElement("span");
        lbl.setAttribute("class", "detail-field-label");
        lbl.innerText = label;
        Element select = doc.createElement("select");
        for (String opt : options) {
            Element o = doc.createElement("option");
            o.setAttribute("value", opt);
            o.innerText = opt;
            if (opt.equals(currentValue)) o.setAttribute("selected", "true");
            select.append(o);
        }
        if (onChange != null) {
            select.addEventListener("change", e -> onChange.accept(select.value));
        }
        field.append(lbl);
        field.append(select);
        parent.append(field);
    }

    /** 添加一个标签行（显示 targets 列表） */
    private static void addDetailTags(Document doc, Element parent,
                                       String label, List<String> targets) {
        Element field = doc.createElement("div");
        field.setAttribute("class", "detail-field");
        Element lbl = doc.createElement("span");
        lbl.setAttribute("class", "detail-field-label");
        lbl.innerText = label;
        field.append(lbl);
        Element tagsRow = doc.createElement("div");
        tagsRow.setAttribute("class", "detail-tags-row");
        for (String t : targets) {
            Element tag = doc.createElement("span");
            tag.setAttribute("class", "detail-tag");
            tag.innerText = t;
            tagsRow.append(tag);
        }
        field.append(tagsRow);
        parent.append(field);
    }

    /** 添加一个开关字段 */
    private static void addDetailToggle(Document doc, Element parent,
                                         String label, boolean isOn,
                                         java.util.function.Consumer<Boolean> onChange) {
        Element row = doc.createElement("div");
        row.setAttribute("class", "detail-toggle-row");
        Element lbl = doc.createElement("span");
        lbl.setAttribute("class", "detail-toggle-label");
        lbl.innerText = label;

        Element sw = doc.createElement("div");
        String swClass = "detail-toggle-switch" + (isOn ? " on" : "");
        sw.setAttribute("class", swClass);

        Element knob = doc.createElement("div");
        knob.setAttribute("class", "detail-toggle-knob");
        sw.append(knob);

        row.addEventListener("mousedown", e -> {
            if (!(e instanceof MouseEvent me) || me.button != 0) return;
            boolean newVal = !isOn;
            sw.setAttribute("class", "detail-toggle-switch" + (newVal ? " on" : ""));
            if (onChange != null) onChange.accept(newVal);
        });

        row.append(lbl);
        row.append(sw);
        parent.append(row);
    }

    /** 添加一个水平滑条字段 */
    private static void addDetailSlider(Document doc, Element parent,
                                         String label, float min, float max,
                                         float value,
                                         java.util.function.Consumer<Float> onChange) {
        Element field = doc.createElement("div");
        field.setAttribute("class", "detail-slider-field");

        Element header = doc.createElement("div");
        header.setAttribute("class", "detail-slider-header");
        Element lbl = doc.createElement("span");
        lbl.setAttribute("class", "detail-slider-label");
        lbl.innerText = label;
        Element valEl = doc.createElement("span");
        valEl.setAttribute("class", "detail-slider-value");
        valEl.innerText = String.valueOf(Math.round(value));
        header.append(lbl);
        header.append(valEl);

        int pct = max > min ? Math.round((value - min) / (max - min) * 100) : 50;

        Element track = doc.createElement("div");
        track.setAttribute("class", "detail-slider-track");
        Element fill = doc.createElement("div");
        fill.setAttribute("class", "detail-slider-fill");
        fill.setAttribute("style", "width:" + pct + "%");
        track.append(fill);

        // 滑条拖拽
        track.addEventListener("mousedown", e -> {
            if (!(e instanceof MouseEvent me) || me.button != 0) return;
            startSliderDrag(me, track, fill, valEl, min, max, value, onChange, doc);
        });

        field.append(header);
        field.append(track);
        parent.append(field);
    }

    // ============================================================
    //  滑条拖拽（右栏水平滑条）
    // ============================================================

    /** 拖拽状态（右栏水平滑条） */
    private static Element hDragTrack;
    private static Element hDragFill;
    private static Element hDragVal;
    private static double hDragStartX;
    private static float hDragStartVal;
    private static float hDragMin;
    private static float hDragMax;
    private static java.util.function.Consumer<Float> hDragOnChange;
    private static Document hDragDoc;
    /** 是否为右栏水平滑条拖拽 */
    private static boolean hDragActive = false;

    /** 启动水平滑条拖拽 */
    private static void startSliderDrag(MouseEvent me, Element track, Element fill,
                                         Element valEl, float min, float max,
                                         float currentVal,
                                         java.util.function.Consumer<Float> onChange,
                                         Document doc) {
        hDragTrack = track;
        hDragFill = fill;
        hDragVal = valEl;
        hDragStartX = me.clientX;
        hDragStartVal = currentVal;
        hDragMin = min;
        hDragMax = max;
        hDragOnChange = onChange;
        hDragDoc = doc;
        hDragActive = true;

        if (!hDragBodyRegistered && doc.body != null) {
            doc.body.addEventListener("mousemove", PanelConfigEditor::onHSliderMove);
            doc.body.addEventListener("mouseup", PanelConfigEditor::onHSliderUp);
            hDragBodyRegistered = true;
        }
    }

    private static boolean hDragBodyRegistered = false;

    /** 水平滑条拖拽移动 */
    private static void onHSliderMove(com.sighs.apricityui.init.Event e) {
        if (!hDragActive || hDragTrack == null) return;
        MouseEvent me = (MouseEvent) e;
        double trackWidth = 100.0; // 基准宽度，AUI 无法获取实际宽度
        double delta = me.clientX - hDragStartX;
        float range = hDragMax - hDragMin;
        if (range <= 0) return;
        float deltaVal = (float) (delta / trackWidth * range);
        float newVal = Math.clamp(hDragStartVal + deltaVal, hDragMin, hDragMax);
        int pct = Math.round((newVal - hDragMin) / range * 100);
        hDragFill.setAttribute("style", "width:" + pct + "%");
        if (hDragVal != null) {
            hDragVal.innerText = String.valueOf(Math.round(newVal));
        }
    }

    /** 水平滑条拖拽释放 */
    private static void onHSliderUp(com.sighs.apricityui.init.Event e) {
        if (!hDragActive) return;
        if (hDragFill != null) {
            String style = hDragFill.getAttribute("style");
            int finalPct = 50;
            if (style != null && style.contains("width:")) {
                try {
                    String numStr = style.replaceAll("[^0-9]", "");
                    if (!numStr.isEmpty()) finalPct = Integer.parseInt(numStr);
                } catch (NumberFormatException ignored) {}
            }
            float range = hDragMax - hDragMin;
            float finalVal = range > 0 ? hDragMin + (float) finalPct / 100f * range : hDragMin;
            if (hDragOnChange != null) hDragOnChange.accept(finalVal);
        }
        hDragActive = false;
        hDragTrack = null;
        hDragFill = null;
        hDragVal = null;
        hDragOnChange = null;
        hDragDoc = null;
    }

    // ============================================================
    //  CRUD 操作
    // ============================================================

    /** 添加新控制组 */
    private static void onAddGroup() {
        String name = "NEW_GROUP_" + (groupList.size());
        GroupEditData g = new GroupEditData(name, "INHERIT");
        groupList.add(g);
        selGroup = groupList.size() - 1;
        selBinding = -1;
        selAction = -1;
        LOGGER.debug("[ConfigEditor] Added group '{}'", name);
    }

    /** 删除控制组（base 组不能删除） */
    private static void onDeleteGroup(int index) {
        if (index <= 0 || index >= groupList.size()) return;
        groupList.remove(index);
        if (selGroup >= groupList.size()) selGroup = groupList.size() - 1;
        selBinding = -1;
        selAction = -1;
        LOGGER.debug("[ConfigEditor] Deleted group at index {}", index);
    }

    /** 重置预设（清除所有编辑回到初始状态） */
    private static void onResetDefaults() {
        initialized = false;
        groupList = null;
        actionList = null;
        selGroup = 0;
        selBinding = -1;
        selAction = -1;
        LOGGER.debug("[ConfigEditor] Reset to defaults");
    }

    /** 添加按键绑定到当前选中的组 */
    private static void onAddBinding(int groupIdx) {
        if (groupIdx < 0 || groupIdx >= groupList.size()) return;
        GroupEditData g = groupList.get(groupIdx);
        g.bindings.add(new BindingEditData("key.", "PRESS", "channel", List.of("vehicle")));
        selBinding = g.bindings.size() - 1;
        selAction = -1;
        LOGGER.debug("[ConfigEditor] Added binding to group '{}'", g.name);
    }

    /** 从指定组删除按键绑定 */
    private static void onDeleteBinding(int groupIdx, int bindingIdx) {
        if (groupIdx < 0 || groupIdx >= groupList.size()) return;
        GroupEditData g = groupList.get(groupIdx);
        if (bindingIdx < 0 || bindingIdx >= g.bindings.size()) return;
        g.bindings.remove(bindingIdx);
        if (selBinding >= g.bindings.size()) selBinding = g.bindings.size() - 1;
        LOGGER.debug("[ConfigEditor] Deleted binding #{} from group '{}'", bindingIdx, g.name);
    }

    /** 添加 GUI 交互元素 */
    private static void onAddGuiAction() {
        actionList.add(new ActionEditData("PULSE", "NEW ACTION", "channel", List.of("vehicle")));
        selAction = actionList.size() - 1;
        selBinding = -1;
        LOGGER.debug("[ConfigEditor] Added GUI action");
    }

    /** 删除 GUI 交互元素 */
    private static void onDeleteGuiAction(int index) {
        if (index < 0 || index >= actionList.size()) return;
        actionList.remove(index);
        if (selAction >= actionList.size()) selAction = actionList.size() - 1;
        LOGGER.debug("[ConfigEditor] Deleted GUI action #{}", index);
    }

    // ============================================================
    //  保存：EditData → ControlGroupSet → 网络包
    // ============================================================

    /**
     * 保存当前编辑配置到服务端。<br>
     * 将内部 EditData 转换为 ControlGroupSet 并通过 ControlGroupSetEditPayload 发送。
     */
    private static void onSaveConfig(Document doc) {
        if (groupList == null || groupList.isEmpty()) return;
        ControlGroupSet saved = buildControlGroupSet();
        ControlDataAccessor.saveControlSet(Minecraft.getInstance(), saved);
        LOGGER.debug("[ConfigEditor] Saved config to server, groups={}, actions={}",
                groupList.size(), actionList.size());
        showToast(doc, "SAVED");
    }

    /**
     * 从当前编辑器的 EditData 构建不可变的 ControlGroupSet。
     */
    private static ControlGroupSet buildControlGroupSet() {
        // baseGroup (groupList[0])
        GroupEditData baseEdit = groupList.get(0);
        ControlGroup baseGroup = new ControlGroup(
                baseEdit.name,
                ControlMode.valueOf(baseEdit.controlMode),
                buildBindings(baseEdit.bindings)
        );

        // sub groups
        List<ControlGroup> subGroups = new ArrayList<>();
        for (int i = 1; i < groupList.size(); i++) {
            GroupEditData g = groupList.get(i);
            subGroups.add(new ControlGroup(
                    g.name,
                    ControlMode.valueOf(g.controlMode),
                    buildBindings(g.bindings)
            ));
        }

        // GUI actions
        List<AbstractGuiAction> guiActions = new ArrayList<>();
        for (ActionEditData a : actionList) {
            AbstractGuiAction action = switch (a.type) {
                case "PULSE" -> new GuiPulseAction(a.label, a.channel, List.copyOf(a.targets));
                case "TOGGLE" -> {
                    GuiToggleAction ta = new GuiToggleAction(a.label, a.channel, List.copyOf(a.targets));
                    ta.setActive(a.active);
                    yield ta;
                }
                case "SLIDER" -> new GuiSliderAction(a.label, a.channel, List.copyOf(a.targets),
                        a.min, a.max, a.step, a.value);
                default -> throw new IllegalArgumentException("Unknown action type: " + a.type);
            };
            guiActions.add(action);
        }

        return new ControlGroupSet(baseGroup, subGroups, guiActions, editActiveIndex);
    }

    /**
     * 从 BindingEditData 列表构建 ControlBinding 列表。
     */
    private static List<ControlBinding> buildBindings(List<BindingEditData> bindings) {
        List<ControlBinding> result = new ArrayList<>();
        for (BindingEditData b : bindings) {
            ControlBinding cb = new ControlBinding(
                    b.trigger, BindingAction.valueOf(b.action), b.channel, List.copyOf(b.targets)
            );
            cb.setToggleState(b.toggleState);
            result.add(cb);
        }
        return result;
    }

    /**
     * 显示 Toast 提示消息。<br>
     * 查找文档中的 #toast 元素，若不存在则创建一个。3 秒后自动隐藏。
     */
    private static void showToast(Document doc, String message) {
        Element toast = doc.getElementById("toast");
        if (toast == null) {
            // 文档中无 toast 元素时动态创建并追加到 body
            Element newToast = doc.createElement("div");
            newToast.setAttribute("id", "toast");
            newToast.setAttribute("class", "toast");
            if (doc.body != null) doc.body.append(newToast);
            toast = newToast;
        }
        final Element finalToast = toast; // lambda 需要 effectively final
        finalToast.innerText = message;
        finalToast.setAttribute("class", "toast show");
        // 3 秒后自动隐藏
        new Thread(() -> {
            try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
            finalToast.setAttribute("class", "toast");
        }).start();
    }

    // ============================================================
    //  工具方法
    // ============================================================

    /**
     * 构建配置项行（通用，绑定和 GUI 列表共用）。<br>
     * 行结构：名称 / 类型标签 / 频道 / 删除按钮
     */
    private static Element buildItemRow(Document doc, String name,
                                         String typeBadge, String channel,
                                         boolean selected) {
        Element row = doc.createElement("div");
        String cls = "config-item-row" + (selected ? " selected" : "");
        row.setAttribute("class", cls);

        Element nameEl = doc.createElement("span");
        nameEl.setAttribute("class", "ci-name");
        nameEl.innerText = name;

        Element badge = doc.createElement("span");
        badge.setAttribute("class", "ci-type-badge");
        badge.innerText = typeBadge;

        Element chEl = doc.createElement("span");
        chEl.setAttribute("class", "ci-channel");
        chEl.innerText = channel;

        Element delBtn = doc.createElement("span");
        delBtn.setAttribute("class", "ci-del-btn");
        delBtn.innerText = "×";

        row.append(nameEl);
        row.append(badge);
        row.append(chEl);
        row.append(delBtn);
        return row;
    }

    /**
     * 在元素的子节点中按 class 查找第一个匹配的后代。<br>
     * AUI Element 没有 querySelector 方法，手动遍历。
     */
    private static Element findChild(Element parent, String className) {
        if (parent == null || parent.children == null) return null;
        for (Element child : parent.children) {
            String cls = child.getAttribute("class");
            if (cls != null && cls.contains(className)) return child;
            Element found = findChild(child, className);
            if (found != null) return found;
        }
        return null;
    }

    // ============================================================
    //  重置（Screen 关闭时调用）
    // ============================================================

    /**
     * 重置所有静态状态。<br>
     * Screen 关闭时由 VehicleControlScreen.onClose() 调用。
     */
    public static void reset() {
        selGroup = 0;
        selBinding = -1;
        selAction = -1;
        initialized = false;
        groupList = null;
        actionList = null;
        hDragActive = false;
        hDragBodyRegistered = false;
        hDragTrack = null;
        hDragFill = null;
        hDragVal = null;
        hDragOnChange = null;
        hDragDoc = null;
        LOGGER.debug("[ConfigEditor] Reset static state");
    }
}
