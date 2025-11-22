package io.github.sweetzonzi.machine_max.common.vehicle.event.connector;

import io.github.sweetzonzi.machine_max.common.vehicle.connector.AbstractConnector;
import lombok.Getter;
import net.neoforged.bus.api.ICancellableEvent;

@Getter
public abstract class ConnectorAttachEvent extends ConnectorEvent {
    public final AbstractConnector targetConnector;

    public ConnectorAttachEvent(AbstractConnector connector, AbstractConnector targetConnector) {
        super(connector);
        this.targetConnector = targetConnector;
    }

    public static class Pre extends ConnectorAttachEvent implements ICancellableEvent {
        public Pre(AbstractConnector connector, AbstractConnector targetConnector) {
            super(connector, targetConnector);
        }
    }

    public static class Post extends ConnectorAttachEvent {
        public Post(AbstractConnector connector, AbstractConnector targetConnector) {
            super(connector, targetConnector);
        }
    }
}
