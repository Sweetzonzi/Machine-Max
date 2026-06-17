package io.github.sweetzonzi.machine_max.datagen;

import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

public class MMLanguageProviderEN_US extends LanguageProvider {
    public MMLanguageProviderEN_US(PackOutput output, String modid, String locale) {
        super(output, modid, locale);
    }

    @Override
    protected void addTranslations() {
        // 内置标签
        this.add("machine_max.direction.left", "Left");
        this.add("machine_max.direction.right", "Right");
        this.add("machine_max.direction.front", "Front");
        this.add("machine_max.direction.back", "Back");
        this.add("machine_max.direction.top", "Top");
        this.add("machine_max.direction.bottom", "Bottom");
        this.add("machine_max.category.structural", "Structural");
        this.add("machine_max.category.decoration", "Decoration");
        this.add("machine_max.category.mobility", "Mobility");
        this.add("machine_max.category.weapon", "Weapon");
        this.add("machine_max.category.misc", "Misc");
        this.add("machine_max.type.land", "Land");
        this.add("machine_max.type.marine", "Marine");
        this.add("machine_max.type.aerial", "Aerial");
        this.add("machine_max.type.mecha", "Mecha");

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
        this.add("key.machine_max.general.toggle_light", "Toggle Light");
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
        this.add("key.machine_max.assembly.add_attach_angle", "Rotate Attach Angle (+)");
        this.add("key.machine_max.assembly.sub_attach_angle", "Rotate Attach Angle (-)");
        this.add("key.machine_max.assembly.cycle_connector", "Cycle Part Connector");
        this.add("key.machine_max.assembly.cycle_variant", "Cycle Part Variant");
        this.add("key.machine_max.assembly.cycle_recipe", "Cycle Part Recipe");
        //Custom pack exception handler
        this.add("error.machine_max.load", "An error occurred when loading external pack file at: %1$s, Reason: ");
        this.add("error.machine_max.invalid_resource_location", "Invalid resource location. Only lowercase letters, numbers, hyphens and underscores are allowed.");
        this.add("error.machine_max.subpart.zero_mass", "Sub-part mass must be greater than zero");
        this.add("error.machine_max.subpart.empty_hit_boxes", "Sub-part must have at least one hit-box");
        this.add("error.machine_max.subpart.empty_collision_shape", "Sub-part shape cannot be empty");
        this.add("error.machine_max.subpart.locator_not_found", "Locator %1$s not found in part model");
        this.add("error.machine_max.part.subsystem_hitbox_not_found", "Failed to find hit-box %3$s for subsystem %2$s in part %1$s");
        this.add("error.machine_max.seat_subsystem.no_locator", "Seat subsystem requires a locator (e.g. \"locator\": \"seat_locator\") to define sitting position");
        this.add("error.machine_max.seat_subsystem.no_view", "Seat subsystem must either allow first person view or third person view");
        this.add("error.machine_max.item_storage_subsystem.invalid_row_num", "The row number of item-storage subsystem must be greater than 1");
        this.add("error.machine_max.item_storage_subsystem.invalid_column_num", "The column number of item-storage subsystem must be greater than 1");
        //Part assembly exception handler
        this.add("error.machine_max.part.model_not_found", "Model file not found at: %1$s");
        this.add("error.machine_max.part.connector_locator_not_found", "Failed to find locator %2$s's locator %3$s in the model of part %1$");
        this.add("error.machine_max.part.invalid_connector_type", "Invalid locator type: \"%3$s\" in part: %1$: %2$s, must be \"Special\" or \"AttachPoint\"");
        this.add("error.machine_max.part.invalid_internal_connector_connection", "Illegal internal locator connection between %2$s and %3$s in part %1$s. Only at most one \"Special\" locator is allowed");
        //Hint messages
        this.add("toast.machine_max.research_complete", "Research Complete!");
        this.add("message.machine_max.leaving_vehicle", "Hold [%1$s] %2$s/0.50s to leave the vehicle.");
        this.add("message.machine_max.watch_interact_box_info", "[%1$s]");
        this.add("error.machine_max.use_part_item", "An error occurred while trying to deploy %1$s：%2$s");
        this.add("tooltip.machine_max.crowbar.safe_disassembly", "Interact to safely disassemble %1$s");
        this.add("tooltip.machine_max.crowbar.detach_connector", "Interact to detach %1$s from %2$s");
        this.add("tooltip.machine_max.crowbar.unsafe_disassembly", "Interact to force disassemble %1$s (Could damage part)");
        this.add("tooltip.machine_max.wrench.disassembly", "Integrity: %1$s/%2$s Interact to safely unbolt %3$s");
        this.add("tooltip.machine_max.wrench.repair", "Integrity: %2$s/%3$s Durability: %4$s/%5$s Interact to repair %1$s");
        this.add("tooltip.machine_max.wrench.cant_repair", "%1$s has been destroyed, cannot repair. Crouch and interact to unbolt the part");
        this.add("tooltip.machine_max.wrench.no_need_to_repair", "%1$s is no need to repair");
        this.add("tooltip.machine_max.spray_can.interact", "Interact to paint:");
        this.add("tooltip.machine_max.jade.subpart_durability", "SubPart: %1$s");
        this.add("tooltip.machine_max.jade.vehicle_durability", "Vehicle: %1$s");
        this.add("message.machine_max.blueprint_saved", "Blueprint has been saved to %1$s");
        this.add("message.machine_max.blueprint_error", "Failed to save blueprint: %1$s");
        this.add("message.machine_max.blueprint_pass", "No vehicle selected, skipping blueprint save");
        this.add("message.machine_max.blueprint.place_failed", "No enough space to deploy vehicle");
        this.add("message.machine_max.vehicle.place_failed", "Failed to deploy vehicle: %1$s");
        this.add("message.machine_max.part.place_failed", "Failed to place part: %1$s");
        //Item Group
        this.add("itemGroup.machine_max.main", "MachineMax: Materials and Tools");
        this.add("itemGroup.machine_max.part", "MachineMax: Parts");
        this.add("itemGroup.machine_max.vehicle_blueprint", "MachineMax: Vehicle Designs");
        this.add("itemGroup.machine_max.fabricating_blueprint", "MachineMax: Fabricating Blueprints");
        this.add("itemGroup.machine_max.assembly", "MachineMax: Assembly");
        // Item
        this.add("block.machine_max.fabricator", "Fabricator(WIP)");
        this.add("block.machine_max.research_table", "Research Table");
        this.add("item.machine_max.crowbar", "Crowbar");
        this.add("item.machine_max.welding_torch", "Welding Torch");
        this.add("item.machine_max.spray_can", "Spray Can");
        this.add("item.machine_max.ender_gk_resin", "Ender GK Resin");
        this.add("item.machine_max.empty_blueprint", "Empty Vehicle Blueprint");
        this.add("item.machine_max.fabricating_blueprint", "Fabricating Blueprint");
        this.add("item.machine_max.structural_component_1", "Basic Structural Component");
        this.add("item.machine_max.mechanic_component_1", "Basic Mechanical Component");
        this.add("item.machine_max.weapon_component_1", "Basic Weapon Component");
        this.add("item.machine_max.electronic_component_1", "Basic Electronic Component");
        this.add("item.machine_max.power_component_1", "Basic Power Component");
        this.add("item.machine_max.energetic_component_1", "Basic Energetic Material");
        // Item sound effects
        this.add("subtitles.item.welding_torch.start", "Welding torch starts");
        this.add("subtitles.item.welding_torch.loop", "Welding torch running");
        this.add("subtitles.item.welding_torch.end", "Welding torch stops");
        //Menu & Screen
        this.add("gui.machine_max.welcome.title", "Welcome to Machine Max Beta!");
        this.add("gui.machine_max.welcome.content", "This is a Minecraft mod themed around physics and vehicle assembly.\nThis mod is still in an early version, may have various issues, and does not represent final quality.\nPlease make sure to back up your old saves.\n\nHave fun!");
        this.add("gui.machine_max.welcome.proceed", "Confirm");
        this.add("gui.machine_max.welcome.dont_show_again", "Don't show this again");
        this.add("gui.machine_max.set_vehicle_name", "Set Vehicle Name");
        this.add("gui.machine_max.confirm", "Confirm");
        this.add("gui.machine_max.cancel", "Cancel");
        this.add("gui.machine_max.enter_vehicle_name", "Enter Vehicle Name");
        this.add("gui.machine_max.fabricator.recipes", "Recipes");
        this.add("gui.machine_max.fabricator.tasks", "Tasks");
        this.add("gui.machine_max.fabricator.preview", "Preview");
        this.add("gui.machine_max.fabricator.materials", "Material requirements");
        this.add("gui.machine_max.fabricator.free_slots", "Idle task queue: %1$s/%2$s");
        this.add("gui.machine_max.fabricator.insufficient_materials", "Insufficient materials");
        this.add("gui.machine_max.fabricator.no_free_slots", "Task queue is full");
        this.add("gui.machine_max.fabricator.start_production", "FABRICATE");
        this.add("gui.machine_max.fabricator.cancel_task", "CANCEL");
        this.add("gui.machine_max.fabricator.collect_task", "CLAIM");
        this.add("gui.machine_max.fabricator.collect_all_task", "CLAIM ALL");
        this.add("gui.machine_max.fabricator.search_hint", "Search recipe…");
        this.add("gui.machine_max.fabricator.status.queued", "Queued");
        this.add("gui.machine_max.fabricator.status.producing", "Producing");
        this.add("gui.machine_max.fabricator.status.completed", "Completed");
        this.add("gui.machine_max.fabricator.status.idle", "Idle");
        this.add("gui.machine_max.fabricator.ready_for_collection", "Complete!");
        this.add("gui.machine_max.fabricator.estimated_time", "EST: %s");
        this.add("gui.machine_max.fabricator.remaining_time", "Remaining: %s");
        this.add("gui.machine_max.fabricator.no_item", "No Item");
        this.add("gui.machine_max.fabricator.select_recipe_hint", "Select a recipe to view details");
        this.add("gui.machine_max.fabricator.recipe_details", "Recipe Details");
        this.add("gui.machine_max.fabricator.actual_time", "Fabrication Time: %s");
        this.add("gui.machine_max.fabricator.base_time", "Base Time: %s");
        this.add("gui.machine_max.fabricator.efficiency", "Efficiency: %sx");
        this.add("gui.machine_max.fabricator.output_count", "Output Count: %s");
        this.add("gui.machine_max.fabricator.recipe_description", "Recipe Description:");
        this.add("gui.machine_max.fabricator.item_description", "Item Description:");
        this.add("gui.machine_max.research.no_preview", "No preview available");
        this.add("gui.machine_max.research.no_blueprint_product", "No unlocked product to preview");
        this.add("gui.machine_max.research.status.can_research", "Can Research");
        this.add("gui.machine_max.research.status.can_claim", "Can Claim");
        this.add("gui.machine_max.research.status.can_reprint", "Can Reprint");
        this.add("gui.machine_max.research.status.prereq_missing", "Prerequisite Missing x%1$s");
        this.add("gui.machine_max.research.status.need_materials_rp", "Insufficient Materials / RP");
        this.add("gui.machine_max.research.status.unavailable", "Unavailable");
        this.add("gui.machine_max.research.tab.research_materials", "Research Materials");
        this.add("gui.machine_max.research.tab.fabricating_materials", "Fabricating Materials");
        this.add("gui.machine_max.research.no_materials", "No materials required");
        this.add("gui.machine_max.research.fabrication_time", "Fabrication Time: %s sec");
        this.add("jei.machine_max.blueprint_research.rp_cost", "Research Point Cost: %1$s");
        //Hud
        this.add("hud.warn.machine_max.subpart_destroying", "❌ DESTROYED, DESTRUCT IN: %1$ss");
        this.add("hud.warn.machine_max.subsystem_malfunction", "❌ %1$s MALFUNCTION");
        this.add("hud.hint.machine_max.subsystem_durability", "⚠ %1$s DURABILITY: %2$s/%3$s");
        this.add("hud.warn.machine_max.connector_integrity_low", "❌ %1$s INTEGRITY LOW");
        this.add("hud.hint.machine_max.connector_integrity", "⚠ %1$s INTEGRITY: %2$s/%3$s");
        this.add("hud.info.machine_max.assembling_progress", "ASSEMBLY: ");
        this.add("hud.hint.machine_max.cannot_assemble", "Not researched or have no blueprint, cannot assemble");
        this.add("hud.key.machine_max.assemble", "[%1$s] Assemble & Repair & Reinforce");
        this.add("hud.key.machine_max.repair_without_assemble", "[%1$s] Repair & Reinforce");
        this.add("hud.key.machine_max.disassemble", "[%1$s+%2$s] Disassemble");
        this.add("hud.key.machine_max.cycle_recipe", "[%1$s] Cycle Recipe (%2$s Available)");
        this.add("hud.key.machine_max.tear_down", "[%1$s] Remove part");
        this.add("hud.key.machine_max.detach", "[%1$s+%2$s] Detach connector");
        // Research Point Hud
        this.add("hud.hint.machine_max.rp_add_reason.exp", "Gain EXP");
        this.add("hud.hint.machine_max.rp_add_reason.repair", "Repair");
        this.add("hud.hint.machine_max.rp_add_reason.assembly", "Assembly");
        this.add("hud.hint.machine_max.rp_add_reason.hit", "Hit");
        this.add("hud.hint.machine_max.rp_add_reason.part_damage", "Damage Part");
        this.add("hud.hint.machine_max.rp_add_reason.part_destroy", "Destroy Part");
        this.add("hud.hint.machine_max.rp_add_reason.subsystem_damage", "Damage Subsystem");
        this.add("hud.hint.machine_max.rp_add_reason.subsystem_destroy", "Destroy Subsystem");
        this.add("hud.hint.machine_max.rp_add_reason.unknown", "Unknown");
        // Configuration
        this.add("machine_max.configuration.title", "Machine Max Configuration");
        this.add("machine_max.configuration.section.machine.max.client.toml.title", "Client Configuration");
        this.add("machine_max.configuration.section.machine.max.client.toml", "Client Configuration");
        this.add("machine_max.configuration.section.machine.max.common.toml", "Common Configuration");
        this.add("machine_max.configuration.section.machine.max.server.toml", "Server Configuration");
        this.add("config.jade.plugin_machine_max.mm_part_entity_status", "Machine Max Part Durability");
        // Ground Vehicle Configuration
        this.add("machine_max.configuration.ground_vehicle", "Ground Vehicle");
        this.add("machine_max.configuration.ground_vehicle.button", "Ground Vehicle");
        this.add("machine_max.configuration.ground_vehicle.tooltip", "Ground vehicle control smoothness and behavior settings");
        this.add("machine_max.configuration.full_power_time", "Throttle Ramp-Up Time");
        this.add("machine_max.configuration.full_power_time.tooltip", "Seconds to reach full throttle after pressing the key. Lower = faster response. Default: 1.5 (ground), 2.5 (plane). Range: 0.05 ~ 99999.0");
        this.add("machine_max.configuration.power_off_time", "Throttle Release Time");
        this.add("machine_max.configuration.power_off_time.tooltip", "Seconds to return throttle to 0 after releasing the key. Lower = faster throttle cut. Default: 2.0. Range: 0.05 ~ 99999.0");
        this.add("machine_max.configuration.full_brake_time", "Brake Ramp-Up Time");
        this.add("machine_max.configuration.full_brake_time.tooltip", "Seconds to reach full brake after pressing the key. Lower = faster braking. Default: 0.3. Range: 0.05 ~ 99999.0");
        this.add("machine_max.configuration.brake_off_time", "Brake Release Time");
        this.add("machine_max.configuration.brake_off_time.tooltip", "Seconds to release brake to 0 after releasing the key. Lower = wheels resume rolling faster. Default: 0.1. Range: 0.05 ~ 99999.0");
        this.add("machine_max.configuration.full_steering_time", "Steering Ramp-Up Time");
        this.add("machine_max.configuration.full_steering_time.tooltip", "Seconds to reach full steering angle after pressing the key. Lower = faster steering. Default: 0.4 (ground), 0.25 (ship). Range: 0.05 ~ 99999.0");
        this.add("machine_max.configuration.steering_off_time", "Steering Return Time");
        this.add("machine_max.configuration.steering_off_time.tooltip", "Seconds to return steering to center after releasing the key. Lower = wheels straighten faster. Default: 0.2. Range: 0.05 ~ 99999.0");
        this.add("machine_max.configuration.auto_switch_gear", "Auto Gear Shift");
        this.add("machine_max.configuration.auto_switch_gear.tooltip", "FOLLOW_VEHICLE = vehicle-defined. ALWAYS_ENABLED = always auto. ALWAYS_DISABLED = manual only. Default: FOLLOW_VEHICLE");
        this.add("machine_max.configuration.auto_handbrake", "Auto Handbrake");
        this.add("machine_max.configuration.auto_handbrake.tooltip", "FOLLOW_VEHICLE = vehicle-defined. ALWAYS_ENABLED = always auto. ALWAYS_DISABLED = manual only. Default: FOLLOW_VEHICLE");
        this.add("machine_max.configuration.drift_assist", "Drift Assist");
        this.add("machine_max.configuration.drift_assist.tooltip", "Auto counter-steer during drifting. FOLLOW_VEHICLE = vehicle-defined. ALWAYS_ENABLED = always on. ALWAYS_DISABLED = off. Default: FOLLOW_VEHICLE");
        this.add("machine_max.configuration.pose_preference", "Camera Follow Vehicle");
        this.add("machine_max.configuration.pose_preference.tooltip", "Auto rotate camera to follow vehicle heading. FOLLOW_VEHICLE = vehicle-defined. ALWAYS_ENABLED = follow. ALWAYS_DISABLED = free. Default: FOLLOW_VEHICLE");
        this.add("machine_max.configuration.speed_turning_limit", "High-Speed Steering Limit");
        this.add("machine_max.configuration.speed_turning_limit.tooltip", "Limit steering angle at high speed to prevent rollover. Default: true");
        this.add("machine_max.configuration.separate_throttle_brake", "Separate Throttle & Brake");
        this.add("machine_max.configuration.separate_throttle_brake.tooltip", "Separate throttle and brake into independent channels. false = intent mode (W forward, S backward, vehicle auto-decides). true = raw pedal mode (W gas, S brake). Default: false");
        this.add("machine_max.configuration.render_hit_whitening", "Part Hit Flash");
        this.add("machine_max.configuration.render_hit_whitening.tooltip", "Show a white flash on parts when hit. Default: true");
        this.add("machine_max.configuration.render_destroy_blackening", "Part Destroyed Overlay");
        this.add("machine_max.configuration.render_destroy_blackening.tooltip", "Dark overlay on destroyed parts. Default: true");
        this.add("machine_max.configuration.render_force_translucent_parts", "Force Translucent Parts");
        this.add("machine_max.configuration.render_force_translucent_parts.tooltip", "Replace cutout part rendering with translucent entity rendering. Off by default because translucent rendering often causes incorrect z-sorting and object culling. Default: false");
        this.add("machine_max.configuration.show_welcome_screen", "Show Welcome Screen");
        this.add("machine_max.configuration.show_welcome_screen.tooltip", "Show the welcome screen when entering the title screen for the first time after launch. Default: true");
        // Ship Configuration
        this.add("machine_max.configuration.ship", "Ship");
        this.add("machine_max.configuration.ship.button", "Ship");
        this.add("machine_max.configuration.ship.tooltip", "Ship steering smoothness settings");
        // Plane Configuration
        this.add("machine_max.configuration.plane", "Plane");
        this.add("machine_max.configuration.plane.button", "Plane");
        this.add("machine_max.configuration.plane.tooltip", "Plane control smoothness settings");
        this.add("machine_max.configuration.full_pitch_time", "Pitch Ramp-Up Time");
        this.add("machine_max.configuration.full_pitch_time.tooltip", "Seconds to reach full pitch after pressing the key. Lower = faster. Default: 0.25. Range: 0.05 ~ 99999.0");
        this.add("machine_max.configuration.full_yaw_time", "Yaw Ramp-Up Time");
        this.add("machine_max.configuration.full_yaw_time.tooltip", "Seconds to reach full yaw after pressing the key. Lower = faster. Default: 0.25. Range: 0.05 ~ 99999.0");
        this.add("machine_max.configuration.full_roll_time", "Roll Ramp-Up Time");
        this.add("machine_max.configuration.full_roll_time.tooltip", "Seconds to reach full roll after pressing the key. Lower = faster. Default: 0.25. Range: 0.05 ~ 99999.0");
        // Server Configuration
        this.add("machine_max.configuration.should_destroy_blocks", "Allow Collision Destroy Blocks");
        this.add("machine_max.configuration.should_destroy_blocks.tooltip", "Whether or not the parts should destroy blocks when the impact is big enough. Default: true");
        this.add("machine_max.configuration.projectile_destroy_blocks", "Allow Projectile Destroy Blocks");
        this.add("machine_max.configuration.projectile_destroy_blocks.tooltip", "Whether or not projectiles should destroy blocks when penetrating them. Default: true");
        this.add("machine_max.configuration.ignore_assembly_tag_requirements", "Ignore Assembly Tag Requirements");
        this.add("machine_max.configuration.ignore_assembly_tag_requirements.tooltip", "Whether to ignore connector tag requirements when assembling vehicles. Default: false");
        this.add("machine_max.configuration.auto_override_official_pack", "Auto Override Official Pack");
        this.add("machine_max.configuration.auto_override_official_pack.tooltip", "Whether to auto-pack and override machine_max:official content pack on startup/reload. Default: true");
        this.add("machine_max.configuration.subpart_destroy_ticks_per_durability", "SubPart Destroy Ticks Per Durability");
        this.add("machine_max.configuration.subpart_destroy_ticks_per_durability.tooltip", "Destroy timer ticks per 1 max durability point for destroyed sub-parts. Default: 10. Range: 0 ~ 100000");
        this.add("machine_max.configuration.subpart_destroy_min_ticks", "SubPart Destroy Minimum Ticks");
        this.add("machine_max.configuration.subpart_destroy_min_ticks.tooltip", "Minimum destroy timer ticks for destroyed sub-parts. Default: 200. Range: 0 ~ 1000000");
        this.add("machine_max.configuration.subpart_destroy_advance_ticks_per_damage", "SubPart Destroy Advance Ticks Per Damage");
        this.add("machine_max.configuration.subpart_destroy_advance_ticks_per_damage.tooltip", "Additional destroy timer advance ticks per 1 damage applied to already destroyed sub-parts. Default: 20. Range: 0 ~ 100000");
    }
}
