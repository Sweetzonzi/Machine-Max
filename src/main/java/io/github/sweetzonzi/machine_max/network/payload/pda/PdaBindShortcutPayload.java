package io.github.sweetzonzi.machine_max.network.payload.pda;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * 绑定或解绑设计模式快捷栏的一个格位（客户端 → 服务端）。
 *
 * @param hand          手持 PDA 的手
 * @param shortcutIndex 格位序号，合法域 0~8
 * @param recipe        待绑定的配方 id；{@code empty} 表示解绑
 */
public record PdaBindShortcutPayload(
        @NotNull InteractionHand hand,
        int shortcutIndex,
        @NotNull Optional<ResourceLocation> recipe
) implements CustomPacketPayload {
    public static final Type<PdaBindShortcutPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "pda_bind_shortcut_payload")
    );
    private static final StreamCodec<ByteBuf, Optional<ResourceLocation>> OPTIONAL_RECIPE =
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs::optional);
    public static final StreamCodec<FriendlyByteBuf, PdaBindShortcutPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(@NotNull FriendlyByteBuf buffer, @NotNull PdaBindShortcutPayload payload) {
            buffer.writeEnum(payload.hand);
            buffer.writeVarInt(payload.shortcutIndex);
            OPTIONAL_RECIPE.encode(buffer, payload.recipe);
        }

        @Override
        public @NotNull PdaBindShortcutPayload decode(@NotNull FriendlyByteBuf buffer) {
            InteractionHand hand = buffer.readEnum(InteractionHand.class);
            int shortcutIndex = buffer.readVarInt();
            Optional<ResourceLocation> recipe = OPTIONAL_RECIPE.decode(buffer);
            return new PdaBindShortcutPayload(hand, shortcutIndex, recipe);
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
