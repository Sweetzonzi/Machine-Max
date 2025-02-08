package io.github.tt432.machinemax.common.phys.thread;

import io.github.tt432.machinemax.common.vehicle.AbstractPart;
import io.github.tt432.machinemax.util.data.PosRotVel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.ode4j.ode.DBody;

import java.util.HashMap;

public class MMClientPhysLevel extends MMAbstractPhysLevel {
    public volatile boolean needSync = false;
    public volatile HashMap<Integer, Integer> partNoInfoCount = HashMap.newHashMap(100);

    public MMClientPhysLevel(@NotNull ResourceLocation id, @NotNull String name, @NotNull Level level, long tickStep, boolean customApply) {
        super(id, name, level, tickStep, customApply);
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
        for (AbstractPart part : syncParts.values()) {//遍历所有需要同步的部件
            if (syncData.get(part.getId()) != null) {//若同步数据包内包含对应部件的信息
                partNoInfoCount.put(part.getId(), 0);//重置重发次数
                DBody b = part.rootBody.getBody();
                PosRotVel data = syncData.get(part.getId());
                b.setPosition(data.pos());//同步位置
                b.setQuaternion(data.rot());//同步姿态
                b.setLinearVel(data.lVel());//同步线速度
                b.setAngularVel(data.aVel());//同步角速度
            } else {//若同步数据包内不包含对应部件的信息
                if (partNoInfoCount.get(part.getId()) == null) return;
                partNoInfoCount.put(part.getId(), partNoInfoCount.get(part.getId()) + 1);
                if (partNoInfoCount.get(part.getId()) > 5)//若超过5次同步都没有收到对应部件的信息，则认为服务端已将其删除
                    part.removeAllBodiesFromLevel();//故删除服务器中不再存在的部件
            }
        }
    }

    protected void updateMolang() {
//        for (Iterator<DBody> it = world.getBodyIterator(); it.hasNext(); ) {//遍历线程内所有运动体
//            DxBody b = (DxBody) it.next();
//            AbstractPart part = b.getAttachedPart();
//            if (part!= null && part.molangScope!= null) {
//                part.molangScope.updatePhysMolang();
//            }
//        }
    }
}
