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
        var anim = event.getAnimatable();
        var subPart = new SubPartBinding(anim);
        var vehicle = new VehicleBinding(anim);
        event.getBindings().putMember("spt", subPart);
        event.getBindings().putMember("subpart", subPart);
        event.getBindings().putMember("veh", vehicle);
        event.getBindings().putMember("vehicle", vehicle);
    }

}
