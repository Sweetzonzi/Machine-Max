package io.github.sweetzonzi.machine_max.network.payload;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.vehicle.DestroyableObject;
import io.github.sweetzonzi.machine_max.common.vehicle.ObjectManager;
import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public record SubPartSyncPayload(
        int id,
        List<SynchedEntityData.DataValue<?>> syncData //发生变化的数据
) implements CustomPacketPayload {
    public static final Type<SubPartSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "subpart_data_sync_payload"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SubPartSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull SubPartSyncPayload decode(RegistryFriendlyByteBuf buffer) {
            int id = buffer.readInt();
            List<SynchedEntityData.DataValue<?>> syncData = new ArrayList<>();
            int i;
            while ((i = buffer.readUnsignedByte()) != 255) {
                syncData.add(SynchedEntityData.DataValue.read(buffer, i));
            }
            return new SubPartSyncPayload(id, syncData);
        }

        @Override
        public void encode(@NotNull RegistryFriendlyByteBuf buffer, SubPartSyncPayload value) {
            buffer.writeInt(value.id());
            for (SynchedEntityData.DataValue<?> datavalue : value.syncData()) {
                datavalue.write(buffer);
            }
            buffer.writeByte(255);
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handler(final SubPartSyncPayload payload, final IPayloadContext context) {
        DestroyableObject object = ObjectManager.getDestroyableObject(context.player().level(), payload.id());
        if (object instanceof SubPart subPart) {
            context.enqueueWork(() -> subPart.getSynchedData().assignValues(payload.syncData()));
        } else
            MachineMax.LOGGER.error("维度{}收到不存在载具的同步数据包: {}", context.player().level().dimension().location(), payload.id);
    }
}
