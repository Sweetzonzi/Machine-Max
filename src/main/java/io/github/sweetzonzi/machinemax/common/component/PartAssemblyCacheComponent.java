package io.github.sweetzonzi.machinemax.common.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machinemax.common.vehicle.PartType;
import lombok.Getter;
import lombok.Setter;

import java.util.Iterator;
import java.util.Objects;

@Getter
public class PartAssemblyCacheComponent {
    @Setter
    private Iterator<String> variantIterator;
    @Setter
    private Iterator<String> connectorIterator;
    private final PartType partType;

    public static final Codec<PartAssemblyCacheComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PartType.CODEC.fieldOf("part_type").forGetter(PartAssemblyCacheComponent::getPartType)
    ).apply(instance, PartAssemblyCacheComponent::new));

    public PartAssemblyCacheComponent(PartType partType) {
        this.partType = partType;
        this.variantIterator = partType.getVariantIterator();
        this.connectorIterator = partType.getConnectorIterator();
    }

    public String getNextVariant() {
        if (variantIterator != null && variantIterator.hasNext()) {
            return variantIterator.next();
        } else if (variantIterator != null && !partType.variants.isEmpty()) {
            this.variantIterator = partType.getVariantIterator();
            return variantIterator.next();
        } else return "default";
    }

    public String getNextConnector() {
        if (connectorIterator != null && connectorIterator.hasNext()) {
            return connectorIterator.next();
        } else if (connectorIterator != null && !partType.subParts.isEmpty()) {
            this.connectorIterator = partType.getConnectorIterator();
            return connectorIterator.next();
        } else return "default";
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
