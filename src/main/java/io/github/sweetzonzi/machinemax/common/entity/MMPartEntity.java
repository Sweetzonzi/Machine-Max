package io.github.sweetzonzi.machinemax.common.entity;

import cn.solarmoon.spark_core.animation.IEntityAnimatable;
import cn.solarmoon.spark_core.animation.anim.play.AnimController;
import cn.solarmoon.spark_core.animation.anim.play.BoneGroup;
import cn.solarmoon.spark_core.animation.anim.play.ModelIndex;
import cn.solarmoon.spark_core.entity.attack.CollisionHurtData;
import cn.solarmoon.spark_core.molang.core.storage.IForeignVariableStorage;
import cn.solarmoon.spark_core.molang.core.storage.IScopedVariableStorage;
import cn.solarmoon.spark_core.molang.core.storage.ITempVariableStorage;
import cn.solarmoon.spark_core.molang.core.storage.VariableStorage;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.SparkMathKt;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.preinput.PreInput;
import cn.solarmoon.spark_core.skill.Skill;
import cn.solarmoon.spark_core.sync.EntitySyncerType;
import cn.solarmoon.spark_core.sync.IntSyncData;
import cn.solarmoon.spark_core.sync.SyncData;
import cn.solarmoon.spark_core.sync.SyncerType;
import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machinemax.MachineMax;
import io.github.sweetzonzi.machinemax.common.registry.MMEntities;
import io.github.sweetzonzi.machinemax.common.vehicle.Part;
import io.github.sweetzonzi.machinemax.common.vehicle.SubPart;
import io.github.sweetzonzi.machinemax.common.vehicle.VehicleCore;
import io.github.sweetzonzi.machinemax.common.vehicle.VehicleManager;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machinemax.mixin_interface.IEntityMixin;
import io.github.sweetzonzi.machinemax.mixin_interface.IProjectileMixin;
import io.github.sweetzonzi.machinemax.util.MMMath;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class MMPartEntity extends Entity implements IEntityAnimatable<MMPartEntity>, IEntityWithComplexSpawn {

    public Part part;//实体所属的部件
    public UUID vehicleUUID;
    public UUID partUUID;
    public AtomicReference<List<BoundingBox>> boundingBoxes = new AtomicReference<>(List.of());

    /**
     * 不应被使用！
     *
     * @param entityType 实体类型
     * @param level      实体加入的世界
     */
    public MMPartEntity(EntityType<? extends Entity> entityType, Level level) {
        super(entityType, level);
        this.blocksBuilding = false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {

    }

    public MMPartEntity(Level level, Part part) {
        super(MMEntities.getPART_ENTITY().get(), level);
        this.setNoGravity(true);
        this.part = part;
        this.setPos(SparkMathKt.toVec3(part.rootSubPart.body.getPhysicsLocation(null)));
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCount == 2) removeAllBodies();//移除SparkCore为实体添加的默认刚体
        if (this.part == null) {//如果实体没有所属的部件，则移除实体
            if (tickCount % 20 == 0) updatePart();
            else if (tickCount > 100) {//等待100tick用于同步部件信息
                MachineMax.LOGGER.warn("部件实体没有匹配的部件，已移除!");
                this.remove(RemovalReason.DISCARDED);
            }
        } else {
            //更新实体位置
            this.setPos(SparkMathKt.toVec3(part.rootSubPart.body.getPhysicsLocation(null)));
            Quaternionf q = SparkMathKt.toQuaternionf(part.rootSubPart.body.getPhysicsRotation(null));
            org.joml.Vector3f eulerAngles = new org.joml.Vector3f();
            q.getEulerAnglesZYX(eulerAngles).mul((float) (180 / Math.PI));
            this.setRot(-eulerAngles.y + 180f, eulerAngles.x);
            updateBoundingBox();//更新实体包围盒
        }
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        if (source.getDirectEntity() instanceof Projectile projectile) {
            //来自投射物的伤害处理
            IProjectileMixin mixinProjectile = (IProjectileMixin) projectile;
            SubPart hitSubPart = mixinProjectile.machine_Max$getHitSubPart();
            if (hitSubPart != null) {//如果命中了部件
                Vector3f normal = mixinProjectile.machine_Max$getHitNormal();
                Vector3f contactPoint = mixinProjectile.machine_Max$getHitPoint();
                long hitBoxId = mixinProjectile.machine_Max$getHitBoxId();
                return part.onHurt(source, amount, true, hitSubPart, normal,
                        PhysicsHelperKt.toBVector3f(projectile.getDeltaMovement().scale(20))
                                .subtract(hitSubPart.body.getLinearVelocity(null)), contactPoint, hitBoxId);
            } else return false;
        } else if (source.getSourcePosition() != null && source.getDirectEntity() instanceof Entity entity) {
            //来自其他实体的伤害处理
            PhysicsLevel level = level().getPhysicsLevel();
            Vector3f start;
            Vector3f end;
            if (entity instanceof LivingEntity livingEntity) {
                start = PhysicsHelperKt.toBVector3f(livingEntity.getEyePosition());
                end = PhysicsHelperKt.toBVector3f(
                                livingEntity.getViewVector(1).normalize()
                                        .scale(livingEntity.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE)))
                        .add(start);
            } else {
                start = PhysicsHelperKt.toBVector3f(source.getSourcePosition());
                end = PhysicsHelperKt.toBVector3f(this.position());
            }
            if (end.subtract(start).length() > 0) {
                var results = level.getWorld().rayTest(start, end);
                for (var result : results) {
                    PhysicsRigidBody body = (PhysicsRigidBody) result.getCollisionObject();
                    if (body.getOwner() instanceof SubPart subPart) {
                        //TODO: new一个新的source存储攻击来袭方向
                        Vector3f normal = result.getHitNormalLocal(null);
                        Vector3f contactPoint = start.add(end.subtract(start).mult(result.getHitFraction()));
                        //将伤害转发给部件进行操作
                        return subPart.part.onHurt(source, amount, true, subPart, normal, end.subtract(start).normalize(), contactPoint, subPart.collisionShape.findChild(result.triangleIndex()).getShape().nativeId());
                    }
                }
            } else {//射线长度有问题时的异常处理
                MachineMax.LOGGER.error("Damage source {} is too close to entity position, causing a zero-length ray.", entity);
            }
            return false;//未能命中任何部件碰撞箱则不处理伤害
        } else return false;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;//投射物命中判定交由物理引擎处理
    }

    public void updateBoundingBox() {
        List<BoundingBox> boxes = boundingBoxes.get();
        if (part != null && !boxes.isEmpty()) {
            Vector3f min = new Vector3f(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
            Vector3f max = new Vector3f(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE).mult(-1);
            for (BoundingBox box : boxes) {
                Vector3f tempMin = box.getMin(null);
                Vector3f tempMax = box.getMax(null);
                for (int i = 0; i < 3; i++) {
                    if (tempMax.get(i) > max.get(i)) max.set(i, tempMax.get(i));
                    if (tempMin.get(i) < min.get(i)) min.set(i, tempMin.get(i));
                }
            }
            AABB aabb = new AABB(min.get(0), min.get(1), min.get(2), max.get(0), max.get(1), max.get(2));
            if (!aabb.isInfinite() && !aabb.hasNaN() && min.get(0) < max.get(0) && min.get(1) < max.get(1) && min.get(2) < max.get(2))
                this.setBoundingBox(aabb);
            else setBoundingBox(new AABB(0, 0, 0, 0, 0, 0));
        }
    }

    @Override
    protected @NotNull Vec3 getPassengerAttachmentPoint(@NotNull Entity entity, @NotNull EntityDimensions dimensions, float partialTick) {
        if (entity instanceof LivingEntity livingEntity && ((IEntityMixin) livingEntity).machine_Max$getRidingSubsystem() instanceof SeatSubsystem seat)
            return SparkMathKt.toVec3(MMMath.localVectorToWorldVector(seat.seatLocator.subPartTransform.getTranslation(), seat.seatLocator.subPart.body));
        else return new Vec3(0, 0, 0);
    }

    @Override
    public @NotNull Vec3 getDismountLocationForPassenger(@NotNull LivingEntity passenger) {
        return super.getDismountLocationForPassenger(passenger);
    }

    @Override
    public void onPassengerTurned(@NotNull Entity entityToUpdate) {
        if (this.part != null && entityToUpdate instanceof LivingEntity livingEntity && ((IEntityMixin) livingEntity).machine_Max$getRidingSubsystem() instanceof SeatSubsystem seat) {
            entityToUpdate.setYBodyRot(180);
//            float f = Mth.wrapDegrees(entityToUpdate.getYRot() - this.getYRot());
//            float f1 = Mth.clamp(f, -105.0F, 105.0F);
//            entityToUpdate.yRotO += f1 - f;
//            entityToUpdate.setYRot(entityToUpdate.getYRot() + f1 - f);
//            entityToUpdate.setYRot(this.getYRot());
        }
    }

    @Override
    public void move(@NotNull MoverType type, @NotNull Vec3 pos) {
        //交由物理引擎处理
    }

    @Override
    public void setPos(double x, double y, double z) {
        this.setPosRaw(x, y, z);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compoundTag) {

    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compoundTag) {

    }

    @Override
    public boolean canCollideWith(@NotNull Entity entity) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Nullable
    @Override
    public CollisionHurtData getHurtData() {
        return null;
    }

    @Override
    public void pushHurtData(@Nullable CollisionHurtData collisionHurtData) {

    }

    @NotNull
    @Override
    public PhysicsLevel getPhysicsLevel() {
        return level().getPhysicsLevel();
    }

    @NotNull
    @Override
    public SyncerType getSyncerType() {
        return new EntitySyncerType();
    }

    @NotNull
    @Override
    public SyncData getSyncData() {
        return new IntSyncData(1);
    }

    @NotNull
    @Override
    public ModelIndex getModelIndex() {//返回部件的模型索引用于渲染
        if (part != null) return part.getModelIndex();
        else return IEntityAnimatable.super.getModelIndex();
    }

    @Override
    public MMPartEntity getAnimatable() {
        return this;
    }

    @NotNull
    @Override
    public AnimController getAnimController() {
        if (part == null) return new AnimController(this);
        else return part.getAnimController();
    }

    @NotNull
    @Override
    public BoneGroup getBones() {
        if (part != null) return part.getBones();
        else return new BoneGroup(this);
    }

    private void updatePart() {
        VehicleCore vehicle = VehicleManager.clientAllVehicles.get(vehicleUUID);
        if (vehicle != null && vehicle.level == this.level()) {
            this.part = vehicle.partMap.get(partUUID);//设置实体对应的部件
            if (part != null) {
                if (part.entity != null) part.entity.part = null;
                part.entity = this;
            }
        }
    }

    /**
     * 服务器创建实体时写入对应的部件UUID
     *
     * @param buffer 数据流
     */
    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        if (part != null) {
            buffer.writeBoolean(true);
            buffer.writeUUID(this.part.vehicle.uuid);
            buffer.writeUUID(this.part.uuid);
        } else buffer.writeBoolean(false);
    }

    /**
     * 客户端接收实体创建包时读取匹配的部件UUID，
     * 寻找并并设置实体对应的部件
     *
     * @param additionalData 数据流
     */
    @Override
    public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
        boolean hasPart = additionalData.readBoolean();
        if (hasPart) {
            vehicleUUID = additionalData.readUUID();
            partUUID = additionalData.readUUID();
            updatePart();
        } else this.remove(RemovalReason.DISCARDED);
    }

    @NotNull
    @Override
    public ITempVariableStorage getTempStorage() {
        if (part != null) return part.tempStorage;
        else return new VariableStorage();
    }

    @NotNull
    @Override
    public IScopedVariableStorage getScopedStorage() {
        if (part != null) return part.scopedStorage;
        else return new VariableStorage();
    }

    @NotNull
    @Override
    public IForeignVariableStorage getForeignStorage() {
        if (part != null) return part.foreignStorage;
        else return new VariableStorage();
    }

    @NotNull
    @Override
    public AtomicInteger getSkillCount() {
        return new AtomicInteger();
    }

    @NotNull
    @Override
    public ConcurrentHashMap<Integer, Skill> getAllSkills() {
        return new ConcurrentHashMap<>();
    }

    @NotNull
    @Override
    public ConcurrentHashMap<Integer, Skill> getPredictedSkills() {
        return getAllSkills();
    }

    @NotNull
    @Override
    public Level getAnimLevel() {
        return this.level();
    }

    @NotNull
    @Override
    public PreInput getPreInput() {
        return new PreInput(this);
    }
}
