package io.github.sweetzonzi.machine_max.network.payload.research;

import com.mojang.datafixers.util.Pair;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.attachment.BluePrintAttachment;
import io.github.sweetzonzi.machine_max.util.data.RpAddReason;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record FreeRpSyncPayload(
        int freeRp,
        List<Pair<RpAddReason, Integer>> rpChanges
) implements CustomPacketPayload {
    public static final Type<FreeRpSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "free_rp_sync_payload"));
    public static final StreamCodec<FriendlyByteBuf, FreeRpSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, FreeRpSyncPayload::freeRp,
            BluePrintAttachment.RP_CHANGE_LIST_STREAM_CODEC, FreeRpSyncPayload::rpChanges,
            FreeRpSyncPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
