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
        // 客户端注册入口：把部件实体绑定到自定义状态提示 Provider。
        registration.registerEntityComponent(MMPartEntityStatusProvider.INSTANCE, MMPartEntity.class);
    }
}
