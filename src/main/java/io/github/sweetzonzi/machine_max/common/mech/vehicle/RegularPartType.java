package io.github.sweetzonzi.machine_max.common.mech.vehicle;

import io.github.sweetzonzi.machine_max.common.mech.part.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.VariantAttr;
import lombok.Getter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

@Getter
public final class RegularPartType extends AbstractPartType {
    // 属性
    public final float vehicleDurabilityRate;//载具耐久度贡献系数
    public final float vehicleDamageRate;//载具伤害传递系数
    public final float vehicleDamageRateDestroyed;//部件被摧毁时的伤害传递系数
    public final float functionalThreshold;//工作阈值，达到后视为可正常工作


    // 编解码器
    public static final Codec<RegularPartType> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.optionalFieldOf("icon", ResourceLocation.withDefaultNamespace("missingno")).forGetter(RegularPartType::getIcon),
            Codec.FLOAT.optionalFieldOf("vehicle_durability_rate", 0.8f).forGetter(RegularPartType::getVehicleDurabilityRate),
            Codec.FLOAT.optionalFieldOf("vehicle_damage_rate", 1.0f).forGetter(RegularPartType::getVehicleDamageRate),
            Codec.FLOAT.optionalFieldOf("vehicle_damage_rate_destroyed", 0.1f).forGetter(RegularPartType::getVehicleDamageRateDestroyed),
            Codec.FLOAT.optionalFieldOf("functional_threshold", 0.3f).forGetter(RegularPartType::getFunctionalThreshold),
            Codec.BOOL.optionalFieldOf("share_durability", true).forGetter(RegularPartType::isShareDurability),
            Codec.INT.optionalFieldOf("max_stack_size", 1).forGetter(RegularPartType::getMaxStackSize),
            VARIANT_MAP_CODEC.fieldOf("variants").forGetter(RegularPartType::getVariants)
    ).apply(instance, RegularPartType::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RegularPartType> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull RegularPartType decode(RegistryFriendlyByteBuf buffer) {
            ResourceLocation icon = buffer.readResourceLocation();
            float vehicleDurabilityRate = buffer.readFloat();
            float vehicleDamageRate = buffer.readFloat();
            float vehicleDamageRateDestroyed = buffer.readFloat();
            float functionalThreshold = buffer.readFloat();
            boolean shareDurability = buffer.readBoolean();
            int maxStackSize = buffer.readInt();
            Map<String, VariantAttr> variants = buffer.readJsonWithCodec(VARIANT_MAP_CODEC);
            return new RegularPartType(icon, vehicleDurabilityRate, vehicleDamageRate, vehicleDamageRateDestroyed, functionalThreshold, shareDurability, maxStackSize, variants);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, RegularPartType value) {
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

    public RegularPartType(
            ResourceLocation icon,
            float vehicleDurabilityRate,
            float vehicleDamageRate,
            float vehicleDamageRateDestroyed,
            float functionalThreshold,
            boolean shareDurability,
            int maxStackSize,
            Map<String, VariantAttr> variants
    ) {
        super(icon, shareDurability, maxStackSize, variants);
        this.vehicleDurabilityRate = vehicleDurabilityRate;
        this.vehicleDamageRate = vehicleDamageRate;
        this.vehicleDamageRateDestroyed = vehicleDamageRateDestroyed;
        this.functionalThreshold = Math.clamp(functionalThreshold, 0f, 1f);
    }

    @Nullable
    public static RegularPartType get(Level level, ResourceLocation id) {
        AbstractPartType type = PartDefinitions.get(level.isClientSide(), id);
        return type instanceof RegularPartType regular ? regular : null;
    }

    @Override public String kindId() { return "regular"; }
}
