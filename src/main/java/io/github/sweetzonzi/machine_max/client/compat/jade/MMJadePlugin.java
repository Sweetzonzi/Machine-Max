package io.github.sweetzonzi.machine_max.client.compat.jade;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin(MachineMax.MOD_ID)
public class MMJadePlugin implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityComponent(MMPartEntityStatusProvider.INSTANCE, MMPartEntity.class);
    }
}
