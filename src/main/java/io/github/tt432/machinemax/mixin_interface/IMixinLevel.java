package io.github.tt432.machinemax.mixin_interface;

import io.github.tt432.machinemax.common.sloarphys.thread.MMAbstractPhysLevel;

public interface IMixinLevel {
    MMAbstractPhysLevel machine_Max$getPhysLevel();
    void machine_Max$setPhysLevel(MMAbstractPhysLevel level);
}
