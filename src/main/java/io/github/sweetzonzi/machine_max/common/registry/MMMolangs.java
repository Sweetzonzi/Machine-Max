package io.github.sweetzonzi.machine_max.common.registry;

import cn.solarmoon.spark_core.event.OnMolangValueBindingEvent;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.vehicle.molang.PartBinding;
import io.github.sweetzonzi.machine_max.common.vehicle.molang.VehicleBinding;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = MachineMax.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class MMMolangs {
    @SubscribeEvent
    private static void registerMolangBinding(OnMolangValueBindingEvent event) {
        event.getBindings().putMember("p", PartBinding.INSTANCE);
        event.getBindings().putMember("part", PartBinding.INSTANCE);
        event.getBindings().putMember("veh", VehicleBinding.INSTANCE);
        event.getBindings().putMember("vehicle", VehicleBinding.INSTANCE);
    }

}
