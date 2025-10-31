package io.github.sweetzonzi.machine_max.common.vehicle;

import cn.solarmoon.spark_core.animation.model.origin.OBone;
import cn.solarmoon.spark_core.animation.model.origin.OLocator;
import cn.solarmoon.spark_core.animation.model.origin.OModel;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.sound.SpreadingSoundHelper;
import cn.solarmoon.spark_core.util.PPhase;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.bounding.BoundingBox;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.mojang.datafixers.util.Pair;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.ConnectorAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.HitBoxAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.SubPartAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.AbstractSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.AttachPointConnector;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.SpecialConnector;
import io.github.sweetzonzi.machine_max.common.vehicle.data.PartData;
import io.github.sweetzonzi.machine_max.common.vehicle.data.SubPartData;
import io.github.sweetzonzi.machine_max.common.vehicle.interact.HitBox;
import io.github.sweetzonzi.machine_max.common.vehicle.signal.ISignalReceiver;
import io.github.sweetzonzi.machine_max.common.vehicle.signal.SignalChannel;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import io.github.sweetzonzi.machine_max.util.data.PosRotVelVel;
import jme3utilities.math.MyMath;
import lombok.Getter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Getter
/**
 * 组装与UGC创作的最小单元
 */
public class Part implements ISignalReceiver {
    //常规属性 General attributes
    public volatile VehicleCore vehicle;//所属的VehicleCore
    public String name;
    public final PartType type;
    public final Level level;
    public final String variant;
    public final UUID uuid;
    public volatile boolean destroyed = false;
    public volatile float sharedDurability;//仅在部件内共享耐久度启用时有效
    public volatile float integrity;
    public final ConcurrentMap<Vector3f, Float> accumulatedImpact = new ConcurrentHashMap<>(8);
    public final ConcurrentMap<String, SignalChannel> signalChannels = new ConcurrentHashMap<>();//部件内共享的信号

    public final Map<String, SubPart> subParts = HashMap.newHashMap(1);
    public final SubPart rootSubPart;
    public float totalMass;
    //模块化属性 Modular attributes
    public final Map<Pair<String, String>, AbstractConnector> externalConnectors = HashMap.newHashMap(1);
    public final Map<Pair<String, String>, AbstractConnector> allConnectors = HashMap.newHashMap(1);

    /**
     * <p>创建新部件，使用指定变体</p>
     * <p>仅应在服务端新建部件时使用</p>
     *
     * @param partType 部件类型
     * @param variant  部件变体类型
     * @param level    部件被加入的世界
     */
    public Part(PartType partType, @Nullable String variant, Level level) {
        if (variant == null) variant = "default";
        this.name = partType.getName();
        this.type = partType;
        this.variant = variant;
        this.level = level;
        this.uuid = UUID.randomUUID();
        this.sharedDurability = partType.basicDurability;
        this.integrity = partType.basicIntegrity;
        this.rootSubPart = createSubParts(type.getVariants().get(variant).subParts());//创建子部件并指定根子部件
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
     * 从保存或网络传输的数据中重建部件
     *
     * @param data               保存或网络传输的数据
     * @param level              部件所在的世界
     * @param readAdditionalData 是否从保存的数据中读取额外数据，否则使用默认数据
     */
    public Part(PartData data, Level level, boolean readAdditionalData) {
        this.name = data.name;
        this.type = getPT(level, data.registryKey);
        this.level = level;
        this.variant = data.variant;
        this.uuid = UUID.fromString(data.uuid);
        this.sharedDurability = readAdditionalData ? Math.min(data.durability, type.basicDurability) : type.basicDurability;
        this.integrity = readAdditionalData ? Math.min(data.integrity, type.basicIntegrity) : type.basicIntegrity;
        this.rootSubPart = createSubParts(type.getVariants().get(variant).subParts());//重建子部件并指定根子部件
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

    public PartType getPT(Level level, ResourceLocation registryKey) {
        PartType pt;
        if (level.isClientSide) pt = MMDynamicRes.PART_TYPES.get(registryKey);
        else pt = MMDynamicRes.SERVER_PART_TYPES.get(registryKey);
        if (pt == null)
            throw new NullPointerException("部件类型" + registryKey + "不存在，请检查数据。可用部件列表: " + (level.isClientSide() ? MMDynamicRes.PART_TYPES.keySet() : MMDynamicRes.SERVER_PART_TYPES.keySet()));
        return pt;
    }

    public void onTick() {
        //判定摧毁
        if (!destroyed && sharedDurability <= 0) destroyed = true;
    }

    public void onPrePhysicsTick() {
        if (!level.isClientSide) {
            float impact = 0;
            if (!accumulatedImpact.isEmpty()) {
                Vector3f hitPoint = new Vector3f();
                for (Map.Entry<Vector3f, Float> entry : accumulatedImpact.entrySet()) {
                    impact += entry.getValue();
                    hitPoint.add(entry.getKey().mult(entry.getValue()));
                }
                if (impact > 0) {
                    hitPoint.divideLocal(impact);
                    if (impact >= integrity) {
                        //强冲击，立即击落部件
                        int count = (int) Math.floor(impact / integrity);//计算击落数量
                        if (integrity <= 0) count = externalConnectors.size();
                        for (int i = 0; i < count; i++) {
                            AbstractConnector connectorToBreak = null;
                            float minDistance = Float.MAX_VALUE;
                            //寻找最近的外部接口并设置为要断开的接口
                            for (AbstractConnector connector : externalConnectors.values()) {
                                if (connector.hasPart()) {
                                    Vector3f connectorWorldPos = connector.subPart.getLocatorWorldPos(connector.attr.locatorName());
                                    float distance = connectorWorldPos.subtract(hitPoint).lengthSquared();
                                    if (distance < minDistance) connectorToBreak = connector;
                                }
                            }
                            if (connectorToBreak != null) vehicle.detachConnector(connectorToBreak);
                        }
                    }
                    //削减部件完整性
                    integrity = Math.clamp(integrity - (destroyed ? 0.5f * impact : 0.1f * impact), 0, type.basicIntegrity);
                }
                accumulatedImpact.clear();
            }
            if (integrity <= 0 && destroyed) {
                float finalImpact = (destroyed ? 0.5f * impact : 0.1f * impact);
                level.submitImmediateTask(PPhase.ALL, () -> {
                    vehicle.removePart(this);
                    SoundEvent sound = SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "part.torn_apart"));
                    SpreadingSoundHelper.playSpreadingSound(level, sound, SoundSource.NEUTRAL, SparkMathKt.toVec3(PhysicsBodyExtensionKt.stateOf(rootSubPart.body).getTransform().getTranslation()), Vec3.ZERO, 64f,
                            (float) ((2 - Math.min(type.basicIntegrity, finalImpact) / type.basicIntegrity) * (1f + 0.2f * (Math.random() - 0.5f))),
                            0.2f + 0.8f * Math.min(type.basicIntegrity, finalImpact) / type.basicIntegrity);
                    return null;
                });
            }
        }
    }

    public void onPostPhysicsTick() {
    }


    /**
     * <p>获取部件所有零件持有的子系统</p>
     * <p>Gets all subsystems held by all parts</p>
     *
     * @return
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

    public void updateMass() {
        float totalMass = 0;
        for (SubPartAttr subPart : type.getVariants().get(variant).subParts().values()) {
            totalMass += subPart.mass;
        }
        this.totalMass = totalMass;
    }

    /**
     * <p>获取此部件为载具提供的最大耐久度</p>
     * <p>Gets the maximum durability of this part as a vehicle</p>
     *
     * @return 最大耐久度 max durability
     */
    public float getDurabilityForVehicle() {
        return type.vehicleDurabilityRate * type.basicDurability;
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
        rootSubPart.body.setPhysicsTransform(transform);
        Transform subPartTransform = new Transform();
        for (SubPart subPart : subParts.values()) {
            if (subPart == rootSubPart) continue;
            subPart.body.getTransform(subPartTransform);
            MyMath.combine(subPartTransform, rootTransform, subPartTransform);
            MyMath.combine(subPartTransform, transform, subPartTransform);
            subPart.body.setPhysicsTransform(subPartTransform);
        }
    }

    @Override
    public ConcurrentMap<String, SignalChannel> getSignalInputChannels() {
        return signalChannels;
    }
}
