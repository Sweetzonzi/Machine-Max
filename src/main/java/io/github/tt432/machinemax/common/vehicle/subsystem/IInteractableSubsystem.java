package io.github.tt432.machinemax.common.vehicle.subsystem;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

public interface IInteractableSubsystem {
    /**
     * 当玩家在常规模式下按下互动键时调用此方法
     * @param player 进行互动操作的玩家
     */
    void fastInteract(Player player);

    void preciseInteract(Player player, InteractionHand hand);

    default void watching(Player player){}

    default void stopWatching(Player player){}
}
