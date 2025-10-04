package io.github.sweetzonzi.machine_max.common.vehicle;

import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractSubsystem;
import net.minecraft.world.level.Level;

import java.util.Map;

public interface ISubsystemHost {//持有子系统的接口，可能是部件，也可能是某些改装件？
    SubPart getSubPart();
    Map<String, AbstractSubsystem> getSubsystems();
    Level getLevel();
}
