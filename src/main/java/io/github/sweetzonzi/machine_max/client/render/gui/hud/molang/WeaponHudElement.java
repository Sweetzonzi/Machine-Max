package io.github.sweetzonzi.machine_max.client.render.gui.hud.molang;

import io.github.sweetzonzi.machine_max.common.mech.subsystem.WeaponControllerSubsystem;
import io.github.sweetzonzi.machine_max.common.visual.HudElement;

public final class WeaponHudElement extends HudRuntimeElement {
    private final WeaponControllerSubsystem controller;
    /** 上一次解出的分划表及其弹种键；弹种不变即直接复用，求解不再进 tick 路径。 */
    private volatile String resolvedAmmo;
    private volatile HudBallisticTable resolvedTable;

    public WeaponHudElement(WeaponControllerSubsystem controller, HudElement element) {
        super(controller, element);
        this.controller = controller;
    }

    public WeaponControllerSubsystem controller() { return controller; }

    /** 惰性取分划表：只在首次查询或弹种变化时走求解，其余情况命中元素缓存或全局参数缓存。 */
    public HudBallisticTable ballistics() {
        var snapshot = frame().weapon();
        String ammo = snapshot.activeAmmo();
        HudBallisticTable table = resolvedTable;
        if (table != null && ammo.equals(resolvedAmmo)) return table;
        table = HudBallisticTable.resolve(controller, snapshot);
        resolvedTable = table;
        resolvedAmmo = ammo;
        return table;
    }

    @Override protected HudMolangContext createContext() { return new WeaponHudMolangContext(); }
}
