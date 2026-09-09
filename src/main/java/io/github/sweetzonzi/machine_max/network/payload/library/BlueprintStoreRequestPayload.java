package io.github.sweetzonzi.machine_max.network.payload.library;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.blueprint.BlueprintLibraryServerHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 客户端 → 服务端：「存入库」请求。
 *
 * <p>客户端手中蓝图物品的 {@code VEHICLE_DATA} 组件不含 meta（网络同步走
 * {@link io.github.sweetzonzi.machine_max.common.mech.vehicle.data.VehicleData#STREAM_CODEC}），
 * 因此不能直接读组件写文件，必须由服务端读取组件后回传
 * {@link io.github.sweetzonzi.machine_max.network.payload.assembly.VehicleDataSavedPayload}。</p>
 *
 * @param inventorySlot 玩家背包槽位索引，指向待入库的 {@code vehicle_blueprint} 物品
 */
public record BlueprintStoreRequestPayload(
        int inventorySlot
) implements CustomPacketPayload {
    public static final Type<BlueprintStoreRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "blueprint_store_request")
    );

    public static final StreamCodec<FriendlyByteBuf, BlueprintStoreRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BlueprintStoreRequestPayload::inventorySlot,
            BlueprintStoreRequestPayload::new
    );

    public static void serverHandler(BlueprintStoreRequestPayload packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                BlueprintLibraryServerHelper.handleStoreRequest(player, packet.inventorySlot());
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
