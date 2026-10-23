package io.github.sweetzonzi.machine_max.common.mech.part;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPartType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.RegularPartData;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PartKinds {
    private static final Map<String, PartKind<?, ?>> KINDS = new ConcurrentHashMap<>();
    static {
        register("regular", new PartKind<>(RegularPartType.class, RegularPartType.CODEC,
                RegularPartData.class, RegularPartData.CODEC, RegularPart::new,
                (type, data, level, mode, context) -> new RegularPart(data, type, level, mode, context),
                (type, context) -> {
                    if (type.variants.isEmpty()) throw new IllegalArgumentException("Part needs at least one variant");
                    if (type.maxStackSize < 1 || type.maxStackSize > 99) throw new IllegalArgumentException("Invalid stack size");
                }));
    }

    private PartKinds() {}

    public static void register(String id, PartKind<?, ?> kind) {
        if (!id.equals("regular") && net.minecraft.resources.ResourceLocation.tryParse(id) == null) {
            throw new IllegalArgumentException("Invalid part kind: " + id);
        }
        if (KINDS.putIfAbsent(id, kind) != null) throw new IllegalStateException("Duplicate part kind: " + id);
    }

    public static PartKind<?, ?> get(String id) {
        PartKind<?, ?> result = KINDS.get(id);
        if (result == null) throw new IllegalArgumentException("Unknown part type: " + id);
        return result;
    }

    /** Only an absent type field defaults to regular; explicit invalid values produce errors. */
    public static final Codec<AbstractPartType> CODEC = new Codec<>() {
        @Override public <N> DataResult<Pair<AbstractPartType, N>> decode(DynamicOps<N> ops, N input) {
            return ops.getMap(input).flatMap(map -> {
                boolean present = map.entries().anyMatch(entry -> ops.getStringValue(entry.getFirst())
                        .result().filter("type"::equals).isPresent());
                DataResult<String> id = present ? ops.getStringValue(map.get("type")) : DataResult.success("regular");
                return id.flatMap(name -> {
                    PartKind<?, ?> kind = KINDS.get(name);
                    if (kind == null) return DataResult.error(() -> "Unknown part type: " + name);
                    return kind.definitionCodec().decode(ops, input).map(pair -> Pair.of(pair.getFirst(), pair.getSecond()));
                });
            });
        }

        @Override public <N> DataResult<N> encode(AbstractPartType value, DynamicOps<N> ops, N prefix) {
            PartKind<?, ?> kind = KINDS.get(value.kindId());
            if (kind == null || !kind.definitionClass().isInstance(value)) {
                return DataResult.error(() -> "Unregistered definition class for " + value.kindId());
            }
            return encodeDefinition(kind, value, ops).flatMap(encoded -> {
                if (value.kindId().equals("regular")) return ops.mergeToMap(prefix, ops.getMap(encoded).getOrThrow());
                return ops.getMap(encoded).flatMap(map -> {
                    var builder = ops.mapBuilder();
                    map.entries().filter(entry -> !entry.getFirst().equals(ops.createString("type")))
                            .forEach(entry -> builder.add(entry.getFirst(), entry.getSecond()));
                    return builder.add("type", ops.createString(value.kindId())).build(prefix);
                });
            });
        }
    };

    private static <T extends AbstractPartType, N> DataResult<N> encodeDefinition(
            PartKind<T, ?> kind, AbstractPartType value, DynamicOps<N> ops) {
        return kind.definitionCodec().encodeStart(ops, kind.definitionClass().cast(value));
    }
}
