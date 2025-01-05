package io.github.tt432.machinemax.common.sloarphys;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class MMClientPhysLevel extends MMAbstractPhysLevel {
    public volatile boolean needSync = false;

    public MMClientPhysLevel(@NotNull String id, @NotNull String name, @NotNull ClientLevel level, long tickStep) {
        super(id, name, level, tickStep);
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
