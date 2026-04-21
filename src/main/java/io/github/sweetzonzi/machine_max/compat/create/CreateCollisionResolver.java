package io.github.sweetzonzi.machine_max.compat.create;

import cn.solarmoon.spark_core.api.SparkLevel;
import cn.solarmoon.spark_core.compat.create.CreateContraptionPhysicsHost;
import cn.solarmoon.spark_core.physics.PhysicsHost;
import cn.solarmoon.spark_core.util.BlockCollisionUtil;
import cn.solarmoon.spark_core.util.PPhase;
import com.jme3.bullet.collision.ManifoldPoints;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.util.MMMath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

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

