package io.github.sweetzonzi.machine_max.network.payload.assembly;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.item.prop.BaseVehicleItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** 客户端载具放置意图；服务端必须重新校验手持物品与世界位置。 */
public record VehiclePlacementRequestPayload(
        InteractionHand hand,
        ResourceLocation sourceId,
        Vec3 anchor,
        float yaw
) implements CustomPacketPayload {
    public static final Type<VehiclePlacementRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "vehicle_placement_request"));

    public static final StreamCodec<FriendlyByteBuf, VehiclePlacementRequestPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(@NotNull FriendlyByteBuf buffer, VehiclePlacementRequestPayload value) {
            buffer.writeEnum(value.hand);
            buffer.writeBoolean(value.sourceId != null);
            if (value.sourceId != null) buffer.writeResourceLocation(value.sourceId);
            buffer.writeDouble(value.anchor.x);
            buffer.writeDouble(value.anchor.y);
            buffer.writeDouble(value.anchor.z);
            buffer.writeFloat(value.yaw);
        }

        @Override
        public @NotNull VehiclePlacementRequestPayload decode(@NotNull FriendlyByteBuf buffer) {
            InteractionHand hand = buffer.readEnum(InteractionHand.class);
            ResourceLocation sourceId = buffer.readBoolean() ? buffer.readResourceLocation() : null;
            return new VehiclePlacementRequestPayload(hand,
                    sourceId,
                    new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                    buffer.readFloat());
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void serverHandler(VehiclePlacementRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().getItemInHand(payload.hand()).getItem() instanceof BaseVehicleItem item) {
                item.handlePlacementRequest(context.player(), payload);
            }
        });
    }
}
