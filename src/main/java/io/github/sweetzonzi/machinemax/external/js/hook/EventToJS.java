package io.github.sweetzonzi.machinemax.external.js.hook;

public interface EventToJS {
    Object call(Object... args);
    String packName();
    String location();
}
