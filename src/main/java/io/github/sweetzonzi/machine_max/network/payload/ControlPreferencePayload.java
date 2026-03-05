package io.github.sweetzonzi.machine_max.network.payload;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.attachment.ControlPreferenceAttachment;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record ControlPreferencePayload(ControlPreferenceAttachment preference) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ControlPreferencePayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "control_preference_payload"));
    public static final StreamCodec<FriendlyByteBuf, ControlPreferencePayload> STREAM_CODEC = new StreamCodec<FriendlyByteBuf, ControlPreferencePayload>() {
        @Override
        public @NotNull ControlPreferencePayload decode(@NotNull FriendlyByteBuf buf) {
            return new ControlPreferencePayload(ControlPreferenceAttachment.STREAM_CODEC.decode(buf));
        }

        @Override
        public void encode(@NotNull FriendlyByteBuf buf, ControlPreferencePayload payload) {
            ControlPreferenceAttachment.STREAM_CODEC.encode(buf, payload.preference());
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final ControlPreferencePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            player.setData(MMAttachments.getCONTROL_PREFERENCE(), payload.preference());
        });
    }
}
