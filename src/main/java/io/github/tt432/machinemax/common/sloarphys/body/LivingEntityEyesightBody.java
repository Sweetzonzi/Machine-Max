package io.github.tt432.machinemax.common.sloarphys.body;

import lombok.Getter;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.ode4j.math.DVector3;
import org.ode4j.ode.*;

import static net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE;

public class LivingEntityEyesightBody extends AbstractBody {
    final LivingEntity owner;
    public final DRay ray;
    @Getter
    volatile private double range;
    @Getter
    volatile private DGeom target;
    volatile private boolean hit;

    public LivingEntityEyesightBody(String name, LivingEntity entity) {
        super(name, entity);
        this.owner = entity;
        this.ray = OdeHelper.createRay(null, entity.getAttributeValue(ENTITY_INTERACTION_RANGE));
        geoms.add(ray);
        ray.setBody(body);
        ray.setPassFromCollide(true);
        this.setPosRotVel();
        ray.onCollide(this::onCollide);
        getPhysLevel().getPhysWorld().laterConsume(() -> {
            getPhysLevel().getPhysWorld().getSpace().add(ray);
            this.enable();
            return null;
        });
        this.enable();
    }

    @Override
    protected void onPhysTick() {
        if (!hit) {
            target = null;
            range = Double.MAX_VALUE;
        } else hit = false;
    }

    @Override
    protected void onTick() {
        this.setPosRotVel();
    }

    @Override
    protected void onCollide(DGeom dGeom, DContactBuffer dContacts) {
        hit = true;
        double range = dContacts.get(0).getContactGeom().depth;
        if (range < this.range) {
            this.range = range;
            this.target = dGeom;
        }
    }

    private void setPosRotVel() {
        getPhysLevel().getPhysWorld().laterConsume(() -> {
            Vec3 rot = owner.getViewVector(1);
            ray.set(new DVector3(owner.getX(), owner.getY() + owner.getEyeHeight(), owner.getZ()), new DVector3(rot.x, rot.y, rot.z));
            Vec3 vel = owner.getDeltaMovement().scale(20);
            body.setLinearVel(new DVector3(vel.x, vel.y, vel.z));
            return null;
        });
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
