package io.github.tt432.machinemax.common.part;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.anim.play.AnimController;
import cn.solarmoon.spark_core.animation.anim.play.BoneGroup;
import cn.solarmoon.spark_core.animation.anim.play.ModelIndex;
import cn.solarmoon.spark_core.animation.model.origin.OBone;
import cn.solarmoon.spark_core.animation.model.origin.OModel;
import cn.solarmoon.spark_core.phys.SparkMathKt;
import cn.solarmoon.spark_core.phys.thread.PhysLevel;
import cn.solarmoon.spark_core.phys.thread.ThreadHelperKt;
import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.client.PartMolangScope;
import io.github.tt432.machinemax.common.entity.part.MMPartEntity;
import io.github.tt432.machinemax.common.part.slot.AbstractBodySlot;
import io.github.tt432.machinemax.common.sloarphys.body.ModelPartBody;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.ode4j.ode.DGeom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class AbstractPart implements IAnimatable<AbstractPart> {
    //渲染属性
    public PartMolangScope molangScope;//用于储存作用域为部件本身的Molang变量
    public ModelIndex modelIndex;//用于储存部件的模型索引(模型贴图动画路径等)
    //基础属性
    @Getter
    @Setter
    volatile int id;
    @Getter
    final Level level;
    @Getter
    final PartType type;
    @Getter
    @Setter
    protected MMPartEntity attachedEntity;//此部件附着的实体
    //模块化属性
    public final ModelPartBody rootBody;//部件的核心组成零件
    public final Map<String, ModelPartBody> partBody = new HashMap<>();//部件的组成零件

    public AbstractPart(PartType type, Level level) {
        this.type = type;
        this.level = level;
        rootBody = createBodiesFromModel();
        this.modelIndex = getModelIndex();
    }

    protected ModelPartBody createBodiesFromModel() {
        ModelPartBody rootBody = null;
        ModelIndex data = this.getModelIndex();
        LinkedHashMap<String, OBone> bones = data.getModel().getBones();//从模型获取所有骨骼
        ArrayList<OBone> collisionBones = new ArrayList<>();//碰撞骨骼
        ArrayList<OBone> massBones = new ArrayList<>();//质量骨骼
        ArrayList<OBone> jointBones = new ArrayList<>();//关节骨骼
        for (OBone bone : bones.values()) {//遍历模型骨骼
            if (bone.getName().startsWith("mmPartBody_")) {
                String name = bone.getName().replaceFirst("mmPartBody_", "");
                var body = new ModelPartBody(name, this, bone);
                partBody.put(name, body);//创建零件
                if (bone.getLocators().get("RootElement") != null) {//标记根零件
                    if (rootBody == null) rootBody = body;
                    else MachineMax.LOGGER.error("在模型文件{}中检测到了多个根零件标记！", getName());
                }
            } else if (bone.getName().startsWith("mmCollision_")) collisionBones.add(bone);
            else if (bone.getName().startsWith("mmMass_")) massBones.add(bone);
            else if (bone.getName().startsWith("mmSlot_")) jointBones.add(bone);
        }
        for (OBone collisionBone : collisionBones) {//为零件创建碰撞体积
            OBone parent = collisionBone.getParent();
            if (parent != null && parent.getName().startsWith("mmPartBody_"))
                partBody.get(parent.getName().replaceFirst("mmPartBody_", "")).createCollisionShape(collisionBone);
            else MachineMax.LOGGER.error("{}的碰撞参数骨骼{}没有匹配的零件！", getName(), collisionBone.getName());
        }
        for (OBone massBone : massBones) {//为零件添加质量与转动惯量信息
            OBone parent = massBone.getParent();
            if (parent != null && parent.getName().startsWith("mmPartBody_"))
                partBody.get(parent.getName().replaceFirst("mmPartBody_", "")).createMass(massBone);
            else MachineMax.LOGGER.error("{}的质量参数骨骼{}没有匹配的零件！", getName(), massBone.getName());
        }
        for (OBone jointBone : jointBones) {//为零件创建安装槽
            OBone parent = jointBone.getParent();
            if (parent != null && parent.getName().startsWith("mmPartBody_"))
                partBody.get(parent.getName().replaceFirst("mmPartBody_", "")).createPartBodySlots(jointBone);
            else MachineMax.LOGGER.error("{}的安装槽骨骼{}没有匹配的零件！", getName(), jointBone.getName());
            //TODO:连接同一部件内的零件
            //TODO:调整位置和姿态
            //TODO:连接关节
        }
        //TODO:处理特殊骨骼连接关系，如履带的首位相连
        if (!partBody.values().isEmpty() && rootBody == null)
            rootBody = partBody.values().iterator().next();//将表中第一个零件作为部件的根零件
        else throw new RuntimeException("部件模型" + getName() + "中没有包含零件标识符(mmPartBody_)的骨骼！");
        rootBody.setRootBody(true);
        return rootBody;
    }

    public void addAllBodiesToLevel() {
        var level = ThreadHelperKt.getPhysLevelById(getLevel(), ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "main"));
        level.getPhysWorld().laterConsume(() -> {
            for (ModelPartBody body : partBody.values()) {
                for (DGeom geom : body.getGeoms()) level.getPhysWorld().getSpace().add(geom);//添加碰撞体
                body.getBody().setGravityMode(true);
                body.enable();//激活零件
            }
            return null;
        });
    }

    public void removeAllBodiesFromLevel() {
        var level = ThreadHelperKt.getPhysLevelById(getLevel(), ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "main"));
        level.getPhysWorld().laterConsume(() -> {
            for (ModelPartBody body : partBody.values()) {
                for(AbstractBodySlot slot : body.getBodySlots().values()){
                    slot.destroy();
                }
                body.getBody().destroy();//移除运动体
                for (DGeom geom : body.getGeoms()) {
                    geom.disable();
                    geom.destroy();//移除碰撞体
                }
            }
            return null;
        });
    }

    abstract public String getName();

    public void createMolangScope() {
        this.molangScope = new PartMolangScope(this);
    }

    public PhysLevel getPhysLevel() {
        return ThreadHelperKt.getPhysLevelById(level, ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "main"));
    }

    @Override
    public AbstractPart getAnimatable() {
        return this;
    }

    @NotNull
    @Override
    public AnimController getAnimController() {
        return new AnimController(this);
    }

    @Override
    public void setModelIndex(@NotNull ModelIndex modelIndex) {
        this.modelIndex = modelIndex;
    }

    @NotNull
    @Override
    public BoneGroup getBones() {
        return new BoneGroup(this);
    }

    @NotNull
    @Override
    public Vec3 getWorldPosition(float v) {
        return SparkMathKt.toVec3(rootBody.getBody().getPosition());//pos
    }

    @Override
    public float getRootYRot(float v) {
        return 0;
    }

    @Override
    public Matrix4f getWorldPositionMatrix(float partialTick) {
        return new Matrix4f().translate(getWorldPosition(partialTick).toVector3f()).rotateZYX(SparkMathKt.toVector3f(rootBody.getBody().getQuaternion().toEuler()));
    }

    @Override
    public void syncMainAnimToClient() {

    }
}
