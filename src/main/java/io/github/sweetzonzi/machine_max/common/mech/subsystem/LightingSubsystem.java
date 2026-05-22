package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalSender;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalResult;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.LightingSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.visual.VisualEffectHelper;

import java.util.List;

public class LightingSubsystem extends BasicSubsystem {
    public final LightingSubsystemAttr attr;

    public LightingSubsystem(ISubsystemHost owner, String name, LightingSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
    }

    @Override
    public List<String> getAcceptedChannels() {
        return List.of("toggle_light");
    }

    @Override
    public SignalResult onSignalUpdated(String channelName, ISignalSender sender) {
        SignalResult result = super.onSignalUpdated(channelName, sender);
        if ("toggle_light".equals(channelName)) {
            Object signal = getSignalValueFrom(channelName, sender);
            if (signal instanceof Float f) {
                setActive(f > 0);
            } else if (signal instanceof Number n) {
                setActive(n.floatValue() > 0);
            }
            return SignalResult.CONSUME;
        }
        return result;
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
