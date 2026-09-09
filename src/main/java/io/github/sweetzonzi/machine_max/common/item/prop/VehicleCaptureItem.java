package io.github.sweetzonzi.machine_max.common.item.prop;

import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.common.registry.MMDataComponents;
import io.github.sweetzonzi.machine_max.common.registry.MMItems;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.VehicleData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public interface VehicleCaptureItem {
    static InteractionResultHolder<ItemStack> captureVehicleToAssembly(Level level, Player player, ItemStack sourceStack, boolean consumeOnSuccess) {
        if (level.isClientSide()) {
            return InteractionResultHolder.sidedSuccess(sourceStack, true);
        }
        if (!player.hasData(MMAttachments.getENTITY_EYESIGHT())) {
            player.sendSystemMessage(Component.translatable("message.machine_max.blueprint_pass"));
            return InteractionResultHolder.pass(sourceStack);
        }

        var eyesight = player.getData(MMAttachments.getENTITY_EYESIGHT());
        SubPart subPart = eyesight.getSubPart();
        VehicleCore vehicle = subPart != null && subPart.part != null && subPart.part.assembly instanceof VehicleCore vc ? vc : null;
        if (vehicle == null || vehicle.isRemoved) {
            player.sendSystemMessage(Component.translatable("message.machine_max.blueprint_pass"));
            return InteractionResultHolder.pass(sourceStack);
        }

        ItemStack assemblyStack = new ItemStack(MMItems.getASSEMBLY_ITEM().get());
        // 收纳时归一化坐标并清零速度：放出来的载具以原点为基准、静止，状态（进度/耐久/血量）原样保留
        assemblyStack.set(MMDataComponents.getVEHICLE_DATA(), new VehicleData(vehicle).rebased());
        if (!player.addItem(assemblyStack)) {
            player.drop(assemblyStack, false);
        }

        ObjectManager.removeVehicle(vehicle);
        if (consumeOnSuccess) {
            sourceStack.consume(1, player);
        }
        player.sendSystemMessage(Component.translatable("message.machine_max.vehicle_capture_success"));
        return InteractionResultHolder.sidedSuccess(sourceStack, false);
    }
}
