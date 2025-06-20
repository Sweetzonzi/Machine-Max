package io.github.sweetzonzi.machinemax.external.js.hook;

import io.github.sweetzonzi.machinemax.common.registry.MMAttachments;
import io.github.sweetzonzi.machinemax.common.vehicle.attr.subsystem.ScriptableSubsystemAttr;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.ScriptableSubsystem;
import io.github.sweetzonzi.machinemax.external.MMDynamicRes;
import io.github.sweetzonzi.machinemax.external.js.JSUtils;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.mozilla.javascript.Context;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static io.github.sweetzonzi.machinemax.MachineMax.LOGGER;
import static io.github.sweetzonzi.machinemax.external.js.MMInitialJS.JS_RUNNER;
import static io.github.sweetzonzi.machinemax.external.js.MMInitialJS.JS_SCOPE;

public class Hook {
    public static ConcurrentMap<String, Double> HOOK_SIGNAL_MAP = new ConcurrentHashMap<>();//会自增的信号
    public static HashMap<String, List<EventToJS>> LISTENING_EVENT = new HashMap<>();
    public static HashMap<String, String> CHANNEL_DOCUMENT = new HashMap<>();

    public static void clear() {
        HOOK_SIGNAL_MAP.clear();
        LISTENING_EVENT.clear();
    }

    private enum RunType {
        run,
        replace,
    }


    /**
     * 普通的钩子传入方式，JS可以在channel上挂载自定义钩函数，并且拿到args
     * 每当Java调用该方法时就会触发一次
     * @param args 传给JS的所有参数
     * @return {@link Object} JS钩函数返回的对象。
     */
    public static Object run(Object... args) {
//        String packName = "";
//        boolean isScriptableSubsystem = false;
//        if (args[0] instanceof ScriptableSubsystem scriptableSubsystem) {
//            isScriptableSubsystem = true;
//            ResourceLocation location = scriptableSubsystem.getPart().type.registryKey;
//            if (MMDynamicRes.EXTERNAL_RESOURCE.get(location) instanceof DynamicPack pack) packName = pack.getPackName();
//        }
        var currentThread = Thread.currentThread();
        var stack = currentThread.getStackTrace()[2];
        String className = stack.getClassName();
        String methodName = stack.getMethodName();
        String channel = JSUtils.getSimpleName(className) + ":" + methodName;
        _generateDocument(args, channel);
        return _run(RunType.run, args, channel, currentThread);
    }



    /**
     * 尝试脚本替换原对象的钩子，将original传入，若没有JS钩入或JS钩函数返回的对象与original的类型不符，
     * 则视为替换失败，返回original。
     * 相应的，若JS钩函数返回的对象满足original同类型，则返回该对象来覆盖
     * @param original 原初对象
     * @param tag 传给JS的自定义标签
     * @return {@link T} 尝试覆盖后的对象。
     * @apiNote T是泛型，用于自动将该方法的返回类型设置成original的类型
     */
    @SuppressWarnings("unchecked")
    public static <T> T replace(T original, String tag) {
        Object[] fakeArgs = new Object[]{original};
        var currentThread = Thread.currentThread();
        var stack = currentThread.getStackTrace()[2];
        String className = stack.getClassName();
        String methodName = stack.getMethodName();
        String channel = "REPLACE."+JSUtils.getSimpleName(className) + ":" + methodName + ":" + tag;
        _generateDocument(fakeArgs, channel);
        Object objFromRun = _run(RunType.replace, fakeArgs, channel, currentThread);
        if (objFromRun != null && objFromRun.getClass().isInstance(original)) return (T) objFromRun;
        return original;
    }
    

    @Nullable
    private static Object _run(RunType runType, Object[] args, String channel, Thread currentThread) {
        if (LISTENING_EVENT.get(channel) instanceof List<EventToJS> li) {
            for (EventToJS eventToJS : li) {
//                if (isScriptableSubsystem && (!eventToJS.packName().equals(packName))) return null;
                try {
                    String packName = eventToJS.packName();
                    String location = eventToJS.location();
                    switch (runType) {

                        case run -> {
                            if (args[0] instanceof ScriptableSubsystem scriptableSubsystem) {
                                String scriptPath = ((ScriptableSubsystemAttr) scriptableSubsystem.getAttr()).script;
                                if (!location.equals(scriptPath)) return null;
                            }

                            JS_SCOPE.put("args", JS_SCOPE, args);
                        }
                        case replace -> {
                            JS_SCOPE.put("origin", JS_SCOPE, args[0]);
                        }
                    }


                    JS_SCOPE.put("mm", JS_SCOPE, new JSUtils(location, packName));
                    JS_SCOPE.put("channel", JS_SCOPE, channel);
                    JS_SCOPE.put("packName", JS_SCOPE, packName);
                    JS_SCOPE.put("location", JS_SCOPE, location);
                    JS_SCOPE.put("thread", JS_SCOPE, currentThread.getName());
                    JS_SCOPE.put("mc", JS_SCOPE, Minecraft.getInstance());
                    JS_SCOPE.put("attach", JS_SCOPE, Minecraft.getInstance());
                    JS_SCOPE.put("sight", JS_SCOPE, MMAttachments.getENTITY_EYESIGHT());
                    return eventToJS.call(args);
                } catch (RuntimeException e) {
                    JS_RUNNER = Context.enter();
                    JS_SCOPE = JS_RUNNER.initStandardObjects();
                    StringBuilder errTrace = new StringBuilder();
                    for (StackTraceElement element : e.getStackTrace()) {
                        errTrace.append(element);
                        errTrace.append("\n");
                    }
                    LOGGER.error("JS钩子在{}出现错误，尝试重启: {}\n{}\n{}", channel, e, e.getCause(), errTrace);
                }
            }
        }
        return null;
    }

    private static void _generateDocument(Object[] args, String channel) {
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
    }

}
