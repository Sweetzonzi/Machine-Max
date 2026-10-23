package io.github.sweetzonzi.machine_max.common.mech.part;

import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.Set;

/** Candidate lookup is restricted to load-time validators. Required edges drive failure propagation. */
public final class PartValidationContext {
    private final Map<ResourceLocation, AbstractPartType> candidates;
    private final Set<ResourceLocation> required;

    PartValidationContext(Map<ResourceLocation, AbstractPartType> candidates, Set<ResourceLocation> required) {
        this.candidates = candidates;
        this.required = required;
    }

    public AbstractPartType require(ResourceLocation id) {
        required.add(id);
        AbstractPartType result = candidates.get(id);
        if (result == null) throw new IllegalArgumentException("Missing required part: " + id);
        return result;
    }

    public void requireConnector(ResourceLocation id, String variant, String subPart, String connector) {
        var definition = require(id);
        var selected = definition.getVariant(variant);
        if (selected == null) throw new IllegalArgumentException(id + ": unknown variant " + variant);
        var child = selected.subParts.get(subPart);
        if (child == null || !child.connectors.containsKey(connector)) {
            throw new IllegalArgumentException(id + ": unknown connector " + variant + "/" + subPart + "/" + connector);
        }
    }
}
