package io.github.sweetzonzi.machine_max.network.payload.library;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.blueprint.BlueprintLibraryServerHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 客户端 → 服务端：取出「内容包蓝图」。
 *
 * <p>只发送 {@code blueprintId}，服务端自行从 {@code MMDynamicRes.BLUEPRINTS} 解析模板，
 * 客户端无法伪造模板数据。</p>
 *
 * @param blueprintId 内容包蓝图注册 id（{@code BlueprintData} 的 id，非模板 id）
 */
public record BlueprintExtractPackPayload(
        ResourceLocation blueprintId
) implements CustomPacketPayload {
    public static final Type<BlueprintExtractPackPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "blueprint_extract_pack")
    );

    public static final StreamCodec<FriendlyByteBuf, BlueprintExtractPackPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, BlueprintExtractPackPayload::blueprintId,
            BlueprintExtractPackPayload::new
    );

    public static void serverHandler(BlueprintExtractPackPayload packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                BlueprintLibraryServerHelper.handleExtractPack(player, packet.blueprintId());
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
