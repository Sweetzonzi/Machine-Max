package io.github.tt432.machinemax.common.part.old.ae86;

import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.entity.old.entity.OldPartEntity;
import io.github.tt432.machinemax.common.part.old.AbstractWheelPart;
import org.ode4j.math.DQuaternion;
import org.ode4j.math.DVector3;
import org.ode4j.math.DVector3C;
import org.ode4j.ode.OdeHelper;
import org.ode4j.ode.internal.DxGeom;
import net.minecraft.resources.ResourceLocation;

public class AE86WheelPart extends AbstractWheelPart {
    //模型资源参数
    public static final ResourceLocation PART_TEXTURE = ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "textures/entity/ae86_1.png");
    public static final ResourceLocation PART_MODEL = ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "entity/ae86/ae86_wheel");
    public static final ResourceLocation PART_ANIMATION = ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "entity/ae86/ae86_wheel.animation");
    public static final ResourceLocation PART_ANI_CONTROLLER = ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "entity/ae86/ae86_wheel.animation_controllers");
    //属性参数
    public static final double BASIC_HEALTH = 20;
    public static final double BASIC_ARMOR = 1;
    //物理参数
    public static final double BASIC_MASS = 100;
    public static final DVector3C airDragCentre = new DVector3(-0.1, 0, 0);//空气阻力/升力作用点(相对重心位置)
    public static final DVector3C waterDragCentre = new DVector3(-0.1, 0, 0);//水阻力/升力作用点(相对重心位置)

    public AE86WheelPart(OldPartEntity entity) {
        super(entity);
        //几何参数
        WHEEL_RADIUS = 26D / 2 / 16;
        WHEEL_WIDTH = 14D / 16;
        //模块化参数
        PART_TYPE = partTypes.WHEEL;
        //构建物理碰撞模型
        dmass.setCylinderTotal(BASIC_MASS, 1, WHEEL_RADIUS, WHEEL_WIDTH);
        dbody.setMass(dmass);

        dgeoms = new DxGeom[1];

        dgeoms[0] = OdeHelper.createCylinder(WHEEL_RADIUS, WHEEL_WIDTH);//创建一个平面朝向Z正方向的圆柱体
        dgeoms[0].setBody(dbody);//碰撞体绑定到运动体
        dgeoms[0].setOffsetQuaternion(DQuaternion.fromEulerDegrees(0, 90, 0));//默认创建的是一个立着的圆柱，转一下
        dgeoms[0].setOffsetPosition(0, 0, 0);//对齐碰撞体形状与模型形状
    }

    @Override
    public double getMass() {
        return BASIC_MASS;
    }

    @Override
    public void updateMass() {
        dmass.adjust(getMass());
    }

    @Override
    public double getArmor() {
        return BASIC_ARMOR;
    }

    @Override
    public double getBasicArmor() {
        return BASIC_ARMOR;
    }

    @Override
    public double getMaxHealth() {
        return BASIC_HEALTH;
    }

    @Override
    public ResourceLocation getModel() {
        return PART_MODEL;
    }

    @Override
    public ResourceLocation getTexture() {
        return PART_TEXTURE;
    }

    @Override
    public ResourceLocation getAnimation() {
        return PART_ANIMATION;
    }

    @Override
    public ResourceLocation getAniController() {
        return PART_ANI_CONTROLLER;
    }
}
