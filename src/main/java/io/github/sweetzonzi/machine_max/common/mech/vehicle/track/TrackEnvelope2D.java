package io.github.sweetzonzi.machine_max.common.mech.vehicle.track;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.util.geometry.BeltPath2D;
import io.github.sweetzonzi.machine_max.util.geometry.Circle2D;
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
 * {@link SubPart#getMaxWheelRadius()}。这与 {@link BeltPath2D} 的 2D 约定一致，
 * 渲染时再按 {@code (0, y, z)} 映射回三维。</p>
 *
 * <p>输入顺序就是 {@link TrackLink#members()} 的顺序。本方法先把列表旋转到主动轮
 * 首位（保持绕行方向不变），再按“主动轮 → 下行程 → 上行程”的锚定顺序构建。</p>
 *
 * <p>轮心全共线，或轮组实际重叠导致有序点接触无解时，回退到圆盘凸包
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
     * @param path           锚定顺序包络，或共线退化时的凸包回退
     * @param sprocketWorld  主动轮刚体的世界位姿（局部系 → 世界）
     * @param fallback       是否走了共线凸包回退
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
     * @return 包络结果；任一成员缺少轮半径或几何完全不可用时返回 null
     */
    @Nullable
    public static Result build(List<SubPart> members, @Nullable SubPart sprocket, float partialTick) {
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

        Matrix4f sprocketWorld = sprocket.getWorldPositionMatrix(partialTick);
        Matrix4f worldToSprocket = new Matrix4f(sprocketWorld).invert();

        List<Circle2D> circles = new ArrayList<>(ordered.size());
        for (SubPart member : ordered) {
            if (member == null || member.isDestroyed()) {
                return null;
            }
            float radius = member.getMaxWheelRadius();
            if (radius <= MIN_RADIUS) {
                return null;
            }
            Matrix4f memberLocal = new Matrix4f(worldToSprocket)
                    .mul(member.getWorldPositionMatrix(partialTick));
            Vector3f center = memberLocal.getTranslation(new Vector3f());
            circles.add(new Circle2D(center.y, center.z, radius));
        }

        try {
            return new Result(BeltPath2D.build(circles), sprocketWorld, false);
        } catch (BeltPath2D.DegenerateLayoutException degenerate) {
            return fallback(circles, sprocketWorld, "履带包络退化（轮心共线），回退凸包: "
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
