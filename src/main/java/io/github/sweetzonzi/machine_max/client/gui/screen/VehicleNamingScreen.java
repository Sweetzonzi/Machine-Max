package io.github.sweetzonzi.machine_max.client.gui.screen;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.menu.VehicleNamingMenu;
import io.github.sweetzonzi.machine_max.network.payload.assembly.VehicleConfigPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.awt.*;

public class VehicleNamingScreen extends AbstractContainerScreen<VehicleNamingMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "textures/gui/vehicle_naming.png");

    private EditBox nameEditBox;
    private Button confirmButton;
    private Button cancelButton;

    public VehicleNamingScreen(VehicleNamingMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 100;
    }

    @Override
    protected void init() {
        super.init();

        // 计算GUI中心位置
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // 名称输入框
        this.nameEditBox = new EditBox(this.font, x + 20, y + 30, 136, 20, Component.empty());
        this.nameEditBox.setMaxLength(50);
        this.nameEditBox.setValue("MyVehicle");
        this.nameEditBox.setResponder(this::onNameChanged);
        this.addRenderableWidget(this.nameEditBox);
        this.setInitialFocus(this.nameEditBox);

        // 确认按钮
        this.confirmButton = Button.builder(
                Component.translatable("gui.machine_max.confirm"),
                button -> this.confirm()
        ).bounds(x + 20, y + 65, 60, 20).build();
        this.addRenderableWidget(this.confirmButton);

        // 取消按钮
        this.cancelButton = Button.builder(
                Component.translatable("gui.machine_max.cancel"),
                button -> this.onClose()
        ).bounds(x + 96, y + 65, 60, 20).build();
        this.addRenderableWidget(this.cancelButton);

        this.updateButtonState();
    }

    private void onNameChanged(String newName) {
        this.menu.setVehicleName(newName);
        this.updateButtonState();
    }

    private void updateButtonState() {
        String name = this.nameEditBox.getValue().trim();
        this.confirmButton.active = !name.isEmpty() && name.length() <= 50;
    }

    private void confirm() {
        if (this.confirmButton.active) {
            // 发送数据包到服务器处理保存
            PacketDistributor.sendToServer(new VehicleConfigPayload(
                    this.menu.containerId,
                    this.nameEditBox.getValue().trim()
            ));
            this.onClose();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            this.confirm();
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        if (nameEditBox != null && nameEditBox.isFocused()) {
            // E 键 - 在搜索框中输入 'e' 而不是关闭界面
            if (Minecraft.getInstance().options.keyInventory.isActiveAndMatches(InputConstants.getKey(keyCode, scanCode))) {
                // 让搜索框处理 E 键输入
                nameEditBox.keyPressed(keyCode, scanCode, modifiers);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x1 = (this.width - this.imageWidth) / 2;
        int y1 = (this.height - this.imageHeight) / 2;
        int x2 = x1 + this.imageWidth;
        int y2 = y1 + this.imageHeight;
        Color color1 = new Color(16,16,16,150);
        Color color2 = new Color(32,32,32,150);
        guiGraphics.fillGradient(x1, y1, x2, y2, color1.getRGB(), color2.getRGB());
    }

    @Override
    public void renderTransparentBackground(GuiGraphics guiGraphics) {
        //无半透明背景
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
        guiGraphics.drawString(this.font,
                Component.translatable("gui.machine_max.enter_vehicle_name").withColor(Color.WHITE.getRGB()),
                20, 15, 0x404040, false);
    }

    @Override
    public void resize(net.minecraft.client.Minecraft minecraft, int width, int height) {
        String currentName = this.nameEditBox.getValue();
        super.resize(minecraft, width, height);
        this.nameEditBox.setValue(currentName);
    }
}