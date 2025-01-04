package io.github.tt432.machinemax.common.sloarphys;

import cn.solarmoon.spark_core.phys.thread.PhysLevel;
import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.util.data.BodiesSyncData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.ode4j.ode.DAABBC;
import org.ode4j.ode.DGeom;
import org.ode4j.ode.OdeHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractPhysLevel extends PhysLevel {

    volatile public Map<Integer, BodiesSyncData> syncData = HashMap.newHashMap(100);//用于同步的线程内所有运动体位姿速度数据
    protected Map<BlockPos, BlockState> terrainCollisionBlocks = HashMap.newHashMap(100);//用于碰撞检测的地形块位置集合
    public ArrayList<DGeom> terrainGeoms = new ArrayList<>();
    protected int step = 0;//物理运算迭代运行的总次数
    /**
     * 初始化物理模拟线程，设置仿真基本参数
     */
    public AbstractPhysLevel(@NotNull Level level) {
        super(level);
        getPhysWorld().laterConsume(()->{//修改世界设置
            getPhysWorld().getWorld().setGravity(0, -9.81, 0);//设置重力
            getPhysWorld().getWorld().setERP(0.3);
            getPhysWorld().getWorld().setCFM(0.00005);
            getPhysWorld().getWorld().setAutoDisableFlag(true);//设置静止物体自动休眠以节约性能
            getPhysWorld().getWorld().setAutoDisableSteps(5);
            getPhysWorld().getWorld().setQuickStepNumIterations(40);//设定迭代次数以提高物理计算精度
            getPhysWorld().getWorld().setQuickStepW(1.3);
            getPhysWorld().getWorld().setContactMaxCorrectingVel(20);
            //TODO:区分碰撞空间(常规)，命中判定空间(弹头刀刃等放进来)和自体碰撞空间(头发布料等有物理没碰撞的放进来)
            OdeHelper.createPlane(getPhysWorld().getSpace(), 0, 1, 0, -64);//创造碰撞平面
            return null;
        });
    }

    @Override
    public void physTick() {
        step++;
        addTerrainCollisionBoxes();
        super.physTick();
    }

    /**
     * 将运动物体周围的方块添加到碰撞空间中
     */
    protected void addTerrainCollisionBoxes() {
        terrainCollisionBlocks.clear();
        for (DGeom geom : getPhysWorld().getSpace().getGeoms()) {
            if (geom.getBody() != null) {
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
                    geoms[j] = OdeHelper.createBox(getPhysWorld().getSpace(), 1, 1, 1);
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
