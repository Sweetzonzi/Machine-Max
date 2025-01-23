package io.github.tt432.machinemax.common.entity;

import cn.solarmoon.spark_core.animation.IEntityAnimatable;
import cn.solarmoon.spark_core.animation.anim.play.AnimController;
import cn.solarmoon.spark_core.animation.anim.play.BoneGroup;
import cn.solarmoon.spark_core.animation.anim.play.ModelIndex;
import cn.solarmoon.spark_core.phys.thread.ThreadHelperKt;
import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.part.slot.AbstractBodySlot;
import io.github.tt432.machinemax.common.registry.MMEntities;
import io.github.tt432.machinemax.common.part.AbstractPart;
import io.github.tt432.machinemax.common.registry.PartType;
import io.github.tt432.machinemax.common.sloarphys.body.AbstractPartBody;
import io.github.tt432.machinemax.common.sloarphys.body.ModelPartBody;
import io.github.tt432.machinemax.common.sloarphys.thread.MMAbstractPhysLevel;
import io.github.tt432.machinemax.common.sloarphys.thread.MMClientPhysLevel;
import io.github.tt432.machinemax.mixin_interface.IMixinClientLevel;
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
    public AbstractPart part;//部件实体对应的部件
    public MMPartEntity motherPartEntity;//本部件附着于的母部件实体
    public AbstractPartBody motherBody;//本部件附着于的母部件的零件
    public AbstractBodySlot motherSlot;//本部件附着于的母部件的槽位
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
    private UUID initMotherEntityUUID;
    private String initMotherPartBodyName = "";
    private String initMotherPartSlotName = "";
    private int childrenPartToCreate = 0;//仍需创建的子部件数量
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
        this.part = part;
        part.setAttachedEntity(this);
        part.createMolangScope();
        this.setId(part.getId());
        DVector3 pos = part.rootBody.getBody().getPosition().copy();
        this.setPos(pos.get0(), pos.get1(), pos.get2());
    }

    /**
     * 在世界中创建新的部件实体，不连接到已有实体
     *
     * @param type
     * @param level
     */
    public MMPartEntity(PartType type, Level level) {
        this(MMEntities.getPART_ENTITY().get(), level);
        this.createPart(this.getId(), type);
    }

    /**
     * 在世界中创建新的部件实体，连接到已有实体
     *
     * @param type
     * @param level
     * @param slot
     */
    public MMPartEntity(PartType type, Level level, AbstractBodySlot slot) {
        this(type, level);
        this.attachPart(slot.getSlotOwnerBody().getPart().getAttachedEntity(), slot.getSlotOwnerBody().getName(), slot.getName());
        this.initPosRotVel = new PosRotVel(
                new DVector3(part.rootBody.getBody().getPosition()),
                new DQuaternion(part.rootBody.getBody().getQuaternion()),
                new DVector3(part.rootBody.getBody().getLinearVel()),
                new DVector3(part.rootBody.getBody().getAngularVel())
        );
    }

    @Override
    public void tick() {
        if (firstTick) {
            if (part == null) return;
            if (!initialized && childrenPartToCreate == 0) {//所有子部件创建完成时
                checkInitialized();//检查自根部件到自身的所有部件是否都初始化完成
                //初始化部件连接关系
                if (initMotherEntityUUID != null && !initMotherPartBodyName.isEmpty() && !initMotherPartSlotName.isEmpty()) {
                    if (!this.level().isClientSide())
                        this.motherPartEntity = (MMPartEntity) ((ServerLevel) this.level()).getEntity(initMotherEntityUUID);
                    else
                        this.motherPartEntity = (MMPartEntity) ((IMixinClientLevel) this.level()).machine_Max$getEntity(initMotherEntityUUID);
                    if (this.motherPartEntity != null)
                        this.motherBody = motherPartEntity.part.partBody.get(initMotherPartBodyName);
                    else return;
                    if (this.motherBody != null)
                        this.motherSlot = motherBody.getBodySlots().get(initMotherPartSlotName);
                    else return;
                    this.attachPart(motherPartEntity, initMotherPartBodyName, initMotherPartSlotName);
                    //初始化部件运动体位姿
                    if (initPosRotVel != null) {
                        part.rootBody.getBody().setPosition(initPosRotVel.pos());//读取部件位置
                        part.rootBody.getBody().setQuaternion(initPosRotVel.rot());//读取部件姿态
                        part.rootBody.getBody().setLinearVel(initPosRotVel.lVel());//读取部件平移速度
                        part.rootBody.getBody().setAngularVel(initPosRotVel.aVel());//读取部件角速度
                    }
                    motherPartEntity.childrenPartToCreate--;
                }
            } else if (!initialized && childrenPartToCreate > 0) {//还有子部件未创建完成时
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
            //读取部件位置姿态速度
            DVector3 pos = new DVector3(compound.getDouble("part_x"), compound.getDouble("part_y"), compound.getDouble("part_z"));
            DQuaternion dq = new DQuaternion(compound.getDouble("part_qw"), compound.getDouble("part_qx"), compound.getDouble("part_qy"), compound.getDouble("part_qz"));
            DVector3 lVel = new DVector3(compound.getDouble("part_lx"), compound.getDouble("part_ly"), compound.getDouble("part_lz"));
            DVector3 aVel = new DVector3(compound.getDouble("part_ax"), compound.getDouble("part_ay"), compound.getDouble("part_az"));
            initPosRotVel = new PosRotVel(pos, dq, lVel, aVel);
            //读取部件连接关系
            if (compound.contains("mother_entity_uuid"))
                initMotherEntityUUID = UUID.fromString(compound.getString("mother_entity_uuid"));
            initMotherPartBodyName = compound.getString("mother_body_name");
            initMotherPartSlotName = compound.getString("mother_slot_name");
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        if (part != null) {
            compound.putString("part_type", part.getType().getRegistryKey().toString());//存储部件类型
            if (this.motherPartEntity != null && this.motherBody != null && this.motherSlot != null) {
                compound.putString("mother_entity_uuid", motherPartEntity.getUUID().toString());
                compound.putString("mother_body_name", motherBody.getName());
                compound.putString("mother_slot_name", motherSlot.getName());
            }
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

    protected void attachPart(MMPartEntity motherPartEntity, String motherBodyName, String motherSlotName) {
        this.motherPartEntity = motherPartEntity;
        this.motherBody = motherPartEntity.part.partBody.get(motherBodyName);
        this.motherSlot = motherBody.getBodySlots().get(motherSlotName);
        //连接部件
        this.motherSlot.attachBody(part.rootBody);
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
        buffer.writeInt(this.getId());//写入实体ID
        buffer.writeBoolean(reCreateFromPart);
        buffer.writeResourceLocation(part.getType().getRegistryKey());
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
        if (this.motherPartEntity != null) {
            buffer.writeBoolean(true);
            buffer.writeUUID(motherPartEntity.getUUID());
            buffer.writeUtf(motherBody.getName());
            buffer.writeUtf(motherSlot.getName());
            buffer.writeInt(childrenPartToCreate);
        } else buffer.writeBoolean(false);
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
        this.initPosRotVel = new PosRotVel(
                new DVector3(additionalData.readDouble(), additionalData.readDouble(), additionalData.readDouble()),
                new DQuaternion(additionalData.readDouble(), additionalData.readDouble(), additionalData.readDouble(), additionalData.readDouble()),
                new DVector3(additionalData.readDouble(), additionalData.readDouble(), additionalData.readDouble()),
                new DVector3(additionalData.readDouble(), additionalData.readDouble(), additionalData.readDouble())
        );
        boolean attached = additionalData.readBoolean();
        if (reCreate) {//若是通过已有部件创建的实体，则调用reCreateFromPart方法
            this.reCreateFromPart();
        } else {//若是完全新建的实体，则调用createPart方法
            this.createPart(id, Objects.requireNonNull(PartType.PART_TYPE.getRegistry().get().get(type)));
            if (attached) {//若应被安装于某个槽位，则调用attachPart方法，保存母体及安装槽信息并连接部件
                this.initMotherEntityUUID = additionalData.readUUID();
                this.initMotherPartBodyName = additionalData.readUtf();
                this.initMotherPartSlotName = additionalData.readUtf();
                this.childrenPartToCreate = additionalData.readInt();
            }
        }
    }

    private void checkInitialized() {
        if (this.motherPartEntity !=null){
            if(this.childrenPartToCreate != 0) initialized = false;
            else {
                motherPartEntity.checkInitialized();
                if(motherPartEntity.initialized) this.initialized = true;
            }
        } else {
            if(this.childrenPartToCreate != 0) initialized = false;
            else this.initialized = true;
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
