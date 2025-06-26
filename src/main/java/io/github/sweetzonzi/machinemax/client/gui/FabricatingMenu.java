package io.github.sweetzonzi.machinemax.client.gui;

import io.github.sweetzonzi.machinemax.common.registry.MMMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class FabricatingMenu extends AbstractContainerMenu {

    public FabricatingMenu(int containerId, Inventory playerInventory) {
        super(MMMenus.FABRICATING_MENU.get(), containerId);
    }

    /**
     * Handle when the stack in slot {@code index} is shift-clicked. Normally this moves the stack between the player inventory and the other inventory(s).
     *
     * @param player
     * @param index
     */
    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        return ItemStack.EMPTY;
    }

    /**
     * Determines whether supplied player can use this container
     *
     * @param player
     */
    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }
}
