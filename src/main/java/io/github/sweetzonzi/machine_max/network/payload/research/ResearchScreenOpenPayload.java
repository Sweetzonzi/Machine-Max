package io.github.sweetzonzi.machine_max.network.payload.research;

import io.github.sweetzonzi.machine_max.MachineMax;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * 通知客户端打开研究台界面。
 *
 * <p>研究台界面由纯 {@code Screen} 承载，不再经菜单打开，因此由服务端在玩家使用研究台或
 * 研究平板时下发本载荷。</p>
 */
public record ResearchScreenOpenPayload() implements CustomPacketPayload {
    public static final Type<ResearchScreenOpenPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "research_screen_open_payload"));
    public static final StreamCodec<FriendlyByteBuf, ResearchScreenOpenPayload> STREAM_CODEC = StreamCodec.unit(new ResearchScreenOpenPayload());

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
