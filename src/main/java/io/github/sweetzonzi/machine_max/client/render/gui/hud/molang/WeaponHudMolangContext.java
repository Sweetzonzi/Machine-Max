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

    /** 全部有效发射管的炮管轴线都在瞄准容差内时取 1；没有发射管取 0，与装填就绪无关。 */
    @QueryBinding(value = "aimed", namespace = "weapon")
    public double weapon_aimed() {
        var tubes = weapon().tubes();
        return !tubes.isEmpty() && tubes.stream().allMatch(HudWeaponSnapshot.Tube::aimed) ? 1 : 0;
    }
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

    @QueryBinding(value = "loaded", namespace = "weapon")
    public double weapon_loaded() { return weapon().loaded(); }

    @QueryBinding(value = "reserve_remaining", namespace = "weapon")
    public double weapon_reserve_remaining() { return weapon().reserveRemaining(); }

    @QueryBinding(value = "reserve_capacity", namespace = "weapon")
    public double weapon_reserve_capacity() { return weapon().reserveCapacity(); }

    @QueryBinding(value = "reserve_infinite", namespace = "weapon")
    public double weapon_reserve_infinite() { return weapon().reserveInfinite() ? 1 : 0; }

    @StringQueryBinding(value = "reserve_text", namespace = "weapon")
    public String weapon_reserve_text() { return weapon().reserveText(); }

    @QueryBinding(value = "magazine_remaining", namespace = "weapon")
    public double weapon_magazine_remaining() { return weapon().magazineRemaining(); }

    @QueryBinding(value = "chambered_count", namespace = "weapon")
    public double weapon_chambered_count() { return weapon().chamberedCount(); }

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
    public String weapon_tube_chambered_ammo_label(double index) { var value = tube(index); return value == null ? "" : value.chamber().label(); }

    @StringQueryBinding(value = "chambered_ammo_label", namespace = "tube")
    public String tube_chambered_ammo_label() { return requireTube().chamber().label(); }

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

    @QueryBinding(value = "tube_loaded", namespace = "weapon")
    public double weapon_tube_loaded(double index) { var value = tube(index); return value == null ? 0 : value.loaded(); }

    @QueryBinding(value = "loaded", namespace = "tube")
    public double tube_loaded() { return requireTube().loaded(); }

    @QueryBinding(value = "tube_chambered", namespace = "weapon")
    public double weapon_tube_chambered(double index) { var value = tube(index); return value == null || !value.chamber().loaded() ? 0 : 1; }

    @QueryBinding(value = "chambered", namespace = "tube")
    public double tube_chambered() { return requireTube().chamber().loaded() ? 1 : 0; }

    @QueryBinding(value = "tube_magazine", namespace = "weapon")
    public double weapon_tube_magazine(double index) { var value = tube(index); return value == null || !value.magazine().loaded() ? 0 : 1; }

    @QueryBinding(value = "magazine", namespace = "tube")
    public double tube_magazine() { return requireTube().magazine().loaded() ? 1 : 0; }

    @QueryBinding(value = "tube_magazine_remaining", namespace = "weapon")
    public double weapon_tube_magazine_remaining(double index) { var value = tube(index); return value == null ? 0 : value.magazine().remaining(); }

    @QueryBinding(value = "magazine_remaining", namespace = "tube")
    public double tube_magazine_remaining() { return requireTube().magazine().remaining(); }

    @QueryBinding(value = "tube_magazine_capacity", namespace = "weapon")
    public double weapon_tube_magazine_capacity(double index) { var value = tube(index); return value == null ? 0 : value.magazine().capacity(); }

    @QueryBinding(value = "magazine_capacity", namespace = "tube")
    public double tube_magazine_capacity() { return requireTube().magazine().capacity(); }

    @StringQueryBinding(value = "tube_magazine_label", namespace = "weapon")
    public String weapon_tube_magazine_label(double index) { var value = tube(index); return value == null ? "" : value.magazine().label(); }

    @StringQueryBinding(value = "magazine_label", namespace = "tube")
    public String tube_magazine_label() { return requireTube().magazine().label(); }

    @QueryBinding(value = "tube_reserve_remaining", namespace = "weapon")
    public double weapon_tube_reserve_remaining(double index) { var value = tube(index); return value == null ? 0 : value.reserve().remaining(); }

    @QueryBinding(value = "reserve_remaining", namespace = "tube")
    public double tube_reserve_remaining() { return requireTube().reserve().remaining(); }

    @QueryBinding(value = "tube_reserve_capacity", namespace = "weapon")
    public double weapon_tube_reserve_capacity(double index) { var value = tube(index); return value == null ? 0 : value.reserve().capacity(); }

    @QueryBinding(value = "reserve_capacity", namespace = "tube")
    public double tube_reserve_capacity() { return requireTube().reserve().capacity(); }

    @QueryBinding(value = "tube_reserve_infinite", namespace = "weapon")
    public double weapon_tube_reserve_infinite(double index) { var value = tube(index); return value == null || !value.reserve().infinite() ? 0 : 1; }

    @QueryBinding(value = "reserve_infinite", namespace = "tube")
    public double tube_reserve_infinite() { return requireTube().reserve().infinite() ? 1 : 0; }

    @QueryBinding(value = "tube_swapping", namespace = "weapon")
    public double weapon_tube_swapping(double index) { var value = tube(index); return value == null || !value.swapping() ? 0 : 1; }

    @QueryBinding(value = "swapping", namespace = "tube")
    public double tube_swapping() { return requireTube().swapping() ? 1 : 0; }

    @QueryBinding(value = "tube_aimed", namespace = "weapon")
    public double weapon_tube_aimed(double index) { var value = tube(index); return value == null || !value.aimed() ? 0 : 1; }

    @QueryBinding(value = "aimed", namespace = "tube")
    public double tube_aimed() { return requireTube().aimed() ? 1 : 0; }

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
        return getEntity() instanceof WeaponHudElement weapon ? weapon.ballistics().hold(distance) : 0;
    }
}
