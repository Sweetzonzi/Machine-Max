package io.github.sweetzonzi.machine_max.network.payload;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableObject;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SubsystemController;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 一轮子系统同步批次（服务端→客户端）。
 *
 * <p>发送单元是<b>同一个控制器的一轮批次</b>，不是单个子系统：外层只有装配体身份，然后按条目
 * 列出本轮有变化的子系统。条目仍以 {@code (子零件 ID, 子系统序号)} 定位：序号是子系统在该子零件
 * 声明序中的位置，双端解同一份内容定义、构造序相同，因此可以只传 varint。名字仍是存档键与翻译键，
 * 不上线。</p>
 *
 * <p>批次不携带代次、序号或"是否全量"的标记：同步值是值表而不是事件流，客户端按
 * {@code (子零件 ID, 子系统序号, 数据项 ID)} 合并，同一字段后到的覆盖先到的。服务端在主线程上
 * 采样与发送成对发生、连接内传输有序，因此后到的批次采样一定更晚，覆盖总是朝新值方向进行；
 * 全量补推与变化广播因此只是"条目覆盖的字段多少"不同，不需要在线上区分。</p>
 *
 * <p>客户端不在收到时落地：条目并入控制器的待应用值表，由本端业务 tick 收尾统一写入，因此不会在
 * 模拟中途被逐项应用。</p>
 *
 * @param assemblyId 装配体身份，用于核对批次归属
 * @param entries    本轮有变化的子系统条目
 */
public record SubsystemSyncPayload(
        UUID assemblyId,
        List<Entry> entries
) implements CustomPacketPayload {

    /** 单个子系统的条目：结构寻址 + 本轮变化的访问器值。 */
    public record Entry(int subPartId, int subsystemIndex, List<SynchedEntityData.DataValue<?>> values) {
        static Entry read(RegistryFriendlyByteBuf buffer) {
            int subPartId = buffer.readVarInt();
            int subsystemIndex = buffer.readVarInt();
            List<SynchedEntityData.DataValue<?>> values = new ArrayList<>();
            int id;
            while ((id = buffer.readUnsignedByte()) != 255) {
                values.add(SynchedEntityData.DataValue.read(buffer, id));
            }
            return new Entry(subPartId, subsystemIndex, values);
        }

        void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(subPartId);
            buffer.writeVarInt(subsystemIndex);
            for (SynchedEntityData.DataValue<?> value : values) {
                value.write(buffer);
            }
            buffer.writeByte(255);
        }
    }

    public static final Type<SubsystemSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "subsystem_sync_payload"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SubsystemSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull SubsystemSyncPayload decode(RegistryFriendlyByteBuf buffer) {
            UUID assemblyId = buffer.readUUID();
            int count = buffer.readVarInt();
            List<Entry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) entries.add(Entry.read(buffer));
            return new SubsystemSyncPayload(assemblyId, entries);
        }

        @Override
        public void encode(@NotNull RegistryFriendlyByteBuf buffer, SubsystemSyncPayload value) {
            buffer.writeUUID(value.assemblyId());
            buffer.writeVarInt(value.entries().size());
            for (Entry entry : value.entries()) entry.write(buffer);
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 按批次里的第一个条目定位装配体与控制器，然后交给待应用值表。
     *
     * <p>定位走"子零件 → 所属部件 → 装配体"，因此不假设宿主一定是载具；装配体身份再与批次
     * 头部核对一次，不一致即整包丢弃。成员缺失时整包丢弃：条目序号对不上说明两端结构定义已经
     * 不一致，回退不到别的子系统。</p>
     */
    public static void handler(final SubsystemSyncPayload payload, final IPayloadContext context) {
        if (payload.entries().isEmpty()) return;
        DestroyableObject object = ObjectManager.getDestroyableObject(context.player().level(),
                payload.entries().getFirst().subPartId());
        if (!(object instanceof SubPart subPart) || subPart.part.assembly == null) {
            MachineMax.LOGGER.error("维度{}收到不存在子零件(id={})的子系统同步批次",
                    context.player().level().dimension().location(), payload.entries().getFirst().subPartId());
            return;
        }
        if (!subPart.part.assembly.getAssemblyId().equals(payload.assemblyId())) {
            MachineMax.LOGGER.warn("丢弃子系统同步批次：装配体身份不一致（包 {} / 实际 {}）",
                    payload.assemblyId(), subPart.part.assembly.getAssemblyId());
            return;
        }
        SubsystemController controller = subPart.part.assembly.getSubsystemController();
        controller.mergeSyncBatch(payload);
    }
}
