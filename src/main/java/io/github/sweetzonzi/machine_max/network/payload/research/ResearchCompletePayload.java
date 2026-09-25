package io.github.sweetzonzi.machine_max.network.payload.research;

import io.github.sweetzonzi.machine_max.MachineMax;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 通知客户端一个研发项目已完成。
 *
 * @param researchId 研发配方ID
 * @param product    该条目 {@code unlock_recipe} 的产物物品，仅供完成弹窗展示；研发不产出实物
 */
public record ResearchCompletePayload(
        ResourceLocation researchId,
        ItemStack product
) implements CustomPacketPayload {
    public static final Type<ResearchCompletePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "research_complete_payload"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ResearchCompletePayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, ResearchCompletePayload::researchId,
            ItemStack.OPTIONAL_STREAM_CODEC, ResearchCompletePayload::product,
            ResearchCompletePayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
