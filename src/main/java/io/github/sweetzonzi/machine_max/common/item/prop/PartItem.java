package io.github.sweetzonzi.machine_max.common.item.prop;

import cn.solarmoon.spark_core.animation.ItemAnimatable;
import cn.solarmoon.spark_core.animation.model.ModelIndex;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.sound.SpreadingSoundHelper;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Transform;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.item.ICustomModelItem;
import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.common.registry.MMDataComponents;
import io.github.sweetzonzi.machine_max.common.vehicle.Part;
import io.github.sweetzonzi.machine_max.common.vehicle.PartType;
import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.ConnectorAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.VariantAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.SimpleConnector;
import io.github.sweetzonzi.machine_max.common.visual.VisualEffectHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class PartItem extends Item implements ICustomModelItem, PartAssemblyItem {
    public PartItem() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    /**
     * 右键点击物品，尝试将零件放置到世界中或尝试与选择的连接口连接
     *
     * @param level    世界
     * @param player   玩家
     * @param usedHand 玩家使用的手
     * @return 互动结果
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (!level.isClientSide()) {
            if (player.hasData(MMAttachments.getVEHICLE_ASSEMBLY())) {
                var cache = player.getData(MMAttachments.getVEHICLE_ASSEMBLY());
                if (cache.getPartType() != null && cache.getVariantName() != null) {
                    Part part = new Part(cache.getPartType(), cache.getVariantName(), level);
                    RecipeHolder<?> recipeHolder = PartAssemblyItem.getRecipeHolder(stack, level);
                    if (recipeHolder != null && recipeHolder.value() instanceof FabricatingRecipe) {
                        part.customRecipe = stack.get(MMDataComponents.getRECIPE_TYPE()); // 设置配方为物品对应的配方
                    }
                    var result = cache.assembly(level, player, stack, part); // 放出部件
                    if (result.getResult() == InteractionResult.CONSUME) { // 若成功则播放音效
                        stack.consume(1, player);
                        SoundEvent sound = SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "item.part.placed"), 32f);
                        SpreadingSoundHelper.playSpreadingSound(level, sound, SoundSource.PLAYERS, player.getPosition(1), player.getDeltaMovement().scale(20), (float) (1f + 0.2f * (Math.random() - 0.5f)), 1.0f);
                    }
                    return result;
                } else return InteractionResultHolder.pass(stack);
            } else return InteractionResultHolder.pass(stack);
        } else return InteractionResultHolder.success(stack);
    }


    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int portId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, portId, isSelected);
        if (level.isClientSide() && isSelected && entity instanceof Player player) {
            try {
                var cache = player.getData(MMAttachments.getVEHICLE_ASSEMBLY());
                var eyesight = entity.getData(MMAttachments.getENTITY_EYESIGHT());
                PartType partType = cache.getPartType();//获取物品保存的部件类型
                if (partType == null) return;
                VariantAttr variantAttr = cache.getVariant();//获取物品保存的部件变体属性
                if (variantAttr == null) return;
                String variantName = cache.getVariantName();//获取物品保存的部件变体
                ConnectorAttr connectorAttr = cache.getConnector();
                SubPart targetSubPart = eyesight.getSubPart();
                AbstractConnector targetConnector = eyesight.getEmptyConnector();
                MutableComponent message = Component.empty();
                if (targetSubPart != null // 直接填满未组装的蓝图部件进度
                        && targetSubPart.part.type.getRegistryKey() == partType.getRegistryKey()
                        && Objects.equals(variantName, targetSubPart.part.variantName)
                        && targetSubPart.part.getAssemblingProgress() == 0
                        && targetSubPart.part.getMaterialProgress() == 0) {
                    message.append("右键以直接完成" + Component.translatable(targetSubPart.part.name).getString() + "的组装进度");
                    if (VisualEffectHelper.partToPlace != null) {
                        VisualEffectHelper.partToPlace.setTransform(
                                new Transform(
                                        targetSubPart.part.rootSubPart.getPosition(),
                                        targetSubPart.part.rootSubPart.getRotation()
                                )
                        );
                    }
                } else if (targetConnector != null && connectorAttr != null) {
                    if (targetConnector.conditionCheck(partType, variantName)) {
                        if ((targetConnector instanceof SimpleConnector || connectorAttr.isSimpleConnector())) {
                            message.append("目标接口:" + Component.translatable(targetConnector.name).getString() + "部件接口:"
                                    + Component.translatable(cache.getConnectorName().getFirst()).getString() + " "
                                    + Component.translatable(cache.getConnectorName().getSecond()).getString());
                            if (!variantName.equals("default") && partType.variants.size() > 1)
                                message.append(" 部件变体类型:" + Component.translatable(variantName).getString());
                            if (VisualEffectHelper.partToPlace != null) {
                                VisualEffectHelper.partToPlace.setTransform(
                                        targetConnector.mergeTransform(targetConnector.calculateExtraTransform(
                                                connectorAttr.direction(),
                                                PhysicsHelperKt.toBVector3f(cache.getOffset()),
                                                SparkMathKt.toBQuaternion(cache.getQuaternion()),
                                                cache.getAttachRotation()
                                        ).invert())
                                );
                            }
                        } else message.append("无法连接两个高级连接点");
                    } else {
                        message = Component.empty().append(" 连接点" + Component.translatable(targetConnector.name).getString()
                               + "不接受部件" + partType.getRegistryKey() + "的" + Component.translatable(variantName).getString() + "变体");
                    }
                } else {
                    message.append("未选中可用的部件接口，右键将直接放置零件");
                    if (VisualEffectHelper.partToPlace != null) {
                        LivingEntity livingEntity = (LivingEntity) entity;
                        Quaternionf rotation = new Quaternionf().rotateY((float) Math.toRadians(cache.getAttachRotation() - entity.getYRot()));
                        VisualEffectHelper.partToPlace.setTransform(
                                new Transform(
                                        PhysicsHelperKt.toBVector3f(level.clip(new ClipContext(
                                                entity.getEyePosition(),
                                                entity.getEyePosition().add(entity.getViewVector(1).scale(livingEntity.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE))),
                                                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity)).getLocation()),
                                        SparkMathKt.toBQuaternion(rotation)
                                )

                        );
                    }
                }
                player.displayClientMessage(message, true);
            } catch (NullPointerException e) {
                if (entity.tickCount % 100 == 0)
                    MachineMax.LOGGER.error("Invalid data: {}", stack.getDisplayName(), e);
            }
        }
    }

    //TODO:之后用这个改Tooltip，为零件添加各类详细信息
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
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

    public ItemAnimatable createItemAnimatable(ItemStack itemStack, Level level, ItemDisplayContext context) {
        var animatable = new ItemAnimatable(itemStack, level);
        PartType partType = PartAssemblyItem.getPartType(itemStack, level);//获取物品保存的部件类型
        if (partType == null) return animatable;
        String variant = partType.getVariantIterator().next();
        HashMap<ItemDisplayContext, ItemAnimatable> customModels;
        if (itemStack.has(MMDataComponents.getCUSTOM_ITEM_MODEL()) && !Objects.requireNonNull(itemStack.get(MMDataComponents.getCUSTOM_ITEM_MODEL())).isEmpty())
            customModels = itemStack.get(MMDataComponents.getCUSTOM_ITEM_MODEL());
        else customModels = new HashMap<>();
        if (context == ItemDisplayContext.GUI) {
            animatable.getModelController().setModel(new ModelIndex(
                    "item", ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "item_icon_2d_128x")));
            animatable.getModelController().setTextureLocation(partType.getDefaultIcon());
        } else {
            animatable.getModelController().setModel(new ModelIndex("part", partType.getVariant(variant).getModel()));
            animatable.getModelController().setTextureLocation(partType.getVariant(variant).getTextureList().getFirst());
        }
        if (customModels != null) {
            customModels.put(context, animatable);
            itemStack.set(MMDataComponents.getCUSTOM_ITEM_MODEL(), customModels);
        }
        return animatable;
    }

    @Override
    public Vector3f getRenderScale(ItemStack itemStack, Level level, ItemDisplayContext displayContext) {
        if (displayContext == ItemDisplayContext.GUI) return new Vector3f(1);
        else return new Vector3f(0.3f);
    }
}
