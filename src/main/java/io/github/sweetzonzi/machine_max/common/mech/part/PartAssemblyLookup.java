package io.github.sweetzonzi.machine_max.common.mech.part;

import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.IPartAssembly;
import net.minecraft.world.level.Level;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;

/** External assembly adapters resolve existing owners without registering fake vehicles. */
public final class PartAssemblyLookup {
    private static final CopyOnWriteArrayList<BiFunction<Level, UUID, IPartAssembly>> ADAPTERS = new CopyOnWriteArrayList<>();
    private PartAssemblyLookup() {}
    public static void register(BiFunction<Level, UUID, IPartAssembly> adapter) { ADAPTERS.addIfAbsent(adapter); }
    public static IPartAssembly findClient(Level level, UUID id) {
        if (!level.isClientSide()) throw new IllegalArgumentException("Client lookup requires client level");
        IPartAssembly vehicle = ObjectManager.clientAllVehicles.get(id);
        if (vehicle != null && vehicle.getLevel() == level) return vehicle;
        for (var adapter : ADAPTERS) {
            IPartAssembly assembly = adapter.apply(level, id);
            if (assembly != null && assembly.getLevel() == level && assembly.getAssemblyId().equals(id)) return assembly;
        }
        return null;
    }
}
