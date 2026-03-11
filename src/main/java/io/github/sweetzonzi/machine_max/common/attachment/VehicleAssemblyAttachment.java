package io.github.sweetzonzi.machine_max.common.attachment;

import cn.solarmoon.spark_core.animation.model.ModelIndex;
import cn.solarmoon.spark_core.animation.model.origin.OBone;
import cn.solarmoon.spark_core.animation.model.origin.OLocator;
import cn.solarmoon.spark_core.animation.model.origin.OModel;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Transform;
import com.mojang.datafixers.util.Pair;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.item.prop.FabricatingBlueprintItem;
import io.github.sweetzonzi.machine_max.common.item.prop.PartAssemblyItem;
import io.github.sweetzonzi.machine_max.common.item.prop.PartItem;
import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.common.vehicle.*;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.ConnectorAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.VariantAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.SimpleConnector;
import io.github.sweetzonzi.machine_max.common.visual.VisualEffectHelper;
import io.github.sweetzonzi.machine_max.network.payload.assembly.PartChangeRecipePayload;
import io.github.sweetzonzi.machine_max.network.payload.assembly.PlayerPartAssemblyCacheSyncPayload;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

@Getter
@EventBusSubscriber(modid = MachineMax.MOD_ID)
public class VehicleAssemblyAttachment {
    public final LivingEntity owner;
    @Nullable
    public PartType partType = null;
    @Nullable
    public Iterator<String> variantIterator = null;
    @Nullable
    @Setter
    private String variantName = null;
    @Nullable
    public Iterator<Pair<String, String>> connectorIterator = null;
    @Nullable
    @Setter
    private Pair<String, String> connectorName = null; // 零件-连接点名称
    @Setter
    private float attachRotation = 0; // 安装角，90°的倍数，受网络包控制在[0~360)之间循环
    @Setter
    private Vector3f offset = new Vector3f(); // 组装预览用
    @Setter
    private Quaternionf quaternion = new Quaternionf(); // 组装预览用

    public VehicleAssemblyAttachment(LivingEntity entity) {
        this.owner = entity;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onTick(EntityTickEvent.Pre event) {
        Level level = event.getEntity().level();
        if (event.getEntity() instanceof Player player && !player.hasData(MMAttachments.getVEHICLE_ASSEMBLY().get())) {
            // 自动为所有玩家初始化载具组装用缓存
            player.setData(MMAttachments.getVEHICLE_ASSEMBLY().get(), new VehicleAssemblyAttachment(player));
        }
        if (event.getEntity() instanceof LivingEntity entity && entity.hasData(MMAttachments.getVEHICLE_ASSEMBLY().get())) {
            VehicleAssemblyAttachment cache = entity.getData(MMAttachments.getVEHICLE_ASSEMBLY().get());
            ItemStack stack = null; // 获取玩家手中可用于组装载具的物品
            if (entity.getMainHandItem().getItem() instanceof PartAssemblyItem) {
                stack = entity.getMainHandItem();
            } else if (entity.getOffhandItem().getItem() instanceof PartAssemblyItem) {
                stack = entity.getOffhandItem();
            }
            if (stack != null) {
                PartType oldPartType = cache.getPartType();
                PartType newPartType = PartAssemblyItem.getPartType(stack, level);
                cache.setPartType(newPartType);
                if (!level.isClientSide() && entity.hasData(MMAttachments.getENTITY_EYESIGHT().get())) {
                    if (oldPartType != newPartType) // 切换了部件类型时重新计算偏移等数据
                        cache.reCalculateOffset();
                    var eyesight = entity.getData(MMAttachments.getENTITY_EYESIGHT());
                    AbstractConnector targetConnector = eyesight.getEmptyConnector();
                    // 服务端额外根据选中的目标连接点自动切换使用的变体和连接点
                    if (targetConnector != null && !targetConnector.hasPart() && cache.getConnector() != null) {
                        if (cache.getVariant() != null && !targetConnector.conditionCheck(cache.getPartType(), cache.getVariantName())) {
                            cache.cycleVariants();
                        }
                    }
                }
            } else cache.setPartType(null);
        }
    }

    /**
     * 以90°为间隔旋转当前部件的安装角，仅应在服务端被主动调用
     * 安装角的旋转轴为连接点的装配法线
     *
     * @param add 增加还是减少安装角
     */
    public void cycleAttachAngle(boolean add) {
        //TODO: 旋转后检查部件重叠状态，若重叠则取消或跳过？之后再说
        this.attachRotation = (this.attachRotation + (add ? 90 : -90)) % 360;
        if (owner instanceof Player player && partType != null && variantName != null && connectorName != null) {
            boolean hasConnector = this.getConnectorName() != null;
            PacketDistributor.sendToPlayer((ServerPlayer) player, new PlayerPartAssemblyCacheSyncPayload(
                    partType.getRegistryKey(),
                    variantName,
                    hasConnector ? getConnectorName().getFirst() : null,
                    hasConnector ? getConnectorName().getSecond() : null,
                    this.attachRotation,
                    this.quaternion,
                    this.offset
            ));
        }
    }

    /**
     * 循环选择所有连接点直到找到合适的连接点或到达迭代次数上限，仅应在服务端被主动调用
     */
    public void cycleConnectors() {
        var eyesight = owner.getData(MMAttachments.getENTITY_EYESIGHT());
        AbstractConnector targetConnector = eyesight.getEmptyConnector();//获取视线看着的部件连接点
        if (targetConnector != null && !targetConnector.hasPart()) {
            PartType partType = this.getPartType();
            if (partType == null) return;
            String variantName = this.getVariantName();
            VariantAttr variantAttr = this.getVariant();
            if (variantAttr == null) return;
            int i = partType.getVariant(variantName).getPartOutwardConnectors().size();//设置最大迭代次数
            while (i > 0) {
                //循环获取下一个端口，直到找到合适的接口或到达迭代次数上限
                ConnectorAttr connectorAttr = this.getNextConnector();//获取下一个部件接口
                if (connectorAttr == null) return;
                if (connectorAttr.isSimpleConnector() || targetConnector instanceof SimpleConnector) {
                    //检查部件Tag是否与目标接口接受的类型匹配
                    if (targetConnector.conditionCheck(partType, variantName) && connectorAttr.conditionCheck(partType, variantName)) {
                        reCalculateOffset();//重新计算渲染用的姿态与偏移
                        break;
                    }
                }
                i--;
            }
            if (owner instanceof Player player && !player.level().isClientSide()) {
                boolean hasConnector = this.getConnectorName() != null;
                PacketDistributor.sendToPlayer((ServerPlayer) player, new PlayerPartAssemblyCacheSyncPayload(
                        partType.getRegistryKey(),
                        variantName,
                        hasConnector ? getConnectorName().getFirst() : null,
                        hasConnector ? getConnectorName().getSecond() : null,
                        this.attachRotation,
                        this.quaternion,
                        this.offset
                ));
            }
        }
    }

    /**
     * 循环选择所有变体直到找到合适的变体或到达迭代次数上限，随后再寻找可行的连接点，仅应在服务端被主动调用
     */
    public void cycleVariants() {
        var eyesight = owner.getData(MMAttachments.getENTITY_EYESIGHT());
        AbstractConnector targetConnector = eyesight.getEmptyConnector();//获取视线看着的部件连接点
        PartType partType = this.getPartType();
        if (partType == null) return;
        int i = partType.variants.size();//设置最大迭代次数
        while (i >= 0) {
            //循环获取下一个部件变体，直到找到合适的部件变体或到达迭代次数上限
            this.getNextVariant();//获取下一个部件变体
            String variantName = this.getVariantName();
            if (targetConnector.conditionCheck(partType, variantName)) {
                this.cycleConnectors(); // 再找到最合适的连接点
                break;
            }
            i--;
        }
    }

    public void cycleRecipe() {
        if (owner instanceof Player player && !player.level().isClientSide()) {
            var eyesight = player.getData(MMAttachments.getENTITY_EYESIGHT());
            SubPart subPart = eyesight.getSubPart();
            if (subPart != null && (player.isCreative() ||
                    (subPart.part.getMaterialProgress() <= 0
                            && subPart.part.getAssemblingProgress() <= 0))) {
                var blueprints = player.getData(MMAttachments.getBLUEPRINT());
                var availableRecipes = blueprints.getAvailablePartRecipeFor(player, subPart.part.getType().getRegistryKey());
                if (availableRecipes == null) return;
                Iterator<RecipeHolder<FabricatingRecipe>> recipeIterator = availableRecipes.iterator();
                // 使用下一个配方
                if (subPart.part.getCustomRecipe() != FabricatingRecipe.EMPTY) {
                    // 首先找到当前使用的配方
                    while (subPart.part.customRecipe != recipeIterator.next().id()) {
                        if (!recipeIterator.hasNext()) { // 若没有找到当前使用的配方，则重置迭代器
                            break;
                        }
                    }
                    if (!recipeIterator.hasNext()) recipeIterator = availableRecipes.iterator();
                } // 未指定配方或为默认配方则直接取用第一个配方
                if (recipeIterator.hasNext()) {
                    ResourceLocation newRecipe = recipeIterator.next().id();
                    if (newRecipe != subPart.part.getCustomRecipe()) {
                        subPart.part.customRecipe = newRecipe;
                        PacketDistributor.sendToPlayersInDimension((ServerLevel) player.level(), new PartChangeRecipePayload(subPart.part.vehicle.getUuid(), subPart.part.getUuid(), newRecipe));
                    }
                }
            }
        }
    }

    public InteractionResultHolder<ItemStack> assembly(
            Level level,
            LivingEntity entity,
            ItemStack stack,
            Part part
    ) {
        ConnectorAttr connector = getConnector();
        if (level.isClientSide() || partType == null || variantName == null) {
            return InteractionResultHolder.pass(stack);
        } else {
            try {
                var eyesight = entity.getData(MMAttachments.getENTITY_EYESIGHT());
                SubPart targetSubPart = eyesight.getSubPart();
                AbstractConnector targetConnector = eyesight.getEmptyConnector();
                // 待安装部件配方产出结果与当前部件类型一致且尚未装配时
                if (targetSubPart != null && targetSubPart.part.type.getRegistryKey() == partType.getRegistryKey()
                        && targetSubPart.part.getAssemblingProgress() == 0
                        && targetSubPart.part.getMaterialProgress() == 0) {
                    // 是已装配的部件则直接填满未组装的蓝图部件进度并更新使用的配方
                    if (Objects.equals(part.variantName, targetSubPart.part.variantName)
                            && part.getMaterialProgress() > 0 && part.getAssemblingProgress() > 0) {
                        targetSubPart.part.setMaterialProgress(part.getMaterialProgress());
                        targetSubPart.part.setAssemblingProgress(part.getAssemblingProgress());
                        targetSubPart.part.customRecipe = part.customRecipe;
                        for (Map.Entry<String, SubPart> entry : targetSubPart.part.subParts.entrySet()) {
                            entry.getValue().setDurability(part.subParts.get(entry.getKey()).getDurability());
                        }
                        if (stack.getItem() instanceof PartItem) {
                            var pos = targetSubPart.getPosition();
                            ((ServerLevel) level).sendParticles(ParticleTypes.PORTAL, pos.x, pos.y, pos.z, 10, 1, 1, 1, 0.01);
                        }
                        return InteractionResultHolder.consume(stack);
                    } else if (entity.isCrouching() // 若玩家蹲下且持有的是蓝图则仅更新配方
                            && entity.getMainHandItem().getItem() instanceof FabricatingBlueprintItem
                            && targetSubPart.part.customRecipe != FabricatingRecipe.EMPTY && !targetSubPart.part.customRecipe.equals(part.customRecipe)) {
                        targetSubPart.part.customRecipe = part.customRecipe;
                        return InteractionResultHolder.consume(stack);
                    }
                }
                // 若有可用的连接点，则尝试将零件连接至接口
                if (connector != null && targetConnector != null && connectorName != null) {
                    if (targetConnector.conditionCheck(partType, variantName)) {//检查变体条件
                        if ((targetConnector instanceof SimpleConnector || connector.isSimpleConnector())) {//检查接口条件
                            VehicleCore vehicleCore = targetConnector.subPart.part.vehicle;//获取目标连接点所属的载具
                            targetConnector.adjustTransform(part.externalConnectors.get(connectorName), attachRotation);
                            vehicleCore.attachConnector(targetConnector, part.externalConnectors.get(connectorName), part);//尝试将新部件连接至接口
                            if (stack.getItem() instanceof PartItem) {
                                var pos = part.rootSubPart.getPosition();
                                ((ServerLevel) level).sendParticles(ParticleTypes.PORTAL, pos.x, pos.y, pos.z, 10, 1, 1, 1, 0.2f);
                            }
                            return InteractionResultHolder.consume(stack);
                        } else return InteractionResultHolder.pass(stack);
                    } else return InteractionResultHolder.pass(stack);
                }
                // 若没有可用的连接点，则尝试直接放置零件
                Quaternionf rotation = new Quaternionf().rotationY((float) Math.toRadians(attachRotation - entity.getYRot()));
                Transform transform = new Transform(
                        PhysicsHelperKt.toBVector3f(level.clip(new ClipContext(
                                entity.getEyePosition(),
                                entity.getEyePosition().add(entity.getViewVector(1).scale(entity.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE))),
                                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity)).getLocation()),
                        SparkMathKt.toBQuaternion(rotation)
                );
                part.setTransform(transform);//设置初始位姿
                ObjectManager.addVehicle(new VehicleCore(level, part));//直接放置零件
                if (stack.getItem() instanceof PartItem) {
                    var pos = transform.getTranslation();
                    ((ServerLevel) level).sendParticles(ParticleTypes.PORTAL, pos.x, pos.y, pos.z, 10, 1, 1, 1, 0.01);
                }
                return InteractionResultHolder.consume(stack);
            } catch (Exception e) {
                MachineMax.LOGGER.error("An error occurred while assembling the part:", e);
                return InteractionResultHolder.fail(stack);
            }
        }
    }

    public void setPartType(@Nullable PartType newPartType) {
        if (newPartType != null) {
            if (this.partType == null || !this.partType.equals(newPartType)) {
                this.partType = newPartType;
                this.variantIterator = null;
                this.connectorIterator = null;
                this.getNextVariant();
                this.getNextConnector();
            }
        } else {
            this.partType = null;
            this.variantIterator = null;
            this.connectorIterator = null;
        }
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
        if (variant == null) return null;
        if (variant.getPartOutwardConnectors().isEmpty()) return null;
        if (connectorIterator == null || !connectorIterator.hasNext())
            this.connectorIterator = variant.getConnectorIterator();
        if (connectorIterator == null) return null;
        this.connectorName = connectorIterator.next();
        return variant.getPartOutwardConnectors().get(connectorName);
    }

    public void reCalculateOffset() {
        if (getVariant() == null || getConnector() == null) {
            this.offset = new Vector3f();
            this.quaternion = new Quaternionf();
        } else {
            OModel model = OModel.getOrEmpty(new ModelIndex("part", getVariant().getModel()));
            OBone startBone = null;
            if (getConnectorName() != null) {
                String startBoneName = getVariant().getSubParts().get(getConnectorName().getFirst()).getStartBone();
                startBone = model.getBone(startBoneName);
            }
            var locators = model.getLocators();
            OLocator partConnectorLocator = locators.get(getConnector().locatorName());
            if (partConnectorLocator != null) {
                Vector3f rotation = partConnectorLocator.getRotation().toVector3f();
                Matrix4f pose = new Matrix4f();
                partConnectorLocator.getBone().applyTransformWithParents(pose, startBone);
                pose.translate(partConnectorLocator.getOffset().toVector3f())
                        .rotate(new Quaternionf().rotationZYX(rotation.z, rotation.y, rotation.x));
                this.offset = pose.getTranslation(new Vector3f());
                this.quaternion = pose.getNormalizedRotation(new Quaternionf());
            } else {
                this.offset = new Vector3f();
                this.quaternion = new Quaternionf();
            }
        }
    }
}
