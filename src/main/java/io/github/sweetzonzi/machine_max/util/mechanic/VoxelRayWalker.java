package io.github.sweetzonzi.machine_max.util.mechanic;

import com.jme3.math.Vector3f;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.List;

/**
 * 跨 section 的全局 3D DDA 体素遍历（Amanatides–Woo）。
 *
 * <p>与 {@code ProjectileManager.walkBlocksAlongRay} 的三点关键差异：</p>
 * <ol>
 *   <li>直接读 Minecraft 区块数据（{@code LevelChunk.getSection(...).getBlockState(...)}），
 *       不要求已构建的物理地形 section；</li>
 *   <li>跨 section 连续遍历，不裁剪到单个 16&sup3;；</li>
 *   <li>游标由调用方持有，可跨 tick 复用。</li>
 * </ol>
 *
 * <p><b>线程</b>：主线程。区块数据由主线程独占。</p>
 */
public final class VoxelRayWalker {

    private static final float EPS = 1e-7f;

    private VoxelRayWalker() {
    }

    /**
     * 沿射线推进 {@code distance} 米，按 {@code hitFraction} 升序收集途经的实体方块命中。
     *
     * <p>起点所在体素只在**首次**遍历时判定一次（由游标的 {@code started} 标志控制），
     * 因此当起点埋在实心方块内部时该方块会参与判定；之后同一体素不会因跨 tick 推进被重复报告。</p>
     *
     * @param level    世界（主线程）
     * @param from     段起点（世界坐标）
     * @param dir      单位方向（构造后不变）
     * @param distance 段长度（米）
     * @param cursor   调用方持有的游标，原地推进
     * @param out      结果列表，命中按参数升序追加（不清空）
     */
    public static void walk(Level level, Vector3f from, Vector3f dir, float distance,
                            VoxelCursor cursor, List<VoxelHit> out) {
        if (distance <= 0f) return;

        float dx = dir.x, dy = dir.y, dz = dir.z;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < EPS) return;
        dx /= len;
        dy /= len;
        dz /= len;

        final float ox = from.x, oy = from.y, oz = from.z;
        int ix = (int) Math.floor(ox);
        int iy = (int) Math.floor(oy);
        int iz = (int) Math.floor(oz);

        if (!cursor.started) {
            cursor.started = true;
            cursor.voxelX = ix;
            cursor.voxelY = iy;
            cursor.voxelZ = iz;
            // 起点体素参与判定（起点在实心方块内部时若跳过会导致爆炸从介质里"凭空穿出"）
            addIfSolid(level, ix, iy, iz, 0f, out);
        }

        // 步进方向与跨越参数（tMax 相对本段起点，单位米）
        int stepX = 0, stepY = 0, stepZ = 0;
        float tDeltaX = Float.POSITIVE_INFINITY, tDeltaY = Float.POSITIVE_INFINITY, tDeltaZ = Float.POSITIVE_INFINITY;
        float tMaxX = Float.POSITIVE_INFINITY, tMaxY = Float.POSITIVE_INFINITY, tMaxZ = Float.POSITIVE_INFINITY;

        if (dx > EPS || dx < -EPS) {
            stepX = dx > 0 ? 1 : -1;
            tDeltaX = Math.abs(1f / dx);
            tMaxX = ((stepX > 0 ? (ix + 1) : ix) - ox) / dx;
        }
        if (dy > EPS || dy < -EPS) {
            stepY = dy > 0 ? 1 : -1;
            tDeltaY = Math.abs(1f / dy);
            tMaxY = ((stepY > 0 ? (iy + 1) : iy) - oy) / dy;
        }
        if (dz > EPS || dz < -EPS) {
            stepZ = dz > 0 ? 1 : -1;
            tDeltaZ = Math.abs(1f / dz);
            tMaxZ = ((stepZ > 0 ? (iz + 1) : iz) - oz) / dz;
        }

        while (true) {
            float t;
            int axis;
            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                axis = 0;
                t = tMaxX;
            } else if (tMaxY <= tMaxZ) {
                axis = 1;
                t = tMaxY;
            } else {
                axis = 2;
                t = tMaxZ;
            }
            // t 为 +Inf（三轴均无跨越）或超出本段长度时结束
            if (!(t <= distance)) break;

            switch (axis) {
                case 0 -> {
                    ix += stepX;
                    tMaxX += tDeltaX;
                }
                case 1 -> {
                    iy += stepY;
                    tMaxY += tDeltaY;
                }
                default -> {
                    iz += stepZ;
                    tMaxZ += tDeltaZ;
                }
            }
            cursor.voxelX = ix;
            cursor.voxelY = iy;
            cursor.voxelZ = iz;
            cursor.lastNormalAxis = axis;
            addIfSolid(level, ix, iy, iz, t / distance, out);
        }
    }

    /** 判定单个体素是否为"会挡住射线的实体方块"，是则追加一条命中。 */
    private static void addIfSolid(Level level, int x, int y, int z, float fraction, List<VoxelHit> out) {
        if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) return;
        // 只读已加载区块；未加载的区块视作空气，避免遍历触发区块加载
        LevelChunk chunk;
        if (level.getChunkSource().getChunk(x >> 4, z >> 4, ChunkStatus.FULL, false) instanceof LevelChunk loaded) {
            chunk = loaded;
        } else {
            return;
        }
        int sectionIndex = chunk.getSectionIndex(y);
        if (sectionIndex < 0 || sectionIndex >= chunk.getSectionsCount()) return;
        LevelChunkSection section = chunk.getSection(sectionIndex);
        if (section == null || section.hasOnlyAir()) return;

        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
        if (state.isAir()) return;
        if (state.getCollisionShape(level, pos).isEmpty()) return;
        out.add(new VoxelHit(pos, fraction, state));
    }
}
