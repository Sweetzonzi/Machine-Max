package io.github.tt432.machinemax.common.item.prop;

import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.vehicle.Part;
import io.github.tt432.machinemax.common.vehicle.VehicleCore;
import io.github.tt432.machinemax.common.vehicle.VehicleManager;
import io.github.tt432.machinemax.common.vehicle.data.VehicleData;
import io.github.tt432.machinemax.external.MMDynamicRes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.UUID;

public class TestCarSpawnerItem extends Item {
    public TestCarSpawnerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        if(!level.isClientSide()){
//            Part part = new Part(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID,"test_cube"), level);
            VehicleData vehicleData = MMDynamicRes.BLUEPRINTS.values().stream().findFirst().get();
            VehicleCore vehicle = new VehicleCore(level, vehicleData);
            vehicle.setUuid(UUID.randomUUID());
            vehicle.setPos(player.position().add(0, 1, 0));
            VehicleManager.addVehicle(vehicle);
        }
        return super.use(level, player, usedHand);
    }
}
