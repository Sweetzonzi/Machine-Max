package io.github.sweetzonzi.machinemax.external.js;

import io.github.sweetzonzi.machinemax.client.input.KeyBinding;
import io.github.sweetzonzi.machinemax.external.MMDynamicRes;
import io.github.sweetzonzi.machinemax.external.js.hook.Hook;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.util.Arrays;

import static io.github.sweetzonzi.machinemax.MachineMax.LOGGER;

public class MMInitialJS {
    public static Context JS_RUNNER;
    public static Scriptable JS_SCOPE;

    public static void clear() {
        if (JS_RUNNER != null) JS_RUNNER.close();
        JS_RUNNER = null;
        JS_SCOPE = null;
        Hook.clear();
    }

    public static void run(Object[] args) {
        try {
            ScriptableObject.putProperty(JS_SCOPE, "args", Context.javaToJS(args, JS_SCOPE,  JS_RUNNER));
//            JS_SCOPE.put("args", JS_SCOPE, args);
        } catch (Exception e) {
            LOGGER.error("MMInitialJS Error: " + Arrays.toString(e.getStackTrace()));
        }

        MMDynamicRes.MM_SCRIPTS.forEach((location, jsPack) -> {
            try {
                Object jsObj = JS_RUNNER.evaluateString(
                        JS_SCOPE, jsPack.getContent(),
                        "mm_initial_js_run_"+location.getPath(), 1, null);
//                System.out.println("MMInitialJS Run: "+jsObj);
            } catch (Exception e) {
                LOGGER.error("MMInitialJS Error: " + Arrays.toString(e.getStackTrace()));
            }
        });
    }

    public static void register() {
        JS_RUNNER = Context.enter();
        JS_SCOPE = JS_RUNNER.initStandardObjects();
        StringBuilder packages = new StringBuilder();
        for (String publicScript : MMDynamicRes.MM_PUBLIC_SCRIPTS) {
            packages.append(publicScript);
            packages.append("\n");
        }

        ScriptableObject.putProperty(JS_SCOPE, "mm", Context.javaToJS(new JSUtils(), JS_SCOPE,  JS_RUNNER));
        ScriptableObject.putProperty(JS_SCOPE, "signal", Context.javaToJS(new SignalProvider(), JS_SCOPE,  JS_RUNNER));

        MMDynamicRes.MM_SCRIPTS.forEach((location, jsPack) -> {
            try {
                Object jsObj = JS_RUNNER.evaluateString(
                        JS_SCOPE, packages + jsPack.getContent(),
                        "mm_initial_js_register_"+location.getPath(), 1, null);
//                System.out.println("MMInitialJS Run: "+jsObj);
            } catch (Exception e) {
                LOGGER.error("MMInitialJS Error: " + Arrays.toString(e.getStackTrace()));
            }
        });
    }
}
