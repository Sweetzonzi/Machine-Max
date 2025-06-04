package io.github.sweetzonzi.machinemax.external.js;

import io.github.sweetzonzi.machinemax.common.registry.MMAttachments;
import io.github.sweetzonzi.machinemax.external.js.hook.Hook;
import io.github.sweetzonzi.machinemax.external.js.hook.KeyHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;

import static io.github.sweetzonzi.machinemax.MachineMax.LOGGER;
import static io.github.sweetzonzi.machinemax.external.js.MMInitialJS.JS_RUNNER;
import static io.github.sweetzonzi.machinemax.external.js.MMInitialJS.JS_SCOPE;

public class JSUtils {

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

    public void hook(String threadString, org.mozilla.javascript.ArrowFunction arrowFunction) {
        Hook.Thread thread = Hook.Thread.valueOf(threadString);
        if (!Hook.LISTENING_EVENT.containsKey(thread)) Hook.LISTENING_EVENT.put(thread, new ArrayList<>());
        Hook.LISTENING_EVENT.get(thread)
                    .add(((args) -> (JS_RUNNER != null && JS_SCOPE != null) ?
                            arrowFunction.call(JS_RUNNER, JS_SCOPE, JS_SCOPE, args) : null
                    ));

    }
    public void log(Object object) {
        System.out.println(object);
    }

    public void print(Object object) {
        if (Minecraft.getInstance().player instanceof Player player) {
            player.sendSystemMessage(Component.literal(String.valueOf(object)));
        }
    }
}
