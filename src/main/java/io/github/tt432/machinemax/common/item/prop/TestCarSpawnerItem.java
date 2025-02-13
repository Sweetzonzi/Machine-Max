package io.github.tt432.machinemax.common.item.prop;

import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.vehicle.PartType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class TestCarSpawnerItem extends Item {
    public TestCarSpawnerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        if(!level.isClientSide()){
//            CoreEntity coreEntity = new CoreEntity(PartType.AE86_CHASSIS_PART.get(), level);
//            level.addFreshEntity(coreEntity);
//            MachineMax.LOGGER.info(player+" tried to place a test car.");
        }
        return super.use(level, player, usedHand);
    }
}
