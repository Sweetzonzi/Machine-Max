package io.github.sweetzonzi.machine_max.common.item;

import cn.solarmoon.spark_core.animation.IAnimatable;
import com.mojang.serialization.Codec;
import io.github.sweetzonzi.machine_max.common.registry.MMDataComponents;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** 自定义物品动画体缓存的序列化、读取与写入。 */
public final class CustomItemModelCache {
    /** 动画体属于运行时状态；每次解码都创建独立的空缓存。 */
    public static final Codec<HashMap<ItemDisplayContext, IAnimatable<?>>> CODEC =
            Codec.unit(() -> new HashMap<>(6));

    private CustomItemModelCache() {}

    /** 客户端主线程：读取物品在目标显示场景下的缓存，缺失时返回 null。 */
    @Nullable
    public static IAnimatable<?> get(ItemStack stack, ItemDisplayContext context) {
        var models = stack.get(MMDataComponents.getCUSTOM_ITEM_MODEL());
        return models == null ? null : models.get(context);
    }

    /** 客户端主线程：将动画体写入独立的缓存副本，再存回物品组件。 */
    public static void put(ItemStack stack, ItemDisplayContext context, IAnimatable<?> animatable) {
        stack.set(MMDataComponents.getCUSTOM_ITEM_MODEL(), withModel(
                stack.get(MMDataComponents.getCUSTOM_ITEM_MODEL()), context, animatable));
    }

    /** 返回含目标场景动画体的独立缓存，保留其他场景的条目。 */
    public static HashMap<ItemDisplayContext, IAnimatable<?>> withModel(
            @Nullable Map<ItemDisplayContext, IAnimatable<?>> models,
            ItemDisplayContext context,
            IAnimatable<?> animatable) {
        HashMap<ItemDisplayContext, IAnimatable<?>> result =
                models == null ? new HashMap<>() : new HashMap<>(models);
        result.put(context, animatable);
        return result;
    }
}
