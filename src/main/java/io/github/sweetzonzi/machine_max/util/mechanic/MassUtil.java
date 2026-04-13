package io.github.sweetzonzi.machine_max.util.mechanic;

import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.AbstractConnector;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

public class MassUtil {
    public static double getEntityMass(Entity entity){
        AABB entitySize = entity.getBoundingBox();
        return 92.6 * entitySize.getXsize() * entitySize.getYsize() * entitySize.getZsize();
    }

    public static float getEquivalentMass(SubPart subPart) {
        float partMass = subPart.body.getMass();
        for (AbstractConnector connector : subPart.connectors.values()) {
            if (connector.hasPart())
                partMass += (0.3f * connector.attachedConnector.subPart.body.getMass());
        }
        partMass += 0.05f * (subPart.part.vehicle.totalMass - subPart.body.getMass());
        return partMass;
    }
}
