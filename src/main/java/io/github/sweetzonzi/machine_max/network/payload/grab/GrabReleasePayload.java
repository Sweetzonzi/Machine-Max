package io.github.sweetzonzi.machine_max.network.payload.grab;

import io.github.sweetzonzi.machine_max.common.mech.grab.GrabConstraint;
import io.github.sweetzonzi.machine_max.common.mech.grab.GrabManager;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 → 服务端：放下（松开抓取输入）。
 * <p>
 * 不携带任何字段。放下是显式事件，不需要靠超时推断（详细设计文档 §8.5）。
 */
public record GrabReleasePayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<GrabReleasePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("machine_max", "grab_release"));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handler(GrabReleasePayload message, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                GrabManager.get(player.level()).releaseGrab(player, GrabConstraint.ReleaseReason.INPUT);
            }
        });
    }
}
