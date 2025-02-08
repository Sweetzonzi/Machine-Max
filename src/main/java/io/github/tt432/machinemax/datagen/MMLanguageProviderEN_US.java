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
        this.add("key.category.machine_max.general", "Machine Max:General");
        this.add("key.category.machine_max.ground", "Machine Max:Ground");
        this.add("key.category.machine_max.ship", "Machine Max:Ship");
        this.add("key.category.machine_max.plane", "Machine Max:Plane");
        this.add("key.category.machine_max.mech", "Machine Max:Mech");
        this.add("key.category.machine_max.assembly", "Machine Max:Assembly");
        //按键名称
    }
}
