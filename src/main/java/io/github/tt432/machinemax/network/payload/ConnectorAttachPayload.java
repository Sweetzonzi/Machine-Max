package io.github.tt432.machinemax.network.payload;

import io.github.tt432.machinemax.MachineMax;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class ConnectorAttachPayload implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ConnectorAttachPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "connector_attach_payload")
    );
    @Override
    public @NotNull CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
