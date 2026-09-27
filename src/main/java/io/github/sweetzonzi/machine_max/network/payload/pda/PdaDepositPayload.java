package io.github.sweetzonzi.machine_max.network.payload.pda;

import io.github.sweetzonzi.machine_max.MachineMax;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 把背包中的蓝图存入 PDA（客户端 → 服务端）。
 *
 * @param hand  手持 PDA 的手
 * @param slots 目标背包槽位；{@code all} 为 true 时本字段被忽略
 * @param all   是否扫描整个背包（主背包 36 格 + 副手）
 */
public record PdaDepositPayload(
        @NotNull InteractionHand hand,
        @NotNull List<Integer> slots,
        boolean all
) implements CustomPacketPayload {
    public static final Type<PdaDepositPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "pda_deposit_payload")
    );
    public static final StreamCodec<FriendlyByteBuf, PdaDepositPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(@NotNull FriendlyByteBuf buffer, @NotNull PdaDepositPayload payload) {
            buffer.writeEnum(payload.hand);
            buffer.writeBoolean(payload.all);
            buffer.writeVarInt(payload.slots.size());
            for (int slot : payload.slots) buffer.writeVarInt(slot);
        }

        @Override
        public @NotNull PdaDepositPayload decode(@NotNull FriendlyByteBuf buffer) {
            InteractionHand hand = buffer.readEnum(InteractionHand.class);
            boolean all = buffer.readBoolean();
            int size = buffer.readVarInt();
            List<Integer> slots = new ArrayList<>(size);
            for (int i = 0; i < size; i++) slots.add(buffer.readVarInt());
            return new PdaDepositPayload(hand, slots, all);
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
