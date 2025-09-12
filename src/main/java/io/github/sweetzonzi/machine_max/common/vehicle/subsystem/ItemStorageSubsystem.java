package io.github.sweetzonzi.machine_max.common.vehicle.subsystem;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.ItemStorageSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.menu.ItemStorageSubsystemMenu;
import lombok.Getter;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

@Getter
public class ItemStorageSubsystem extends AbstractSubsystem implements MenuProvider {
    public final ItemStorageSubsystemAttr attr;
    public final SimpleContainer container;

    public ItemStorageSubsystem(ISubsystemHost owner, String name, ItemStorageSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
        container = new SimpleContainer(attr.rows * attr.columns);
    }

    @Override
    public void onDestroyed() {
        super.onDestroyed();
        //被摧毁时爆出物品
        popItems();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        //拆卸时爆出物品
        popItems();
    }

    @Override
    public void onInteract(LivingEntity entity) {
        super.onInteract(entity);
        if (entity instanceof ServerPlayer player && this.active) {
            player.openMenu(this, (buf) -> {
                buf.writeInt(container.getContainerSize());
                buf.writeInt(attr.rows);
                buf.writeInt(attr.columns);
            });
        }
    }

    public void popItems() {
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                Vec3 pos = getOwner().getPart().getWorldPosition(1);
                ItemEntity item = new ItemEntity(getOwner().getLevel(), pos.x, pos.y, pos.z, stack);
                getOwner().getLevel().addFreshEntity(item);
            }
        }
    }

    /**
     * 在此填入各个信号名对应的接收者名列表，用于自动组织信号传输关系。<p>
     * Return a map of signal names to a list of receiver names here, to automatically organize signal transfer.
     *
     * @return 信号名称->接收者名称列表 Map of signal names to a list of receiver names.
     */
    @Override
    public Map<String, List<String>> getTargetNames() {
        return Map.of();
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable(name);
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, @NotNull Inventory playerInventory, @NotNull Player player) {
        return new ItemStorageSubsystemMenu(containerId, playerInventory, this.container, attr.rows, attr.columns);
    }

    @Override
    public void loadData(CompoundTag data) {
        super.loadData(data);
        NonNullList<ItemStack> items = NonNullList.withSize(this.container.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(data, items, getOwner().getLevel().registryAccess());
        for (int i = 0; i < items.size(); i++) {
            if (i < container.getContainerSize())
                container.setItem(i, items.get(i));
            else {
                MachineMax.LOGGER.error("ItemStorageSubsystem: loadData: item count of {} exceeds max capacity, some items will be lost.", name);
                break;
            }
        }
    }

    @Override
    public CompoundTag saveData(CompoundTag data) {
        super.saveData(data);
        ContainerHelper.saveAllItems(data, this.container.getItems(), true, getOwner().getLevel().registryAccess());
        return data;
    }
}


