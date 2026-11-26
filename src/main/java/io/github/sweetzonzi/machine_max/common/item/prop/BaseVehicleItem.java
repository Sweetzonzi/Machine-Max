package io.github.sweetzonzi.machine_max.common.item.prop;

import io.github.sweetzonzi.machine_max.common.mech.part.PartRestoreMode;
import cn.solarmoon.spark_core.api.SparkLevel;
import cn.solarmoon.spark_core.physics.body.CollisionGroups;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.physics.level.WorldSnapshot;
import cn.solarmoon.spark_core.util.PPhase;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.objects.PhysicsGhostObject;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import io.github.sweetzonzi.machine_max.common.item.ICustomModelItem;
import io.github.sweetzonzi.machine_max.common.registry.MMDataComponents;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;

public abstract class BaseVehicleItem extends Item implements ICustomModelItem {

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

    /** 物品指向的内容包路径组件 id，用于名称与工具提示解析；无则 {@code null} */
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

    /** 放置时应用的状态策略；蓝图使用骨架，收纳物品使用完整状态。 */
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
        if (vehicleData == null) return InteractionResultHolder.fail(stack);
        float yaw = VehicleAssemblyHelper.getInstance().getVehiclePlacementYaw();
        PlacementContext ctx = calculatePlacement(level, player, vehicleData, yaw);
        PacketDistributor.sendToServer(new VehiclePlacementRequestPayload(
                usedHand, getTemplateId(stack), SparkMathKt.toVec3(ctx.transform.getTranslation()), yaw));
        return InteractionResultHolder.sidedSuccess(stack, true);
    }

    /** 服务端从指定手重新解析数据并校验客户端放置请求，成功登记后才消费物品。 */
    public void handlePlacementRequest(Player player, VehiclePlacementRequestPayload request) {
        ItemStack stack = player.getItemInHand(request.hand());
        try {
            if (!Float.isFinite(request.yaw()) || request.yaw() % 90f != 0f
                    || !Double.isFinite(request.anchor().x) || !Double.isFinite(request.anchor().y)
                    || !Double.isFinite(request.anchor().z)
                    || !Objects.equals(getTemplateId(stack), request.sourceId())) {
                return;
            }
            VehicleData vehicleData = getVehicleData(stack);
            if (vehicleData == null || !player.level().hasChunkAt(player.blockPosition())) return;
            PlacementContext ctx = calculatePlacement(player.level(), player, vehicleData, request.yaw());
            Vec3 serverAnchor = SparkMathKt.toVec3(ctx.transform.getTranslation());
            if (serverAnchor.distanceToSqr(request.anchor()) > 1.0) return;

            PhysicsGhostObject testGhost = new PhysicsGhostObject(new BoxCollisionShape(ctx.halfExtents));
            testGhost.setPhysicsLocation(ctx.transform.getTranslation());
            testGhost.setCollisionGroup(CollisionGroups.PAWN);
            testGhost.setCollideWithGroups(CollisionGroups.PAWN | CollisionGroups.PHYSICS_BODY);
            boolean blocked = hasPlacementCollision(
                    SparkLevel.getPhysicsLevel(player.level()).getWorld().getWorldSnapshot(), testGhost);
            if (blocked) {
                player.displayClientMessage(MMLanguageRegistry.MESSAGE_BLUEPRINT_PLACE_FAILED.translated(), true);
                return;
            }

            VehiclePlacementService.instantiateAndAdd(player.level(), vehicleData, restoreMode(), ctx.transform);
            onPlaceSuccess(player.level(), ctx, stack, player);
        } catch (Exception e) {
            player.sendSystemMessage(Component.translatable(
                    MMLanguageRegistry.MESSAGE_VEHICLE_PLACE_FAILED.tag, e.getMessage())
                    .withColor(Color.RED.getRGB()));
        }
    }

    protected void onPlaceSuccess(Level level, PlacementContext ctx, ItemStack stack, Player player) {
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if (level.isClientSide) {
            try {
                // 座位上禁止使用物品时不重建投影与包围盒：渲染侧同一判据，
                // 这里提前返回只省掉每 tick 的射线与物理接触测试
                if (entity instanceof Player player && !SeatSubsystem.canUseItems(player)) return;
                VehicleData vehicleData = getVehicleData(stack);
                if (vehicleData == null) return;
                if (isSelected) {
                    updateClientPreview(level, entity, vehicleData);
                }
            } catch (NullPointerException ignored) {
            }
        }
    }

    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        return Component.translatable(getNameTranslationKey(stack));
    }

    private PlacementContext calculatePlacement(Level level, Player player, VehicleData vehicleData, float yaw) {
        Transform transform = new Transform(
                PhysicsHelperKt.toBVector3f(level.clip(new ClipContext(
                        player.getEyePosition(),
                        player.getEyePosition().add(player.getViewVector(1).scale(player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE))),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation()).add(0, -(float) vehicleData.min.y, 0),
                new Quaternion().fromAngleNormalAxis((float) Math.toRadians(yaw), com.jme3.math.Vector3f.UNIT_Y)
        );
        Vec3[] bounds = transformedBounds(transform, vehicleData);
        Vec3 min = bounds[0];
        Vec3 max = bounds[1];
        com.jme3.math.Vector3f halfExtents = new com.jme3.math.Vector3f((float) (max.x - min.x), (float) (max.y - min.y), (float) (max.z - min.z)).mult(0.5f);
        return new PlacementContext(transform, halfExtents);
    }

    private void updateClientPreview(Level level, Entity entity, VehicleData vehicleData) {
        Transform transform = computePreviewTransform(level, entity, vehicleData,
                VehicleAssemblyHelper.getInstance().getVehiclePlacementYaw());
        updateBoundingBoxPreview(level, transform, vehicleData);
        updateVehicleProjection(level, transform, vehicleData);
    }

    private static Transform computePreviewTransform(Level level, Entity entity, VehicleData vehicleData, float yaw) {
        if (entity instanceof LivingEntity livingEntity) {
            return new Transform(
                    PhysicsHelperKt.toBVector3f(level.clip(new ClipContext(
                            entity.getEyePosition(),
                            entity.getEyePosition().add(entity.getViewVector(1).scale(livingEntity.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE))),
                            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity)).getLocation()).add(0, -(float) vehicleData.min.y, 0),
                    new Quaternion().fromAngleNormalAxis((float) Math.toRadians(yaw), com.jme3.math.Vector3f.UNIT_Y)
            );
        } else {
            return new Transform(
                    PhysicsHelperKt.toBVector3f(entity.position()),
                    new Quaternion().fromAngleNormalAxis((float) Math.toRadians(yaw), com.jme3.math.Vector3f.UNIT_Y)
            );
        }
    }

    private static void updateBoundingBoxPreview(Level level, Transform transform, VehicleData vehicleData) {
        Vec3[] bounds = transformedBounds(transform, vehicleData);
        Vec3 min = bounds[0];
        Vec3 max = bounds[1];
        RenderableBoundingBox boundingBox;
        if (VisualEffectHelper.boundingBox != null) {
            boundingBox = VisualEffectHelper.boundingBox;
        } else {
            boundingBox = new RenderableBoundingBox(min, max);
            VisualEffectHelper.boundingBox = boundingBox;
        }
        boundingBox.updateShape(PhysicsHelperKt.toBVector3f(min), PhysicsHelperKt.toBVector3f(max));
        PhysicsGhostObject testGhost = new PhysicsGhostObject(new BoxCollisionShape(
                boundingBox.getXExtent(), boundingBox.getYExtent(), boundingBox.getZExtent()));
        testGhost.setPhysicsLocation(transform.getTranslation());
        testGhost.setCollisionGroup(CollisionGroups.PAWN);
        testGhost.setCollideWithGroups(CollisionGroups.PAWN | CollisionGroups.PHYSICS_BODY);
        PhysicsLevel physicsLevel = SparkLevel.getPhysicsLevel(level);
        physicsLevel.submitImmediateTask(PPhase.PRE, () -> {
            boolean blocked = hasPlacementCollision(
                    physicsLevel.getWorld().getWorldSnapshot(), testGhost);
            if (blocked) boundingBox.setColor(Color.RED);
            else boundingBox.setColor(Color.GREEN);
            return null;
        });
    }

    private static boolean hasPlacementCollision(WorldSnapshot snapshot, PhysicsGhostObject ghost) {
        boolean[] blocked = {false};
        snapshot.contactTest(ghost, event -> {
            PhysicsCollisionObject other = event.getObjectA() == ghost
                    ? event.getObjectB() : event.getObjectA();
            Object owner = PhysicsBodyExtensionKt.getOwner(other);
            if (owner instanceof io.github.sweetzonzi.machine_max.common.entity.MMPartEntity) return;
            blocked[0] = true;
        });
        return blocked[0];
    }

    /** 将模板包围盒的八个角点按候选姿态变换，得到预览与动态占位共用的世界 AABB。 */
    private static Vec3[] transformedBounds(Transform transform, VehicleData vehicleData) {
        com.jme3.math.Vector3f min = new com.jme3.math.Vector3f(Float.POSITIVE_INFINITY,
                Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
        com.jme3.math.Vector3f max = new com.jme3.math.Vector3f(Float.NEGATIVE_INFINITY,
                Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);
        com.jme3.math.Vector3f corner = new com.jme3.math.Vector3f();
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 2; z++) {
                    corner.set((float) (x == 0 ? vehicleData.min.x : vehicleData.max.x),
                            (float) (y == 0 ? vehicleData.min.y : vehicleData.max.y),
                            (float) (z == 0 ? vehicleData.min.z : vehicleData.max.z));
                    transform.getRotation().toRotationMatrix().mult(corner, corner).addLocal(transform.getTranslation());
                    min.set(Math.min(min.x, corner.x), Math.min(min.y, corner.y), Math.min(min.z, corner.z));
                    max.set(Math.max(max.x, corner.x), Math.max(max.y, corner.y), Math.max(max.z, corner.z));
                }
            }
        }
        return new Vec3[]{SparkMathKt.toVec3(min), SparkMathKt.toVec3(max)};
    }

    private static void updateVehicleProjection(Level level, Transform transform, VehicleData vehicleData) {
        if (VisualEffectHelper.vehicleProjection == null
                || VisualEffectHelper.vehicleProjection.vehicleData != vehicleData) {
            VisualEffectHelper.vehicleProjection = new VehicleAnimatable(level, vehicleData);
            VisualEffectHelper.vehicleProjection.setTransform(transform);
        } else {
            VisualEffectHelper.vehicleProjection.updateTransform(transform);
        }
    }

    protected static class PlacementContext {
        public final Transform transform;
        public final com.jme3.math.Vector3f halfExtents;

        public PlacementContext(Transform transform, com.jme3.math.Vector3f halfExtents) {
            this.transform = transform;
            this.halfExtents = halfExtents;
        }
    }
}
