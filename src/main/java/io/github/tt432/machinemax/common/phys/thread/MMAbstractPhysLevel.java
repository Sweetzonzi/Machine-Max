package io.github.tt432.machinemax.common.phys.thread;


import io.github.tt432.machinemax.util.data.PosRotVel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public abstract class MMAbstractPhysLevel {

//    volatile public HashMap<Integer, PosRotVel> syncData = HashMap.newHashMap(100);//用于同步的线程内所有运动体位姿速度数据
//    volatile public HashMap<Integer, AbstractPart> syncParts = HashMap.newHashMap(100);//用于同步的线程内所有部件
//    protected HashMap<BlockPos, BlockState> terrainCollisionBlocks = HashMap.newHashMap(100);//用于碰撞检测的地形块位置集合
//    public ArrayList<DGeom> terrainGeoms = new ArrayList<>();
//    protected HashMap<Long, ChunkAccess> terrainChunkCache = HashMap.newHashMap(4);//地形碰撞方块所在的区块缓存
//    protected HashMap<Long, Integer> terrainChunkCount = HashMap.newHashMap(4);//地形碰撞方块所在的区块缓存
//    int terrainChunkMaxTick = 50;//地形碰撞方块所在的区块缓存的清理时间，指定次数物理计算未被访问后将被从缓存中清除
//    public int vehicleUUID = 0;//物理运算迭代运行的总次数
//    long start;
//    long end;
//
//    public MMAbstractPhysLevel(@NotNull ResourceLocation id, @NotNull String name, @NotNull Level level, long tickStep, boolean customApply) {
//        super(id, name, level, tickStep, customApply);
//        init(this);
//    }
//
//
//    @Override
//    public void physTick() {
//        start = System.nanoTime();
//        vehicleUUID++;
//        addTerrainCollisionBoxes();
//        super.physTick();
//        end = System.nanoTime();
////        if (getLevel().dimension() == Level.OVERWORLD)System.out.println("level: " + getLevel() + " vehicleUUID: " + vehicleUUID + " time: " + (end - start) / 1000000);
//    }
//
//    public void init(PhysLevel level) {
//        level.getWorld().laterConsume(() -> {//修改世界设置
//            level.getWorld().setGravity(0, -9.81, 0);//设置重力
//            level.getWorld().setERP(0.3);
//            level.getWorld().setCFM(0.00005);
//            level.getWorld().setAutoDisableFlag(false);//设置静止物体自动休眠以节约性能
////            level.getWorld().setAutoDisableSteps(5);
//            level.getWorld().setQuickStepNumIterations(40);//设定迭代次数以提高物理计算精度
//            level.getWorld().setTaskExecutor(new MultiThreadTaskExecutor(6));//设定线程数以提高物理计算效率
//            level.getWorld().setQuickStepW(1.3);
//            level.getWorld().setContactMaxCorrectingVel(20);
//            //TODO:区分碰撞空间(常规)，命中判定空间(弹头刀刃等放进来)和自体碰撞空间(头发布料等有物理没碰撞的放进来)
//            return null;
//        });
//    }
//
//    /**
//     * 将运动物体周围的方块添加到碰撞空间中
//     */
//    protected void addTerrainCollisionBoxes() {
//        terrainCollisionBlocks.clear();
//        DAABBC aabb;
//        BlockPos pos;
//        BlockState state;
//        long chunkPos;
//        Iterator<HashMap.Entry<Long, Integer>> iterator = terrainChunkCount.entrySet().iterator();
//        while (iterator.hasNext()) {//清理过期区块缓存
//            HashMap.Entry<Long, Integer> entry = iterator.next();
//            entry.setValue(entry.getValue() - 1);
//            if (entry.getValue() <= 0) {
//                chunkPos = entry.getKey();
//                terrainChunkCache.remove(chunkPos);
//                iterator.remove();
//            }
//        }
//        for (DGeom geom : getWorld().getSpace().getGeoms()) {
//            if (geom.getBody() != null && !terrainGeoms.contains(geom)) {
//                aabb = geom.getAABB();
//                int minX = (int) Math.floor(aabb.getMin0() - 1);
//                int maxX = (int) Math.ceil(aabb.getMax0()) + 1;
//                int minY = (int) Math.floor(aabb.getMin1());
//                int maxY = (int) Math.ceil(aabb.getMax1());
//                int minZ = (int) Math.floor(aabb.getMin2() - 1);
//                int maxZ = (int) Math.ceil(aabb.getMax2() + 1);
//                for (int x = minX; x <= maxX; x++) {
//                    for (int y = minY; y <= maxY; y++) {
//                        for (int z = minZ; z <= maxZ; z++) {
//                            pos = new BlockPos(x, y, z);
//                            chunkPos = ChunkPos.asLong(pos);
//                            if (terrainCollisionBlocks.get(pos) == null) {//如果该位置的方块没有记录过，则获取块状态
//                                if (terrainChunkCache.get(chunkPos) == null) {//服务端的getChunk()性能不佳，故如果该位置的区块没有缓存过，则获取区块并缓存
//                                    terrainChunkCache.put(chunkPos, getLevel().getChunk(pos));
//                                    terrainChunkCount.put(chunkPos, terrainChunkMaxTick);
//                                }
//                                state = terrainChunkCache.get(ChunkPos.asLong(pos)).getBlockState(pos);
//                                if (!state.isAir() || !state.getCollisionShape(getLevel(), pos).isEmpty()) {
//                                    // 如果块不是空气或可替换方块，记录方块的状态和坐标
//                                    terrainCollisionBlocks.put(pos, state);
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//        }
//        int blockNum = terrainCollisionBlocks.size();
//        if (blockNum > 0) {
//            if (terrainGeoms.size() < blockNum) {//所需方块数量大于现有碰撞体数量时，创建新的碰撞体
//                int i = blockNum - terrainGeoms.size();
//                DGeom[] geoms = new DGeom[i];
//                for (int j = 0; j < i; j++) {
//                    BlockBody blockBody = new BlockBody("terrain", getLevel(), getWorld().getSpace());
//                    geoms[j] = blockBody.getGeoms().getFirst();
//                    terrainGeoms.add(geoms[j]);
//                }
//            }
//            int i = 0;
//            for (BlockPos blockPos : terrainCollisionBlocks.keySet()) {
//                terrainGeoms.get(i).setPosition(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
//                i++;
//            }
//        }
//    }
}
