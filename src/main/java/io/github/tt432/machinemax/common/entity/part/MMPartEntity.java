package io.github.tt432.machinemax.common.entity.part;

import cn.solarmoon.spark_core.phys.thread.ThreadHelperKt;
import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.entity.AbstractPartEntity;
import io.github.tt432.machinemax.common.entity.MMEntities;
import io.github.tt432.machinemax.common.part.AbstractPart;
import io.github.tt432.machinemax.common.sloarphys.body.ModelPartBody;
import io.github.tt432.machinemax.common.part.TestCubePart;
import io.github.tt432.machinemax.common.sloarphys.thread.MMAbstractPhysLevel;
import io.github.tt432.machinemax.common.sloarphys.thread.MMClientPhysLevel;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;
import org.jetbrains.annotations.NotNull;
import org.ode4j.math.DQuaternion;
import org.ode4j.math.DVector3;
import org.ode4j.ode.DAABB;
import org.ode4j.ode.DAABBC;
import org.ode4j.ode.DGeom;

public class MMPartEntity extends Entity implements IEntityWithComplexSpawn {
    public AbstractPart part;
    @Setter
    @Getter
    private float ZRot;
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYRot;
    private double lerpXRot;
    private boolean fromExistingPart = false;//是否是通过已有部件创建的实体

    public MMPartEntity(Level level){
        super(MMEntities.MM_PART_ENTITY.get(), level);
    }

    public MMPartEntity(EntityType<? extends MMPartEntity> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
    }

    public MMPartEntity(Level level, AbstractPart part) {
        super(MMEntities.MM_PART_ENTITY.get(), level);
        this.part = part;
        DVector3 pos = part.rootElement.getBody().getPosition().copy();
        this.setPos(pos.get0(), pos.get1(), pos.get2());
        fromExistingPart = true;
    }

    @Override
    public void tick() {
        if (firstTick) {
            if (part == null) return;//若部件为空，则不进行初始化
            //将部件加入同步列表
            part.setId(this.getId());
            ((MMAbstractPhysLevel) ThreadHelperKt.getPhysLevelById(this.level(), MachineMax.MOD_ID)).syncParts.put(this.getId(), this.part);
            //初始化部件运动体位姿
            part.rootElement.getBody().setPosition(this.getX(), this.getY(), this.getZ());
            part.addAllElementsToLevel();
        }
        this.syncPoseToMainThread();
        this.updateBoundingBox();
        super.tick();
    }

    /***
     * 将物理计算线程中的实体位姿同步到游戏主线程
     */
    public void syncPoseToMainThread() {
        if (part != null) {//将实体位姿与物理计算结果同步
            DVector3 pos = part.rootElement.getBody().getPosition().copy();
            this.setPosRaw(pos.get0(), pos.get1(), pos.get2());
            DQuaternion dq = part.rootElement.getBody().getQuaternion().copy();
            DVector3 heading = dq.toEulerDegrees();
            setXRot((float) heading.get0());
            setYRot(-(float) heading.get1());
            setZRot((float) heading.get2());
            this.setBoundingBox(this.makeBoundingBox());
        }
    }

    @Override
    public void move(@NotNull MoverType type, @NotNull Vec3 pos) {
        //运动交由物理引擎处理，原版运动留空
    }

    @Override
    public boolean isPickable() {//不是是否可拾取而是是否可被选中
        return !this.isRemoved();
    }

    @Override
    public boolean hurt(DamageSource pSource, float pAmount) {
        this.remove(RemovalReason.KILLED);
        return true;
    }

    @Override
    public void onRemovedFromLevel() {
        //将部件从同步列表移除
        ((MMAbstractPhysLevel) ThreadHelperKt.getPhysLevelById(this.level(), MachineMax.MOD_ID)).syncParts.remove(this.getId());
        if (this.level().isClientSide())
            ((MMClientPhysLevel) ThreadHelperKt.getPhysLevelById(this.level(), MachineMax.MOD_ID)).partNoInfoCount.remove(this.getId());
        //移除部件
        part.removeAllElementsFromLevel();
        super.onRemovedFromLevel();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {

    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {

    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {

    }

    public void updateBoundingBox() {
        if (part != null) {
            DVector3 min = new DVector3(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE);
            DVector3 max = new DVector3(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE).scale(-1);
            for (ModelPartBody partElement : part.partElements.values()) {
                for (DGeom geom : partElement.getGeoms()) {
                    DAABBC temp = geom.getAABB();
                    for (int i = 0; i < 3; i++) {
                        if (temp.getMax(i) > max.get(i)) max.set(i, temp.getMax(i));
                        if (temp.getMin(i) < min.get(i)) min.set(i, temp.getMin(i));
                    }
                }
            }
            DAABB daabb = new DAABB();
            daabb.setMin(min);
            daabb.setMax(max);
            if (daabb.isValid())
                this.setBoundingBox(new AABB(min.get0(), min.get1(), min.get2(), max.get0(), max.get1(), max.get2()));
            else setBoundingBox(new AABB(0, 0, 0, 0, 0, 0));
        }
    }

    @Override
    public void lerpTo(double pX, double pY, double pZ, float pYRot, float pXRot, int pSteps) {
        this.lerpX = pX;
        this.lerpY = pY;
        this.lerpZ = pZ;
        this.lerpYRot = pYRot;
        this.lerpXRot = pXRot;
        this.lerpSteps = 10;
    }

    @Override
    public double lerpTargetX() {
        return this.lerpSteps > 0 ? this.lerpX : this.getX();
    }

    @Override
    public double lerpTargetY() {
        return this.lerpSteps > 0 ? this.lerpY : this.getY();
    }

    @Override
    public double lerpTargetZ() {
        return this.lerpSteps > 0 ? this.lerpZ : this.getZ();
    }

    @Override
    public float lerpTargetXRot() {
        return this.lerpSteps > 0 ? (float) this.lerpXRot : this.getXRot();
    }

    @Override
    public float lerpTargetYRot() {
        return this.lerpSteps > 0 ? (float) this.lerpYRot : this.getYRot();
    }

    /**
     * 将部件ID写入实体生成数据包
     *
     * @param buffer 实体生成数据包 The packet data stream
     */
    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        //TODO:若是通过已有部件创建的实体，则反过来将部件id同步至实体
//        buffer.writeInt(this.getId());//写入实体ID
    }

    /**
     * 从实体生成数据包读取部件ID并更新
     *
     * @param additionalData 实体生成数据包 The packet data stream
     */
    @Override
    public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
//        //TODO:若是通过已有部件创建的实体，则反过来将部件id同步至实体
//        if (part != null) {//设置部件ID
//            part.setId(additionalData.readInt());
//        } else {
//            MachineMax.LOGGER.error("{} Part is null", this);
//        }
    }
}
