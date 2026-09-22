package io.github.sweetzonzi.machine_max.network.payload.grab;

import io.github.sweetzonzi.machine_max.common.mech.grab.GrabManager;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 → 服务端：丢出（牵引中触发攻击）。
 * <p>
 * 不携带任何字段：冲量大小与抛出方向由服务端用属性与视线重算，客户端无法指定超大冲量
 * （详细设计文档 §8.5）。
 */
public record GrabThrowPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<GrabThrowPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("machine_max", "grab_throw"));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handler(GrabThrowPayload message, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                GrabManager.get(player.level()).throwGrab(player);
            }
        });
    }
}
