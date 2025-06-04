package io.github.sweetzonzi.machinemax.external.js.hook;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.EvaluatorException;

import java.util.HashMap;
import java.util.List;

import static io.github.sweetzonzi.machinemax.MachineMax.LOGGER;
import static io.github.sweetzonzi.machinemax.external.js.MMInitialJS.JS_RUNNER;
import static io.github.sweetzonzi.machinemax.external.js.MMInitialJS.JS_SCOPE;

public class Hook {
    public static HashMap<String, Double> SIGNAL_MAP = new HashMap<>();
    public static HashMap<Thread, List<EventToJS>> LISTENING_EVENT = new HashMap<>();
    public enum Thread {
        tick,
        pre,
        post
    }
    public static void clear() {
        SIGNAL_MAP.clear();
        LISTENING_EVENT.clear();
    }

    public static void run(Hook.Thread thread, Object... args) {
        if (Hook.LISTENING_EVENT.get(thread) instanceof List<EventToJS> li) {
            for (EventToJS eventToJS : li) {
                try {
                    eventToJS.call(args);
                } catch (RuntimeException e) {
                    JS_RUNNER = Context.enter();
                    JS_SCOPE = JS_RUNNER.initStandardObjects();
                    LOGGER.error("JS钩子在{}出现错误，尝试重启: {}", thread, e.getStackTrace());
                }
            }
        }
    }
}
