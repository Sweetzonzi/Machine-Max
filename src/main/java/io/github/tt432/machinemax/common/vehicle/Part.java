package io.github.tt432.machinemax.common.vehicle;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.anim.play.AnimController;
import cn.solarmoon.spark_core.animation.anim.play.BoneGroup;
import cn.solarmoon.spark_core.animation.anim.play.ModelIndex;
import cn.solarmoon.spark_core.animation.model.origin.OBone;
import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.registry.MMRegistries;
import io.github.tt432.machinemax.common.vehicle.attr.ConnectorAttr;
import io.github.tt432.machinemax.common.vehicle.attr.ShapeAttr;
import io.github.tt432.machinemax.common.vehicle.attr.SubPartAttr;
import io.github.tt432.machinemax.common.vehicle.connector.AbstractConnector;
import io.github.tt432.machinemax.common.vehicle.data.PartData;
import io.github.tt432.machinemax.util.data.PosRotVelVel;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.util.*;

@Getter
public class Part implements IAnimatable<Part> {
    //渲染属性
    @Setter
    public ModelIndex modelIndex;//用于储存部件的模型索引(模型贴图动画路径等)
    public int textureIndex;//当前使用的纹理的索引(用于切换纹理)
    //基本属性
    public VehicleCore vehicle;//所属的VehicleCore
    public String name;
    public final PartType type;
    public final Level level;
    public final String variant;
    public final UUID uuid;
    public float durability;
    public final Map<String, SubPart> subParts = HashMap.newHashMap(1);
    public final SubPart rootSubPart;
    public final AnimController animController = new AnimController(this);
    //模块化属性
    public final Map<String, AbstractConnector> connectors = HashMap.newHashMap(1);
    /**
     * 创建新部件
     * 仅在服务端新建部件时使用
     *
     * @param partType 部件类型
     * @param variant  部件变体类型
     * @param level    部件被加入的世界
     */
    public Part(PartType partType, String variant, Level level) {
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
        this.rootSubPart = createSubPart(type.subParts);//创建子部件并指定根子部件
    }

    /**
     * 创建新部件，使用默认变体
     * 仅在服务端新建部件时使用
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
     * @param data  保存或网络传输的数据
     * @param level 部件所在的世界
     */
    public Part(PartData data, Level level) {
        this.name = data.name;
        PartType partType = MMRegistries.getRegistryAccess(level).registry(PartType.PART_REGISTRY_KEY).get().get(data.registryKey);
        if (partType == null)
            MMRegistries.getRegistryAccess(level).registry(PartType.PART_REGISTRY_KEY).get().get(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "test_cube"));
        this.type = MMRegistries.getRegistryAccess(level).registry(PartType.PART_REGISTRY_KEY).get().get(data.registryKey);
        this.level = level;
        this.variant = data.variant;
        this.textureIndex = data.textureIndex;
        this.modelIndex = new ModelIndex(
                type.variants.getOrDefault(variant, type.variants.get("default")),//获取部件模型路径
                type.animation,//获取部件动画路径
                type.textures.get(textureIndex % type.textures.size()));//获取部件第一个可用纹理作为默认纹理
        this.uuid = UUID.fromString(data.uuid);
        this.durability = data.durability;
        this.rootSubPart = createSubPart(type.subParts);//重建子部件并指定根子部件
        for (Map.Entry<String, PosRotVelVel> entry : data.subPartTransforms.entrySet()) {//遍历保存的子部件位置、旋转、速度数据
            SubPart subPart = subParts.get(entry.getKey());//获取已重建的子部件
            if (subPart != null) {
                //TODO:设定子部件body的位置、旋转、速度
            }
        }
    }

    private SubPart createSubPart(Map<String, SubPartAttr> subPartAttrMap) {
        SubPart rootSubPart = null;
        ModelIndex data = this.getModelIndex();
        HashMap<SubPart, String> subPartMap = new HashMap<>();//用于记录子部件的父子关系
        LinkedHashMap<String, OBone> bones = data.getModel().getBones();//从模型获取所有骨骼
        if (bones.isEmpty()) throw new RuntimeException("模型路径" + data.getModelPath() + "中无骨骼，请检查模型文件路径配置。");
        //创建零件
        for (Map.Entry<String, SubPartAttr> subPartEntry : subPartAttrMap.entrySet()) {
            SubPart subPart = new SubPart(subPartEntry.getKey(), subPartEntry.getValue(), this);
            subParts.put(subPartEntry.getKey(), subPart);//创建零件并放入部件的零件表
            if (subPartEntry.getValue().parent().isEmpty()) {//检测是否为根零件
                if (rootSubPart == null) rootSubPart = subPart;//记录第一个根零件
                else MachineMax.LOGGER.error("在模型文件{}的零件信息仅允许存在一个父节点为空的零件作为根零件，请检查模型文件！", type);
            } else subPartMap.put(subPart, subPartEntry.getValue().parent());//记录子部件的父子关系
            for (Map.Entry<String, ShapeAttr> shapeEntry : subPartEntry.getValue().shapeAndMaterials().entrySet()) {
                if (bones.get(shapeEntry.getKey()) != null) {//若找到了对应的碰撞形状骨骼
                    switch (shapeEntry.getValue().type()) {
                        case "box":
                            //TODO:创建碰撞体积
                            break;
                        case "sphere":
                            //TODO:创建碰撞体积
                            break;
                        case "cylinder":
                            //TODO:创建碰撞体积
                            break;
                        case "cone":
                            //TODO:创建碰撞体积
                            break;
                        case "capsule":
                            //TODO:创建碰撞体积
                            break;
                        default:
                            MachineMax.LOGGER.error("在{}的零件中发现不支持的碰撞形状类型{}！", type.name, shapeEntry.getValue().type());
                    }
                    //TODO:设置碰撞形状的材质与厚度
                } else
                    MachineMax.LOGGER.error("在{}的零件中发现未找到对应的碰撞形状骨骼{}！", type.name, shapeEntry.getKey());
            }
            for (Map.Entry<String, ConnectorAttr> connectorEntry : subPartEntry.getValue().connectors().entrySet()) {
                if (bones.get(connectorEntry.getValue().boneName()) != null) {//若找到了对应的零件接口骨骼
                    switch (connectorEntry.getValue().type()) {
                        case "AttachPoint":
                            //TODO:创建AttachPoint
                            break;
                        case "6DOF":
                            //TODO:创建6自由度关节
                            break;
                        default:
                            MachineMax.LOGGER.error("在{}的零件中发现不支持的零件接口类型{}！", type.name, connectorEntry.getValue().type());
                    }
                } else
                    MachineMax.LOGGER.error("在{}的零件中发现未找到对应的零件接口骨骼{}！", type.name, connectorEntry.getValue().boneName());
            }
            //TODO:调整位置和姿态
            //TODO:连接关节
        }
        //设置零件的父子关系
        for(Map.Entry<SubPart, String> entry : subPartMap.entrySet()){
            SubPart subPart = entry.getKey();
            String parentName = entry.getValue();
            subPart.parent = subParts.get(parentName);//设置子部件的父部件
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
        this.textureIndex = index;
        this.setModelIndex(new ModelIndex(
                modelIndex.getModelPath(),
                modelIndex.getAnimPath(),
                type.getTextures().get(index % type.getTextures().size())
        ));
    }

    public void addToLevel(){
        //TODO:添加到世界
    }

    public void destroy() {
        //TODO:断开所有连接关系
        //TODO:销毁零件
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
        return null;
    }

    @Override
    public float getRootYRot(float v) {
        return 0;
    }

    @Override
    public Matrix4f getWorldPositionMatrix(float partialTick) {
//        return new Matrix4f().translate(getWorldPosition(partialTick).toVector3f()).rotateZYX(SparkMathKt.toVector3f(rootSubPart.getBody().getQuaternion().toEuler()));
        return null;
    }

}
