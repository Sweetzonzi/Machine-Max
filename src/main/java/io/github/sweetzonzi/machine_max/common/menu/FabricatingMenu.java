package io.github.sweetzonzi.machine_max.common.menu;

import io.github.sweetzonzi.machine_max.common.block.fabricator.FabricatorBlockEntity;
import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.registry.MMMenus;
import lombok.Getter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import org.jetbrains.annotations.NotNull;

@Getter
public class FabricatingMenu extends AbstractContainerMenu {
    public final FabricatorBlockEntity fabricatorBlockEntity;

    public FabricatingMenu(int containerId, Inventory playerInventory, FriendlyByteBuf data) {
        super(MMMenus.FABRICATING_MENU.get(), containerId);
        var blockEntity = playerInventory.player.level().getBlockEntity(data.readBlockPos());
        if (blockEntity instanceof FabricatorBlockEntity) {
            this.fabricatorBlockEntity = (FabricatorBlockEntity) blockEntity;
        } else {
            throw new IllegalStateException("Container is associated with wrong block entity");
        }
    }

    public FabricatingMenu(int containerId, FabricatorBlockEntity fabricatorBlockEntity) {
        super(MMMenus.FABRICATING_MENU.get(), containerId);
        this.fabricatorBlockEntity = fabricatorBlockEntity;
    }

    /**
     * 开始生产任务
     */
    public void startFabrication(ResourceLocation recipeId, Player player) {
        if (!(fabricatorBlockEntity.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        RecipeManager recipeManager = serverLevel.getRecipeManager();
        Recipe<?> recipe = recipeManager.byKey(recipeId).orElseThrow().value();
        if (recipe instanceof FabricatingRecipe fabricatingRecipe) {
            fabricatorBlockEntity.addFabricationTask(player, fabricatingRecipe);
        }
    }

    /**
     * 取消任务
     */
    public void cancelTask(int taskIndex) {
        fabricatorBlockEntity.cancelTask(taskIndex);
    }

    /**
     * 领取任务产物
     */
    public void collectTask(int taskIndex, Player player) {
        fabricatorBlockEntity.collectTask(taskIndex, player);
    }

    /**
     * 领取任务产物
     */
    public void collectAllTask(Player player) {
        fabricatorBlockEntity.collectAllTasks(player);
    }

    /**
     * Handle when the stack in slot {@code index} is shift-clicked. Normally this moves the stack between the player inventory and the other inventory(s).
     *
     * @param player the player who is shift-clicking
     * @param index  the index of the slot that was shift-clicked
     */
    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        return ItemStack.EMPTY;
    }

    /**
     * Determines whether supplied player can use this container
     *
     * @param player the player to check
     */
    @Override
    public boolean stillValid(@NotNull Player player) {
        return fabricatorBlockEntity.stillValid(player);
    }
}
