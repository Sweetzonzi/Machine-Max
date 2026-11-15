package io.github.sweetzonzi.machine_max.network.payload;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableObject;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.control.AbstractGuiAction;
import io.github.sweetzonzi.machine_max.common.mech.control.GuiActionType;
import io.github.sweetzonzi.machine_max.common.mech.control.GuiPulseAction;
import io.github.sweetzonzi.machine_max.common.mech.control.GuiSliderAction;
import io.github.sweetzonzi.machine_max.common.mech.control.GuiToggleAction;
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

import java.util.List;

/**
 * GUI 控件操作包（客户端→服务端）。<br>
 * 当玩家在设备控制面板中点击 PULSE 按钮、切换 TOGGLE 开关、拖拽 SLIDER 滑块时发送。
 *
 * @param subPartId      受控子系统所属子零件的全局 ID
 * @param subsystemIndex 受控子系统在该子零件声明序中的位置
 * @param actionIndex    GUI 动作索引
 * @param actionType     动作类型
 * @param value          滑块值
 */
public record GuiActionPayload(
        int subPartId,
        int subsystemIndex,
        int actionIndex,
        GuiActionType actionType,
        float value
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<GuiActionPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "gui_action_payload"));
    public static final StreamCodec<FriendlyByteBuf, GuiActionPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull GuiActionPayload decode(FriendlyByteBuf buf) {
            int subPartId = buf.readVarInt();
            int subsystemIndex = buf.readVarInt();
            int actionIndex = buf.readVarInt();
            GuiActionType actionType = GuiActionType.STREAM_CODEC.decode(buf);
            float value = buf.readFloat();
            return new GuiActionPayload(subPartId, subsystemIndex, actionIndex, actionType, value);
        }

        @Override
        public void encode(FriendlyByteBuf buffer, @NotNull GuiActionPayload payload) {
            buffer.writeVarInt(payload.subPartId());
            buffer.writeVarInt(payload.subsystemIndex());
            buffer.writeVarInt(payload.actionIndex());
            GuiActionType.STREAM_CODEC.encode(buffer, payload.actionType());
            buffer.writeFloat(payload.value());
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void serverHandler(final GuiActionPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            DestroyableObject object = ObjectManager.getDestroyableObject(context.player().level(), payload.subPartId());
            if (!(object instanceof SubPart subPart)) {
                MachineMax.LOGGER.warn("收到GuiActionPayload但维度 {} 中不存在 SubPart(id={})",
                        context.player().level().dimension().location(), payload.subPartId());
                return;
            }
            AbstractSubsystem subsystem = subPart.subsystemAt(payload.subsystemIndex());
            if (!(subsystem instanceof AbstractControllableSubsystem controllable) || !controllable.isActive()) {
                MachineMax.LOGGER.warn("收到GuiActionPayload但 SubPart(id={}) 上序号为 {} 的子系统不存在或未激活",
                        payload.subPartId(), payload.subsystemIndex());
                return;
            }

            List<AbstractGuiAction> guiActions = controllable.getControlGroupSet().getGuiActions();
            if (payload.actionIndex() < 0 || payload.actionIndex() >= guiActions.size()) {
                MachineMax.LOGGER.warn("收到GuiActionPayload但 actionIndex {} 越界 (大小={})",
                        payload.actionIndex(), guiActions.size());
                return;
            }

            AbstractGuiAction action = guiActions.get(payload.actionIndex());
            switch (payload.actionType()) {
                case PULSE -> {
                    GuiPulseAction pulse = (GuiPulseAction) action;
                    for (String targetName : pulse.targets) {
                        controllable.sendSignalToTarget(pulse.channel, targetName, 1.0f);
                    }
                }
                case TOGGLE -> {
                    GuiToggleAction toggle = (GuiToggleAction) action;
                    toggle.flip();
                    float signalValue = toggle.isActive() ? 1.0f : 0.0f;
                    for (String targetName : toggle.targets) {
                        controllable.sendSignalToTarget(toggle.channel, targetName, signalValue);
                    }
                }
                case SLIDER -> {
                    GuiSliderAction slider = (GuiSliderAction) action;
                    slider.setValue(payload.value());
                    for (String targetName : slider.targets) {
                        controllable.sendSignalToTarget(slider.channel, targetName, slider.getValue());
                    }
                }
            }
            controllable.saveData(new CompoundTag());
        });
    }
}
