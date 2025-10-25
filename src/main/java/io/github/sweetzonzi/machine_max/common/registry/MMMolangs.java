package io.github.sweetzonzi.machine_max.common.registry;

import cn.solarmoon.spark_core.event.OnMolangValueBindingEvent;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.vehicle.molang.SubPartBinding;
import io.github.sweetzonzi.machine_max.common.vehicle.molang.VehicleBinding;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = MachineMax.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class MMMolangs {
    @SubscribeEvent
    private static void registerMolangBinding(OnMolangValueBindingEvent event) {
        event.getBindings().putMember("spt", new SubPartBinding(event.getAnimatable()));
        event.getBindings().putMember("subpart", new SubPartBinding(event.getAnimatable()));
        event.getBindings().putMember("veh", new VehicleBinding(event.getAnimatable()));
        event.getBindings().putMember("vehicle", new VehicleBinding(event.getAnimatable()));
    }

}
