package io.github.tt432.machinemax.common.part;

import cn.solarmoon.spark_core.animation.model.part.BonePart;
import cn.solarmoon.spark_core.animation.model.part.CubePart;
import cn.solarmoon.spark_core.phys.attached_body.AttachedBody;
import cn.solarmoon.spark_core.phys.thread.ThreadHelperKt;
import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.util.formula.IPartPhysParameters;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.ode4j.math.DQuaternion;
import org.ode4j.math.DVector3;
import org.ode4j.ode.DBody;
import org.ode4j.ode.DGeom;
import org.ode4j.ode.DMass;
import org.ode4j.ode.OdeHelper;

import java.util.ArrayList;

public class PartElement implements AttachedBody, IPartPhysParameters {

    ArrayList<DGeom> geoms = new ArrayList<>();
    DMass mass;
    @Getter
    DBody body;
    @Getter
    @Setter
    volatile int id;
    @Getter
    final String name;
    @Getter
    final Level level;
    @Getter
    final AbstractPart parentPart;
    /*物理运算相关参数*/
    //流体动力相关系数
    public DVector3 airDragCentre = new DVector3(0, 0, 0);//空气阻力/升力作用点(相对重心位置)
    public DVector3 waterDragCentre = new DVector3(0, 0, 0);//水阻力/升力作用点(相对重心位置)
    //TODO:浮力
    //TODO:摩擦力
    PartElement(String name, AbstractPart part, BonePart bone){
        this.name = name;
        this.level = part.getAttachedEntity().level();
        this.parentPart = part;
        id = -1;
        mass = OdeHelper.createMass();
        body = OdeHelper.createBody(ThreadHelperKt.getPhysWorld(level).getWorld());
        body.setGravityMode(false);
        body.disable();
    }

    //TODO:为body赋予质量
    protected void createMass(BonePart bone){
        String name = bone.getName();
        if(name.contains("mmMass_Box_")){
            name = name.replaceFirst("mmMass_Box_", "");
            double massValue = getMassValue(name);
            Vec3 size = bone.getCubes().getFirst().getSize();
            mass.setBoxTotal(massValue, size.x, size.y, size.z);
            body.setMass(mass);
        } else if (name.contains("mmMass_Sphere_")) {
            name = name.replaceFirst("mmMass_Sphere_", "");
            double massValue = getMassValue(name);
            Vec3 size = bone.getCubes().getFirst().getSize();
            mass.setSphereTotal(massValue, size.x);
            body.setMass(mass);
        } else if (name.contains("mmMass_Capsule_")) {

        } else if (name.contains("mmMass_Cylinder_")) {

        } else MachineMax.LOGGER.error("不支持的质量体形状: {}", name.replaceFirst("mmMass_", ""));
    }

    //TODO:根据骨骼名赋值Geoms的方法
    protected void createCollisionShape(BonePart bone){
        String name = bone.getName();
        if(name.contains("mmCollision_Box_")){
            for(CubePart cube : bone.getCubes()){
                Vector3f size = cube.getSize().toVector3f();
                DGeom geom = OdeHelper.createBox(size.x, size.y, size.z);
                Vector3f offset = cube.getTransformedCenter(new Matrix4f()).sub(bone.getPivot().toVector3f());
                geom.setOffsetPosition(offset.x, offset.y, offset.z);
                Vec3 rot = cube.getRotation();
                DQuaternion q = DQuaternion.fromEulerDegrees(rot.x, rot.y, rot.z);
                geom.setOffsetQuaternion(q);
                geoms.add(geom);
            }
        } else if (name.contains("mmCollision_Sphere_")) {
            for(CubePart cube : bone.getCubes()){
                Vector3f size = cube.getSize().toVector3f();
                DGeom geom = OdeHelper.createSphere(size.x);
                Vector3f offset = cube.getTransformedCenter(new Matrix4f()).sub(bone.getPivot().toVector3f());
                geom.setOffsetPosition(offset.x, offset.y, offset.z);
                geoms.add(geom);
            }
        } else if (name.contains("mmCollision_Capsule_")) {

        } else if (name.contains("mmCollision_Cylinder_")) {

        } else MachineMax.LOGGER.error("不支持的碰撞体形状: {}", name.replaceFirst("mmCollision_", ""));
    }

    protected double getMassValue(String name){
        double massValue = 1;
        // 使用正则表达式提取第一个数字部分
        String[] parts = name.split("_", 2);
        if (parts.length > 0 && parts[0].matches("\\d+(\\.\\d+)?")) {
            try {
                massValue = Double.parseDouble(parts[0]);
            } catch (NumberFormatException e) {
                MachineMax.LOGGER.error("无法解析质量值: {}", parts[0]);
            }
        } else {
            MachineMax.LOGGER.error("名称格式不正确: {}", name);
        }
        return massValue;
    }

    @Override
    public void enable() {
        getBody().enable();
    }

    @Override
    public void disable() {
        getBody().disable();
    }

}
