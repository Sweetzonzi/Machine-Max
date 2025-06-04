package io.github.sweetzonzi.machinemax.external.js.hook;

import io.github.sweetzonzi.machinemax.external.MMDynamicRes;
import org.mozilla.javascript.Context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static io.github.sweetzonzi.machinemax.MachineMax.LOGGER;
import static io.github.sweetzonzi.machinemax.external.js.MMInitialJS.JS_RUNNER;
import static io.github.sweetzonzi.machinemax.external.js.MMInitialJS.JS_SCOPE;

public class Hook {
    public static HashMap<String, Double> SIGNAL_MAP = new HashMap<>();
    public static HashMap<String, List<EventToJS>> LISTENING_EVENT = new HashMap<>();
    public static void clear() {
        SIGNAL_MAP.clear();
        LISTENING_EVENT.clear();
    }


    public static void run(Object... args) {
        var stack = Thread.currentThread().getStackTrace()[2];
        String className = stack.getClassName();
        String methodName = stack.getMethodName();
        String channel = className + ":" + methodName;
        if (!Hook.LISTENING_EVENT.containsKey(channel)) {
            Hook.LISTENING_EVENT.put(channel, new ArrayList<>());
            String jsCode = "";
            int index = 0;
            for (String c1 : LISTENING_EVENT.keySet()) {
                jsCode += "var c%s = \"%s\";\n".formatted(index, c1);
                index++;
            }
            MMDynamicRes.GenerateChannels(jsCode);
        }
        if (Hook.LISTENING_EVENT.get(channel) instanceof List<EventToJS> li) {
            for (EventToJS eventToJS : li) {
                try {
                    JS_SCOPE.put("args", JS_SCOPE, args);
                    JS_SCOPE.put("channel", JS_SCOPE, channel);
                    eventToJS.call(args);
                } catch (RuntimeException e) {
                    JS_RUNNER = Context.enter();
                    JS_SCOPE = JS_RUNNER.initStandardObjects();
                    LOGGER.error("JS钩子在{}出现错误，尝试重启: {}", channel, e);
                }
            }
        }
    }
}
