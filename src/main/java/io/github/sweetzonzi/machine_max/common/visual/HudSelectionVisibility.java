package io.github.sweetzonzi.machine_max.common.visual;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

public enum HudSelectionVisibility implements StringRepresentable {
    SELECTED_ONLY("selected_only"), ALWAYS("always");

    public static final Codec<HudSelectionVisibility> CODEC = StringRepresentable.fromEnum(HudSelectionVisibility::values);
    private final String name;

    HudSelectionVisibility(String name) { this.name = name; }

    @Override
    public @NotNull String getSerializedName() { return name; }

    public boolean visible(boolean weapon, boolean selected) {
        return !weapon || selected || this == ALWAYS;
    }
}
