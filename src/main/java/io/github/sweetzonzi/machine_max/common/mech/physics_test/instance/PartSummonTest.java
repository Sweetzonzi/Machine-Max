package io.github.sweetzonzi.machine_max.common.mech.physics_test.instance;

import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.mojang.datafixers.util.Pair;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.physics_test.BaseJoinPositionPhysicsTest;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.Part;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.PartType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 一个示范测试用例，通过召唤者与入点召唤指定刚体
 * */
public class PartSummonTest extends BaseJoinPositionPhysicsTest {

    @Override
    public void runWithJoinPosition(Player player, Pair<Vec3, Transform> joinPosition) {
        ResourceLocation partId = ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "dayun_steel_coil");
        Level level = player.level();
        PartType partType = PartType.get(level, partId);
        Part part = new Part(partType, level);
        part.setTransform(joinPosition.getSecond());

        VehicleCore vehicle = new VehicleCore(level, part);
        ObjectManager.addVehicle(vehicle);
        vehicles.add(vehicle);

        double strength = 80.0;
        var lookAt = joinPosition.getFirst();
        var velocity = new Vec3(lookAt.x * strength, lookAt.y * strength, lookAt.z * strength);
        Vector3f v = new Vector3f((float) velocity.x, (float) velocity.y, (float) velocity.z);
        part.rootSubPart.setLinearVelocity(v);
        part.rootSubPart.body.setLinearVelocity(v);

        Vector3f v2 = new Vector3f(6, 7, 0);
        part.rootSubPart.setAngularVelocity(v2);
        part.rootSubPart.body.setAngularVelocity(v2);

    }

}
