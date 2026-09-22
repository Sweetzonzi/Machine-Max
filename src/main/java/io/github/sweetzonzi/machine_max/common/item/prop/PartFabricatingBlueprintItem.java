package io.github.sweetzonzi.machine_max.common.item.prop;

import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Transform;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.PartType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleAssemblyHelper;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.VariantAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.connector.ConnectorAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.SimpleConnector;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.common.visual.VisualEffectHelper;
import io.github.sweetzonzi.machine_max.network.payload.assembly.PartAssemblyRequestPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;

import java.util.List;

/**
 * 零件制造蓝图：代表某个零件配方的蓝图物品。
 *
 * <p>可放置为线框零件、可参与装配，并在选中时提供连接点预览与瞄准提示。显示名、图标与零件标签
 * 都读 {@code machine_max:part_type}；{@code machine_max:recipe_type} 供装配门禁、JEI 子类型与研发界面使用。</p>
 */
public class PartFabricatingBlueprintItem extends FabricatingBlueprintItem implements PartAssemblyItem {

    public PartFabricatingBlueprintItem() {
        super();
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

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int portId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, portId, isSelected);
        if (level.isClientSide() && isSelected && entity instanceof Player player) {
            try {
                var helper = VehicleAssemblyHelper.getInstance();
                var eyesight = entity.getData(MMAttachments.getENTITY_EYESIGHT());
                PartType partType = helper.getPartType();//获取本地装配状态保存的部件类型
                if (partType == null) return;
                VariantAttr variantAttr = helper.getVariant();//获取本地装配状态保存的部件变体属性
                if (variantAttr == null) return;
                String variant = helper.getVariantName();//获取本地装配状态保存的部件变体
                ConnectorAttr connectorAttr = helper.getConnector();
                AbstractConnector targetConnector = eyesight.getEmptyConnector();
                MutableComponent message = Component.empty();
                if (targetConnector != null && connectorAttr != null) {
                    if (targetConnector.conditionCheck(partType, variant)) {
                        if ((targetConnector instanceof SimpleConnector || connectorAttr.isSimpleConnector())) {
                            message.append("目标接口:" + Component.translatable(targetConnector.name).getString() + "部件接口:"
                                    + Component.translatable(helper.getConnectorName().getFirst()).getString() + " "
                                    + Component.translatable(helper.getConnectorName().getSecond()).getString());
                            if (!variant.equals("default") && partType.variants.size() > 1)
                                message.append(" 部件变体类型:" + Component.translatable(variant).getString());
                            if (VisualEffectHelper.partToPlace != null) {
                                // 服务端 adjustTransform 以“待安装连接点所属 SubPart”为绝对锚点，预览需保持一致
                                VisualEffectHelper.partToPlace.updateTransform(
                                        targetConnector.mergeTransform(
                                                targetConnector.calculateExtraTransform(
                                                        connectorAttr.getDirection(),
                                                        PhysicsHelperKt.toBVector3f(helper.getOffset()),
                                                        SparkMathKt.toBQuaternion(helper.getQuaternion()),
                                                        helper.getAttachRotation()).invert()
                                        ),
                                        helper.getConnectorName().getFirst()
                                );
                            }
                        } else message.append("无法连接两个高级连接点");
                    } else {
                        for (String variantName : partType.variants.keySet()) {
                            if (targetConnector.conditionCheck(partType, variantName)) {
                                // 本地演化：直接切换到可用变体，无需服务端往返
                                helper.cycleVariants();
                                return;
                            }
                        }
                        message = Component.empty().append(" 连接点 " + Component.translatable(targetConnector.name).getString()
                                + " 不接受部件 " + Component.translatable(partType.getRegistryKey().toLanguageKey()).getString() + " 的 " + Component.translatable(variant).getString() + " 变体");
                    }
                } else {
                    message.append("未选中可用的部件接口，右键将直接放置零件");
                    if (VisualEffectHelper.partToPlace != null) {
                        LivingEntity livingEntity = (LivingEntity) entity;
                        Quaternionf rotation = new Quaternionf().rotateY((float) Math.toRadians(helper.getAttachRotation() - entity.getYRot()));
                        VisualEffectHelper.partToPlace.updateTransform(
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

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        appendPartTags(stack, context, tooltipComponents, tooltipFlag);
    }
}
