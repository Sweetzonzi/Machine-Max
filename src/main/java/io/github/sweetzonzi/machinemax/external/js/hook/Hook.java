package io.github.sweetzonzi.machinemax.external.js.hook;

import io.github.sweetzonzi.machinemax.client.input.KeyBinding;
import io.github.sweetzonzi.machinemax.external.MMDynamicRes;
import io.github.sweetzonzi.machinemax.external.js.JSUtils;
import io.github.sweetzonzi.machinemax.external.js.MMInitialJS;
import io.github.sweetzonzi.machinemax.external.js.SignalProvider;
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
    public static HashMap<String, String> CHANNEL_DOCUMENT = new HashMap<>();
    public static boolean isHotReloading = false;

    public static void clear() {
        SIGNAL_MAP.clear();
        LISTENING_EVENT.clear();
    }


    public static Object run(Object... args) {
        Object readyToReturn = null;
        var currentThread = Thread.currentThread();
        var stack = currentThread.getStackTrace()[2];
        String className = stack.getClassName();
        String methodName = stack.getMethodName();
        String channel = JSUtils.getSimpleName(className) + ":" + methodName;
        String argType = "";
        int argIndex = 0;
        for (Object arg : args) {
            argType += JSUtils.getSimpleName(arg.getClass().getTypeName());
            argType += argIndex != (args.length-1) ? ", ":"";
            argIndex++;
        }
        if (!LISTENING_EVENT.containsKey(channel)) {
            LISTENING_EVENT.put(channel, new ArrayList<>());
            CHANNEL_DOCUMENT.put(channel, argType);
            String jsCode = "";
            int docIndex = 0;
            jsCode += "// 注意！下面的变量均为动态生成。变量仅作为文档和测试使用，发布个人外部包前请检查均使用完整字符串！\n";
            jsCode += "//      错误用法 mm.hook(c11, (各个参数) => {}); ❌\n";
            jsCode += "//      正确用法 mm.hook(\"VehicleCore:tick\", (各个参数) => {}); ✅\n";
            jsCode += "\n// 若因使用不当造成个人外部包失效概不负责\n\n";
            for (String c : CHANNEL_DOCUMENT.keySet()) {
                jsCode += "var c%s = \"%s\";\n".formatted(docIndex, c);
                jsCode += "//param: (%s)\n\n".formatted(CHANNEL_DOCUMENT.get(c));
                docIndex++;
            }
            MMDynamicRes.GenerateChannels(jsCode);
        }
        if (LISTENING_EVENT.get(channel) instanceof List<EventToJS> li) {
            for (EventToJS eventToJS : li) {
                try {
                    if (isHotReloading){
                        return null;
                    }
                    if (SignalProvider.getKeyStatus("backslash") && (!isHotReloading)) {
                        isHotReloading = true;
                        MMInitialJS.clear();
                        MMInitialJS.hotReload();
                        MMInitialJS.register();
                        isHotReloading = false;
                        return null;
                    }
                    JS_SCOPE.put("args", JS_SCOPE, args);
                    JS_SCOPE.put("channel", JS_SCOPE, channel);
                    JS_SCOPE.put("thread", JS_SCOPE, currentThread.getName());
                    readyToReturn = eventToJS.call(args);
                } catch (RuntimeException e) {
                    JS_RUNNER = Context.enter();
                    JS_SCOPE = JS_RUNNER.initStandardObjects();
                    LOGGER.error("JS钩子在{}出现错误，尝试重启: {} {}", channel, e, e.getMessage());
                }
            }
        }
        return readyToReturn;
    }

}
