package io.github.sweetzonzi.machine_max.network.payload.assembly;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableObject;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record PartPaintPayload(
        int subPartId,
        String textureName
) implements CustomPacketPayload {
    public static final Type<PartPaintPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "part_paint_payload")
    );
    public static final StreamCodec<FriendlyByteBuf, PartPaintPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull PartPaintPayload decode(@NotNull FriendlyByteBuf buffer) {
            int subPartId = buffer.readInt();
            String textureName = buffer.readUtf();
            return new PartPaintPayload(subPartId, textureName);
        }

        @Override
        public void encode(@NotNull FriendlyByteBuf buffer, @NotNull PartPaintPayload value) {
            buffer.writeInt(value.subPartId);
            buffer.writeUtf(value.textureName);
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PartPaintPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            DestroyableObject object = ObjectManager.getDestroyableObject(context.player().level(), payload.subPartId());
            if (object instanceof SubPart subPart) {
                subPart.switchTexture(payload.textureName());
            } else MachineMax.LOGGER.error("维度{}中不存在SubPart(id={})，无法切换涂装。",
                    context.player().level().dimension().location(), payload.subPartId());
        });
    }
}
