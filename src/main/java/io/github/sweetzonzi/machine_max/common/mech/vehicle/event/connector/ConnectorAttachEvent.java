package io.github.sweetzonzi.machine_max.common.mech.vehicle.event.connector;

import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.SimpleConnector;
import lombok.Getter;
import net.neoforged.bus.api.ICancellableEvent;
import org.jetbrains.annotations.NotNull;

@Getter
public abstract class ConnectorAttachEvent extends ConnectorEvent {
    private final AbstractConnector advancedConnector;
    private final SimpleConnector simpleConnector;

    public ConnectorAttachEvent(@NotNull AbstractConnector advancedConnector, @NotNull SimpleConnector simpleConnector) {
        super(advancedConnector);
        this.advancedConnector = advancedConnector;
        this.simpleConnector = simpleConnector;
    }

    public static class Pre extends ConnectorAttachEvent implements ICancellableEvent {
        public Pre(@NotNull AbstractConnector advancedConnector, @NotNull SimpleConnector simpleConnector) {
            super(advancedConnector, simpleConnector);
        }
    }

    public static class Post extends ConnectorAttachEvent {
        public Post(@NotNull AbstractConnector advancedConnector, @NotNull SimpleConnector simpleConnector) {
            super(advancedConnector, simpleConnector);
        }
    }
}
