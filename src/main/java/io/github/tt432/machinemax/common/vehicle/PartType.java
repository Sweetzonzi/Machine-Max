package io.github.tt432.machinemax.common.vehicle;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.vehicle.attr.ConnectorAttr;
import io.github.tt432.machinemax.common.vehicle.attr.SubPartAttr;
import io.github.tt432.machinemax.common.vehicle.attr.subsystem.AbstractSubsystemAttr;
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
    //属性
    public final String name;//部件名称(兼做注册id)
    public final Map<String, ResourceLocation> variants;//部件变体(引擎，轮胎，AE86，之类)
    public final List<ResourceLocation> textures;
    public final ResourceLocation animation;
    public final float basicDurability;//部件基础耐久度
    //       public final List<TagKey<Part>> tags,//部件标签(引擎，轮胎，AE86，之类)
    public final Map<String, AbstractSubsystemAttr> subsystems;//子系统(引擎功能，车门控制，转向…等)
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
            Codec.STRING.fieldOf("name").forGetter(PartType::getName),
            VARIANT_MAP_CODEC.fieldOf("variants").forGetter(PartType::getVariants),
            ResourceLocation.CODEC.listOf().fieldOf("textures").forGetter(PartType::getTextures),
            ResourceLocation.CODEC.optionalFieldOf("animation",
                    ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "empty")).forGetter(PartType::getAnimation),
            Codec.FLOAT.fieldOf("basic_durability").forGetter(PartType::getBasicDurability),
            AbstractSubsystemAttr.MAP_CODEC.optionalFieldOf("subsystems", Map.of()).forGetter(PartType::getSubsystems),
            SubPartAttr.MAP_CODEC.fieldOf("sub_parts").forGetter(PartType::getSubParts)
    ).apply(instance, PartType::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PartType> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull PartType decode(RegistryFriendlyByteBuf buffer) {
            String name = buffer.readUtf();
            Map<String, ResourceLocation> variants = buffer.readJsonWithCodec(VARIANT_MAP_CODEC);
            List<ResourceLocation> textures = buffer.readList(FriendlyByteBuf::readResourceLocation);
            ResourceLocation animation = buffer.readResourceLocation();
            float basicDurability = buffer.readFloat();
            Map<String, AbstractSubsystemAttr> subsystems = buffer.readJsonWithCodec(AbstractSubsystemAttr.MAP_CODEC);
            Map<String, SubPartAttr> subParts = buffer.readJsonWithCodec(SubPartAttr.MAP_CODEC);
            return new PartType(name, variants, textures, animation, basicDurability, subsystems, subParts);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, PartType value) {
            buffer.writeUtf(value.name);
            buffer.writeJsonWithCodec(VARIANT_MAP_CODEC, value.variants);
            buffer.writeCollection(value.textures, FriendlyByteBuf::writeResourceLocation);
            buffer.writeResourceLocation(value.animation);
            buffer.writeFloat(value.basicDurability);
            buffer.writeJsonWithCodec(AbstractSubsystemAttr.MAP_CODEC, value.subsystems);
            buffer.writeJsonWithCodec(SubPartAttr.MAP_CODEC, value.subParts);
        }
    };

    public static final ResourceKey<Registry<PartType>> PART_REGISTRY_KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("assembly", "part_type"));

    public PartType(
            String name,
            Map<String, ResourceLocation> variants,
            List<ResourceLocation> textures,
            ResourceLocation animation,
            float basicDurability,
//        List<TagKey<Part>> tags,//部件标签(引擎，轮胎，AE86，之类)
            Map<String, AbstractSubsystemAttr> subsystems,
            Map<String, SubPartAttr> subParts
    ) {
        this.name = name;
        this.variants = variants;
        this.textures = textures;
        this.animation = animation;
        this.basicDurability = basicDurability;
        this.subsystems = subsystems;
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

    public Iterator<String> getVariantIterator() {
        return variants.keySet().iterator();
    }

    public Iterator<String> getConnectorIterator() {
        Set<String> connectors = new HashSet<>();
        for (SubPartAttr subParts : this.subParts.values()) {//遍历零件
            for (Map.Entry<String, ConnectorAttr> connector : subParts.connectors().entrySet()) {//遍历零件的接口
                if (connector.getValue().ConnectedTo().isEmpty()) connectors.add(connector.getKey());//外部接口加入可用接口集合
            }
        }
        return connectors.iterator();
    }

    public Map<String, String> getPartOutwardConnectors() {
        Map<String, String> partConnectors = new HashMap<>(1);//获取部件所有外部对接口名称与类型
        for (SubPartAttr subParts : this.subParts.values()) {
            for (Map.Entry<String, ConnectorAttr> entry : subParts.connectors().entrySet()) {
                if (entry.getValue().ConnectedTo().isEmpty())//外部零件对接口
                    partConnectors.put(entry.getKey(), entry.getValue().type());
            }
        }
        return partConnectors;
    }
}
