package io.github.sweetzonzi.machine_max.network.payload.research;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record FreeRpSyncPayload(
        int freeRp
) implements CustomPacketPayload {
    public static final Type<FreeRpSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "free_rp_sync_payload"));
    public static final StreamCodec<ByteBuf, FreeRpSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, FreeRpSyncPayload::freeRp,
            FreeRpSyncPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handler(final FreeRpSyncPayload payload, final IPayloadContext context) {
        Player player = context.player();
        context.enqueueWork(() -> player.getData(MMAttachments.getBLUEPRINT()).setFreeResearchPoint(context.player(), payload.freeRp));
    }
}
