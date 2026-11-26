package io.github.sweetzonzi.machine_max.network.payload.assembly;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.part.PartStaleness;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** 服务端权威的连续做旧状态更新载荷，通过一个子零件 ID 定位所属部件。 */
public record PartStalenessSyncPayload(int subPartId, float staleness) implements CustomPacketPayload {
    public static final Type<PartStalenessSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "part_staleness_sync_payload")
    );

    public static final StreamCodec<FriendlyByteBuf, PartStalenessSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull PartStalenessSyncPayload decode(@NotNull FriendlyByteBuf buffer) {
            return new PartStalenessSyncPayload(buffer.readInt(), buffer.readFloat());
        }

        @Override
        public void encode(@NotNull FriendlyByteBuf buffer, @NotNull PartStalenessSyncPayload value) {
            buffer.writeInt(value.subPartId());
            buffer.writeFloat(value.staleness());
        }
    };

    public PartStalenessSyncPayload {
        staleness = PartStaleness.requireValid(staleness);
    }

    /** 返回此载荷的网络类型标识。 */
    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 在客户端主线程中查找子零件，并更新其所属部件的做旧进度。 */
    public static void handle(PartStalenessSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var level = context.player().level();
            var object = ObjectManager.getDestroyableObject(level, payload.subPartId());
            if (object instanceof SubPart subPart) {
                subPart.part.setStaleness(payload.staleness());
            } else {
                MachineMax.LOGGER.warn("维度{}中的子零件(id={})不存在，忽略陈旧度同步。",
                        level.dimension().location(), payload.subPartId());
            }
        });
    }
}
