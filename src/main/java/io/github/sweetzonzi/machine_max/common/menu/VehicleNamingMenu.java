package io.github.sweetzonzi.machine_max.common.menu;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.common.registry.MMDataComponents;
import io.github.sweetzonzi.machine_max.common.registry.MMItems;
import io.github.sweetzonzi.machine_max.common.registry.MMMenus;
import io.github.sweetzonzi.machine_max.common.vehicle.data.VehicleData;
import lombok.Getter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.text.Normalizer;
import java.util.regex.Pattern;

@Getter
public class VehicleNamingMenu extends AbstractContainerMenu {
    private final ItemStack blueprintStack;
    private String vehicleName = "";

    public VehicleNamingMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, playerInventory.getItem(extraData.readInt()));
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

    public static void saveVehicleWithName(ServerPlayer player, ItemStack emptyBlueprint, String vehicleName) {
        try {
            // 获取视线中的载具部件
            var eyesight = player.getData(MMAttachments.getENTITY_EYESIGHT());
            var subPart = eyesight.getSubPart();

            if (subPart != null && subPart.part.vehicle != null) {
                VehicleData vehicleData = new VehicleData(subPart.part.vehicle);

                // 设置载具名称
                vehicleData = vehicleData.setName(vehicleName);
                //TODO: 配置是否保存为文件，否则仅保存为物品
                // 生成安全的文件名
                String safeFileName = makeSafeFileName(vehicleName);
                if (safeFileName.isEmpty()) {
                    safeFileName = "vehicle";
                }

                // 保存到文件
                var gameDir = FMLPaths.GAMEDIR.get().toFile();
                var saveDir = new File(gameDir, "saved_blueprints");
                if (!saveDir.exists()) {
                    saveDir.mkdirs();
                }

                var saveFile = new File(saveDir, safeFileName + ".json");
                int counter = 1;
                while (saveFile.exists()) {
                    saveFile = new File(saveDir, safeFileName + "_" + counter + ".json");
                    counter++;
                }

                VehicleData.serializeVehicleDataToJson(vehicleData, saveFile);

                // 消耗空蓝图并给予已保存的蓝图
                emptyBlueprint.consume(1, player);
                ItemStack savedBlueprint = new ItemStack(MMItems.getVEHICLE_BLUEPRINT().get());
                savedBlueprint.set(MMDataComponents.getVEHICLE_DATA(), vehicleData);

                player.addItem(savedBlueprint);

                player.sendSystemMessage(Component.translatable(
                        "message.machine_max.blueprint_saved",
                        saveFile.getPath()
                ));
            }
        } catch (IOException e) {
            MachineMax.LOGGER.error("Failed to save vehicle data!", e);
            player.sendSystemMessage(Component.translatable("message.machine_max.blueprint_error", e.getMessage()));
        }
    }

    private static String makeSafeFileName(String input) {
        // 移除重音符号
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        String ascii = pattern.matcher(normalized).replaceAll("");

        // 只保留字母、数字、下划线和连字符
        ascii = ascii.replaceAll("[^a-zA-Z0-9_\\- ]", "");

        // 替换空格为下划线
        ascii = ascii.replace(' ', '_');

        // 限制长度
        if (ascii.length() > 50) {
            ascii = ascii.substring(0, 50);
        }

        return ascii;
    }

}