package io.github.sweetzonzi.machine_max.common;

import net.neoforged.neoforge.common.ModConfigSpec;

public class MMServerConfig {
    public static final ModConfigSpec SERVER_SPEC;

    private static final ModConfigSpec.BooleanValue SHOULD_DESTROY_BLOCKS;
    private static final ModConfigSpec.BooleanValue PROJECTILE_DESTROY_BLOCKS;
    private static final ModConfigSpec.BooleanValue IGNORE_ASSEMBLY_TAG_REQUIREMENTS;
    private static final ModConfigSpec.IntValue SUBPART_DESTROY_TICKS_PER_DURABILITY;
    private static final ModConfigSpec.IntValue SUBPART_DESTROY_MIN_TICKS;
    private static final ModConfigSpec.IntValue SUBPART_DESTROY_ADVANCE_TICKS_PER_DAMAGE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        SHOULD_DESTROY_BLOCKS = builder
                .comment("Whether or not the parts should destroy blocks when the impact is big enough.")
                .define("should_destroy_blocks", true);

        PROJECTILE_DESTROY_BLOCKS = builder
                .comment("Whether or not projectiles should destroy blocks when penetrating them.")
                .define("projectile_destroy_blocks", true);

        IGNORE_ASSEMBLY_TAG_REQUIREMENTS = builder
                .comment("Whether to ignore connector and part tag requirements when assembling vehicles.")
                .define("ignore_assembly_tag_requirements", false);

        SUBPART_DESTROY_TICKS_PER_DURABILITY = builder
                .comment("Destroy timer ticks per 1 max durability point for destroyed sub-parts.")
                .defineInRange("subpart_destroy_ticks_per_durability", 10, 0, 100000);

        SUBPART_DESTROY_MIN_TICKS = builder
                .comment("Minimum destroy timer ticks for destroyed sub-parts.")
                .defineInRange("subpart_destroy_min_ticks", 200, 0, 1000000);

        SUBPART_DESTROY_ADVANCE_TICKS_PER_DAMAGE = builder
                .comment("Additional destroy timer advance ticks per 1 damage applied to already destroyed sub-parts.")
                .defineInRange("subpart_destroy_advance_ticks_per_damage", 20, 0, 100000);

        SERVER_SPEC = builder.build();
    }

    public static boolean shouldDestroyBlocks() {
        return SHOULD_DESTROY_BLOCKS.get();
    }

    public static boolean projectileDestroyBlocks() {
        return PROJECTILE_DESTROY_BLOCKS.get();
    }

    public static boolean ignoreAssemblyTagRequirements() {
        return IGNORE_ASSEMBLY_TAG_REQUIREMENTS.get();
    }

    public static int getSubPartDestroyTicksPerDurability() {
        return SUBPART_DESTROY_TICKS_PER_DURABILITY.get();
    }

    public static int getSubPartDestroyMinTicks() {
        return SUBPART_DESTROY_MIN_TICKS.get();
    }

    public static int getSubPartDestroyAdvanceTicksPerDamage() {
        return SUBPART_DESTROY_ADVANCE_TICKS_PER_DAMAGE.get();
    }
}
