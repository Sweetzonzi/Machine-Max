package io.github.sweetzonzi.machinemax.client.renderer;

import cn.solarmoon.spark_core.animation.ItemAnimatable;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public interface ICustomModelItem {
    /**
     * <p>获取存储有模型、贴图、动画等内容的动画体，用于物品渲染</p>
     * <p>Get an instance of ItemAnimatable that contains the model, texture, and animation content, for item rendering</p>
     *
     * @param itemStack <p>物品堆栈，同类不同物品堆实例可拥有不同的动画体进而拥有不同的模型动画效果</p><p>Item stack, different instances of the same item can have different ItemAnimatable instances to have different model animation effects</p>
     * @param context   <p>此物品被渲染的场景，如GUI、凋落物、第一人称视角等</p><p>The scene where this item is rendered, such as GUI, falling blocks, first-person view</p>
     * @return 动画体对象 ItemAnimatable instance
     */
    ItemAnimatable getRenderInstance(ItemStack itemStack, Level level, ItemDisplayContext context);

    /**
     * <p>是否在特定情况下使用2D模型，例如物品栏、掉落物等场景</p>
     * <p>Whether to use 2D model in specific scenarios, such as inventory, drops</p>
     *
     * @return <p>true: 使用2D模型，false: 不使用2D模型</p><p>true: use 2D model, false: not use 2D model</p>
     */
    default boolean use2dModel(ItemStack itemStack, Level level, ItemDisplayContext displayContext) {
        return true;
    }

    /**
     * <p>用于物品栏、掉落物等场景的2D模型资源位置，仅在 {@link #use2dModel} 返回true时生效</p>
     * <p>ModelResourceLocation used for GUI display, only effective when {@link #use2dModel} returns true</p>
     *
     * @return <p>资源位置，null则返回缺省模型(紫黑格子)</p><p>ModelResourceLocation, null for default model(purple black)</p>
     */
    default ModelResourceLocation get2dModelResourceLocation() {
        return null;
    }
}
