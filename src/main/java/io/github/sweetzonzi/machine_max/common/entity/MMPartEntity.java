package io.github.sweetzonzi.machine_max.common.entity;

import cn.solarmoon.spark_core.animation.IEntityAnimatable;
import cn.solarmoon.spark_core.animation.anim.AnimController;
import cn.solarmoon.spark_core.animation.model.ModelController;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.util.BlackBoard;
import cn.solarmoon.spark_core.util.PPhase;
import cn.solarmoon.spark_core.util.SparkMathKt;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Matrix3f;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.registry.MMEntities;
import io.github.sweetzonzi.machine_max.common.vehicle.*;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import io.github.sweetzonzi.machine_max.mixin_interface.IProjectileMixin;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;
import net.neoforged.neoforge.event.EventHooks;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class MMPartEntity extends VehicleEntity implements IEntityAnimatable<MMPartEntity>, IEntityWithComplexSpawn {

    public SubPart subPart;//实体所属的零件
    public UUID vehicleUUID;
    public UUID partUUID;
    public String subPartName;
    public AtomicReference<BoundingBox> boundingBox = new AtomicReference<>();

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
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
    }

    @Override
    protected @NotNull Item getDropItem() {
        return ItemStack.EMPTY.getItem();
    }

    public MMPartEntity(Level level, SubPart subPart) {
        super(MMEntities.getPART_ENTITY().get(), level);
        this.setNoGravity(true);
        this.subPart = subPart;
        this.subPartName = subPart.name;
        this.partUUID = subPart.part.uuid;
        this.vehicleUUID = subPart.part.vehicle.uuid;
        this.setPos(SparkMathKt.toVec3(subPart.body.getPhysicsLocation(null)));
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCount == 2) {//移除SparkCore为实体添加的默认碰撞箱刚体
            var body = getPhysicsBody("body");
            if (body != null) {
                PhysicsBodyExtensionKt.removePhysicsBody(level(), body);
            }
        }
        if (this.subPart == null) {//如果实体没有所属的部件，则移除实体
            if (!this.isRemoved()) {
                if (tickCount % 20 == 0) updatePart();
                else if (tickCount > 100) {//等待100tick用于同步部件信息
                    MachineMax.LOGGER.warn("部件实体没有匹配的部件，已移除!");
                    this.remove(RemovalReason.DISCARDED);
                }
            }
        } else {
            //更新实体位置
            this.setPos(SparkMathKt.toVec3(subPart.body.getPhysicsLocation(null)));
            Quaternionf q = SparkMathKt.toQuaternionf(subPart.body.getPhysicsRotation(null));
            // 从四元数提取前向向量
            org.joml.Vector3f forward = new org.joml.Vector3f(0, 0, 1).rotate(q);
            // 计算yaw和pitch
            float yaw = -(float) Math.toDegrees(Math.atan2(forward.x, forward.z)) + 180;
            float pitch = (float) Math.toDegrees(Math.asin(forward.y));
            yaw = yaw % 360;
            if (yaw > 180) yaw -= 360;
            else if (yaw < -180) yaw += 360;
            // 设置实体旋转
            this.setRot(yaw, pitch);
            updateBoundingBox();//更新实体包围盒
        }
    }

    @Override
    public boolean isAlwaysTicking() {
        return subPart != null && !subPart.isRemoved;
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        if (this.subPart == null) return false;
        if (source.getDirectEntity() instanceof Projectile projectile) {
            //来自投射物的伤害处理
            IProjectileMixin mixinProjectile = (IProjectileMixin) projectile;
            SubPart hitSubPart = mixinProjectile.machine_Max$getHitSubPart();
            if (hitSubPart == this.subPart) {//如果命中了部件
                Vector3f normal = mixinProjectile.machine_Max$getHitNormal();
                Vector3f contactPoint = mixinProjectile.machine_Max$getHitPoint();
                HitBox hitBox = mixinProjectile.machine_Max$getHitBox();
                return subPart.part.onHurt(source, amount, null, hitSubPart, normal,
                        PhysicsHelperKt.toBVector3f(projectile.getDeltaMovement().scale(20))
                                .subtract(hitSubPart.body.getLinearVelocity(null)), contactPoint, hitBox);
            } else return false;
        } else if (source.getSourcePosition() != null && source.getDirectEntity() instanceof Entity entity) {
            //来自其他实体的伤害处理
            PhysicsLevel level = level().getPhysicsLevel();
            Vector3f start;
            Vector3f end;
            if (entity instanceof LivingEntity livingEntity && livingEntity.isAlive() && !livingEntity.isRemoved()) {
                start = PhysicsHelperKt.toBVector3f(livingEntity.getEyePosition());
                end = PhysicsHelperKt.toBVector3f(
                                livingEntity.getViewVector(1).normalize()
                                        .scale(5))
                        .add(start);
            } else {
                start = PhysicsHelperKt.toBVector3f(source.getSourcePosition());
                end = PhysicsHelperKt.toBVector3f(this.position());
            }
            if (end.subtract(start).length() > 0) {
                if (source.is(DamageTypes.EXPLOSION) || source.is(DamageTypes.PLAYER_EXPLOSION)) {
                    //范围伤害处理
                    SubPart nearest = null;
                    float nearestDistance = Float.MAX_VALUE;
                    Vector3f normal = new Vector3f();
                    Vector3f contactPoint = new Vector3f();
                    Vector3f delta = PhysicsBodyExtensionKt.stateOf(subPart.body).getTransform().getTranslation().subtract(start);
                    float d = delta.length();
                    if (d < nearestDistance) {
                        nearest = subPart;
                        contactPoint = PhysicsBodyExtensionKt.stateOf(subPart.body).getTransform().getTranslation();
                        normal = delta.multLocal(-1).normalize();
                        nearestDistance = d;
                    }
                    if (nearest != null) {
                        HitBox hitBox = null;
                        float maxThickness = -1;
                        for (String hitBoxName : nearest.attr.hitBoxNames.values()) {
                            if (subPart.hitBoxes.get(hitBoxName).getRHA(subPart) > maxThickness)
                                hitBox = subPart.hitBoxes.get(hitBoxName);
                        }
                        return subPart.part.onHurt(source, amount, null, nearest, normal, normal.mult(-1), contactPoint, hitBox);
                    } else throw new IllegalStateException("No subpart found for explosion damage.");
                } else {//一般伤害处理
                    var results = level.getWorld().rayTest(start, end);
                    for (var result : results) {
                        PhysicsRigidBody body = (PhysicsRigidBody) result.getCollisionObject();
                        if (PhysicsBodyExtensionKt.getOwner(body) instanceof SubPart subPart) {
                            //TODO: new一个新的source存储攻击来袭方向
                            Vector3f normal = result.getHitNormalLocal(null);
                            Vector3f contactPoint = start.add(end.subtract(start).mult(result.getHitFraction()));
                            HitBox hitBox = subPart.getHitBox(result.triangleIndex());
                            //将伤害转发给部件进行操作
                            return subPart.part.onHurt(source, amount, null, subPart, normal, end.subtract(start).normalize(), contactPoint, hitBox);
                        }
                    }
                }
            } else {//射线长度有问题时的异常处理
                MachineMax.LOGGER.error("Damage source {} is too close to entity position, causing a zero-length ray.", entity);
            }
            return false;//未能命中任何部件碰撞箱则不处理伤害
        } else return false;
    }

    @Override
    public Component getDisplayName() {
        if (subPart != null)
            return Component.translatable(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, subPart.name).toLanguageKey());
        return super.getDisplayName();
    }

    @Override
    public @NotNull Component getName() {
        if (subPart != null)
            return Component.translatable(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, subPart.name).toLanguageKey());
        return super.getName();
    }

    @Override
    public boolean canBeHitByProjectile() {
        return true;//投射物命中判定交由物理引擎处理
    }

    public void updateBoundingBox() {
        BoundingBox bb = boundingBox.get();
        if (bb == null) return;
        AABB aabb = SparkMathKt.toAABB(bb);
        if (!aabb.isInfinite() && !aabb.hasNaN())
            this.setBoundingBox(aabb);
        else setBoundingBox(new AABB(0, 0, 0, 0, 0, 0));
    }

    @Override
    protected @NotNull Vec3 getPassengerAttachmentPoint(@NotNull Entity entity, @NotNull EntityDimensions dimensions, float partialTick) {
        var subsystem = ((IEntityMixin) entity).machine_Max$getControllingSubsystem();
        if (entity instanceof LivingEntity && subsystem instanceof SeatSubsystem seat) {
            Vector3f rawRelPos = seat.getSeatPointLocalTransform().getTranslation();
            Matrix3f pose = seat.getSeatPointWorldTransform().getRotation().toRotationMatrix();
            Vector3f relPos = pose.mult(rawRelPos, null);
            return SparkMathKt.toVec3(relPos);
        } else return new Vec3(0, 0, 0);
    }

    @Override
    public @NotNull Vec3 getDismountLocationForPassenger(@NotNull LivingEntity passenger) {
        return super.getDismountLocationForPassenger(passenger);
    }

    @Override
    public void onPassengerTurned(@NotNull Entity entityToUpdate) {
        if (this.subPart != null && entityToUpdate instanceof LivingEntity livingEntity && ((IEntityMixin) livingEntity).machine_Max$getControllingSubsystem() instanceof SeatSubsystem) {
            float rot = Mth.wrapDegrees(livingEntity.getYRot() - this.getYRot() + 180f);
            entityToUpdate.setYBodyRot(rot);
            livingEntity.yHeadRotO = rot;
            livingEntity.setYHeadRot(rot);
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

    @NotNull
    @Override
    public PhysicsLevel getPhysicsLevel() {
        return level().getPhysicsLevel();
    }

    @Override
    public MMPartEntity getAnimatable() {
        return this;
    }

    @Override
    public @NotNull AnimController getAnimController() {
        if (subPart == null) return new AnimController(this);
        else return subPart.getAnimController();
    }

    private void updatePart() {
        VehicleCore vehicle = VehicleManager.clientAllVehicles.get(vehicleUUID);
        if (vehicle != null && vehicle.level == this.level()) {
            Part part = vehicle.partMap.get(partUUID);
            if (part != null) {
                this.subPart = part.getSubParts().get(subPartName);//设置实体对应的部件
                if (subPart != null) {
                    if (subPart.entity != null) subPart.entity.subPart = null;
                    subPart.entity = this;
                }
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
        if (subPart != null) {
            buffer.writeBoolean(true);
            buffer.writeUUID(this.subPart.part.vehicle.uuid);
            buffer.writeUUID(this.subPart.part.uuid);
            buffer.writeUtf(this.subPart.name);
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
            subPartName = additionalData.readUtf();
            updatePart();
        } else this.remove(RemovalReason.DISCARDED);
    }

    @NotNull
    @Override
    public Level getAnimLevel() {
        return this.level();
    }

    @NotNull
    @Override
    public BlackBoard getHurtData() {
        return new BlackBoard();
    }

    @NotNull
    @Override
    public ModelController getModelController() {
        if (subPart != null) return subPart.getModelController();
        else return new ModelController(this);
    }
}
