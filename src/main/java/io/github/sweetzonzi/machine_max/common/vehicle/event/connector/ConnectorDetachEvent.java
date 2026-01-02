package io.github.sweetzonzi.machine_max.common.vehicle.event.connector;

import io.github.sweetzonzi.machine_max.common.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.SimpleConnector;
import lombok.Getter;
import net.neoforged.bus.api.ICancellableEvent;
import org.jetbrains.annotations.NotNull;

@Getter
abstract public class ConnectorDetachEvent extends ConnectorEvent implements ICancellableEvent {
    private final AbstractConnector advancedConnector;
    private final SimpleConnector simpleConnector;

    protected ConnectorDetachEvent(@NotNull AbstractConnector advancedConnector, @NotNull SimpleConnector simpleConnector) {
        super(advancedConnector);
        this.advancedConnector = advancedConnector;
        this.simpleConnector = simpleConnector;
    }

    public static class Pre extends ConnectorDetachEvent implements ICancellableEvent{
        public Pre(@NotNull AbstractConnector advancedConnector, @NotNull SimpleConnector simpleConnector) {
            super(advancedConnector, simpleConnector);
        }
    }

    public static class Post extends ConnectorDetachEvent {
        public Post(@NotNull AbstractConnector advancedConnector, @NotNull SimpleConnector simpleConnector) {
            super(advancedConnector, simpleConnector);
        }
    }
}
