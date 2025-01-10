package io.github.tt432.machinemax.common.item.prop;

import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.entity.MMEntities;
import io.github.tt432.machinemax.common.entity.part.MMPartEntity;
import io.github.tt432.machinemax.common.part.AbstractPart;
import io.github.tt432.machinemax.common.part.TestCubePart;
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
            MMPartEntity partEntity = new MMPartEntity(MMEntities.MM_PART_ENTITY.get(),level);
            partEntity.part = new TestCubePart(partEntity);
            level.addFreshEntity(partEntity);
            MachineMax.LOGGER.info(player+" tried to place a test car.");
        }
        return super.use(level, player, usedHand);
    }
}
