package io.github.sweetzonzi.machine_max.network.payload;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableObject;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.InteractBox;
import lombok.Data;
import lombok.experimental.Accessors;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

@Accessors(chain=true)
@Data
public class SubsystemInteractPayload implements CustomPacketPayload {
    UUID excutePlayerUUID = null;
    final int subPartId;
    final String interactBoxName;
    public SubsystemInteractPayload(int subPartId, String interactBoxName) {
        this.subPartId = subPartId;
        this.interactBoxName = interactBoxName;
    }
    public static final Type<SubsystemInteractPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "subsystem_interact_payload")
    );
    public static final StreamCodec<FriendlyByteBuf, SubsystemInteractPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull SubsystemInteractPayload decode(FriendlyByteBuf buffer) {
            return new SubsystemInteractPayload(buffer.readInt(), buffer.readUtf())
                    .setExcutePlayerUUID(buffer.readBoolean() ? null : buffer.readUUID());
        }

        @Override
        public void encode(FriendlyByteBuf buffer, @NotNull SubsystemInteractPayload value) {
            buffer.writeInt(value.subPartId);
            buffer.writeUtf(value.interactBoxName);
            boolean uuidIsNull = value.excutePlayerUUID == null;
            buffer.writeBoolean(uuidIsNull);
            if (!uuidIsNull) buffer.writeUUID(value.excutePlayerUUID);
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    //将其他玩家的输入同步至本机，以在客户端模拟其他玩家的操作
    public static void clientHandler(final SubsystemInteractPayload payload, final IPayloadContext context) {
        handle(payload, context);
    }

    public static void serverHandler(final SubsystemInteractPayload payload, final IPayloadContext context) {
        handle(payload, context);
        //将玩家输入转发给其他玩家，以在其他玩家客户端模拟自己的操作
        Player player = context.player();
        PacketDistributor.sendToPlayersInDimension((ServerLevel) player.level(),
                payload.setExcutePlayerUUID(player.getUUID())); // 传输前将当前触发者UUID填入
    }

    public static void handle(SubsystemInteractPayload payload, IPayloadContext context) {
        Level level = context.player().level();
        String envStr = level.isClientSide() ? "客户端" : "服务端";
        Player executingPlayer =
                payload.getExcutePlayerUUID() == null ?
                context.player() : //为空，说明是服务端
                level.getPlayerByUUID(payload.excutePlayerUUID);
        if (executingPlayer == null) {
            MachineMax.LOGGER.warn("UUID 为 {} 的玩家在该{}未被发现, handle发送终止", payload.excutePlayerUUID, envStr);
            return;
        }
        DestroyableObject object = ObjectManager.getDestroyableObject(level, payload.getSubPartId());
        if (object instanceof SubPart subPart) {
            InteractBox interactBox = subPart.interactBoxes.get(payload.getInteractBoxName());
            if (interactBox != null)
                context.enqueueWork(() -> interactBox.interact(executingPlayer));
            else
                MachineMax.LOGGER.error("SubPart(id={})中未找到交互框{}，无法互动。", payload.getSubPartId(), payload.getInteractBoxName());
        } else
            MachineMax.LOGGER.error("维度{}中不存在SubPart(id={})，无法互动。",
                    level.dimension().location(), payload.getSubPartId());
    }
}
