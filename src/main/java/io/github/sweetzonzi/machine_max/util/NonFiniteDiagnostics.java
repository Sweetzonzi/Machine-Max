package io.github.sweetzonzi.machine_max.util;

import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** 非有限状态的观测出口，异常按键去重并限制进程内记录数量。 */
public final class NonFiniteDiagnostics {
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private NonFiniteDiagnostics() {}

    public static boolean finite(Vector3f v) {
        return Float.isFinite(v.x) && Float.isFinite(v.y) && Float.isFinite(v.z);
    }

    public static boolean finite(Vec3 v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }

    public static String world(Level level) {
        return "side=" + (level.isClientSide() ? "client" : "server")
                + " dimension=" + level.dimension().location() + " gameTime=" + level.getGameTime();
    }

    public static void reportOnce(String key, Supplier<String> details) {
        if (REPORTED.size() >= 64 || !REPORTED.add(key)) return;
        MachineMax.LOGGER.error("[MM-NaN] " + key + " " + details.get(),
                new IllegalStateException("First non-finite state observed here"));
    }

}
