package io.github.sweetzonzi.machine_max.common.menu;

import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.common.registry.MMDataComponents;
import io.github.sweetzonzi.machine_max.common.registry.MMItems;
import io.github.sweetzonzi.machine_max.common.registry.MMMenus;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.BlueprintMeta;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.VehicleData;
import io.github.sweetzonzi.machine_max.network.payload.assembly.VehicleDataSavedPayload;
import lombok.Getter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;

@Getter
public class VehicleNamingMenu extends AbstractContainerMenu {
    private final ItemStack blueprintStack;
    private String vehicleName = "";
    private String vehicleDescription = "";

    public VehicleNamingMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, playerInventory.getItem(extraData.readInt()));
        vehicleName = extraData.readUtf();
    }

    public VehicleNamingMenu(int containerId, Inventory playerInventory, ItemStack blueprintStack) {
        super(MMMenus.VEHICLE_NAMING_MENU.get(), containerId);
        this.blueprintStack = blueprintStack;
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive() && !blueprintStack.isEmpty();
    }

    public void setVehicleName(String name) {
        this.vehicleName = name != null ? name : "Vehicle";
    }

    public void setVehicleDescription(String description) {
        this.vehicleDescription = description != null ? description : "";
    }

    /**
     * 抄录当前视线中的载具：归一化数据 + 元信息，产出 {@code vehicle_blueprint} 物品，
     * 并通知客户端写入本地蓝图库。
     *
     * @param player      玩家
     * @param emptyBlueprint 待消耗的空蓝图
     * @param vehicleName 载具展示名
     * @param description 玩家填写的设计描述
     */
    public static void saveVehicleWithName(ServerPlayer player, ItemStack emptyBlueprint, String vehicleName, String description) {
        // 获取视线中的载具部件
        var eyesight = player.getData(MMAttachments.getENTITY_EYESIGHT());
        var subPart = eyesight.getSubPart();

        if (subPart != null && subPart.part.assembly instanceof VehicleCore vehicle) {
            // 归一化：清空世界坐标、血量与零件进度，物品与库文件都是「骨架」
            VehicleData vehicleData = new VehicleData(vehicle).withNewName(vehicleName).normalized();
            // 元信息只进 JSON / 独立字段，不并入 VehicleData 的网络编码
            BlueprintMeta meta = new BlueprintMeta(
                    player.getName().getString(),
                    OffsetDateTime.now().toString(),
                    description != null ? description : ""
            );

            // 发送 VehicleData 与独立 meta 到客户端，由客户端序列化为 JSON 保存
            PacketDistributor.sendToPlayer(player, new VehicleDataSavedPayload(vehicleData, meta));

            // 消耗空蓝图并给予已保存的蓝图
            emptyBlueprint.consume(1, player);
            ItemStack savedBlueprint = new ItemStack(MMItems.getVEHICLE_BLUEPRINT().get());
            savedBlueprint.set(MMDataComponents.getVEHICLE_DATA(), vehicleData.withMeta(meta));

            player.addItem(savedBlueprint);

            // 文件名由客户端生成随机 UUID，提示不再暴露具体文件名
            player.sendSystemMessage(Component.translatable("message.machine_max.blueprint_saved"));
        }
    }

}
