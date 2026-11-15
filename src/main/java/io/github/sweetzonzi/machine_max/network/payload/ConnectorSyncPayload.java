package io.github.sweetzonzi.machine_max.network.payload;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableObject;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 连接点同步数据（服务端→客户端）。
 *
 * <p>以 {@code (子零件 ID, 连接点序号)} 定位：序号是连接点在该子零件声明序中的位置，双端解同一份内容定义、
 * 构造序相同，因此可以只传序号。名字仍是存档键与翻译键，不上线。</p>
 *
 * @param id             连接点所属子零件的全局 ID
 * @param connectorIndex 连接点在该子零件声明序中的位置
 * @param syncData       发生变化的数据
 */
public record ConnectorSyncPayload(
        int id,
        int connectorIndex,
        List<SynchedEntityData.DataValue<?>> syncData //发生变化的数据
) implements CustomPacketPayload {
    public static final Type<ConnectorSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "connector_sync_payload"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ConnectorSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull ConnectorSyncPayload decode(RegistryFriendlyByteBuf buffer) {
            int id = buffer.readVarInt();
            int connectorIndex = buffer.readVarInt();
            List<SynchedEntityData.DataValue<?>> syncData = new ArrayList<>();
            int i;
            while ((i = buffer.readUnsignedByte()) != 255) {
                syncData.add(SynchedEntityData.DataValue.read(buffer, i));
            }
            return new ConnectorSyncPayload(id, connectorIndex, syncData);
        }

        @Override
        public void encode(@NotNull RegistryFriendlyByteBuf buffer, ConnectorSyncPayload value) {
            buffer.writeVarInt(value.id());
            buffer.writeVarInt(value.connectorIndex());
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

    public static void handler(final ConnectorSyncPayload payload, final IPayloadContext context) {
        DestroyableObject object = ObjectManager.getDestroyableObject(context.player().level(), payload.id());
        if (object instanceof SubPart subPart) {
            AbstractConnector connector = subPart.connectorAt(payload.connectorIndex());
            // 目标缺失时整包丢弃：序号对不上说明两端结构定义已经不一致，回退不到别的连接点
            if (connector == null) {
                MachineMax.LOGGER.warn("丢弃连接点同步包：SubPart(id={}) 上没有序号为 {} 的连接点",
                        payload.id(), payload.connectorIndex());
                return;
            }
            context.enqueueWork(() -> connector.getSynchedData().assignValues(payload.syncData()));
        } else
            MachineMax.LOGGER.error("维度{}收到不存在物体{}的连接点同步数据包: 连接点序号 {}",
                    context.player().level().dimension().location(), payload.id(), payload.connectorIndex());
    }
}
