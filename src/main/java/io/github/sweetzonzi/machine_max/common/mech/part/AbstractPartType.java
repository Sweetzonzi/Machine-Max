package io.github.sweetzonzi.machine_max.common.mech.part;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.VariantAttr;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

@Getter
public abstract class AbstractPartType {
    public final ResourceLocation icon;
    public final boolean shareDurability;
    public final Map<String, VariantAttr> variants;
    public final int maxStackSize;
    private ResourceLocation registryKey;

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

    protected AbstractPartType(ResourceLocation icon, boolean shareDurability, int maxStackSize,
                               Map<String, VariantAttr> variants) {
        this.icon = icon;
        this.shareDurability = shareDurability;
        this.maxStackSize = maxStackSize;
        this.variants = Collections.unmodifiableMap(new LinkedHashMap<>(variants));
    }

    public abstract String kindId();
    public VariantAttr getVariant(String name) { return variants.get(name); }
    public Iterator<String> getVariantIterator() { return variants.keySet().iterator(); }
    public ResourceLocation getDefaultIcon() { return icon; }
    public ResourceLocation getRegistryKey() {
        return Objects.requireNonNull(registryKey, "Unregistered part definition");
    }
    public void setRegistryKey(ResourceLocation id) {
        if (registryKey != null) throw new IllegalStateException("Already registered: " + registryKey);
        registryKey = Objects.requireNonNull(id);
    }
}
