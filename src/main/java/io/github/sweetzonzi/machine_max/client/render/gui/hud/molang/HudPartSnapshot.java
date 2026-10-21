package io.github.sweetzonzi.machine_max.client.render.gui.hud.molang;

import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.Part;

import java.util.HashMap;
import java.util.Map;

/** local/global 查询的不可变发布值，数组坐标以字段名加轴号存储。 */
public record HudPartSnapshot(Map<String, Double> numbers, Map<String, String> strings) {
    public static final HudPartSnapshot EMPTY = new HudPartSnapshot(Map.of(), Map.of());

    public HudPartSnapshot {
        numbers = Map.copyOf(numbers);
        strings = Map.copyOf(strings);
    }

    public double number(String key) { return numbers.getOrDefault(key, 0.0); }
    public String string(String key) { return strings.get(key); }

    public static HudPartSnapshot capture(Part part) {
        if (part == null) return EMPTY;
        Map<String, Double> n = new HashMap<>();
        Map<String, String> s = new HashMap<>();
        var root = part.getRootSubPart();
        if (root != null) {
            n.put("local.durability", (double) root.getDurability());
            n.put("local.max_durability", (double) root.getMaxDurability());
            n.put("local.is_destroyed", root.isDestroyed() ? 1.0 : 0.0);
        }
        part.getConnectorsByName().forEach((key, connector) -> {
            n.put("local.has_connector:" + key, connector.hasPart() ? 1.0 : 0.0);
            for (int axis = 0; axis < 3; axis++) {
                n.put("local.connector_offset:" + key + ":" + axis, (double) connector.getPivotOffset().get(axis));
                n.put("local.connector_rotation:" + key + ":" + axis, (double) connector.getPivotRotation().get(axis));
            }
        });
        part.getSubsystemsByName().forEach((key, subsystem) -> {
            n.put("local.has_subsystem:" + key, 1.0);
            n.put("local.subsystem_durability:" + key, (double) subsystem.getDurability());
            n.put("local.subsystem_max_durability:" + key, (double) subsystem.getMaxDurability());
            n.put("local.subsystem_active:" + key, subsystem.isActive() ? 1.0 : 0.0);
            n.put("local.subsystem_destroyed:" + key, subsystem.isDestroyed() ? 1.0 : 0.0);
        });
        signals("local.get:", part.getSignalStorage(), n, s);
        var assembly = part.getAssembly();
        if (assembly != null) {
            n.put("global.hp", (double) assembly.getHp());
            n.put("global.max_hp", (double) assembly.getMaxHp());
            var controller = assembly.getSubsystemController();
            var grid = controller.getEnergyGrid();
            if (grid != null) {
                n.put("global.energy", (double) grid.getTotalStoredEnergy());
                n.put("global.max_energy", (double) grid.getMaxStoredEnergy());
            }
            signals("global.get:", controller.signalStorage, n, s);
        }
        return new HudPartSnapshot(n, s);
    }

    private static void signals(String prefix, Map<String, Object> signals, Map<String, Double> n, Map<String, String> s) {
        signals.forEach((key, value) -> {
            if (value instanceof Number number) n.put(prefix + key, number.doubleValue());
            if (value != null) s.put(prefix + key, value.toString());
        });
    }
}
