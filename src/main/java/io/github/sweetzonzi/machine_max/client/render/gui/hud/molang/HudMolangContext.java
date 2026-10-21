package io.github.sweetzonzi.machine_max.client.render.gui.hud.molang;

import cn.solarmoon.spark_core.molang.SparkMolangContext;
import cn.solarmoon.spark_core.molang.runtime.binding.QueryBinding;
import cn.solarmoon.spark_core.molang.runtime.binding.StringQueryBinding;
import cn.solarmoon.spark_core.molang.runtime.value.MutableObjectBinding;
import cn.solarmoon.spark_core.molang.runtime.value.ObjectProperty;
import cn.solarmoon.spark_core.molang.runtime.value.Value;

import java.util.Locale;

public class HudMolangContext extends SparkMolangContext<HudRuntimeElement> {
    protected HudRuntimeElement.Frame frame = new HudRuntimeElement.Frame(
            HudViewSnapshot.EMPTY, HudPartSnapshot.EMPTY, HudWeaponSnapshot.EMPTY, false);
    protected String alias;
    protected int itemIndex = -1;
    private final MutableObjectBinding variables = new MutableObjectBinding() {
        @Override public ObjectProperty getProperty(String name) {
            if (alias != null && alias.equalsIgnoreCase(name)) return ObjectProperty.property(Value.of(itemIndex), true);
            var stored = super.getProperty(name);
            if (stored != null) return stored;
            return getEntity() == null ? null : ObjectProperty.property(Value.of(getEntity().getVariables().get(name)), false);
        }
        @Override public boolean set(String name, Value value) {
            if (alias != null && alias.equalsIgnoreCase(name)) throw new IllegalArgumentException("只读重复项下标: " + name);
            return super.set(name, value);
        }
    };

    public HudMolangContext() { super(); }

    @Override public void reset(HudRuntimeElement element, double animationTime) {
        super.reset(element, animationTime);
        frame = element.frame();
        alias = null;
        itemIndex = -1;
        clearItem();
    }

    protected void clearItem() {}
    public void useFrame(HudRuntimeElement.Frame frame) { this.frame = frame; }
    @Override public MutableObjectBinding getVariableStorage() { return variables; }

    @QueryBinding(value = "camera_pos_x", namespace = "hud")
    public double hud_camera_pos_x() { return frame.view().x(); }

    @QueryBinding(value = "camera_pos_y", namespace = "hud")
    public double hud_camera_pos_y() { return frame.view().y(); }

    @QueryBinding(value = "camera_pos_z", namespace = "hud")
    public double hud_camera_pos_z() { return frame.view().z(); }

    @QueryBinding(value = "view_heading_deg", namespace = "hud")
    public double hud_view_heading_deg() { return frame.view().heading(); }

    @QueryBinding(value = "view_pitch_deg", namespace = "hud")
    public double hud_view_pitch_deg() { return frame.view().pitch(); }

    @QueryBinding(value = "view_roll_deg", namespace = "hud")
    public double hud_view_roll_deg() { return frame.view().roll(); }

    @QueryBinding(value = "fov_deg", namespace = "hud")
    public double hud_fov_deg() { return frame.view().fov(); }

    @QueryBinding(value = "current_zoom", namespace = "hud")
    public double hud_current_zoom() { return frame.view().zoom(); }

    @QueryBinding(value = "screen_width", namespace = "hud")
    public double hud_screen_width() { return frame.view().width(); }

    @QueryBinding(value = "screen_height", namespace = "hud")
    public double hud_screen_height() { return frame.view().height(); }

    @QueryBinding(value = "durability", namespace = "local")
    public double local_durability() { return frame.part().number("local.durability"); }

    @QueryBinding(value = "max_durability", namespace = "local")
    public double local_max_durability() { return frame.part().number("local.max_durability"); }

    @QueryBinding(value = "is_destroyed", namespace = "local")
    public double local_is_destroyed() { return frame.part().number("local.is_destroyed"); }

    @QueryBinding(value = "has_connector", namespace = "local")
    public double local_has_connector(String name) { return frame.part().number("local.has_connector:" + name); }

    @QueryBinding(value = "has_subsystem", namespace = "local")
    public double local_has_subsystem(String name) { return frame.part().number("local.has_subsystem:" + name); }

    @QueryBinding(value = "subsystem_durability", namespace = "local")
    public double local_subsystem_durability(String name) { return frame.part().number("local.subsystem_durability:" + name); }

    @QueryBinding(value = "subsystem_max_durability", namespace = "local")
    public double local_subsystem_max_durability(String name) { return frame.part().number("local.subsystem_max_durability:" + name); }

    @QueryBinding(value = "subsystem_active", namespace = "local")
    public double local_subsystem_active(String name) { return frame.part().number("local.subsystem_active:" + name); }

    @QueryBinding(value = "subsystem_destroyed", namespace = "local")
    public double local_subsystem_destroyed(String name) { return frame.part().number("local.subsystem_destroyed:" + name); }

    @QueryBinding(value = "connector_offset", namespace = "local")
    public double local_connector_offset(String name, int axis) { return frame.part().number("local.connector_offset:" + name + ":" + axis); }

    @QueryBinding(value = "connector_rotation", namespace = "local")
    public double local_connector_rotation(String name, int axis) { return frame.part().number("local.connector_rotation:" + name + ":" + axis); }

    @QueryBinding(value = "hp", namespace = "global")
    public double global_hp() { return frame.part().number("global.hp"); }

    @QueryBinding(value = "max_hp", namespace = "global")
    public double global_max_hp() { return frame.part().number("global.max_hp"); }

    @QueryBinding(value = "energy", namespace = "global")
    public double global_energy() { return frame.part().number("global.energy"); }

    @QueryBinding(value = "max_energy", namespace = "global")
    public double global_max_energy() { return frame.part().number("global.max_energy"); }

    @QueryBinding(value = "get", namespace = "local")
    public double localGet(String name) { return frame.part().number("local.get:" + name); }
    @StringQueryBinding(value = "get_str", namespace = "local")
    public String localGetStr(String name) { return frame.part().string("local.get:" + name); }

    @QueryBinding(value = "get", namespace = "global")
    public double globalGet(String name) { return frame.part().number("global.get:" + name); }
    @StringQueryBinding(value = "get_str", namespace = "global")
    public String globalGetStr(String name) { return frame.part().string("global.get:" + name); }

}
