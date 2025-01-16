package io.github.tt432.machinemax.common.entity.part;

import cn.solarmoon.spark_core.animation.IEntityAnimatable;
import cn.solarmoon.spark_core.animation.anim.play.AnimController;
import cn.solarmoon.spark_core.animation.anim.play.BoneGroup;
import cn.solarmoon.spark_core.animation.anim.play.ModelIndex;
import cn.solarmoon.spark_core.phys.thread.ThreadHelperKt;
import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.registry.MMEntities;
import io.github.tt432.machinemax.common.part.AbstractPart;
import io.github.tt432.machinemax.common.registry.PartType;
import io.github.tt432.machinemax.common.sloarphys.body.ModelPartBody;
import io.github.tt432.machinemax.common.sloarphys.thread.MMAbstractPhysLevel;
import io.github.tt432.machinemax.common.sloarphys.thread.MMClientPhysLevel;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
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

import java.util.Objects;

public class MMPartEntity extends Entity implements IEntityWithComplexSpawn, IEntityAnimatable<MMPartEntity> {
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
    public boolean reCreateFromPart = false;//是否是通过已有部件创建的实体

    final AnimController animController = new AnimController(this);

    public MMPartEntity(EntityType<? extends MMPartEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
    }

    /**
     * 从已有的部件创建部件实体，未完成，请勿使用
     *
     * @param level
     * @param part
     */
    public MMPartEntity(Level level, AbstractPart part) {
        this(MMEntities.getPART_ENTITY().get(), level);
        reCreateFromPart = true;
        this.part = part;
        part.setAttachedEntity(this);
        part.createMolangScope();
        this.setId(part.getId());
        DVector3 pos = part.rootBody.getBody().getPosition().copy();
        this.setPos(pos.get0(), pos.get1(), pos.get2());
    }

    public MMPartEntity(PartType type, Level level) {
        this(MMEntities.getPART_ENTITY().get(), level);
        this.createPart(this.getId(), type);
    }

    @Override
    public void tick() {
        if (firstTick) {
            if (part == null) return;//若部件为空，则不进行初始化
            //将部件加入同步列表
            ((MMAbstractPhysLevel) ThreadHelperKt.getPhysLevelById(this.level(), ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "main"))).syncParts.put(this.getId(), this.part);
            //初始化部件运动体位姿
            part.rootBody.getBody().setPosition(this.getX(), this.getY(), this.getZ());
            part.addAllBodiesToLevel();
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
            DVector3 pos = part.rootBody.getBody().getPosition().copy();
            this.setPosRaw(pos.get0(), pos.get1(), pos.get2());
            DQuaternion dq = part.rootBody.getBody().getQuaternion().copy();
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
        if (part != null) {
            part.setAttachedEntity(null);
            //将部件从同步列表移除
            ((MMAbstractPhysLevel) ThreadHelperKt.getPhysLevelById(this.level(), ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "main"))).syncParts.remove(this.getId());
            if (this.level().isClientSide())
                ((MMClientPhysLevel) ThreadHelperKt.getPhysLevelById(this.level(), ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "main"))).partNoInfoCount.remove(this.getId());
            //移除部件
            part.removeAllBodiesFromLevel();
        }
        super.onRemovedFromLevel();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {

    }



    public void updateBoundingBox() {
        if (part != null) {
            DVector3 min = new DVector3(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE);
            DVector3 max = new DVector3(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE).scale(-1);
            for (ModelPartBody partElement : part.partBody.values()) {
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

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        PartType type = PartType.PART_TYPE.getRegistry().get().get(ResourceLocation.parse(compound.getString("part_type")));
        if (type != null) {
            createPart(this.getId(), type);//重建部件
            DVector3 pos = new DVector3(compound.getDouble("part_x"), compound.getDouble("part_y"), compound.getDouble("part_z"));
            DQuaternion dq = new DQuaternion(compound.getDouble("part_qw"), compound.getDouble("part_qx"), compound.getDouble("part_qy"), compound.getDouble("part_qz"));
            DVector3 lVel = new DVector3(compound.getDouble("part_lx"), compound.getDouble("part_ly"), compound.getDouble("part_lz"));
            DVector3 aVel = new DVector3(compound.getDouble("part_ax"), compound.getDouble("part_ay"), compound.getDouble("part_az"));
            part.rootBody.getBody().setPosition(pos);//读取部件位置
            part.rootBody.getBody().setQuaternion(dq);//读取部件姿态
            part.rootBody.getBody().setLinearVel(lVel);//读取部件平移速度
            part.rootBody.getBody().setAngularVel(aVel);//读取部件角速度
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        if(part!= null){
            compound.putString("part_type", part.getType().getRegistryKey().toString());//存储部件类型
            DVector3 pos = part.rootBody.getBody().getPosition().copy();//存储部件位置
            compound.putDouble("part_x", pos.get0());
            compound.putDouble("part_y", pos.get1());
            compound.putDouble("part_z", pos.get2());
            DQuaternion dq = part.rootBody.getBody().getQuaternion().copy();//存储部件姿态
            compound.putDouble("part_qw", dq.get0());
            compound.putDouble("part_qx", dq.get1());
            compound.putDouble("part_qy", dq.get2());
            compound.putDouble("part_qz", dq.get3());
            DVector3 lVel = part.rootBody.getBody().getLinearVel().copy();//存储部件平移速度
            compound.putDouble("part_lx", lVel.get0());
            compound.putDouble("part_ly", lVel.get1());
            compound.putDouble("part_lz", lVel.get2());
            DVector3 aVel = part.rootBody.getBody().getAngularVel().copy();//存储部件角速度
            compound.putDouble("part_ax", aVel.get0());
            compound.putDouble("part_ay", aVel.get1());
            compound.putDouble("part_az", aVel.get2());
        }
    }

    protected void reCreateFromPart() {
        //TODO,用来替换第二个构造方法中的内容
    }

    protected void createPart(int id, PartType type) {
        this.part = type.createPart(this.level());
        part.setId(id);
        part.setAttachedEntity(this);
        part.createMolangScope();
    }

    /**
     * 将部特殊数据写入实体生成数据包
     * 实体id
     * 是否是通过已有部件创建的实体
     * 部件类型
     * @param buffer 实体生成数据包 The packet data stream
     */
    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        buffer.writeInt(this.getId());//写入实体ID
        buffer.writeBoolean(reCreateFromPart);
        buffer.writeResourceLocation(part.getType().getRegistryKey());
    }

    /**
     * 从实体生成数据包读取实体信息并更新
     *
     * @param additionalData 实体生成数据包 The packet data stream
     */
    @Override
    public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
        int id = additionalData.readInt();
        boolean reCreate = additionalData.readBoolean();
        ResourceLocation type = additionalData.readResourceLocation();
        if (reCreate) {//若是通过已有部件创建的实体，则调用reCreateFromPart方法
            this.reCreateFromPart();
        } else {//若是完全新建的实体，则调用createPart方法
            this.createPart(id, Objects.requireNonNull(PartType.PART_TYPE.getRegistry().get().get(type)));
            MachineMax.LOGGER.info("type:" +type);
        }
    }

    @Override
    public MMPartEntity getAnimatable() {
        return this;
    }

    @NotNull
    @Override
    public AnimController getAnimController() {
        return part.getAnimController();
    }

    @NotNull
    @Override
    public ModelIndex getModelIndex() {
        //指向零件的modelData
        return part.getModelIndex();
    }

    @NotNull
    @Override
    public BoneGroup getBones() {
        return part.getBones();
    }
}
