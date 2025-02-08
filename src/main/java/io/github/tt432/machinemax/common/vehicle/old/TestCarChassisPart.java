package io.github.tt432.machinemax.common.vehicle.old;

import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.entity.old.entity.OldPartEntity;
import io.github.tt432.machinemax.common.vehicle.old.slot.*;
import org.ode4j.math.DQuaternion;
import org.ode4j.math.DVector3;
import org.ode4j.math.DVector3C;
import org.ode4j.ode.OdeHelper;
import org.ode4j.ode.internal.DxGeom;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;

public class TestCarChassisPart extends AbstractPart {
    //模型资源参数
    public static final ResourceLocation PART_TEXTURE = ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "textures/entity/mini_ev.png");
    public static final ResourceLocation PART_MODEL = ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "entity/mini_ev/mini_ev_chassis");
    public static final ResourceLocation PART_ANIMATION = ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "entity/mini_ev/mini_ev_chassis.animation");
    public static final ResourceLocation PART_ANI_CONTROLLER = ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "entity/mini_ev/mini_ev_chassis.animation_controllers");
    //属性参数
    public static final double BASIC_HEALTH = 20;
    public static final double BASIC_ARMOR = 1;
    //物理参数
    public static final double BASIC_MASS = 400;
    public static final DVector3C airDragCentre = new DVector3(0, 0, -0.1);//空气阻力/升力作用点(相对重心位置)
    public static final DVector3C waterDragCentre = new DVector3(0, 0, -0.1);//水阻力/升力作用点(相对重心位置)

    public TestCarChassisPart(OldPartEntity attachedEntity) {
        super(attachedEntity);
        //模块化参数
        PART_TYPE = partTypes.CORE;
        PART_SLOT_NUM = 5;
        MOD_SLOT_NUM = 4;
        attachPoint = new DVector3(0, 0, 0);
        this.childrenPartSlots = new ArrayList<>(PART_SLOT_NUM);
        this.moduleSlots = new ArrayList<>(MOD_SLOT_NUM);
        for (int i = 0; i < PART_SLOT_NUM; i++) {//为车架部件设置部件安装槽
            this.childrenPartSlots.add(new UndefinedPartSlot(this));//占位的槽位类型，否则无法get/set
            switch (i) {
                case 0://右前轮
                    this.childrenPartSlots.set(0, new SteeringWheelPartSlot(this, "right_front_wheel",
                            new DVector3(-17.0569 / 16, 0.7075 / 16, 19.0756 / 16),
                            DQuaternion.fromEulerDegrees(0, 0, 0), 50000, 1000));
                    this.childrenPartSlots.get(0).attachPart(new TestCarWheelPart(this.getAttachedEntity()));
                    break;
                case 1://左前轮
                    this.childrenPartSlots.set(1, new SteeringWheelPartSlot(this, "left_front_wheel",
                            new DVector3(17.0569 / 16, 0.7075 / 16, 19.0756 / 16),
                            DQuaternion.fromEulerDegrees(0, 180, 0), 50000, 1000));
                    this.childrenPartSlots.get(1).attachPart(new TestCarWheelPart(this.getAttachedEntity()));
                    break;
                case 2://左后轮
                    this.childrenPartSlots.set(2, new WheelPartSlot(this, "left_back_wheel",
                            new DVector3(17.0569 / 16, 0.7075 / 16, -26.9244 / 16),
                            DQuaternion.fromEulerDegrees(0, 180, 0), 50000, 1000));
                    this.childrenPartSlots.get(2).attachPart(new TestCarWheelPart(this.getAttachedEntity()));
                    break;
                case 3://右后轮
                    this.childrenPartSlots.set(3, new WheelPartSlot(this, "right_back_wheel",
                            new DVector3(-17.0569 / 16, 0.7075 / 16, -26.9244 / 16),
                            DQuaternion.fromEulerDegrees(0, 0, 0), 50000, 1000));
                    this.childrenPartSlots.get(3).attachPart(new TestCarWheelPart(this.getAttachedEntity()));
                    break;
                case 4://车壳
                    this.childrenPartSlots.set(4, new HullPartSlot(this, "hull",
                            new DVector3(0, 10D / 16, -4D / 16),
                            DQuaternion.fromEulerDegrees(0, 0, 0)));
                    this.childrenPartSlots.get(4).attachPart(new TestCarHullPart(this.getAttachedEntity()));
                    break;
                default:
                    break;//什么也不做
            }
        }
        //构建物理碰撞模型
        dmass.setBoxTotal(BASIC_MASS, 40D / 16, 6D / 16, 72D / 16);
        dbody.setMass(dmass);

        dgeoms = new DxGeom[1];

        dgeoms[0] = OdeHelper.createBox(40D / 16, 6D / 16, 72D / 16);
        //dgeoms[0]=OdeHelper.createBox(40D/16,35D/16,72D/16);
        dgeoms[0].setBody(dbody);
        dgeoms[0].setOffsetPosition(0, (3.5D) / 16, 4D / 16);//对齐碰撞体形状与模型形状
        //dgeoms[0].setOffsetPosition(0,(3.5D+35D/2)/16,-4D/16);//对齐碰撞体形状与模型形状
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

    @Override
    public DVector3 getAerodynamicForceCoef(AbstractPart part) {
        //气动力相关系数
        double BASIC_AIRDRAG_COEF_ZP = 0.1;//空气阻力系数(前向)，一般较小
        double BASIC_AIRDRAG_COEF_ZN = 0.1;//空气阻力系数(后向)，一般较小
        double BASIC_AIRDRAG_COEF_XP = 0.2;//空气阻力系数(左向)
        double BASIC_AIRDRAG_COEF_XN = 0.2;//空气阻力系数(右向)
        double BASIC_AIRDRAG_COEF_YP = 0.2;//空气阻力系数(上向)
        double BASIC_AIRDRAG_COEF_YN = 0.4;//空气阻力系数(下向)
        double BASIC_AIRLIFT_COEF_Z = 0;//空气升力系数(前向)，形状带来的额外升力
        double BASIC_AIRLIFT_COEF_X = 0;//空气升力系数(水平向)，一般为0
        double BASIC_AIRLIFT_COEF_Y = 0;//空气升力系数(垂向)，一般为0
        DVector3 coef = new DVector3(BASIC_AIRLIFT_COEF_X, BASIC_AIRLIFT_COEF_Y, BASIC_AIRLIFT_COEF_Z);
        DVector3 vAbs = new DVector3();
        part.dbody.getRelPointVel(part.airDragCentre, vAbs);//获取升力作用点的绝对速度
        DVector3 vRel = new DVector3();
        part.dbody.vectorFromWorld(vAbs, vRel);//绝对速度转换为相对速度
        if (vRel.get0() > 0) {
            coef.add0(BASIC_AIRDRAG_COEF_XP);
        } else {
            coef.add0(BASIC_AIRDRAG_COEF_XN);
        }
        if (vRel.get1() > 0) {
            coef.add1(BASIC_AIRDRAG_COEF_YP);
        } else {
            coef.add1(BASIC_AIRDRAG_COEF_YN);
        }
        if (vRel.get2() > 0) {
            coef.add2(BASIC_AIRDRAG_COEF_ZP);
        } else {
            coef.add2(BASIC_AIRDRAG_COEF_ZN);
        }
        return coef;
    }
}
