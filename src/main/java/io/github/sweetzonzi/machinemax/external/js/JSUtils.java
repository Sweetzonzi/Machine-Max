package io.github.sweetzonzi.machinemax.external.js;

import io.github.sweetzonzi.machinemax.common.registry.MMAttachments;
import io.github.sweetzonzi.machinemax.external.js.hook.EventToJS;
import io.github.sweetzonzi.machinemax.external.js.hook.Hook;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Objects;

import static io.github.sweetzonzi.machinemax.MachineMax.LOGGER;
import static io.github.sweetzonzi.machinemax.external.js.MMInitialJS.JS_RUNNER;
import static io.github.sweetzonzi.machinemax.external.js.MMInitialJS.JS_SCOPE;

public class JSUtils {
    private final String packName;
    public JSUtils(String packName) {
        this.packName = packName;
    }

//    public double get(String tag) {
//        double value = 0;
//        if (KeyHooks.SIGNAL_MAP.get(tag) instanceof Double d) {
//            value = d;
//        }
//        return value;
//    }

    public void attachInteract() {
        if (Minecraft.getInstance().player instanceof Player player) {
            player.getData(MMAttachments.getENTITY_EYESIGHT().get()).clientInteract();

        }
    }

    public void hook(String channel, org.mozilla.javascript.ArrowFunction arrowFunction) {
        if (!Hook.LISTENING_EVENT.containsKey(channel)) Hook.LISTENING_EVENT.put(channel, new ArrayList<>());
        Hook.LISTENING_EVENT.get(channel)
                    .add((new EventToJS() {
                        @Override
                        public Object call(Object... args) {
                            return (JS_RUNNER != null && JS_SCOPE != null) ?
                                    arrowFunction.call(JS_RUNNER, JS_SCOPE, JS_SCOPE, args) : null;
                        }

                        @Override
                        public String packName() {
                            return packName;
                        }
                    }
                    ));

    }
    public void log(Object object) {
        LOGGER.info(String.valueOf(object));
    }

    public void print(Object... objects) {
        StringBuilder msg = new StringBuilder();
        if (! Objects.equals(Thread.currentThread().getName(), "Server thread")) {
            //不是服务器的都打印，避免物理进程不响应
            if (Minecraft.getInstance().player instanceof Player player) {
                for (Object object : objects) {
                    msg.append(object);
                    msg.append(" ");
                }
                player.sendSystemMessage(Component.literal(msg.toString()));
            }
        } else {
            for (Object object : objects) {
                msg.append(object);
                msg.append(" ");
            }
            log(msg);
        }

    }

    public static String getSimpleName(String fullClassName) {
        int lastDotIndex = fullClassName.lastIndexOf('.');
        return (lastDotIndex == -1) ? fullClassName : fullClassName.substring(lastDotIndex + 1);
    }
}
