package io.github.sweetzonzi.machine_max.common;

import net.neoforged.neoforge.common.ModConfigSpec;

public class MMServerConfig {
    public static final ModConfigSpec SERVER_SPEC;

    private static final ModConfigSpec.BooleanValue SHOULD_DESTROY_BLOCKS;
    private static final ModConfigSpec.BooleanValue IGNORE_ASSEMBLY_TAG_REQUIREMENTS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        SHOULD_DESTROY_BLOCKS = builder
                .comment("Whether or not the parts should destroy blocks when the impact is big enough.")
                .define("should_destroy_blocks", true);

        IGNORE_ASSEMBLY_TAG_REQUIREMENTS = builder
                .comment("Whether to ignore connector and part tag requirements when assembling vehicles.")
                .define("ignore_assembly_tag_requirements", false);

        SERVER_SPEC = builder.build();
    }

    public static boolean shouldDestroyBlocks() {
        return SHOULD_DESTROY_BLOCKS.get();
    }

    public static boolean ignoreAssemblyTagRequirements() {
        return IGNORE_ASSEMBLY_TAG_REQUIREMENTS.get();
    }
}
