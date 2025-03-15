package io.github.tt432.machinemax.common.vehicle;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.anim.play.AnimController;
import cn.solarmoon.spark_core.animation.anim.play.BoneGroup;
import cn.solarmoon.spark_core.animation.anim.play.ModelIndex;
import cn.solarmoon.spark_core.animation.model.origin.OBone;
import cn.solarmoon.spark_core.animation.model.origin.OCube;
import cn.solarmoon.spark_core.animation.model.origin.OLocator;
import cn.solarmoon.spark_core.molang.core.storage.IForeignVariableStorage;
import cn.solarmoon.spark_core.molang.core.storage.IScopedVariableStorage;
import cn.solarmoon.spark_core.molang.core.storage.ITempVariableStorage;
import cn.solarmoon.spark_core.molang.core.storage.VariableStorage;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.SparkMathKt;
import cn.solarmoon.spark_core.sync.SyncData;
import cn.solarmoon.spark_core.sync.SyncerType;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CylinderCollisionShape;
import com.jme3.bullet.collision.shapes.SphereCollisionShape;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.mojang.datafixers.util.Pair;
import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.entity.MMPartEntity;
import io.github.tt432.machinemax.common.vehicle.attr.ConnectorAttr;
import io.github.tt432.machinemax.common.vehicle.attr.ShapeAttr;
import io.github.tt432.machinemax.common.vehicle.attr.SubPartAttr;
import io.github.tt432.machinemax.common.vehicle.attr.subsystem.*;
import io.github.tt432.machinemax.common.vehicle.connector.AbstractConnector;
import io.github.tt432.machinemax.common.vehicle.connector.AttachPointConnector;
import io.github.tt432.machinemax.common.vehicle.connector.SpecialConnector;
import io.github.tt432.machinemax.common.vehicle.data.PartData;
import io.github.tt432.machinemax.common.vehicle.signal.*;
import io.github.tt432.machinemax.common.vehicle.subsystem.*;
import io.github.tt432.machinemax.common.vehicle.subsystem.AbstractSubsystem;
import io.github.tt432.machinemax.common.vehicle.subsystem.ResourceStorageSubsystem;
import io.github.tt432.machinemax.network.payload.assembly.PartPaintPayload;
import io.github.tt432.machinemax.util.data.PosRotVelVel;
import jme3utilities.math.MyMath;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Getter
public class Part implements IAnimatable<Part>, ISubsystemHost, ISignalReceiver {
    //渲染属性
    @Setter
    public ModelIndex modelIndex;//用于储存部件的模型索引(模型贴图动画路径等)
    public int textureIndex;//当前使用的纹理的索引(用于切换纹理)
    private Vec3 oldPos;//上一tick的位置
    private Vec3 pos;//当前tick位置//基本属性
    public VehicleCore vehicle;//所属的VehicleCore
    @Nullable
    public MMPartEntity entity;//实体对象
    public String name;
    public final PartType type;
    public final Level level;
    public final String variant;
    public final UUID uuid;
    public float durability;
    public final Map<String, SubPart> subParts = HashMap.newHashMap(1);
    public final SubPart rootSubPart;
    public final AnimController animController = new AnimController(this);
    public final float totalMass;
    //Molang变量存储
    public ITempVariableStorage tempStorage = new VariableStorage();
    public IScopedVariableStorage scopedStorage = new VariableStorage();
    public IForeignVariableStorage foreignStorage = new VariableStorage();
    //模块化属性
    public final Map<String, AbstractConnector> externalConnectors = HashMap.newHashMap(1);
    public final Map<String, AbstractConnector> allConnectors = HashMap.newHashMap(1);
    public final Map<String, AbstractSubsystem> subsystems = HashMap.newHashMap(1);
    public final ConcurrentMap<String, Signals> signals = new ConcurrentHashMap<>();//部件内共享的信号

    /**
     * <p>创建新部件，使用指定变体</p>
     * <p>仅应在服务端新建部件时使用</p>
     *
     * @param partType 部件类型
     * @param variant  部件变体类型
     * @param level    部件被加入的世界
     */
    public Part(PartType partType, String variant, Level level) {
        if (variant == null) variant = "default";
        this.modelIndex = new ModelIndex(
                partType.variants.getOrDefault(variant, partType.variants.get("default")),//获取部件模型路径
                partType.animation,//获取部件动画路径
                partType.textures.getFirst());//获取部件第一个可用纹理作为默认纹理
        this.textureIndex = 0;
        this.name = partType.getName();
        this.type = partType;
        this.variant = variant;
        this.level = level;
        this.uuid = UUID.randomUUID();
        this.durability = partType.basicDurability;
        float totalMass = 0;
        for (SubPartAttr subPart : partType.subParts.values()){
            totalMass += subPart.mass();
        }
        this.totalMass = totalMass;
        this.rootSubPart = createSubPart(type.subParts);//创建子部件并指定根子部件
    }

    /**
     * <p>从注册名创建新部件，使用指定变体</p>
     * <p>仅应在服务端新建部件时使用</p>
     *
     * @param registryKey 部件注册名
     * @param variant     部件变体类型
     * @param level       部件被加入的世界
     */
    public Part(ResourceLocation registryKey, String variant, Level level) {
        this(Objects.requireNonNull(level.registryAccess().registry(PartType.PART_REGISTRY_KEY).get().get(registryKey)), variant, level);
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
     * <p>创建新部件，使用默认变体</p>
     * <p>仅应在服务端新建部件时使用</p>
     *
     * @param registryKey 部件注册名
     * @param level       部件被加入的世界
     */
    public Part(ResourceLocation registryKey, Level level) {
        this(registryKey, "default", level);
    }

    /**
     * 从保存或网络传输的数据中重建部件
     *
     * @param data  保存或网络传输的数据
     * @param level 部件所在的世界
     */
    public Part(PartData data, Level level) {
        this.name = data.name;
        PartType partType = level.registryAccess().registry(PartType.PART_REGISTRY_KEY).get().get(data.registryKey);
        if (partType == null)
            level.registryAccess().registry(PartType.PART_REGISTRY_KEY).get().get(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "test_cube"));
        this.type = level.registryAccess().registry(PartType.PART_REGISTRY_KEY).get().get(data.registryKey);
        this.level = level;
        this.variant = data.variant;
        this.textureIndex = data.textureIndex;
        this.modelIndex = new ModelIndex(
                type.variants.getOrDefault(variant, type.variants.get("default")),//获取部件模型路径
                type.animation,//获取部件动画路径
                type.textures.get(textureIndex % type.textures.size()));//获取部件第一个可用纹理作为默认纹理
        this.uuid = UUID.fromString(data.uuid);
        this.durability = data.durability;
        float totalMass = 0;
        for (SubPartAttr subPart : this.type.subParts.values()){
            totalMass += subPart.mass();
        }
        this.totalMass = totalMass;
        this.rootSubPart = createSubPart(type.subParts);//重建子部件并指定根子部件
        for (Map.Entry<String, PosRotVelVel> entry : data.subPartTransforms.entrySet()) {//遍历保存的子部件位置、旋转、速度数据
            SubPart subPart = subParts.get(entry.getKey());//获取已重建的子部件
            if (subPart != null) {//设定子部件body的位置、旋转、速度
                subPart.body.setPhysicsLocation(entry.getValue().position());
                subPart.body.setPhysicsRotation(SparkMathKt.toBQuaternion(entry.getValue().rotation()));
                subPart.body.setLinearVelocity(entry.getValue().linearVel());
                subPart.body.setAngularVelocity(entry.getValue().angularVel());
            } else
                MachineMax.LOGGER.error("从数据中重建部件时发生了错误，部件{}中未能找到子部件{}。", type.name, entry.getKey());
        }
    }

    public void onTick() {
        if (this.entity == null || this.entity.isRemoved()) {
            if (!level.isClientSide()) refreshPartEntity();
        }
        if(pos!=null) oldPos = pos;
        pos = SparkMathKt.toVec3(rootSubPart.body.getPhysicsLocation(null));
    }

    public void onPhysicsTick() {

    }

    public void refreshPartEntity() {
        this.entity = new MMPartEntity(level, this);
        level.addFreshEntity(this.entity);
    }

    private void createSubsystems(
            Map<String, AbstractSubsystemAttr> subSystemAttrMap
    ) {
        for (Map.Entry<String, AbstractSubsystemAttr> entry : subSystemAttrMap.entrySet()) {
            String name = entry.getKey();
            AbstractSubsystemAttr attr = entry.getValue();
            switch (attr.getType()) {//根据类型为部件创建不同的子系统
                case SEAT:
                    SeatSubsystem seatSubsystem = new SeatSubsystem(this, name, (SeatSubsystemAttr) attr);
                    subsystems.put(name, seatSubsystem);
                    break;
                case RESOURCE_STORAGE:
                    subsystems.put(name, new ResourceStorageSubsystem(this, name, (ResourceStorageSubsystemAttr) attr));
                    break;
                case ENGINE:
                    EngineSubsystem engineSubsystem = new EngineSubsystem(this, name, (EngineSubsystemAttr) attr);
                    subsystems.put(name, engineSubsystem);
                    break;
                case GEARBOX:

                    break;
                case CAR_CTRL:

                    break;
                case TRANSMISSION:
                    TransmissionSubsystem transmissionSubsystem = new TransmissionSubsystem(this, name, (TransmissionSubsystemAttr) attr);
                    subsystems.put(name, transmissionSubsystem);
                    break;
                case MOTOR:
                    MotorSubsystem motorSubsystem = new MotorSubsystem(this, name, (MotorSubsystemAttr) attr);
                    subsystems.put(name, motorSubsystem);
                    break;
            }
        }
    }

    private void createConnectors(
            SubPart subPart,
            SubPartAttr subPartAttr,
            LinkedHashMap<String, OLocator> locators
    ) {
        for (Map.Entry<String, ConnectorAttr> connectorEntry : subPartAttr.connectors().entrySet()) {
            if (locators.get(connectorEntry.getValue().locatorName()) instanceof OLocator locator) {//若找到了对应的零件对接口Locator
                org.joml.Vector3f rotation = locator.getRotation().toVector3f();
                Transform posRot = new Transform(//对接口的位置与姿态
                        PhysicsHelperKt.toBVector3f(locator.getOffset()).subtract(subPart.massCenterTransform.getTranslation()),
                        SparkMathKt.toBQuaternion(new Quaternionf().rotationZYX(rotation.x, rotation.y, rotation.z)).mult(subPart.massCenterTransform.getRotation().inverse())
                );
                AbstractConnector connector = null;
                switch (connectorEntry.getValue().type()) {
                    case "AttachPoint"://连接点接口
                        connector = new AttachPointConnector(
                                connectorEntry.getKey(),
                                connectorEntry.getValue(),
                                subPart,
                                posRot
                        );
                        break;
                    case "Special"://6自由度自定义关节接口
                        connector = new SpecialConnector(
                                connectorEntry.getKey(),
                                connectorEntry.getValue(),
                                subPart,
                                posRot
                        );
                        break;
                    default:
                        MachineMax.LOGGER.error("在零件{}中发现不支持的零件接口类型{}。", type.name, connectorEntry.getValue().type());
                }
                if (connector != null) {
                    subPart.connectors.put(connectorEntry.getKey(), connector);
                    this.allConnectors.put(connectorEntry.getKey(), connector);
                    if (!connector.internal) this.externalConnectors.put(connectorEntry.getKey(), connector);
                }
            } else
                MachineMax.LOGGER.error("在部件{}中未找到对应的零件接口Locator:{}。", type.name, connectorEntry.getValue().locatorName());
        }

    }

    private void createCollisionShapes(
            SubPart subPart,
            SubPartAttr subPartAttr,
            LinkedHashMap<String, OBone> bones,
            LinkedHashMap<String, OLocator> locators
    ) {
        for (Map.Entry<String, ShapeAttr> shapeEntry : subPartAttr.collisionShapeAttr().entrySet()) {
            if (bones.get(shapeEntry.getKey()) != null) {//若找到了对应的碰撞形状骨骼
                OBone bone = bones.get(shapeEntry.getKey());
                switch (shapeEntry.getValue().shapeType()) {
                    case "box"://与方块的尺寸匹配
                        for (OCube cube : bone.getCubes()) {
                            org.joml.Vector3f size = cube.getSize().scale(0.5f).toVector3f();
                            BoxCollisionShape boxShape = new BoxCollisionShape(size.x, size.y, size.z);
                            org.joml.Vector3f rotation = cube.getRotation().toVector3f();
                            Quaternionf quaternion = new Quaternionf().rotationXYZ(rotation.x, rotation.y, rotation.z);
                            subPart.collisionShape.addChildShape(
                                    boxShape,
                                    PhysicsHelperKt.toBVector3f(cube.getTransformedCenter(new Matrix4f())),
                                    SparkMathKt.toBQuaternion(quaternion).toRotationMatrix());
                        }
                        break;
                    case "sphere"://取方块的x轴尺寸作为球直径
                        for (OCube cube : bone.getCubes()) {
                            SphereCollisionShape ballShape = new SphereCollisionShape((float) (cube.getSize().x / 2));
                            subPart.collisionShape.addChildShape(
                                    ballShape,
                                    PhysicsHelperKt.toBVector3f(cube.getTransformedCenter(new Matrix4f()).sub(bone.getPivot().toVector3f())));
                        }
                        break;
                    case "cylinder"://取方块的z轴尺寸为直径，x轴尺寸为圆柱高，圆柱默认方向为X轴(横躺)
                        for (OCube cube : bone.getCubes()) {
                            Vector3f size = PhysicsHelperKt.toBVector3f(cube.getSize().scale(0.5f));
                            org.joml.Vector3f rotation = cube.getRotation().toVector3f();
                            Quaternionf quaternion = new Quaternionf().rotationXYZ(rotation.x, rotation.y, rotation.z);
                            CylinderCollisionShape cylinderShape = new CylinderCollisionShape(size, 0);
                            subPart.collisionShape.addChildShape(
                                    cylinderShape,
                                    PhysicsHelperKt.toBVector3f(cube.getTransformedCenter(new Matrix4f()).sub(bone.getPivot().toVector3f())),
                                    SparkMathKt.toBQuaternion(quaternion).toRotationMatrix());
                        }
                        break;
                    case "cone":
                        //TODO:创建碰撞体积
                        break;
                    case "capsule":
                        //TODO:创建碰撞体积
                        break;
                    default:
                        MachineMax.LOGGER.error("在部件{}中发现不支持的碰撞形状类型{}。", type.name, shapeEntry.getValue());
                }
            } else
                MachineMax.LOGGER.error("在部件{}中未找到对应的碰撞形状骨骼{}。", type.name, shapeEntry.getKey());
        }
    }

    private SubPart createSubPart(Map<String, SubPartAttr> subPartAttrMap) {
        SubPart rootSubPart = null;
        ModelIndex data = this.getModelIndex();
        HashMap<SubPart, String> subPartMap = new HashMap<>();//用于记录子部件的父子关系
        LinkedHashMap<String, OBone> bones = data.getModel().getBones();//从模型获取所有骨骼
        LinkedHashMap<String, OLocator> locators = LinkedHashMap.newLinkedHashMap(0);
        for (OBone bone : bones.values()) locators.putAll(bone.getLocators());//从模型获取所有定位器
        if (bones.isEmpty())
            throw new RuntimeException("模型路径" + data.getModelPath() + "中无骨骼，请检查模型文件路径配置。");
        //创建零件
        for (Map.Entry<String, SubPartAttr> subPartEntry : subPartAttrMap.entrySet()) {//遍历部件的零件属性
            SubPart subPart = new SubPart(subPartEntry.getKey(), this, subPartEntry.getValue());//创建零件
            subParts.put(subPartEntry.getKey(), subPart);//将零件放入部件的零件表
            if (subPartEntry.getValue().parent().isEmpty()) {//检测是否为根零件
                if (rootSubPart == null) rootSubPart = subPart;//记录第一个根零件
                else MachineMax.LOGGER.error("仅允许存在一个根零件，请检查模型文件{}。", type);
            } else subPartMap.put(subPart, subPartEntry.getValue().parent());//记录子部件的父子关系
            //创建碰撞体积
            createCollisionShapes(subPart, subPartEntry.getValue(), bones, locators);
            //调整零件质心
            Transform massCenter = new Transform();
            if (!subPartEntry.getValue().massCenterLocator().isEmpty()) {//若零件制定了质心定位点
                OLocator locator = locators.get(subPartEntry.getValue().massCenterLocator());
                if (locator != null) {
                    org.joml.Vector3f rotation = locator.getRotation().toVector3f();
                    massCenter = new Transform(
                            PhysicsHelperKt.toBVector3f(locator.getOffset()),
                            SparkMathKt.toBQuaternion(new Quaternionf().rotationZYX(rotation.x, rotation.y, rotation.z))
                    );
                } else {
                    MachineMax.LOGGER.error("在部件{}中未找到质心定位点{}。", type.name, subPartEntry.getValue().massCenterLocator());
                }
                subPart.collisionShape.correctAxes(massCenter);//调整碰撞体位置，使模型原点对齐质心(必须在创建完成碰撞体积后进行！)
                subPart.massCenterTransform = massCenter;
                //TODO:重新计算并调节转动惯量
            }
            subPart.body.setCollisionShape(subPart.collisionShape);//重新设置碰撞体积
            subPart.body.setMass(subPartEntry.getValue().mass());//设置质量
            //创建零件对接口
            createConnectors(subPart, subPartEntry.getValue(), locators);
        }
        //创建部件内子系统
        createSubsystems(type.subsystems);//创建子系统，赋予部件实际功能
        //设置零件的父子关系，连接内部关节
        for (Map.Entry<SubPart, String> entry : subPartMap.entrySet()) {
            SubPart subPart = entry.getKey();
            String parentName = entry.getValue();
            subPart.parent = subParts.get(parentName);//设置子部件的父部件
            for (AbstractConnector connector : subPart.connectors.values()) {
                if (connector.internal && connector.attachedConnector == null) {
                    for (Map.Entry<SubPart, String> entry2 : subPartMap.entrySet()) {
                        if (entry2.getValue().equals(parentName)) continue;
                        if (entry2.getKey().connectors.containsKey(connector.attr.ConnectedTo())) {
                            AbstractConnector targetConnector = entry2.getKey().connectors.get(connector.attr.ConnectedTo());
                            if (targetConnector.internal && targetConnector.attachedConnector == null && targetConnector instanceof AttachPointConnector) {
                                targetConnector.subPart.body.setPhysicsTransform(targetConnector.subPart.massCenterTransform);
                                connector.attach((AttachPointConnector) targetConnector, true);
                            } else if (targetConnector.internal && targetConnector.attachedConnector == null && connector instanceof AttachPointConnector) {
                                connector.subPart.body.setPhysicsTransform(connector.subPart.massCenterTransform);
                                targetConnector.attach((AttachPointConnector) connector, true);
                            } else
                                MachineMax.LOGGER.error("零件{}的{}接口与零件{}的{}接口不匹配。", type.name, connector.name, entry2.getKey().name, targetConnector.name);
                        }
                    }
                }
            }
        }
        //设置默认根零件
        if (!subParts.values().isEmpty() && rootSubPart == null)//若模型中没有根零件
            rootSubPart = subParts.values().iterator().next();//将表中第一个零件作为部件的根零件
        return rootSubPart;
    }

    /**
     * 按给定的纹理索引切换部件纹理
     * 可用于为拥有多个纹理的部件选择外观
     *
     * @param index 纹理索引
     */
    public void switchTexture(int index) {
        if (type.getTextures().size() == 1) return;
        this.textureIndex = index % type.getTextures().size();
        this.setModelIndex(new ModelIndex(
                modelIndex.getModelPath(),
                modelIndex.getAnimPath(),
                type.getTextures().get(index % type.getTextures().size())
        ));
        //同步客户端
        if (!level.isClientSide()) PacketDistributor.sendToPlayersInDimension((ServerLevel) level,
                new PartPaintPayload(vehicle.uuid.toString(), this.uuid.toString(), this.textureIndex));
    }

    /**
     * 将部件的所有零件添加到物理世界，开始物理运算
     */
    public void addToLevel() {
        for (SubPart subPart : subParts.values()) subPart.addToLevel();
    }

    public void destroy() {
        for (SubPart subPart : subParts.values()) subPart.destroy();
        subsystems.clear();
        if (this.entity != null) {
            this.entity.part = null;
            this.entity.remove(Entity.RemovalReason.DISCARDED);
            this.entity = null;
        }
    }

    public void setTransform(Transform transform) {
        level.getPhysicsLevel().submitTask((a, b) -> {
            Transform rootTransform = rootSubPart.body.getTransform(null).invert();
            rootSubPart.body.setPhysicsTransform(transform);
            for (SubPart subPart : subParts.values()) {
                if (subPart == rootSubPart) continue;
                Transform subPartTransform = subPart.body.getTransform(null);
                MyMath.combine(subPartTransform, rootTransform, subPartTransform);
                MyMath.combine(subPartTransform, transform, subPartTransform);
                subPart.body.setPhysicsTransform(subPartTransform);
            }
            return null;
        });
    }

    @Override
    public Part getAnimatable() {
        return this;
    }

    @NotNull
    @Override
    public AnimController getAnimController() {
        return animController;
    }

    @NotNull
    @Override
    public BoneGroup getBones() {
        return new BoneGroup(this);
    }

    @NotNull
    @Override
    public Vec3 getWorldPosition(float v) {
        if (rootSubPart != null) {
            if (oldPos == null || pos == null)
                return SparkMathKt.toVec3(rootSubPart.body.getPhysicsLocation(null));
            else
                return oldPos.scale(1 - v).add(pos.scale(v));
        } else return Vec3.ZERO;
    }

    @Override
    public float getRootYRot(float v) {
        return 0;
    }

    @Override
    public Matrix4f getWorldPositionMatrix(float partialTick) {
        return SparkMathKt.toMatrix4f(rootSubPart.body.getTransform(null).setTranslation(PhysicsHelperKt.toBVector3f(getWorldPosition(partialTick))).toTransformMatrix());
    }

    @NotNull
    @Override
    public SyncerType getSyncerType() {
        return null;
    }

    @NotNull
    @Override
    public SyncData getSyncData() {
        return null;
    }

    @NotNull
    @Override
    public ITempVariableStorage getTempStorage() {
        return tempStorage;
    }

    @NotNull
    @Override
    public IScopedVariableStorage getScopedStorage() {
        return scopedStorage;
    }

    @NotNull
    @Override
    public IForeignVariableStorage getForeignStorage() {
        return foreignStorage;
    }

    @Override
    public Part getPart() {
        return this;
    }

    @Override
    public ConcurrentMap<String, Signals> getSignalInputs() {
        return signals;
    }

    @Nullable
    @Override
    public Level getAnimLevel() {
        return level;
    }
}
