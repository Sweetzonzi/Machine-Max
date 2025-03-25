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
        //按键名称
        //提示信息
        this.add("message.machine_max.leaving_vehicle", "Hold %1$s %2$s/0.50s to leave the vehicle.");
        this.add("tooltip.machinemax.crossbar.interactWitchPart", "Interact to disassemble:");
        this.add("tooltip.machinemax.spray_can.interactWitchPart", "Interact to paint:");
    }
}
