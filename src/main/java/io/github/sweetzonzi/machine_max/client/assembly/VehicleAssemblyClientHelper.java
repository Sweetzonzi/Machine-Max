package io.github.sweetzonzi.machine_max.client.assembly;

import cn.solarmoon.spark_core.animation.model.ModelIndex;
import cn.solarmoon.spark_core.animation.model.origin.OLocator;
import cn.solarmoon.spark_core.animation.model.origin.OModel;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Transform;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.item.prop.PartAssemblyItem;
import io.github.sweetzonzi.machine_max.common.item.prop.PartItem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPartType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleAssemblyHelper;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleAssemblyServerHelper;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.VariantAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.connector.ConnectorAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.SimpleConnector;
import io.github.sweetzonzi.machine_max.common.recipe.PartFabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.common.visual.VisualEffectHelper;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Objects;

/**
 * 手动零件装配的客户端输入门禁、预览姿态与瞄准提示。
 */
@Getter
@EventBusSubscriber(modid = MachineMax.MOD_ID, value = Dist.CLIENT)
@OnlyIn(Dist.CLIENT)
public class VehicleAssemblyClientHelper {
    private static final VehicleAssemblyClientHelper INSTANCE = new VehicleAssemblyClientHelper();
    private final VehicleAssemblyHelper state = VehicleAssemblyHelper.getInstance();

    /** 客户端装配选择状态的唯一实例（客户端仅有本地玩家参与预览） */
    public static VehicleAssemblyClientHelper getInstance() {
        return INSTANCE;
    }

    /** 当前手持来源仍有效、且玩家可以操作装配控件。 */
    public boolean canUseAssemblyInput(LocalPlayer player) {
        if (Minecraft.getInstance().screen != null || !SeatSubsystem.canUseItems(player)
                || state.getPartType() == null || state.getVariantName() == null) {
            return false;
        }
        ItemStack stack = player.getItemInHand(state.getHand());
        if (!(stack.getItem() instanceof PartAssemblyItem)) return false;
        RegularPartType heldPartType = PartAssemblyItem.partTypeOf(stack, player.level());
        return heldPartType != null && heldPartType.getRegistryKey().equals(state.getPartType().getRegistryKey());
    }

    private VehicleAssemblyClientHelper() {
    }

    /** 预览用：待安装连接点相对本部件质心的位置 */
    private Vector3f offset = new Vector3f();
    /** 预览用：待安装连接点相对本部件质心的姿态 */
    private Quaternionf quaternion = new Quaternionf();
    private String lastFailedAutoVariantKey;

    /**
     * 客户端每 tick 状态演化：解析手持装配物品 → 更新 partType/hand，清理失效状态，
     * 并在视线对准接口时自动过滤到可用变体。
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        LocalPlayer player = Minecraft.getInstance().player;
        VehicleAssemblyClientHelper helper = INSTANCE;
        if (player == null) {
            helper.resetAutoVariantFailure();
            helper.updateSelection(null, InteractionHand.MAIN_HAND);
            return;
        }
        if (!SeatSubsystem.canUseItems(player)) {
            helper.resetAutoVariantFailure();
            helper.updateSelection(null, InteractionHand.MAIN_HAND);
            return;
        }
        // 主手优先解析可用于组装的物品
        ItemStack stack = null;
        InteractionHand hand = InteractionHand.MAIN_HAND;
        if (player.getMainHandItem().getItem() instanceof PartAssemblyItem) {
            stack = player.getMainHandItem();
        } else if (player.getOffhandItem().getItem() instanceof PartAssemblyItem) {
            stack = player.getOffhandItem();
            hand = InteractionHand.OFF_HAND;
        }
        if (stack == null) {
            helper.resetAutoVariantFailure();
            helper.updateSelection(null, InteractionHand.MAIN_HAND);
            return;
        }
        RegularPartType newPartType = PartAssemblyItem.partTypeOf(stack, player.level());
        helper.updateSelection(newPartType, hand);
        if (newPartType == null) {
            helper.resetAutoVariantFailure();
            return;
        }
        // 自动过滤：视线对准的接口不接受当前变体时，自动切换到可用变体
        AbstractConnector targetConnector = getClientEmptyConnector(player);
        if (targetConnector != null && !targetConnector.hasPart()) {
            if (helper.isCurrentSelectionCompatible(targetConnector)) {
                helper.resetAutoVariantFailure();
            } else {
                RegularPartType partType = helper.state.getPartType();
                if (partType != null) {
                    String autoKey = helper.autoVariantAttemptKey(partType, targetConnector);
                    if (!Objects.equals(helper.lastFailedAutoVariantKey, autoKey)) {
                        helper.state.selectCompatibleCombination(targetConnector);
                        helper.reCalculateOffset();
                        boolean compatible = helper.isCurrentSelectionCompatible(targetConnector);
                        helper.lastFailedAutoVariantKey = compatible ? null : autoKey;
                    }
                }
            }
        } else {
            helper.resetAutoVariantFailure();
        }
        // 预览姿态与瞄准提示：跟随本次解析出的手持物品（主手优先、副手兜底）
        try {
            helper.updatePreview(player, stack);
        } catch (NullPointerException e) {
            // 数据未装载完全（部件名 / 连接点名缺失）时跳过本 tick，避免刷屏
            if (player.tickCount % 100 == 0)
                MachineMax.LOGGER.error("Invalid data: {}", stack.getDisplayName(), e);
        }
    }

    /**
     * 客户端每 tick 更新放置预览姿态与动作栏瞄准提示（主线程）。
     *
     * <p>预览模型的创建与替换由渲染器（{@code PartAssemblyRenderer}）负责，本方法只负责把已有模型
     * 摆到目标处，并给出当次能否放置的提示。三种情形按优先级判定：手持零件物品瞄准同类型未组装部件时
     * 提示可直接补全进度、并把模型对齐到该部件；瞄准可用连接点时对齐到该连接点；否则对齐到视线落点。</p>
     *
     * <p>第一情形是零件物品专属路径——服务端
     * {@link VehicleAssemblyServerHelper#handle} 的蓝图意图判定只对 {@link PartItem} 补全进度，
     * 蓝图与 PDA 走不到该分支。</p>
     *
     * @param player 本地玩家
     * @param stack  本次解析出的手持装配物品
     */
    private void updatePreview(LocalPlayer player, ItemStack stack) {
        RegularPartType partType = state.getPartType();
        if (partType == null) return;
        VariantAttr variantAttr = state.getVariant();
        String variantName = state.getVariantName();
        if (variantAttr == null || variantName == null) return;
        ConnectorAttr connectorAttr = state.getConnector();
        var eyesight = player.getData(MMAttachments.getENTITY_EYESIGHT());
        MutableComponent message = Component.empty();
        SubPart targetSubPart = eyesight.getSubPart();
        if (targetSubPart != null && canFillAssemblyProgress(player.level(), stack, targetSubPart)) {
            message.append("右键以直接完成" + Component.translatable(targetSubPart.part.name).getString() + "的组装进度");
            if (VisualEffectHelper.partToPlace != null) {
                VisualEffectHelper.partToPlace.updateTransform(new Transform(
                        targetSubPart.part.rootSubPart.getPosition(),
                        targetSubPart.part.rootSubPart.getRotation()
                ));
            }
        } else {
            AbstractConnector targetConnector = eyesight.getEmptyConnector();
            if (targetConnector != null && connectorAttr != null) {
                boolean targetAcceptsVariant = targetConnector.conditionCheck(partType, variantName);
                boolean canAttach = !targetConnector.hasPart()
                        && VehicleAssemblyHelper.partConnectorMatches(connectorAttr, targetConnector, partType, variantName);
                if (canAttach) {
                    message.append("目标接口:" + Component.translatable(targetConnector.name).getString() + " 部件接口:"
                            + Component.translatable(state.getConnectorName().getFirst()).getString() + "-"
                            + Component.translatable(state.getConnectorName().getSecond()).getString());
                    if (!variantName.equals("default") && partType.variants.size() > 1)
                        message.append(" 部件变体类型:" + Component.translatable(variantName).getString());
                    if (VisualEffectHelper.partToPlace != null) {
                        // Keep the preview anchor consistent with server-side attachment placement.
                        VisualEffectHelper.partToPlace.updateTransform(
                                targetConnector.mergeTransform(
                                        targetConnector.calculateExtraTransform(
                                                connectorAttr.getDirection(),
                                                PhysicsHelperKt.toBVector3f(getOffset()),
                                                SparkMathKt.toBQuaternion(getQuaternion()),
                                                state.getAttachRotation()).invert()
                                ),
                                state.getConnectorName().getFirst()
                        );
                    }
                } else {
                    if (targetConnector.hasPart()) {
                        message.append("目标接口已占用");
                    } else if (!targetAcceptsVariant) {
                        message = Component.empty().append(" 连接点 " + Component.translatable(targetConnector.name).getString()
                                + " 不接受部件 " + Component.translatable(partType.getRegistryKey().toLanguageKey()).getString()
                                + " 的 " + Component.translatable(variantName).getString() + " 变体");
                    } else if (!(targetConnector instanceof SimpleConnector || connectorAttr.isSimpleConnector())) {
                        message.append("无法连接两个高级连接点");
                    } else {
                        message.append("所选部件连接点与目标接口不兼容");
                    }
                    updateFreePlacementPreview(player);
                }
            } else {
                message.append("未选中可用的部件接口，右键将直接放置零件");
                updateFreePlacementPreview(player);
            }
        }
        player.displayClientMessage(message, true);
    }

    private void updateFreePlacementPreview(LocalPlayer player) {
        if (VisualEffectHelper.partToPlace == null) return;
        Quaternionf rotation = new Quaternionf().rotateY((float) Math.toRadians(state.getAttachRotation() - player.getYRot()));
        VisualEffectHelper.partToPlace.updateTransform(new Transform(
                PhysicsHelperKt.toBVector3f(player.level().clip(new ClipContext(
                        player.getEyePosition(),
                        player.getEyePosition().add(player.getViewVector(1).scale(player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE))),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation()),
                SparkMathKt.toBQuaternion(rotation)
        ));
    }

    /**
     * 手持零件物品能否直接补全目标部件的组装进度。
     *
     * <p>需同时满足：目标部件与手持零件同类型、同变体、尚未开始组装；手持零件自身携带非零进度
     * （由耐久差值反推，成品零件从满耐久起算）；目标部件存在可手动组装的配方。该判据与服务端
     * {@link VehicleAssemblyServerHelper} 的填进度分支一致，避免出现"客户端提示可补全、服务端不补全"
     * 的偏差。</p>
     *
     * @param level          用于查零件配方索引
     * @param stack          手持装配物品
     * @param targetSubPart  视线命中的零件
     * @return 是否可以补全组装进度
     */
    private boolean canFillAssemblyProgress(Level level, ItemStack stack, SubPart targetSubPart) {
        if (!(stack.getItem() instanceof PartItem)) return false;
        RegularPartType partType = state.getPartType();
        if (partType == null) return false;
        if (!(targetSubPart.part instanceof RegularPart targetPart)) return false;
        if (!targetPart.type.getRegistryKey().equals(partType.getRegistryKey())) return false;
        if (!Objects.equals(state.getVariantName(), targetPart.variantName)) return false;
        if (targetPart.getAssemblingProgress() != 0 || targetPart.getMaterialProgress() != 0) return false;
        // 手持零件须携带进度，否则服务端不会走填进度分支
        if (!stack.has(DataComponents.MAX_DAMAGE)) return false;
        int capacity = stack.getMaxDamage();
        if (capacity <= 0 || stack.getDamageValue() >= capacity) return false;
        RecipeHolder<PartFabricatingRecipe> holder = MMDynamicRes.getPartRecipe(level, partType.getRegistryKey());
        return holder != null && !holder.value().getManualAssembleIngredientList().isEmpty();
    }

    /**
     * 设置当前手持部件类型；类型变化时重置变体 / 连接点选择并重算预览姿态。
     *
     * @param newPartType 新的部件类型，{@code null} 表示清空状态
     */
    private void updateSelection(@Nullable RegularPartType newPartType, InteractionHand hand) {
        boolean changed = state.setPartType(newPartType);
        state.setHand(hand);
        if (changed) reCalculateOffset();
    }

    /**
     * 本地旋转安装角（+/- 90°），归一化到 [0,360)。
     *
     * @param add 增加还是减少安装角
     */
    public void cycleAttachAngle(boolean add) {
        state.cycleAttachAngle(add);
    }

    /**
     * 循环选择部件对外连接点，直到找到与视线目标接口配对成功的连接点或达到迭代次数上限。
     * <p>未瞄准可用接口时仅轮换到下一个连接点。</p>
     */
    public void cycleConnectors() {
        state.cycleConnectors(getClientEmptyConnector(Minecraft.getInstance().player));
        resetAutoVariantFailure();
        reCalculateOffset();
    }

    /**
     * 循环选择变体，直到找到目标接口可接受的变体或达到迭代次数上限，随后再寻找可行的连接点。
     */
    public void cycleVariants() {
        LocalPlayer player = Minecraft.getInstance().player;
        AbstractConnector targetConnector = getClientEmptyConnector(player);
        state.cycleVariants(targetConnector);
        reCalculateOffset();
        resetAutoVariantFailure();
    }

    private void resetAutoVariantFailure() {
        lastFailedAutoVariantKey = null;
    }

    private String autoVariantAttemptKey(RegularPartType partType, AbstractConnector targetConnector) {
        SubPart targetSubPart = targetConnector.getSubPart();
        return partType.getRegistryKey() + "/" + targetSubPart.part.uuid + "/"
                + targetSubPart.name + "#" + targetSubPart.getId() + "/" + targetConnector.getName();
    }

    private boolean isCurrentSelectionCompatible(AbstractConnector targetConnector) {
        RegularPartType partType = state.getPartType();
        String variantName = state.getVariantName();
        ConnectorAttr connector = state.getConnector();
        return partType != null && variantName != null && connector != null
                && VehicleAssemblyHelper.partConnectorMatches(connector, targetConnector, partType, variantName);
    }

    /**
     * 从模型 locator 本地重算预览所需的 offset / quaternion（等价于原服务端 attachment 的重算逻辑）。
     */
    public void reCalculateOffset() {
        VariantAttr variant = state.getVariant();
        ConnectorAttr connector = state.getConnector();
        if (variant == null || connector == null) {
            this.offset = new Vector3f();
            this.quaternion = new Quaternionf();
            return;
        }
        OModel model = OModel.getOrEmpty(new ModelIndex("part", variant.getModel()));
        OLocator partConnectorLocator = model.getLocators().get(connector.getLocatorName());
        if (partConnectorLocator != null) {
            Transform transform = variant.getSubParts().get(state.getConnectorName().getFirst())
                    .getLocatorTransforms().getOrDefault(connector.getLocatorName(), new Transform());
            this.offset = SparkMathKt.toVector3f(transform.getTranslation());
            this.quaternion = SparkMathKt.toQuaternionf(transform.getRotation());
        } else {
            this.offset = new Vector3f();
            this.quaternion = new Quaternionf();
        }
    }

    /** 获取客户端玩家视线命中的最近可用连接点。 */
    @Nullable
    private static AbstractConnector getClientEmptyConnector(@Nullable LocalPlayer player) {
        if (player == null || !player.hasData(MMAttachments.getENTITY_EYESIGHT())) return null;
        return player.getData(MMAttachments.getENTITY_EYESIGHT()).getEmptyConnector();
    }
}
