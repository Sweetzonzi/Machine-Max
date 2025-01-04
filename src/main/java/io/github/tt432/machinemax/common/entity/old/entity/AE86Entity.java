package io.github.tt432.machinemax.common.entity.old.entity;

import io.github.tt432.machinemax.common.entity.old.controller.AE86Controller;
import io.github.tt432.machinemax.common.part.old.ae86.AE86ChassisPart;
import org.ode4j.math.DVector3;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class AE86Entity extends OldPartEntity {

    public AE86Entity(EntityType<? extends OldPartEntity> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
        this.corePart = new AE86ChassisPart(this);
        this.setController(new AE86Controller(this));
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity entity, EntityDimensions dimensions, float partialTick) {
        return new Vec3(0.5, 0.7, 0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {

    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide() && this.getFirstPassenger() instanceof Player p) {
            DVector3 v = new DVector3();
            corePart.dbody.vectorFromWorld(corePart.dbody.getLinearVel().copy(), v);
            p.displayClientMessage(Component.literal("速度:" + String.format("%.2f", v.get2() * 3.6) + "km/h"), true);
            this.level().addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(), 0, 0, 0);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {

    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {

    }

    @Override
    public @NotNull InteractionResult interact(Player pPlayer, InteractionHand pHand) {
        InteractionResult interactionresult = super.interact(pPlayer, pHand);
        if (interactionresult != InteractionResult.PASS) {
            return interactionresult;
        } else if (pPlayer.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        } else {
            if (!this.level().isClientSide) {
                return pPlayer.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
            } else {
                return InteractionResult.SUCCESS;
            }
        }
    }

    @Override
    public boolean canCollideWith(Entity pEntity) {
        return canVehicleCollide(this, pEntity);
    }

    public static boolean canVehicleCollide(Entity pVehicle, Entity pEntity) {
        return (pEntity.canBeCollidedWith() || pEntity.isPushable()) && !pVehicle.isPassengerOfSameVehicle(pEntity);
    }
}
