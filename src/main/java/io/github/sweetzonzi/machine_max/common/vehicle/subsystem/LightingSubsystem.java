package io.github.sweetzonzi.machine_max.common.vehicle.subsystem;

import io.github.sweetzonzi.machine_max.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.dynamic_attr.LightingSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.visual.VisualEffectHelper;

public class LightingSubsystem extends BasicSubsystem {
    public final LightingSubsystemAttr attr;

    public LightingSubsystem(ISubsystemHost owner, String name, LightingSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
    }

    @Override
    public void onAttach() {
        super.onAttach();
        if (getLevel().isClientSide()) {
            VisualEffectHelper.lightingSubsystems.add(this);
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        VisualEffectHelper.lightingSubsystems.remove(this);
    }
}
