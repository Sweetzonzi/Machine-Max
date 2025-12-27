package io.github.sweetzonzi.machine_max.common.item.prop;

import cn.solarmoon.spark_core.animation.ItemAnimatable;
import cn.solarmoon.spark_core.animation.model.ModelIndex;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.sound.SpreadingSoundHelper;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Quaternion;
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
import io.github.sweetzonzi.machine_max.common.vehicle.connector.AttachPointConnector;
import io.github.sweetzonzi.machine_max.common.visual.VisualEffectHelper;
import io.github.sweetzonzi.machine_max.network.payload.RegularInputPayload;
import io.github.sweetzonzi.machine_max.util.data.KeyInputMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.checkerframework.checker.units.qual.C;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import java.awt.*;
import java.util.HashMap;
import java.util.Objects;

public class FabricatingBlueprintItem extends Item implements ICustomModelItem, PartAssemblyItem {
    public static final Color COLOR = new Color(150, 200, 255);

    public FabricatingBlueprintItem() {
        super(new Properties());
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
                    part.setMaterialProgress(0);
                    part.setAssemblingProgress(0);
                    if (PartAssemblyItem.getRecipe(stack, level) instanceof FabricatingRecipe) {
                        part.customRecipe = stack.get(MMDataComponents.getRECIPE_TYPE());
                    }
                    return cache.assembly(level, player, stack, part);
                } else return InteractionResultHolder.pass(stack); //TODO:方块拼装和物品拼装？
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
                String variant = cache.getVariantName();//获取物品保存的部件变体
                ConnectorAttr connectorAttr = cache.getConnector();
                AbstractConnector targetConnector = eyesight.getConnector();
                MutableComponent message = Component.empty();
                if (targetConnector != null && connectorAttr != null) {
                    if (targetConnector.conditionCheck(partType, variant)) {
                        if ((targetConnector instanceof AttachPointConnector || connectorAttr.type().equals("AttachPoint"))) {
                            message.append("目标接口:" + targetConnector.name + "部件接口:" + cache.getConnectorName());
                            if (!variant.equals("default") && partType.variants.size() > 1)
                                message.append(" 部件变体类型:" + variant);
                            if (VisualEffectHelper.partToPlace != null) {
                                VisualEffectHelper.partToPlace.setTransform(
                                        targetConnector.mergeTransform(new Transform(
                                                PhysicsHelperKt.toBVector3f(cache.getOffset()),
                                                SparkMathKt.toBQuaternion(cache.getQuaternion())
                                        ).invert())
                                );
                            }
                        } else message.append("无法连接两个高级连接点");
                    } else {
                        for (String variantName : partType.variants.keySet()) {
                            if (targetConnector.conditionCheck(partType, variantName)) {
                                PacketDistributor.sendToServer(new RegularInputPayload(KeyInputMapping.CYCLE_PART_VARIANTS.getValue(), 0));
                                return;
                            }
                        }
                        message = Component.empty().append(" 连接点" + targetConnector.name + "不接受部件" + partType.getRegistryKey() + "的" + variant + "变体");
                    }
                } else {
                    message.append("未选中可用的部件接口，右键将直接放置零件");
                    if (VisualEffectHelper.partToPlace != null) {
                        LivingEntity livingEntity = (LivingEntity) entity;
                        VisualEffectHelper.partToPlace.setTransform(
                                new Transform(
                                        PhysicsHelperKt.toBVector3f(level.clip(new ClipContext(
                                                entity.getEyePosition(),
                                                entity.getEyePosition().add(entity.getViewVector(1).scale(livingEntity.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE))),
                                                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity)).getLocation()),
                                        Quaternion.IDENTITY
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

    /**
     * 根据物品Component中的部件类型修改物品显示的名称
     *
     * @param stack 物品堆
     * @return 翻译键
     */
    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        try {
            ResourceLocation type = stack.get(MMDataComponents.getRECIPE_TYPE());
            return Component.translatable(type.toLanguageKey()).append(Component.translatable("item.machine_max.fabricating_blueprint"));
        } catch (Exception e) {
            return super.getName(stack);
        }
    }


    public ItemAnimatable createItemAnimatable(ItemStack itemStack, Level level, ItemDisplayContext context) {
        var animatable = new ItemAnimatable(itemStack, level);
        PartType partType = PartAssemblyItem.getPartType(itemStack, level);//获取物品保存的部件类型
        HashMap<ItemDisplayContext, ItemAnimatable> customModels;
        if (itemStack.has(MMDataComponents.getCUSTOM_ITEM_MODEL()) && !Objects.requireNonNull(itemStack.get(MMDataComponents.getCUSTOM_ITEM_MODEL())).isEmpty())
            customModels = itemStack.get(MMDataComponents.getCUSTOM_ITEM_MODEL());
        else customModels = new HashMap<>();
        if (context == ItemDisplayContext.GUI && partType != null) {
            animatable.getModelController().setModel(new ModelIndex(
                    "item", ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "item_icon_2d_128x")));
            animatable.getModelController().setTextureLocation(partType.getDefaultIcon());
        } else {
            animatable.getModelController().setModel(new ModelIndex(
                    "item", ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "blueprint")));
            animatable.getModelController().setTextureLocation(
                    ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "textures/item/blueprint.png"));
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

    @Override
    public Color getColor(ItemStack itemStack, Level level, ItemDisplayContext displayContext) {
        return COLOR;
    }
}
