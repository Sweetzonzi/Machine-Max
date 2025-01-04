package io.github.tt432.machinemax.common.sloarphys;

import io.github.tt432.machinemax.network.payload.PhysSyncPayload;
import io.github.tt432.machinemax.util.data.BodiesSyncData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.ode4j.ode.DBody;
import org.ode4j.ode.internal.DxBody;

import java.util.Iterator;

public class ServerPhysLevel extends AbstractPhysLevel{
    /**
     * 初始化物理模拟线程，设置仿真基本参数
     *
     * @param level
     */
    public ServerPhysLevel(@NotNull Level level) {
        super(level);
    }

    @Override
    public void physTick() {
        syncBodies();
        super.physTick();
    }

    /**
     * 将物理线程内所有运动体的位姿速度打包发送给本维度内所有玩家
     */
    protected void syncBodies() {
        syncData.clear();
//        for (Iterator<DBody> it = getPhysWorld().getBodyIterator(); it.hasNext(); ) {//记录本维度内每个运动体的位置、姿态、速度和角速度
//            DxBody b = (DxBody) it.next();
//            syncData.put(b.getId(), new BodiesSyncData(b.getPosition().copy(), b.getQuaternion().copy(), b.getLinearVel().copy(), b.getAngularVel().copy()));
//        }
        if (!syncData.isEmpty())//维度存在运动体则将信息同步给维度内的玩家
            PacketDistributor.sendToPlayersInDimension((ServerLevel) getLevel(), new PhysSyncPayload(step, syncData));

    }
}
