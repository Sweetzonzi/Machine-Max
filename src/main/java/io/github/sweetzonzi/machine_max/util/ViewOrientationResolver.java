package io.github.sweetzonzi.machine_max.util;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;

/**
 * 工程视图自动取向解析器
 * <p>
 * 功能：
 * 1. 根据相机 forward 向量，选择模型应展示的主观察面（Facing）
 * 2. 根据 up 向量，在该观察面内选择“向上方向”
 * 3. 将 (Facing + UpDir) 映射为整数倍 90° 的欧拉旋转
 * 4. 引入“滞后阈值（Hysteresis）”，避免在临界角度频繁抖动
 * <p>
 * 使用方式：
 * - 为每个需要自动取向的 HUD / 模型持有一个实例
 * - 每帧调用 resolveViewRotation(forward, up)
 */
public class ViewOrientationResolver {

    /**
     * 主观察面
     */
    public enum Facing {
        POS_X, NEG_X,
        POS_Y, NEG_Y,
        POS_Z, NEG_Z
    }

    /**
     * 在观察面内的“向上方向”
     */
    public enum UpDir {
        POS_X, NEG_X,
        POS_Y, NEG_Y,
        POS_Z, NEG_Z
    }

    /**
     * 滞后系数（Hysteresis Factor）
     * <p>
     * 含义：
     * - 新方向的“主轴分量”必须 ≥ 当前方向主轴分量 × hysteresisFactor
     * 才允许切换 Facing
     * <p>
     * 推荐范围：
     * - 1.10 ~ 1.25
     */
    private final float hysteresisFactor;

    /**
     * 上一帧已选定的主观察面（用于滞后判断）
     */
    private Facing lastFacing = null;

    public ViewOrientationResolver() {
        this(1.05f);
    }

    public ViewOrientationResolver(float hysteresisFactor) {
        this.hysteresisFactor = hysteresisFactor;
    }

    /**
     * (Facing + UpDir) → 欧拉角旋转（度）
     * 所有角度均为 90° 的整数倍，使用时请用YXZ顺序
     * <p>
     * 注意：
     * - Vector3f(x, y, z) 表示绕 X/Y/Z 轴的旋转角度（单位：度）
     */
    private static final Map<Facing, Map<UpDir, Vector3f>> ROTATION_TABLE = Map.of(
            Facing.POS_Z, Map.of(
                    UpDir.POS_Y, new Vector3f(0, 0, 0),
                    UpDir.NEG_Y, new Vector3f(0, 0, 180),
                    UpDir.POS_X, new Vector3f(0, 0, -90),
                    UpDir.NEG_X, new Vector3f(0, 0, 90)
            ),
            Facing.NEG_Z, Map.of(
                    UpDir.POS_Y, new Vector3f(0, 180, 0),
                    UpDir.NEG_Y, new Vector3f(0, 180, 180),
                    UpDir.POS_X, new Vector3f(0, 180, 90),
                    UpDir.NEG_X, new Vector3f(0, 180, -90)
            ),
            Facing.POS_X, Map.of(
                    UpDir.POS_Y, new Vector3f(0, -90, 0),
                    UpDir.NEG_Y, new Vector3f(0, -90, 180),
                    UpDir.POS_Z, new Vector3f(0, -90, 90),
                    UpDir.NEG_Z, new Vector3f(0, -90, -90)
            ),
            Facing.NEG_X, Map.of(
                    UpDir.POS_Y, new Vector3f(0, 90, 0),
                    UpDir.NEG_Y, new Vector3f(0, 90, 180),
                    UpDir.POS_Z, new Vector3f(0, 90, -90),
                    UpDir.NEG_Z, new Vector3f(0, 90, 90)
            ),
            Facing.POS_Y, Map.of(
                    UpDir.POS_Z, new Vector3f(-90, 180, 0),
                    UpDir.NEG_Z, new Vector3f(90, 0, 0),
                    UpDir.POS_X, new Vector3f(0, 90, 90),
                    UpDir.NEG_X, new Vector3f(0, -90, -90)
            ),
            Facing.NEG_Y, Map.of(
                    UpDir.POS_Z, new Vector3f(-90, 0, 0),
                    UpDir.NEG_Z, new Vector3f(90, 180, 0),
                    UpDir.POS_X, new Vector3f(0, -90, 90),
                    UpDir.NEG_X, new Vector3f(0, 90, -90)
            )
    );

    /**
     * 根据相机朝向解析模型应使用的工程视图旋转
     *
     * @param forward 相机 forward 向量（世界或相机空间，长度不限）
     * @param up      相机 up 向量
     * @return 四元数旋转，平行于各个坐标轴
     */
    public Quaternionf resolveViewRotation(Vector3f forward, Vector3f up) {
        // 1. 根据 forward + 滞后规则选择主观察面
        Facing facing = resolveFacingWithHysteresis(forward);

        // 2. 在该观察面内，根据 up 选择向上方向
        UpDir upDir = resolveUpDir(forward, up, facing);

        // 3. 查表得到最终旋转
        Map<UpDir, Vector3f> byUp = ROTATION_TABLE.get(facing);
        if (byUp == null) {
            return new Quaternionf();
        }

        Vector3f rot = byUp.get(upDir);

        return rot != null ? new Quaternionf().rotateYXZ(
                (float) Math.toRadians(rot.y()),
                (float) Math.toRadians(rot.x()),
                (float) Math.toRadians(rot.z())) : new Quaternionf();
    }

    /**
     * 根据 forward 向量解析主观察面，并应用滞后阈值
     */
    public Facing resolveFacingWithHysteresis(Vector3f forward) {
        Vector3f f = new Vector3f(forward).normalize();

        float ax = Math.abs(f.x());
        float ay = Math.abs(f.y());
        float az = Math.abs(f.z());

        // 当前 forward 的最强轴
        Facing candidate;
        float candidateStrength;

        if (ax >= ay && ax >= az) {
            candidate = f.x() > 0 ? Facing.POS_X : Facing.NEG_X;
            candidateStrength = ax;
        } else if (ay >= az) {
            candidate = f.y() > 0 ? Facing.POS_Y : Facing.NEG_Y;
            candidateStrength = ay;
        } else {
            candidate = f.z() > 0 ? Facing.POS_Z : Facing.NEG_Z;
            candidateStrength = az;
        }

        // 若尚无历史状态，直接采用
        if (lastFacing == null) {
            lastFacing = candidate;
            return candidate;
        }

        // 计算当前 facing 对应的轴强度
        float lastStrength = switch (lastFacing) {
            case POS_X, NEG_X -> ax;
            case POS_Y, NEG_Y -> ay;
            case POS_Z, NEG_Z -> az;
        };

        // 滞后判断：只有“显著更强”才允许切换
        if (candidate != lastFacing &&
                candidateStrength < lastStrength * hysteresisFactor) {
            return lastFacing;
        }

        lastFacing = candidate;
        return candidate;
    }


    /**
     * 在指定观察面内，根据 up 向量解析“向上方向”
     * <p>
     * 说明：
     * - 会先将 up 正交化到 forward 的切平面内，避免抖动
     */
    public UpDir resolveUpDir(Vector3f forward, Vector3f up, Facing face) {
        Vector3f f = new Vector3f(forward).normalize();
        Vector3f u = new Vector3f(up).normalize();

        // Gram–Schmidt 正交化：移除 forward 分量
        u.fma(-u.dot(f), f).normalize();

//        return switch (face) {
//            // XY 平面
//            case POS_Z, NEG_Z -> Math.abs(u.y()) >= Math.abs(u.x())
//                    ? (u.y() > 0 ? UpDir.POS_Y : UpDir.NEG_Y)
//                    : (u.x() > 0 ? UpDir.POS_X : UpDir.NEG_X);
//
//            // YZ 平面
//            case POS_X, NEG_X -> Math.abs(u.y()) >= Math.abs(u.z())
//                    ? (u.y() > 0 ? UpDir.POS_Y : UpDir.NEG_Y)
//                    : (u.z() > 0 ? UpDir.POS_Z : UpDir.NEG_Z);
//
//            // XZ 平面
//            case POS_Y, NEG_Y -> Math.abs(u.z()) >= Math.abs(u.x())
//                    ? (u.z() > 0 ? UpDir.POS_Z : UpDir.NEG_Z)
//                    : (u.x() > 0 ? UpDir.POS_X : UpDir.NEG_X);
//        };

        return switch (face) {
            // XY 平面
            case POS_Z, NEG_Z -> (u.y() > 0 ? UpDir.POS_Y : UpDir.NEG_Y);

            // YZ 平面
            case POS_X, NEG_X -> (u.y() > 0 ? UpDir.POS_Y : UpDir.NEG_Y);

            // XZ 平面
            case POS_Y, NEG_Y -> Math.abs(u.z()) >= Math.abs(u.x())
                    ? (u.z() > 0 ? UpDir.POS_Z : UpDir.NEG_Z)
                    : (u.x() > 0 ? UpDir.POS_X : UpDir.NEG_X);
        };
    }
}
