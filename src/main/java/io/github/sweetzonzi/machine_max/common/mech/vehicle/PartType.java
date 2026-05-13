package io.github.sweetzonzi.machine_max.common.mech.vehicle;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.VariantAttr;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import lombok.Getter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

@Getter
public class PartType {
    // 属性
    public final ResourceLocation icon; //图标路径
    public final float vehicleDurabilityRate;//载具耐久度贡献系数
    public final float vehicleDamageRate;//载具伤害传递系数
    public final float vehicleDamageRateDestroyed;//部件被摧毁时的伤害传递系数
    public final float functionalThreshold;//工作阈值，达到后视为可正常工作
    public final boolean shareDurability;//部件内零件是否共享耐久度
    public final Map<String, VariantAttr> variants;//部件所有变体列表
    public final int maxStackSize;//部件最大堆叠数量
    private ResourceLocation registryKey = null;

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
            ResourceLocation.CODEC.optionalFieldOf("icon", ResourceLocation.withDefaultNamespace("missingno")).forGetter(PartType::getIcon),
            Codec.FLOAT.optionalFieldOf("vehicle_durability_rate", 0.8f).forGetter(PartType::getVehicleDurabilityRate),
            Codec.FLOAT.optionalFieldOf("vehicle_damage_rate", 1.0f).forGetter(PartType::getVehicleDamageRate),
            Codec.FLOAT.optionalFieldOf("vehicle_damage_rate_destroyed", 0.1f).forGetter(PartType::getVehicleDamageRateDestroyed),
            Codec.FLOAT.optionalFieldOf("functional_threshold", 0.3f).forGetter(PartType::getFunctionalThreshold),
            Codec.BOOL.optionalFieldOf("share_durability", true).forGetter(PartType::isShareDurability),
            Codec.INT.optionalFieldOf("max_stack_size", 1).forGetter(PartType::getMaxStackSize),
            VARIANT_MAP_CODEC.fieldOf("variants").forGetter(PartType::getVariants)
    ).apply(instance, PartType::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PartType> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull PartType decode(RegistryFriendlyByteBuf buffer) {
            ResourceLocation icon = buffer.readResourceLocation();
            float vehicleDurabilityRate = buffer.readFloat();
            float vehicleDamageRate = buffer.readFloat();
            float vehicleDamageRateDestroyed = buffer.readFloat();
            float functionalThreshold = buffer.readFloat();
            boolean shareDurability = buffer.readBoolean();
            int maxStackSize = buffer.readInt();
            Map<String, VariantAttr> variants = buffer.readJsonWithCodec(VARIANT_MAP_CODEC);
            return new PartType(icon, vehicleDurabilityRate, vehicleDamageRate, vehicleDamageRateDestroyed, functionalThreshold, shareDurability, maxStackSize, variants);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, PartType value) {
            buffer.writeResourceLocation(value.icon);
            buffer.writeFloat(value.vehicleDurabilityRate);
            buffer.writeFloat(value.vehicleDamageRate);
            buffer.writeFloat(value.vehicleDamageRateDestroyed);
            buffer.writeFloat(value.functionalThreshold);
            buffer.writeBoolean(value.shareDurability);
            buffer.writeInt(value.maxStackSize);
            buffer.writeJsonWithCodec(VARIANT_MAP_CODEC, value.variants);
        }
    };

    public PartType(
            ResourceLocation icon,
            float vehicleDurabilityRate,
            float vehicleDamageRate,
            float vehicleDamageRateDestroyed,
            float functionalThreshold,
            boolean shareDurability,
            int maxStackSize,
            Map<String, VariantAttr> variants
    ) {
        this.icon = icon;
        this.vehicleDurabilityRate = vehicleDurabilityRate;
        this.vehicleDamageRate = vehicleDamageRate;
        this.vehicleDamageRateDestroyed = vehicleDamageRateDestroyed;
        this.functionalThreshold = Math.clamp(functionalThreshold, 0f, 1f);
        this.shareDurability = shareDurability;
        this.maxStackSize = maxStackSize;
        this.variants = variants;
    }

    public static PartType get(Level level, ResourceLocation registryKey) {
        PartType result = null;
        if (level.isClientSide) result = MMDynamicRes.PART_TYPES.get(registryKey);
        else result = MMDynamicRes.SERVER_PART_TYPES.get(registryKey);
        if (result == null)
            throw new IllegalArgumentException("Unknown part type: " + registryKey);
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        PartType partType = (PartType) other;
        return Objects.equals(this.registryKey, partType.registryKey);
    }

    @Override
    public int hashCode() {
        return registryKey.hashCode();
    }

    public VariantAttr getVariant(String variant) {
        return variants.get(variant);
    }

    public Iterator<String> getVariantIterator() {
        return variants.keySet().iterator();
    }

    public ResourceLocation getDefaultIcon() {
        return icon;
    }

    @NotNull
    public ResourceLocation getRegistryKey() {
        if (registryKey == null) throw new IllegalStateException("PartType not registered");
        return registryKey;
    }

    /**
     * 注册部件类型，仅应被调用一次
     *
     * @param registryKey 注册名
     */
    public void setRegistryKey(ResourceLocation registryKey) {
        if (this.registryKey != null)
            throw new UnsupportedOperationException("PartType + " + this.registryKey + " already registered, cannot register as " + registryKey + ". ");
        this.registryKey = registryKey;
    }

    @Override
    public String toString() {
        return "PartType{" +
                "registryKey=" + getRegistryKey() +
                ", variants=" + variants +
                '}';
    }
}
