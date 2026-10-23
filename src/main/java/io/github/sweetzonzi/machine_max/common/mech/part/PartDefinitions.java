package io.github.sweetzonzi.machine_max.common.mech.part;

import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPartType;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Each logical side publishes one immutable, validated generation. */
public final class PartDefinitions {
    private record Snapshot(Map<ResourceLocation, AbstractPartType> all, Map<ResourceLocation, RegularPartType> regular, boolean ready) {}
    private static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of(), false);
    private static volatile Snapshot client = EMPTY;
    private static volatile Snapshot server = EMPTY;
    private PartDefinitions() {}

    public static void begin(boolean clientSide) { publish(clientSide, EMPTY); }
    public static boolean isReady(boolean clientSide) { return snapshot(clientSide).ready; }
    public static Map<ResourceLocation, AbstractPartType> all(boolean clientSide) { return snapshot(clientSide).all; }
    public static AbstractPartType get(boolean clientSide, ResourceLocation id) { return all(clientSide).get(id); }
    public static Map<ResourceLocation, RegularPartType> regular(boolean clientSide) { return snapshot(clientSide).regular; }

    /** Read-only live view, including callers that retain the map across a reload. */
    public static Map<ResourceLocation, RegularPartType> regularView(boolean clientSide) {
        return Collections.unmodifiableMap(new AbstractMap<>() {
            @Override public Set<Entry<ResourceLocation, RegularPartType>> entrySet() { return regular(clientSide).entrySet(); }
            @Override public RegularPartType get(Object id) { return regular(clientSide).get(id); }
        });
    }

    public static List<String> validateAndPublish(boolean clientSide, Map<ResourceLocation, AbstractPartType> input) {
        begin(clientSide);
        Map<ResourceLocation, AbstractPartType> candidates = Map.copyOf(input);
        Map<ResourceLocation, Set<ResourceLocation>> edges = new LinkedHashMap<>();
        Map<ResourceLocation, String> invalid = new LinkedHashMap<>();
        candidates.forEach((id, type) -> {
            Set<ResourceLocation> dependencies = new LinkedHashSet<>();
            edges.put(id, dependencies);
            try {
                if (!id.equals(type.getRegistryKey())) throw new IllegalArgumentException("Definition id mismatch");
                PartKinds.get(type.kindId()).validate(type, new PartValidationContext(candidates, dependencies));
            } catch (RuntimeException error) {
                invalid.put(id, id + ": " + error.getMessage());
            }
        });
        boolean changed;
        do {
            changed = false;
            for (var entry : edges.entrySet()) {
                if (invalid.containsKey(entry.getKey())) continue;
                for (var dependency : entry.getValue()) {
                    if (!candidates.containsKey(dependency) || invalid.containsKey(dependency)) {
                        invalid.put(entry.getKey(), entry.getKey() + " -> " + invalid.getOrDefault(dependency, dependency + ": missing"));
                        changed = true;
                        break;
                    }
                }
            }
        } while (changed);
        Map<ResourceLocation, AbstractPartType> valid = new LinkedHashMap<>(candidates);
        invalid.keySet().forEach(valid::remove);
        Map<ResourceLocation, RegularPartType> regular = new LinkedHashMap<>();
        valid.forEach((id, type) -> { if (type instanceof RegularPartType value) regular.put(id, value); });
        publish(clientSide, new Snapshot(Map.copyOf(valid), Map.copyOf(regular), true));
        return List.copyOf(invalid.values());
    }

    public static <T extends AbstractPartType> T requireCurrent(boolean clientSide, T type) {
        if (get(clientSide, type.getRegistryKey()) != type) {
            throw new IllegalArgumentException("Unpublished or stale part definition: " + type.getRegistryKey());
        }
        return type;
    }
    private static Snapshot snapshot(boolean clientSide) { return clientSide ? client : server; }
    private static void publish(boolean clientSide, Snapshot value) { if (clientSide) client = value; else server = value; }
}
