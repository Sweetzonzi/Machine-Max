package io.github.sweetzonzi.machine_max.network.payload;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.vehicle.DestroyableObject;
import io.github.sweetzonzi.machine_max.common.vehicle.ObjectManager;
import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractSubsystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public record SubsystemSyncPayload(
        int id,
        String subsystemName,
        List<SynchedEntityData.DataValue<?>> syncData //发生变化的数据
) implements CustomPacketPayload {
    public static final Type<SubsystemSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "subsystem_sync_payload"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SubsystemSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull SubsystemSyncPayload decode(RegistryFriendlyByteBuf buffer) {
            int id = buffer.readInt();
            String name = buffer.readUtf();
            List<SynchedEntityData.DataValue<?>> syncData = new ArrayList<>();
            int i;
            while ((i = buffer.readUnsignedByte()) != 255) {
                syncData.add(SynchedEntityData.DataValue.read(buffer, i));
            }
            return new SubsystemSyncPayload(id, name, syncData);
        }

        @Override
        public void encode(@NotNull RegistryFriendlyByteBuf buffer, SubsystemSyncPayload value) {
            buffer.writeInt(value.id());
            buffer.writeUtf(value.subsystemName());
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

    public static void handler(final SubsystemSyncPayload payload, final IPayloadContext context) {
        DestroyableObject object = ObjectManager.getDestroyableObject(context.player().level(), payload.id());
        if (object instanceof SubPart subPart) {
            AbstractSubsystem subsystem = subPart.subsystems.get(payload.subsystemName());
            context.enqueueWork(() -> subsystem.getSynchedData().assignValues(payload.syncData()));
        } else
            MachineMax.LOGGER.error("维度{}收到不存在物体{}的子系统同步数据包: {}", context.player().level().dimension().location(), payload.id, payload.subsystemName());
    }
}
