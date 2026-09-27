package io.github.sweetzonzi.machine_max.network.payload.pda;

import io.github.sweetzonzi.machine_max.MachineMax;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import org.jetbrains.annotations.NotNull;

/**
 * 写入 PDA 的当前格位序号（客户端 → 服务端）。
 *
 * <p>载荷携带目标格位而不是"加一/减一"指令：丢包或重发的结果都是幂等的；客户端在发包前已用同一段
 * 逻辑写过本地组件，因此服务端回显的内容与客户端已持有的一致。</p>
 *
 * @param hand          手持 PDA 的手
 * @param shortcutIndex 新的当前格位序号，合法域 0~8
 */
public record PdaSelectShortcutPayload(
        @NotNull InteractionHand hand,
        int shortcutIndex
) implements CustomPacketPayload {
    public static final Type<PdaSelectShortcutPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "pda_select_shortcut_payload")
    );
    public static final StreamCodec<FriendlyByteBuf, PdaSelectShortcutPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(@NotNull FriendlyByteBuf buffer, @NotNull PdaSelectShortcutPayload payload) {
            buffer.writeEnum(payload.hand);
            buffer.writeVarInt(payload.shortcutIndex);
        }

        @Override
        public @NotNull PdaSelectShortcutPayload decode(@NotNull FriendlyByteBuf buffer) {
            InteractionHand hand = buffer.readEnum(InteractionHand.class);
            int shortcutIndex = buffer.readVarInt();
            return new PdaSelectShortcutPayload(hand, shortcutIndex);
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
