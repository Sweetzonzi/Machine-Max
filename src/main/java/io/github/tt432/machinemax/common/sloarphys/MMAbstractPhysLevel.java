package io.github.tt432.machinemax.common.sloarphys;

import cn.solarmoon.spark_core.phys.thread.PhysLevel;
import io.github.tt432.machinemax.common.part.AbstractPart;
import io.github.tt432.machinemax.util.data.BodiesSyncData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.ode4j.ode.DAABBC;
import org.ode4j.ode.DBody;
import org.ode4j.ode.DGeom;
import org.ode4j.ode.OdeHelper;
import org.ode4j.ode.threading.task.MultiThreadTaskExecutor;

import java.util.ArrayList;
import java.util.HashMap;

public abstract class MMAbstractPhysLevel extends PhysLevel {

    volatile public HashMap<Integer, BodiesSyncData> syncData = HashMap.newHashMap(100);//用于同步的线程内所有运动体位姿速度数据
    volatile public HashMap<Integer, AbstractPart> syncParts = HashMap.newHashMap(100);//用于同步的线程内所有部件
    protected HashMap<BlockPos, BlockState> terrainCollisionBlocks = HashMap.newHashMap(100);//用于碰撞检测的地形块位置集合
    public ArrayList<DGeom> terrainGeoms = new ArrayList<>();
    public int step = 0;//物理运算迭代运行的总次数

    public MMAbstractPhysLevel(@NotNull String id, @NotNull String name, @NotNull Level level, long tickStep, boolean customApply) {
        super(id, name, level, tickStep, customApply);
        init(this);
    }


    @Override
    public void physTick() {
        step++;
        addTerrainCollisionBoxes();
        super.physTick();
    }

    public void init(PhysLevel level) {
        level.getPhysWorld().laterConsume(() -> {//修改世界设置
            level.getPhysWorld().getWorld().setGravity(0, -9.81, 0);//设置重力
            level.getPhysWorld().getWorld().setERP(0.3);
            level.getPhysWorld().getWorld().setCFM(0.00005);
            level.getPhysWorld().getWorld().setAutoDisableFlag(false);//设置静止物体自动休眠以节约性能
//            level.getPhysWorld().getWorld().setAutoDisableSteps(5);
            level.getPhysWorld().getWorld().setQuickStepNumIterations(40);//设定迭代次数以提高物理计算精度
            level.getPhysWorld().getWorld().setTaskExecutor(new MultiThreadTaskExecutor(6));//设定线程数以提高物理计算效率
            level.getPhysWorld().getWorld().setQuickStepW(1.3);
            level.getPhysWorld().getWorld().setContactMaxCorrectingVel(20);
            //TODO:区分碰撞空间(常规)，命中判定空间(弹头刀刃等放进来)和自体碰撞空间(头发布料等有物理没碰撞的放进来)
            return null;
        });
    }

    /**
     * 将运动物体周围的方块添加到碰撞空间中
     */
    protected void addTerrainCollisionBoxes() {
        terrainCollisionBlocks.clear();
        for (DGeom geom : getPhysWorld().getSpace().getGeoms()) {
            if (geom.getBody() != null && !terrainGeoms.contains(geom)) {
                DAABBC aabb = geom.getAABB();
                int minX = (int) Math.floor(aabb.getMin0() - 1);
                int maxX = (int) Math.ceil(aabb.getMax0()) + 1;
                int minY = (int) Math.floor(aabb.getMin1());
                int maxY = (int) Math.ceil(aabb.getMax1());
                int minZ = (int) Math.floor(aabb.getMin2() - 1);
                int maxZ = (int) Math.ceil(aabb.getMax2() + 1);

                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            BlockPos pos = new BlockPos(x, y, z);
                            BlockState state = getLevel().getBlockState(pos);
                            if (!state.getCollisionShape(getLevel(), pos).isEmpty()) {
                                // 如果块不是空气或可替换方块，记录方块的状态和坐标
                                terrainCollisionBlocks.put(pos, state);
                            }
                        }
                    }
                }
            }
        }
        int blockNum = terrainCollisionBlocks.size();
        if (blockNum > 0) {
            if (terrainGeoms.size() < blockNum) {//所需方块数量大于现有碰撞体数量时，创建新的碰撞体
                int i = blockNum - terrainGeoms.size();
                DGeom[] geoms = new DGeom[i];
                for (int j = 0; j < i; j++) {
                    DBody body = OdeHelper.createBody("terrain", getLevel(), false, getPhysWorld().getWorld());
                    body.setKinematic();
                    geoms[j] = OdeHelper.createBox(getPhysWorld().getSpace(), 1.0, 1.0, 1.0);
                    geoms[j].setBody(body);
                    geoms[j].setPosition(0, -512, 0);
                    terrainGeoms.add(geoms[j]);
                }
            }
            int i = 0;
            for (BlockPos pos : terrainCollisionBlocks.keySet()) {
                terrainGeoms.get(i).setPosition(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                i++;
            }
        }
    }
}
