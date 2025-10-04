package io.github.sweetzonzi.machine_max.common.vehicle;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.ConnectorAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.SubPartAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.VariantAttr;
import lombok.Getter;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@Getter
public class PartType {
    // 属性
    public final String name;//部件名称
    public final float vehicleDurabilityRate;//载具耐久度贡献系数
    public final float vehicleDamageRate;//载具伤害传递系数
    public final float vehicleDamageRateDestroyed;//部件被摧毁时的伤害传递系数
    public final float basicDurability;//部件基础耐久度
    public final float basicIntegrity;//部件基础结构完整度
    public final Map<String, VariantAttr> variants;//部件所有变体列表
    public final ResourceLocation registryKey;

    // 编解码器
    public static final Codec<Map<String, VariantAttr>> VARIANT_MAP_CODEC = Codec.either(
            VariantAttr.CODEC,
            Codec.unboundedMap(Codec.STRING, VariantAttr.CODEC)
    ).xmap(
            either -> either.map(
                    variant -> Map.of("default", variant),
                    map -> map
            ),
            map -> {
                if (map.size() == 1 && map.containsKey("default")) {
                    return Either.left(map.get("default"));
                } else {
                    return Either.right(map);
                }
            }
    );

    public static final Codec<PartType> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(PartType::getName),
            Codec.FLOAT.optionalFieldOf("vehicle_durability_rate", 0.8f).forGetter(PartType::getVehicleDurabilityRate),
            Codec.FLOAT.optionalFieldOf("vehicle_damage_rate", 1.0f).forGetter(PartType::getVehicleDamageRate),
            Codec.FLOAT.optionalFieldOf("vehicle_damage_rate_destroyed", 0.1f).forGetter(PartType::getVehicleDamageRateDestroyed),
            Codec.FLOAT.optionalFieldOf("basic_durability", 20f).forGetter(PartType::getBasicDurability),
            Codec.FLOAT.optionalFieldOf("basic_integrity", 20f).forGetter(PartType::getBasicIntegrity),
            VARIANT_MAP_CODEC.fieldOf("variants").forGetter(PartType::getVariants)
    ).apply(instance, PartType::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PartType> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull PartType decode(RegistryFriendlyByteBuf buffer) {
            String name = buffer.readUtf();
            float vehicleDurabilityRate = buffer.readFloat();
            float vehicleDamageRate = buffer.readFloat();
            float vehicleDamageRateDestroyed = buffer.readFloat();
            float basicDurability = buffer.readFloat();
            float basicIntegrity = buffer.readFloat();
            Map<String, VariantAttr> variants = buffer.readJsonWithCodec(VARIANT_MAP_CODEC);
            return new PartType(name, vehicleDurabilityRate, vehicleDamageRate, vehicleDamageRateDestroyed,
                    basicDurability, basicIntegrity, variants);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, PartType value) {
            buffer.writeUtf(value.name);
            buffer.writeFloat(value.vehicleDurabilityRate);
            buffer.writeFloat(value.vehicleDamageRate);
            buffer.writeFloat(value.vehicleDamageRateDestroyed);
            buffer.writeFloat(value.basicDurability);
            buffer.writeFloat(value.basicIntegrity);
            buffer.writeJsonWithCodec(VARIANT_MAP_CODEC, value.variants);
        }
    };

    public PartType(
            String name,
            float vehicleDurabilityRate,
            float vehicleDamageRate,
            float vehicleDamageRateDestroyed,
            float basicDurability,
            float basicIntegrity,
            Map<String, VariantAttr> variants
    ) {
        this.name = name;
        this.vehicleDurabilityRate = vehicleDurabilityRate;
        this.vehicleDamageRate = vehicleDamageRate;
        this.vehicleDamageRateDestroyed = vehicleDamageRateDestroyed;
        this.basicDurability = basicDurability;
        this.basicIntegrity = basicIntegrity;
        this.variants = variants;
        this.registryKey = ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, name);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        PartType partType = (PartType) other;
        return Objects.equals(name, partType.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    public VariantAttr getVariant(String variant) {
        return variants.get(variant);
    }

    public Iterator<String> getVariantIterator() {
        return variants.keySet().iterator();
    }

    public ResourceLocation getDefaultIcon(){
        var variant = getVariantIterator().next();
        return variants.get(variant).icon();
    }

}