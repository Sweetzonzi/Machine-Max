package io.github.tt432.machinemax.common.phys.body;

import io.github.tt432.machinemax.common.registry.MMBodyTypes;
import net.minecraft.world.level.Level;
import org.ode4j.math.DVector3;
import org.ode4j.ode.DContactBuffer;
import org.ode4j.ode.DGeom;
import org.ode4j.ode.DSpace;
import org.ode4j.ode.OdeHelper;

public class BlockBody extends AbstractBody {
    public BlockBody(String name, Level level, DSpace space) {
        super(name, level);
        body = OdeHelper.createBody(MMBodyTypes.getBLOCK_BOUNDING_BOX().get(), this, name, false, getPhysLevel().getWorld());
        body.disable();
        body.onTick(this::onTick);
        body.onPhysTick(this::onPhysTick);
        body.setGravityMode(false);
        body.setKinematic();
        DGeom geom = OdeHelper.createBox(space, new DVector3(1, 1, 1));
        geom.setBody(body);
        geoms.add(geom);
        geom.setPosition(new DVector3(0, -512, 0));
        this.enable();
        //TODO: 储存BlockState部分信息，用于摩擦系数计算等
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
