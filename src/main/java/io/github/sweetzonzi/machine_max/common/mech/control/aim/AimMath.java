package io.github.sweetzonzi.machine_max.common.mech.control.aim;

import net.minecraft.world.phys.Vec2;

import net.minecraft.world.phys.Vec3;

/**
 * 瞄准几何与输入的纯函数，不读取世界、活体刚体或渲染状态。
 * 角度统一使用弧度，Vec2 的 x 表示俯仰、y 表示偏航；正俯仰抬高光轴，正偏航向右。
 * 调用线程：主线程或物理线程，输入数据由调用方提供。
 */
public final class AimMath {
    public static final double AIM_DISTANCE_METERS = 1000;
    public static final double MAX_INPUT_LEAD_RAD = Math.toRadians(30);
    private static final double DIFFERENCE_STEP = 1e-3;
    private AimMath() {}

    /** 将向右／向下的鼠标输入按当前变焦倍率转换为俯仰／偏航角增量。 */
    public static Vec2 mouse(double right, double down, double zoom) {
        double s = Math.toRadians(.15) / Math.max(zoom, .1);
        return new Vec2((float) (-s * down), (float) (s * right));
    }

    public static Vec2 advanceRelativeAim(AimGeometrySnapshot b, Vec2 targetAngles, Vec2 inputDelta) {
        return b.constrain(new Vec2(targetAngles.x + inputDelta.x, targetAngles.y + inputDelta.y));
    }

    /** 只限制本次输入扩大的领先量；零输入保持既存目标，反向输入可立即缩小领先量。 */
    public static double filterLead(double delta, double desired, double actual, double limit) {
        double e = desired - actual;
        return Math.clamp(delta, Math.min(0, -limit - e), Math.max(0, limit - e));
    }

    public static Vec2 filterLead(Vec2 inputDelta, Vec2 desiredAngles, Vec2 actualAngles) {
        return new Vec2((float) filterLead(inputDelta.x, desiredAngles.x, actualAngles.x, MAX_INPUT_LEAD_RAD),
                (float) filterLead(inputDelta.y, desiredAngles.y, actualAngles.y, MAX_INPUT_LEAD_RAD));
    }

    public static Vec3 buildAimPoint(AimGeometrySnapshot b, Vec2 q) {
        AimPose pose = b.forward(q);
        return pose.position().add(pose.forward().scale(AIM_DISTANCE_METERS));
    }

    private static Vec3 residual(AimGeometrySnapshot b, Vec3 point, Vec2 q) {
        AimPose pose = b.forward(q);
        Vec3 direction = point.subtract(pose.position());
        if (direction.length() < 1e-9) return Vec3.ZERO;
        return pose.forward().subtract(direction.normalize());
    }

    /** 求解结果：期望关节角、残余指向误差（弧度），以及目标方向是否有效。 */
    public record Solution(Vec2 angles, double errorRadians, boolean valid) {}

    /** 在机械行程内使用局部阻尼最小二乘求解双轴目标，并以已有解保持连续角分支。 */
    public static Solution solveAim(AimGeometrySnapshot b, Vec3 point, Vec2 previous) {
        Vec2 q = refine(b, point, b.constrain(previous));
        if (residual(b, point, q).length() > .1) {
            q = solveSingleAxisAim(b, point, q, true, q).angles();
            q = solveSingleAxisAim(b, point, q, false, q).angles();
            q = refine(b, point, q);
        }
        return solution(b, point, q);
    }

    private static Vec2 refine(AimGeometrySnapshot b, Vec3 point, Vec2 initial) {
        Vec2 q = initial;
        for (int i = 0; i < 48; i++) {
            Vec3 r = residual(b, point, q);
            if (r.length() < 1e-7) break;
            Vec3 jy = residual(b, point, new Vec2((float) (q.x), (float) (q.y+DIFFERENCE_STEP)))
                    .subtract(residual(b, point, new Vec2((float) (q.x), (float) (q.y-DIFFERENCE_STEP))))
                    .scale(.5 / DIFFERENCE_STEP);
            Vec3 jp = residual(b, point, new Vec2((float) (q.x+DIFFERENCE_STEP), (float) (q.y)))
                    .subtract(residual(b, point, new Vec2((float) (q.x-DIFFERENCE_STEP), (float) (q.y))))
                    .scale(.5 / DIFFERENCE_STEP);
            double a = jy.dot(jy)+1e-10, c = jp.dot(jp)+1e-10, cross = jy.dot(jp);
            double ry = -jy.dot(r), rp = -jp.dot(r), det = a*c-cross*cross;
            double dy = Math.clamp((ry*c-rp*cross)/det, -.5, .5);
            double dp = Math.clamp((rp*a-ry*cross)/det, -.5, .5);
            Vec2 next = q;
            for (double scale = 1; scale >= 1.0/128; scale *= .5) {
                Vec2 candidate = b.constrain(new Vec2((float) (q.x+scale*dp), (float) (q.y+scale*dy)));
                if (residual(b, point, candidate).length() < r.length()) { next = candidate; break; }
            }
            if (next.equals(q)) break;
            q = next;
        }
        return q;
    }

    /**
     * 固定无稳轴的相对目标，仅搜索稳定轴以减小指向误差。
     * 连续轴在已有解相邻的一周内搜索，有限轴在机械行程内搜索。
     *
     * @param stableYaw true 表示仅偏航稳定，false 表示仅俯仰稳定
     */
    public static Solution solveSingleAxisAim(AimGeometrySnapshot b, Vec3 point, Vec2 held,
                                               boolean stableYaw, Vec2 previous) {
        AimAxisLimit limit = stableYaw ? b.yawLimit() : b.pitchLimit();
        double old = stableYaw ? previous.y : previous.x;
        double low = limit.continuous() ? old-Math.PI : limit.min();
        double high = limit.continuous() ? old+Math.PI : limit.max();
        double best = limit.apply(old);
        double score = score(b, point, held, stableYaw, best);
        double step = (high-low)/64;
        for (int i = 0; i <= 64; i++) {
            double angle = low + step*i;
            double value = score(b, point, held, stableYaw, angle);
            if (value < score - scoreTolerance(score)) { best = angle; score = value; }
        }
        if (step > 0) {
            double left = Math.max(low, best-step), right = Math.min(high, best+step);
            for (int i = 0; i < 64; i++) {
                double m1 = left+(right-left)/3, m2 = right-(right-left)/3;
                if (score(b, point, held, stableYaw, m1) < score(b, point, held, stableYaw, m2)) right = m2;
                else left = m1;
            }
            double candidate = (left+right)/2;
            if (score(b, point, held, stableYaw, candidate) < score - scoreTolerance(score)) best = candidate;
        }
        best = limit.near(best, old);
        return solution(b, point, stableYaw ? new Vec2((float) (held.x), (float) (best)) : new Vec2((float) (best), (float) (held.y)));
    }

    private static double score(AimGeometrySnapshot b, Vec3 p, Vec2 held, boolean yaw, double angle) {
        Vec3 r = residual(b, p, yaw ? new Vec2((float) (held.x), (float) (angle)) : new Vec2((float) (angle), (float) (held.y)));
        return r.dot(r);
    }
    // JME 正解使用 float；误差改善小于其精度时保持已有分支，避免退化方向上的数值抖动。
    private static double scoreTolerance(double score) {
        return 1e-6 * Math.sqrt(score) + 1e-13;
    }
    private static Solution solution(AimGeometrySnapshot b, Vec3 p, Vec2 q) {
        AimPose pose = b.forward(q);
        Vec3 to = p.subtract(pose.position());
        if (to.length() < 1e-9) return new Solution(q, Math.PI, false);
        Vec3 d = to.normalize();
        double error = Math.atan2(pose.forward().cross(d).length(), pose.forward().dot(d));
        return new Solution(q, error, true);
    }
}
