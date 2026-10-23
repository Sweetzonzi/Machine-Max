package io.github.sweetzonzi.machine_max.common.mech.part;

import com.mojang.serialization.*;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPartType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.RegularPartData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import java.util.Objects;

public final class PartFactory {
    private PartFactory() {}
    public static AbstractPartType resolve(boolean clientSide, ResourceLocation id) {
        AbstractPartType type = PartDefinitions.get(clientSide, id);
        if (type == null) throw new IllegalArgumentException("Unknown or invalid part: " + id);
        return type;
    }
    public static AbstractPart<?> create(AbstractPartType type, String variant, Level level, PartCreationContext context) {
        PartDefinitions.requireCurrent(level.isClientSide(), type);
        validateVariant(type, variant == null ? "default" : variant);
        return PartKinds.get(type.kindId()).create(type, variant, level, context);
    }
    public static AbstractPart<?> restore(AbstractPartData data, Level level, PartRestoreMode mode, PartCreationContext context) {
        AbstractPartType type = validateRestore(data, level.isClientSide());
        return PartKinds.get(type.kindId()).restore(type, data, level, Objects.requireNonNull(mode), context);
    }
    /** Pure preflight for batch loaders; no native bodies or assembly registration. */
    public static AbstractPartType validateRestore(AbstractPartData data, boolean clientSide) {
        AbstractPartType type = resolve(clientSide, data.registryKey);
        validateVariant(type, data.variant);
        java.util.UUID.fromString(data.uuid);
        if (!PartKinds.get(type.kindId()).dataClass().isInstance(data)) {
            throw new IllegalArgumentException("Saved state type mismatch: " + data.registryKey);
        }
        if (!type.getVariant(data.variant).subParts.keySet().containsAll(data.subParts.keySet())) {
            throw new IllegalArgumentException("Unknown saved SubPart in " + data.registryKey);
        }
        return type;
    }
    public static RegularPart createRegular(RegularPartType type, String variant, Level level) {
        return (RegularPart) create(type, variant, level, PartCreationContext.REGULAR);
    }
    public static RegularPart restoreRegular(RegularPartData data, Level level, PartRestoreMode mode) {
        if (!(resolve(level.isClientSide(), data.registryKey) instanceof RegularPartType)) {
            throw new IllegalArgumentException("Vehicle requires regular part: " + data.registryKey);
        }
        return (RegularPart) restore(data, level, mode, PartCreationContext.REGULAR);
    }
    public static <N> DataResult<? extends AbstractPartData> decodeData(boolean clientSide, DynamicOps<N> ops, N input) {
        return ops.getMap(input).flatMap(map -> ResourceLocation.CODEC.parse(ops, map.get("registry_key")))
                .flatMap(id -> {
                    AbstractPartType type = PartDefinitions.get(clientSide, id);
                    if (type == null) return DataResult.error(() -> "Unknown or invalid part: " + id);
                    return PartKinds.get(type.kindId()).dataCodec().parse(ops, input).map(data -> (AbstractPartData) data);
                });
    }
    public static <N> DataResult<N> encodeData(boolean clientSide, DynamicOps<N> ops, AbstractPartData data) {
        AbstractPartType type = PartDefinitions.get(clientSide, data.registryKey);
        if (type == null) return DataResult.error(() -> "Unknown or invalid part: " + data.registryKey);
        return encode(PartKinds.get(type.kindId()), data, ops);
    }
    private static <D extends AbstractPartData, N> DataResult<N> encode(PartKind<?, D> kind, AbstractPartData data, DynamicOps<N> ops) {
        if (!kind.dataClass().isInstance(data)) return DataResult.error(() -> "Saved state type mismatch");
        return kind.dataCodec().encodeStart(ops, kind.dataClass().cast(data));
    }
    private static void validateVariant(AbstractPartType type, String variant) {
        if (type.getVariant(variant) == null) throw new IllegalArgumentException("Unknown variant " + type.getRegistryKey() + "/" + variant);
    }
}
