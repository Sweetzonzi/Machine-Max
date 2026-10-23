package io.github.sweetzonzi.machine_max.common.mech.part;

import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.SubPartData;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

@Getter
public abstract class AbstractPartData {
    public final ResourceLocation registryKey;
    public final String name;
    public final String uuid;
    public final String variant;
    public final float sharedDurabilityRatio;
    public final String textureName;
    public final Map<String, SubPartData> subParts;

    protected AbstractPartData(ResourceLocation registryKey, String name, String uuid, String variant,
                               float sharedDurabilityRatio, String textureName, Map<String, SubPartData> subParts) {
        this.registryKey = registryKey;
        this.name = name;
        this.uuid = uuid;
        this.variant = variant;
        this.sharedDurabilityRatio = sharedDurabilityRatio;
        this.textureName = textureName;
        this.subParts = subParts;
    }

    protected AbstractPartData(AbstractPart<?> part) {
        this.registryKey = part.type.getRegistryKey();
        this.name = part.name;
        this.uuid = part.uuid.toString();
        this.variant = part.variantName;
        float max = part.getSharedMaxDurability();
        this.sharedDurabilityRatio = part.type.shareDurability && max > 0f
                ? Math.clamp(part.getSharedDurability() / max, 0f, 1f) : 1f;
        this.textureName = part.textureName;
        this.subParts = new HashMap<>();
        part.subParts.forEach((key, subPart) -> subParts.put(key, new SubPartData(subPart)));
    }
}
