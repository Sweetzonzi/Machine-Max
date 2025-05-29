package io.github.sweetzonzi.machinemax.external.js;

import io.github.sweetzonzi.machinemax.client.input.KeyBinding;
import io.github.sweetzonzi.machinemax.common.registry.MMAttachments;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

import static io.github.sweetzonzi.machinemax.MachineMax.LOGGER;

public class JSUtils {

    public double get(String tag) {
        double value = 0;
        if (KeyBinding.INPUT_KEY_MAP.get(tag) instanceof Double d) {
            value = d;
        }
        return value;
    }

    public void attachInteract() {
        if (Minecraft.getInstance().player instanceof Player player) {
            player.getData(MMAttachments.getENTITY_EYESIGHT().get()).clientInteract();

        }
    }

    public void listen(String tag, org.mozilla.javascript.ArrowFunction arrowFunction) {
        KeyBinding.LISTENING_EVENT
                    .add(((cx, scope) -> {
                        return arrowFunction.call(cx, scope, scope, new Object[]{get(tag)});
                    }));

    }
    public void log(Object object) {
        LOGGER.info(object.toString());
    }
    public void print(Object object) {
        if (Minecraft.getInstance().player instanceof Player player) {
            player.sendSystemMessage(Component.literal(String.valueOf(object)));
        }
    }
}
