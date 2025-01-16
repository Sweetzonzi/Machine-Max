package io.github.tt432.machinemax.common.sloarphys.body;

import cn.solarmoon.spark_core.phys.thread.ThreadHelperKt;
import io.github.tt432.machinemax.MachineMax;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.ode4j.math.DVector3;
import org.ode4j.ode.DContactBuffer;
import org.ode4j.ode.DGeom;
import org.ode4j.ode.DSpace;
import org.ode4j.ode.OdeHelper;

public class BlockBody extends AbstractBody{
    public BlockBody(String name, Level level, DSpace space) {
        super(name, level);
        body.setGravityMode(false);
        body.setKinematic();
        DGeom geom = OdeHelper.createBox(space, new DVector3(1,1,1));
        geom.setBody(body);
        geoms.add(geom);
        geom.setPosition(new DVector3(0,-512,0));
        this.enable();
    }

    @Override
    protected void onTick() {

    }

    @Override
    protected void onCollide(DGeom dGeom, DContactBuffer dContacts) {

    }

    @Override
    protected void onPhysTick() {

    }
}
