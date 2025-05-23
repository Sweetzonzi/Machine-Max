package io.github.sweetzonzi.machinemax.common.item;

import io.github.sweetzonzi.machinemax.common.vehicle.Part;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

public interface IConnectorInteractableItem {
    void interactWitchConnector(@NotNull Part part, @NotNull Player player);

    void watchingConnector(@NotNull Part part, @NotNull Player player);

    void stopWatchingConnector(@NotNull Part part, @NotNull Player player);
}
