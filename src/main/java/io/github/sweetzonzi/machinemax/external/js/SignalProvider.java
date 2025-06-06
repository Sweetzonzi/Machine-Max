package io.github.sweetzonzi.machinemax.external.js;


import static io.github.sweetzonzi.machinemax.external.js.hook.Hook.SIGNAL_MAP;

public class SignalProvider {
    public static String key(String name) {
        return "key.keyboard."+ name;
    }

    public static boolean getKeyStatus(String name) {
        if (SIGNAL_MAP.get(SignalProvider.key(name)) instanceof Double d) {
            return d != 0.0;
        }
        return false;
    }
}
