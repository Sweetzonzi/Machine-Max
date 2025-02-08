package io.github.tt432.machinemax.common.entity;

import io.github.tt432.machinemax.common.vehicle.PartType;
import io.github.tt432.machinemax.common.vehicle.VehicleCore;
import io.github.tt432.machinemax.common.registry.MMEntities;
import io.github.tt432.machinemax.mixin_interface.IMixinClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

import java.util.Collections;
import java.util.UUID;

public class CoreEntity extends LivingEntity implements IMMEntityAttribute, IEntityWithComplexSpawn {

    public VehicleCore core;
    public MMPartEntity rootPartEntity;
    //初始化属性
    private UUID initRootPartEntityUUID;

    public CoreEntity(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
        setNoGravity(true);
        noPhysics = true;
    }

    /**
     * 创建核心实体，并创建指定类型的部件实体作为载具的根部件
     * 仅应在服务端被调用
     *
     * @param type
     * @param level
     */
    public CoreEntity(PartType type, Level level) {
        this(MMEntities.getCORE_ENTITY().get(), level);
        var entity = new MMPartEntity(type, level, this);
        this.initRootPartEntityUUID = entity.getUUID();//记录载具的根部件实体的UUID，同步给客户端后统一根据UUID指定根部件实体
        level.addFreshEntity(entity);
    }

    @Override
    public void tick() {
        if (firstTick) {
            if (initRootPartEntityUUID != null) {
                //设置载具核心的根部件实体
                if (this.level().isClientSide()) {
                    rootPartEntity = (MMPartEntity) ((IMixinClientLevel) level()).machine_Max$getEntity(initRootPartEntityUUID);
                } else {
                    rootPartEntity = (MMPartEntity) ((ServerLevel) level()).getEntity(initRootPartEntityUUID);
                }
                if (rootPartEntity == null|| rootPartEntity.isInitialized()) return;//等待根部件实体初始化完成

            }
        }
        if (this.rootPartEntity != null && !this.rootPartEntity.isRemoved()) this.setPos(rootPartEntity.getPosition(1));
        else this.remove(RemovalReason.DISCARDED);
        super.tick();
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("initRootPartEntityUUID"))
            this.initRootPartEntityUUID = compound.getUUID("initRootPartEntityUUID");
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putUUID("initRootPartEntityUUID", initRootPartEntityUUID);
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(initRootPartEntityUUID);
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
        this.initRootPartEntityUUID = additionalData.readUUID();
    }

    @Override
    public void move(MoverType type, Vec3 pos) {
        //屏蔽原版move方法，交由物理引擎控制
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return Collections.singleton(ItemStack.EMPTY);
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {

    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }


}
