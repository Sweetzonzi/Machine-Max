package io.github.sweetzonzi.machine_max.common.mech.vehicle;

import com.mojang.datafixers.util.Pair;
import io.github.sweetzonzi.machine_max.common.item.prop.PartAssemblyItem;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.VariantAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.connector.ConnectorAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.SimpleConnector;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.network.payload.assembly.PartAssemblyRequestPayload;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Objects;

/** 共通的手动零件装配选择状态与请求构造。 */
@Getter
public class VehicleAssemblyHelper {
    private static final VehicleAssemblyHelper INSTANCE = new VehicleAssemblyHelper();

    @Nullable
    private RegularPartType partType;
    @Nullable
    private Iterator<String> variantIterator;
    @Nullable
    @Setter
    private String variantName;
    @Nullable
    private Iterator<Pair<String, String>> connectorIterator;
    @Nullable
    @Setter
    private Pair<String, String> connectorName;
    @Setter
    private InteractionHand hand = InteractionHand.MAIN_HAND;
    /** 安装角（玩家可自定义，服务端仅归一化到 [0,360)）。 */
    @Setter
    private float attachRotation;

    private VehicleAssemblyHelper() {
    }

    public static VehicleAssemblyHelper getInstance() {
        return INSTANCE;
    }

    /**
     * 更新当前手持部件类型。类型变化时重置并初始化变体与连接点选择。
     *
     * @return 选择状态是否因本次更新而变化
     */
    public boolean setPartType(@Nullable RegularPartType newPartType) {
        if (newPartType != null) {
            if (this.partType != null && this.partType.equals(newPartType)) return false;
            this.partType = newPartType;
            this.variantIterator = null;
            this.variantName = null;
            this.connectorIterator = null;
            this.connectorName = null;
            getNextVariant();
            getNextConnector();
            return true;
        }

        boolean changed = partType != null || variantName != null || connectorName != null;
        this.partType = null;
        this.variantIterator = null;
        this.variantName = null;
        this.connectorIterator = null;
        this.connectorName = null;
        return changed;
    }

    @Nullable
    public VariantAttr getVariant() {
        if (partType == null || variantName == null) return null;
        return partType.getVariant(variantName);
    }

    @Nullable
    public VariantAttr getNextVariant() {
        if (partType == null) return null;
        if (variantIterator == null || !variantIterator.hasNext()) variantIterator = partType.getVariantIterator();
        if (variantIterator == null || !variantIterator.hasNext()) {
            variantName = null;
            return null;
        }
        String nextVariant = variantIterator.next();
        if (!Objects.equals(variantName, nextVariant)) {
            connectorIterator = null;
            connectorName = null;
        }
        variantName = nextVariant;
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
            connectorName = null;
            return null;
        }
        if (connectorIterator == null || !connectorIterator.hasNext()) connectorIterator = variant.getConnectorIterator();
        if (connectorIterator == null || !connectorIterator.hasNext()) {
            connectorName = null;
            return null;
        }
        connectorName = connectorIterator.next();
        return variant.getPartOutwardConnectors().get(connectorName);
    }

    /** 目标接口是否接受给定变体。 */
    public static boolean connectorMatches(AbstractConnector target, RegularPartType type, String variant) {
        return target.conditionCheck(type, variant);
    }

    /** 部件对外连接点是否可与目标接口配对。 */
    public static boolean partConnectorMatches(
            ConnectorAttr attr, AbstractConnector target, RegularPartType type, String variant) {
        if (!(target instanceof SimpleConnector || attr.isSimpleConnector())) return false;
        return target.conditionCheck(type, variant) && attr.conditionCheck(type, variant);
    }

    /** 循环安装角（+/- 90°），归一化到 [0,360)。 */
    public void cycleAttachAngle(boolean add) {
        float next = (attachRotation + (add ? 90f : -90f)) % 360f;
        if (next < 0) next += 360f;
        attachRotation = next;
    }

    /** 循环连接点；若提供目标接口，优先停在兼容连接点。 */
    public void cycleConnectors(@Nullable AbstractConnector targetConnector) {
        VariantAttr variantAttr = getVariant();
        if (partType == null || variantAttr == null) return;
        if (targetConnector == null || targetConnector.hasPart()) {
            getNextConnector();
            return;
        }

        int remaining = variantAttr.getPartOutwardConnectors().size();
        while (remaining-- > 0) {
            ConnectorAttr connectorAttr = getNextConnector();
            if (connectorAttr == null || partConnectorMatches(connectorAttr, targetConnector, partType, variantName)) break;
        }
    }

    /** 循环到目标接口可接受的变体，并在成功时选择兼容连接点。 */
    public void cycleVariants(@Nullable AbstractConnector targetConnector) {
        if (partType == null) return;
        int remaining = partType.getVariants().size() + 1;
        boolean matched = false;
        while (remaining-- > 0) {
            VariantAttr next = getNextVariant();
            if (next == null) break;
            if (targetConnector == null || targetConnector.hasPart()
                    || targetConnector.conditionCheck(partType, variantName)) {
                matched = true;
                break;
            }
        }
        if (matched) cycleConnectors(targetConnector);
    }

    /** 根据当前选择状态构造放置请求；物品类型与当前选择不一致时不构造请求。 */
    @Nullable
    public PartAssemblyRequestPayload buildRequest(Player player, InteractionHand usedHand, ItemStack stack) {
        if (partType == null || variantName == null || hand != usedHand) return null;
        RegularPartType usedType = PartAssemblyItem.partTypeOf(stack, player.level());
        if (usedType == null || !usedType.getRegistryKey().equals(partType.getRegistryKey())) return null;

        ConnectorAttr connectorAttr = getConnector();
        AbstractConnector targetConnector = player.hasData(MMAttachments.getENTITY_EYESIGHT())
                ? player.getData(MMAttachments.getENTITY_EYESIGHT()).getEmptyConnector()
                : null;
        boolean attachToTarget = targetConnector != null && !targetConnector.hasPart() && connectorAttr != null
                && targetConnector.conditionCheck(partType, variantName)
                && (targetConnector instanceof SimpleConnector || connectorAttr.isSimpleConnector());
        int targetSubPartId = attachToTarget ? targetConnector.subPart.getId() : 0;
        String targetConnectorName = attachToTarget ? targetConnector.name : null;

        return new PartAssemblyRequestPayload(
                partType.getRegistryKey(),
                variantName,
                connectorName != null ? connectorName.getFirst() : null,
                connectorName != null ? connectorName.getSecond() : null,
                usedHand,
                attachToTarget,
                targetSubPartId,
                targetConnectorName,
                attachRotation
        );
    }
}
