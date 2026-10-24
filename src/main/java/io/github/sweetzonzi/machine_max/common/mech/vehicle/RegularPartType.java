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

/**
 * 普通部件的静态定义，提供载具 HP 贡献、伤害传递与组装工作阈值。
 *
 * <p>定义内容由 {@link #CODEC} 解码；缺少种类字段的内容包由 {@link PartKinds#CODEC} 默认分派到此类。
 * 实例构造与恢复通过 {@link PartFactory}，不能用静态定义直接表示制造进度或库存。</p>
 */
@Getter
public final class RegularPartType extends AbstractPartType {
    // 属性
    public final float vehicleDurabilityRate;//载具耐久度贡献系数
    public final float vehicleDamageRate;//载具伤害传递系数
    public final float vehicleDamageRateDestroyed;//部件被摧毁时的伤害传递系数
    public final float functionalThreshold;//工作阈值，达到后视为可正常工作


    // 编解码器
    /**
     * 普通部件字段的 JSON Codec；不负责注册名绑定，也不自行添加种类判别字段。
     */
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

    /**
     * 普通部件定义的固定字段序网络编码。
     * <p>读写顺序必须成对维护；本编码不携带注册名和种类标签，不能作为任意扩展定义的分派协议。</p>
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, RegularPartType> STREAM_CODEC = new StreamCodec<>() {
        /**
         * 按固定字段序读取普通部件配置；所得对象尚未绑定注册名。
         */
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

        /**
         * 按与解码器相同的字段序写出配置，不包含注册名。
         */
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

    /**
     * 保存普通部件配置，将工作阈值钳制到 [0, 1]。
     * <p>注册名由装载器在解码后设置；文件内配置检查与整批种类校验在构造后分别执行。</p>
     */
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

    /**
     * 按世界逻辑端查找普通部件定义。
     * @return 当前发布的普通部件定义；缺失、装载中或注册名对应扩展种类时返回 {@code null}
     */
    @Nullable
    public static RegularPartType get(Level level, ResourceLocation id) {
        AbstractPartType type = PartDefinitions.get(level.isClientSide(), id);
        return type instanceof RegularPartType regular ? regular : null;
    }

    /**
     * 返回内建普通部件种类标识 regular。
     */
    @Override
    public String kindId() {
        return "regular";
    }
}
