package io.github.sweetzonzi.machinemax.external.js;

import io.github.sweetzonzi.machinemax.external.MMDynamicRes;
import io.github.sweetzonzi.machinemax.external.js.hook.Hook;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.util.Arrays;
import java.util.Objects;

import static io.github.sweetzonzi.machinemax.MachineMax.LOGGER;

public class MMInitialJS {
    public static Context JS_RUNNER;
    public static Scriptable JS_SCOPE;

    public static void hotReload() {
        MMDynamicRes.MM_SCRIPTS.forEach((location, jsPack) -> {
            jsPack.loadContent();
        });
        String msg = "Javascript热重载运行完毕";
        if (! Objects.equals(Thread.currentThread().getName(), "Server thread")) {
            //不是服务器的都打印，避免物理进程不响应
            if (Minecraft.getInstance().player instanceof Player player) {
                player.sendSystemMessage(((MutableComponent) Component.literal(msg))
                        .withStyle(style -> style.withColor(0xFFED6F00)));
            }
        } else {
            LOGGER.info(msg);
        }
    }

    public static void clear() {
        if (JS_RUNNER != null) JS_RUNNER.close();
        JS_RUNNER = null;
        JS_SCOPE = null;
        Hook.clear();
    }


    public static void register() {
        JS_RUNNER = Context.enter();
        JS_SCOPE = JS_RUNNER.initStandardObjects();
        StringBuilder packages = new StringBuilder();
        for (String publicScript : MMDynamicRes.MM_PUBLIC_SCRIPTS) {
            packages.append(publicScript);
            packages.append("\n");
        }

        MMDynamicRes.MM_SCRIPTS.forEach((location, jsPack) -> {
            try {
                ScriptableObject.putProperty(JS_SCOPE, "mm", Context.javaToJS(new JSUtils(location.toString(), jsPack.getPackName()), JS_SCOPE,  JS_RUNNER));
                ScriptableObject.putProperty(JS_SCOPE, "signal", Context.javaToJS(new InputSignalProvider(), JS_SCOPE,  JS_RUNNER));
                Object jsObj = JS_RUNNER.evaluateString(
                        JS_SCOPE, packages + jsPack.getContent(),
                        "mm_initial_js_register_"+location.getPath(), 1, null);
//                System.out.println("MMInitialJS Run: "+jsObj);
            } catch (Exception e) {
                LOGGER.error("MMInitialJS Error: " + e.getMessage() + "  " + Arrays.toString(e.getStackTrace()));
            }
        });
    }
}
