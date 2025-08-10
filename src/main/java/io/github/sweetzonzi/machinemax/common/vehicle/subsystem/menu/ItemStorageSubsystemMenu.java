package io.github.sweetzonzi.machinemax.common.vehicle.subsystem.menu;

import io.github.sweetzonzi.machinemax.common.registry.MMMenus;
import io.netty.buffer.ByteBuf;
import lombok.Getter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

@Getter
public class ItemStorageSubsystemMenu extends AbstractContainerMenu {
    private final Container container;
    private final int containerRows;
    private final int containerColumns;

    public ItemStorageSubsystemMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf data) {
        this(containerId, playerInventory, new SimpleContainer(data.readInt()), data.readInt(), data.readInt());
    }

    public ItemStorageSubsystemMenu(int containerId, Inventory playerInventory, Container container, int rows, int columns) {
        super(MMMenus.ITEM_STORAGE_SUBSYSTEM_MENU.get(), containerId);
        checkContainerSize(container, rows * columns);
        this.container = container;
        this.containerRows = rows;
        this.containerColumns = columns;
        container.startOpen(playerInventory.player);
        int i = (this.containerRows - 4) * 18;

        //容器
        //动态计算容器起始X坐标使其居中
        int containerWidth = containerColumns * 18;
        int inventoryWidth = 162; // 玩家物品栏宽度 (9 * 18)
        int containerStartX = (inventoryWidth - containerWidth) / 2 + 8; // 8是玩家物品栏起始X

        // 居中显示容器格子
        for (int j = 0; j < this.containerRows; j++) {
            for (int k = 0; k < containerColumns; k++) {
                this.addSlot(new Slot(container, k + j * containerColumns,
                        containerStartX + k * 18,  // 动态计算的X坐标
                        18 + j * 18));
            }
        }

        //玩家物品栏
        for (int l = 0; l < 3; l++) {
            for (int j1 = 0; j1 < 9; j1++) {
                this.addSlot(new Slot(playerInventory, j1 + l * 9 + 9, 8 + j1 * 18, 103 + l * 18 + i));
            }
        }

        //玩家快捷栏
        for (int i1 = 0; i1 < 9; i1++) {
            this.addSlot(new Slot(playerInventory, i1, 8 + i1 * 18, 161 + i));
        }
    }

    /**
     * Determines whether supplied player can use this container
     */
    @Override
    public boolean stillValid(@NotNull Player player) {
        return this.container.stillValid(player);
    }

    /**
     * Handle when the stack in slot {@code index} is shift-clicked. Normally this moves the stack between the player container and the other container(s).
     */
    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            ItemStack itemStack1 = slot.getItem();
            itemstack = itemStack1.copy();
            if (index < this.containerRows * containerColumns) {
                if (!this.moveItemStackTo(itemStack1, this.containerRows * containerColumns, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(itemStack1, 0, this.containerRows * containerColumns, false)) {
                return ItemStack.EMPTY;
            }

            if (itemStack1.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return itemstack;
    }

    /**
     * Called when the container is closed.
     */
    @Override
    public void removed(@NotNull Player player) {
        super.removed(player);
        this.container.stopOpen(player);
    }

}
