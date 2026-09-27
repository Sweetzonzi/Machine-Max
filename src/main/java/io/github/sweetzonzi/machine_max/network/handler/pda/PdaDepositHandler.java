package io.github.sweetzonzi.machine_max.network.handler.pda;

import io.github.sweetzonzi.machine_max.common.item.prop.PdaDepositService;
import io.github.sweetzonzi.machine_max.network.payload.pda.PdaDepositPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * {@link PdaDepositPayload} 的服务端处理器：执行存入并按结果给出提示。
 */
public final class PdaDepositHandler {
    private PdaDepositHandler() {
    }

    public static void handler(final PdaDepositPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            PdaDepositService.DepositResult result =
                    PdaDepositService.deposit(player, payload.hand(), payload.slots(), payload.all());
            if (result.stored() > 0) {
                player.displayClientMessage(Component.translatable(
                        "message.machine_max.pda.deposit.success", result.stored()), true);
            } else if (result.rejected() > 0) {
                player.displayClientMessage(Component.translatable(
                        "message.machine_max.pda.deposit.rejected", result.rejected()), true);
            }
        });
    }
}
