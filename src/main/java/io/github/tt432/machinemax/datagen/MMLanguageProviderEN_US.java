package io.github.tt432.machinemax.datagen;

import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

public class MMLanguageProviderEN_US extends LanguageProvider {
    public MMLanguageProviderEN_US(PackOutput output, String modid, String locale) {
        super(output, modid, locale);
    }

    @Override
    protected void addTranslations() {
        //按键类别
        this.add("resourceType.category.machine_max.general", "Machine Max:General");
        this.add("resourceType.category.machine_max.ground", "Machine Max:Ground");
        this.add("resourceType.category.machine_max.ship", "Machine Max:Ship");
        this.add("resourceType.category.machine_max.plane", "Machine Max:Plane");
        this.add("resourceType.category.machine_max.mech", "Machine Max:Mech");
        this.add("resourceType.category.machine_max.assembly", "Machine Max:Assembly");
        //按键名称-通用
        this.add("resourceType.machine_max.general.free_cam", "Free Camera");
        this.add("resourceType.machine_max.general.interact", "Interact with Vehicle");
        this.add("resourceType.machine_max.general.leave_vehicle", "Leave Vehicle");
        //按键名称-地面载具
        this.add("resourceType.machine_max.ground.forward", "Forward");
        this.add("resourceType.machine_max.ground.backward", "Backward");
        this.add("resourceType.machine_max.ground.leftward", "Leftward");
        this.add("resourceType.machine_max.ground.rightward", "Rightward");
        this.add("resourceType.machine_max.ground.clutch", "Clutch");
        this.add("resourceType.machine_max.ground.up_shift", "Shift Up");
        this.add("resourceType.machine_max.ground.down_shift", "Shift Down");
        //按键名称-组装
        this.add("resourceType.machine_max.assembly.cycle_connector", "Cycle Part Connector");
        this.add("resourceType.machine_max.assembly.cycle_variant", "Cycle Part Variant");

        //提示信息
        this.add("message.machine_max.leaving_vehicle", "Hold %1$s %2$s/0.50s to leave the vehicle.");
        this.add("tooltip.machinemax.crossbar.interact", "Interact to disassemble:");
        this.add("tooltip.machinemax.spray_can.interact", "Interact to paint:");
    }
}
