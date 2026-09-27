package io.github.sweetzonzi.machine_max.network.payload.pda;

import io.github.sweetzonzi.machine_max.MachineMax;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import org.jetbrains.annotations.NotNull;

/**
 * 写入 PDA 的设计模式开关（客户端 → 服务端）。
 *
 * <p>载荷携带目标状态而不是"翻转"指令：重复发送同一个值的结果是幂等的，因此丢包或重发都不会把开关
 * 翻回来。客户端在发包前已用同一段逻辑写过本地组件。</p>
 *
 * @param hand 手持 PDA 的手
 * @param on   目标状态
 */
public record PdaSetDesignModePayload(
        @NotNull InteractionHand hand,
        boolean on
) implements CustomPacketPayload {
    public static final Type<PdaSetDesignModePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "pda_set_design_mode_payload")
    );
    public static final StreamCodec<FriendlyByteBuf, PdaSetDesignModePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(@NotNull FriendlyByteBuf buffer, @NotNull PdaSetDesignModePayload payload) {
            buffer.writeEnum(payload.hand);
            buffer.writeBoolean(payload.on);
        }

        @Override
        public @NotNull PdaSetDesignModePayload decode(@NotNull FriendlyByteBuf buffer) {
            InteractionHand hand = buffer.readEnum(InteractionHand.class);
            boolean on = buffer.readBoolean();
            return new PdaSetDesignModePayload(hand, on);
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
