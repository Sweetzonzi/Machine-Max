package io.github.tt432.machinemax.common.part;

import cn.solarmoon.spark_core.animation.anim.play.AnimData;
import cn.solarmoon.spark_core.animation.anim.play.AnimPlayData;
import cn.solarmoon.spark_core.animation.anim.play.ModelType;
import cn.solarmoon.spark_core.animation.model.part.BonePart;
import cn.solarmoon.spark_core.phys.thread.PhysLevel;
import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.client.PartMolangScope;
import io.github.tt432.machinemax.common.entity.part.MMPartEntity;
import io.github.tt432.machinemax.mixin_interface.IMixinLevel;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.resources.ResourceLocation;
import org.ode4j.ode.DGeom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractPart {
    //渲染属性
    public PartMolangScope molangScope;//用于储存作用域为部件本身的Molang变量
    //基础属性
    @Getter
    @Setter
    protected MMPartEntity attachedEntity;//此部件附着的实体
    //模块化属性
    public final PartElement rootElement;
    public final Map<String, PartElement> partElements = new HashMap<>();//部件的组成零件

    public AbstractPart(MMPartEntity attachedEntity) {
        this.attachedEntity = attachedEntity;
        this.molangScope = new PartMolangScope(this);
        rootElement = createElementsFromModel();
    }

    protected PartElement createElementsFromModel() {
        AnimData data = new AnimData(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, getName()), ModelType.ENTITY, AnimPlayData.getEMPTY());
        ArrayList<BonePart> bones = data.getModel().getBones();//从模型获取所有骨骼
        ArrayList<BonePart> collisionBones = new ArrayList<>();//碰撞骨骼
        ArrayList<BonePart> massBones = new ArrayList<>();//质量骨骼
        ArrayList<BonePart> jointBones = new ArrayList<>();//关节骨骼
        for (BonePart bone : bones) {//遍历模型骨骼
            if (bone.getName().contains("mmElement_")) {
                String name = bone.getName().replaceFirst("mmElement_", "");
                partElements.put(name, new PartElement(name, this, bone));//创建零件
            }
            else if (bone.getName().contains("mmCollision_")) collisionBones.add(bone);
            else if (bone.getName().contains("mmMass_")) massBones.add(bone);
            else if (bone.getName().contains("mmJoint_")) jointBones.add(bone);
        }
        for(BonePart collisionBone : collisionBones){//为零件创建碰撞体积
            BonePart parent = collisionBone.getParent();
            if(parent!=null &&parent.getName().contains("mmElement_")) partElements.get(parent.getName().replaceFirst("mmElement_", "")).createCollisionShape(collisionBone);
            else MachineMax.LOGGER.error("碰撞参数骨骼{}没有匹配的零件！", collisionBone.getName());
        }
        for(BonePart massBone : massBones){
            BonePart parent = massBone.getParent();
            if(parent!=null &&parent.getName().contains("mmElement_")) partElements.get(parent.getName().replaceFirst("mmElement_", "")).createMass(massBone);
            else MachineMax.LOGGER.error("质量参数骨骼{}没有匹配的零件！", massBone.getName());
        }
        for(BonePart jointBone : jointBones){
            //TODO:处理关节骨骼
        }
        //TODO:处理特殊骨骼连接关系，如履带的首位相连
        return partElements.values().iterator().next();//将表中第一个零件作为部件的根零件
    }

    public void addAllElementsToLevel(){
        PhysLevel level = ((IMixinLevel)attachedEntity.level()).machine_Max$getPhysLevel();
        level.launch(()->{
            for(PartElement element : partElements.values()){
                for (DGeom geom : element.geoms) level.getPhysWorld().getSpace().add(geom);//添加碰撞体
                element.enable();//激活零件
            }
            return null;
        });
    }

    public void removeAllElementsFromLevel(){
        PhysLevel level = ((IMixinLevel)attachedEntity.level()).machine_Max$getPhysLevel();
        level.launch(()->{
            for(PartElement element : partElements.values()){
                for (DGeom geom : element.geoms) geom.destroy();//销毁碰撞体
                element.body.destroy();//销毁运动体
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
