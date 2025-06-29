package io.github.sweetzonzi.machinemax.common.block;

import io.github.sweetzonzi.machinemax.common.crafting.FabricatingMenu;
import io.github.sweetzonzi.machinemax.common.registry.MMBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FabricatorBlockEntity extends BaseContainerBlockEntity {
    public FabricatorBlockEntity(BlockPos pos, BlockState blockState) {
        super(MMBlockEntities.getFABRICATOR_BLOCK_ENTITY().get(), pos, blockState);
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.literal("Fabricator");
    }

    @Override
    protected @NotNull Component getDefaultName() {
        return getDisplayName();
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return NonNullList.withSize(1, ItemStack.EMPTY);
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {

    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }


    @Override
    public @NotNull AbstractContainerMenu createMenu(int containerId, @NotNull Inventory playerInventory) {
        return new FabricatingMenu(containerId, playerInventory);
    }

    @Override
    public int getContainerSize() {
        return 1;
    }
}
