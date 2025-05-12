package io.github.tt432.machinemax.external.style;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import io.github.tt432.machinemax.external.MMDynamicRes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import static io.github.tt432.machinemax.MachineMax.MOD_ID;

public class StyleProvider {
    public static MutableComponent styleFactory(String tag, MutableComponent input) {
        Gson gson = new Gson();
        if (tag.startsWith("f.")) {
            return input.withStyle(style -> style.withFont(ResourceLocation.parse(MOD_ID+":"+tag.substring(2))));
        }
        if (tag.startsWith("c.")) {
            for (JsonElement json : MMDynamicRes.COLORS.values().stream().toList()) {
                ColorPalette palette = gson.fromJson(json, ColorPalette.class);
                if (palette.colors.containsKey(tag.substring(2))) {
                    return input.withStyle(style -> style.withColor(Integer.parseInt(palette.colors.get(tag.substring(2)), 16)));
                }
            }
            try {
                return input.withStyle(style -> style.withColor(Integer.parseInt(tag.substring(2), 16)));
            } catch (Exception ignored) {}

        }
        switch (tag) {
            case "s" -> {
                return Component.literal(" ").append(input);
            }
            case "t" -> {
                return Component.literal("  ").append(input);
            }
            case "br" -> {
                return input.append("\n");
            }
            case "italic" -> {
                return input.withStyle(style -> style.withItalic(true));
            }
            case "bold" -> {
                return input.withStyle(style -> style.withBold(true));
            }
            case "underline" -> {
                return input.withStyle(style -> style.withUnderlined(true));
            }
            case "strike" -> {
                return input.withStyle(style -> style.withStrikethrough(true));
            }
            case "obfuscated" -> {
                return input.withStyle(style -> style.withObfuscated(true));
            }

            case "root" -> {
                return input;
            }
            default -> {
                return input;
            }
        }
    }
}
