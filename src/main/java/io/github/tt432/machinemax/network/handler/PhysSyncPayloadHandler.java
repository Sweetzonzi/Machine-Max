package io.github.tt432.machinemax.network.handler;

import cn.solarmoon.spark_core.phys.thread.ThreadHelperKt;
import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.sloarphys.MMClientPhysLevel;
import io.github.tt432.machinemax.mixin_interface.IMixinLevel;
import io.github.tt432.machinemax.network.payload.PhysSyncPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class PhysSyncPayloadHandler {
    public static void handler(final PhysSyncPayload payload, final IPayloadContext context) {
        MMClientPhysLevel localThread = (MMClientPhysLevel) ThreadHelperKt.getPhysLevelById(context.player().level(), MachineMax.MOD_ID);
        localThread.syncData = payload.syncData();//将服务器运动体的位置姿态速度传入本地物理线程
        localThread.needSync = true;//修改运动体同步标记状态
        //TODO:根据时间戳判定数据包的有效性，并根据延迟情况对客户端位姿进行预测
    }
}
