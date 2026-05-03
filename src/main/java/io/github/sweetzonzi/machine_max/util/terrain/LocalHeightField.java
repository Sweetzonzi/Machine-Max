package io.github.sweetzonzi.machine_max.util.terrain;

import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.physics.terrain.SectionSnapshot;
import com.jme3.math.Vector3f;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class LocalHeightField {

    /** 表面类型：硬质（镐/斧可挖掘或基岩）、软质（锹可挖掘）、无（空气/树叶等） */
    private enum SurfaceType { HARD, SOFT, NONE }

    /**
     * 高度场半径（单位：方块）
     */
    private final int radius;

    /**
     * 网格尺寸 = 2R+1
     */
    private final int size;

    /** 硬质表面原始高度（镐/斧可挖掘或基岩） */
    private final float[][] hardRawHeight;
    /** 软质表面原始高度（锹可挖掘） */
    private final float[][] softRawHeight;
    /** 硬质表面平滑高度 */
    private final float[][] hardSmoothHeight;
    /** 软质表面平滑高度 */
    private final float[][] softSmoothHeight;
    /** 每格表面类型，决定查询时使用哪张高度场 */
    private final SurfaceType[][] surfaceType;

    /**
     * 高度场左下角世界坐标
     */
    private int originX;
    private int originZ;

    public LocalHeightField(int radius) {
        this.radius = radius;
        this.size = radius * 2 + 1;
        this.hardRawHeight = new float[size][size];
        this.softRawHeight = new float[size][size];
        this.hardSmoothHeight = new float[size][size];
        this.softSmoothHeight = new float[size][size];
        this.surfaceType = new SurfaceType[size][size];
    }

    /**
     * 构建部件附近的局部高度场
     * <p>
     * 分别构建硬质场和软质场，各自独立平滑。
     * 硬质表面（石材、木材、基岩等）的平滑不受相邻软质地面（泥土、沙子等）影响，
     * 从而避免铺装路面被地面拉成拱形。
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
                sampleColumn(level, wx, wz, baseY, x, z);
            }
        }

        smoothField(hardRawHeight, hardSmoothHeight, 3);
        smoothField(softRawHeight, softSmoothHeight, 3);
    }

    /**
     * 采样某一方块柱的最高碰撞高度，并按方块类型分入硬质/软质高度场
     * <p>
     * 分类规则：
     * <ul>
     *   <li>硬质：镐可挖掘、斧可挖掘或基岩 → 填入硬质场，软质场置 -∞</li>
     *   <li>软质：锹可挖掘 → 填入软质场，硬质场置 -∞</li>
     *   <li>其他（树叶、植株等非硬非软方块）：两场均置 -∞，不参与平滑</li>
     * </ul>
     * 两张高度场完全互补，确保平滑时各场只与同类型邻居交互。
     *
     * @param level 物理世界
     * @param wx    世界X坐标
     * @param wz    世界Z坐标
     * @param baseY 部件底部参考高度
     * @param gx    网格X索引
     * @param gz    网格Z索引
     */
    private void sampleColumn(PhysicsLevel level, int wx, int wz, float baseY, int gx, int gz) {

        int by = (int) Math.floor(baseY);

        float highestHeight = Float.NEGATIVE_INFINITY;
        SurfaceType highestType = SurfaceType.NONE;

        // 在车辆附近垂直范围内搜索碰撞体
        for (int dy = -2; dy <= 4; dy++) {

            BlockPos pos = new BlockPos(wx, by + dy, wz);

            SectionSnapshot.BlockSnapshot snap =
                    level.terrainManager.getBlockSnapshotAt(pos);

            if (snap == null) {
                if (highestType == SurfaceType.NONE)
                    continue;
                else break;
            }

            BlockState state = snap.getState();

            var shape = state.getCollisionShape(
                    EmptyBlockGetter.INSTANCE,
                    BlockPos.ZERO
            );

            if (shape.isEmpty()) {
                if (highestType == SurfaceType.NONE)
                    continue;
                else break;
            }

            float height = (float) shape.max(Direction.Axis.Y);
            float worldHeight = pos.getY() + height;

            if (worldHeight <= highestHeight) continue;

            // 判断方块表面类型
            SurfaceType type;
            if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)
                    || state.is(BlockTags.MINEABLE_WITH_AXE)
                    || state.is(Blocks.BEDROCK)) {
                type = SurfaceType.HARD;
            } else if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
                type = SurfaceType.SOFT;
            } else {
                continue; // 树叶等非硬非软方块跳过，不参与高度场
            }

            highestHeight = worldHeight;
            highestType = type;
        }

        // 根据类型填入对应高度场，另一场置 -∞ 实现互补
        surfaceType[gx][gz] = highestType;
        if (highestType == SurfaceType.HARD) {
            hardRawHeight[gx][gz] = highestHeight;
            hardSmoothHeight[gx][gz] = highestHeight;
            softRawHeight[gx][gz] = Float.NEGATIVE_INFINITY;
            softSmoothHeight[gx][gz] = Float.NEGATIVE_INFINITY;
        } else if (highestType == SurfaceType.SOFT) {
            hardRawHeight[gx][gz] = Float.NEGATIVE_INFINITY;
            hardSmoothHeight[gx][gz] = Float.NEGATIVE_INFINITY;
            softRawHeight[gx][gz] = highestHeight;
            softSmoothHeight[gx][gz] = highestHeight;
        } else {
            hardRawHeight[gx][gz] = Float.NEGATIVE_INFINITY;
            hardSmoothHeight[gx][gz] = Float.NEGATIVE_INFINITY;
            softRawHeight[gx][gz] = Float.NEGATIVE_INFINITY;
            softSmoothHeight[gx][gz] = Float.NEGATIVE_INFINITY;
        }
    }

    /**
     * 对单张高度场进行平滑处理
     * <p>
     * 使用基于差值的 Gaussian 卷积（Laplacian smoothing）。
     * <p>
     * 同时满足以下约束：
     * <p>
     * 1 不允许跨越断崖（高度为 -∞）
     * 2 不允许平滑结果超过原始高度
     * <p>
     * 参数化设计允许硬质场和软质场各自独立调用，
     * 保证不同材质表面的平滑互不干扰。
     *
     * @param rawField    原始高度（同时作为上限约束）
     * @param smoothField 输出平滑高度
     * @param iterations  卷积次数
     */
    private void smoothField(float[][] rawField, float[][] smoothField, int iterations) {

        float[][] src = rawField;
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

                            float w = (dx == 0 || dz == 0) ? 1f : 0.5f;

                            float delta = h - center;
                            if (delta > 0) continue; // 比自身高的地形不纳入考虑，避免被额外抬高产生突变阶梯
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
                    if (h > rawField[x][z]) {
                        h = rawField[x][z];
                    }

                    dst[x][z] = h;
                }
            }

            // swap buffers
            float[][] tmp = src;
            src = dst;
            dst = tmp;
        }

        // 最终结果写入 smoothField
        for (int x = 0; x < size; x++) {
            System.arraycopy(src[x], 0, smoothField[x], 0, size);
        }
    }

    /**
     * 双线性插值获取高度
     * <p>
     * 根据查询位置所在网格的表面类型，自动路由到硬质或软质高度场。
     * 硬质路面与软质地面的平滑结果彼此隔离，互不干扰。
     *
     * @param worldX 世界X
     * @param worldZ 世界Z
     * @return 高度，若该位置无表面（空气）则返回负无穷
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

        // 根据主格点表面类型选择对应高度场
        SurfaceType type = surfaceType[x0][z0];
        float[][] field;
        if (type == SurfaceType.HARD) {
            field = hardSmoothHeight;
        } else if (type == SurfaceType.SOFT) {
            field = softSmoothHeight;
        } else {
            return Float.NEGATIVE_INFINITY;
        }

        float h00 = field[x0][z0];
        float h10 = field[x0 + 1][z0];
        float h01 = field[x0][z0 + 1];
        float h11 = field[x0 + 1][z0 + 1];

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
        if (wheelCenter.subtract(initialContact).lengthSquared() > 1e-6f)
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
