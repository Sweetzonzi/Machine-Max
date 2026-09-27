package io.github.sweetzonzi.machine_max.network.handler.pda;

import io.github.sweetzonzi.machine_max.common.item.prop.PdaDepositService;
import io.github.sweetzonzi.machine_max.network.payload.pda.PdaSelectShortcutPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * {@link PdaSelectShortcutPayload} 的服务端处理器：写入当前格位。
 *
 * <p>校验由 {@link PdaDepositService#selectShortcut} 内部完成；不满足时忽略本次请求。</p>
 */
public final class PdaSelectShortcutHandler {
    private PdaSelectShortcutHandler() {
    }

    public static void handler(final PdaSelectShortcutPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            PdaDepositService.selectShortcut(player, payload.hand(), payload.shortcutIndex());
        });
    }
}
