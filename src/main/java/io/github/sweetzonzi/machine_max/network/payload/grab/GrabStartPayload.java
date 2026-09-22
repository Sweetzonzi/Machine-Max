package io.github.sweetzonzi.machine_max.network.payload.grab;

import io.github.sweetzonzi.machine_max.common.mech.grab.GrabManager;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 → 服务端：开始抓取。
 * <p>
 * 不携带任何字段：目标、抓取点、方向、距离与全部受力参数由服务端用真实视线与属性重算
 * （详细设计文档 §8.5 的安全口径）。
 */
public record GrabStartPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<GrabStartPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("machine_max", "grab_start"));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handler(GrabStartPayload message, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                GrabManager.get(player.level()).startGrab(player);
            }
        });
    }
}
