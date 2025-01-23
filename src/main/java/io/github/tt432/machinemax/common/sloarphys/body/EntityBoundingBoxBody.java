package io.github.tt432.machinemax.common.sloarphys.body;

import cn.solarmoon.spark_core.phys.BodyType;
import cn.solarmoon.spark_core.phys.SparkMathKt;
import cn.solarmoon.spark_core.registry.common.SparkBodyTypes;
import io.github.tt432.machinemax.MachineMax;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.ode4j.math.DVector3;
import org.ode4j.ode.DBox;
import org.ode4j.ode.DContactBuffer;
import org.ode4j.ode.DGeom;
import org.ode4j.ode.OdeHelper;

public class EntityBoundingBoxBody extends AbstractBody {
    final Entity owner;

    public EntityBoundingBoxBody(Entity entity) {
        super(entity.getName().getString() + "_bounding_box", entity.level());
        body = OdeHelper.createBody(SparkBodyTypes.getENTITY_BOUNDING_BOX().get(), entity, entity.getName().getString() + "_bounding_box", false, getPhysLevel().getWorld());
        body.disable();
        body.onTick(this::onTick);
        body.onPhysTick(this::onPhysTick);
        this.owner = entity;
        DGeom geom = OdeHelper.laterCreateBox(body, getPhysLevel().getWorld(), new DVector3());
        geom.onCollide(this::onCollide);
        geoms.add(geom);
        this.enable();
    }

    @Override
    protected void onTick() {
        setPosVel();
    }

    @Override
    protected void onCollide(DGeom dGeom, DContactBuffer dContacts) {

    }

    @Override
    protected void onPhysTick() {

    }

    private void setPosVel() {
        getPhysLevel().getWorld().laterConsume(() -> {
            AABB aabb = owner.getBoundingBox();
            ((DBox) (geoms.getFirst())).setLengths(aabb.getXsize(), aabb.getYsize(), aabb.getZsize());
            body.setPosition(SparkMathKt.toDVector3(aabb.getCenter()));
            Vec3 vel = owner.getDeltaMovement().scale(20);
            body.setLinearVel(new DVector3(vel.x, vel.y, vel.z));
            return null;
        });
    }
}
