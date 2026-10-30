package io.github.sweetzonzi.machine_max.client.render.gui.hud.molang;

import cn.solarmoon.spark_core.molang.runtime.binding.QueryBinding;
import cn.solarmoon.spark_core.molang.runtime.binding.StringQueryBinding;
import java.util.List;

public class WeaponHudMolangContext extends HudMolangContext {
    private HudWeaponSnapshot.Tube tube;
    private HudWeaponSnapshot.Belt belt;
    public WeaponHudMolangContext() {}

    @Override protected void clearItem() { tube = null; belt = null; }
    private HudWeaponSnapshot weapon() { return frame.weapon(); }

    public ItemScope enterItem(String collection, Object key, String indexAlias) {
        int index = -1;
        HudWeaponSnapshot.Tube nextTube = null;
        HudWeaponSnapshot.Belt nextBelt = null;
        if (collection.equals("weapon.tubes")) {
            var items = weapon().tubes();
            for (int i = 0; i < items.size(); i++) if (items.get(i).key().equals(key)) {
                index = i; nextTube = items.get((int) i); break;
            }
        } else if (collection.equals("weapon.belts")) {
            var items = weapon().belts();
            for (int i = 0; i < items.size(); i++) if (items.get(i).key().equals(key)) {
                index = i; nextBelt = items.get((int) i); break;
            }
        } else throw new IllegalArgumentException("未知 HUD 集合: " + collection);
        if (index < 0) throw new IllegalArgumentException("本轮快照不存在成员: " + key);
        ItemScope restore = new ItemScope(tube, belt, alias, itemIndex);
        tube = nextTube; belt = nextBelt; alias = indexAlias; itemIndex = index;
        return restore;
    }

    public final class ItemScope implements AutoCloseable {
        private final HudWeaponSnapshot.Tube previousTube;
        private final HudWeaponSnapshot.Belt previousBelt;
        private final String previousAlias;
        private final int previousIndex;
        private boolean closed;
        private ItemScope(HudWeaponSnapshot.Tube tube, HudWeaponSnapshot.Belt belt, String alias, int index) {
            previousTube = tube; previousBelt = belt; previousAlias = alias; previousIndex = index;
        }
        @Override public void close() {
            if (closed) return;
            closed = true;
            tube = previousTube; belt = previousBelt; alias = previousAlias; itemIndex = previousIndex;
        }
    }

    private HudWeaponSnapshot.Tube requireTube() {
        if (tube == null) throw new IllegalStateException("tube.* 需要 weapon.tubes 当前项作用域");
        return tube;
    }
    private HudWeaponSnapshot.Belt requireBelt() {
        if (belt == null) throw new IllegalStateException("belt.* 需要 weapon.belts 当前项作用域");
        return belt;
    }
    private HudWeaponSnapshot.Tube tube(double i) {
        return !Double.isFinite(i) || i != Math.rint(i) || i < 0 || i >= weapon().tubes().size() ? null : weapon().tubes().get((int) i);
    }
    private HudWeaponSnapshot.Belt belt(double i) {
        return !Double.isFinite(i) || i != Math.rint(i) || i < 0 || i >= weapon().belts().size() ? null : weapon().belts().get((int) i);
    }
    @QueryBinding(value = "selected", namespace = "weapon")
    public double selected() { return frame.selected() ? 1 : 0; }
    @QueryBinding(value = "tube_count", namespace = "weapon")
    public double tubeCount() { return weapon().tubes().size(); }
    @QueryBinding(value = "belt_count", namespace = "weapon")
    public double beltCount() { return weapon().belts().size(); }
    @QueryBinding(value = "index", namespace = "tube")
    public double tubeIndex() { requireTube(); return itemIndex; }
    @QueryBinding(value = "index", namespace = "belt")
    public double beltIndex() { requireBelt(); return itemIndex; }

    @StringQueryBinding(value = "name", namespace = "weapon")
    public String weapon_name() { return weapon().name(); }

    @StringQueryBinding(value = "state", namespace = "weapon")
    public String weapon_state() { return weapon().state(); }

    @QueryBinding(value = "ammo_remaining", namespace = "weapon")
    public double weapon_ammo_remaining() { return weapon().ammoRemaining(); }

    @QueryBinding(value = "ammo_capacity", namespace = "weapon")
    public double weapon_ammo_capacity() { return weapon().ammoCapacity(); }

    @QueryBinding(value = "ammo_infinite", namespace = "weapon")
    public double weapon_ammo_infinite() { return weapon().ammoInfinite() ? 1 : 0; }

    @StringQueryBinding(value = "ammo_text", namespace = "weapon")
    public String weapon_ammo_text() { return weapon().ammoText(); }

    @StringQueryBinding(value = "round_label", namespace = "weapon")
    public String weapon_round_label() { return weapon().roundLabel(); }

    @QueryBinding(value = "round_mismatch", namespace = "weapon")
    public double weapon_round_mismatch() { return weapon().roundMismatch() ? 1 : 0; }

    @StringQueryBinding(value = "reload_text", namespace = "weapon")
    public String weapon_reload_text() { return weapon().reloadText(); }

    @StringQueryBinding(value = "active_ammo", namespace = "weapon")
    public String weapon_active_ammo() { return weapon().activeAmmo(); }

    @StringQueryBinding(value = "tube_state", namespace = "weapon")
    public String weapon_tube_state(double index) { var value = tube(index); return value == null ? "" : value.state(); }

    @StringQueryBinding(value = "state", namespace = "tube")
    public String tube_state() { return requireTube().state(); }

    @QueryBinding(value = "ready", namespace = "tube")
    public double tube_ready() { return requireTube().state().equals("ready") ? 1 : 0; }

    @QueryBinding(value = "empty", namespace = "tube")
    public double tube_empty() { return requireTube().state().equals("empty") ? 1 : 0; }

    @QueryBinding(value = "tube_ready", namespace = "weapon")
    public double weapon_tube_ready(double index) { var value = tube(index); return value != null && value.state().equals("ready") ? 1 : 0; }

    @QueryBinding(value = "tube_empty", namespace = "weapon")
    public double weapon_tube_empty(double index) { var value = tube(index); return value != null && value.state().equals("empty") ? 1 : 0; }

    @QueryBinding(value = "tube_fill", namespace = "weapon")
    public double weapon_tube_fill(double index) { var value = tube(index); return value == null ? 0 : value.fill(); }

    @QueryBinding(value = "fill", namespace = "tube")
    public double tube_fill() { return requireTube().fill(); }

    @StringQueryBinding(value = "tube_round_label", namespace = "weapon")
    public String weapon_tube_round_label(double index) { var value = tube(index); return value == null ? "" : value.roundLabel(); }

    @StringQueryBinding(value = "round_label", namespace = "tube")
    public String tube_round_label() { return requireTube().roundLabel(); }

    @StringQueryBinding(value = "tube_reload_text", namespace = "weapon")
    public String weapon_tube_reload_text(double index) { var value = tube(index); return value == null ? "" : value.reloadText(); }

    @StringQueryBinding(value = "reload_text", namespace = "tube")
    public String tube_reload_text() { return requireTube().reloadText(); }

    @QueryBinding(value = "tube_reload_remaining", namespace = "weapon")
    public double weapon_tube_reload_remaining(double index) { var value = tube(index); return value == null ? 0 : value.reloadRemaining(); }

    @QueryBinding(value = "reload_remaining", namespace = "tube")
    public double tube_reload_remaining() { return requireTube().reloadRemaining(); }

    @StringQueryBinding(value = "tube_chambered_ammo_label", namespace = "weapon")
    public String weapon_tube_chambered_ammo_label(double index) { var value = tube(index); return value == null ? "" : value.chamberedAmmoLabel(); }

    @StringQueryBinding(value = "chambered_ammo_label", namespace = "tube")
    public String tube_chambered_ammo_label() { return requireTube().chamberedAmmoLabel(); }

    @StringQueryBinding(value = "tube_loading_belt_label", namespace = "weapon")
    public String weapon_tube_loading_belt_label(double index) { var value = tube(index); return value == null ? "" : value.loadingBeltLabel(); }

    @StringQueryBinding(value = "loading_belt_label", namespace = "tube")
    public String tube_loading_belt_label() { return requireTube().loadingBeltLabel(); }

    @StringQueryBinding(value = "tube_phase", namespace = "weapon")
    public String weapon_tube_phase(double index) { var value = tube(index); return value == null ? "" : value.phase(); }

    @StringQueryBinding(value = "phase", namespace = "tube")
    public String tube_phase() { return requireTube().phase(); }

    @QueryBinding(value = "tube_has_source", namespace = "weapon")
    public double weapon_tube_has_source(double index) { var value = tube(index); return value == null ? 0 : (value.hasSource() ? 1 : 0); }

    @QueryBinding(value = "has_source", namespace = "tube")
    public double tube_has_source() { return requireTube().hasSource() ? 1 : 0; }

    @StringQueryBinding(value = "belt_label", namespace = "weapon")
    public String weapon_belt_label(double index) { var value = belt(index); return value == null ? "" : value.label(); }

    @StringQueryBinding(value = "label", namespace = "belt")
    public String belt_label() { return requireBelt().label(); }

    @QueryBinding(value = "belt_remaining", namespace = "weapon")
    public double weapon_belt_remaining(double index) { var value = belt(index); return value == null ? 0 : value.remaining(); }

    @QueryBinding(value = "remaining", namespace = "belt")
    public double belt_remaining() { return requireBelt().remaining(); }

    @QueryBinding(value = "belt_selected", namespace = "weapon")
    public double weapon_belt_selected(double index) { var value = belt(index); return value == null ? 0 : (value.selected() ? 1 : 0); }

    @QueryBinding(value = "selected", namespace = "belt")
    public double belt_selected() { return requireBelt().selected() ? 1 : 0; }

    @QueryBinding(value = "bdc_hold_rad", namespace = "weapon")
    public double hold(double distance) {
        return frame.ballistics().hold(distance);
    }
}
