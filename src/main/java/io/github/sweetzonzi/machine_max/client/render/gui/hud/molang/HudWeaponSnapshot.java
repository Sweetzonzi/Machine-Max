package io.github.sweetzonzi.machine_max.client.render.gui.hud.molang;

import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.*;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.IAmmoSupplier.SupplierRef;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.IAmmoSupplier.SupplierStatus;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/** 客户端 tick 生成的单控制器业务值；不包含观察者状态或可变子系统引用。 */
public record HudWeaponSnapshot(String name, String state, int ammoRemaining, int ammoCapacity,
                                boolean ammoInfinite, String ammoText, String roundLabel,
                                boolean roundMismatch, String reloadText, String activeAmmo,
                                List<Tube> tubes, List<Belt> belts) {
    public static final HudWeaponSnapshot EMPTY = new HudWeaponSnapshot("", "empty", 0, 0,
            false, "0/0", "", false, "", "", List.of(), List.of());

    public HudWeaponSnapshot {
        tubes = List.copyOf(tubes);
        belts = List.copyOf(belts);
    }

    public record Tube(SupplierRef key, String roundLabel, String state, double fill,
                       double reloadRemaining, String reloadText, String chamberedAmmoLabel,
                       String loadingBeltLabel) {}

    public record Belt(List<ResourceLocation> key, String label, int remaining, boolean selected) {
        public Belt { key = List.copyOf(key); }
    }

    public static HudWeaponSnapshot capture(WeaponControllerSubsystem controller) {
        var launchers = controller.getLaunchers().keySet().stream()
                .filter(l -> l.isActive() && !l.isDestroyed())
                .sorted(Comparator.comparing(LauncherSubsystem::getName).thenComparing(HudWeaponSnapshot::key))
                .toList();
        var available = controller.getAvailableBelts();
        var selected = controller.getSelectedBelt();
        if (selected == null) selected = available.isEmpty() ? List.of() : available.getFirst();
        var pool = controller.getAmmoPool();
        List<Belt> belts = new ArrayList<>();
        for (var belt : available) {
            var entries = pool.getOrDefault(belt, List.of());
            List<IAmmoSupplier> suppliers = entries.stream().map(e -> (IAmmoSupplier) e.loader()).toList();
            belts.add(new Belt(belt, suppliers.isEmpty() ? "" : beltLabel(suppliers.getFirst()),
                    totals(suppliers).remaining(), belt.equals(selected)));
        }
        List<Tube> tubes = new ArrayList<>();
        ProjectileType chambered = null;
        IAmmoSupplier firstSupplier = null;
        List<IAmmoSupplier> bound = new ArrayList<>();
        for (var launcher : launchers) {
            ProjectileType round = launcher.getChamberedType();
            IAmmoSupplier supplier = launcher.getCurrentSupplier();
            if (chambered == null) chambered = round;
            if (firstSupplier == null) firstSupplier = supplier;
            if (supplier != null) bound.add(supplier);
            SupplierStatus status = round != null ? SupplierStatus.READY
                    : supplier == null ? SupplierStatus.EMPTY : supplier.getStatus(launcher);
            double progress = status == SupplierStatus.READY ? 1 : status == SupplierStatus.EMPTY ? 0
                    : finiteProgress(supplier.getReloadProgress(launcher));
            double seconds = status != SupplierStatus.RELOADING ? 0
                    : supplier.getReloadTimeTicks() > 0
                    ? supplier.getReloadTimeTicks() * (1 - progress) / 20.0 : -1;
            tubes.add(new Tube(key(launcher), round != null ? round.getDisplayLabel() : roundLabel(supplier),
                    state(status), progress * 100, seconds,
                    status == SupplierStatus.RELOADING ? reloadText(seconds, progress) : "",
                    round != null ? round.getDisplayLabel() : "",
                    status == SupplierStatus.RELOADING ? beltLabel(supplier) : ""));
        }
        var entries = pool.get(selected);
        Totals total = totals(entries == null ? bound
                : entries.stream().map(e -> (IAmmoSupplier) e.loader()).toList());
        ProjectileType active = chambered != null ? chambered
                : firstSupplier != null ? firstSupplier.getSuppliedType() : null;
        String label = chambered != null ? chambered.getDisplayLabel() : roundLabel(firstSupplier);
        boolean reloading = tubes.stream().anyMatch(t -> t.state().equals("reloading"));
        String state = tubes.isEmpty() || tubes.stream().allMatch(t -> t.state().equals("empty"))
                ? "empty" : reloading ? "reloading" : "ready";
        double remaining = tubes.stream().filter(t -> t.state().equals("reloading"))
                .mapToDouble(Tube::reloadRemaining).max().orElse(-1);
        double progress = tubes.stream().filter(t -> t.state().equals("reloading"))
                .mapToDouble(t -> t.fill() / 100).max().orElse(0);
        String tail = reloading ? reloadText(remaining, progress)
                : Component.translatable("gui.machine_max.weapon_hud." + state).getString();
        ResourceLocation chamberKey = chambered == null ? null : chambered.getRegistryKey();
        return new HudWeaponSnapshot(Component.translatable(controller.getName()).getString(), state, total.remaining(), total.capacity(),
                total.infinite(), total.remaining() + "/" + (total.infinite() ? "∞" : total.capacity()),
                label, chamberKey != null && !selected.isEmpty() && !selected.contains(chamberKey), tail,
                active == null || active.getRegistryKey() == null ? "" : active.getRegistryKey().toString(), tubes, belts);
    }

    public static SupplierRef key(LauncherSubsystem launcher) {
        var subPart = launcher.getSubPart();
        return new SupplierRef(subPart.part.uuid, subPart.name, launcher.getName());
    }

    private record Totals(int remaining, int capacity, boolean infinite) {}

    private static Totals totals(Collection<IAmmoSupplier> suppliers) {
        Set<IAmmoSupplier> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        int remaining = 0, capacity = 0;
        boolean infinite = false;
        for (var supplier : suppliers) {
            if (!seen.add(supplier)) continue;
            remaining += Math.max(0, supplier.getRemainingCount());
            if (supplier.getCapacity() < 0) infinite = true;
            else capacity += supplier.getCapacity();
        }
        return new Totals(remaining, infinite ? -1 : capacity, infinite);
    }

    private static double finiteProgress(double progress) {
        return Double.isFinite(progress) ? Math.clamp(progress, 0, 1) : 0;
    }

    private static String state(SupplierStatus status) {
        return switch (status) { case READY -> "ready"; case RELOADING -> "reloading"; case EMPTY -> "empty"; };
    }

    private static String reloadText(double seconds, double progress) {
        return seconds >= 0 ? String.format(Locale.ROOT, "%.1fs", seconds)
                : String.format(Locale.ROOT, "%d%%", Math.round(progress * 100));
    }

    private static String roundLabel(IAmmoSupplier supplier) {
        return supplier instanceof RegenLoaderSubsystem regen ? regen.getCurrentRoundLabel()
                : supplier != null ? supplier.getLabel() : "";
    }

    private static String beltLabel(IAmmoSupplier supplier) {
        return supplier instanceof RegenLoaderSubsystem regen ? regen.getBeltLabel()
                : supplier != null ? supplier.getLabel() : "";
    }
}
