package io.github.tt432.machinemax.common.part;

import cn.solarmoon.spark_core.animation.anim.play.AnimData;
import cn.solarmoon.spark_core.animation.anim.play.AnimPlayData;
import cn.solarmoon.spark_core.animation.anim.play.ModelType;
import cn.solarmoon.spark_core.animation.model.part.BonePart;
import cn.solarmoon.spark_core.phys.thread.ThreadHelperKt;
import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.client.PartMolangScope;
import io.github.tt432.machinemax.common.entity.part.MMPartEntity;
import io.github.tt432.machinemax.common.sloarphys.body.ModelPartBody;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.resources.ResourceLocation;
import org.ode4j.ode.DGeom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class AbstractPart {
    //渲染属性
    public PartMolangScope molangScope;//用于储存作用域为部件本身的Molang变量
    //基础属性
    @Getter
    @Setter
    volatile int id;
    @Getter
    @Setter
    protected MMPartEntity attachedEntity;//此部件附着的实体
    //模块化属性
    public final ModelPartBody rootElement;//部件的核心组成零件
    public final Map<String, ModelPartBody> partElements = new HashMap<>();//部件的组成零件

    public AbstractPart(MMPartEntity attachedEntity) {
        this.attachedEntity = attachedEntity;
        this.id = attachedEntity.getId();
        this.molangScope = new PartMolangScope(this);
        rootElement = createElementsFromModel();
    }

    protected ModelPartBody createElementsFromModel() {
        ModelPartBody rootElement = null;
        AnimData data = new AnimData(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, getName()), ModelType.ENTITY, AnimPlayData.getEMPTY());
        LinkedHashMap<String, BonePart> bones = data.getModel().getBones();//从模型获取所有骨骼
        ArrayList<BonePart> collisionBones = new ArrayList<>();//碰撞骨骼
        ArrayList<BonePart> massBones = new ArrayList<>();//质量骨骼
        ArrayList<BonePart> jointBones = new ArrayList<>();//关节骨骼
        for (BonePart bone : bones.values()) {//遍历模型骨骼
            if (bone.getName().startsWith("mmPartBody_")) {
                String name = bone.getName().replaceFirst("mmPartBody_", "");
                var element = new ModelPartBody(name, this, bone);
                partElements.put(name, element);//创建零件
                if (bone.getLocators().get("RootElement") != null) {//标记根零件
                    if (rootElement == null) rootElement = element;
                    else MachineMax.LOGGER.error("在模型文件{}中检测到了多个根零件标记！", getName());
                }
            } else if (bone.getName().startsWith("mmCollision_")) collisionBones.add(bone);
            else if (bone.getName().startsWith("mmMass_")) massBones.add(bone);
            else if (bone.getName().startsWith("mmSlot_")) jointBones.add(bone);
        }
        for (BonePart collisionBone : collisionBones) {//为零件创建碰撞体积
            BonePart parent = collisionBone.getParent();
            if (parent != null && parent.getName().startsWith("mmPartBody_"))
                partElements.get(parent.getName().replaceFirst("mmPartBody_", "")).createCollisionShape(collisionBone);
            else MachineMax.LOGGER.error("{}的碰撞参数骨骼{}没有匹配的零件！", getName(), collisionBone.getName());
        }
        for (BonePart massBone : massBones) {//为零件添加质量与转动惯量信息
            BonePart parent = massBone.getParent();
            if (parent != null && parent.getName().startsWith("mmPartBody_"))
                partElements.get(parent.getName().replaceFirst("mmPartBody_", "")).createMass(massBone);
            else MachineMax.LOGGER.error("{}的质量参数骨骼{}没有匹配的零件！", getName(), massBone.getName());
        }
        for (BonePart jointBone : jointBones) {//为零件创建安装槽
            BonePart parent = jointBone.getParent();
            if (parent != null && parent.getName().startsWith("mmPartBody_"))
                partElements.get(parent.getName().replaceFirst("mmPartBody_", "")).createPartBodySlots(jointBone);
            else MachineMax.LOGGER.error("{}的安装槽骨骼{}没有匹配的零件！", getName(), jointBone.getName());
            //TODO:连接同一部件内的零件
                //TODO:调整位置和姿态
                //TODO:连接关节
        }
        //TODO:处理特殊骨骼连接关系，如履带的首位相连
        if (!partElements.values().isEmpty() && rootElement == null)
            rootElement = partElements.values().iterator().next();//将表中第一个零件作为部件的根零件
        else throw new RuntimeException("部件模型" + getName() + "中没有包含零件标识符(mmPartBody_)的骨骼！");
        return rootElement;
    }

    public void addAllElementsToLevel() {
        var level = ThreadHelperKt.getPhysLevelById(attachedEntity.level(), MachineMax.MOD_ID);
        level.getPhysWorld().laterConsume(() -> {
            for (ModelPartBody element : partElements.values()) {
                for (DGeom geom : element.getGeoms()) level.getPhysWorld().getSpace().add(geom);//添加碰撞体
                element.getBody().setGravityMode(true);
                element.enable();//激活零件
            }
            return null;
        });
    }

    public void removeAllElementsFromLevel() {
        var level = ThreadHelperKt.getPhysLevelById(attachedEntity.level(), MachineMax.MOD_ID);
        level.getPhysWorld().laterConsume(() -> {
            for (ModelPartBody element : partElements.values()) {
                element.getBody().destroy();//移除运动体
                for (DGeom geom : element.getGeoms()) {
                    geom.disable();
                    geom.destroy();//移除碰撞体
                }
            }
            return null;
        });
    }

    abstract public String getName();

    abstract public ResourceLocation getModel();

    abstract public ResourceLocation getTexture();

    abstract public ResourceLocation getAnimation();

    abstract public ResourceLocation getAniController();
}
