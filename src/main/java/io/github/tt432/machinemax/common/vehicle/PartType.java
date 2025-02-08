package io.github.tt432.machinemax.common.vehicle;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.vehicle.attr.SubPartAttr;
import lombok.Getter;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.RegistryBuilder;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Getter
public class PartType {
    //属性
    public final String name;//部件名称(兼做注册id)
    public final Map<String, ResourceLocation> variants;//部件变体(引擎，轮胎，AE86，之类)
    public final List<ResourceLocation> textures;
    public final ResourceLocation animation;
    public final float basicDurability;//部件基础耐久度
    //       public final List<TagKey<Part>> tags,//部件标签(引擎，轮胎，AE86，之类)
    //TODO:子系统(引擎功能，车门控制，转向…等)
    public final Map<String, SubPartAttr> subParts;
    public final ResourceLocation registryKey;
    //编解码器
    public static final Codec<Map<String, ResourceLocation>> VARIANT_MAP_CODEC = Codec.either(
            // 尝试解析为单个ResourceLocation（单值模式）
            ResourceLocation.CODEC,
            // 如果失败，尝试解析为Map（多键模式）
            Codec.unboundedMap(Codec.STRING, ResourceLocation.CODEC)
    ).xmap(
            // 输入转换：将单值包装成"default"键的Map
            either -> either.map(
                    loc -> Map.of("default", loc), // 单值转Map
                    map -> map                     // 直接使用Map
            ),
            // 输出转换：如果Map只有"default"键，则序列化为单值
            map -> {
                if (map.size() == 1 && map.containsKey("default")) {
                    return Either.left(map.get("default")); // 序列化为单值
                } else return Either.right(map); // 序列化为Map
            }
    );
    public static final Codec<PartType> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("nameA").forGetter(PartType::getName),
            VARIANT_MAP_CODEC.fieldOf("variants").forGetter(PartType::getVariants),
            ResourceLocation.CODEC.listOf().fieldOf("textures").forGetter(PartType::getTextures),
            ResourceLocation.CODEC.optionalFieldOf("animation",
                    ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "empty")).forGetter(PartType::getAnimation),
            Codec.FLOAT.fieldOf("basic_durability").forGetter(PartType::getBasicDurability),
            SubPartAttr.MAP_CODEC.fieldOf("sub_parts").forGetter(PartType::getSubParts)
    ).apply(instance, PartType::new));

    public static final ResourceKey<Registry<PartType>> PART_REGISTRY_KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "part_type"));

    public PartType(
            String name,
            Map<String, ResourceLocation> variants,
            List<ResourceLocation> textures,
            ResourceLocation animation,
            float basicDurability,
//        List<TagKey<Part>> tags,//部件标签(引擎，轮胎，AE86，之类)
            Map<String, SubPartAttr> subParts
    ) {
        this.name = name;
        this.variants = variants;
        this.textures = textures;
        this.animation = animation;
        this.basicDurability = basicDurability;
        this.subParts = subParts;
        this.registryKey = ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, name);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        PartType partType = (PartType) other;
        return Objects.equals(registryKey, partType.registryKey);

    }

    @Override
    public int hashCode() {
        return registryKey.hashCode();
    }
}
