package io.github.sweetzonzi.machine_max.common.mech.projectile;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

@Getter
public class ProjectileType {

    private final String type;
    private final float mass;
    private final float gravityFactor;
    private final float dragFactor;
    private final float radius;

    private final float baseVelocity;
    private final float basePenetration;
    private final float baseDamage;
    private final float baseAccuracyMil;

    private final float penetrationVelocityCoefficient;
    private final float damageVelocityCoefficient;

    private final int maxLifetimeTicks;

    private ResourceLocation registryKey;

    public static final Codec<ProjectileType> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("type").forGetter(ProjectileType::getType),
        Codec.FLOAT.fieldOf("mass").forGetter(ProjectileType::getMass),
        Codec.FLOAT.optionalFieldOf("gravity_factor", 1.0f).forGetter(ProjectileType::getGravityFactor),
        Codec.FLOAT.optionalFieldOf("drag_factor", 0f).forGetter(ProjectileType::getDragFactor),
        Codec.FLOAT.optionalFieldOf("radius", 0.05f).forGetter(ProjectileType::getRadius),
        Codec.FLOAT.fieldOf("base_velocity").forGetter(ProjectileType::getBaseVelocity),
        Codec.FLOAT.fieldOf("base_penetration").forGetter(ProjectileType::getBasePenetration),
        Codec.FLOAT.fieldOf("base_damage").forGetter(ProjectileType::getBaseDamage),
        Codec.FLOAT.optionalFieldOf("base_accuracy_mil", 5.0f)
            .forGetter(ProjectileType::getBaseAccuracyMil),
        Codec.FLOAT.optionalFieldOf("penetration_velocity_coefficient", 0f)
            .forGetter(ProjectileType::getPenetrationVelocityCoefficient),
        Codec.FLOAT.optionalFieldOf("damage_velocity_coefficient", 0f)
            .forGetter(ProjectileType::getDamageVelocityCoefficient),
        Codec.INT.optionalFieldOf("max_lifetime_ticks", 200)
            .forGetter(ProjectileType::getMaxLifetimeTicks)
    ).apply(instance, ProjectileType::new));

    public ProjectileType(
        String type,
        float mass,
        float gravityFactor,
        float dragFactor,
        float radius,
        float baseVelocity,
        float basePenetration,
        float baseDamage,
        float baseAccuracyMil,
        float penetrationVelocityCoefficient,
        float damageVelocityCoefficient,
        int maxLifetimeTicks
    ) {
        this.type = type;
        this.mass = mass;
        this.gravityFactor = gravityFactor;
        this.dragFactor = dragFactor;
        this.radius = radius;
        this.baseVelocity = baseVelocity;
        this.basePenetration = basePenetration;
        this.baseDamage = baseDamage;
        this.baseAccuracyMil = baseAccuracyMil;
        this.penetrationVelocityCoefficient = penetrationVelocityCoefficient;
        this.damageVelocityCoefficient = damageVelocityCoefficient;
        this.maxLifetimeTicks = maxLifetimeTicks;
    }

    public void setRegistryKey(ResourceLocation registryKey) {
        if (this.registryKey != null)
            throw new UnsupportedOperationException("ProjectileType " + this.registryKey + " already registered, cannot register as " + registryKey + ".");
        this.registryKey = registryKey;
    }

    public static ProjectileType get(Level level, ResourceLocation key) {
        return level.isClientSide
            ? MMDynamicRes.PROJECTILE_TYPES.get(key)
            : MMDynamicRes.SERVER_PROJECTILE_TYPES.get(key);
    }
}
