package io.github.sweetzonzi.machinemax.common.registry;

import cn.solarmoon.spark_core.event.MolangBindingRegisterEvent;
import cn.solarmoon.spark_core.event.MolangQueryRegisterEvent;
import io.github.sweetzonzi.machinemax.MachineMax;
import io.github.sweetzonzi.machinemax.common.vehicle.molang.PartBinding;
import io.github.sweetzonzi.machinemax.common.vehicle.molang.VehicleBinding;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = MachineMax.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class MMMolangs {
    @SubscribeEvent
    private static void registerMolangBinding(MolangBindingRegisterEvent event) {
        event.getBindings().put("p", PartBinding.INSTANCE);
        event.getBindings().put("part", PartBinding.INSTANCE);
        event.getBindings().put("vehicle", VehicleBinding.INSTANCE);
    }

    @SubscribeEvent
    private static void registerMolangQueries(MolangQueryRegisterEvent event) {
        event.getBinding().var("machine_max_test", ctx -> 150);
    }
}
