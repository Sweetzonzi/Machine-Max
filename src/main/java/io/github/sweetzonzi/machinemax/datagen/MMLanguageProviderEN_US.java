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
        //Custom pack exception handle
        this.add("error.machine_max.load", "An error occurred when loading external pack file at: %1$s, Reason: ");
        this.add("error.machine_max.invalid_resource_location", "Invalid resource location. Only lowercase letters, numbers, hyphens and underscores are allowed.");
        this.add("error.machine_max.subpart.zero_mass", "Sub-part mass must be greater than zero");
        this.add("error.machine_max.subpart.empty_hit_boxes", "Sub-part must have at least one hit-box");
        this.add("error.machine_max.subpart.locator_not_found", "Locator %1$s not found in part model");
        this.add("error.machine_max.part.subsystem_hitbox_not_found", "Failed to find hit-box %3$s for subsystem %2$s in part %1$s");
        this.add("error.machine_max.seat_subsystem.no_locator", "Seat subsystem requires a locator (e.g. \"locator\": \"seat_locator\") to define sitting position");
        this.add("error.machine_max.seat_subsystem.no_view", "Seat subsystem must either allow first person view or third person view");

        //Part assembly exception handle
        this.add("error.machine_max.part.connector_locator_not_found", "Failed to find locator %2$s's locator %3$s in the model of part %1$");
        this.add("error.machine_max.part.invalid_connector_type", "Invalid locator type: \"%3$s\" in part: %1$: %2$s, must be \"Special\" or \"AttachPoint\"");
        this.add("error.machine_max.part.invalid_internal_connector_connection", "Illegal internal locator connection between %2$s and %3$s in part %1$s. Only at most one \"Special\" locator is allowed");
        //Hint messages
        this.add("message.machine_max.leaving_vehicle", "Hold [%1$s] %2$s/0.50s to leave the vehicle.");
        this.add("message.machine_max.watch_interact_box_info", "Press [%1$s] to interact with %2$s");
        this.add("error.machine_max.use_part_item", "An error occurred while trying to deploy %1$s：%2$s");
        this.add("tooltip.machine_max.crowbar.safe_disassembly", "Interact to safely disassemble %1$s");
        this.add("tooltip.machine_max.crowbar.unsafe_disassembly", "Integrity: %1$s/%2$s Interact to force disassemble %3$s (Could damage part)");
        this.add("tooltip.machine_max.wrench.disassembly", "Integrity: %1$s/%2$s Interact to safely unbolt %3$s");
        this.add("tooltip.machine_max.wrench.repair", "Integrity: %2$s/%3$s Durability: %4$s/%5$s Interact to repair %1$s");
        this.add("tooltip.machine_max.wrench.cant_repair", "%1$s has been destroyed, cannot repair. Crouch and interact to unbolt the part");
        this.add("tooltip.machine_max.wrench.no_need_to_repair", "%1$s is no need to repair");
        this.add("tooltip.machine_max.spray_can.interact", "Interact to paint:");
        this.add("message.machine_max.blueprint_saved", "Blueprint has been saved to %1$s");
        this.add("message.machine_max.blueprint_error", "Failed to save blueprint: %1$s");
        this.add("message.machine_max.blueprint_pass", "No vehicle selected, skipping blueprint save");
        this.add("message.machine_max.blueprint.place_failed", "No enough space to deploy vehicle");
        //Item
        this.add("itemGroup.machine_max.blueprint", "MachineMax: Custom BluePrints");
        this.add("itemGroup.machine_max.main", "MachineMax: Part and Tools");
        this.add("item.machine_max.crowbar", "Crowbar");
        this.add("item.machine_max.wrench", "Wrench");
        this.add("item.machine_max.spray_can", "Spray Can");
        this.add("item.machine_max.empty_vehicle_blueprint", "Empty Vehicle Blueprint");
    }
}
