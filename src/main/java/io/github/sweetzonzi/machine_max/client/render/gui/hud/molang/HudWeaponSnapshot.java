package io.github.sweetzonzi.machine_max.client.render.gui.hud.molang;

import io.github.sweetzonzi.machine_max.common.mech.ammo.AmmoBelt;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.*;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.IAmmoSupplier.ReloadPhase;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.IAmmoSupplier.SupplierRef;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 客户端 tick 生成的单控制器业务值；不包含观察者身份或可变子系统引用。<br>
 * 弹药分三层计数，彼此不包含：<b>膛内</b>是已压进膛、下一发就打的那一发；
 * <b>弹匣</b>是本管已装的那一份 Belt；<b>备弹</b>是供给者手里还能给出的数量。
 * 一份 Belt 从供给者交到本管时，这些发数从备弹转到弹匣；压进膛时从弹匣转到膛内。
 * <p>
 * {@code capture} 接收观察者的瞄准点，因此 {@link Tube#aimed()} 与它同一口径——
 * 同一控制器被不同观察者看到时，对准读数随观察者变化，与 {@code weapon.selected} 同类。
 */
public record HudWeaponSnapshot(String name, String state,
                                int reserveRemaining, int reserveCapacity, boolean reserveInfinite, String reserveText,
                                int magazineRemaining, int chamberedCount,
                                String roundLabel, boolean roundMismatch, String reloadText, String activeAmmo,
                                List<Tube> tubes, List<Belt> belts) {
    public static final HudWeaponSnapshot EMPTY = new HudWeaponSnapshot("", "empty", 0, 0, false, "0/0",
            0, 0, "", false, "", "", List.of(), List.of());

    public HudWeaponSnapshot {
        tubes = List.copyOf(tubes);
        belts = List.copyOf(belts);
    }

    /**
     * 待发发数：膛内 + 弹匣。<br>
     * HUD 把它与 {@link #reserveRemaining()} 配成"现在能打的 / 还能补的"；
     * 两者相加是这支武器全部可用的发数。
     */
    public int loaded() {
        return chamberedCount + magazineRemaining;
    }

    /**
     * 单根管的一帧读数。<br>
     * {@code phase} 把 {@code reloading} 拆成输弹、逐发再生与整批回充三段，页面据此选择文案与显隐；
     * {@code hasSource} 表达"路由没给它摊到装弹机"，与"有机器但打空"区分开；
     * {@code swapping} 表示本管正在替换已装的那一份，此时射击锁定，即使膛内有弹也不算就绪；
     * {@code aimed} 表示本管此刻满足开火门限：与观察者瞄准点、含火控修正的有效发射方向的夹角在控制器瞄准容差内，与装填状态无关。
     */
    public record Tube(SupplierRef key, String roundLabel, String state, String phase,
                       double fill, double reloadRemaining, String reloadText, String loadingBeltLabel,
                       boolean swapping, boolean hasSource,
                       Chamber chamber, Magazine magazine, Reserve reserve, boolean aimed) {
        /** 本管待发发数：膛内那一发（至多 1）加弹链余量。 */
        public int loaded() {
            return (chamber.loaded() ? 1 : 0) + magazine.remaining();
        }
    }

    /** 膛内那一发：至多一发，{@code label} 是它的展示名。 */
    public record Chamber(boolean loaded, String label) {
        public static final Chamber EMPTY = new Chamber(false, "");
    }

    /** 已装的那一份 Belt：{@code capacity} 是整份长度，不是剩余容量。 */
    public record Magazine(boolean loaded, String label, int remaining, int capacity) {
        public static final Magazine EMPTY = new Magazine(false, "", 0, 0);
    }

    /** 供给者储备：{@code infinite} 为真时容量无上限，{@code capacity} 报 -1。 */
    public record Reserve(int remaining, int capacity, boolean infinite) {
        public static final Reserve EMPTY = new Reserve(0, 0, false);
    }

    public record Belt(AmmoBelt key, String label, int remaining, boolean selected) {}

    public static HudWeaponSnapshot capture(WeaponControllerSubsystem controller, @Nullable Vec3 aimPoint) {
        var launchers = controller.getLaunchers().keySet().stream()
                .filter(l -> l.isActive() && !l.isDestroyed())
                .sorted(Comparator.comparing(LauncherSubsystem::getName).thenComparing(HudWeaponSnapshot::key))
                .toList();
        // 容差取控制器自己的瞄准容差，判定与开火门限同为 LauncherSubsystem#isAimedAt；
        // 没有瞄准点（未进入控制来源）时全部不判定
        float aimTolerance = controller.attr.staticAttribute.getAimToleranceDeg();
        var available = controller.getAvailableBelts();
        var selected = controller.getSelectedBelt();
        var pool = controller.getAmmoPool();
        List<Belt> belts = new ArrayList<>();
        for (AmmoBelt belt : available) {
            var entries = pool.getOrDefault(belt, List.of());
            List<IAmmoSupplier> suppliers = entries.stream().map(e -> (IAmmoSupplier) e.loader()).toList();
            belts.add(new Belt(belt, belt.displayLabel(), totals(suppliers).remaining(), belt.equals(selected)));
        }
        List<Tube> tubes = new ArrayList<>();
        ProjectileType chambered = null;
        ProjectileType offBelt = null;
        IAmmoSupplier firstSupplier = null;
        List<IAmmoSupplier> bound = new ArrayList<>();
        int chamberedCount = 0;
        int magazineRemaining = 0;
        for (var launcher : launchers) {
            ProjectileType round = launcher.getChamberedType();
            IAmmoSupplier supplier = launcher.getCurrentSupplier();
            AmmoBelt belt = launcher.getLoadedBelt();
            boolean swapping = launcher.isSwapping();
            if (chambered == null) chambered = round;
            // 只有选中弹链存在时才谈得上"膛内弹不在选中链上"；多管混装时取第一发对不上的
            if (offBelt == null && round != null && selected != null
                    && !selected.ammoTypes().contains(round.getRegistryKey())) {
                offBelt = round;
            }
            if (firstSupplier == null) firstSupplier = supplier;
            if (supplier != null) bound.add(supplier);
            if (round != null) chamberedCount++;
            if (belt != null) magazineRemaining += belt.remaining();

            // 状态描述这根管此刻能不能打：换装中一律不算就绪，哪怕膛内还压着一发
            ReloadJob job = launcher.getReloadJob();
            boolean canFire = launcher.hasRoundToFire();
            // 补给还指望得上：机构可工作，且已有一条作业在推进、有可分配的弹，或正在再生／回充。
            // 作业在推进就够——那份弹已按作业身份预留，可分配数量为 0 不代表这一份不会到
            boolean supplyComing = supplier != null
                    && supplier.isSupplyOperational()
                    && (job != null || supplier.hasAmmo() || supplier.getRefillPhase() != ReloadPhase.IDLE);
            String state = swapping ? "reloading"
                    : canFire ? "ready"
                    : supplyComing ? "reloading" : "empty";
            boolean loading = state.equals("reloading");
            // 有作业 = 这一份正在推进；否则报供给者此刻在忙的那一段（逐发再生 / 整批回充）
            ReloadPhase phase = !loading ? ReloadPhase.IDLE
                    : job != null ? ReloadPhase.LOADING
                    : supplier != null ? supplier.getRefillPhase() : ReloadPhase.IDLE;
            double progress = state.equals("ready") ? 1
                    : job != null ? finiteProgress(job.progress())
                    : loading && phase != ReloadPhase.IDLE ? finiteProgress(supplier.getRefillProgress()) : 0;
            // 秒数只按当前阶段的剩余 tick 折算：再生与整批回充的耗时都不是 reload_time
            int ticks = job != null ? job.remainingTicks()
                    : loading && phase != ReloadPhase.IDLE ? supplier.getRefillRemainingTicks() : -1;
            double seconds = !loading ? 0 : ticks >= 0 ? ticks / 20.0 : -1;
            Totals reserve = supplier == null ? null : totals(List.of(supplier));
            tubes.add(new Tube(key(launcher), round != null ? round.getDisplayLabel() : roundLabel(supplier),
                    state, phase(phase), progress * 100, seconds,
                    loading ? reloadText(seconds, progress) : "",
                    phase == ReloadPhase.LOADING ? beltLabel(supplier) : "",
                    swapping, supplier != null,
                    new Chamber(round != null, round != null ? round.getDisplayLabel() : ""),
                    belt == null ? Magazine.EMPTY
                            : new Magazine(true, belt.displayLabel(), belt.remaining(), belt.length()),
                    reserve == null ? Reserve.EMPTY
                            : new Reserve(reserve.remaining(), reserve.capacity(), reserve.infinite()),
                    aimPoint != null && launcher.isAimedAt(aimPoint, aimTolerance)));
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
                total.remaining(), total.capacity(), total.infinite(),
                total.infinite() ? "∞" : total.remaining() + "/" + total.capacity(),
                magazineRemaining, chamberedCount,
                label, offBelt != null, tail,
                active == null || active.getRegistryKey() == null ? "" : active.getRegistryKey().toString(),
                tubes, belts);
    }

    /**
     * 一根管的稳定键：宿主 SubPart ID + 该管的子系统名。<br>
     * 只用于 HUD 在同一台载具的两帧之间认出"还是这根管"，不参与路由或占用判定。
     */
    public static SupplierRef key(LauncherSubsystem launcher) {
        return new SupplierRef(launcher.getSubPart().getId(), launcher.getName());
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

    private static String beltLabel(IAmmoSupplier supplier) {
        return supplier == null ? "" : supplier.getLabel();
    }

    private static String roundLabel(IAmmoSupplier supplier) {
        ProjectileType supplied = supplier == null ? null : supplier.getSuppliedType();
        return supplied != null ? supplied.getDisplayLabel() : "";
    }
}
