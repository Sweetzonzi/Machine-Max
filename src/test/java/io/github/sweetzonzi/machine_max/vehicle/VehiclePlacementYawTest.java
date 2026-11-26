package io.github.sweetzonzi.machine_max.vehicle;

import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleAssemblyHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 验证载具放置朝向与部件安装角互不影响。 */
class VehiclePlacementYawTest {
    @Test
    void cyclesInNinetyDegreeStepsIndependently() {
        VehicleAssemblyHelper helper = VehicleAssemblyHelper.getInstance();
        float initialYaw = helper.getVehiclePlacementYaw();
        float initialAttachRotation = helper.getAttachRotation();

        helper.cycleVehiclePlacementYaw(true);
        assertEquals((initialYaw + 90f) % 360f, helper.getVehiclePlacementYaw());
        assertEquals(initialAttachRotation, helper.getAttachRotation());

        helper.cycleVehiclePlacementYaw(false);
        assertEquals(initialYaw, helper.getVehiclePlacementYaw());
        helper.cycleVehiclePlacementYaw(false);
        assertEquals((initialYaw + 270f) % 360f, helper.getVehiclePlacementYaw());

        helper.cycleVehiclePlacementYaw(true);
        assertEquals(initialYaw, helper.getVehiclePlacementYaw());
    }
}
