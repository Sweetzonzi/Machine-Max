package io.github.tt432.machinemax.mixin_interface;

import net.minecraft.world.entity.Entity;

import java.util.UUID;

public interface IClientLevelMixin {
    Entity machine_Max$getEntity(UUID uuid);
}
