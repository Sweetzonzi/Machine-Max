package io.github.sweetzonzi.machine_max.common.vehicle.event.connector;

import io.github.sweetzonzi.machine_max.common.vehicle.connector.AbstractConnector;

public abstract class ConnectorTickEvent extends ConnectorEvent {

    protected ConnectorTickEvent(AbstractConnector connector) {
        super(connector);
    }

    public static class Pre extends ConnectorTickEvent {
        public Pre(AbstractConnector connector) {
            super(connector);
        }
    }

    public static class Post extends ConnectorTickEvent {
        public Post(AbstractConnector connector) {
            super(connector);
        }
    }

    public static class PrePhys extends ConnectorTickEvent {
        public PrePhys(AbstractConnector connector) {
            super(connector);
        }
    }

    public static class PostPhys extends ConnectorTickEvent {
        public PostPhys(AbstractConnector connector) {
            super(connector);
        }
    }
}
