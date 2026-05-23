package io.github.sweetzonzi.machine_max.client.compat.jade;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import net.minecraft.client.Minecraft;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin(MachineMax.MOD_ID)
public class MMJadePlugin implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        // 客户端注册入口：把部件实体绑定到自定义状态提示 Provider。
        registration.registerEntityComponent(MMPartEntityStatusProvider.INSTANCE, MMPartEntity.class);

        // 注册射线追踪回调：当玩家乘坐座椅子系统时，隐藏整个 Jade 叠加层，
        // 避免驾驶载具时屏幕顶部的实体/方块信息造成干扰。
        registration.addRayTraceCallback((hitResult, accessor, originalAccessor) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null &&
                    ((IEntityMixin) mc.player).machine_Max$getControllingSubsystem() instanceof SeatSubsystem) {
                return null; // 返回 null 可完全取消 Jade 叠加层的显示
            }
            return accessor;
        });
    }
}
