package io.github.sweetzonzi.machine_max.common.mech.vehicle;

import cn.solarmoon.spark_core.api.SpreadingSoundHelper;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.mojang.datafixers.util.Pair;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.item.prop.PartAssemblyItem;
import io.github.sweetzonzi.machine_max.common.item.prop.PartItem;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableObject;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.VariantAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.connector.ConnectorAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.SimpleConnector;
import io.github.sweetzonzi.machine_max.common.recipe.PartFabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.network.payload.assembly.PartAssemblyRequestPayload;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.Map;
import java.util.Objects;

/**
 * <p>手动零件组装的服务端权威处理器。</p>
 * <p>接收客户端 {@link PartAssemblyRequestPayload} 后，按"手持物品 + 姿态 + 视线目标"完成轻量校验、
 * 蓝图意图判定、部件构造与放置，并处理物品消耗 / 音效 / 粒子等副作用。</p>
 * <p>同时承载原 {@code VehicleAssemblyAttachment} 迁移而来的配方切换与进度还原逻辑。</p>
 */
public final class VehicleAssemblyServerHelper {
    private VehicleAssemblyServerHelper() {
    }

    /**
     * 处理一次零件组装放置请求（主线程）。
     *
     * @param player  发起请求的玩家
     * @param request 客户端上报的放置请求
     */
    public static void handle(Player player, PartAssemblyRequestPayload request) {
        Level level = player.level();
        ItemStack stack = player.getItemInHand(request.hand());
        // 校验1：请求部件类型必须与手持物品解析结果一致（防作弊换物品）
        PartType heldType = PartAssemblyItem.partTypeOf(stack, level);
        if (heldType == null || !heldType.getRegistryKey().equals(request.registryKey())) return;
        PartType partType = PartType.get(level, request.registryKey());
        if (partType == null) return;
        // 校验2：变体必须属于该部件类型
        if (!partType.getVariants().containsKey(request.variant())) return;
        VariantAttr variantAttr = partType.getVariant(request.variant());
        // 校验3：部件对外连接点必须属于该变体的对外连接点
        Pair<String, String> partConnectorKey = null;
        ConnectorAttr partConnectorAttr = null;
        if (request.subPart() != null && request.connector() != null) {
            partConnectorKey = Pair.of(request.subPart(), request.connector());
            partConnectorAttr = variantAttr.getPartOutwardConnectors().get(partConnectorKey);
            if (partConnectorAttr == null) return;
        }
        // 安装角归一化到 [0,360)，不限制为 90° 倍数
        float attachRotation = request.attachRotation() % 360f;
        if (attachRotation < 0) attachRotation += 360f;
        try {
            // 部件构造：复刻原 PartItem.use / FabricatingBlueprintItem.use 的构造流程
            Part part = new Part(partType, request.variant(), level);
            if (stack.getItem() instanceof PartItem) {
                restoreAssemblyStateFromDamage(stack, part);
            } else {
                part.setMaterialProgress(0);
                part.setAssemblingProgress(0);
            }
            // 蓝图意图优先于 attach / place
            if (tryBlueprintIntent(player, level, stack, part)) return;
            if (request.attachToTarget()) {
                attachToTarget(player, level, stack, part, partConnectorKey, partConnectorAttr, request, attachRotation);
            } else {
                placeInAir(player, level, stack, part, attachRotation);
            }
        } catch (Exception e) {
            MachineMax.LOGGER.error("处理零件组装请求时发生错误：", e);
        }
    }

    /**
     * 蓝图意图判定（服务端自行以视线目标为准，不信任客户端请求字段）。
     *
     * @return 是否已按蓝图意图处理（已处理则不再进入放置分支）
     */
    private static boolean tryBlueprintIntent(Player player, Level level, ItemStack stack, Part part) {
        if (!player.hasData(MMAttachments.getENTITY_EYESIGHT())) return false;
        SubPart targetSubPart = player.getData(MMAttachments.getENTITY_EYESIGHT()).getSubPart();
        if (targetSubPart == null) return false;
        Part targetPart = targetSubPart.part;
        // 目标必须是同类型且尚未组装的部件
        if (!targetPart.type.getRegistryKey().equals(part.type.getRegistryKey())) return false;
        if (targetPart.getAssemblingProgress() != 0 || targetPart.getMaterialProgress() != 0) return false;
        // 分支1：手持带进度的 PartItem → 填进度
        if (stack.getItem() instanceof PartItem
                && Objects.equals(part.variantName, targetPart.variantName)
                && part.getMaterialProgress() > 0 && part.getAssemblingProgress() > 0) {
            targetPart.setMaterialProgress(part.getMaterialProgress());
            targetPart.setAssemblingProgress(part.getAssemblingProgress());
            for (Map.Entry<String, SubPart> entry : targetPart.subParts.entrySet()) {
                SubPart source = part.subParts.get(entry.getKey());
                if (source != null) entry.getValue().setDurability(source.getDurability());
            }
            Vector3f pos = targetSubPart.getPosition();
            ((ServerLevel) level).sendParticles(ParticleTypes.PORTAL, pos.x, pos.y, pos.z, 10, 1, 1, 1, 0.01);
            finishPlacement(player, stack, level);
            return true;
        }
        return false;
    }

    /**
     * 安装到目标连接点：仅做占用 + 交互距离 + 条件校验，不做射线复核。
     */
    private static void attachToTarget(Player player, Level level, ItemStack stack, Part part,
                                       @Nullable Pair<String, String> partConnectorKey,
                                       @Nullable ConnectorAttr partConnectorAttr,
                                       PartAssemblyRequestPayload request, float attachRotation) {
        if (partConnectorKey == null || partConnectorAttr == null) return;
        DestroyableObject object = ObjectManager.getDestroyableObject(level, request.targetSubPartId());
        if (!(object instanceof SubPart targetSubPart)) return;
        AbstractConnector targetConnector = targetSubPart.connectors.get(request.targetConnector());
        if (targetConnector == null || targetConnector.hasPart() || targetConnector.body == null) return;
        // 交互距离校验：目标连接点位置到玩家眼睛的距离不超过交互距离
        float maxDistance = (float) player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        Vector3f eyePos = PhysicsHelperKt.toBVector3f(player.getEyePosition());
        if (targetConnector.body.getPhysicsLocation(null).subtract(eyePos).lengthSquared() > maxDistance * maxDistance) return;
        // 条件校验：目标接口接受该变体 + 简单接口配对
        if (!targetConnector.conditionCheck(part.type, part.variantName)) return;
        if (!(targetConnector instanceof SimpleConnector || partConnectorAttr.isSimpleConnector())) return;
        AbstractConnector partConnector = part.externalConnectors.get(partConnectorKey);
        if (partConnector == null) return;
        if (!(targetConnector.subPart.part.assembly instanceof VehicleCore vehicleCore)) return;
        targetConnector.adjustTransform(partConnector, attachRotation);
        vehicleCore.attachConnector(targetConnector, partConnector, part);
        finishPlacement(player, stack, level);
    }

    /**
     * 凭空放置悬浮零件：位置由服务端 {@link Level#clip} 在交互范围内计算，方向随安装角。
     */
    private static void placeInAir(Player player, Level level, ItemStack stack, Part part, float attachRotation) {
        Quaternionf rotation = new Quaternionf().rotationY((float) Math.toRadians(attachRotation - player.getYRot()));
        Transform transform = new Transform(
                PhysicsHelperKt.toBVector3f(level.clip(new ClipContext(
                        player.getEyePosition(),
                        player.getEyePosition().add(player.getViewVector(1).scale(player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE))),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation()),
                SparkMathKt.toBQuaternion(rotation)
        );
        part.setTransform(transform);
        ObjectManager.addVehicle(new VehicleCore(level, part));
        // 粒子反馈只给零件物品：蓝图（含 PDA 路径）成功放置不产生粒子，但音效对所有物品统一（见 finishPlacement）
        if (stack.getItem() instanceof PartItem) {
            Vector3f pos = transform.getTranslation();
            ((ServerLevel) level).sendParticles(ParticleTypes.PORTAL, pos.x, pos.y, pos.z, 10, 1, 1, 1, 0.01);
        }
        finishPlacement(player, stack, level);
    }

    /**
     * 成功放置 / 安装后的副作用：播放放置音效；零件物品额外消耗 1 个。
     *
     * <p>音效与物品消耗是两件事：音效是放置成功的听觉反馈，所有成功路径、所有手持物品（零件物品、零件制造蓝图、PDA）
     * 都播放；消耗只对零件物品生效。粒子反馈另有一套口径——只在零件物品路径发出（见 {@code placeInAir}）。</p>
     */
    private static void finishPlacement(Player player, ItemStack stack, Level level) {
        if (stack.getItem() instanceof PartItem) {
            stack.consume(1, player);
        }
        SoundEvent sound = SoundEvent.createFixedRangeEvent(
                ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "item.part.placed"), 32f);
        SpreadingSoundHelper.playSpreadingSound(level, sound, SoundSource.PLAYERS, player.getPosition(1),
                player.getDeltaMovement().scale(20), (float) (1f + 0.2f * (Math.random() - 0.5f)), 1.0f);
    }

    /**
     * 从物品耐久组件反推材料 / 装配进度（原 {@code PartItem.restoreAssemblyStateFromDamage}）。
     */
    public static void restoreAssemblyStateFromDamage(ItemStack stack, Part part) {
        if (!stack.has(net.minecraft.core.component.DataComponents.MAX_DAMAGE)) return;
        int cap = stack.getMaxDamage();
        if (cap <= 0) return;

        PartFabricatingRecipe recipe = part.getRecipe();
        if (recipe == null) return;
        if (recipe.getManualAssembleIngredientList().isEmpty()) return;

        int gap = Math.clamp(stack.getDamageValue(), 0, cap);
        int provided = Math.clamp(cap - gap, 0, cap);
        part.setMaterialProgress(provided);
        part.setAssemblingProgress((float) provided / cap);
        for (SubPart subPart : part.subParts.values()) {
            subPart.setDurability(subPart.getMaxDurability());
        }
    }
}
