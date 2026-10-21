package io.github.sweetzonzi.machine_max.client.render.gui.hud.molang;

import io.github.sweetzonzi.machine_max.common.mech.subsystem.WeaponControllerSubsystem;
import io.github.sweetzonzi.machine_max.common.visual.HudElement;

public final class WeaponHudElement extends HudRuntimeElement {
    private final WeaponControllerSubsystem controller;

    public WeaponHudElement(WeaponControllerSubsystem controller, HudElement element) {
        super(controller, element);
        this.controller = controller;
    }

    public WeaponControllerSubsystem controller() { return controller; }
    public HudBallisticTable ballistics() { return frame().ballistics(); }
    public void updateBallistics(HudWeaponSnapshot snapshot) {
        setBallistics(HudBallisticTable.update(ballistics(), controller, snapshot));
    }
    @Override protected HudMolangContext createContext() { return new WeaponHudMolangContext(); }
}
