package io.github.sweetzonzi.machine_max.common.item.prop;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.ItemAnimatable;
import cn.solarmoon.spark_core.animation.model.ModelIndex;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.item.ICustomModelItem;
import io.github.sweetzonzi.machine_max.common.registry.MMDataComponents;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.PartType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleAssemblyHelper;
import io.github.sweetzonzi.machine_max.common.visual.PartAnimatable;
import io.github.sweetzonzi.machine_max.network.payload.assembly.PartAssemblyRequestPayload;
import io.github.sweetzonzi.machine_max.util.MMMath;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class PartItem extends Item implements ICustomModelItem, PartAssemblyItem {
    public PartItem() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    /**
     * 右键点击物品，尝试将零件放置到世界中或尝试与选择的连接口连接。
     * <p>客户端基于本地装配选择状态构造请求上报，服务端由 {@code VehicleAssemblyServerHelper} 权威处理。</p>
     *
     * @param level    世界
     * @param player   玩家
     * @param usedHand 玩家使用的手
     * @return 互动结果
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (level.isClientSide()) {
            PartAssemblyRequestPayload request = VehicleAssemblyHelper.getInstance().buildRequest(player, usedHand, stack);
            if (request == null) return InteractionResultHolder.pass(stack);
            PacketDistributor.sendToServer(request);
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    //TODO:之后用这个改Tooltip，为零件添加各类详细信息
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        appendPartTags(stack, context, tooltipComponents, tooltipFlag);
    }

    /**
     * 根据物品Component中的蓝图修改物品显示的名称
     *
     * @param stack 物品堆
     * @return 翻译键
     */
    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        ResourceLocation type = stack.get(MMDataComponents.getPART_TYPE());
        if (type != null) {
            return Component.translatable(type.toLanguageKey());
        } else return super.getName(stack);
    }

    public IAnimatable<?> createItemAnimatable(ItemStack itemStack, Level level, ItemDisplayContext context) {
        // GUI上下文：使用2D图标模型
        if (context == ItemDisplayContext.GUI) {
            var animatable = new ItemAnimatable(itemStack, level);
            PartType partType = PartAssemblyItem.partTypeOf(itemStack, level);//获取物品保存的部件类型
            if (partType == null) return animatable;
            String variant = partType.getVariantIterator().next();
            HashMap<ItemDisplayContext, IAnimatable<?>> customModels;
            if (itemStack.has(MMDataComponents.getCUSTOM_ITEM_MODEL()) && !Objects.requireNonNull(itemStack.get(MMDataComponents.getCUSTOM_ITEM_MODEL())).isEmpty())
                customModels = itemStack.get(MMDataComponents.getCUSTOM_ITEM_MODEL());
            else customModels = new HashMap<>();
            animatable.getModelController().setModel(new ModelIndex(
                    "item", ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "item_icon_2d_128x")));
            animatable.getModelController().setTextureLocation(partType.getDefaultIcon());
            if (customModels != null) {
                customModels.put(context, animatable);
                itemStack.set(MMDataComponents.getCUSTOM_ITEM_MODEL(), customModels);
            }
            return animatable;
        } else {
            // 非GUI上下文（第一人称、第三人称等）：使用PartAnimatable以支持按零件渲染
            PartType partType = PartAssemblyItem.partTypeOf(itemStack, level);
            if (partType == null) {
                // 无法获取部件类型，回退到ItemAnimatable
                return new ItemAnimatable(itemStack, level);
            }
            // 获取默认变体（使用第一个变体）
            String variant = partType.getVariantIterator().next();
            // 创建PartAnimatable，表示整个部件及其所有零件
            PartAnimatable partAnimatable = new PartAnimatable(level, partType, variant);
            // 设置部件的基础变换为单位变换，渲染器会应用偏移、旋转、缩放
            partAnimatable.setTransform(new com.jme3.math.Transform());
            // 缓存到物品组件，遵循现有模式
            HashMap<ItemDisplayContext, IAnimatable<?>> customModels;
            if (itemStack.has(MMDataComponents.getCUSTOM_ITEM_MODEL()) && !Objects.requireNonNull(itemStack.get(MMDataComponents.getCUSTOM_ITEM_MODEL())).isEmpty())
                customModels = itemStack.get(MMDataComponents.getCUSTOM_ITEM_MODEL());
            else customModels = new HashMap<>();
            if (customModels != null) {
                customModels.put(context, partAnimatable);
                itemStack.set(MMDataComponents.getCUSTOM_ITEM_MODEL(), customModels);
            }
            return partAnimatable;
        }
    }

    @Override
    public boolean use2dModel(ItemStack itemStack, Level level, ItemDisplayContext displayContext) {
        // 仅在GUI中使用2D图标模型，其他上下文使用3D零件渲染
        return displayContext == ItemDisplayContext.GUI;
    }

    @Override
    public Vector3f getRenderScale(ItemStack itemStack, Level level, ItemDisplayContext displayContext) {
        if (displayContext == ItemDisplayContext.GUI) return MMMath.ONE;
        else return new Vector3f(0.3f);
    }
}
