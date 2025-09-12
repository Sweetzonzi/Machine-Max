package io.github.sweetzonzi.machine_max.client.gui;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.menu.ItemStorageSubsystemMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public class ItemStorageSubsystemScreen extends AbstractContainerScreen<ItemStorageSubsystemMenu> implements MenuAccess<ItemStorageSubsystemMenu> {

    /**
     * The ResourceLocation containing the chest GUI texture.
     */
    private static final ResourceLocation CONTAINER_BACKGROUND = ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID,"textures/gui/generic_bg_54.png");
    private static final ResourceLocation SLOT = ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "textures/gui/slot.png");
    /**
     * Window height is calculated with these values, the more rows, the higher
     */
    private final int containerRows;


    public ItemStorageSubsystemScreen(ItemStorageSubsystemMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.containerRows = menu.getContainerRows();
        this.imageHeight = 114 + this.containerRows * 18;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderSlot(GuiGraphics guiGraphics, Slot slot) {
        guiGraphics.blit(SLOT, slot.x - 1, slot.y - 1, 0, 0, 18, 18, 18, 18);
        super.renderSlot(guiGraphics, slot);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int i = (this.width - this.imageWidth) / 2;
        int j = (this.height - this.imageHeight) / 2;
        guiGraphics.blit(CONTAINER_BACKGROUND, i, j, 0, 0, this.imageWidth, this.containerRows * 18 + 17);
        guiGraphics.blit(CONTAINER_BACKGROUND, i, j + this.containerRows * 18 + 17, 0, 126, this.imageWidth, 96);
    }
}
