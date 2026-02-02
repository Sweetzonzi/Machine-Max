package io.github.sweetzonzi.machine_max.common.menu;

import io.github.sweetzonzi.machine_max.common.attachment.BlueprintAttachment;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.common.registry.MMMenus;
import lombok.Getter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 蓝图研发 Menu
 * 仅用于承载玩家的科研状态，不包含任何物品槽位。
 * 所有数据直接来自 {@link BlueprintAttachment}。
 */
@Getter
public class BlueprintResearchMenu extends AbstractContainerMenu {

    private final Player player;
    private final BlueprintAttachment research;

    /**
     * 客户端构造函数
     *
     * @param containerId 容器 ID
     * @param inventory   玩家背包
     * @param buf         网络缓冲（未使用，保留接口）
     */
    public BlueprintResearchMenu(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        this(containerId, inventory);
    }

    /**
     * 服务端 / 客户端通用构造函数
     */
    public BlueprintResearchMenu(int containerId, Inventory inventory) {
        super(MMMenus.BLUEPRINT_RESEARCH_MENU.get(), containerId);
        this.player = inventory.player;
        this.research = player.getData(MMAttachments.getBLUEPRINT());
    }

    /**
     * Menu 是否仍然有效
     * 本 Menu 不绑定方块，仅检查玩家存活状态。
     */
    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int i) {
        return ItemStack.EMPTY;
    }
}
