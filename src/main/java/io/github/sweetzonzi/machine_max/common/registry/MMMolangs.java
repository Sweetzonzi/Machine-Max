package io.github.sweetzonzi.machine_max.common.registry;

import cn.solarmoon.spark_core.event.MolangRegisterEvent;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.vehicle.molang.SubPartBinding;
import io.github.sweetzonzi.machine_max.common.vehicle.molang.VehicleBinding;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = MachineMax.MOD_ID)
public class MMMolangs {
    @SubscribeEvent
    private static void registerMolangBinding(MolangRegisterEvent event) {
        var subPart = new SubPartBinding();
        var vehicle = new VehicleBinding();
        event.register("spt", subPart);
        event.register("subpart", subPart);
        event.register("veh", vehicle);
        event.register("vehicle", vehicle);
    }

}
