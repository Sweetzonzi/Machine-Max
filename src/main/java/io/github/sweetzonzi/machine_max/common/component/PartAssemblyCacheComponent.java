package io.github.sweetzonzi.machine_max.common.component;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.vehicle.PartType;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.Iterator;
import java.util.Objects;

@Getter
public class PartAssemblyCacheComponent {
    private final ResourceLocation registryKey;
    @Setter
    private Iterator<String> variantIterator;
    @Setter
    private Iterator<Pair<String, String>> connectorIterator;
    private final PartType partType;

    public static final StreamCodec<RegistryFriendlyByteBuf, PartAssemblyCacheComponent> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, PartAssemblyCacheComponent::getRegistryKey,
            PartAssemblyCacheComponent::new
    );

    public PartAssemblyCacheComponent(ResourceLocation registryKey) {
        this.registryKey = registryKey;
        this.partType = MMDynamicRes.PART_TYPES.get(registryKey);
        this.variantIterator = partType.getVariantIterator();
        this.connectorIterator = partType.getVariants().get(getNextVariant()).getConnectorIterator();
    }

    public String getNextVariant() {
        if (variantIterator != null && variantIterator.hasNext()) {
            return variantIterator.next();
        } else if (variantIterator != null && !partType.variants.isEmpty()) {
            this.variantIterator = partType.getVariantIterator();
            return variantIterator.next();
        } else return "default";
    }

    /**
     * 获取下一个连接器所属的零件名称和连接器本身的名称
     *
     * @return Pair<零件名称, 连接器名称> Pair<SubPartName, ConnectorName>
     */
    public Pair<String, String> getNextConnector() {
        if (connectorIterator != null && connectorIterator.hasNext()) {
            return connectorIterator.next();
        } else if (connectorIterator != null) {
            this.connectorIterator = partType.getVariants().get(getNextVariant()).getConnectorIterator();
            return connectorIterator.next();
        } else return Pair.of(partType.getRegistryKey().toLanguageKey(), "empty");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PartAssemblyCacheComponent that = (PartAssemblyCacheComponent) o;
        return Objects.equals(partType, that.partType);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(partType);
    }
}
