package io.github.tt432.machinemax.common.entity;

import cn.solarmoon.spark_core.animation.IEntityAnimatable;
import cn.solarmoon.spark_core.animation.anim.play.AnimController;
import cn.solarmoon.spark_core.animation.anim.play.BoneGroup;
import cn.solarmoon.spark_core.animation.anim.play.ModelIndex;
import cn.solarmoon.spark_core.phys.thread.ThreadHelperKt;
import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.registry.MMRegistries;
import io.github.tt432.machinemax.common.vehicle.PartType;
import io.github.tt432.machinemax.common.vehicle.VehicleCore;
import io.github.tt432.machinemax.common.vehicle.port.AbstractPortPort;
import io.github.tt432.machinemax.common.vehicle.port.AttachPointPortPort;
import io.github.tt432.machinemax.common.registry.MMEntities;
import io.github.tt432.machinemax.common.vehicle.AbstractPart;
import io.github.tt432.machinemax.common.phys.body.ModelPartBody;
import io.github.tt432.machinemax.common.phys.thread.MMAbstractPhysLevel;
import io.github.tt432.machinemax.common.phys.thread.MMClientPhysLevel;
import io.github.tt432.machinemax.mixin_interface.IMixinClientLevel;
import io.github.tt432.machinemax.util.data.PosRot;
import io.github.tt432.machinemax.util.data.PosRotVel;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
import java.util.UUID;

public class MMPartEntity extends Entity implements IEntityWithComplexSpawn, IEntityAnimatable<MMPartEntity> {
    @Getter
    public AbstractPart part;//部件实体对应的部件
    public PartType partType;//部件类型
    @Getter
    @Setter
    private VehicleCore core;//本载具的控制核心
    @Getter
    private CoreEntity coreEntity;//本载具的控制核心实体
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
    //从保存的文件中新建时的初始化信息
    @Setter
    private PosRotVel initPosRotVel;
    @Setter
    private PosRot attachPosRot = new PosRot(new DVector3(), new DQuaternion(1,0,0,0));
    private UUID coreEntityUUID;
    @Getter
    @Setter
    private boolean initialized = false;

    final AnimController animController = new AnimController(this);

    /**
     * 基本的构造方法，不会创建部件，仅应被其他构造方法调用
     *
     * @param entityType
     * @param level
     */
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
        this.setPart(part);
        DVector3 pos = part.rootBody.getBody().getPosition().copy();
        this.setPos(pos.get0(), pos.get1(), pos.get2());
        //TODO:客户端部分
    }

    /**
     * 在世界中创建新的部件实体，不连接到已有实体
     *
     * @param type
     * @param level
     */
    public MMPartEntity(PartType type, Level level, CoreEntity coreEntity) {
        this(MMEntities.getPART_ENTITY().get(), level);
        setCoreEntity(coreEntity);
        this.createPart(this.getId(), type);
    }

    /**
     * 在世界中创建新的部件实体，连接到已有实体
     *
     * @param type
     * @param level
     * @param targetPort
     * @param partPortName
     */
    public MMPartEntity(PartType type, Level level, AbstractPortPort targetPort, String partPortName) {
        this(type, level, targetPort.getPortOwnerBody().getPart().getAttachedEntity().coreEntity);

        AbstractPortPort partPort = part.getBodyPorts().get(partPortName);
        part.rootBody.getBody().setPosition(targetPort.getWorldAttachPos(partPort));
        part.rootBody.getBody().setQuaternion(targetPort.getWorldAttachRot(partPort));

        if(targetPort instanceof AttachPointPortPort) partPort.attach((AttachPointPortPort) targetPort);
        else targetPort.attach((AttachPointPortPort) partPort);

        this.attachPosRot = new PosRot(
                new DVector3(part.rootBody.getBody().getPosition()),
                new DQuaternion(part.rootBody.getBody().getQuaternion())
        );
    }

    @Override
    public void tick() {
        if (firstTick) {
            if (part == null) return;
            if (this.coreEntityUUID != null) {
                if (this.level().isClientSide()) {
                    this.coreEntity = (CoreEntity) ((IMixinClientLevel) this.level()).machine_Max$getEntity(this.coreEntityUUID);
                } else {
                    this.coreEntity = (CoreEntity) ((ServerLevel) this.level()).getEntity(this.coreEntityUUID);
                }
                if (this.coreEntity == null) return;//等待核心实体创建完成
                this.initialized = true;//TODO:载具核心实体负责完成各个部件实体的初始化
            }
            if (initialized) {//所有子部件创建完成时
                if (initPosRotVel != null) {
                    part.rootBody.getBody().setPosition(initPosRotVel.pos());//读取部件位置
                    part.rootBody.getBody().setQuaternion(initPosRotVel.rot());//读取部件姿态
                    part.rootBody.getBody().setLinearVel(initPosRotVel.lVel());//读取部件平移速度
                    part.rootBody.getBody().setAngularVel(initPosRotVel.aVel());//读取部件角速度
                }
            } else {//还有子部件未创建完成时
                return;//等待子部件创建完成
            }
            //将部件加入同步列表
            ((MMAbstractPhysLevel) ThreadHelperKt.getPhysLevelById(this.level(), ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "main"))).syncParts.put(this.getId(), this.part);
            //将部件加入物理世界
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
            setYRot((float) heading.get1());
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
            for (ModelPartBody partBody : part.partBody.values()) {
                for (DGeom geom : partBody.getGeoms()) {
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
        partType =MMRegistries.getRegistryAccess(level()).registry(PartType.PART_REGISTRY_KEY).get().get(ResourceLocation.parse(compound.getString("part_type")));
        this.coreEntityUUID = compound.getUUID("core_entity_uuid");
        if (this.partType != null) {
            createPart(this.getId(), this.partType);//服务端重建部件
            //读取部件位置姿态速度角速度
            DVector3 pos = new DVector3(compound.getDouble("part_x"), compound.getDouble("part_y"), compound.getDouble("part_z"));
            DQuaternion dq = new DQuaternion(compound.getDouble("part_qw"), compound.getDouble("part_qx"), compound.getDouble("part_qy"), compound.getDouble("part_qz"));
            DVector3 lVel = new DVector3(compound.getDouble("part_lx"), compound.getDouble("part_ly"), compound.getDouble("part_lz"));
            DVector3 aVel = new DVector3(compound.getDouble("part_ax"), compound.getDouble("part_ay"), compound.getDouble("part_az"));
            initPosRotVel = new PosRotVel(pos, dq, lVel, aVel);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        if (part != null) {
            compound.putString("part_type", part.getType().getRegistryKey().toString());//存储部件类型
            compound.putUUID("core_entity_uuid", coreEntityUUID);//存储控制核心实体UUID
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
//        this.setPart(type.createPart(this.level()));
        part.setId(id);
        part.setCore(core);
        part.rootBody.getBody().setPosition(attachPosRot.pos());
        part.rootBody.getBody().setQuaternion(attachPosRot.rot());
        this.setId(id);
    }

    /**
     * 将部特殊数据写入实体生成数据包
     * 实体id
     * 是否是通过已有部件创建的实体
     * 部件类型
     *
     * @param buffer 实体生成数据包 The packet data stream
     */
    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(coreEntityUUID);//写入控制核心实体ID
        buffer.writeInt(this.getId());//写入实体ID
        buffer.writeBoolean(reCreateFromPart);
        buffer.writeResourceLocation(part.getType().getRegistryKey());
        buffer.writeDouble(attachPosRot.pos().get0());
        buffer.writeDouble(attachPosRot.pos().get1());
        buffer.writeDouble(attachPosRot.pos().get2());
        buffer.writeDouble(attachPosRot.rot().get0());
        buffer.writeDouble(attachPosRot.rot().get1());
        buffer.writeDouble(attachPosRot.rot().get2());
        buffer.writeDouble(attachPosRot.rot().get3());
        if (initPosRotVel == null)
            initPosRotVel = new PosRotVel(part.rootBody.getBody().getPosition(),
                    part.rootBody.getBody().getQuaternion(),
                    part.rootBody.getBody().getLinearVel(),
                    part.rootBody.getBody().getAngularVel());
        buffer.writeDouble(initPosRotVel.pos().get0());
        buffer.writeDouble(initPosRotVel.pos().get1());
        buffer.writeDouble(initPosRotVel.pos().get2());
        buffer.writeDouble(initPosRotVel.rot().get0());
        buffer.writeDouble(initPosRotVel.rot().get1());
        buffer.writeDouble(initPosRotVel.rot().get2());
        buffer.writeDouble(initPosRotVel.rot().get3());
        buffer.writeDouble(initPosRotVel.lVel().get0());
        buffer.writeDouble(initPosRotVel.lVel().get1());
        buffer.writeDouble(initPosRotVel.lVel().get2());
        buffer.writeDouble(initPosRotVel.aVel().get0());
        buffer.writeDouble(initPosRotVel.aVel().get1());
        buffer.writeDouble(initPosRotVel.aVel().get2());
    }

    /**
     * 从实体生成数据包读取实体信息并更新
     *
     * @param additionalData 实体生成数据包 The packet data stream
     */
    @Override
    public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
        coreEntityUUID = additionalData.readUUID();
        int id = additionalData.readInt();
        boolean reCreate = additionalData.readBoolean();
        ResourceLocation type = additionalData.readResourceLocation();
        this.attachPosRot = new PosRot(
                new DVector3(additionalData.readDouble(), additionalData.readDouble(), additionalData.readDouble()),
                new DQuaternion(additionalData.readDouble(), additionalData.readDouble(), additionalData.readDouble(), additionalData.readDouble())
        );
        this.initPosRotVel = new PosRotVel(
                new DVector3(additionalData.readDouble(), additionalData.readDouble(), additionalData.readDouble()),
                new DQuaternion(additionalData.readDouble(), additionalData.readDouble(), additionalData.readDouble(), additionalData.readDouble()),
                new DVector3(additionalData.readDouble(), additionalData.readDouble(), additionalData.readDouble()),
                new DVector3(additionalData.readDouble(), additionalData.readDouble(), additionalData.readDouble())
        );
        if (reCreate) {//若是通过已有部件创建的实体，则调用reCreateFromPart方法
            this.reCreateFromPart();
        } else {//若是完全新建的实体，则调用createPart方法
//            this.createPart(id, Objects.requireNonNull(PartType.PART_REGISTRY.get(type)));//客户端重建部件
        }
    }

    @Override
    public MMPartEntity getAnimatable() {
        return this;
    }

    @NotNull
    @Override
    public AnimController getAnimController() {
        if (part != null)
            return part.getAnimController();
        else return animController;
    }

    @NotNull
    @Override
    public ModelIndex getModelIndex() {
        //指向零件的modelData
        if(part == null) return new ModelIndex(
                ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "unknown"),
                ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "unknown")
        );
        else return part.getModelIndex();
    }

    @NotNull
    @Override
    public BoneGroup getBones() {
        return part.getBones();
    }

    public void setPart(@NotNull AbstractPart part) {
        if (this.part != null)
            this.part.removeAllBodiesFromLevel();//销毁原来的部件
        if (part.getId() != -1) this.setId(part.getId());
        this.part = part;
        this.part.setAttachedEntity(this);
        this.part.createMolangScope();
    }

    public void setCoreEntity(CoreEntity coreEntity) {
        this.coreEntity = coreEntity;
        this.coreEntityUUID = coreEntity.getUUID();
        this.core = coreEntity.core;
    }
}
