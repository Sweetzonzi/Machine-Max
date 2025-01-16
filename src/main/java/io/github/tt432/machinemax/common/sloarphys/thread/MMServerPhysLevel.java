package io.github.tt432.machinemax.common.sloarphys.thread;

import io.github.tt432.machinemax.common.part.AbstractPart;
import io.github.tt432.machinemax.network.payload.PhysSyncPayload;
import io.github.tt432.machinemax.util.data.PosRotVel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.ode4j.ode.DBody;

public class MMServerPhysLevel extends MMAbstractPhysLevel {



    public MMServerPhysLevel(@NotNull ResourceLocation id, @NotNull String name, @NotNull Level level, long tickStep, boolean customApply) {
        super(id, name, level, tickStep, customApply);
    }

    @Override
    public void physTick() {
        if (step % 5 == 0) syncBodies();//每5次物理运算(0.1秒)迭代运行一次同步
        super.physTick();
    }

    /**
     * 将物理线程内所有运动体的位姿速度打包发送给本维度内所有玩家
     */
    protected void syncBodies() {
        syncData.clear();
        DBody b;
        for (AbstractPart part : syncParts.values()) {//记录所有需要同步的部件根零件的位置、姿态、速度和角速度
            b = part.rootBody.getBody();
            syncData.put(part.getId(), new PosRotVel(b.getPosition(), b.getQuaternion(), b.getLinearVel(), b.getAngularVel()));
        }
        if (!syncData.isEmpty()) {//同步给维度内的玩家
            PacketDistributor.sendToPlayersInDimension((ServerLevel) getLevel(), new PhysSyncPayload(step, syncData));
        }
    }
}
