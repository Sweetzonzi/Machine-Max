package io.github.sweetzonzi.machine_max.common.vehicle;

import io.github.sweetzonzi.machine_max.common.vehicle.energy.EnergyGrid;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractSubsystem;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public interface ISubsystemHost {
    SubPart getSubPart();
    Map<String, AbstractSubsystem> getSubsystems();
    Level getLevel();

    @NotNull
    SubsystemController getSubsystemController();

    @Nullable
    default EnergyGrid getEnergyGrid() { return null; }
}
