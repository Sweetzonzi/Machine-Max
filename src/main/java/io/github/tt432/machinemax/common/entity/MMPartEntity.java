package io.github.tt432.machinemax.common.entity;

import cn.solarmoon.spark_core.animation.IEntityAnimatable;
import cn.solarmoon.spark_core.animation.anim.play.AnimController;
import cn.solarmoon.spark_core.animation.anim.play.BoneGroup;
import cn.solarmoon.spark_core.animation.anim.play.ModelIndex;
import cn.solarmoon.spark_core.entity.attack.CollisionHurtData;
import cn.solarmoon.spark_core.physics.SparkMathKt;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.skill.SkillGroup;
import cn.solarmoon.spark_core.skill.SkillInstance;
import cn.solarmoon.spark_core.sync.EntitySyncerType;
import cn.solarmoon.spark_core.sync.IntSyncData;
import cn.solarmoon.spark_core.sync.SyncData;
import cn.solarmoon.spark_core.sync.SyncerType;
import com.jme3.bounding.BoundingBox;
import com.jme3.math.Vector3f;
import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.registry.MMEntities;
import io.github.tt432.machinemax.common.vehicle.Part;
import io.github.tt432.machinemax.common.vehicle.SubPart;
import io.github.tt432.machinemax.common.vehicle.VehicleCore;
import io.github.tt432.machinemax.common.vehicle.VehicleManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;

public class MMPartEntity extends LivingEntity implements IEntityAnimatable<MMPartEntity>, IEntityWithComplexSpawn, IMMPartEntityAttribute {

    public Part part;//实体所属的部件
    public UUID vehicleUUID;
    public UUID partUUID;

    /**
     * 不应被使用！
     *
     * @param entityType 实体类型
     * @param level      实体加入的世界
     */
    public MMPartEntity(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
    }

    public MMPartEntity(Level level, Part part) {
        super(MMEntities.getPART_ENTITY().get(), level);
        this.setNoGravity(true);
        this.part = part;
        this.setPos(SparkMathKt.toVec3(part.rootSubPart.body.getPhysicsLocation(null)));
        MachineMax.LOGGER.info("创建部件实体: " + this);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.part == null) {//如果实体没有所属的部件，则移除实体
            if (tickCount % 20 == 0) updatePart();
            else if (tickCount > 100) {//等待100tick用于同步部件信息
                MachineMax.LOGGER.warn("部件实体没有匹配的部件，已移除!");
                this.remove(RemovalReason.DISCARDED);
            }
        } else {
            //更新实体位置
            this.deathTime = 0;
            this.setPos(SparkMathKt.toVec3(part.rootSubPart.body.getPhysicsLocation(null)));
            updateBoundingBox();//更新实体包围盒
        }
    }

    public void updateBoundingBox() {
        if (part != null) {
            Vector3f min = new Vector3f(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
            Vector3f max = new Vector3f(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE).mult(-1);
            for (SubPart subPart : part.subParts.values()) {
                BoundingBox temp = subPart.body.boundingBox(null);
                Vector3f tempMin = temp.getMin(null);
                Vector3f tempMax = temp.getMax(null);
                for (int i = 0; i < 3; i++) {
                    if (tempMax.get(i) > max.get(i)) max.set(i, tempMax.get(i));
                    if (tempMin.get(i) < min.get(i)) min.set(i, tempMin.get(i));
                }
            }
            AABB aabb = new AABB(min.get(0), min.get(1), min.get(2), max.get(0), max.get(1), max.get(2));
            if (!aabb.isInfinite() && !aabb.hasNaN())
                this.setBoundingBox(aabb);
            else setBoundingBox(new AABB(0, 0, 0, 0, 0, 0));
        }
    }

    @Override
    public void move(@NotNull MoverType type, @NotNull Vec3 pos) {
        //交由物理引擎处理
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public @NotNull Iterable<ItemStack> getArmorSlots() {
        return Collections.singleton(ItemStack.EMPTY);
    }

    @Override
    public @NotNull ItemStack getItemBySlot(@NotNull EquipmentSlot equipmentSlot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(@NotNull EquipmentSlot equipmentSlot, @NotNull ItemStack itemStack) {

    }

    @Override
    public @NotNull HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
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

    @Nullable
    @Override
    public SkillGroup getActiveSkillGroup() {
        return null;
    }

    @Override
    public void setActiveSkillGroup(@Nullable SkillGroup skillGroup) {

    }

    @NotNull
    @Override
    public Set<SkillInstance> getActiveSkills() {
        return Set.of();
    }

    @NotNull
    @Override
    public LinkedHashMap<ResourceLocation, SkillGroup> getSkillGroups() {
        return LinkedHashMap.newLinkedHashMap(1);
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
}
