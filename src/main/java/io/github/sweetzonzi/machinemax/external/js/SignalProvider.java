package io.github.sweetzonzi.machinemax.external.js;


import io.github.sweetzonzi.machinemax.external.js.hook.Hook;

import static io.github.sweetzonzi.machinemax.external.js.hook.Hook.SIGNAL_MAP;

public class SignalProvider {
    public static String key(String name) {
        return "key.keyboard."+ name;
    }

    public static boolean getKeyStatus(String name) {
        if (SIGNAL_MAP.get(SignalProvider.key(name)) instanceof Double d) {
            keyTick(name);
            return d != 0.0;
        }
        return false;
    }
    public static Double getKeyTicks(String name) {
        if (SIGNAL_MAP.get(SignalProvider.key(name)) instanceof Double d) {
            keyTick(name);
            return d;
        }
        return 0.0;
    }

    public static void keyTick(String name) { //按键按下记录+1
        signalTick(SignalProvider.key(name));
    }

    public static void signalTick(String name) { //信号获取后记录+1
        if (Hook.SIGNAL_MAP.get(name) != 0) Hook.SIGNAL_MAP.put(name,Hook.SIGNAL_MAP.get(name) + 1);
    }
}
