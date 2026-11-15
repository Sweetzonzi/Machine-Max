package io.github.sweetzonzi.machine_max.network.payload;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableObject;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.control.ControlGroupSet;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractControllableSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 控制组集合编辑结果保存包（客户端→服务端）。<br>
 * 编辑器保存配置时，将整个修改后的 ControlGroupSet 发送到服务端，
 * 服务端以 {@code (子零件 ID, 子系统序号)} 定位到目标子系统并持久化。
 *
 * @param subPartId      受控子系统所属子零件的全局 ID
 * @param subsystemIndex 受控子系统在该子零件声明序中的位置
 * @param controlGroupSet 修改后的控制组集合
 */
public record ControlGroupSetEditPayload(
        int subPartId,
        int subsystemIndex,
        ControlGroupSet controlGroupSet
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ControlGroupSetEditPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "control_group_set_edit_payload"));
    public static final StreamCodec<FriendlyByteBuf, ControlGroupSetEditPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull ControlGroupSetEditPayload decode(FriendlyByteBuf buf) {
            int subPartId = buf.readVarInt();
            int subsystemIndex = buf.readVarInt();
            ControlGroupSet cgs = ControlGroupSet.STREAM_CODEC.decode(buf);
            return new ControlGroupSetEditPayload(subPartId, subsystemIndex, cgs);
        }

        @Override
        public void encode(FriendlyByteBuf buffer, @NotNull ControlGroupSetEditPayload payload) {
            buffer.writeVarInt(payload.subPartId());
            buffer.writeVarInt(payload.subsystemIndex());
            ControlGroupSet.STREAM_CODEC.encode(buffer, payload.controlGroupSet());
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void serverHandler(final ControlGroupSetEditPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            DestroyableObject object = ObjectManager.getDestroyableObject(context.player().level(), payload.subPartId());
            if (object instanceof SubPart subPart) {
                AbstractSubsystem subsystem = subPart.subsystemAt(payload.subsystemIndex());
                if (subsystem instanceof AbstractControllableSubsystem controllable
                        && ((io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin) context.player()).machine_Max$getControllingSubsystem() == controllable) {
                    controllable.setControlGroupSet(payload.controlGroupSet());
                } else {
                    MachineMax.LOGGER.warn("收到ControlGroupSetEditPayload但 SubPart(id={}) 上序号为 {} 的子系统不存在",
                            payload.subPartId(), payload.subsystemIndex());
                }
            } else {
                MachineMax.LOGGER.warn("收到ControlGroupSetEditPayload但维度 {} 中不存在 SubPart(id={})",
                        context.player().level().dimension().location(), payload.subPartId());
            }
        });
    }
}
