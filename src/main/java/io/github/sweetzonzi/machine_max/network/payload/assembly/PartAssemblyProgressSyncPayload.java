package io.github.sweetzonzi.machine_max.network.payload.assembly;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 服务端下发普通部件的组装、材料供给与组装形态。
 *
 * <p>以该部件任一已注册子零件的运行时 ID 寻址，由对象表取得子零件后访问所属部件。
 * 这条路径不要求装配体注册为载具，也不依赖代理实体；子零件的 ID 由创建数据同步到客户端。</p>
 *
 * @param subPartId 目标部件中一个仍在对象表内的子零件 ID
 * @param assemblingProgress 组装完成比例
 * @param materialProgress 已供给的材料项数
 * @param assembling 是否仍处于组装形态
 */
public record PartAssemblyProgressSyncPayload(
        int subPartId,
        float assemblingProgress,
        int materialProgress,
        boolean assembling
) implements CustomPacketPayload {
    public static final Type<PartAssemblyProgressSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "part_assembly_progress_sync_payload")
    );

    /** 固定编码顺序为子零件 ID、组装比例、材料项数、组装形态，共 13 字节。 */
    public static final StreamCodec<FriendlyByteBuf, PartAssemblyProgressSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        /** 按固定字段顺序读取一次普通部件状态更新。 */
        @Override
        public @NotNull PartAssemblyProgressSyncPayload decode(@NotNull FriendlyByteBuf buffer) {
            int subPartId = buffer.readInt();
            float assemblingProgress = buffer.readFloat();
            int materialProgress = buffer.readInt();
            boolean assembling = buffer.readBoolean();
            return new PartAssemblyProgressSyncPayload(subPartId, assemblingProgress, materialProgress, assembling);
        }

        /** 写出子零件寻址信息与制造状态，字段顺序与解码器一致。 */
        @Override
        public void encode(@NotNull FriendlyByteBuf buffer, @NotNull PartAssemblyProgressSyncPayload value) {
            buffer.writeInt(value.subPartId());
            buffer.writeFloat(value.assemblingProgress());
            buffer.writeInt(value.materialProgress());
            buffer.writeBoolean(value.assembling());
        }
    };

    /** 返回本载荷在同步协议组中的注册类型。 */
    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 在接收端主线程查询当前世界的对象表，再更新子零件所属的普通部件。
     *
     * <p>查找和写入位于同一个排队任务内，避免捕获随后被注销的部件。目标缺失、不是子零件，
     * 或所属部件不是普通部件时记录诊断并忽略；不会回退到载具或装配体 UUID 查询。</p>
     */
    public static void handle(PartAssemblyProgressSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var level = context.player().level();
            var object = ObjectManager.getDestroyableObject(level, payload.subPartId());
            if (object instanceof SubPart subPart && subPart.part instanceof RegularPart part) {
                part.setAssemblingProgress(payload.assemblingProgress());
                part.setMaterialProgress(payload.materialProgress());
                part.setAssembling(payload.assembling());
            } else {
                MachineMax.LOGGER.warn("维度{}中的子零件(id={})不存在或不属于普通部件，忽略组装状态同步。",
                        level.dimension().location(), payload.subPartId());
            }
        });
    }
}
