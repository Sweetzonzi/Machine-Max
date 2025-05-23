package io.github.sweetzonzi.machinemax.util.mechanic;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

public class MassUtil {
    public static double getEntityMass(Entity entity){
        AABB entitySize = entity.getBoundingBox();
        double entityMass = 92.6 * entitySize.getXsize() * entitySize.getYsize() * entitySize.getZsize();
        return entityMass;
    }
}
