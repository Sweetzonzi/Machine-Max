package io.github.sweetzonzi.machine_max.client.render.gui.hud.molang;

import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.*;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.IAmmoSupplier.ReloadPhase;
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

    /**
     * 单根管的一帧读数。<br>
     * {@code phase} 把 {@code reloading} 拆成输弹、逐发再生与整批回充三段，页面据此选择文案与显隐；
     * {@code hasSource} 表达"路由没给它摊到装弹机"，与"有机器但打空"区分开。
     */
    public record Tube(SupplierRef key, String roundLabel, String state, String phase, double fill,
                       double reloadRemaining, String reloadText, String chamberedAmmoLabel,
                       String loadingBeltLabel, boolean hasSource) {}

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
        ProjectileType offBelt = null;
        IAmmoSupplier firstSupplier = null;
        List<IAmmoSupplier> bound = new ArrayList<>();
        for (var launcher : launchers) {
            ProjectileType round = launcher.getChamberedType();
            IAmmoSupplier supplier = launcher.getCurrentSupplier();
            if (chambered == null) chambered = round;
            // 只有选中弹链存在时才谈得上"膛内弹不在选中链上"；多管混装时取第一发对不上的
            if (offBelt == null && round != null && selected != null && !selected.isEmpty()
                    && !selected.contains(round.getRegistryKey())) {
                offBelt = round;
            }
            if (firstSupplier == null) firstSupplier = supplier;
            if (supplier != null) bound.add(supplier);
            SupplierStatus status = round != null ? SupplierStatus.READY
                    : supplier == null ? SupplierStatus.EMPTY : supplier.getStatus(launcher);
            ReloadPhase phase = round != null || supplier == null || status != SupplierStatus.RELOADING
                    ? ReloadPhase.IDLE : supplier.getReloadPhase(launcher);
            double progress = status == SupplierStatus.READY ? 1 : status == SupplierStatus.EMPTY ? 0
                    : finiteProgress(supplier.getReloadProgress(launcher));
            // 秒数只按当前阶段的剩余 tick 折算：再生与整批回充的耗时都不是 reload_time
            int ticks = status == SupplierStatus.RELOADING && phase != ReloadPhase.IDLE
                    ? supplier.getRemainingReloadTicks(launcher) : -1;
            double seconds = status != SupplierStatus.RELOADING ? 0 : ticks >= 0 ? ticks / 20.0 : -1;
            tubes.add(new Tube(key(launcher), round != null ? round.getDisplayLabel() : roundLabel(supplier),
                    state(status), phase(phase), progress * 100, seconds,
                    status == SupplierStatus.RELOADING ? reloadText(seconds, progress) : "",
                    round != null ? round.getDisplayLabel() : "",
                    phase == ReloadPhase.LOADING ? beltLabel(supplier) : "",
                    supplier != null));
        }
        var entries = selected == null ? null : pool.get(selected);
        Totals total = totals(entries == null ? bound
                : entries.stream().map(e -> (IAmmoSupplier) e.loader()).toList());
        ProjectileType active = chambered != null ? chambered
                : firstSupplier != null ? firstSupplier.getSuppliedType() : null;
        ProjectileType labelRound = offBelt != null ? offBelt : chambered;
        String label = labelRound != null ? labelRound.getDisplayLabel() : roundLabel(firstSupplier);
        boolean anyReloading = tubes.stream().anyMatch(t -> t.state().equals("reloading"));
        boolean anyReady = tubes.stream().anyMatch(t -> t.state().equals("ready"));
        String state = tubes.isEmpty() ? "empty" : anyReady ? "ready" : anyReloading ? "reloading" : "empty";
        double remaining = tubes.stream().filter(t -> t.state().equals("reloading"))
                .mapToDouble(Tube::reloadRemaining).max().orElse(-1);
        double progress = tubes.stream().filter(t -> t.state().equals("reloading"))
                .mapToDouble(t -> t.fill() / 100).max().orElse(0);
        String tail = anyReloading ? reloadText(remaining, progress)
                : Component.translatable("gui.machine_max.weapon_hud." + state).getString();
        return new HudWeaponSnapshot(Component.translatable(controller.getName()).getString(), state,
                total.remaining(), total.capacity(),
                total.infinite(), total.infinite() ? "∞" : total.remaining() + "/" + total.capacity(),
                label, offBelt != null, tail,
                active == null || active.getRegistryKey() == null ? "" : active.getRegistryKey().toString(),
                tubes, belts);
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
            int count = supplier.getRemainingCount();
            if (count > 0) remaining += count;
            int supplierCapacity = supplier.getCapacity();
            if (supplierCapacity < 0) infinite = true;
            else capacity += supplierCapacity;
        }
        return new Totals(remaining, infinite ? -1 : capacity, infinite);
    }

    private static double finiteProgress(double progress) {
        return Double.isFinite(progress) ? Math.clamp(progress, 0, 1) : 0;
    }

    private static String state(SupplierStatus status) {
        return switch (status) { case READY -> "ready"; case RELOADING -> "reloading"; case EMPTY -> "empty"; };
    }

    private static String phase(ReloadPhase phase) {
        return switch (phase) {
            case LOADING -> "loading";
            case REGENERATING -> "regenerating";
            case REFILLING -> "refilling";
            case IDLE -> "idle";
        };
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
