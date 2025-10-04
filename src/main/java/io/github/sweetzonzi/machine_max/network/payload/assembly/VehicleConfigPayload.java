package io.github.sweetzonzi.machine_max.network.payload.assembly;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.menu.VehicleNamingMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record VehicleConfigPayload(
        int menuId,
        String name
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<VehicleConfigPayload> TYPE = new CustomPacketPayload.Type<>
            (ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "vehicle_config"));

    public static final StreamCodec<ByteBuf, VehicleConfigPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, VehicleConfigPayload::menuId,
            ByteBufCodecs.STRING_UTF8, VehicleConfigPayload::name,
            VehicleConfigPayload::new
    );

    public static void handler(VehicleConfigPayload packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                if (serverPlayer.containerMenu.containerId == packet.menuId() &&
                        serverPlayer.containerMenu instanceof VehicleNamingMenu menu) {
                    ItemStack blueprintStack = menu.getBlueprintStack();
                    if (!blueprintStack.isEmpty()) {
                        //调用保存逻辑
                        VehicleNamingMenu.saveVehicleWithName(serverPlayer, blueprintStack, packet.name());
                    }
                }
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
