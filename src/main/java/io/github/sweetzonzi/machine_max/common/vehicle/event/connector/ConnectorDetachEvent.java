package io.github.sweetzonzi.machine_max.common.vehicle.event.connector;

import io.github.sweetzonzi.machine_max.common.vehicle.connector.AbstractConnector;
import lombok.Getter;
import net.neoforged.bus.api.ICancellableEvent;

@Getter
abstract public class ConnectorDetachEvent extends ConnectorEvent implements ICancellableEvent {
    public final AbstractConnector connectedConnector;

    protected ConnectorDetachEvent(AbstractConnector connectorA, AbstractConnector connectorB) {
        super(connectorA);
        this.connectedConnector = connectorB;
    }

    public static class Pre extends ConnectorDetachEvent implements ICancellableEvent{
        public Pre(AbstractConnector connectorA, AbstractConnector connectorB) {
            super(connectorA, connectorB);
        }
    }

    public static class Post extends ConnectorDetachEvent {
        public Post(AbstractConnector connectorA, AbstractConnector connectorB) {
            super(connectorA, connectorB);
        }
    }
}
