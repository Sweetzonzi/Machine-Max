package io.github.sweetzonzi.machine_max.common.mech.vehicle.event.connector;

import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

@Getter
public abstract class ConnectorTickEvent extends ConnectorEvent {
    public final AbstractConnector connector;

    protected ConnectorTickEvent(@NotNull AbstractConnector connector) {
        super(connector);
        this.connector = connector;
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
