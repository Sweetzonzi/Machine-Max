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

/** 客户端载具放置意图；服务端必须重新校验手持物品与世界位置。
 *
 * @param hand 使用的手，服务端从该手重新读取物品
 * @param sourceId 可选模板 ID，仅用于与手持物品核对
 * @param groundAnchor 候选世界接地锚点；XZ 对齐参考根局部 AABB 的底心投影，Y 为接地高度
 * @param yaw 世界 Y 轴朝向，服务端仅接受有限的 90 度倍数
 */
public record VehiclePlacementRequestPayload(
        InteractionHand hand,
        ResourceLocation sourceId,
        Vec3 groundAnchor,
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
            buffer.writeDouble(value.groundAnchor.x);
            buffer.writeDouble(value.groundAnchor.y);
            buffer.writeDouble(value.groundAnchor.z);
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

    /**
     * 将请求交给服务端主线程，并由手持物品执行权威校验。
     *
     * @param payload 客户端载具放置请求
     * @param context 当前网络处理上下文，用于切换到服务端主线程并取得发起玩家
     */
    public static void serverHandler(VehiclePlacementRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().getItemInHand(payload.hand()).getItem() instanceof BaseVehicleItem item) {
                item.handlePlacementRequest(context.player(), payload);
            }
        });
    }
}
