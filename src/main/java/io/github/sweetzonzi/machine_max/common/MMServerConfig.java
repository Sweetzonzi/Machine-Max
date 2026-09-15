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

    // ==================== 爆炸系统（见 docs/武器系统-爆炸系统详细设计.md §11.2）====================

    private static final ModConfigSpec.BooleanValue EXPLOSION_BLOCK_DAMAGE;
    private static final ModConfigSpec.DoubleValue EXPLOSION_TERRAIN_DAMAGE_MULTIPLIER;
    private static final ModConfigSpec.IntValue EXPLOSION_MAX_ACTIVE_INSTANCES;
    private static final ModConfigSpec.DoubleValue EXPLOSION_FRONT_SPEED_DEFAULT;
    private static final ModConfigSpec.DoubleValue EXPLOSION_INTERACTION_CUTOFF;
    private static final ModConfigSpec.DoubleValue EXPLOSION_ARMOR_K;

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

        // ==================== 爆炸系统 ====================

        EXPLOSION_BLOCK_DAMAGE = builder
                .comment("Global switch for explosion terrain destruction. When false, explosions still reduce ray energy (cover keeps working) but never destroy blocks.")
                .define("explosion_block_damage", true);

        EXPLOSION_TERRAIN_DAMAGE_MULTIPLIER = builder
                .comment("Multiplier applied to explosion damage when judging block destruction. 0.0 keeps cover effects but never destroys terrain.")
                .defineInRange("explosion_terrain_damage_multiplier", 1.0, 0.0, 1000.0);

        EXPLOSION_MAX_ACTIVE_INSTANCES = builder
                .comment("Maximum number of simultaneously active explosion instances per level (loop protection for chain detonations).")
                .defineInRange("explosion_max_active_instances", 32, 1, 4096);

        EXPLOSION_FRONT_SPEED_DEFAULT = builder
                .comment("Default blast front speed in m/s. Content pack JSON can override per explosion.")
                .defineInRange("explosion_front_speed_default", 20.0, 0.1, 1000.0);

        EXPLOSION_INTERACTION_CUTOFF = builder
                .comment("Ray deactivation threshold on residual energy ratio. Pure performance knob, does not affect balance.")
                .defineInRange("explosion_interaction_cutoff", 0.01, 0.0, 1.0);

        EXPLOSION_ARMOR_K = builder
                .comment("Scale factor converting the material factor of ArmorUtil.getBlockArmor into blast block armor A_block. Calibration target: stone blocks a medium charge, dirt is penetrated.")
                .defineInRange("explosion_armor_k", 0.4, 0.0, 10.0);

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

    // ==================== 爆炸系统 ====================

    /** 全局爆炸地形破坏总开关；关 → 只折减能量、不摧毁方块。 */
    public static boolean explosionBlockDamage() {
        return EXPLOSION_BLOCK_DAMAGE.get();
    }

    /** 对地形的破坏力倍乘；0.0 = 只留掩体不破坏地形。 */
    public static float explosionTerrainDamageMultiplier() {
        return EXPLOSION_TERRAIN_DAMAGE_MULTIPLIER.get().floatValue();
    }

    /** 每维度同时活跃的最大爆炸实例数（殉爆环路保护）。 */
    public static int explosionMaxActiveInstances() {
        return EXPLOSION_MAX_ACTIVE_INSTANCES.get();
    }

    /** 默认波前速度（m/s），JSON 可覆盖。 */
    public static float explosionFrontSpeedDefault() {
        return EXPLOSION_FRONT_SPEED_DEFAULT.get().floatValue();
    }

    /** 射线失活阈值（剩余能量比例）；纯性能旋钮。 */
    public static float explosionInteractionCutoff() {
        return EXPLOSION_INTERACTION_CUTOFF.get().floatValue();
    }

    /** 方块护甲系数 k_armor（材料因子 → 方块护甲）。 */
    public static float explosionArmorK() {
        return EXPLOSION_ARMOR_K.get().floatValue();
    }
}
