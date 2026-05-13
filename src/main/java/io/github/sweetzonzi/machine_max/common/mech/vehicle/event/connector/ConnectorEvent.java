package io.github.sweetzonzi.machine_max.common.mech.vehicle.event.connector;

import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import lombok.Getter;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.NotNull;

@Getter
public abstract class ConnectorEvent extends Event {
    private final Level level;
    protected ConnectorEvent(@NotNull AbstractConnector connector) {
        this.level = connector.getSubPart().getLevel();
    }
}
