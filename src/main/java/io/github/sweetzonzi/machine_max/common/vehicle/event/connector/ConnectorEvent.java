package io.github.sweetzonzi.machine_max.common.vehicle.event.connector;

import io.github.sweetzonzi.machine_max.common.vehicle.connector.AbstractConnector;
import lombok.Getter;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;

@Getter
public abstract class ConnectorEvent extends Event {
    private final AbstractConnector connector;
    private final Level level;
    protected ConnectorEvent(AbstractConnector connector) {
        this.connector = connector;
        this.level = connector.subPart.getLevel();
    }
}
