package io.github.tt432.machinemax.datagen;

import io.github.tt432.machinemax.external.MMDynamicRes;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

import java.util.HashMap;
import java.util.function.Supplier;

public class DynamicLanguageProvider extends LanguageProvider {
    private final Supplier<HashMap<String, String>> translation;
    private final String locale;
    public DynamicLanguageProvider(PackOutput output, String modid, String locale, Supplier<HashMap<String, String>> translation) {
        super(output, modid, locale);
        this.locale = locale;
        this.translation = translation;
    }

    @Override
    protected void addTranslations() {
        translation.get().forEach(this::add); //动态载具包内部的翻译
        //开发者自定义的翻译
        if (MMDynamicRes.CUSTOM_LANGUAGE_PROVIDERS.get(locale) instanceof OtherLanguage.CustomLanguageGetter getter) {
            getter.doTrans(this);
        }
    }

}
