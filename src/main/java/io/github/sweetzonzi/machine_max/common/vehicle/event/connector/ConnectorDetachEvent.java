package io.github.sweetzonzi.machine_max.common.vehicle.event.connector;

import io.github.sweetzonzi.machine_max.common.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.AttachPointConnector;
import lombok.Getter;
import net.neoforged.bus.api.ICancellableEvent;
import org.jetbrains.annotations.NotNull;

@Getter
abstract public class ConnectorDetachEvent extends ConnectorEvent implements ICancellableEvent {
    private final AbstractConnector specialConnector;
    private final AttachPointConnector attachPointConnector;

    protected ConnectorDetachEvent(@NotNull AbstractConnector specialConnector, @NotNull AttachPointConnector attachPointConnector) {
        super(specialConnector);
        this.specialConnector = specialConnector;
        this.attachPointConnector = attachPointConnector;
    }

    public static class Pre extends ConnectorDetachEvent implements ICancellableEvent{
        public Pre(@NotNull AbstractConnector specialConnector, @NotNull AttachPointConnector attachPointConnector) {
            super(specialConnector, attachPointConnector);
        }
    }

    public static class Post extends ConnectorDetachEvent {
        public Post(@NotNull AbstractConnector specialConnector, @NotNull AttachPointConnector attachPointConnector) {
            super(specialConnector, attachPointConnector);
        }
    }
}
