package io.github.sweetzonzi.machine_max.common;

import net.neoforged.neoforge.common.ModConfigSpec;

public class MMServerConfig {
    public static final ModConfigSpec SERVER_SPEC;

    private static final ModConfigSpec.BooleanValue SHOULD_DESTROY_BLOCKS;
    private static final ModConfigSpec.BooleanValue PROJECTILE_DESTROY_BLOCKS;
    private static final ModConfigSpec.BooleanValue IGNORE_ASSEMBLY_TAG_REQUIREMENTS;
    private static final ModConfigSpec.IntValue PART_FULL_WEATHERING_TICKS;
    private static final ModConfigSpec.IntValue SUBPART_DESTROY_TICKS_PER_DURABILITY;
    private static final ModConfigSpec.IntValue SUBPART_DESTROY_MIN_TICKS;
    private static final ModConfigSpec.IntValue SUBPART_DESTROY_ADVANCE_TICKS_PER_DAMAGE;
    private static final ModConfigSpec.IntValue SUBPART_DESTROY_ADVANCE_FLOOR_TICKS;

    // ==================== 爆炸系统 ====================

    private static final ModConfigSpec.BooleanValue EXPLOSION_BLOCK_DAMAGE;
    private static final ModConfigSpec.DoubleValue EXPLOSION_TERRAIN_DAMAGE_MULTIPLIER;
    private static final ModConfigSpec.IntValue EXPLOSION_MAX_ACTIVE_INSTANCES;
    private static final ModConfigSpec.DoubleValue EXPLOSION_FRONT_SPEED_DEFAULT;
    private static final ModConfigSpec.DoubleValue EXPLOSION_INTERACTION_CUTOFF;
    private static final ModConfigSpec.DoubleValue EXPLOSION_ARMOR_K;
    private static final ModConfigSpec.DoubleValue EXPLOSION_BLOCK_DAMAGE_FACTOR;

    // ==================== 碰撞撞击 ====================

    private static final ModConfigSpec.DoubleValue COLLISION_APPROACH_EPSILON;
    private static final ModConfigSpec.DoubleValue COLLISION_IMPULSE_PER_DURABILITY;
    private static final ModConfigSpec.DoubleValue COLLISION_DAMAGE_PER_IMPULSE;

    // ==================== 命中冲量 ====================

    private static final ModConfigSpec.DoubleValue IMPACT_IMPULSE_PER_DAMAGE_RATIO;
    private static final ModConfigSpec.DoubleValue MELEE_IMPULSE_PER_DAMAGE_RATIO;
    private static final ModConfigSpec.DoubleValue IMPACT_MAX_SPEED;
    private static final ModConfigSpec.DoubleValue BLAST_IMPULSE_PER_DAMAGE_RATIO;
    private static final ModConfigSpec.DoubleValue BLAST_MAX_SPEED;

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

        PART_FULL_WEATHERING_TICKS = builder
                .comment("每个部件从全新状态达到最大老化程度所需的服务端 tick 数。默认值：2000（100 秒）。")
                .defineInRange("part_full_weathering_ticks", 2000, 1, 1_000_000);

        SUBPART_DESTROY_TICKS_PER_DURABILITY = builder
                .comment("Destroy timer ticks per 1 max durability point for destroyed sub-parts.")
                .defineInRange("subpart_destroy_ticks_per_durability", 10, 0, 100000);

        SUBPART_DESTROY_MIN_TICKS = builder
                .comment("Minimum destroy timer ticks for destroyed sub-parts.")
                .defineInRange("subpart_destroy_min_ticks", 200, 0, 1000000);

        SUBPART_DESTROY_ADVANCE_TICKS_PER_DAMAGE = builder
                .comment("Additional destroy timer advance ticks per 1 damage applied to already destroyed sub-parts.")
                .defineInRange("subpart_destroy_advance_ticks_per_damage", 20, 0, 100000);

        SUBPART_DESTROY_ADVANCE_FLOOR_TICKS = builder
                .comment("Lowest destroy timer value that additional damage may advance a destroyed sub-part to.",
                        "100 ticks = 5 seconds. 0 disables the floor. A value at or above the part's initial timer disables acceleration entirely.")
                .defineInRange("subpart_destroy_advance_floor_ticks", 100, 0, 100000);

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

        EXPLOSION_BLOCK_DAMAGE_FACTOR = builder
                .comment("Converts explosion damage points into block durability units when judging terrain destruction.",
                        "Blast damage is clamped inside near_radius, so a 1 m^2 block face always receives base_damage points at point blank; block thresholds are DamageUtil.getMaxBlockDurability (general blocks: 100 * (0.01 + hardness), i.e. stone ~151, dirt ~102).",
                        "Therefore base_damage * factor must exceed the material threshold for craters to form; with this factor the crater radius is approximately near_radius.",
                        "Default 8.0 lets a base_damage ~20 charge shatter stone at point blank.")
                .defineInRange("explosion_block_damage_factor", 8.0, 0.0, 1000.0);

        // ==================== 碰撞撞击 ====================

        COLLISION_APPROACH_EPSILON = builder
                .comment("Lower bound of the normal-approach speed (m/s) used as the impact gate, and the sole speed threshold of the single-point instant-break branch. Below this, a contact counts as resting.")
                .defineInRange("collision_approach_epsilon", 0.05, 0.0, 10.0);

        COLLISION_IMPULSE_PER_DURABILITY = builder
                .comment("C_J: normal impulse (N*s) needed to break one point of block durability. Derived from reference vehicles, not from the legacy energy constant 250.")
                .defineInRange("collision_impulse_per_durability", 100.0, 0.0001, 1.0e6);

        COLLISION_DAMAGE_PER_IMPULSE = builder
                .comment("C_D: collision damage (HP) per N*s of the part's own impulse share in ordinary impacts. Linear first version.")
                .defineInRange("collision_damage_per_impulse", 0.003, 0.0, 10.0);

        // ==================== 命中冲量 ====================

        IMPACT_IMPULSE_PER_DAMAGE_RATIO = builder
                .comment("C_hit: impulse (N*s) per 1.0 of the damage-to-max-durability ratio for the default row",
                        "(projectile hits and anything whose source type is not recognised as melee or explosion).",
                        "Small hits stay linear in that ratio (C_hit * ratio), so ordering between attacks is preserved;",
                        "the asymptote is impact_max_speed * part mass, so heavy parts always respond less than light ones.",
                        "Calibration anchors: pistol on a 24 kg / 30 HP door ~= 1 N*s; 125 mm class round on the same door ~= 20 N*s;",
                        "vanilla arrow on the same door ~= 0.8 N*s.")
                .defineInRange("impact_impulse_per_damage_ratio", 6.0, 0.0, 1.0e6);

        MELEE_IMPULSE_PER_DAMAGE_RATIO = builder
                .comment("C_melee: impulse (N*s) per 1.0 of the damage-to-max-durability ratio for a melee strike.",
                        "A swing brings body weight into the blow, so one point of durability damage stands for about an order of",
                        "magnitude more momentum than a projectile does; sharing the projectile slope leaves a sword hit on a car",
                        "door invisible (1 degree of hinge swing at most).",
                        "Only the slope differs: the asymptote stays impact_max_speed, because a swing cannot push anything faster.",
                        "Calibration anchors on a 24 kg / 30 HP door: bare hand ~= 2.6 N*s (0.7 deg); plain iron sword ~= 14 N*s (3.7 deg);",
                        "fully buffed critical axe ~= 43 N*s (11 deg), which stays just below the joint driver's 180 deg/s.")
                .defineInRange("melee_impulse_per_damage_ratio", 80.0, 0.0, 1.0e7);

        IMPACT_MAX_SPEED = builder
                .comment("V_hit: the speed (m/s) that one ordinary hit approaches asymptotically, scaled by the part's mass.",
                        "Shared by the default row and the melee row.",
                        "The asymptote is never reached, so the ceiling itself stays inside the damage law instead of clipping it.")
                .defineInRange("impact_max_speed", 5.0, 0.0, 1000.0);

        BLAST_IMPULSE_PER_DAMAGE_RATIO = builder
                .comment("C_blast: impulse (N*s) per 1.0 of the damage-to-max-durability ratio for an explosion.",
                        "Same law and same asymptote shape as the other rows, only a much steeper slope:",
                        "a detonation spreads its momentum over a wide frontal area and a longer time, about 500 times more",
                        "impulse per point of durability than a bullet (and about 38 times more than a sword blow).",
                        "Calibration anchor: vanilla TNT (~40 points) on a 550 kg / 200 HP hull ~= 575 N*s (1.05 m/s).")
                .defineInRange("blast_impulse_per_damage_ratio", 3000.0, 0.0, 1.0e9);

        BLAST_MAX_SPEED = builder
                .comment("V_blast: the speed (m/s) that one explosion approaches asymptotically, scaled by the part's mass.")
                .defineInRange("blast_max_speed", 25.0, 0.0, 1000.0);

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

    /** 返回部件从全新状态达到最大老化程度所需的服务端 tick 数。 */
    public static int getPartFullWeatheringTicks() {
        return PART_FULL_WEATHERING_TICKS.get();
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

    /** 追加伤害推进倒计时时的下限（tick）；0 = 不设下限。 */
    public static int getSubPartDestroyAdvanceFloorTicks() {
        return SUBPART_DESTROY_ADVANCE_FLOOR_TICKS.get();
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

    /** 伤害点数 → 方块耐久单位的换算系数（决定弹坑半径 ≈ near_radius）。 */
    public static float explosionBlockDamageFactor() {
        return EXPLOSION_BLOCK_DAMAGE_FACTOR.get().floatValue();
    }

    // ==================== 碰撞撞击 ====================

    /** 撞击资格门的下限与即时破坏的唯一速度门槛（m/s）。 */
    public static float collisionApproachEpsilon() {
        return COLLISION_APPROACH_EPSILON.get().floatValue();
    }

    /** C_J：每点方块耐久的法向冲量门槛（N·s / 耐久点）。 */
    public static float collisionImpulsePerDurability() {
        return COLLISION_IMPULSE_PER_DURABILITY.get().floatValue();
    }

    /** C_D：部件自承冲量到碰撞伤害的换算（HP / (N·s)）。 */
    public static float collisionDamagePerImpulse() {
        return COLLISION_DAMAGE_PER_IMPULSE.get().floatValue();
    }

    // ==================== 命中冲量 ====================

    /** C_hit：普通命中每 1.0 伤害/耐久比对应的冲量（N·s）。 */
    public static float impactImpulsePerDamageRatio() {
        return IMPACT_IMPULSE_PER_DAMAGE_RATIO.get().floatValue();
    }

    /** V_hit：一次普通命中能推到的速度渐近线（m/s），按零件质量缩放。 */
    public static float impactMaxSpeed() {
        return IMPACT_MAX_SPEED.get().floatValue();
    }

    /** C_melee：近战命中每 1.0 伤害/耐久比对应的冲量（N·s）。 */
    public static float meleeImpulsePerDamageRatio() {
        return MELEE_IMPULSE_PER_DAMAGE_RATIO.get().floatValue();
    }

    /** C_blast：爆炸每 1.0 伤害/耐久比对应的冲量（N·s）。 */
    public static float blastImpulsePerDamageRatio() {
        return BLAST_IMPULSE_PER_DAMAGE_RATIO.get().floatValue();
    }

    /** V_blast：一次爆炸能推到的速度渐近线（m/s），按零件质量缩放。 */
    public static float blastMaxSpeed() {
        return BLAST_MAX_SPEED.get().floatValue();
    }
}
