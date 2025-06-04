package io.github.sweetzonzi.machinemax.external.js.hook;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.sweetzonzi.machinemax.MachineMax;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;

import static io.github.sweetzonzi.machinemax.external.js.hook.Hook.SIGNAL_MAP;

@EventBusSubscriber(modid = MachineMax.MOD_ID, value = Dist.CLIENT)
public class KeyHooks {

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        InputConstants.Key inputKey = InputConstants.getKey(event.getKey(), event.getScanCode());
        String keyName = inputKey.getName();
        double value = event.getAction();
        if (!SIGNAL_MAP.containsKey(keyName)) SIGNAL_MAP.put(keyName, 0.0);
        if (value == 0 || value == 1) SIGNAL_MAP.put(keyName, value);

    }


    public static void tick() {
//        INPUT_KEY_MAP.forEach((name, tick) -> {
//            if (tick != 0) INPUT_KEY_MAP.put(name, tick + 1);
//        });
//        for (EventToJS eventToJS : LISTENING_EVENT) {
//            eventToJS.call(MMInitialJS.JS_RUNNER, MMInitialJS.JS_SCOPE);
//        }
    }

}
