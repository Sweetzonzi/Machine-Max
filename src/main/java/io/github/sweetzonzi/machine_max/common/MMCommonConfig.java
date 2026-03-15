package io.github.sweetzonzi.machine_max.common;

import net.neoforged.neoforge.common.ModConfigSpec;

public class MMCommonConfig {
    public static final ModConfigSpec COMMON_SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        COMMON_SPEC = builder.build();
    }
}
