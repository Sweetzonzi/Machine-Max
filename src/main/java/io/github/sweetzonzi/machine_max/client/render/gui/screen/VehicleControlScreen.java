package io.github.sweetzonzi.machine_max.client.render.gui.screen;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import io.github.sweetzonzi.machine_max.client.render.gui.panel.*;
import io.github.sweetzonzi.machine_max.common.mech.control.ControlGroupSet;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractControllableSubsystem;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 载具控制面板主 Screen。<br>
 * 采用 Minecraft Screen + AUI Document 混合架构：
 * <ul>
 *   <li>Screen 负责：生命周期管理、3D 预览渲染、3D 视角拖拽输入</li>
 *   <li>AUI Document 负责：UI 渲染（由 Client.java 全局事件自动处理）、UI 元素输入</li>
 * </ul>
 * <p>
 * 热重载方案：利用 AUI body 的 "load" 事件。<br>
 * AUI Document.refresh() 在重建 DOM 后保留 body 的 EventListener 列表并触发 "load" 事件,
 * 因此只需在 init() 中注册一次监听器，后续热重载后自动重新渲染面板内容。
 * </p>
 */
@OnlyIn(Dist.CLIENT)
public class VehicleControlScreen extends Screen {

    private static final String AUI_DOC_PATH = "machine_max/vehicle_control.html";

    /** 所有会被 Java 动态填充子元素的容器 ID，用于热重载时的显式清理 */
    private static final String[] PANEL_CONTAINER_IDS = {
            "group-strip-0", "group-strip-1",
            "status-col", "warning-col", "extra-col",
            "pulse-section", "toggle-section", "slider-section",
            "config-left", "config-middle", "config-right"
    };

    private Document auiDocument;

    private int activeTab = 1;
    private ControlGroupSet controlSet;

    private float previewRotX = 25f;
    private float previewRotY = -45f;
    private float previewZoom = 1.0f;
    private boolean isDragging3d;

    /** 防止 Screen 缩放/重建时重复注册 load 监听器 */
    private boolean loadListenerRegistered = false;

    public VehicleControlScreen() {
        super(Component.literal("Vehicle Control"));
    }

    @Override
    protected void init() {
        controlSet = ControlDataAccessor.getCurrentControlSet(Minecraft.getInstance());
        if (controlSet == null || controlSet == ControlGroupSet.EMPTY) {
            onClose();
            return;
        }

        auiDocument = Document.create(AUI_DOC_PATH);
        if (auiDocument == null) {
            onClose();
            return;
        }

        initPanels();

        // 注册 body "load" 事件：热重载后 Document.refresh() 末尾会触发此事件。
        // body 的 EventListener 列表会在 refresh() 中被保留并转移到新 body，
        // 因此只需注册一次，后续每次热重载都会自动调用 initPanels() 重建面板内容。
        if (!loadListenerRegistered && auiDocument.body != null) {
            auiDocument.body.addEventListener("load", e -> initPanels());
            loadListenerRegistered = true;
        }
    }

    /**
     * 初始化所有面板到当前 Document 中。<br>
     * 先清理所有容器的旧内容，再注入新数据，确保热重载后不留残留元素。
     */
    private void initPanels() {
        clearPanels();
        PanelTabBar.init(auiDocument, this::switchTab);
        PanelTabBar.setActive(auiDocument, activeTab);
        PanelOverview.render(auiDocument, controlSet);
        PanelDeviceControl.render(auiDocument, controlSet);
        PanelConfigEditor.render(auiDocument, controlSet);
    }

    /**
     * 显式清理所有动态容器的子元素。作为安全网，确保每次注入前容器是干净的。
     */
    private void clearPanels() {
        if (auiDocument == null) return;
        for (String id : PANEL_CONTAINER_IDS) {
            Element el = auiDocument.getElementById(id);
            if (el == null || el.children == null) continue;
            while (!el.children.isEmpty()) {
                el.children.getFirst().remove();
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (Minecraft.getInstance().player instanceof LocalPlayer player) {
            if (!(((IEntityMixin)player).machine_Max$getControllingSubsystem() instanceof AbstractControllableSubsystem)) {
                this.onClose();
            }
        }
    }

    private void switchTab(int index) {
        if (index == activeTab) return;
        if (index == 3) return;
        activeTab = index;
        PanelTabBar.setActive(auiDocument, index);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (activeTab == 0 || activeTab == 1) {
            render3dPreview(graphics, partialTick);
        } else {
            graphics.fillGradient(0, 0, width, height, 0xC0101010, 0xC0101010);
        }
    }

    @Override
    protected void renderMenuBackground(GuiGraphics partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void render3dPreview(GuiGraphics graphics, float partialTick) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isInPreviewArea(mouseX, mouseY) && (activeTab == 0 || activeTab == 1)) {
            isDragging3d = true;
            return true;
        }
        if (isInAuiPanelArea(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDragging3d && button == 0) {
            previewRotY += dragX * 0.5f;
            previewRotX += dragY * 0.5f;
            previewRotX = Math.clamp(previewRotX, -90f, 90f);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) isDragging3d = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isInPreviewArea(mouseX, mouseY)) {
            previewZoom = (float) Math.clamp(previewZoom - scrollY * 0.1, 0.5, 3.0);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        if (auiDocument != null) {
            auiDocument.remove();
            auiDocument = null;
        }
        super.onClose();
    }

    @Override
    public void removed() {
        if (auiDocument != null) {
            auiDocument.remove();
            auiDocument = null;
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean isInPreviewArea(double mouseX, double mouseY) {
        int previewX = (int)(width * 0.52f);
        int previewY = (int)(height * 0.08f);
        int previewW = (int)(width * 0.46f);
        int previewH = (int)(height * 0.84f);
        return mouseX >= previewX && mouseX <= previewX + previewW
                && mouseY >= previewY && mouseY <= previewY + previewH;
    }

    private boolean isInAuiPanelArea(double mouseX, double mouseY) {
        int panelW = (int)(width * 0.50f);
        return mouseX >= 0 && mouseX <= panelW && mouseY >= 0 && mouseY <= height;
    }
}
