package io.github.sweetzonzi.machine_max.client.render.gui;

import com.sighs.apricityui.ApricityUI;
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
 * <p>所有 UI 内容直接写在 HTML 模板中，Java 只处理：
 * <ul>
 *   <li>页面路由（opacity 切换）</li>
 *   <li>事件绑定</li>
 *   <li>编辑页表单数据填充</li>
 * </ul>
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

    /** 初始化事件绑定 */
    private static void initPanel(Document doc) {
        // --- 主页 ---
        bindClick(doc, "btn-add-group", e -> showToast(doc, "添加控制组（待实现）"));
        bindClick(doc, "btn-add-gui", e -> showToast(doc, "添加GUI控件（待实现）"));
        bindClick(doc, "btn-reset", e -> showToast(doc, "已重置为预设配置"));

        // 控制组卡片 → 编辑页
        for (String[] g : GROUP_DATA) {
            String id = "btn-config-" + g[0];
            String name = g[0];
            bindClick(doc, id, e -> openGroupEdit(doc, name));
        }

        // GUI 控件卡片 → 编辑页
        for (String[] g : GUI_DATA) {
            String id = "btn-gui-config-" + g[0];
            String name = g[0];
            bindClick(doc, id, e -> openGuiEdit(doc, name));
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

    // ===== 页面路由 =====

    private static void showPage(Document doc, String pageId) {
        Element panelBody = doc.getElementById("panel-body");
        if (panelBody == null) return;

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
