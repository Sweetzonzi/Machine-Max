package io.github.sweetzonzi.machine_max.client.render.gui.hud.molang;

import cn.solarmoon.spark_core.molang.runtime.binding.QueryBinding;
import cn.solarmoon.spark_core.molang.runtime.binding.StringQueryBinding;

public class WeaponHudMolangContext extends HudMolangContext {
    private HudWeaponSnapshot.Tube tube;
    private HudWeaponSnapshot.AmmoOption ammoOption;

    public WeaponHudMolangContext() {}

    @Override protected void clearRepeat() { super.clearRepeat(); tube = null; ammoOption = null; }
    private HudWeaponSnapshot weapon() { return frame.weapon(); }

    public RepeatScope enterRepeat(String collection, Object key, String indexAlias) {
        if (collection.equals("vehicle.seats")) return super.enterRepeat(collection, key, indexAlias);
        int index = -1;
        HudWeaponSnapshot.Tube nextTube = null;
        HudWeaponSnapshot.AmmoOption nextAmmoOption = null;
        if (collection.equals("weapon.tubes")) {
            var items = weapon().tubes();
            for (int i = 0; i < items.size(); i++) if (items.get(i).key().equals(key)) {
                index = i; nextTube = items.get(i); break;
            }
        } else if (collection.equals("weapon.ammo_options")) {
            var items = weapon().ammoOptions();
            for (int i = 0; i < items.size(); i++) if (items.get(i).key().equals(key)) {
                index = i; nextAmmoOption = items.get(i); break;
            }
        } else throw new IllegalArgumentException("未知 HUD 集合: " + collection);
        if (index < 0) throw new IllegalArgumentException("本轮快照不存在成员: " + key);
        var previousTube = tube;
        var previousAmmoOption = ammoOption;
        RepeatScope restore = new RepeatScope(() -> { tube = previousTube; ammoOption = previousAmmoOption; });
        tube = nextTube; ammoOption = nextAmmoOption; alias = indexAlias; repeatIndex = index;
        return restore;
    }

    private HudWeaponSnapshot.Tube requireTube() {
        if (tube == null) throw new IllegalStateException("tube.* 需要 weapon.tubes 当前项作用域");
        return tube;
    }
    private HudWeaponSnapshot.AmmoOption requireAmmoOption() {
        if (ammoOption == null) throw new IllegalStateException("ammo_option.* 需要 weapon.ammo_options 当前项作用域");
        return ammoOption;
    }
    private HudWeaponSnapshot.Tube tube(double i) {
        return !Double.isFinite(i) || i != Math.rint(i) || i < 0 || i >= weapon().tubes().size() ? null : weapon().tubes().get((int) i);
    }
    private HudWeaponSnapshot.AmmoOption ammoOption(double i) {
        return !Double.isFinite(i) || i != Math.rint(i) || i < 0 || i >= weapon().ammoOptions().size()
                ? null : weapon().ammoOptions().get((int) i);
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
    @QueryBinding(value = "ammo_option_count", namespace = "weapon")
    public double ammoOptionCount() { return weapon().ammoOptions().size(); }
    @QueryBinding(value = "index", namespace = "tube")
    public double tubeIndex() { requireTube(); return repeatIndex; }
    @QueryBinding(value = "index", namespace = "ammo_option")
    public double ammoOptionIndex() { requireAmmoOption(); return repeatIndex; }

    @StringQueryBinding(value = "name", namespace = "weapon")
    public String weapon_name() { return weapon().name(); }
    @StringQueryBinding(value = "state", namespace = "weapon")
    public String weapon_state() { return weapon().state(); }
    @StringQueryBinding(value = "state_text", namespace = "weapon")
    public String weapon_state_text() { return weapon().stateText(); }
    @QueryBinding(value = "loaded_rounds", namespace = "weapon")
    public double weapon_loaded_rounds() { return weapon().loadedRounds(); }
    @QueryBinding(value = "reserve_remaining", namespace = "weapon")
    public double weapon_reserve_remaining() { return weapon().reserveRemaining(); }
    @QueryBinding(value = "reserve_capacity", namespace = "weapon")
    public double weapon_reserve_capacity() { return weapon().reserveCapacity(); }
    @QueryBinding(value = "reserve_infinite", namespace = "weapon")
    public double weapon_reserve_infinite() { return weapon().reserveInfinite() ? 1 : 0; }
    @StringQueryBinding(value = "reserve_text", namespace = "weapon")
    public String weapon_reserve_text() { return weapon().reserveText(); }
    @QueryBinding(value = "loaded_belt_rounds", namespace = "weapon")
    public double weapon_loaded_belt_rounds() { return weapon().loadedBeltRounds(); }
    @QueryBinding(value = "chambered_count", namespace = "weapon")
    public double weapon_chambered_count() { return weapon().chamberedCount(); }
    @StringQueryBinding(value = "round_label", namespace = "weapon")
    public String weapon_round_label() { return weapon().roundLabel(); }
    @QueryBinding(value = "chambered_round_mismatch", namespace = "weapon")
    public double weapon_chambered_round_mismatch() { return weapon().chamberedRoundMismatch() ? 1 : 0; }
    @StringQueryBinding(value = "active_ammo_id", namespace = "weapon")
    public String weapon_active_ammo_id() { return weapon().activeAmmoId(); }

    @StringQueryBinding(value = "tube_state", namespace = "weapon")
    public String weapon_tube_state(double index) { var value = tube(index); return value == null ? "" : value.state(); }
    @StringQueryBinding(value = "state", namespace = "tube")
    public String tube_state() { return requireTube().state(); }

    @StringQueryBinding(value = "tube_loading_phase", namespace = "weapon")
    public String weapon_tube_loading_phase(double index) { var value = tube(index); return value == null ? "" : value.loadingPhase(); }
    @StringQueryBinding(value = "loading_phase", namespace = "tube")
    public String tube_loading_phase() { return requireTube().loadingPhase(); }
    @QueryBinding(value = "tube_loading_progress", namespace = "weapon")
    public double weapon_tube_loading_progress(double index) { var value = tube(index); return value == null ? 0 : value.loadingProgress(); }
    @QueryBinding(value = "loading_progress", namespace = "tube")
    public double tube_loading_progress() { return requireTube().loadingProgress(); }
    @QueryBinding(value = "tube_loading_time_remaining", namespace = "weapon")
    public double weapon_tube_loading_time_remaining(double index) { var value = tube(index); return value == null ? -1 : value.loadingTimeRemaining(); }
    @QueryBinding(value = "loading_time_remaining", namespace = "tube")
    public double tube_loading_time_remaining() { return requireTube().loadingTimeRemaining(); }
    @StringQueryBinding(value = "tube_loading_time_text", namespace = "weapon")
    public String weapon_tube_loading_time_text(double index) { var value = tube(index); return value == null ? "" : value.loadingTimeText(); }
    @StringQueryBinding(value = "loading_time_text", namespace = "tube")
    public String tube_loading_time_text() { return requireTube().loadingTimeText(); }

    @StringQueryBinding(value = "tube_supply_refill_phase", namespace = "weapon")
    public String weapon_tube_supply_refill_phase(double index) { var value = tube(index); return value == null ? "" : value.supplyRefillPhase(); }
    @StringQueryBinding(value = "supply_refill_phase", namespace = "tube")
    public String tube_supply_refill_phase() { return requireTube().supplyRefillPhase(); }
    @QueryBinding(value = "tube_supply_refill_progress", namespace = "weapon")
    public double weapon_tube_supply_refill_progress(double index) { var value = tube(index); return value == null ? 0 : value.supplyRefillProgress(); }
    @QueryBinding(value = "supply_refill_progress", namespace = "tube")
    public double tube_supply_refill_progress() { return requireTube().supplyRefillProgress(); }
    @QueryBinding(value = "tube_supply_refill_time_remaining", namespace = "weapon")
    public double weapon_tube_supply_refill_time_remaining(double index) { var value = tube(index); return value == null ? -1 : value.supplyRefillTimeRemaining(); }
    @QueryBinding(value = "supply_refill_time_remaining", namespace = "tube")
    public double tube_supply_refill_time_remaining() { return requireTube().supplyRefillTimeRemaining(); }
    @StringQueryBinding(value = "tube_supply_refill_time_text", namespace = "weapon")
    public String weapon_tube_supply_refill_time_text(double index) { var value = tube(index); return value == null ? "" : value.supplyRefillTimeText(); }
    @StringQueryBinding(value = "supply_refill_time_text", namespace = "tube")
    public String tube_supply_refill_time_text() { return requireTube().supplyRefillTimeText(); }

    @StringQueryBinding(value = "tube_round_label", namespace = "weapon")
    public String weapon_tube_round_label(double index) { var value = tube(index); return value == null ? "" : value.roundLabel(); }
    @StringQueryBinding(value = "round_label", namespace = "tube")
    public String tube_round_label() { return requireTube().roundLabel(); }
    @StringQueryBinding(value = "tube_chambered_ammo_label", namespace = "weapon")
    public String weapon_tube_chambered_ammo_label(double index) { var value = tube(index); return value == null ? "" : value.chamber().label(); }
    @StringQueryBinding(value = "chambered_ammo_label", namespace = "tube")
    public String tube_chambered_ammo_label() { return requireTube().chamber().label(); }
    @StringQueryBinding(value = "tube_loading_source_label", namespace = "weapon")
    public String weapon_tube_loading_source_label(double index) { var value = tube(index); return value == null ? "" : value.loadingSourceLabel(); }
    @StringQueryBinding(value = "loading_source_label", namespace = "tube")
    public String tube_loading_source_label() { return requireTube().loadingSourceLabel(); }

    @QueryBinding(value = "tube_has_supply_source", namespace = "weapon")
    public double weapon_tube_has_supply_source(double index) { var value = tube(index); return value == null ? 0 : (value.hasSupplySource() ? 1 : 0); }
    @QueryBinding(value = "has_supply_source", namespace = "tube")
    public double tube_has_supply_source() { return requireTube().hasSupplySource() ? 1 : 0; }
    @QueryBinding(value = "tube_loaded_rounds", namespace = "weapon")
    public double weapon_tube_loaded_rounds(double index) { var value = tube(index); return value == null ? 0 : value.loadedRounds(); }
    @QueryBinding(value = "loaded_rounds", namespace = "tube")
    public double tube_loaded_rounds() { return requireTube().loadedRounds(); }
    @QueryBinding(value = "tube_chambered", namespace = "weapon")
    public double weapon_tube_chambered(double index) { var value = tube(index); return value == null || !value.chamber().loaded() ? 0 : 1; }
    @QueryBinding(value = "chambered", namespace = "tube")
    public double tube_chambered() { return requireTube().chamber().loaded() ? 1 : 0; }

    @QueryBinding(value = "tube_has_belt", namespace = "weapon")
    public double weapon_tube_has_belt(double index) { var value = tube(index); return value == null || !value.belt().loaded() ? 0 : 1; }
    @QueryBinding(value = "has_belt", namespace = "tube")
    public double tube_has_belt() { return requireTube().belt().loaded() ? 1 : 0; }
    @QueryBinding(value = "tube_belt_remaining", namespace = "weapon")
    public double weapon_tube_belt_remaining(double index) { var value = tube(index); return value == null ? 0 : value.belt().remaining(); }
    @QueryBinding(value = "belt_remaining", namespace = "tube")
    public double tube_belt_remaining() { return requireTube().belt().remaining(); }
    @QueryBinding(value = "tube_belt_capacity", namespace = "weapon")
    public double weapon_tube_belt_capacity(double index) { var value = tube(index); return value == null ? 0 : value.belt().capacity(); }
    @QueryBinding(value = "belt_capacity", namespace = "tube")
    public double tube_belt_capacity() { return requireTube().belt().capacity(); }
    @StringQueryBinding(value = "tube_belt_label", namespace = "weapon")
    public String weapon_tube_belt_label(double index) { var value = tube(index); return value == null ? "" : value.belt().label(); }
    @StringQueryBinding(value = "belt_label", namespace = "tube")
    public String tube_belt_label() { return requireTube().belt().label(); }

    @QueryBinding(value = "tube_source_reserve_remaining", namespace = "weapon")
    public double weapon_tube_source_reserve_remaining(double index) { var value = tube(index); return value == null ? 0 : value.sourceReserve().remaining(); }
    @QueryBinding(value = "source_reserve_remaining", namespace = "tube")
    public double tube_source_reserve_remaining() { return requireTube().sourceReserve().remaining(); }
    @QueryBinding(value = "tube_source_reserve_capacity", namespace = "weapon")
    public double weapon_tube_source_reserve_capacity(double index) { var value = tube(index); return value == null ? 0 : value.sourceReserve().capacity(); }
    @QueryBinding(value = "source_reserve_capacity", namespace = "tube")
    public double tube_source_reserve_capacity() { return requireTube().sourceReserve().capacity(); }
    @QueryBinding(value = "tube_source_reserve_infinite", namespace = "weapon")
    public double weapon_tube_source_reserve_infinite(double index) { var value = tube(index); return value == null || !value.sourceReserve().infinite() ? 0 : 1; }
    @QueryBinding(value = "source_reserve_infinite", namespace = "tube")
    public double tube_source_reserve_infinite() { return requireTube().sourceReserve().infinite() ? 1 : 0; }
    @QueryBinding(value = "tube_swapping", namespace = "weapon")
    public double weapon_tube_swapping(double index) { var value = tube(index); return value == null || !value.swapping() ? 0 : 1; }
    @QueryBinding(value = "swapping", namespace = "tube")
    public double tube_swapping() { return requireTube().swapping() ? 1 : 0; }
    @QueryBinding(value = "tube_aimed", namespace = "weapon")
    public double weapon_tube_aimed(double index) { var value = tube(index); return value == null || !value.aimed() ? 0 : 1; }
    @QueryBinding(value = "aimed", namespace = "tube")
    public double tube_aimed() { return requireTube().aimed() ? 1 : 0; }

    @StringQueryBinding(value = "ammo_option_label", namespace = "weapon")
    public String weapon_ammo_option_label(double index) { var value = ammoOption(index); return value == null ? "" : value.label(); }
    @StringQueryBinding(value = "label", namespace = "ammo_option")
    public String ammo_option_label() { return requireAmmoOption().label(); }
    @QueryBinding(value = "ammo_option_reserve_remaining", namespace = "weapon")
    public double weapon_ammo_option_reserve_remaining(double index) { var value = ammoOption(index); return value == null ? 0 : value.reserveRemaining(); }
    @QueryBinding(value = "reserve_remaining", namespace = "ammo_option")
    public double ammo_option_reserve_remaining() { return requireAmmoOption().reserveRemaining(); }
    @QueryBinding(value = "ammo_option_reserve_capacity", namespace = "weapon")
    public double weapon_ammo_option_reserve_capacity(double index) { var value = ammoOption(index); return value == null ? 0 : value.reserveCapacity(); }
    @QueryBinding(value = "reserve_capacity", namespace = "ammo_option")
    public double ammo_option_reserve_capacity() { return requireAmmoOption().reserveCapacity(); }
    @QueryBinding(value = "ammo_option_reserve_infinite", namespace = "weapon")
    public double weapon_ammo_option_reserve_infinite(double index) { var value = ammoOption(index); return value == null || !value.reserveInfinite() ? 0 : 1; }
    @QueryBinding(value = "reserve_infinite", namespace = "ammo_option")
    public double ammo_option_reserve_infinite() { return requireAmmoOption().reserveInfinite() ? 1 : 0; }
    @QueryBinding(value = "ammo_option_selected", namespace = "weapon")
    public double weapon_ammo_option_selected(double index) { var value = ammoOption(index); return value != null && value.selected() ? 1 : 0; }
    @QueryBinding(value = "selected", namespace = "ammo_option")
    public double ammo_option_selected() { return requireAmmoOption().selected() ? 1 : 0; }

    @QueryBinding(value = "bdc_hold_rad", namespace = "weapon")
    public double hold(double distance) {
        return getEntity() instanceof WeaponHudElement weapon ? weapon.ballistics().hold(distance) : 0;
    }
}
