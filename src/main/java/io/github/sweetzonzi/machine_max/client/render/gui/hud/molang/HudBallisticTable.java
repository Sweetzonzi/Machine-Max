package io.github.sweetzonzi.machine_max.client.render.gui.hud.molang;

import io.github.sweetzonzi.ballistics_framework.api.trajectory.BallisticConfig;
import io.github.sweetzonzi.ballistics_framework.api.trajectory.RealisticTrajectory;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import io.github.sweetzonzi.machine_max.common.mech.projectile.type.BallisticProjectileType;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.LauncherSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.WeaponControllerSubsystem;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 海平面、静止平台、同高目标的分划标定表，角度相对水平出射轴，归零角为 0。
 * <p>
 * 表只由弹道参数与初速决定，与射击状态、平台姿态、瞄准距离都无关，因此按参数缓存：
 * 同一弹种在同一根炮管上只解算一次。求解不挂在 tick 上，只在 {@code weapon.bdc_hold_rad}
 * 首次被查询时触发；弹种切换（如 AP/HE 交替）命中缓存而不重算。
 */
public final class HudBallisticTable {
    public static final HudBallisticTable EMPTY = new HudBallisticTable(new double[0], 0);

    /** 参数完全相同即共用同一张表；AP/HE 这类只差质量与初速的弹种各占一格。 */
    private record Spec(BallisticConfig config, float speed, double maxDistance) {}

    private static final int CACHE_LIMIT = 8;
    private static final Comparator<LauncherSubsystem> TUBE_ORDER =
            Comparator.comparing(LauncherSubsystem::getName).thenComparing(HudWeaponSnapshot::key);
    private static final Map<Spec, HudBallisticTable> CACHE =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<Spec, HudBallisticTable> eldest) {
                    return size() > CACHE_LIMIT;
                }
            };

    private final double[] angles;
    private final double step;

    private HudBallisticTable(double[] angles, double step) {
        this.angles = angles;
        this.step = step;
    }

    public static HudBallisticTable resolve(WeaponControllerSubsystem controller, HudWeaponSnapshot snapshot) {
        if (snapshot.activeAmmo().isEmpty()) return EMPTY;
        var tubes = controller.getLaunchers().keySet().stream()
                .filter(l -> l.isActive() && !l.isDestroyed())
                .sorted(TUBE_ORDER).toList();
        BallisticProjectileType ballistic = null;
        float speed = 0;
        for (var tube : tubes) {
            ProjectileType type = tube.getChamberedType();
            if (type == null && tube.getCurrentSupplier() != null) type = tube.getCurrentSupplier().getSuppliedType();
            if (!(type instanceof BallisticProjectileType candidate) || type.getRegistryKey() == null
                    || !snapshot.activeAmmo().equals(type.getRegistryKey().toString())) continue;
            if (candidate.hasGuidance() || candidate.hasThrust()) return EMPTY;
            var barrel = tube.attr.staticAttribute.getBarrel();
            speed = candidate.getBaseVelocity() * barrel.velocityMultiplier() + barrel.velocityBonus();
            ballistic = candidate;
            break;
        }
        if (ballistic == null) return EMPTY;
        if (!Float.isFinite(speed) || speed <= 0 || !(ballistic.getMass() > 0)) return EMPTY;
        double maxDistance = Math.min(10000, speed * ballistic.getMaxLifetime());
        if (!(maxDistance > 0)) return EMPTY;
        var config = new BallisticConfig(ballistic.getDragFactor(), ballistic.getMass(),
                (float) (Math.PI * ballistic.getRadius() * ballistic.getRadius()),
                ballistic.getGravityFactor() * 9.81f, 1f / 60,
                Math.max(1, Math.min(36000, (int) (ballistic.getMaxLifetime() * 60))));
        var spec = new Spec(config, speed, maxDistance);
        synchronized (CACHE) {
            HudBallisticTable cached = CACHE.get(spec);
            if (cached != null) return cached;
        }
        HudBallisticTable solved = solve(config, speed, maxDistance);
        synchronized (CACHE) {
            CACHE.put(spec, solved);
        }
        return solved;
    }

    static HudBallisticTable solve(BallisticConfig config, float speed, double maxDistance) {
        if (!Double.isFinite(maxDistance) || maxDistance <= 0 || !Float.isFinite(speed) || speed <= 0) return EMPTY;
        double step = maxDistance / 128;
        double[] angles = new double[129];
        for (int i = 1; i < angles.length; i++) {
            var solutions = RealisticTrajectory.solveFiringAngle(new Vec3(0, 62, 0),
                    new Vec3(i * step, 62, 0), speed, Vec3.ZERO, config,
                    pos -> (float) Math.exp(-(pos.y - 62) / 8500), 0.0001f);
            angles[i] = solutions.stream().filter(solution -> !solution.isHighArc()
                            && solution.flightTime() <= config.timeStep() * config.maxSteps())
                    .findFirst().map(solution -> Math.asin(Math.clamp(solution.direction().y, -1, 1)))
                    .orElse(Double.NaN);
        }
        return new HudBallisticTable(angles, step);
    }

    public double hold(double distance) {
        if (!Double.isFinite(distance) || distance < 0 || angles.length == 0 || distance > step * (angles.length - 1)) return 0;
        double index = distance / step;
        int low = Math.min((int) index, angles.length - 2);
        double a = angles[low], b = angles[low + 1];
        if (!Double.isFinite(a) || !Double.isFinite(b)) return 0;
        return a + (b - a) * (index - low);
    }
}
