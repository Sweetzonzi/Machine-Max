package io.github.sweetzonzi.machine_max.common.item.prop;

import cn.solarmoon.spark_core.api.SparkLevel;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.util.PPhase;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.objects.PhysicsGhostObject;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import cn.solarmoon.spark_core.animation.IAnimatable;
import io.github.sweetzonzi.machine_max.common.item.ICustomModelItem;
import io.github.sweetzonzi.machine_max.common.registry.MMDataComponents;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.VehicleData;
import io.github.sweetzonzi.machine_max.common.visual.RenderableBoundingBox;
import io.github.sweetzonzi.machine_max.common.visual.VehicleAnimatable;
import io.github.sweetzonzi.machine_max.common.visual.VisualEffectHelper;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import io.github.sweetzonzi.machine_max.external.html.HtNode;
import io.github.sweetzonzi.machine_max.external.html.HtmlLikeParser;
import io.github.sweetzonzi.machine_max.external.html.TagHtNode;
import io.github.sweetzonzi.machine_max.external.html.TextHtNode;
import io.github.sweetzonzi.machine_max.external.style.StyleProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
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

    /** 物品指向的工具提示键 id；无则 {@code null} */
    @Nullable
    protected ResourceLocation getTooltipId(ItemStack stack) {
        return null;
    }

    /**
     * 放置后是否恢复完整状态（进度 / 耐久 / 连接器 / 子系统）。
     * <p>{@code true} = 原样搬运（收纳物品）；{@code false} = 产出骨架（蓝图）。</p>
     */
    protected boolean restoreFullState() {
        return false;
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

    protected VehicleCore createVehicle(Level level, VehicleData vehicleData) {
        return new VehicleCore(level, vehicleData, restoreFullState());
    }

    protected String getNameTranslationKey(ItemStack stack) {
        ResourceLocation path = getPathId(stack);
        if (path != null) return path.toLanguageKey().replace("/", ".");
        VehicleData vehicleData = getVehicleData(stack);
        return vehicleData != null ? vehicleData.getName() : "machine_max:unreadable_blueprint";
    }

    @Nullable
    protected String getTooltipContent(ItemStack stack) {
        ResourceLocation tooltipId = getTooltipId(stack);
        if (tooltipId == null) return null;
        return MMDynamicRes.TOOLTIPS.get(tooltipId) instanceof String content ? content : null;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        if (!level.isClientSide()) {
            ItemStack stack = player.getItemInHand(usedHand);
            try {
                VehicleData vehicleData = getVehicleData(stack);
                if (vehicleData == null) return InteractionResultHolder.fail(stack);

                PlacementContext ctx = calculatePlacement(level, player, vehicleData);

                PhysicsGhostObject testGhost = new PhysicsGhostObject(new BoxCollisionShape(ctx.halfExtents));
                testGhost.setPhysicsLocation(ctx.transform.getTranslation());
                int contact = SparkLevel.getPhysicsLevel(level).getWorld().getWorldSnapshot().contactTest(testGhost, null);

                if (contact == 0) {
                    VehicleCore vehicle = createVehicle(level, vehicleData.withNewUUID(UUID.randomUUID()));
                    var pos = ctx.transform.getTranslation();
                    vehicle.setPos(SparkMathKt.toVec3(pos));
                    ObjectManager.addVehicle(vehicle);
                    onPlaceSuccess(level, ctx, stack, player);
                } else {
                    player.displayClientMessage(Component.translatable("message.machine_max.blueprint.place_failed"), true);
                }
            } catch (Exception e) {
                player.sendSystemMessage(Component.translatable("message.machine_max.vehicle.place_failed", e.getMessage())
                        .withColor(Color.RED.getRGB()));
                return InteractionResultHolder.fail(stack);
            }
        }
        return super.use(level, player, usedHand);
    }

    protected void onPlaceSuccess(Level level, PlacementContext ctx, ItemStack stack, Player player) {
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if (level.isClientSide) {
            try {
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

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        String tip = getTooltipContent(stack);
        if (tip == null) return;

        String[] regexList = {"\r\n", "\n"};
        boolean contains = false;
        for (String regex : regexList) {
            contains = tip.contains(regex);
            if (contains) {
                for (String span : tip.split(regex)) {
                    MutableComponent component = Component.empty();
                    try {
                        List<TextHtNode> nodeQueue = loadTree(HtmlLikeParser.parse(span), new ArrayList<>());
                        for (TextHtNode text : nodeQueue) {
                            MutableComponent newComp = Component.translatable(text.getText());
                            if (text.getEnclosingTags() instanceof List<String> list) {
                                for (String tag : list) {
                                    newComp = StyleProvider.styleFactory(tag, newComp);
                                }
                            }
                            component = component.append(newComp);
                        }
                    } catch (Exception e) {
                        System.err.println("富文本解析失败: " + e.getMessage());
                    }
                    tooltipComponents.add(component);
                }
                break;
            }
        }
        if (!contains) {
            tooltipComponents.add(Component.translatable(tip));
        }
    }

    private static List<TextHtNode> loadTree(HtNode htNode, List<TextHtNode> cache) {
        if (htNode.isText()) {
            TextHtNode textNode = (TextHtNode) htNode;
            cache.add(textNode);
        } else {
            TagHtNode tagNode = (TagHtNode) htNode;
            for (HtNode child : tagNode.getChildren()) {
                loadTree(child, cache);
            }
        }
        return cache;
    }

    protected void cacheAnimatable(ItemStack itemStack, ItemDisplayContext context, IAnimatable<?> animatable) {
        HashMap<ItemDisplayContext, IAnimatable<?>> customModels;
        if (itemStack.has(MMDataComponents.getCUSTOM_ITEM_MODEL()) &&
            !Objects.requireNonNull(itemStack.get(MMDataComponents.getCUSTOM_ITEM_MODEL())).isEmpty()) {
            customModels = itemStack.get(MMDataComponents.getCUSTOM_ITEM_MODEL());
        } else {
            customModels = new HashMap<>();
        }
        if (customModels != null) {
            customModels.put(context, animatable);
            itemStack.set(MMDataComponents.getCUSTOM_ITEM_MODEL(), customModels);
        }
    }

    private PlacementContext calculatePlacement(Level level, Player player, VehicleData vehicleData) {
        Transform transform = new Transform(
                PhysicsHelperKt.toBVector3f(level.clip(new ClipContext(
                        player.getEyePosition(),
                        player.getEyePosition().add(player.getViewVector(1).scale(player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE))),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation()).add(0, -(float) vehicleData.min.y, 0),
                Quaternion.IDENTITY
        );
        Vec3 min = vehicleData.min.add(SparkMathKt.toVec3(transform.getTranslation()));
        Vec3 max = vehicleData.max.add(SparkMathKt.toVec3(transform.getTranslation()));
        com.jme3.math.Vector3f halfExtents = new com.jme3.math.Vector3f((float) (max.x - min.x), (float) (max.y - min.y), (float) (max.z - min.z)).mult(0.5f);
        return new PlacementContext(transform, halfExtents);
    }

    private void updateClientPreview(Level level, Entity entity, VehicleData vehicleData) {
        Transform transform = computePreviewTransform(level, entity, vehicleData);
        updateBoundingBoxPreview(level, transform, vehicleData);
        updateVehicleProjection(level, transform, vehicleData);
    }

    private static Transform computePreviewTransform(Level level, Entity entity, VehicleData vehicleData) {
        if (entity instanceof LivingEntity livingEntity) {
            return new Transform(
                    PhysicsHelperKt.toBVector3f(level.clip(new ClipContext(
                            entity.getEyePosition(),
                            entity.getEyePosition().add(entity.getViewVector(1).scale(livingEntity.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE))),
                            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity)).getLocation()).add(0, -(float) vehicleData.min.y, 0),
                    Quaternion.IDENTITY
            );
        } else {
            return new Transform(
                    PhysicsHelperKt.toBVector3f(entity.position()),
                    Quaternion.IDENTITY
            );
        }
    }

    private static void updateBoundingBoxPreview(Level level, Transform transform, VehicleData vehicleData) {
        Vec3 min = vehicleData.min.add(SparkMathKt.toVec3(transform.getTranslation()));
        Vec3 max = vehicleData.max.add(SparkMathKt.toVec3(transform.getTranslation()));
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
        PhysicsLevel physicsLevel = SparkLevel.getPhysicsLevel(level);
        physicsLevel.submitImmediateTask(PPhase.PRE, () -> {
            int contact = physicsLevel.getWorld().contactTest(testGhost, null);
            if (contact > 0) boundingBox.setColor(Color.RED);
            else boundingBox.setColor(Color.GREEN);
            return null;
        });
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
