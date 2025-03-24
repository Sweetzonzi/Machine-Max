package io.github.tt432.machinemax.common.vehicle;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public interface IPartInteractableItem {
    void interact(Level level, Player player, InteractionHand usedHand);
}
