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

public class ClientPhysLevel extends AbstractPhysLevel{
    public volatile boolean needSync = false;
    /**
     * 初始化物理模拟线程，设置仿真基本参数
     *
     * @param level
     */
    public ClientPhysLevel(@NotNull Level level) {
        super(level);
    }

    @Override
    public void physTick() {
        if (needSync) {
            syncBodies();
            needSync = false;
        }
        super.physTick();
        updateMolang();
    }

    /**
     * 更新本维度内每个运动体的位置、姿态、速度和角速度
     */
    protected void syncBodies() {
//        for (Iterator<DBody> it = world.getBodyIterator(); it.hasNext(); ) {//遍历线程内所有运动体
//            DxBody b = (DxBody) it.next();
//            if (syncData.get(b.getId()) != null) {//若同步数据包内包含对应运动体的信息
//                BodiesSyncData data = syncData.get(b.getId());
//                b.setPosition(data.pos());//同步位置
//                b.setQuaternion(data.rot());//同步姿态
//                b.setLinearVel(data.lVel());//同步线速度
//                b.setAngularVel(data.aVel());//同步角速度
//            }
//        }
    }
    protected void updateMolang(){
//        for (Iterator<DBody> it = world.getBodyIterator(); it.hasNext(); ) {//遍历线程内所有运动体
//            DxBody b = (DxBody) it.next();
//            AbstractPart part = b.getAttachedPart();
//            if (part!= null && part.molangScope!= null) {
//                part.molangScope.updatePhysMolang();
//            }
//        }
    }
}
