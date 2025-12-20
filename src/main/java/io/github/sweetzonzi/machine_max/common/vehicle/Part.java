package io.github.sweetzonzi.machine_max.common.vehicle;

import cn.solarmoon.spark_core.animation.model.origin.OBone;
import cn.solarmoon.spark_core.animation.model.origin.OLocator;
import cn.solarmoon.spark_core.animation.model.origin.OModel;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.util.PPhase;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.bounding.BoundingBox;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.mojang.datafixers.util.Pair;
import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.ConnectorAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.HitBoxAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.SubPartAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.VariantAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.dynamic_attr.AbstractSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.AttachPointConnector;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.SpecialConnector;
import io.github.sweetzonzi.machine_max.common.vehicle.data.PartData;
import io.github.sweetzonzi.machine_max.common.vehicle.data.SubPartData;
import io.github.sweetzonzi.machine_max.common.vehicle.interact.HitBox;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import io.github.sweetzonzi.machine_max.network.payload.assembly.PartAssemblySyncPayload;
import io.github.sweetzonzi.machine_max.util.data.PosRotVelVel;
import jme3utilities.math.MyMath;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.*;

/**
 * <p>组装与UGC创作的最小单元</p>
 */
@Getter
public class Part {
    //常规属性 General attributes
    public volatile VehicleCore vehicle;//所属的VehicleCore
    public String name;
    public final PartType type;
    public final Level level;
    public final String variantName;
    public final VariantAttr variant;
    public final UUID uuid;
    public volatile float assemblingProgress = 0f; //组装进度(0~1)，控制最大耐久和质量
    @Setter
    public int materialProgress = 0; //材料供给进度，控制最大组装进度，上限取决于配方
    public volatile float sharedDurability;//仅在部件内共享耐久度启用时有效，仅用于传递数据，各类实际判断在零件中进行
    public final SubPart rootSubPart;
    public float totalMass;
    public boolean destroyed = false;
    //模块化属性 Modular attributes
    public final Map<String, SubPart> subParts = HashMap.newHashMap(1);
    public final Map<Pair<String, String>, AbstractConnector> externalConnectors = HashMap.newHashMap(1);
    public final Map<Pair<String, String>, AbstractConnector> allConnectors = HashMap.newHashMap(1);

    /**
     * <p>创建新部件，使用指定变体</p>
     * <p>仅应在服务端新建部件时使用</p>
     *
     * @param partType    部件类型
     * @param variantName 部件变体类型
     * @param level       部件被加入的世界
     */
    public Part(PartType partType, @Nullable String variantName, Level level) {
        if (variantName == null) variantName = "default";
        this.name = partType.getName();
        this.type = partType;
        this.variantName = variantName;
        this.variant = partType.getVariants().get(variantName);
        this.level = level;
        this.uuid = UUID.randomUUID();
        this.sharedDurability = getSharedMaxDurability();
        this.rootSubPart = createSubParts(variant.subParts());//创建子部件并指定根子部件
        updateMass();
    }

    /**
     * <p>创建新部件，使用默认变体</p>
     * <p>仅应在服务端新建部件时使用</p>
     *
     * @param partType 部件类型
     * @param level    部件被加入的世界
     */
    public Part(PartType partType, Level level) {
        this(partType, "default", level);
    }


    /**
     * <p>从保存或网络传输的数据中重建部件</p>
     *
     * @param data               保存或网络传输的数据
     * @param level              部件所在的世界
     * @param readAdditionalData 是否从保存的数据中读取额外数据，否则使用默认数据
     */
    public Part(PartData data, Level level, boolean readAdditionalData) {
        this.name = data.name;
        this.type = getPartType(level, data.registryKey);
        this.level = level;
        this.variantName = data.variant;
        this.variant = type.getVariants().get(variantName);
        this.uuid = UUID.fromString(data.uuid);
        this.rootSubPart = createSubParts(type.getVariants().get(variantName).subParts());//重建子部件并指定根子部件
        this.assemblingProgress = readAdditionalData ? Math.clamp(data.assemblingProgress, 0f, 1f) : 0f;
        this.materialProgress = readAdditionalData ? Math.max(data.materialAssemblingProgress, 0) : 0;
        this.sharedDurability = readAdditionalData ? Math.min(data.sharedDurability, getSharedMaxDurability()) : getSharedMaxDurability();
        //遍历零件，录入基本数据
        for (Map.Entry<String, SubPart> entry : subParts.entrySet()) {
            String subPartName = entry.getKey();
            SubPart subPart = entry.getValue();
            if (data.subParts.containsKey(subPartName)) {
                SubPartData subPartData = data.subParts.get(subPartName);
                if (level.isClientSide()) subPart.setId(subPartData.id);//仅客户端接收应用服务端发送的id
                PosRotVelVel posRotVelVel = subPartData.posRotVelVel;
                subPart.setPosition(posRotVelVel.position());
                subPart.setRotation(SparkMathKt.toBQuaternion(posRotVelVel.rotation()));
                subPart.setLinearVelocity(posRotVelVel.linearVel());
                subPart.setAngularVelocity(posRotVelVel.angularVel());
                subPart.transform = posRotVelVel.toTransform();
                subPart.oldTransform = posRotVelVel.toTransform();
                subPart.body.setPhysicsLocation(posRotVelVel.position());
                subPart.body.setPhysicsRotation(SparkMathKt.toBQuaternion(posRotVelVel.rotation()));
                subPart.body.setLinearVelocity(posRotVelVel.linearVel());
                subPart.body.setAngularVelocity(posRotVelVel.angularVel());
                PhysicsBodyExtensionKt.stateOf(subPart.body).setTransform(posRotVelVel.toTransform());
                PhysicsBodyExtensionKt.stateOf(subPart.body).setLastTransform(posRotVelVel.toTransform());
            } else {
                subPart.destroy();
            }
        }
        if (readAdditionalData) {
            //加载子系统储存的数据
            for (Map.Entry<String, SubPartData> entry : data.subParts.entrySet()) {
                SubPart subPart = subParts.get(entry.getKey());
                subPart.setDurability(entry.getValue().durability);
                for (Map.Entry<String, CompoundTag> connectorData : entry.getValue().connectorData.entrySet()) {
                    String connectorName = connectorData.getKey();
                    CompoundTag connectorTagData = connectorData.getValue();
                    AbstractConnector connector = subPart.connectors.get(connectorName);
                    connector.loadData(connectorTagData);
                }
                for (Map.Entry<String, CompoundTag> subsystemData : entry.getValue().subsystemData.entrySet()) {
                    String subSystemName = subsystemData.getKey();
                    CompoundTag subsystemTagData = subsystemData.getValue();
                    AbstractSubsystem subsystem = subPart.subsystems.get(subSystemName);
                    subsystem.loadData(subsystemTagData);
                }
            }
        }
        updateMass();//更新部件总质量
    }

    public PartType getPartType(Level level, ResourceLocation registryKey) {
        PartType pt;
        if (level.isClientSide) pt = MMDynamicRes.PART_TYPES.get(registryKey);
        else pt = MMDynamicRes.SERVER_PART_TYPES.get(registryKey);
        if (pt == null)
            throw new NullPointerException("部件类型" + registryKey + "不存在，请检查数据。可用部件列表: " + (level.isClientSide() ? MMDynamicRes.PART_TYPES.keySet() : MMDynamicRes.SERVER_PART_TYPES.keySet()));
        return pt;
    }

    public void onTick() {
        boolean shouldDestroy = true;
        for (SubPart subPart : subParts.values()) {
            if (subPart.getDestroyTime() > 0) shouldDestroy = false;
            break;
        }
        if (shouldDestroy) this.destroyed = true;
    }

    public void onPrePhysicsTick() {
    }

    public void onPostPhysicsTick() {
    }

    /**
     * <p>获取部件所有零件持有的子系统</p>
     * <p>Gets all subsystems held by all parts</p>
     *
     * @return 所有子系统的不重复集合 Set of all subsystems
     */
    public Set<AbstractSubsystem> getAllSubsystems() {
        HashSet<AbstractSubsystem> subsystems = new HashSet<>();
        for (SubPart subPart : subParts.values()) {
            subsystems.addAll(subPart.subsystems.values());
        }
        return subsystems;
    }

    public float getSharedMaxDurability() {
        float result = 0;
        for (SubPart subPart : subParts.values()) {
            result += subPart.getSharedMaxDurability();
        }
        return result;
    }

    private void createSubsystems(
            SubPart subPart,
            Map<String, AbstractSubsystemAttr> subSystemAttrMap
    ) {
        for (Map.Entry<String, AbstractSubsystemAttr> entry : subSystemAttrMap.entrySet()) {
            String name = entry.getKey();
            AbstractSubsystemAttr attr = entry.getValue();
            AbstractSubsystem subsystem = attr.createSubsystem(subPart, name);
            subPart.subsystems.put(name, subsystem);//部件内的子系统
        }
    }

    private void createConnectors(
            SubPart subPart,
            SubPartAttr subPartAttr,
            LinkedHashMap<String, OLocator> locators
    ) {
        for (Map.Entry<String, ConnectorAttr> connectorEntry : subPartAttr.connectors.entrySet()) {
            String connectorName = connectorEntry.getKey();
            ConnectorAttr connectorAttr = connectorEntry.getValue();
            if (locators.get(connectorAttr.locatorName()) instanceof OLocator locator) {//若找到了对应的零件对接口Locator
                org.joml.Vector3f rotation = locator.getRotation().toVector3f();
                Transform posRot = new Transform(//对接口的位置与姿态
                        PhysicsHelperKt.toBVector3f(locator.getOffset()).subtract(subPart.massCenterTransform.getTranslation()),
                        SparkMathKt.toBQuaternion(new Quaternionf().rotationZYX(rotation.x, rotation.y, rotation.z)).mult(subPart.massCenterTransform.getRotation().inverse())
                );
                AbstractConnector connector = switch (connectorAttr.type()) {
                    case "AttachPoint" ->//连接点接口
                            new AttachPointConnector(
                                    connectorName,
                                    connectorAttr,
                                    subPart,
                                    posRot
                            );
                    case "Special" ->//6自由度自定义关节接口
                            new SpecialConnector(
                                    connectorName,
                                    connectorAttr,
                                    subPart,
                                    posRot
                            );
                    default ->
                            throw new NullPointerException(Component.translatable("error.machine_max.part.invalid_connector_type", type.name, connectorName, connectorAttr.type()).getString());
                };
                subPart.connectors.put(connectorName, connector);
                this.allConnectors.put(Pair.of(subPart.name, connectorName), connector);
                if (!connector.internal) this.externalConnectors.put(Pair.of(subPart.name, connectorName), connector);
            } else
                throw new NullPointerException(Component.translatable("error.machine_max.part.connector_locator_not_found", type.name, connectorName, connectorAttr.locatorName()).getString());
        }
    }

    /**
     * <p>计算零件三轴投影面积，用于阻力计算以及RCS计算</p>
     * <p>Calculates the projection area of the part in three axes, which is used for force calculation.</p>
     * <p>首选零件属性指定的投影面积，否则使用碰撞体积估算</p>
     * <p>First, the projected area specified in the part attribute is used, otherwise, the estimated volume of the collision shape is used.</p>
     *
     * @param subPart 零件
     * @return 投影面积 projection area (m^2)
     */
    public Vec3 calculateProjectedArea(SubPart subPart) {
        //计算零件三轴投影面积，用于阻力计算
        BoundingBox boundingBox = subPart.collisionShape.boundingBox(new Vector3f(), Quaternion.IDENTITY, null);
        double xArea, yArea, zArea;
        if (subPart.attr.projectedArea.x <= 0)
            xArea = 4 * boundingBox.getYExtent() * boundingBox.getZExtent();//半长相乘，还需乘4才能获得真正的面积
        else xArea = subPart.attr.projectedArea.x;
        if (subPart.attr.projectedArea.y <= 0)
            yArea = 4 * boundingBox.getXExtent() * boundingBox.getZExtent();
        else yArea = subPart.attr.projectedArea.y;
        if (subPart.attr.projectedArea.z <= 0)
            zArea = 4 * boundingBox.getXExtent() * boundingBox.getYExtent();
        else zArea = subPart.attr.projectedArea.z;
        return new Vec3(xArea, yArea, zArea);
    }

    private SubPart createSubParts(Map<String, SubPartAttr> subPartAttrMap) {
        //创建零件
        for (Map.Entry<String, SubPartAttr> subPartEntry : subPartAttrMap.entrySet()) {//遍历部件的零件属性
            String name = subPartEntry.getKey();
            SubPartAttr subPartAttr = subPartEntry.getValue();
            SubPart subPart = new SubPart(name, this, subPartAttr);//创建零件
            //获取模型用于构建碰撞
            OModel model = subPart.getModelController().getOriginModel();
            LinkedHashMap<String, OBone> bones = model.getBones();//从模型获取所有骨骼
            LinkedHashMap<String, OLocator> locators = LinkedHashMap.newLinkedHashMap(0);
            for (OBone bone : bones.values()) locators.putAll(bone.getLocators());//从模型获取所有定位器

            subParts.put(name, subPart);//将零件放入部件的零件表

            subPart.body.setMass(subPartAttr.mass > 0 ? subPartAttr.mass : 20);//设置质量
            subPart.body.setCcdSweptSphereRadius(subPart.collisionShape.maxRadius());//设置CCD半径
            subPart.projectedArea = calculateProjectedArea(subPart);
            //创建零件对接口
            createConnectors(subPart, subPartAttr, locators);
            //创建部件内子系统
            createSubsystems(subPart, subPartAttr.subsystems);//创建子系统，赋予部件实际功能
            //创建命中判定区属性并匹配对应子系统(内部实现)
            for (HitBoxAttr hitBoxAttr : subPart.attr.hitBoxes.values()) {
                subPart.hitBoxes.put(hitBoxAttr.hitBoxName(), new HitBox(subPart, hitBoxAttr));
            }
        }
        //TODO: 连接内部连接器
        //设置默认根零件，取质量最大的
        float maxMass = -100;
        SubPart rootSubPart = null;
        for (SubPart subPart : subParts.values()) {
            if (subPart.body.getMass() > maxMass) {
                maxMass = subPart.body.getMass();
                rootSubPart = subPart;
            }
        }
        return rootSubPart;
    }

    /**
     * <p>每被调用一次，尝试增加组装进度，并消耗材料</p>
     * <p>Attempts to increase assembling progress, and consumes materials.</p>
     *
     * @param container 消耗材料的容器 Material container
     * @param progress  增加的进度 Progress to be increased
     * @return 是否成功改变进度 Whether the progress is successfully changed
     */
    public boolean assemble(Container container, float progress) {
        boolean ignoreMaterial = level.isClientSide();
        if (!level.isClientSide() && container instanceof Inventory inventory) {
            ignoreMaterial = inventory.player.hasInfiniteMaterials();
        }
        FabricatingRecipe recipe = getRecipe();
        // 未找到配方则不改变组装进度
        if (recipe != null) {
            int totalTime = recipe.getProcessingTime();
            float step = progress / totalTime;
            float newProgress = Math.clamp(assemblingProgress + step, 0f, 1f);

            // 计算新的组装进度对应的材料需求
            int totalMaterials = recipe.getIngredientList().size(); // 总材料数量
            int targetMaterialProgress = (int) Math.ceil(newProgress * totalMaterials);

            // 尝试提升材料进度
            if (targetMaterialProgress > materialProgress) {
                if (ignoreMaterial) { // 创造模式无视材料需求
                    materialProgress = Math.clamp(targetMaterialProgress, 0, totalMaterials);
                } else { // 检查材料是否足够，如果不够则组装进度最多提升至材料供给进度的值
                    materialProgress = Math.clamp(materialProgress, 0, totalMaterials);
                    for (int i = materialProgress; i < targetMaterialProgress; i++) {
                        // 获取下一个需要消耗的材料
                        if (i < totalMaterials) {
                            Ingredient requiredIngredient = recipe.getIngredientList().get(i);

                            // 在容器中查找匹配的物品
                            boolean found = false;
                            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                                ItemStack stack = container.getItem(slot);
                                if (!stack.isEmpty() && requiredIngredient.test(stack)) {
                                    // 消耗一个物品
                                    stack.shrink(1);
                                    if (stack.isEmpty()) {
                                        container.setItem(slot, ItemStack.EMPTY);
                                    }
                                    materialProgress++;
                                    found = true;
                                    break;
                                }
                            }
                            // 如果某个材料不足，停止组装
                            if (!found) {
                                break;
                            }
                        }
                    }
                }
            }

            // Clamp进度并设置
            newProgress = Math.min(newProgress, (float) materialProgress / totalMaterials);
            if (newProgress != assemblingProgress) {
                setAssemblingProgress(newProgress);
                return true;
            }
        }
        return false;
    }

    /**
     * <p>每被调用一次，尝试降低组装进度，并返还材料</p>
     * <p>Attempts to decrease assembling progress, and returns materials.</p>
     *
     * @param container 返还材料的容器 Material container
     * @param progress  降低的进度 Progress to be decreased
     * @return 是否成功改变进度 Whether the progress is successfully changed
     */
    public boolean disassemble(Container container, float progress) {
        boolean ignoreMaterial = level.isClientSide();
        if (!level.isClientSide() && container instanceof Inventory inventory) {
            ignoreMaterial = inventory.player.hasInfiniteMaterials();
        }
        FabricatingRecipe recipe = getRecipe();
        // 未找到配方则不改变组装进度
        if (recipe != null) {
            int totalTime = recipe.getProcessingTime();
            float step = progress / totalTime;
            float newProgress = assemblingProgress - step;

            // 计算新的组装进度对应的材料需求
            int totalMaterials = recipe.getIngredientList().size(); // 总材料数量
            int targetMaterialProgress = (int) Math.floor(newProgress * totalMaterials);

            // 检查是否需要返还材料
            if (targetMaterialProgress < materialProgress) {
                if (ignoreMaterial) { // 创造模式不返还材料
                    materialProgress = Math.clamp(targetMaterialProgress, 0, totalMaterials);
                } else {
                    materialProgress = Math.clamp(materialProgress, 0, totalMaterials);
                    int materialsToReturn = materialProgress - targetMaterialProgress;
                    // 从后往前返还材料（后消耗的先返还）
                    for (int i = 0; i < materialsToReturn; i++) {
                        if (materialProgress > 0) {
                            materialProgress--;
                            Ingredient ingredientToReturn = recipe.getIngredientList().get(materialProgress);

                            // 创建要返还的物品（取第一个匹配项）
                            ItemStack[] matchingStacks = ingredientToReturn.getItems();
                            if (matchingStacks.length > 0) {
                                ItemStack returnStack = matchingStacks[0].copy();
                                returnStack.setCount(1);

                                // 尝试放入容器
                                boolean added = false;
                                for (int slot = 0; slot < container.getContainerSize(); slot++) {
                                    ItemStack stack = container.getItem(slot);
                                    if (stack.isEmpty()) {
                                        container.setItem(slot, returnStack);
                                        added = true;
                                        break;
                                    } else if (ItemStack.isSameItemSameComponents(stack, returnStack) &&
                                            stack.getCount() < stack.getMaxStackSize()) {
                                        stack.grow(1);
                                        added = true;
                                        break;
                                    }
                                }

                                // 容器已满，掉落物品
                                if (!added && !level.isClientSide()) {
                                    // 在部件位置掉落物品
                                    net.minecraft.world.entity.item.ItemEntity itemEntity =
                                            new net.minecraft.world.entity.item.ItemEntity(
                                                    level,
                                                    rootSubPart.getPosition().x,
                                                    rootSubPart.getPosition().y,
                                                    rootSubPart.getPosition().z,
                                                    returnStack
                                            );
                                    level.addFreshEntity(itemEntity);
                                }
                            }
                        }
                    }
                }
            }
            // Clamp进度并设置
            newProgress = Math.max(newProgress, 0f);
            if (newProgress != assemblingProgress) {
                setAssemblingProgress(newProgress);
                return true;
            }
        }
        return false;
    }

    /**
     * <p>获取部件的手动组装配方，无则返回null</p>
     * <p>Gets the manual assembly recipe of the part, returns null if there is no manual assembly recipe.</p>
     *
     * @return 配方，包含使用材料，时间等信息 Recipe, including the materials, time, etc.
     */
    @Nullable
    public FabricatingRecipe getRecipe() {
        try {
            RecipeHolder<?> recipeHolder = level.getRecipeManager().byKey(type.registryKey).orElseThrow();
            if (recipeHolder.value() instanceof FabricatingRecipe recipe) {
                return recipe;
            } else return null;
        } catch (NoSuchElementException ignore) {
            return null;
        }
    }

    /**
     * <p>设置部件的组装进度，并影响零件的最大耐久和实际质量</p>
     * <p>Sets the assembling progress of the part, which affects the maximum durability and actual mass of the part.</p>
     *
     * @param progress 组装进度，0~1
     */
    public void setAssemblingProgress(float progress) {
        progress = Math.clamp(progress, 0f, 1f);
        if (progress != this.assemblingProgress) {
            this.assemblingProgress = progress;
            level.getPhysicsLevel().submitDeduplicatedTask("setAssemblingProgress_" + uuid, PPhase.PRE, () -> {
                for (SubPart subPart : subParts.values()) {
                    subPart.body.setMass(subPart.attr.mass * (0.05f + 0.95f * this.assemblingProgress));
                }
                updateMass();
                return null;
            });
            if (!level.isClientSide()) {
                PacketDistributor.sendToPlayersInDimension((ServerLevel) level, new PartAssemblySyncPayload(
                        vehicle.uuid,
                        uuid,
                        assemblingProgress,
                        materialProgress
                ));
            }
        }
    }

    public void updateMass() {
        float totalMass = 0;
        for (SubPartAttr subPart : type.getVariants().get(variantName).subParts().values()) {
            totalMass += subPart.mass;
        }
        this.totalMass = totalMass;
    }

    /**
     * 主线程 Main thread
     * <p>将部件的所有零件添加到物理世界，开始物理运算</p>
     * <p>Adds all sub-parts of the part to the physical world and starts the physical calculation.</p>
     */
    public void addToLevel() {
        for (SubPart subPart : subParts.values()) subPart.addToLevel();
    }

    /**
     * 主线程 Main thread
     * <p>部件实例被销毁时调用，清除所有子零件、子系统、实体、碰撞箱等</p>
     * <p>Called when the part instance is destroyed, clears all sub-parts, sub-systems, entities, hit boxes, etc.</p>
     */
    public void destroy() {
        for (SubPart subPart : subParts.values()) subPart.destroy();
    }

    public void setTransform(Transform transform) {
        if (vehicle == null || !vehicle.inLevel) {
            setTransformRaw(transform);
        } else level.getPhysicsLevel().submitImmediateTask(PPhase.PRE, () -> {
            setTransformRaw(transform);
            return null;
        });
    }

    private void setTransformRaw(Transform transform) {
        Transform rootTransform = rootSubPart.body.getTransform(null).invert();
        rootSubPart.setPosition(transform.getTranslation());
        rootSubPart.setRotation(transform.getRotation());
        rootSubPart.transform = transform.clone();
        rootSubPart.oldTransform = transform.clone();
        rootSubPart.body.setPhysicsTransform(transform);
        PhysicsBodyExtensionKt.stateOf(rootSubPart.body).setTransform(transform);
        PhysicsBodyExtensionKt.stateOf(rootSubPart.body).setLastTransform(transform);
        Transform subPartTransform = new Transform();
        for (SubPart subPart : subParts.values()) {
            if (subPart == rootSubPart) continue;
            subPart.body.getTransform(subPartTransform);
            MyMath.combine(subPartTransform, rootTransform, subPartTransform);
            MyMath.combine(subPartTransform, transform, subPartTransform);
            subPart.setPosition(subPartTransform.getTranslation());
            subPart.setRotation(subPartTransform.getRotation());
            subPart.transform = subPartTransform.clone();
            subPart.oldTransform = subPartTransform.clone();
            subPart.body.setPhysicsTransform(subPartTransform);
            PhysicsBodyExtensionKt.stateOf(subPart.body).setTransform(subPartTransform);
            PhysicsBodyExtensionKt.stateOf(subPart.body).setLastTransform(subPartTransform);
        }
    }

}
