package io.github.sweetzonzi.machinemax.datagen;

import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

public class MMLanguageProviderEN_US extends LanguageProvider {
    public MMLanguageProviderEN_US(PackOutput output, String modid, String locale) {
        super(output, modid, locale);
    }

    @Override
    protected void addTranslations() {
        //Key categories
        this.add("key.category.machine_max.general", "Machine Max:General");
        this.add("key.category.machine_max.ground", "Machine Max:Ground");
        this.add("key.category.machine_max.ship", "Machine Max:Ship");
        this.add("key.category.machine_max.plane", "Machine Max:Plane");
        this.add("key.category.machine_max.mech", "Machine Max:Mech");
        this.add("key.category.machine_max.assembly", "Machine Max:Assembly");
        //Key names-General
        this.add("key.machine_max.general.free_cam", "Free Camera");
        this.add("key.machine_max.general.interact", "Interact with Vehicle");
        this.add("key.machine_max.general.leave_vehicle", "Leave Vehicle");
        //Key names-Ground
        this.add("key.machine_max.ground.forward", "Forward");
        this.add("key.machine_max.ground.backward", "Backward");
        this.add("key.machine_max.ground.leftward", "Leftward");
        this.add("key.machine_max.ground.rightward", "Rightward");
        this.add("key.machine_max.ground.clutch", "Clutch");
        this.add("key.machine_max.ground.up_shift", "Shift Up");
        this.add("key.machine_max.ground.down_shift", "Shift Down");
        this.add("key.machine_max.ground.hand_brake", "Hand Brake (Press)");
        this.add("key.machine_max.ground.toggle_hand_brake", "Hand Brake (Toggle)");
        //Key names-Assembly
        this.add("key.machine_max.assembly.cycle_connector", "Cycle Part Connector");
        this.add("key.machine_max.assembly.cycle_variant", "Cycle Part Variant");
        //Hint messages
        this.add("message.machine_max.leaving_vehicle", "Hold [%1$s] %2$s/0.50s to leave the vehicle.");
        this.add("message.machine_max.watch_interact_box_info", "Press [%1$s] to interact with %2$s");
        this.add("error.machine_max.use_part_item", "An error occurred while trying to deploy %1$s：%2$s");
        this.add("tooltip.machinemax.crossbar.interact", "Interact to disassemble:");
        this.add("tooltip.machinemax.spray_can.interact", "Interact to paint:");
        //Item
        this.add("itemGroup.machine_max.blueprint", "MachineMax: Custom BluePrints");
        this.add("itemGroup.machine_max.main", "MachineMax: Part and Tools");
        this.add("item.machine_max.crossbar_item", "Crossbar");
        this.add("item.machine_max.spray_can_item", "Spray Can");
        this.add("item.machine_max.test_car_spawner", "Test Car Spawner");
        this.add("item.machine_max.vehicle_recorder_item", "Vehicle Recorder");
    }
}
