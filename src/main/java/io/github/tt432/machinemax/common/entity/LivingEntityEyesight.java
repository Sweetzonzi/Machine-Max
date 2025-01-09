package io.github.tt432.machinemax.common.entity;

import cn.solarmoon.spark_core.phys.attached_body.AttachedBody;
import cn.solarmoon.spark_core.phys.thread.PhysLevel;
import cn.solarmoon.spark_core.phys.thread.ThreadHelperKt;
import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.mixin_interface.IMixinEntity;
import lombok.Getter;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.ode4j.math.DMatrix3;
import org.ode4j.math.DVector3;
import org.ode4j.ode.*;
import org.ode4j.ode.internal.Rotation;

import static net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE;

public class LivingEntityEyesight implements AttachedBody {
    @Getter
    final String name;
    @Getter
    DBody body;

    final LivingEntity owner;
    final Level level;
    public final DRay ray;
    @Getter
    volatile public double range;
    @Getter
    volatile public DGeom target;
    volatile private boolean hit;

    public LivingEntityEyesight(String name, LivingEntity entity) {
        this.name = name;
        this.owner = entity;
        this.level = entity.level();
        body = OdeHelper.createBody(name, this, false, getPhysLevel().getPhysWorld().getWorld());
        this.ray = OdeHelper.createRay(null, entity.getAttributeValue(ENTITY_INTERACTION_RANGE));
        ray.setBody(body);
        ray.setPassFromCollide(true);
        this.setPosRotVel();
        body.onTick(this::onTick);
        body.onPhysTick(this::onPhysTick);
        ray.onCollide(this::onCollide);
        getPhysLevel().getPhysWorld().laterConsume(() -> {
            getPhysLevel().getPhysWorld().getSpace().add(ray);
            this.enable();
            return null;
        });
    }

    private void onPhysTick() {
        if (!hit) {
            target = null;
            range = Double.MAX_VALUE;
        } else hit = false;
    }

    private void onTick() {
        this.setPosRotVel();
    }

    private void onCollide(DGeom dGeom, DContactBuffer dContacts) {
        hit = true;
        double range = dContacts.get(0).getContactGeom().depth;
        if (range < this.range) {
            this.range = range;
            this.target = dGeom;
        }
    }

    private void setPosRotVel() {
        getPhysLevel().getPhysWorld().laterConsume(() -> {
            Vec3 rot =owner.getViewVector(1);
            ray.set(new DVector3(owner.getX(), owner.getY()+owner.getEyeHeight(), owner.getZ()), new DVector3(rot.x, rot.y, rot.z));
            Vec3 vel = owner.getDeltaMovement().scale(20);
            body.setLinearVel(new DVector3(vel.x, vel.y, vel.z));
            return null;
        });
    }

    @NotNull
    @Override
    public PhysLevel getPhysLevel() {
        return ThreadHelperKt.getPhysLevelById(level, MachineMax.MOD_ID);
    }

    @Override
    public void enable() {
        body.enable();
        ray.enable();
    }

    @Override
    public void disable() {
        body.disable();
        ray.disable();
    }
}
