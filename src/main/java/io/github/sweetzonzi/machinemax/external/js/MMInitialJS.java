package io.github.sweetzonzi.machinemax.external.js;

import io.github.sweetzonzi.machinemax.client.input.KeyBinding;
import io.github.sweetzonzi.machinemax.external.MMDynamicRes;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import static io.github.sweetzonzi.machinemax.MachineMax.LOGGER;

public class MMInitialJS {
    public static Context JS_RUNNER;
    public static Scriptable JS_SCOPE;

    public static void clear() {
        if (JS_RUNNER != null) JS_RUNNER.close();
        JS_RUNNER = null;
        JS_SCOPE = null;
        KeyBinding.INPUT_KEY_MAP.clear();
        KeyBinding.LISTENING_EVENT.clear();
    }

    public static void register() {
        JS_RUNNER = Context.enter();
        JS_SCOPE = JS_RUNNER.initStandardObjects();

        ScriptableObject.putProperty(JS_SCOPE, "mm", Context.javaToJS(new JSUtils(), JS_SCOPE,  JS_RUNNER));
        ScriptableObject.putProperty(JS_SCOPE, "signal", Context.javaToJS(new SignalProvider(), JS_SCOPE,  JS_RUNNER));

        MMDynamicRes.MM_SCRIPTS.forEach((location, jsPack) -> {
            try {
                Object jsObj = JS_RUNNER.evaluateString(
                        JS_SCOPE, jsPack.getContent(),
                        "mm_initial_js_"+location.getPath(), 1, null);
                System.out.println("MMInitialJS Run: "+jsObj);
            } catch (Exception e) {
                String msg = e.getMessage();
                LOGGER.error("MMInitialJS Error: " + msg);
            }
        });
    }
}
