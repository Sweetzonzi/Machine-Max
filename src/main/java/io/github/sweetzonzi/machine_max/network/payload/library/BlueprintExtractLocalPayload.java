package io.github.sweetzonzi.machine_max.network.payload.library;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.blueprint.BlueprintLibraryServerHelper;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.BlueprintMeta;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.VehicleData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 客户端 → 服务端：取出「玩家库蓝图」。
 *
 * <p>携带完整 {@link VehicleData}（{@code VehicleData.STREAM_CODEC} 不含 meta）与<b>独立附带</b>的
 * {@link BlueprintMeta}，服务端须先 {@code vehicleData.withMeta(meta)} 再校验 / 归一化。</p>
 *
 * @param vehicleData 客户端本地蓝图数据（不可信输入）
 * @param meta        元信息，作为独立字段传输
 */
public record BlueprintExtractLocalPayload(
        VehicleData vehicleData,
        BlueprintMeta meta
) implements CustomPacketPayload {
    public static final Type<BlueprintExtractLocalPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "blueprint_extract_local")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintExtractLocalPayload> STREAM_CODEC = StreamCodec.composite(
            VehicleData.STREAM_CODEC, BlueprintExtractLocalPayload::vehicleData,
            BlueprintMeta.STREAM_CODEC, BlueprintExtractLocalPayload::meta,
            BlueprintExtractLocalPayload::new
    );

    public static void serverHandler(BlueprintExtractLocalPayload packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                BlueprintLibraryServerHelper.handleExtractLocal(player, packet.vehicleData(), packet.meta());
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
