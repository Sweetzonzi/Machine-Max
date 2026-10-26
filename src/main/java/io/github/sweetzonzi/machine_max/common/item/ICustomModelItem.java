package io.github.sweetzonzi.machine_max.common.item;

import cn.solarmoon.spark_core.animation.IAnimatable;
import io.github.sweetzonzi.machine_max.util.MMMath;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

import java.awt.*;

public interface ICustomModelItem {
    /**
     * <p>获取存储有模型、贴图、动画等内容的动画体，用于物品渲染</p>
     * <p>GetVehicleVariable an instance of ItemAnimatable that contains the model, texture, and animation content, for item rendering</p>
     *
     * @param itemStack <p>物品堆栈，同类不同物品堆实例可拥有不同的动画体进而拥有不同的模型动画效果</p><p>Item stack, different instances of the same item can have different ItemAnimatable instances to have different model animation effects</p>
     * @param context   <p>此物品被渲染的场景，如GUI、凋落物、第一人称视角等</p><p>The scene where this item is rendered, such as GUI, falling blocks, first-person view</p>
     * @return 动画体对象 ItemAnimatable instance
     */
    default IAnimatable<?> getRenderInstance(ItemStack itemStack, Level level, ItemDisplayContext context){
        try {
            IAnimatable<?> animatable = CustomItemModelCache.get(itemStack, context);
            if (animatable == null || animatable.getAnimLevel() != level)
                animatable = createItemAnimatable(itemStack, level, context);
            return animatable;
        } catch (Exception e) {
            return null;
        }
    }

    IAnimatable<?> createItemAnimatable(ItemStack itemStack, Level level, ItemDisplayContext context);

    /** 客户端主线程：通过公共缓存写入动画体，物品实现负责选择模型与贴图。 */
    default void cacheAnimatable(ItemStack itemStack, ItemDisplayContext context, IAnimatable<?> animatable) {
        CustomItemModelCache.put(itemStack, context, animatable);
    }

    /**
     * <p>是否在特定情况下使用2D模型，例如物品栏、掉落物等场景</p>
     * <p>Whether to use 2D model in specific scenarios, such as inventory, drops</p>
     *
     * @return <p>true: 使用2D模型，false: 不使用2D模型</p><p>true: use 2D model, false: not use 2D model</p>
     */
    default boolean use2dModel(ItemStack itemStack, Level level, ItemDisplayContext displayContext) {
        return true;
    }

    default Vector3f getRenderOffset(ItemStack itemStack, Level level, ItemDisplayContext displayContext) {
        return MMMath.ZERO;
    }

    default Vector3f getRenderRotation(ItemStack itemStack, Level level, ItemDisplayContext displayContext) {
        return MMMath.ZERO;
    }

    default Vector3f getRenderScale(ItemStack itemStack, Level level, ItemDisplayContext displayContext) {
        return MMMath.ONE;
    }

    default Color getColor(ItemStack itemStack, Level level, ItemDisplayContext displayContext) {
        return Color.WHITE;
    }
}
