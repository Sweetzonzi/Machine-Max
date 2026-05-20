package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.common.mech.energy.EnergyGrid;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubsystemController;
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
