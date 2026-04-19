package io.github.sweetzonzi.machine_max.compat.create;

import net.neoforged.fml.ModList;

public final class CreateCompat {

    public static final String MOD_ID = "create";
    private static volatile boolean checked = false;
    private static volatile boolean loaded = false;

    private CreateCompat() {
    }

    public static boolean isLoaded() {
        if (!checked) {
            loaded = ModList.get().isLoaded(MOD_ID);
            checked = true;
        }
        return loaded;
    }
}

