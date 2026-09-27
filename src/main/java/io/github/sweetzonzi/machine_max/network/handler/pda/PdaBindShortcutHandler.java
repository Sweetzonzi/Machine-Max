package io.github.sweetzonzi.machine_max.network.handler.pda;

import io.github.sweetzonzi.machine_max.common.item.prop.PdaDepositService;
import io.github.sweetzonzi.machine_max.network.payload.pda.PdaBindShortcutPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * {@link PdaBindShortcutPayload} 的服务端处理器：绑定或解绑格位。
 *
 * <p>校验由 {@link PdaDepositService#bindShortcut} 内部完成；不满足时忽略本次请求（不报错、不断开连接）。</p>
 */
public final class PdaBindShortcutHandler {
    private PdaBindShortcutHandler() {
    }

    public static void handler(final PdaBindShortcutPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            PdaDepositService.bindShortcut(player, payload.hand(), payload.shortcutIndex(), payload.recipe().orElse(null));
        });
    }
}
