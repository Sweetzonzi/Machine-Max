package io.github.tt432.machinemax.common.part;

import cn.solarmoon.spark_core.animation.model.part.BonePart;
import cn.solarmoon.spark_core.animation.model.part.CubePart;
import cn.solarmoon.spark_core.animation.model.part.Locator;
import cn.solarmoon.spark_core.phys.SparkMathKt;
import cn.solarmoon.spark_core.phys.attached_body.AttachedBody;
import cn.solarmoon.spark_core.phys.thread.PhysLevel;
import cn.solarmoon.spark_core.phys.thread.ThreadHelperKt;
import cn.solarmoon.spark_core.registry.common.SparkVisualEffects;
import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.part.slot.AbstractElementSlot;
import io.github.tt432.machinemax.common.part.slot.FixedElementSlot;
import io.github.tt432.machinemax.util.data.PosRot;
import io.github.tt432.machinemax.util.formula.IPartPhysParameters;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.ode4j.math.DMatrix3;
import org.ode4j.math.DQuaternion;
import org.ode4j.math.DVector3;
import org.ode4j.ode.*;
import org.ode4j.ode.internal.DxBox;
import org.ode4j.ode.internal.Rotation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static io.github.tt432.machinemax.util.ModelBoneHelper.getLocatorValue;
import static org.ode4j.ode.OdeConstants.dContactBounce;
import static org.ode4j.ode.OdeConstants.dContactRolling;

//TODO:设为抽象类，并创建一些变体，如：
// 1. 直接指定形状、碰撞箱等参数的零件
// 2. 特殊碰撞功能的零件，如刀刃、引信
public class PartElement implements AttachedBody, IPartPhysParameters {

    ArrayList<DGeom> geoms = new ArrayList<>();
    volatile DMass mass;
    @Getter
    DBody body;
    @Getter
    final String name;
    @Getter
    final Level level;
    /*模块化相关参数*/
    @Getter
    final HashMap<String, PosRot> parentElementAttachPoints = HashMap.newHashMap(2);//可用连接点及其相对零件质心的坐标与旋转
    @Getter
    volatile HashMap<String, AbstractElementSlot> parentElementAttachSlots;//本零件安装于的槽位
    @Getter
    final HashMap<String, AbstractElementSlot> elementSlots = HashMap.newHashMap(2);//本零件的零件安装槽
    @Getter
    final AbstractPart part;//零件所属部件
    @Getter
    @Setter
    volatile PartElement motherElement;//本零件附着于的零件(唯一，遵循骨骼结构)
    /*物理运算相关参数*/
    //流体动力相关系数
    public DVector3 airDragCentre = new DVector3();//空气阻力/升力作用点(相对重心位置)
    public DVector3 waterDragCentre = new DVector3();//水阻力/升力作用点(相对重心位置)

    //TODO:浮力
    //TODO:摩擦力
    public PartElement(String name, AbstractPart part, BonePart bone) {
        this.name = name;
        this.level = part.getAttachedEntity().level();
        this.part = part;
        mass = OdeHelper.createMass();
        body = OdeHelper.createBody(name, this, false, getPhysLevel().getPhysWorld().getWorld());
        body.disable();
        body.onPhysTick(this::onPhysTick);
        //创建零件连接点(可以是多个)
        parentElementAttachPoints.put("MassCenter", new PosRot(new DVector3(), new DQuaternion().setIdentity()));//质心作为默认的公有的连接点
        for(HashMap.Entry<String, Locator> entry : bone.getLocators().entrySet()){
            String locatorName = entry.getKey();
            Locator locator = entry.getValue();
            if (locatorName.startsWith("mmAttachPoint_")){
                String AttachPointName = locatorName.replaceFirst("mmAttachPoint_", "");
                DVector3 AttachPointPos = SparkMathKt.toDVector3(locator.getOffset());
                DQuaternion AttachPointRot = DQuaternion.fromEulerDegrees(SparkMathKt.toDVector3(locator.getRotation()));
                parentElementAttachPoints.put(AttachPointName, new PosRot(AttachPointPos, AttachPointRot));
            }
        }
    }


    private void onCollide(DGeom dGeom, DContactBuffer dContacts) {
        var b = dGeom.getBody();
        var o = b.getOwner();
        if (o instanceof PartElement && ((PartElement) o).part == part) return;//同部件内的碰撞体忽略相互碰撞
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
    protected void createMass(BonePart bone) {
        //创建质量体所需的数据
        String name = bone.getName();
        double massValue = 1;
        double radius = 1;
        double length = 1;
        Vec3 size = new Vec3(1, 1, 1);
        DVector3 rotation = new DVector3(0, 0, 0);
        int direction = 0;
        //读取骨骼包含的所有locator，从locator的名字与数据读取创建质量体所需的数据
        for (HashMap.Entry<String, Locator> entry : bone.getLocators().entrySet()) {
            String locatorName = entry.getKey();
            Locator locator = entry.getValue();
            Object value = getLocatorValue(locatorName, locator);
            if (value == null) continue;
            if (locatorName.startsWith("mm")) {
                if (locatorName.startsWith("mmRotation_")) {
                    rotation = (DVector3) value;
                } else {
                    MachineMax.LOGGER.error("未知的Locator关键词: {}", locatorName);
                }
            } else if (locatorName.startsWith("mass_")) {
                massValue = (double) value;
            } else if (locatorName.startsWith("radius_")) {
                radius = (double) value;
            } else if (locatorName.startsWith("length_")) {
                length = (double) value;
            } else if (locatorName.startsWith("direction_")) {
                direction = (int) value;
            } else {
                MachineMax.LOGGER.error("不支持的Locator关键词: {}", locatorName);
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
    protected void createCollisionShape(BonePart bone) {
        String name = bone.getName();
        DGeom geom;
        for (CubePart cube : bone.getCubes()) {
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
            geom.setOffsetQuaternion(DQuaternion.fromEulerDegrees(SparkMathKt.toDVector3(bone.getCubes().getFirst().getRotation())));
            geom.onCollide(this::onCollide);//设置碰撞回调
            geoms.add(geom);
        }
    }

    /**
     * 根据给定骨骼数据，创建零件安装槽
     *
     * @param bone
     */
    protected void createElementSlots(BonePart bone) {
        //创建安装槽所需的数据
        String name = bone.getName();
        DVector3 childElementAttachPos = SparkMathKt.toDVector3(bone.getPivot());
        DQuaternion childElementAttachRot = DQuaternion.fromEulerDegrees(SparkMathKt.toDVector3(bone.getRotation()));
        PosRot childElementAttachPoint = new PosRot(childElementAttachPos, childElementAttachRot);
        //读取骨骼包含的所有locator，从locator的名字与数据读取创建安装槽所需的数据
        for (HashMap.Entry<String, Locator> entry : bone.getLocators().entrySet()) {
            String locatorName = entry.getKey();
            Locator locator = entry.getValue();
            Object value = getLocatorValue(locatorName, locator);
            if (value == null) continue;
            if (locatorName.startsWith("mm")) {
                //TODO:槽位关节属性值，如旋转轴、角度限制
            }
        }
        //根据骨骼名和先前读取到的数据，创建零件安装槽
        //TODO:把安装槽位注册表化后，允许此方法创建任意类型的安装槽
        if (name.startsWith("mmSlot_Fixed_")) {
            name = name.replaceFirst("mmSlot_Fixed_", "");
            this.getElementSlots().put(name, new FixedElementSlot(name, this, childElementAttachPoint));
        } else if (name.startsWith("mmSlot_Ball_")) {

        } else if (name.startsWith("mmSlot_DoubleBall_")) {

        } else if (name.startsWith("mmSlot_Hinge_")) {

        } else if (name.startsWith("mmSlot_Hinge2_")) {

        } else if (name.startsWith("mmSlot_Piston_")) {

        }
    }

    @Override
    public void enable() {
        getBody().enable();
    }

    @Override
    public void disable() {
        getBody().disable();
    }

    @NotNull
    @Override
    public PhysLevel getPhysLevel() {
        return ThreadHelperKt.getPhysLevelById(level, MachineMax.MOD_ID);
    }
}
