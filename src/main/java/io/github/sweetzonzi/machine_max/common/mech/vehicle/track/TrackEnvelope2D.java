package io.github.sweetzonzi.machine_max.common.mech.vehicle.track;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.util.geometry.BeltPath2D;
import io.github.sweetzonzi.machine_max.util.geometry.Circle2D;
import io.github.sweetzonzi.machine_max.util.geometry.Vec2;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * 把一条履带的 {@link TrackLink#members()} 换算成主动轮局部系里的 2D 圆组，再交给
 * {@link BeltPath2D}。
 *
 * <p>坐标系：主动轮刚体局部系。局部 X 是滚动轴（与 {@code GearJoint} 的轴一致），
 * 所以履带平面是 YZ 平面；每个轮心取 {@code (localY, localZ)}，半径取
 * {@link SubPart#getMaxWheelRadius()} 减去履带几何厚度，表示履带内表面。
 * 传动与带速使用的碰撞半径保持原值。尺寸契约见
 * {@code docs/plan/进行中/履带系统-物理实现设计文档.md} §2.4。
 * 调用几何求解器时显式传入该有向平面的绕向，
 * 渲染时按 {@code (0, y, z)} 映射回三维。</p>
 *
 * <p>输入顺序就是 {@link TrackLink#members()} 的顺序。本方法先把列表旋转到主动轮
 * 首位（保持绕行方向不变），再按“主动轮 → 下行程 → 上行程”的锚定顺序构建。</p>
 *
 * <p>轮组实际重叠或相邻轮心重合时，回退到圆盘凸包
 * （{@link Result#fallback()} 为 true），并记录告警。其它几何失败返回 null，
 * 不做静默凸包回退，避免把抬轮凹口抹平。</p>
 *
 * <p>线程：主线程（服务端耐久推导、客户端渲染），不读写物理线程状态。</p>
 */
public final class TrackEnvelope2D {
    private static final float MIN_RADIUS = 1e-4f;

    private TrackEnvelope2D() {
    }

    /**
     * @param path           有序接触路径，或重叠轮组的凸包回退
     * @param sprocketWorld  主动轮刚体的世界位姿（局部系 → 世界）
     * @param fallback       是否使用凸包回退
     */
    public record Result(BeltPath2D path, Matrix4f sprocketWorld, boolean fallback) {
        public Result {
            sprocketWorld = new Matrix4f(sprocketWorld);
        }
    }

    /**
     * @param members     成员表，顺序为“主动轮 → 下行程 → 上行程”
     * @param sprocket    基准轮；为 null、不在成员表中或成员不足 2 个时返回 null
     * @param partialTick 位姿插值系数
     * @param linkThickness 履带几何厚度（米），从成员碰撞半径中扣除
     * @param clockwise 创建时确定的主动轮局部 (Y,Z) 平面内顺时针绕向
     * @return 包络结果；任一成员缺少轮半径或几何完全不可用时返回 null
     */
    @Nullable
    public static Result build(List<SubPart> members, @Nullable SubPart sprocket, float partialTick,
                               float linkThickness, boolean clockwise) {
        if (sprocket == null || members.size() < 2) {
            return null;
        }
        int sprocketIndex = members.indexOf(sprocket);
        if (sprocketIndex < 0) {
            return null;
        }
        List<SubPart> ordered = members;
        if (sprocketIndex > 0) {
            ordered = new ArrayList<>(members.size());
            for (int i = 0; i < members.size(); i++) {
                ordered.add(members.get((sprocketIndex + i) % members.size()));
            }
        }

        List<Matrix4f> poses = new ArrayList<>(ordered.size());
        List<Float> radii = new ArrayList<>(ordered.size());
        for (SubPart member : ordered) {
            if (member == null || member.isDestroyed()) {
                return null;
            }
            float radius = member.getMaxWheelRadius();
            if (radius <= MIN_RADIUS) {
                return null;
            }
            poses.add(member.getWorldPositionMatrix(partialTick));
            radii.add(radius);
        }
        try {
            return buildPoses(poses, radii, linkThickness, clockwise);
        } catch (IllegalArgumentException invalidInput) {
            MachineMax.LOGGER.warn("履带包络尺寸不可用：{}；碰撞半径={}，履带厚度={}",
                    invalidInput.getMessage(), radii, linkThickness);
            return null;
        }
    }

    /**
     * 主线程的位姿快照入口。二维坐标为主动轮局部 (Y,Z)，使用创建时确定的绕向。
     *
     * @param collisionRadii 轮面碰撞半径；内表面包络半径为该值减去 linkThickness
     * @param linkThickness 履带几何厚度（米），必须有限且大于 0
     * @throws IllegalArgumentException 厚度非法，或扣厚度后的半径非有限或不大于数值阈值
     */
    public static Result buildPoses(List<Matrix4f> poses, List<Float> collisionRadii,
                                    float linkThickness, boolean clockwise) {
        if (poses.size() < 2 || poses.size() != collisionRadii.size()) {
            throw new IllegalArgumentException("wheel poses and radii must have matching sizes >= 2");
        }
        if (!Float.isFinite(linkThickness) || !(linkThickness > 0f)) {
            throw new IllegalArgumentException("link thickness must be finite and > 0");
        }
        Matrix4f sprocketWorld = poses.getFirst();
        Matrix4f worldToSprocket = new Matrix4f(sprocketWorld).invert();
        List<Circle2D> circles = new ArrayList<>(poses.size());
        for (int i = 0; i < poses.size(); i++) {
            double radius = (double) collisionRadii.get(i) - linkThickness;
            if (!Double.isFinite(radius) || radius <= MIN_RADIUS) {
                throw new IllegalArgumentException("wheel " + i
                        + " collision radius minus link thickness must be finite and > " + MIN_RADIUS);
            }
            Matrix4f memberLocal = new Matrix4f(worldToSprocket).mul(poses.get(i));
            Vector3f center = memberLocal.getTranslation(new Vector3f());
            circles.add(new Circle2D(center.y, center.z, radius));
        }
        try {
            return new Result(BeltPath2D.build(circles, 1e-9, clockwise), sprocketWorld, false);
        } catch (BeltPath2D.DegenerateLayoutException degenerate) {
            return fallback(circles, sprocketWorld, "履带包络退化，回退凸包: "
                    + degenerate.getMessage());
        } catch (IllegalArgumentException | IllegalStateException orderedFailure) {
            if (BeltPath2D.hasOverlappingWheels(circles)) {
                // 悬挂压缩导致轮子互相嵌入时，有序点接触模型可能无解。回退凸包
                // 会丢掉抬轮凹口，但比整条履带消失更接近可见结果；日志保留原因。
                return fallback(circles, sprocketWorld, "履带轮组重叠，包络回退凸包: "
                        + orderedFailure.getMessage());
            }
            MachineMax.LOGGER.warn("履带包络构建失败，跳过该履带：{}；成员圆={}",
                    orderedFailure.getMessage(), circles);
            return null;
        }
    }

    /** 主线程：在闭合铺设时，从当时的轮位姿和世界下方向确定顺时针绕向。 */
    public static boolean layingClockwise(List<Matrix4f> poses) {
        Matrix4f worldToSprocket = new Matrix4f(poses.getFirst()).invert();
        List<Vec2> centers = new ArrayList<>(poses.size());
        for (Matrix4f pose : poses) {
            Vector3f center = new Matrix4f(worldToSprocket).mul(pose)
                    .getTranslation(new Vector3f());
            centers.add(new Vec2(center.y, center.z));
        }
        Vector3f localDown = worldToSprocket.transformDirection(new Vector3f(0, -1, 0));
        Vec2 down = new Vec2(localDown.y, localDown.z);
        boolean clockwise = false;
        double lowest = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < centers.size() - 1; i++) {
            Vec2 a = centers.get(i), b = centers.get(i + 1);
            double side = b.sub(a).normalized().rightNormal().dot(down);
            double height = a.add(b).mul(.5).dot(down);
            if (Math.abs(side) > 1e-6 && height > lowest + 1e-9) {
                lowest = height;
                clockwise = side < 0;
            }
        }
        return clockwise;
    }

    @Nullable
    private static Result fallback(List<Circle2D> circles, Matrix4f sprocketWorld, String reason) {
        try {
            MachineMax.LOGGER.warn("{}；成员圆={}", reason, circles);
            return new Result(BeltPath2D.convexFallback(circles), sprocketWorld, true);
        } catch (IllegalArgumentException | IllegalStateException fallbackFailure) {
            MachineMax.LOGGER.warn("履带包络凸包回退失败：{}；成员圆={}",
                    fallbackFailure.getMessage(), circles);
            return null;
        }
    }
}
