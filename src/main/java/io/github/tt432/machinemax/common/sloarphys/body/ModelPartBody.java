package io.github.tt432.machinemax.common.sloarphys.body;

import cn.solarmoon.spark_core.animation.model.origin.OBone;
import cn.solarmoon.spark_core.animation.model.origin.OCube;
import cn.solarmoon.spark_core.animation.model.origin.OLocator;
import cn.solarmoon.spark_core.phys.SparkMathKt;
import cn.solarmoon.spark_core.registry.common.SparkVisualEffects;
import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.part.AbstractPart;
import io.github.tt432.machinemax.common.part.port.AttachPointPortPort;
import io.github.tt432.machinemax.common.part.port.FixedPartPort;
import io.github.tt432.machinemax.util.data.PosRot;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.ode4j.math.DMatrix3;
import org.ode4j.math.DQuaternion;
import org.ode4j.math.DVector3;
import org.ode4j.ode.*;
import org.ode4j.ode.internal.DxBox;
import org.ode4j.ode.internal.Rotation;

import java.util.HashMap;

import static io.github.tt432.machinemax.util.ModelBoneHelper.getLocatorValue;
import static org.ode4j.ode.OdeConstants.dContactBounce;
import static org.ode4j.ode.OdeConstants.dContactRolling;

public class ModelPartBody extends AbstractPartBody {

    public ModelPartBody(String name, AbstractPart part) {
        super(name, part);
    }

    @Override
    protected void onTick() {
        super.onTick();
    }


    protected void onCollide(DGeom dGeom, DContactBuffer dContacts) {
        var b = dGeom.getBody();
        var o = b.getOwner();
        if (o instanceof AbstractPartBody || o instanceof BlockBody) {//仅与其他零件或地形碰撞
            if (b == this.getBody() || o == this.getBody().getOwner() || OdeHelper.areConnected(this.getBody(), b))
                return;//防止同一部件内的零件碰撞
            if(o instanceof AbstractPartBody && ((AbstractPartBody) o).getPart().core == this.part.core)
                return;//防止同一载具内的零件碰撞
            for (DContact contact : dContacts) {
                //TODO:读取材料，获取摩擦系数
                contact.surface.mode = dContactBounce | dContactRolling;
                contact.surface.mu = 500;
                contact.surface.rho = 1;
                contact.surface.bounce = 0.0001;
                contact.surface.bounce_vel = 0.1;
            }
            dContacts.createJoint();
        }
    }

    /**
     * 运动体在每次物理线程迭代时执行的方法
     */
    protected void onPhysTick() {
        if (level.isClientSide()) {//调试模式下绘制碰撞箱
            for (DGeom geom : geoms) {
                if (geom instanceof DxBox)
                    SparkVisualEffects.getGEOM().getRenderableBox(geom.getUUID().toString()).refresh(geom, false);
            }
        }
    }

    /**
     * 根据读取的骨骼数据，为零件创建质量和转动惯量数据
     *
     * @param bone
     */
    public void createMass(OBone bone) {
        //创建质量体所需的数据
        String name = bone.getName();
        double massValue = 1;
        double radius = 1;
        double length = 1;
        Vec3 size = new Vec3(1, 1, 1);
        DVector3 rotation = new DVector3(0, 0, 0);
        int direction = 0;
        //读取骨骼包含的所有OLocator，从OLocator的名字与数据读取创建质量体所需的数据
        for (HashMap.Entry<String, OLocator> entry : bone.getLocators().entrySet()) {
            String OLocatorName = entry.getKey();
            OLocator OLocator = entry.getValue();
            Object value = getLocatorValue(OLocatorName, OLocator);
            if (value == null) continue;
            if (OLocatorName.startsWith("rotation_")) {
                rotation = (DVector3) value;
            } else if (OLocatorName.startsWith("mass_")) {
                massValue = (double) value;
            } else if (OLocatorName.startsWith("radius_")) {
                radius = (double) value;
            } else if (OLocatorName.startsWith("length_")) {
                length = (double) value;
            } else if (OLocatorName.startsWith("direction_")) {
                direction = (int) value;
            } else {
                MachineMax.LOGGER.error("不支持的Locator关键词: {}", OLocatorName);
            }
        }
        if (!bone.getCubes().isEmpty() && name.startsWith("mmMass_Box_")) {//对于盒装质量体，取bone内第一个cube的尺寸作为质量体的尺寸
            size = bone.getCubes().getFirst().getSize();
            rotation = SparkMathKt.toDVector3(bone.getCubes().getFirst().getRotation());
        }
        //根据骨骼名和先前读取到的数据，创建不同类型的质量体
        if (name.startsWith("mmMass_Box_")) {
            mass.setBoxTotal(massValue, size.x, size.y, size.z);
            DMatrix3 R = new DMatrix3().setIdentity();
            Rotation.dRFromEulerAngles(R, rotation.get0(), rotation.get1(), rotation.get2());
            mass.rotate(R);
            body.setMass(mass);
        } else if (name.startsWith("mmMass_Sphere_")) {
            mass.setSphereTotal(massValue, radius);
            body.setMass(mass);
        } else if (name.startsWith("mmMass_Capsule_")) {
            mass.setCapsuleTotal(massValue, direction, radius, length);
            DMatrix3 R = new DMatrix3().setIdentity();
            Rotation.dRFromEulerAngles(R, rotation.get0(), rotation.get1(), rotation.get2());
            body.setMass(mass);
        } else if (name.startsWith("mmMass_Cylinder_")) {
            mass.setCylinderTotal(massValue, direction, radius, length);
            DMatrix3 R = new DMatrix3().setIdentity();
            Rotation.dRFromEulerAngles(R, rotation.get0(), rotation.get1(), rotation.get2());
            body.setMass(mass);
        } else MachineMax.LOGGER.error("不支持的质量体形状: {}", name.replaceFirst("mmMass_", ""));
    }

    /**
     * 从给定骨骼读取骨骼名称和元素组成，创建碰撞体
     *
     * @param bone
     */
    public void createCollisionShape(OBone bone) {
        String name = bone.getName();
        DGeom geom;
        for (OCube cube : bone.getCubes()) {
            if (name.startsWith("mmCollision_Box_")) {//创建盒状碰撞体
                geom = OdeHelper.createBox(SparkMathKt.toDVector3(cube.getSize()));
            } else if (name.startsWith("mmCollision_Sphere_")) {//创建球状碰撞体
                Vec3 size = cube.getSize();
                geom = OdeHelper.createSphere(size.x);
            } else if (name.startsWith("mmCollision_Capsule_")) {//创建胶囊状碰撞体
                Vector3f size = cube.getSize().toVector3f();
                geom = OdeHelper.createCapsule(size.x, size.y);//半径，长度
            } else if (name.startsWith("mmCollision_Cylinder_")) {//创建圆柱状碰撞体
                Vector3f size = cube.getSize().toVector3f();
                geom = OdeHelper.createCylinder(size.x, size.y);//半径，高度
            } else {
                throw new RuntimeException("不支持的碰撞体形状: " + name.replaceFirst("mmCollision_", ""));
            }
            geom.setBody(body);
            geom.setOffsetPosition(SparkMathKt.toDVector3(cube.getTransformedCenter(new Matrix4f()).sub(bone.getPivot().toVector3f())));
            geom.setOffsetQuaternion(DQuaternion.fromEuler(SparkMathKt.toDVector3(cube.getRotation())));
            geom.onCollide(this::onCollide);//设置碰撞回调
            geoms.add(geom);
        }
    }

    /**
     * 根据给定骨骼数据，创建零件安装槽
     *
     * @param bone
     */
    public void createPartBodyPorts(OBone bone) {
        //创建安装槽所需的数据
        String name = bone.getName();
        DVector3 childElementAttachPos = SparkMathKt.toDVector3(bone.getPivot());
        DQuaternion childElementAttachRot = DQuaternion.fromEuler(SparkMathKt.toDVector3(bone.getRotation()));
        PosRot childElementAttachPoint = new PosRot(childElementAttachPos, childElementAttachRot);
        //读取骨骼包含的所有OLocator，从OLocator的名字与数据读取创建安装槽所需的数据
        for (HashMap.Entry<String, OLocator> entry : bone.getLocators().entrySet()) {
            String locatorName = entry.getKey();
            OLocator locator = entry.getValue();
            Object value = getLocatorValue(locatorName, locator);
            if (value == null) continue;
            if (locatorName.startsWith("mm")) {
                //TODO:槽位关节属性值，如旋转轴、角度限制
            }
        }
        //根据骨骼名和先前读取到的数据，创建零件安装槽
        //TODO:把安装槽位注册表化后，允许此方法创建任意类型的安装槽
        if (name.startsWith("mmPort_AttachPoint_")) {
            name = name.replaceFirst("mmPort_AttachPoint_", "");
            this.getPartPorts().put(name, new AttachPointPortPort(name, this, childElementAttachPoint));
            //TODO:仅将与外部零件连接的对接口同步到部件对接口列表
            part.getBodyPorts().put(name, this.getPartPorts().get(name));//将对接口同步到部件
        } else if (name.startsWith("mmPort_Fixed_")) {
            name = name.replaceFirst("mmPort_Fixed_", "");
            this.getPartPorts().put(name, new FixedPartPort(name, this, childElementAttachPoint));
            part.getBodyPorts().put(name, this.getPartPorts().get(name));//将对接口同步到部件
        } else if (name.startsWith("mmPort_Ball_")) {

        } else if (name.startsWith("mmPort_DoubleBall_")) {

        } else if (name.startsWith("mmPort_Hinge_")) {

        } else if (name.startsWith("mmPort_Hinge2_")) {

        } else if (name.startsWith("mmPort_Piston_")) {

        }
    }

}
