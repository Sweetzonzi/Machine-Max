package io.github.sweetzonzi.machine_max.util.terrain;

import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.physics.terrain.SectionSnapshot;
import com.jme3.math.Vector3f;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;

public class LocalHeightField {

    /**
     * 高度场半径（单位：方块）
     */
    private final int radius;

    /**
     * 卷积核半径（单位：方块）
     */
    private final int kernelRadius;

    /**
     * 网格尺寸 = 2R+1
     */
    private final int size;

    /**
     * 原始高度（方块柱采样）
     */
    private final float[][] rawHeight;

    /**
     * 平滑高度
     */
    private final float[][] smoothHeight;

    /**
     * 高度场左下角世界坐标
     */
    private int originX;
    private int originZ;

    public LocalHeightField(int radius, int kernelRadius) {
        this.radius = radius;
        this.kernelRadius = kernelRadius;
        this.size = radius * 2 + 1;
        this.rawHeight = new float[size][size];
        this.smoothHeight = new float[size][size];
        float sigma = kernelRadius * 0.5f;
    }

    /**
     * 构建部件附近的局部高度场
     *
     * @param level   物理世界
     * @param centerX 高度场中心方块X
     * @param centerZ 高度场中心方块Z
     * @param baseY   部件底部参考高度
     */
    public void rebuild(PhysicsLevel level, int centerX, int centerZ, float baseY) {

        originX = centerX - radius;
        originZ = centerZ - radius;

        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {

                int wx = originX + x;
                int wz = originZ + z;

                float h = sampleColumn(level, wx, wz, baseY);

                rawHeight[x][z] = h;
                smoothHeight[x][z] = h;
            }
        }

        smooth(3);
    }

    /**
     * 采样某一方块柱的最高碰撞高度
     * <p>
     * 说明：
     * Minecraft 的碰撞形状可能不是完整方块（如半砖、雪层等），
     * 因此需要读取 VoxelShape 并取其最高 Y。
     * <p>
     * 如果该列没有任何碰撞体（空气/断崖），返回负无穷，
     * 用于在平滑阶段阻止跨越断崖。
     */
    private float sampleColumn(PhysicsLevel level, int x, int z, float baseY) {

        int by = (int) Math.floor(baseY);

        float highest = Float.NEGATIVE_INFINITY;

        // 在车辆附近垂直范围内搜索碰撞体
        for (int dy = -2; dy <= 4; dy++) {

            BlockPos pos = new BlockPos(x, by + dy, z);

            SectionSnapshot.BlockSnapshot snap =
                    level.terrainManager.getBlockSnapshotAt(pos);

            if (snap == null) {
                if (highest == Float.NEGATIVE_INFINITY)
                    continue;
                else break;
            }

            BlockState state = snap.getState();

            var shape = state.getCollisionShape(
                    EmptyBlockGetter.INSTANCE,
                    BlockPos.ZERO
            );

            if (shape.isEmpty()) {
                if (highest == Float.NEGATIVE_INFINITY)
                    continue;
                else break;
            }

            float height = (float) shape.max(Direction.Axis.Y);

            float worldHeight = pos.getY() + height;

            if (worldHeight > highest) {
                highest = worldHeight;
            }
        }

        return highest;
    }

    /**
     * 对高度场进行平滑处理
     * <p>
     * 使用基于差值的 Gaussian 卷积（Laplacian smoothing）。
     * <p>
     * 与传统卷积高度不同，本方法只卷积高度差：
     * <p>
     * Δh = Σ w * (h_neighbor - h_center)
     * <p>
     * 这样可以避免在台阶处出现最大坡度的问题，
     * 使坡度在空间上均匀分布。
     * <p>
     * 同时满足以下约束：
     * <p>
     * 1 不允许跨越断崖（高度为 -∞）
     * 2 不允许平滑结果超过原始高度
     *
     * @param iterations 卷积次数
     */
    private void smooth(int iterations) {

        float[][] src = rawHeight;
        float[][] dst = new float[size][size];

        for (int iter = 0; iter < iterations; iter++) {

            for (int x = 0; x < size; x++) {
                for (int z = 0; z < size; z++) {

                    float center = src[x][z];

                    // 如果该列为空气（断崖）
                    if (center == Float.NEGATIVE_INFINITY) {
                        dst[x][z] = center;
                        continue;
                    }

                    float deltaSum = 0f;
                    float weight = 0f;

                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {

                            if (dx == 0 && dz == 0) continue;

                            // 边界 clamp
                            int sx = Math.max(0, Math.min(size - 1, x + dx));
                            int sz = Math.max(0, Math.min(size - 1, z + dz));

                            float h = src[sx][sz];

                            // 跳过空气
                            if (h == Float.NEGATIVE_INFINITY) continue;

                            float w = (dx == 0 || dz == 0) ? 1f : 1f;

                            float delta = h - center;

                            deltaSum += delta * w;
                            weight += w;
                        }
                    }

                    if (weight == 0) {
                        dst[x][z] = center;
                        continue;
                    }

                    float delta = deltaSum / weight;

                    float h = center + delta;

                    // 不允许超过原始高度
                    if (h > rawHeight[x][z]) {
                        h = rawHeight[x][z];
                    }

                    dst[x][z] = h;
                }
            }

            // swap buffers
            float[][] tmp = src;
            src = dst;
            dst = tmp;
        }

        // 最终结果写入 smoothHeight
        for (int x = 0; x < size; x++) {
            System.arraycopy(src[x], 0, smoothHeight[x], 0, size);
        }
    }

    /**
     * 双线性插值获取高度
     *
     * @param worldX 世界X
     * @param worldZ 世界Z
     * @return 高度
     */
    public float getHeight(float worldX, float worldZ) {
        // 将世界坐标映射到网格坐标（网格原点为左下角方块中心）
        float u = worldX - (originX + 0.5f);
        float v = worldZ - (originZ + 0.5f);

        int x0 = (int) Math.floor(u);
        int z0 = (int) Math.floor(v);

        // 边界裁剪（允许的网格索引范围 [0, size-1] 对应 u ∈ [-0.5, size-0.5)）
        if (x0 < 0) x0 = 0;
        if (z0 < 0) z0 = 0;
        if (x0 >= size - 1) x0 = size - 2;
        if (z0 >= size - 1) z0 = size - 2;

        float fx = u - x0;   // 插值权重（范围 [0,1]）
        float fz = v - z0;

        float h00 = smoothHeight[x0][z0];
        float h10 = smoothHeight[x0 + 1][z0];
        float h01 = smoothHeight[x0][z0 + 1];
        float h11 = smoothHeight[x0 + 1][z0 + 1];

        // 若任一角为断崖，则整点无效
        if (h00 == Float.NEGATIVE_INFINITY ||
                h10 == Float.NEGATIVE_INFINITY ||
                h01 == Float.NEGATIVE_INFINITY ||
                h11 == Float.NEGATIVE_INFINITY) {
            return Float.NEGATIVE_INFINITY;
        }

        float hx0 = h00 + (h10 - h00) * fx;
        float hx1 = h01 + (h11 - h01) * fx;
        return hx0 + (hx1 - hx0) * fz;
    }

    /**
     * 计算高度场法线
     * <p>
     * 使用中心差分估计梯度：
     * <p>
     * dh/dx = (h(x+ε) - h(x-ε)) / 2ε
     * dh/dz = (h(z+ε) - h(z-ε)) / 2ε
     * <p>
     * 最终法线：
     * <p>
     * (-dh/dx, 1, -dh/dz)
     */
    public Vector3f getNormal(float x, float z) {

        float eps = 0.5f;

        float hL = getHeight(x - eps, z);
        float hR = getHeight(x + eps, z);
        float hD = getHeight(x, z - eps);
        float hU = getHeight(x, z + eps);

        if (Float.isInfinite(hL) ||
                Float.isInfinite(hR) ||
                Float.isInfinite(hD) ||
                Float.isInfinite(hU)) {
            return new Vector3f(0, 1, 0);
        }

        float dx = (hR - hL) / (2 * eps);
        float dz = (hU - hD) / (2 * eps);

        Vector3f n = new Vector3f(-dx, 1f, -dz);
        return n.normalize();
    }

    /**
     * 求解轮胎球面与高度场的真实接触
     * <p>
     * 使用固定点迭代：
     * <p>
     * 1 初始点使用 Bullet 提供的接触点
     * 2 根据高度场法线反推球面接触点
     * 3 更新接触点并重复
     * <p>
     * 由于地形坡度有限，通常 2~3 次即可收敛。
     *
     * @param wheelCenter    轮胎球心
     * @param radius         轮胎半径
     * @param initialContact Bullet 提供的接触点
     */
    public ContactResult solveContact(
            Vector3f wheelCenter,
            float radius,
            Vector3f initialContact) {

        float x = initialContact.x;
        float z = initialContact.z;

        Vector3f normal = new Vector3f();
        if(wheelCenter.subtract(initialContact).lengthSquared() > 1e-6f)
            for (int i = 0; i < 3; i++) {

                float h = getHeight(x, z);

                if (Float.isInfinite(h)) break;

                normal.set(getNormal(x, z));

                // 根据法线反推球面接触点
                x = wheelCenter.x - normal.x * radius;
                z = wheelCenter.z - normal.z * radius;
            }

        float h = getHeight(x, z);

        Vector3f contact = new Vector3f(x, h, z);

        normal.set(getNormal(x, z));

        float penetration =
                radius - (wheelCenter.y - h) / normal.y;

        return new ContactResult(contact, normal, penetration);
    }

    /**
     * 接触求解结果
     *
     * @param contact     接触点
     * @param normal      接触法线
     * @param penetration 侵入深度
     */
    public record ContactResult(Vector3f contact, Vector3f normal, float penetration) {
    }
}
