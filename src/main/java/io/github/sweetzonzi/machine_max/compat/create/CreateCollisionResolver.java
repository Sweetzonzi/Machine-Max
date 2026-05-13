package io.github.sweetzonzi.machine_max.compat.create;

import cn.solarmoon.spark_core.compat.create.CreateContraptionPhysicsHost;
import cn.solarmoon.spark_core.physics.PhysicsHost;

import static io.github.sweetzonzi.machine_max.util.mechanic.DynamicUtil.calculateSlipScale;

public final class CreateCollisionResolver {

    private CreateCollisionResolver() {
    }

    public static boolean isCreateOwner(PhysicsHost owner) {
        return owner instanceof CreateContraptionPhysicsHost;
    }

    public static CreateCollisionInfo resolve(PhysicsHost owner, int otherHitBoxIndex) {
        if (!(owner instanceof CreateContraptionPhysicsHost host)) {
            return new CreateCollisionInfo(null, null, false);
        }
        return new CreateCollisionInfo(
                host.getContactBlockPosByChildShapeId(otherHitBoxIndex),
                host.getContactBlockStateByChildShapeId(otherHitBoxIndex),
                true
        );
    }
}

