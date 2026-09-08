package io.github.sweetzonzi.machine_max.common.mech.vehicle;

import cn.solarmoon.spark_core.animation.model.ModelIndex;
import cn.solarmoon.spark_core.animation.model.origin.OLocator;
import cn.solarmoon.spark_core.animation.model.origin.OModel;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Transform;
import com.mojang.datafixers.util.Pair;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.item.prop.PartAssemblyItem;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.VariantAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.connector.ConnectorAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.SimpleConnector;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.network.payload.assembly.PartAssemblyRequestPayload;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Iterator;

/**
 * <p>手动零件组装的客户端装配选择状态单例。</p>
 * <p>承载"当前手持部件类型 / 变体 / 部件对外连接点 / 安装角"等选择状态，供渲染预览与放置请求构造使用。</p>
 * <p>状态演化（解析手持物品、自动过滤不匹配的变体 / 连接点、清理失效状态）挂在
 * {@link ClientTickEvent.Pre}，与渲染器解耦；服务端不再持有装配状态。</p>
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
        PartType newPartType = PartAssemblyItem.getPartType(stack, player.level());
        helper.setPartType(newPartType);
        if (newPartType == null) return;
        // 自动过滤：视线对准的接口不接受当前变体时，自动切换到可用变体
        AbstractConnector targetConnector = getClientEmptyConnector();
        if (targetConnector != null && !targetConnector.hasPart() && helper.getConnector() != null
                && helper.getVariantName() != null
                && !targetConnector.conditionCheck(helper.getPartType(), helper.getVariantName())) {
            helper.cycleVariants();
        }
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
        PartType usedType = PartAssemblyItem.getPartType(stack, player.level());
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
