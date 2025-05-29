package io.github.sweetzonzi.machinemax.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.sweetzonzi.machinemax.MachineMax;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;

@EventBusSubscriber(modid = MachineMax.MOD_ID, value = Dist.CLIENT)
public class ClientOnKeyEvents {
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        InputConstants.Key inputKey = InputConstants.getKey(event.getKey(), event.getScanCode());
        String keyName = inputKey.getName();
        double value = event.getAction();
        if (!KeyBinding.INPUT_KEY_MAP.containsKey(keyName)) KeyBinding.INPUT_KEY_MAP.put(keyName, 0.0);
        if (value == 0 || value == 1) KeyBinding.INPUT_KEY_MAP.put(keyName, value);

    }

}
