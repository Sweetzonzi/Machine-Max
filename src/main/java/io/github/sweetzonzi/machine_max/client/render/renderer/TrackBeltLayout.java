package io.github.sweetzonzi.machine_max.client.render.renderer;

import io.github.sweetzonzi.machine_max.util.geometry.BeltPath2D;
import io.github.sweetzonzi.machine_max.util.geometry.Vec2;

/**
 * 主动轮包裹段保持固定角节距，其余路径承载剩余连续节序。
 * {@link #sample(int)} 解析采样轮缘；路径距离只用于包裹段之外的采样。
 *
 * <p>{@code count} 是履带的持久化节数，{@code pitch} 是型号声明的每节长度：两者都来自
 * 型号 / 存档数据，不随某一帧的包络快照变化。包络周长变化只体现在包裹段之外的节间距上。</p>
 */
final class TrackBeltLayout {
    private final BeltPath2D path;
    private final BeltPath2D.ArcContact contact;
    private final int count;
    private final double pitch, entryLabel, engaged, start, end, rest;

    TrackBeltLayout(BeltPath2D path, BeltPath2D.ArcContact contact, int count,
                    double pitch, double entryLabel) {
        this.path = path;
        this.contact = contact;
        this.count = count;
        this.pitch = pitch;
        this.entryLabel = entryLabel;
        start = path.distanceAt(contact.contactPoints().getFirst());
        end = path.distanceAt(contact.contactPoints().getLast());
        double covered = positive(end - start, path.length());
        if (Math.abs(contact.sweep()) > Math.PI * 2 - 1e-8) covered = path.length();
        rest = path.length() - covered;
        engaged = contact.contactLength() / pitch;
    }

    boolean isValid() { return rest > 1e-8 && engaged < count && engaged > 0; }

    double spacing() { return Math.max(pitch, rest / (count - engaged)); }

    double spacing(BeltPath2D.Sample sample) {
        return sample.edgeIndex() == -1 ? pitch : rest / (count - engaged);
    }

    BeltPath2D.Sample sample(int index) {
        double u = positive(index - entryLabel, count);
        if (u < engaged) {
            var circle = path.inputCircles().get(contact.wheelIndex());
            double sign = Math.signum(contact.sweep());
            double angle = contact.startAngle() + sign * u * pitch / circle.radius();
            Vec2 normal = Vec2.fromAngle(angle, 1);
            return new BeltPath2D.Sample(circle.pointAt(angle), normal.leftNormal().mul(sign),
                    normal, -1, start + u * pitch);
        }
        return path.sampleAt(end + rest * (u - engaged) / (count - engaged));
    }

    static double positive(double value, double period) {
        double result = value % period;
        return result < 0 ? result + period : result;
    }
}
