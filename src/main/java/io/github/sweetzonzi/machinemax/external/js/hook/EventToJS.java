package io.github.sweetzonzi.machinemax.external.js.hook;

import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.AbstractSubsystem;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

public interface EventToJS {
    Object call(Object... args);
    String packName();
}
