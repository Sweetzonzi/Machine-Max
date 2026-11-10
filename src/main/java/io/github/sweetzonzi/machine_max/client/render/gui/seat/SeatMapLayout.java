package io.github.sweetzonzi.machine_max.client.render.gui.seat;

import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.bounding.BoundingBox;
import com.jme3.math.Quaternion;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.seat.SeatOrder;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 客户端装配体结构缓存。参考件的世界位姿由各消费方按采样相位读取。 */
public final class SeatMapLayout {
    public record Snapshot(@Nullable SubPart reference, List<SeatSubsystem> seats,
                           List<SubPart> subParts, @Nullable Bounds bounds) {
        public int numberOf(@Nullable SeatSubsystem seat) {
            return seat == null ? 0 : seats.indexOf(seat) + 1;
        }
    }

    public record Bounds(float centerX, float centerY, float centerZ,
                         float halfX, float halfY, float halfZ) {
        public Matrix4f projection(Matrix4f worldToReference, float x, float y, float width, float height,
                                   float yaw, float pitch, float roll) {
            Matrix4f rotation = new Matrix4f()
                    .rotateZ((float) Math.toRadians(roll))
                    .rotateX((float) Math.toRadians(pitch))
                    .rotateY((float) Math.toRadians(yaw));
            // 旋转后的 AABB 半宽等于八个旋转角点在该轴上的最大绝对坐标。
            float halfWidth = Math.abs(rotation.m00()) * halfX + Math.abs(rotation.m10()) * halfY
                    + Math.abs(rotation.m20()) * halfZ;
            float halfHeight = Math.abs(rotation.m01()) * halfX + Math.abs(rotation.m11()) * halfY
                    + Math.abs(rotation.m21()) * halfZ;
            float fit = 0.9f * Math.min(width / Math.max(2f * halfWidth, 0.1f),
                    height / Math.max(2f * halfHeight, 0.1f));
            return new Matrix4f().translate(x + width / 2f, y + height / 2f, 0f)
                    .scale(fit, -fit, fit)
                    .mul(rotation)
                    .translate(-centerX, -centerY, -centerZ)
                    .mul(worldToReference);
        }

        static Bounds between(Vector3fc min, Vector3fc max) {
            return new Bounds(min.x() * 0.5f + max.x() * 0.5f, min.y() * 0.5f + max.y() * 0.5f,
                    min.z() * 0.5f + max.z() * 0.5f, max.x() * 0.5f - min.x() * 0.5f,
                    max.y() * 0.5f - min.y() * 0.5f, max.z() * 0.5f - min.z() * 0.5f);
        }
    }

    private static final ConcurrentHashMap<VehicleCore, Snapshot> CACHE = new ConcurrentHashMap<>();

    private SeatMapLayout() {}

    public static void invalidate(VehicleCore vehicle) {
        CACHE.remove(vehicle);
    }

    public static void retain(Set<VehicleCore> vehicles) {
        CACHE.keySet().retainAll(vehicles);
    }

    public static Snapshot snapshot(VehicleCore vehicle) {
        return CACHE.computeIfAbsent(vehicle, SeatMapLayout::build);
    }

    private static Snapshot build(VehicleCore vehicle) {
        SeatOrder.Layout layout = SeatOrder.layout(vehicle);
        SubPart reference = layout.reference();
        List<SubPart> subParts = new ArrayList<>();
        for (var part : vehicle.partMap.values()) subParts.addAll(part.subParts.values());
        Bounds bounds = reference == null ? null : measure(subParts, reference);
        return new Snapshot(reference, List.copyOf(layout.seats()), List.copyOf(subParts), bounds);
    }

    @Nullable
    public static Vector3f seatWorldPosition(SeatSubsystem seat, float partialTick) {
        SubPart sub = seat.getSubPart();
        String name = seat.getAttr().getLocator();
        var local = name == null ? null : sub.getAttr().locatorTransforms.get(name);
        if (local == null) return null;
        Vector3f position = SparkMathKt.toVector3f(local.getTranslation());
        if (!position.isFinite()) return null;
        sub.getWorldPositionMatrix(partialTick).transformPosition(position);
        return position.isFinite() ? position : null;
    }

    @Nullable
    private static Bounds measure(List<SubPart> subParts, SubPart reference) {
        Matrix4f worldToReference = reference.getWorldPositionMatrix(1f).invert();
        Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
        Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);
        Vector3f corner = new Vector3f();
        for (SubPart sub : subParts) {
            BoundingBox box;
            try {
                box = sub.collisionShape.boundingBox(new com.jme3.math.Vector3f(), Quaternion.IDENTITY, null);
            } catch (RuntimeException e) {
                continue;
            }
            if (box == null) continue;
            Vector3f center = SparkMathKt.toVector3f(box.getCenter(null));
            Matrix4f toReference = new Matrix4f(worldToReference).mul(sub.getWorldPositionMatrix(1f));
            for (int i = 0; i < 8; i++) {
                corner.set(center.x + ((i & 1) == 0 ? -box.getXExtent() : box.getXExtent()),
                        center.y + ((i & 2) == 0 ? -box.getYExtent() : box.getYExtent()),
                        center.z + ((i & 4) == 0 ? -box.getZExtent() : box.getZExtent()));
                toReference.transformPosition(corner);
                if (!corner.isFinite()) continue;
                min.min(corner);
                max.max(corner);
            }
        }
        return min.isFinite() && max.isFinite() ? Bounds.between(min, max) : null;
    }
}
