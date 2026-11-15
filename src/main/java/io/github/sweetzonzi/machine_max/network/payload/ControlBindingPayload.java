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
 * 以 {@code (子零件 ID, 子系统序号)} 一步定位到目标子系统，无需逐级解析载具 UUID 和部件路径。
 * 序号是该子系统在子零件声明序中的位置，名字不上线。
 * <p>
 * 构造链：ControlBinding.trigger → InputConstants.getKey(name) → KeyHooks.EVENT
 *
 * @param subPartId      受控子系统所属子零件的全局 ID
 * @param subsystemIndex 受控子系统在该子零件声明序中的位置
 * @param bindingIndex   控制组绑定索引
 * @param eventType      0 按下 / 1 松开
 */
public record ControlBindingPayload(
        int subPartId,
        int subsystemIndex,
        int bindingIndex,
        int eventType
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ControlBindingPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "control_binding_payload"));
    public static final StreamCodec<FriendlyByteBuf, ControlBindingPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull ControlBindingPayload decode(FriendlyByteBuf buf) {
            int subPartId = buf.readVarInt();
            int subsystemIndex = buf.readVarInt();
            int bindingIndex = buf.readVarInt();
            int eventType = buf.readVarInt();
            return new ControlBindingPayload(subPartId, subsystemIndex, bindingIndex, eventType);
        }

        @Override
        public void encode(FriendlyByteBuf buffer, @NotNull ControlBindingPayload payload) {
            buffer.writeVarInt(payload.subPartId());
            buffer.writeVarInt(payload.subsystemIndex());
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
                AbstractSubsystem subsystem = subPart.subsystemAt(payload.subsystemIndex());
                // 来源校验：换座后到达的旧座位按键包必须被丢弃，否则会把刚清掉的信号重新写回旧座位
                if (subsystem instanceof AbstractControllableSubsystem controllable
                        && AbstractControllableSubsystem.isControlledBy(controllable, context.player())) {
                    controllable.sendBindingSignal(payload.bindingIndex(), payload.eventType());
                } else {
                    MachineMax.LOGGER.debug("丢弃控制绑定包：序号 {} 的子系统 (SubPart id={}) 不在玩家 {} 的控制下",
                            payload.subsystemIndex(), payload.subPartId(), context.player().getName().getString());
                }
            } else {
                MachineMax.LOGGER.warn("收到ControlBindingPayload但维度 {} 中不存在 SubPart(id={})",
                        context.player().level().dimension().location(), payload.subPartId());
            }
        });
    }
}
