package io.github.sweetzonzi.machine_max.network.payload;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableObject;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
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
 * 控制组按键绑定输入数据包（客户端→服务端）。<br>
 * 携带 SubPart 全局 ID 和绑定索引，服务端通过 ObjectManager 一步定位到目标子系统，
 * 无需逐级解析载具 UUID 和部件路径。
 * <p>
 * 构造链：ControlBinding.trigger → InputConstants.getKey(name) → KeyHooks.EVENT
 */
public record ControlBindingPayload(
        int subPartId,
        String subSystemName,
        int bindingIndex,
        int eventType
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ControlBindingPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "control_binding_payload"));
    public static final StreamCodec<FriendlyByteBuf, ControlBindingPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull ControlBindingPayload decode(FriendlyByteBuf buf) {
            int subPartId = buf.readInt();
            String subSystemName = buf.readUtf();
            int bindingIndex = buf.readVarInt();
            int eventType = buf.readVarInt();
            return new ControlBindingPayload(subPartId, subSystemName, bindingIndex, eventType);
        }

        @Override
        public void encode(FriendlyByteBuf buffer, @NotNull ControlBindingPayload payload) {
            buffer.writeInt(payload.subPartId());
            buffer.writeUtf(payload.subSystemName());
            buffer.writeVarInt(payload.bindingIndex());
            buffer.writeVarInt(payload.eventType());
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void serverHandler(final ControlBindingPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            DestroyableObject object = ObjectManager.getDestroyableObject(context.player().level(), payload.subPartId());
            if (object instanceof SubPart subPart) {
                AbstractSubsystem subsystem = subPart.subsystems.get(payload.subSystemName());
                if (subsystem instanceof AbstractControllableSubsystem controllable && controllable.isActive()) {
                    controllable.sendBindingSignal(payload.bindingIndex(), payload.eventType());
                } else {
                    MachineMax.LOGGER.warn("收到ControlBindingPayload但子系统 {} 不存在于 SubPart(id={})",
                            payload.subSystemName(), payload.subPartId());
                }
            } else {
                MachineMax.LOGGER.warn("收到ControlBindingPayload但维度 {} 中不存在 SubPart(id={})",
                        context.player().level().dimension().location(), payload.subPartId());
            }
        });
    }
}
