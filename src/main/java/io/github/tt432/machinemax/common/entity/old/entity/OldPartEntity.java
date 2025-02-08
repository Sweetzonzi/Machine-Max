package io.github.tt432.machinemax.common.entity.old.entity;

import io.github.tt432.eyelib.capability.EyelibAttachableData;
import io.github.tt432.machinemax.common.entity.old.controller.PhysController;
import io.github.tt432.machinemax.common.vehicle.old.AbstractPart;
import org.ode4j.math.DQuaternion;
import org.ode4j.math.DVector3;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;

public abstract class OldPartEntity extends Entity {

    @Setter
    @Getter
    private PhysController controller = new PhysController(this);//实体指定的控制器，默认为基础控制器
    @Setter
    @Getter
    private controlMode mode = OldPartEntity.controlMode.GROUND;//采用的控制模式，决定接收的按键输入方案
    public AbstractPart corePart;//实体连接的核心部件
    @Setter
    @Getter
    private volatile boolean controllerHandled;//控制器是否已在单帧物理计算中生效
    @Setter
    @Getter
    private float ZRot;

    public enum controlMode {
        GROUND,
        SHIP,
        PLANE,
        MECH
    }

    public OldPartEntity(EntityType<? extends OldPartEntity> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
    }

    @Override
    public void tick() {
        if (firstTick) {//完成实体初始化后将运动体位姿与实体同步，并加入物理计算线程中
            if (corePart != null) {
                setPos(this.getX(), this.getY(), this.getZ());//将所有部件的位置同步到物理计算线程
                setRot(this.getXRot(), this.getYRot(), this.getZRot());//将所有部件的姿态同步到物理计算线程
                for (AbstractPart part : this.corePart) part.addAllGeomsToSpace();//将所有部件添加进碰撞空间
                if(this.level().isClientSide()){
                    for (AbstractPart part : this.corePart) part.molangScope.getScope().setParent(part.getAttachedEntity().getData(EyelibAttachableData.RENDER_DATA).getScope());
                }
            }
        }
//        this.syncPoseToMainThread();//将实体位姿与物理计算结果同步
        super.tick();
    }

    /**
     * 将主线程和物理线程中的实体及其子部件传送到新的位置
     *
     * @param x x坐标(m)
     * @param y y坐标(m)
     * @param z z坐标(m)
     */
    @Override
    public void setPos(double x, double y, double z) {
        setPos(new DVector3(x, y, z));
        super.setPos(x, y, z);
    }

    public void setPos(DVector3 v) {
        if (this.getController() != null) controller.setPositionEnqueue(v);
    }

    /**
     * 设置主线程和物理线程中实体及其子部件的姿态
     *
     * @param pitch 俯仰角(deg)
     * @param yaw   偏航角(deg)
     * @param roll  滚转角(deg)
     */
    public void setRot(double pitch, double yaw, double roll) {
        setRot(DQuaternion.fromEulerDegrees(pitch, yaw, roll));
    }

    public void setRot(DQuaternion q) {
        if (this.getController() != null) controller.setRotationEnqueue(q);
        DVector3 ang = q.toEulerDegrees();
        setXRot((float) ang.get0());
        setYRot((float) ang.get1());
        setZRot((float) ang.get2());
    }

    @Override
    public boolean canCollideWith(Entity pEntity) {
        return canVehicleCollide(this, pEntity);
    }

    public static boolean canVehicleCollide(Entity pVehicle, Entity pEntity) {
        return (pEntity.canBeCollidedWith() || pEntity.isPushable()) && !pVehicle.isPassengerOfSameVehicle(pEntity);
    }

}
