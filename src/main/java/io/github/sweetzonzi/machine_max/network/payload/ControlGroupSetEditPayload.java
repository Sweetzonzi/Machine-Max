package io.github.sweetzonzi.machine_max.network.payload;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableObject;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.control.ControlGroupSet;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractControllableSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 控制组集合编辑结果保存包（客户端→服务端）。<br>
 * 编辑器保存配置时，将整个修改后的 ControlGroupSet 发送到服务端，
 * 服务端通过 ObjectManager 定位到目标子系统并持久化。
 */
public record ControlGroupSetEditPayload(
        int subPartId,
        String subSystemName,
        ControlGroupSet controlGroupSet
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ControlGroupSetEditPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "control_group_set_edit_payload"));
    public static final StreamCodec<FriendlyByteBuf, ControlGroupSetEditPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull ControlGroupSetEditPayload decode(FriendlyByteBuf buf) {
            int subPartId = buf.readInt();
            String subSystemName = buf.readUtf();
            ControlGroupSet cgs = ControlGroupSet.STREAM_CODEC.decode(buf);
            return new ControlGroupSetEditPayload(subPartId, subSystemName, cgs);
        }

        @Override
        public void encode(FriendlyByteBuf buffer, @NotNull ControlGroupSetEditPayload payload) {
            buffer.writeInt(payload.subPartId());
            buffer.writeUtf(payload.subSystemName());
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
                AbstractSubsystem subsystem = subPart.subsystems.get(payload.subSystemName());
                if (subsystem instanceof AbstractControllableSubsystem controllable) {
                    controllable.setControlGroupSet(payload.controlGroupSet());
                } else {
                    MachineMax.LOGGER.warn("收到ControlGroupSetEditPayload但子系统 {} 不存在于 SubPart(id={})",
                            payload.subSystemName(), payload.subPartId());
                }
            } else {
                MachineMax.LOGGER.warn("收到ControlGroupSetEditPayload但维度 {} 中不存在 SubPart(id={})",
                        context.player().level().dimension().location(), payload.subPartId());
            }
        });
    }
}
