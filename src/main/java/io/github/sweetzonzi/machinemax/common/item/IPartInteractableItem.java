package io.github.sweetzonzi.machinemax.common.item;

import io.github.sweetzonzi.machinemax.common.vehicle.Part;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

public interface IPartInteractableItem {
    void interactWitchPart(@NotNull Part part, @NotNull Player player);

    void watchingPart(@NotNull Part part, @NotNull Player player);

    void stopWatchingPart(@NotNull Player player);
}
