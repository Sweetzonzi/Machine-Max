package io.github.sweetzonzi.machine_max.common.item.prop;

import io.github.sweetzonzi.machine_max.common.mech.part.PartRestoreMode;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.common.item.ICustomModelItem;
import io.github.sweetzonzi.machine_max.common.registry.MMDataComponents;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehiclePlacementService;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleAssemblyHelper;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.VehicleData;
import io.github.sweetzonzi.machine_max.common.visual.RenderableBoundingBox;
import io.github.sweetzonzi.machine_max.common.visual.VehicleAnimatable;
import io.github.sweetzonzi.machine_max.common.visual.VisualEffectHelper;
import io.github.sweetzonzi.machine_max.network.payload.assembly.VehiclePlacementRequestPayload;
import io.github.sweetzonzi.machine_max.datagen.MMLanguageRegistry;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;


public abstract class BaseVehicleItem extends Item implements ICustomModelItem {
    private static final double ANCHOR_SYNC_TOLERANCE_SQUARED = 1.0;
    private static final Map<UUID, Long> LAST_PLACEMENT_REQUEST = new HashMap<>();

    public BaseVehicleItem(Properties properties) {
        super(properties);
    }

    /**
     * 物品指向的内容包模板 id；返回 {@code null} 表示回退到内联 {@code VEHICLE_DATA}。
     * <p>子类只需声明「路径组件 → 注册表」的解析结果。</p>
     */
    @Nullable
    protected ResourceLocation getTemplateId(ItemStack stack) {
        return null;
    }

    /**
     * 物品指向的内容包路径组件 id，用于名称与工具提示解析；无则 {@code null}
     */
    @Nullable
    protected ResourceLocation getPathId(ItemStack stack) {
        return null;
    }

    /**
     * 物品指向的描述页面 id；无则 {@code null}。
     *
     * <p>{@code namespace:tooltips/demo.html} 对应内容包的
     * {@code documents/tooltips/demo.html}，由客户端 {@code AuiTooltipRenderer} 实例化。
     * 页面缺失时物品回退为原版 tooltip。</p>
     */
    @Nullable
    public ResourceLocation getTooltipId(ItemStack stack) {
        return null;
    }

    /**
     * 放置时应用的状态策略；蓝图使用骨架，收纳物品使用完整状态。
     */
    public PartRestoreMode restoreMode() {
        return PartRestoreMode.STRUCTURE_ONLY;
    }

    /**
     * 解析物品携带的载具数据：优先内容包模板，其次内联 {@code VEHICLE_DATA}。
     *
     * @param stack 物品
     * @return 载具数据，两者皆无时为 {@code null}
     */
    @Nullable
    protected VehicleData getVehicleData(ItemStack stack) {
        ResourceLocation templateId = getTemplateId(stack);
        if (templateId != null) {
            return MMDynamicRes.TEMPLATES.get(templateId);
        }
        return stack.get(MMDataComponents.getVEHICLE_DATA());
    }

    public VehicleCore createVehicle(Level level, VehicleData vehicleData) {
        return new VehicleCore(level, vehicleData, restoreMode());
    }

    protected String getNameTranslationKey(ItemStack stack) {
        ResourceLocation path = getPathId(stack);
        if (path != null) return path.toLanguageKey().replace("/", ".");
        VehicleData vehicleData = getVehicleData(stack);
        return vehicleData != null ? vehicleData.getName() : "machine_max:unreadable_blueprint";
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (!level.isClientSide()) return InteractionResultHolder.sidedSuccess(stack, false);

        VehicleData vehicleData = getVehicleData(stack);
        if (vehicleData == null || !SeatSubsystem.canUseItems(player)) return InteractionResultHolder.fail(stack);
        float yaw = VehicleAssemblyHelper.getInstance().getVehiclePlacementYaw();
        VehicleAnimatable preview = VisualEffectHelper.vehicleProjection;
        VehiclePlacementService placement = preview != null && preview.level == level && preview.vehicleData == vehicleData
                ? preview.getPlacement() : new VehiclePlacementService(level, vehicleData);
        BlockHitResult hit = tracePlacement(level, player, placement.boundsAtOrigin(placementRotation(yaw)));
        if (!isPlacementTarget(hit)) return InteractionResultHolder.fail(stack);
        PacketDistributor.sendToServer(new VehiclePlacementRequestPayload(
                usedHand, getTemplateId(stack), hit.getLocation(), yaw));
        return InteractionResultHolder.sidedSuccess(stack, true);
    }

    /**
     * 从指定手重新解析数据并校验放置请求，成功登记后才执行物品处理（服务端主线程）。
     *
     * @param player  发起请求的服务端玩家
     * @param request 客户端提交的手、来源 ID、候选接地锚点和 yaw；所有字段均需服务端复核
     */
    public void handlePlacementRequest(Player player, VehiclePlacementRequestPayload request) {
        if (!SeatSubsystem.canUseItems(player)) return;
        ItemStack stack = player.getItemInHand(request.hand());
        if (stack.isEmpty() || stack.getItem() != this) return;
        long now = player.level().getGameTime();
        Long previous = LAST_PLACEMENT_REQUEST.put(player.getUUID(), now);
        if (previous != null && now - previous < 4L) return;
        if (LAST_PLACEMENT_REQUEST.size() > 1024) {
            LAST_PLACEMENT_REQUEST.entrySet().removeIf(entry -> now - entry.getValue() > 1200L);
        }
        try {
            if (!Float.isFinite(request.yaw()) || request.yaw() % 90f != 0f
                    || !Double.isFinite(request.groundAnchor().x) || !Double.isFinite(request.groundAnchor().y)
                    || !Double.isFinite(request.groundAnchor().z)
                    || !Objects.equals(getTemplateId(stack), request.sourceId())) {
                return;
            }
            VehicleData vehicleData = getVehicleData(stack);
            if (vehicleData == null || !player.level().hasChunkAt(player.blockPosition())
                    || !vehicleData.validate(player.level()).isEmpty()) return;
            Quaternion rotation = placementRotation(request.yaw());
            VehiclePlacementService placement = new VehiclePlacementService(player.level(), vehicleData);
            AABB boundsAtOrigin = placement.boundsAtOrigin(rotation);
            BlockHitResult hit = tracePlacement(player.level(), player, boundsAtOrigin);
            Vec3 groundAnchor = hit.getLocation();
            // 客户端锚点只用于校验瞄准同步误差；实际生成和占位检查共用服务端锚点。
            if (!isPlacementTarget(hit)
                    || groundAnchor.distanceToSqr(request.groundAnchor()) > ANCHOR_SYNC_TOLERANCE_SQUARED) return;
            if (placement.instantiateAndAdd(restoreMode(), groundAnchor, rotation) == null) {
                player.displayClientMessage(MMLanguageRegistry.MESSAGE_BLUEPRINT_PLACE_FAILED.translated(), true);
                return;
            }
            onPlaceSuccess(player.level(), boundsAtOrigin.move(groundAnchor), stack, player);
        } catch (Exception e) {
            player.sendSystemMessage(Component.translatable(
                            MMLanguageRegistry.MESSAGE_VEHICLE_PLACE_FAILED.tag, e.getMessage())
                    .withColor(Color.RED.getRGB()));
        }
    }

    /**
     * 载具登记成功后的物品处理（服务端主线程）。
     *
     * @param level 载具入世的服务端世界
     * @param bounds 已通过校验的载具世界碰撞包围盒
     * @param stack 发起请求的物品
     * @param player 完成放置的玩家
     */
    protected void onPlaceSuccess(Level level, AABB bounds, ItemStack stack, Player player) {
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if (!level.isClientSide || !(entity instanceof Player player) || !SeatSubsystem.canUseItems(player)) return;
        // 副手仅在主手没有载具物品时显示，避免两件物品在同一 tick 内争用预览。
        if (!isSelected && (player.getOffhandItem() != stack || player.getMainHandItem().getItem() instanceof BaseVehicleItem)) return;
        VehicleData vehicleData = getVehicleData(stack);
        if (vehicleData == null) {
            VisualEffectHelper.vehicleProjection = null;
            VisualEffectHelper.boundingBox = null;
            return;
        }
        updateClientPreview(level, player, vehicleData);
    }

    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        return Component.translatable(getNameTranslationKey(stack));
    }

    private static BlockHitResult tracePlacement(Level level, Player player, AABB boundsAtOrigin) {
        double longest = Math.max(boundsAtOrigin.getXsize(), boundsAtOrigin.getZsize());
        double ordinary = player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        double range = Math.max(ordinary, longest * 0.5 + player.getBbWidth() * 0.5 + 2.0);
        Vec3 eye = player.getEyePosition();
        return level.clip(new ClipContext(eye, eye.add(player.getViewVector(1).scale(range)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
    }

    private static boolean isPlacementTarget(BlockHitResult hit) {
        // 上表面取碰撞形状的实际命中高度（含台阶）；墙与底面不寻地，未命中沿用射线终点。
        return hit.getType() == HitResult.Type.MISS || (hit.getDirection() == Direction.UP && !hit.isInside());
    }

    private static Quaternion placementRotation(float yaw) {
        return new Quaternion().fromAngleNormalAxis((float) Math.toRadians(yaw % 360f), Vector3f.UNIT_Y);
    }

    private void updateClientPreview(Level level, Player player, VehicleData vehicleData) {
        VehicleAnimatable preview = VisualEffectHelper.vehicleProjection;
        boolean firstPose = preview == null || preview.level != level || preview.vehicleData != vehicleData;
        if (firstPose) {
            preview = new VehicleAnimatable(level, vehicleData);
            VisualEffectHelper.vehicleProjection = preview;
            VisualEffectHelper.boundingBox = null;
        }
        Quaternion rotation = placementRotation(VehicleAssemblyHelper.getInstance().getVehiclePlacementYaw());
        VehiclePlacementService placement = preview.getPlacement();
        AABB boundsAtOrigin = placement.boundsAtOrigin(rotation);
        BlockHitResult hit = tracePlacement(level, player, boundsAtOrigin);
        Vec3 groundAnchor = hit.getLocation();
        AABB bounds = boundsAtOrigin.move(groundAnchor);
        updateBoundingBoxPreview(bounds, isPlacementTarget(hit) && placement.canPlace(bounds));

        Transform target = new Transform(PhysicsHelperKt.toBVector3f(groundAnchor), rotation);
        // 第一帧直接落位，后续各 tick 都推进插值，不重建子零件或源数据。
        if (firstPose) preview.setTransform(target);
        else preview.updateTransform(target);
    }

    private static void updateBoundingBoxPreview(AABB bounds, boolean canPlace) {
        Vec3 min = bounds.getMinPosition();
        Vec3 max = bounds.getMaxPosition();
        RenderableBoundingBox box = VisualEffectHelper.boundingBox;
        if (box == null) {
            box = new RenderableBoundingBox(min, max);
            VisualEffectHelper.boundingBox = box;
        } else {
            box.updateShape(PhysicsHelperKt.toBVector3f(min), PhysicsHelperKt.toBVector3f(max));
        }
        box.setColor(canPlace ? Color.GREEN : Color.RED);
    }
}
