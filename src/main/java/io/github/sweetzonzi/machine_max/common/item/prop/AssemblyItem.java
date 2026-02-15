package io.github.sweetzonzi.machine_max.common.item.prop;

import cn.solarmoon.spark_core.animation.ItemAnimatable;
import cn.solarmoon.spark_core.api.SparkLevel;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.util.PPhase;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.objects.PhysicsGhostObject;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import io.github.sweetzonzi.machine_max.common.item.ICustomModelItem;
import io.github.sweetzonzi.machine_max.common.registry.MMDataComponents;
import io.github.sweetzonzi.machine_max.common.vehicle.ObjectManager;
import io.github.sweetzonzi.machine_max.common.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.vehicle.data.AssemblyData;
import io.github.sweetzonzi.machine_max.common.vehicle.data.VehicleData;
import io.github.sweetzonzi.machine_max.common.visual.RenderableBoundingBox;
import io.github.sweetzonzi.machine_max.common.visual.VisualEffectHelper;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import io.github.sweetzonzi.machine_max.external.html.HtNode;
import io.github.sweetzonzi.machine_max.external.html.HtmlLikeParser;
import io.github.sweetzonzi.machine_max.external.html.TagHtNode;
import io.github.sweetzonzi.machine_max.external.html.TextHtNode;
import io.github.sweetzonzi.machine_max.external.style.StyleProvider;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
import java.util.List;
import java.util.*;

public class AssemblyItem extends Item implements ICustomModelItem {

    public AssemblyItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        if (!level.isClientSide()) {
            //TODO:检查AABB尺寸位置是否正确，似乎有微妙偏移
            //TODO:检查与地形的碰撞
            ItemStack stack = player.getItemInHand(usedHand);
            try {
                VehicleData vehicleData = getVehicleData(stack);
                if (vehicleData == null) return InteractionResultHolder.fail(stack);
                Transform transform = new Transform(
                        PhysicsHelperKt.toBVector3f(level.clip(new ClipContext(
                                player.getEyePosition(),
                                player.getEyePosition().add(player.getViewVector(1).scale(player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE))),
                                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation()),
                        Quaternion.IDENTITY
                );
                Vec3 min = vehicleData.min.add(SparkMathKt.toVec3(transform.getTranslation()));
                Vec3 max = vehicleData.max.add(SparkMathKt.toVec3(transform.getTranslation()));
                com.jme3.math.Vector3f shape = new com.jme3.math.Vector3f((float) (max.x - min.x), (float) (max.y - min.y), (float) (max.z - min.z)).mult(0.5f);
                PhysicsGhostObject testGhost = new PhysicsGhostObject(new BoxCollisionShape(shape));
                testGhost.setPhysicsLocation(transform.getTranslation());
                PhysicsLevel physicsLevel = SparkLevel.getPhysicsLevel(level);
                physicsLevel.submitDeduplicatedTask(player.getId() + "_try_place_assembly", PPhase.PRE, () -> {
                    int contact = physicsLevel.getWorld().contactTest(testGhost, null);
                    if (contact == 0) {
                        SparkLevel.submitImmediateTask(level, PPhase.PRE, () -> {
                            VehicleCore vehicle = new VehicleCore(level, vehicleData.withNewUUID(UUID.randomUUID()), true);
                            var pos = transform.getTranslation();
                            vehicle.setPos(SparkMathKt.toVec3(pos));
                            ObjectManager.addVehicle(vehicle);
                            if (!player.hasInfiniteMaterials()) VisualEffectHelper.boundingBox = null;
                            stack.consume(1, player);
                            ((ServerLevel) level).sendParticles(ParticleTypes.PORTAL, pos.x, pos.y, pos.z, Math.max((int) shape.length(), 30),
                                    shape.x / 1.5, shape.y / 1.5, shape.z / 1.5, 0.2f);
                        });
                    } else
                        SparkLevel.submitImmediateTask(level, PPhase.PRE, () -> {
                            player.displayClientMessage(Component.translatable("message.machine_max.blueprint.place_failed"), true);
                        });
                    return null;
                });
            } catch (NullPointerException e) {
                return InteractionResultHolder.fail(stack);
            }
        }
        return super.use(level, player, usedHand);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if (level.isClientSide) {
            try {
                VehicleData vehicleData = getVehicleData(stack);
                if (vehicleData == null) return;
                if (isSelected) {
                    Transform transform = entity instanceof LivingEntity livingEntity ?
                            new Transform(
                                    PhysicsHelperKt.toBVector3f(level.clip(new ClipContext(
                                            entity.getEyePosition(),
                                            entity.getEyePosition().add(entity.getViewVector(1).scale(livingEntity.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE))),
                                            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity)).getLocation()),
                                    Quaternion.IDENTITY
                            ) : new Transform(
                            PhysicsHelperKt.toBVector3f(entity.position()),
                            Quaternion.IDENTITY
                    );
                    Vec3 min = vehicleData.min.add(SparkMathKt.toVec3(transform.getTranslation()));
                    Vec3 max = vehicleData.max.add(SparkMathKt.toVec3(transform.getTranslation()));
                    RenderableBoundingBox boundingBox;
                    if (VisualEffectHelper.boundingBox != null) boundingBox = VisualEffectHelper.boundingBox;
                    else {
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
                } else if (entity instanceof LivingEntity livingEntity) {
                    var leftItem = livingEntity.getItemInHand(InteractionHand.MAIN_HAND).getItem();
                    var rightItem = livingEntity.getItemInHand(InteractionHand.OFF_HAND).getItem();
                    if (leftItem instanceof VehicleBlueprintItem
                            || rightItem instanceof VehicleBlueprintItem
                            || leftItem instanceof AssemblyItem
                            || rightItem instanceof AssemblyItem) {
                    } else VisualEffectHelper.boundingBox = null;
                }
            } catch (NullPointerException ignored) {
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
        String itemName;
        ResourceLocation location = stack.get(MMDataComponents.getASSEMBLY_PATH());
        if (location != null) itemName = location.toLanguageKey().replace("/", ".");
        else {
            VehicleData vehicleData = getVehicleData(stack);
            if (vehicleData != null)
                itemName = vehicleData.getName();
            else itemName = "machine_max:unreadable_blueprint";
        }
        return Component.translatable(itemName);
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

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        //物品栏鼠标自定义信息 （正在筹备）
//                        vehicleData.authors
//                        tooltipComponents.add(Component.translatable("tooltip.%s.%s.details".formatted(MOD_ID, MMDynamicRes.getRealName(location.getPath()).replace("/", ".")))); // 支持本地化
        String tip;
        try {
            if (MMDynamicRes.TOOLTIPS.get(getAssemblyData(stack).getTooltip()) instanceof String content) {
                tip = content;
            } else return;
        } catch (NullPointerException e) {
            return;
        }

        String[] regexList = {"\r\n", "\n"}; //扫描不同类型系统的回车符
        boolean contains = false;
        for (String regex : regexList) {
            contains = tip.contains(regex);
            if (contains) { //扫到了就替换成mc形式的回车，并且退出匹配
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
        if (!contains) { //没有任何匹配，全部作为可翻译标题
            tooltipComponents.add(Component.translatable(tip));
        }

    }

    @NotNull
    public static AssemblyData getAssemblyData(ItemStack stack) {
        AssemblyData assemblyData = AssemblyData.DEFAULT;
        if (stack.has(MMDataComponents.getASSEMBLY_PATH())) {
            //从物品Component中获取内容包装配体数据
            assemblyData = MMDynamicRes.ASSEMBLIES.getOrDefault(stack.get(MMDataComponents.getASSEMBLY_PATH()), AssemblyData.DEFAULT);
        } else if (stack.has(MMDataComponents.getBLUEPRINT_DATA())) {
            //从物品Component中获取nbt保存的装配体数据
            assemblyData = stack.getOrDefault(MMDataComponents.getASSEMBLY_DATA(), AssemblyData.DEFAULT);
        }
        return assemblyData;
    }

    @Nullable
    public static VehicleData getVehicleData(ItemStack stack) {
        VehicleData vehicleData = null;
        AssemblyData assemblyData = getAssemblyData(stack);
        if (assemblyData.getTemplate() != AssemblyData.EMPTY) {
            //从物品Component中获取内容包装配模板
            vehicleData = MMDynamicRes.TEMPLATES.get(assemblyData.getTemplate());
        } else if (stack.has(MMDataComponents.getVEHICLE_DATA())) {
            //从物品Component中获取nbt保存的装配模板
            vehicleData = stack.get(MMDataComponents.getVEHICLE_DATA());
        }
        return vehicleData;
    }

    @Override
    public ItemAnimatable createItemAnimatable(ItemStack itemStack, Level level, ItemDisplayContext context) {
        var animatable = new ItemAnimatable(itemStack, level);
        HashMap<ItemDisplayContext, ItemAnimatable> customModels;
        if (itemStack.has(MMDataComponents.getCUSTOM_ITEM_MODEL()) && !Objects.requireNonNull(itemStack.get(MMDataComponents.getCUSTOM_ITEM_MODEL())).isEmpty())
            customModels = itemStack.get(MMDataComponents.getCUSTOM_ITEM_MODEL());
        else customModels = new HashMap<>();
        AssemblyData assemblyData = getAssemblyData(itemStack);//获取物品保存的部件类型
        animatable.getModelController().setModel(VehicleBlueprintItem.ICON_MODEL);
        animatable.getModelController().setTextureLocation(assemblyData.getIcon());
        if (customModels != null) {
            customModels.put(context, animatable);
            itemStack.set(MMDataComponents.getCUSTOM_ITEM_MODEL(), customModels);
        }
        return animatable;
    }
}
