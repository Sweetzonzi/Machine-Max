package io.github.sweetzonzi.machine_max.network.payload;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableObject;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.InteractBox;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record SubsystemInteractPayload(
        int subPartId,
        String interactBoxName
) implements CustomPacketPayload {
    public static final Type<SubsystemInteractPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "subsystem_interact_payload")
    );
    public static final StreamCodec<FriendlyByteBuf, SubsystemInteractPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull SubsystemInteractPayload decode(FriendlyByteBuf buffer) {
            return new SubsystemInteractPayload(buffer.readInt(), buffer.readUtf());
        }

        @Override
        public void encode(FriendlyByteBuf buffer, @NotNull SubsystemInteractPayload value) {
            buffer.writeInt(value.subPartId());
            buffer.writeUtf(value.interactBoxName());
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
        PacketDistributor.sendToPlayersInDimension((ServerLevel) player.level(), payload);
    }

    public static void handle(SubsystemInteractPayload payload, IPayloadContext context) {
        DestroyableObject object = ObjectManager.getDestroyableObject(context.player().level(), payload.subPartId());
        if (object instanceof SubPart subPart) {
            InteractBox interactBox = subPart.interactBoxes.get(payload.interactBoxName());
            if (interactBox != null)
                context.enqueueWork(() -> interactBox.interact(context.player()));
            else
                MachineMax.LOGGER.error("SubPart(id={})中未找到交互框{}，无法互动。", payload.subPartId(), payload.interactBoxName());
        } else
            MachineMax.LOGGER.error("维度{}中不存在SubPart(id={})，无法互动。",
                    context.player().level().dimension().location(), payload.subPartId());
    }
}
