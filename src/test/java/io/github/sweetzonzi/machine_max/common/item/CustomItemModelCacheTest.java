package io.github.sweetzonzi.machine_max.common.item;

import cn.solarmoon.spark_core.animation.IAnimatable;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemDisplayContext;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomItemModelCacheTest {
    @Test
    void savedItemsDecodeWithIndependentEmptyModelCaches() {
        var codec = CustomItemModelCache.CODEC;
        var encoded = codec.encodeStart(JsonOps.INSTANCE, new HashMap<>()).getOrThrow();
        var trackModels = codec.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        var otherItemModels = codec.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertNotSame(trackModels, otherItemModels);
        trackModels.put(ItemDisplayContext.GUI, null);
        assertTrue(otherItemModels.isEmpty());
        assertTrue(codec.parse(JsonOps.INSTANCE, encoded).getOrThrow().isEmpty());
    }

    @Test
    void syncedItemsDecodeWithIndependentEmptyModelCaches() {
        var type = DataComponentType.<HashMap<ItemDisplayContext, IAnimatable<?>>>builder()
                .persistent(CustomItemModelCache.CODEC).build();
        var codec = type.streamCodec();
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            codec.encode(buffer, new HashMap<>());
            codec.encode(buffer, new HashMap<>());
            var trackModels = codec.decode(buffer);
            var otherItemModels = codec.decode(buffer);

            assertNotSame(trackModels, otherItemModels);
            trackModels.put(ItemDisplayContext.GUI, null);
            assertTrue(otherItemModels.isEmpty());
        } finally {
            buffer.release();
        }
    }

    @Test
    void writingModelPreservesSourceCacheAndOtherDisplayContexts() {
        HashMap<ItemDisplayContext, IAnimatable<?>> source = new HashMap<>();
        source.put(ItemDisplayContext.GROUND, null);

        var result = CustomItemModelCache.withModel(source, ItemDisplayContext.GUI, null);

        assertNotSame(source, result);
        assertFalse(source.containsKey(ItemDisplayContext.GUI));
        assertTrue(result.containsKey(ItemDisplayContext.GUI));
        assertTrue(result.containsKey(ItemDisplayContext.GROUND));
    }
}
