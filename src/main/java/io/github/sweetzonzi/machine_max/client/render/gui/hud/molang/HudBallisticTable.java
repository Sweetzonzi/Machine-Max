package io.github.sweetzonzi.machine_max.client.render.gui.hud.molang;

import io.github.sweetzonzi.ballistics_framework.api.trajectory.BallisticConfig;
import io.github.sweetzonzi.ballistics_framework.api.trajectory.RealisticTrajectory;
import io.github.sweetzonzi.machine_max.common.mech.projectile.type.BallisticProjectileType;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.WeaponControllerSubsystem;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;

/** 海平面、静止平台、同高目标的分划标定表，角度相对水平出射轴，归零角为 0。 */
public final class HudBallisticTable {
    public static final HudBallisticTable EMPTY = new HudBallisticTable(null, new double[0], 0);
    private record Key(BallisticProjectileType type, float speed) {}
    private final Key key;
    private final double[] angles;
    private final double step;

    private HudBallisticTable(Key key, double[] angles, double step) {
        this.key = key; this.angles = angles; this.step = step;
    }

    public static HudBallisticTable update(HudBallisticTable previous, WeaponControllerSubsystem controller,
                                           HudWeaponSnapshot snapshot) {
        if (snapshot.activeAmmo().isEmpty()) return EMPTY;
        var tubes = controller.getLaunchers().keySet().stream()
                .filter(l -> l.isActive() && !l.isDestroyed())
                .sorted(Comparator.comparing(io.github.sweetzonzi.machine_max.common.mech.subsystem.LauncherSubsystem::getName)
                        .thenComparing(HudWeaponSnapshot::key)).toList();
        for (var tube : tubes) {
            io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType type = tube.getChamberedType();
            if (type == null && tube.getCurrentSupplier() != null) type = tube.getCurrentSupplier().getSuppliedType();
            if (!(type instanceof BallisticProjectileType ballistic) || type.getRegistryKey() == null
                    || !snapshot.activeAmmo().equals(type.getRegistryKey().toString())) continue;
            if (ballistic.hasGuidance() || ballistic.hasThrust()) return EMPTY;
            var barrel = tube.attr.staticAttribute.getBarrel();
            float speed = ballistic.getBaseVelocity() * barrel.velocityMultiplier() + barrel.velocityBonus();
            Key key = new Key(ballistic, speed);
            if (key.equals(previous.key)) return previous;
            if (!Float.isFinite(speed) || speed <= 0 || !(ballistic.getMass() > 0)) return EMPTY;
            double maxDistance = Math.min(10000, speed * ballistic.getMaxLifetime());
            if (!(maxDistance > 0)) return EMPTY;
            var config = new BallisticConfig(ballistic.getDragFactor(), ballistic.getMass(),
                    (float) (Math.PI * ballistic.getRadius() * ballistic.getRadius()),
                    ballistic.getGravityFactor() * 9.81f, 1f / 60,
                    Math.max(1, Math.min(36000, (int) (ballistic.getMaxLifetime() * 60))));
            var solved = solve(config, speed, maxDistance);
            return new HudBallisticTable(key, solved.angles, solved.step);
        }
        return EMPTY;
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
        return new HudBallisticTable(null, angles, step);
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
