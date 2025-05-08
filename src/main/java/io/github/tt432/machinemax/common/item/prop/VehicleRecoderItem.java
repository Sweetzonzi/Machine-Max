package io.github.tt432.machinemax.common.item.prop;

import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.attachment.LivingEntityEyesightAttachment;
import io.github.tt432.machinemax.common.registry.MMAttachments;
import io.github.tt432.machinemax.common.vehicle.Part;
import io.github.tt432.machinemax.common.vehicle.VehicleCore;
import io.github.tt432.machinemax.common.vehicle.VehicleManager;
import io.github.tt432.machinemax.common.vehicle.data.VehicleData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.FMLPaths;

import java.io.File;
import java.io.IOException;

public class VehicleRecoderItem extends Item {
    public VehicleRecoderItem(Properties properties) {
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
                    MachineMax.LOGGER.info("Saved vehicle data to {}}.", saveDir);
                } catch (IOException e) {
                    MachineMax.LOGGER.error("Failed to save vehicle data to file!");
                }
            } else {
                MachineMax.LOGGER.info("No vehicle selected, skipping save.");
            }
        }
        return super.use(level, player, usedHand);
    }
}
