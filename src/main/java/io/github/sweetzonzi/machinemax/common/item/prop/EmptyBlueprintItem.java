package io.github.sweetzonzi.machinemax.common.item.prop;

import cn.solarmoon.spark_core.animation.ItemAnimatable;
import cn.solarmoon.spark_core.animation.anim.play.ModelIndex;
import io.github.sweetzonzi.machinemax.MachineMax;
import io.github.sweetzonzi.machinemax.common.attachment.LivingEntityEyesightAttachment;
import io.github.sweetzonzi.machinemax.common.item.ICustomModelItem;
import io.github.sweetzonzi.machinemax.common.registry.MMAttachments;
import io.github.sweetzonzi.machinemax.common.registry.MMDataComponents;
import io.github.sweetzonzi.machinemax.common.vehicle.Part;
import io.github.sweetzonzi.machinemax.common.vehicle.data.VehicleData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.FMLPaths;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

public class EmptyBlueprintItem extends Item implements ICustomModelItem {
    public EmptyBlueprintItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        if (!level.isClientSide()) {
            LivingEntityEyesightAttachment eyesight = player.getData(MMAttachments.getENTITY_EYESIGHT());
            Part part = eyesight.getPart();
            if (part != null && part.vehicle != null) {
                VehicleData vehicleData = new VehicleData(part.vehicle);
                var gameDir = FMLPaths.GAMEDIR.get().toFile();
                var saveDir = new File(gameDir, vehicleData.uuid + ".json");
                try {
                    VehicleData.serializeVehicleDataToJson(vehicleData, saveDir);
                    player.sendSystemMessage(Component.translatable("message.machine_max.blueprint_saved", saveDir.toString()));
                } catch (IOException e) {
                    MachineMax.LOGGER.error("Failed to save vehicle data to file!", e);
                    player.sendSystemMessage(Component.translatable("message.machine_max.blueprint_error", e));
                }
            } else {
                player.sendSystemMessage(Component.translatable("message.machine_max.blueprint_pass"));
            }
        }
        return super.use(level, player, usedHand);
    }

    public ItemAnimatable createItemAnimatable(ItemStack itemStack, Level level, ItemDisplayContext context) {
        var animatable = new ItemAnimatable(itemStack, level);
        HashMap<ItemDisplayContext, ItemAnimatable> customModels;
        if (itemStack.has(MMDataComponents.getCUSTOM_ITEM_MODEL()))
            customModels = itemStack.get(MMDataComponents.getCUSTOM_ITEM_MODEL());
        else customModels = new HashMap<>();
        animatable.setModelIndex(
                new ModelIndex(
                        ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "item/blueprint.geo"),
                        ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "textures/item/blueprint.png"))
        );
        if (customModels != null) {
            customModels.put(context, animatable);
            itemStack.set(MMDataComponents.getCUSTOM_ITEM_MODEL(), customModels);
        }
        return animatable;
    }

    @Override
    public Vector3f getRenderRotation(ItemStack itemStack, Level level, ItemDisplayContext displayContext) {
        if (displayContext == ItemDisplayContext.GUI
                || displayContext == ItemDisplayContext.FIXED
                || displayContext == ItemDisplayContext.GROUND) {
            return new Vector3f(-15f, -30f, 45f).mul((float) (Math.PI / 180f));
        }
        return ICustomModelItem.super.getRenderRotation(itemStack, level, displayContext);
    }
}
