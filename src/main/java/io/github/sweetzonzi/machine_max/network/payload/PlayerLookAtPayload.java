package io.github.sweetzonzi.machine_max.network.payload;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.client.input.CameraController;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;


public record PlayerLookAtPayload(
        float pitch,
        float yaw
) implements CustomPacketPayload {
    public static final Type<PlayerLookAtPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "player_look_at_payload"));
    public static final StreamCodec<FriendlyByteBuf, PlayerLookAtPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull PlayerLookAtPayload decode(FriendlyByteBuf buf) {
            return new PlayerLookAtPayload(
                    buf.readFloat(),
                    buf.readFloat()
            );
        }

        @Override
        public void encode(FriendlyByteBuf buffer, @NotNull PlayerLookAtPayload payload) {
            buffer.writeFloat(payload.pitch());
            buffer.writeFloat(payload.yaw());
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 服务器处理玩家视角同步包。
     */
    public static void serverHandler(final PlayerLookAtPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            context.player().setXRot(payload.pitch());
            context.player().setYRot(payload.yaw());
        });
    }
}
