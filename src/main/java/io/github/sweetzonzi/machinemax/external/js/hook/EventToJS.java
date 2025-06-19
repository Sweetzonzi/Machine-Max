package io.github.sweetzonzi.machinemax.external.js.hook;

import net.minecraft.resources.ResourceLocation;

public interface EventToJS {
    Object call(Object... args);
    String packName();
    String location();
}
