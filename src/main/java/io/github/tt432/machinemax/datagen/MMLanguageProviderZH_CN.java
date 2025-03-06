package io.github.tt432.machinemax.datagen;

import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

public class MMLanguageProviderZH_CN extends LanguageProvider {
    public MMLanguageProviderZH_CN(PackOutput output, String modid, String locale) {
        super(output, modid, locale);
    }

    @Override
    protected void addTranslations() {
        //按键类别
        this.add("resourceType.category.machine_max.general", "Machine Max:通用");
        this.add("resourceType.category.machine_max.ground", "Machine Max:地面载具");
        this.add("resourceType.category.machine_max.ship", "Machine Max:舰艇");
        this.add("resourceType.category.machine_max.plane", "Machine Max:飞行器");
        this.add("resourceType.category.machine_max.mech", "Machine Max:机甲");
        this.add("resourceType.category.machine_max.assembly", "Machine Max:组装");
        //按键名称
        //提示信息
        this.add("message.machine_max.leaving_vehicle", "长按%1$s键%2$s/0.50秒以离开载具");
        this.add("tooltip.machinemax.crossbar.interact", "互动以拆除：");
        this.add("tooltip.machinemax.spray_can.interact", "互动以喷涂：");
    }
}
