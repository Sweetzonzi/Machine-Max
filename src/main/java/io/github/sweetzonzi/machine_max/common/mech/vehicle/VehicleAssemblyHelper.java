package io.github.sweetzonzi.machine_max.common.mech.vehicle;

import cn.solarmoon.spark_core.animation.model.ModelIndex;
import cn.solarmoon.spark_core.animation.model.origin.OLocator;
import cn.solarmoon.spark_core.animation.model.origin.OModel;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Transform;
import com.mojang.datafixers.util.Pair;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.item.prop.PartAssemblyItem;
import io.github.sweetzonzi.machine_max.common.item.prop.PartItem;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.VariantAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.connector.ConnectorAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.SimpleConnector;
import io.github.sweetzonzi.machine_max.common.recipe.PartFabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.common.visual.VisualEffectHelper;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import io.github.sweetzonzi.machine_max.network.payload.assembly.PartAssemblyRequestPayload;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
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

import java.util.Iterator;
import java.util.Objects;

/**
 * <p>手动零件组装的客户端装配选择状态单例。</p>
 * <p>承载"当前手持部件类型 / 变体 / 部件对外连接点 / 安装角"等选择状态，供渲染预览与放置请求构造使用。</p>
 * <p>状态演化（解析手持物品、自动过滤不匹配的变体 / 连接点、清理失效状态）与放置预览的姿态更新、
 * 瞄准提示统一挂在 {@link ClientTickEvent.Pre} 每 tick 执行。预览模型的创建与替换由渲染器负责，
 * 本类只更新已有模型的位姿。服务端不持有装配状态。</p>
 */
@Getter
@EventBusSubscriber(modid = MachineMax.MOD_ID, value = Dist.CLIENT)
@OnlyIn(Dist.CLIENT)
public class VehicleAssemblyHelper {
    private static final VehicleAssemblyHelper INSTANCE = new VehicleAssemblyHelper();

    /** 客户端装配选择状态的唯一实例（客户端仅有本地玩家参与预览） */
    public static VehicleAssemblyHelper getInstance() {
        return INSTANCE;
    }

    private VehicleAssemblyHelper() {
    }

    /** 手持物品解析出的部件类型 */
    @Nullable
    private PartType partType = null;
    @Nullable
    private Iterator<String> variantIterator = null;
    /** 当前选中的变体 */
    @Nullable
    @Setter
    private String variantName = null;
    @Nullable
    private Iterator<Pair<String, String>> connectorIterator = null;
    /** 当前选中的部件对外连接点（子部件名 + 连接点名） */
    @Nullable
    @Setter
    private Pair<String, String> connectorName = null;
    /** 当前 partType 解析来源的手 */
    @Setter
    private InteractionHand hand = InteractionHand.MAIN_HAND;
    /** 安装角（玩家可自定义，服务端仅归一化到 [0,360)） */
    @Setter
    private float attachRotation = 0f;
    /** 预览用：待安装连接点相对本部件质心的位置 */
    private Vector3f offset = new Vector3f();
    /** 预览用：待安装连接点相对本部件质心的姿态 */
    private Quaternionf quaternion = new Quaternionf();

    /**
     * 客户端每 tick 状态演化：解析手持装配物品 → 更新 partType/hand，清理失效状态，
     * 并在视线对准接口时自动过滤到可用变体。
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        LocalPlayer player = Minecraft.getInstance().player;
        VehicleAssemblyHelper helper = INSTANCE;
        if (player == null) {
            helper.setPartType(null);
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
            helper.setPartType(null);
            return;
        }
        helper.hand = hand;
        PartType newPartType = PartAssemblyItem.partTypeOf(stack, player.level());
        helper.setPartType(newPartType);
        if (newPartType == null) return;
        // 自动过滤：视线对准的接口不接受当前变体时，自动切换到可用变体
        AbstractConnector targetConnector = getClientEmptyConnector();
        if (targetConnector != null && !targetConnector.hasPart() && helper.getConnector() != null
                && helper.getVariantName() != null
                && !targetConnector.conditionCheck(helper.getPartType(), helper.getVariantName())) {
            helper.cycleVariants();
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
        PartType partType = getPartType();
        if (partType == null) return;
        VariantAttr variantAttr = getVariant();
        String variantName = getVariantName();
        if (variantAttr == null || variantName == null) return;
        ConnectorAttr connectorAttr = getConnector();
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
                if (targetConnector.conditionCheck(partType, variantName)) {
                    if (targetConnector instanceof SimpleConnector || connectorAttr.isSimpleConnector()) {
                        message.append("目标接口:" + Component.translatable(targetConnector.name).getString() + " 部件接口:"
                                + Component.translatable(getConnectorName().getFirst()).getString() + "-"
                                + Component.translatable(getConnectorName().getSecond()).getString());
                        if (!variantName.equals("default") && partType.variants.size() > 1)
                            message.append(" 部件变体类型:" + Component.translatable(variantName).getString());
                        if (VisualEffectHelper.partToPlace != null) {
                            // 服务端 adjustTransform 以“待安装连接点所属 SubPart”为绝对锚点，预览需保持一致
                            VisualEffectHelper.partToPlace.updateTransform(
                                    targetConnector.mergeTransform(
                                            targetConnector.calculateExtraTransform(
                                                    connectorAttr.getDirection(),
                                                    PhysicsHelperKt.toBVector3f(getOffset()),
                                                    SparkMathKt.toBQuaternion(getQuaternion()),
                                                    getAttachRotation()).invert()
                                    ),
                                    getConnectorName().getFirst()
                            );
                        }
                    } else {
                        message.append("无法连接两个高级连接点");
                    }
                } else {
                    message = Component.empty().append(" 连接点 " + Component.translatable(targetConnector.name).getString()
                            + " 不接受部件 " + Component.translatable(partType.getRegistryKey().toLanguageKey()).getString()
                            + " 的 " + Component.translatable(variantName).getString() + " 变体");
                }
            } else {
                message.append("未选中可用的部件接口，右键将直接放置零件");
                if (VisualEffectHelper.partToPlace != null) {
                    Quaternionf rotation = new Quaternionf().rotateY((float) Math.toRadians(getAttachRotation() - player.getYRot()));
                    VisualEffectHelper.partToPlace.updateTransform(new Transform(
                            PhysicsHelperKt.toBVector3f(player.level().clip(new ClipContext(
                                    player.getEyePosition(),
                                    player.getEyePosition().add(player.getViewVector(1).scale(player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE))),
                                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation()),
                            SparkMathKt.toBQuaternion(rotation)
                    ));
                }
            }
        }
        player.displayClientMessage(message, true);
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
        PartType partType = getPartType();
        if (partType == null) return false;
        Part targetPart = targetSubPart.part;
        if (!targetPart.type.getRegistryKey().equals(partType.getRegistryKey())) return false;
        if (!Objects.equals(getVariantName(), targetPart.variantName)) return false;
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
    public void setPartType(@Nullable PartType newPartType) {
        if (newPartType != null) {
            if (this.partType == null || !this.partType.equals(newPartType)) {
                this.partType = newPartType;
                this.variantIterator = null;
                this.variantName = null;
                this.connectorIterator = null;
                this.connectorName = null;
                getNextVariant();
                getNextConnector();
                reCalculateOffset();
            }
        } else {
            this.partType = null;
            this.variantIterator = null;
            this.variantName = null;
            this.connectorIterator = null;
            this.connectorName = null;
            this.offset = new Vector3f();
            this.quaternion = new Quaternionf();
        }
    }

    /**
     * 目标接口是否接受给定变体（公共纯逻辑，等价于 {@link AbstractConnector#conditionCheck}）。
     */
    public static boolean connectorMatches(AbstractConnector target, PartType type, String variant) {
        return target.conditionCheck(type, variant);
    }

    /**
     * 部件对外连接点是否可与目标接口配对（简单接口 + 双方条件校验）。
     */
    public static boolean partConnectorMatches(ConnectorAttr attr, AbstractConnector target, PartType type, String variant) {
        if (!(target instanceof SimpleConnector || attr.isSimpleConnector())) return false;
        return target.conditionCheck(type, variant) && attr.conditionCheck(type, variant);
    }

    /**
     * 本地旋转安装角（+/- 90°），归一化到 [0,360)。
     *
     * @param add 增加还是减少安装角
     */
    public void cycleAttachAngle(boolean add) {
        float next = (this.attachRotation + (add ? 90f : -90f)) % 360f;
        if (next < 0) next += 360f;
        this.attachRotation = next;
    }

    /**
     * 循环选择部件对外连接点，直到找到与视线目标接口配对成功的连接点或达到迭代次数上限。
     * <p>未瞄准可用接口时仅轮换到下一个连接点。</p>
     */
    public void cycleConnectors() {
        PartType partType = getPartType();
        VariantAttr variantAttr = getVariant();
        if (partType == null || variantAttr == null) return;
        AbstractConnector targetConnector = getClientEmptyConnector();
        if (targetConnector == null || targetConnector.hasPart()) {
            // 未瞄准可用接口：直接轮换到下一个对外连接点
            getNextConnector();
            reCalculateOffset();
            return;
        }
        String variantName = getVariantName();
        int i = variantAttr.getPartOutwardConnectors().size();
        while (i > 0) {
            ConnectorAttr connectorAttr = getNextConnector();
            if (connectorAttr == null) break;
            if (partConnectorMatches(connectorAttr, targetConnector, partType, variantName)) break;
            i--;
        }
        reCalculateOffset();
    }

    /**
     * 循环选择变体，直到找到目标接口可接受的变体或达到迭代次数上限，随后再寻找可行的连接点。
     */
    public void cycleVariants() {
        PartType partType = getPartType();
        if (partType == null) return;
        AbstractConnector targetConnector = getClientEmptyConnector();
        int i = partType.getVariants().size();
        boolean matched = false;
        while (i >= 0) {
            getNextVariant();
            String variantName = getVariantName();
            if (targetConnector == null || targetConnector.hasPart()
                    || targetConnector.conditionCheck(partType, variantName)) {
                matched = true;
                break;
            }
            i--;
        }
        if (matched) cycleConnectors();
        else reCalculateOffset();
    }

    @Nullable
    public VariantAttr getVariant() {
        PartType partType = getPartType();
        if (partType == null || variantName == null) return null;
        return partType.getVariant(variantName);
    }

    @Nullable
    public VariantAttr getNextVariant() {
        PartType partType = getPartType();
        if (partType == null) return null;
        // 若当前变体迭代器为空或已遍历完所有变体，则重新从头遍历
        if (variantIterator == null || !variantIterator.hasNext()) this.variantIterator = partType.getVariantIterator();
        this.variantName = variantIterator.next();
        return partType.getVariant(variantName);
    }

    @Nullable
    public ConnectorAttr getConnector() {
        VariantAttr variant = getVariant();
        if (variant == null || connectorName == null) return null;
        return variant.getPartOutwardConnectors().get(connectorName);
    }

    @Nullable
    public ConnectorAttr getNextConnector() {
        VariantAttr variant = getVariant();
        if (variant == null || variant.getPartOutwardConnectors().isEmpty()) {
            this.connectorName = null;
            return null;
        }
        if (connectorIterator == null || !connectorIterator.hasNext())
            this.connectorIterator = variant.getConnectorIterator();
        if (connectorIterator == null) {
            this.connectorName = null;
            return null;
        }
        this.connectorName = connectorIterator.next();
        return variant.getPartOutwardConnectors().get(connectorName);
    }

    /**
     * 从模型 locator 本地重算预览所需的 offset / quaternion（等价于原服务端 attachment 的重算逻辑）。
     */
    public void reCalculateOffset() {
        VariantAttr variant = getVariant();
        ConnectorAttr connector = getConnector();
        if (variant == null || connector == null) {
            this.offset = new Vector3f();
            this.quaternion = new Quaternionf();
            return;
        }
        OModel model = OModel.getOrEmpty(new ModelIndex("part", variant.getModel()));
        OLocator partConnectorLocator = model.getLocators().get(connector.getLocatorName());
        if (partConnectorLocator != null) {
            Transform transform = variant.getSubParts().get(connectorName.getFirst())
                    .getLocatorTransforms().getOrDefault(connector.getLocatorName(), new Transform());
            this.offset = SparkMathKt.toVector3f(transform.getTranslation());
            this.quaternion = SparkMathKt.toQuaternionf(transform.getRotation());
        } else {
            this.offset = new Vector3f();
            this.quaternion = new Quaternionf();
        }
    }

    /**
     * 根据当前本地状态构造放置请求。
     *
     * @param player 本地玩家
     * @param hand   本次使用的手
     * @param stack  本次使用的手持物品
     * @return 放置请求；当前状态不可用（未选中部件 / 与手持物品不一致）时返回 {@code null}
     */
    @Nullable
    public PartAssemblyRequestPayload buildRequest(Player player, InteractionHand hand, ItemStack stack) {
        PartType partType = getPartType();
        if (partType == null || variantName == null) return null;
        // 校验本地状态与本次使用的手持物品一致，避免主/副手或物品切换导致的错位
        PartType usedType = PartAssemblyItem.partTypeOf(stack, player.level());
        if (usedType == null || !usedType.getRegistryKey().equals(partType.getRegistryKey())) return null;
        ConnectorAttr connectorAttr = getConnector();
        boolean attachToTarget = false;
        int targetSubPartId = 0;
        String targetConnectorName = null;
        AbstractConnector targetConnector = getClientEmptyConnector();
        // 与目标接口配对成功才视为"安装到现有部件的连接点"，否则凭空放置
        if (targetConnector != null && !targetConnector.hasPart() && connectorAttr != null
                && targetConnector.conditionCheck(partType, variantName)
                && (targetConnector instanceof SimpleConnector || connectorAttr.isSimpleConnector())) {
            attachToTarget = true;
            targetSubPartId = targetConnector.subPart.getId();
            targetConnectorName = targetConnector.name;
        }
        return new PartAssemblyRequestPayload(
                partType.getRegistryKey(),
                variantName,
                connectorName != null ? connectorName.getFirst() : null,
                connectorName != null ? connectorName.getSecond() : null,
                hand,
                attachToTarget,
                targetSubPartId,
                targetConnectorName,
                attachRotation
        );
    }

    /**
     * 获取本地玩家视线命中的最近可用连接点（客户端本地射线检测）。
     */
    @Nullable
    private static AbstractConnector getClientEmptyConnector() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.hasData(MMAttachments.getENTITY_EYESIGHT())) return null;
        return player.getData(MMAttachments.getENTITY_EYESIGHT()).getEmptyConnector();
    }
}
