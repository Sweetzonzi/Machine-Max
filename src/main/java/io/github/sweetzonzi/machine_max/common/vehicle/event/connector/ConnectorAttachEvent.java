package io.github.sweetzonzi.machine_max.common.vehicle.event.connector;

import io.github.sweetzonzi.machine_max.common.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.AttachPointConnector;
import lombok.Getter;
import net.neoforged.bus.api.ICancellableEvent;
import org.jetbrains.annotations.NotNull;

@Getter
public abstract class ConnectorAttachEvent extends ConnectorEvent {
    private final AbstractConnector specialConnector;
    private final AttachPointConnector attachPointConnector;

    public ConnectorAttachEvent(@NotNull AbstractConnector specialConnector, @NotNull AttachPointConnector attachPointConnector) {
        super(specialConnector);
        this.specialConnector = specialConnector;
        this.attachPointConnector = attachPointConnector;
    }

    public static class Pre extends ConnectorAttachEvent implements ICancellableEvent {
        public Pre(@NotNull AbstractConnector specialConnector, @NotNull AttachPointConnector attachPointConnector) {
            super(specialConnector, attachPointConnector);
        }
    }

    public static class Post extends ConnectorAttachEvent {
        public Post(@NotNull AbstractConnector specialConnector, @NotNull AttachPointConnector attachPointConnector) {
            super(specialConnector, attachPointConnector);
        }
    }
}
