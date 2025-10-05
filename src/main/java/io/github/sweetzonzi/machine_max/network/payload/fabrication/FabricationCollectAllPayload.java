package io.github.sweetzonzi.machine_max.network.payload.fabrication;

import io.github.sweetzonzi.machine_max.common.menu.FabricatingMenu;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record FabricationCollectAllPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<FabricationCollectAllPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("machine_max", "fabrication_collect_all"));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handler(FabricationCollectAllPayload message, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player.containerMenu instanceof FabricatingMenu menu) {
                menu.collectAllTask(player);
            }
        });
    }
}
