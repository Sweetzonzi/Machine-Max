package io.github.sweetzonzi.machine_max.network.handler.pda;

import io.github.sweetzonzi.machine_max.common.item.prop.PdaDepositService;
import io.github.sweetzonzi.machine_max.network.payload.pda.PdaSetDesignModePayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * {@link PdaSetDesignModePayload} 的服务端处理器：写入设计模式开关。
 *
 * <p>校验由 {@link PdaDepositService#setDesignMode} 内部完成；不满足时忽略本次请求。</p>
 */
public final class PdaSetDesignModeHandler {
    private PdaSetDesignModeHandler() {
    }

    public static void handler(final PdaSetDesignModePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            PdaDepositService.setDesignMode(player, payload.hand(), payload.on());
        });
    }
}
