package io.github.sweetzonzi.machine_max.client.render.gui.seat;

import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.bounding.BoundingBox;
import com.jme3.math.Quaternion;
import io.github.sweetzonzi.machine_max.common.mech.part.AbstractPart;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AdvancedConnector;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
            return projection(worldToReference, x, y, width, height, rotation);
        }

        public Matrix4f projection(Matrix4f worldToReference, float x, float y, float width, float height,
                                   Matrix4fc rotation) {
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

    /** 参考件局部空间 → 实际相机空间；Camera.rotation 已包含 NeoForge 的横滚。 */
    public static Matrix4f cameraRelativeRotation(Matrix4fc referenceToWorld, Quaternionfc cameraToWorld) {
        return new Matrix4f().rotation(cameraToWorld.conjugate(new Quaternionf()))
                .rotate(referenceToWorld.getNormalizedRotation(new Quaternionf()));
    }

    /**
     * 只随相机方位转动的局部观察角，单位度；相机 yaw 使用 Minecraft 的南 0° 约定。
     * 相机右轴落在参考件上轴时，局部方位无定义，保持该元素上一帧的角度。
     */
    public static float relativeHeadingYaw(Matrix4fc referenceToWorld, float cameraYaw, float fallback) {
        Vector3f right = new Quaternionf().rotationY((float) Math.PI - (float) Math.toRadians(cameraYaw))
                .transform(new Vector3f(1, 0, 0));
        referenceToWorld.getNormalizedRotation(new Quaternionf()).conjugate().transform(right);
        if (right.x * right.x + right.z * right.z < 1e-8f) return fallback;
        return (float) Math.toDegrees(Math.atan2(right.z, right.x));
    }

    public static void invalidate(VehicleCore vehicle) {
        CACHE.remove(vehicle);
    }

    public static void retain(Set<VehicleCore> vehicles) {
        CACHE.keySet().retainAll(vehicles);
    }

    public static Snapshot snapshot(VehicleCore vehicle) {
        return CACHE.computeIfAbsent(vehicle, SeatMapLayout::build);
    }

    /**
     * 结构快照：参考件只决定座位标记投影用的局部坐标系，不参与座次排序。
     * <p>座次取自 {@link VehicleCore#getAllSeats()}（部件 UUID 升序 → 子零件声明序 → 子系统声明序），
     * 与几何位姿、名字、会话计数器无关。</p>
     */
    private static Snapshot build(VehicleCore vehicle) {
        List<SubPart> subParts = collectSubParts(vehicle);
        SubPart reference = pickReference(subParts, vehicle);
        Bounds bounds = reference == null ? null : measure(subParts, reference);
        return new Snapshot(reference, List.copyOf(vehicle.getAllSeats()), List.copyOf(subParts), bounds);
    }

    /** 按部件 UUID 升序枚举子零件，让参考件选择与会话无关。 */
    private static List<SubPart> collectSubParts(VehicleCore vehicle) {
        List<UUID> partIds = new ArrayList<>(vehicle.partMap.keySet());
        Collections.sort(partIds);
        List<SubPart> subParts = new ArrayList<>();
        for (UUID partId : partIds) {
            AbstractPart<?> part = vehicle.partMap.get(partId);
            if (part == null) continue;
            subParts.addAll(part.subParts.values());
        }
        return subParts;
    }

    // ===== 参考件选择（只决定局部坐标系，不参与座次排序） =====

    /**
     * 刚性簇：只有 Advanced 一侧的连接点带关节配置，simple 侧的 {@code joint_attrs} 是无操作配置。
     *
     * <p>边必须从连接点关系建立：{@code partNet} 的节点是部件，部件内部子零件之间的连接由
     * {@code AbstractPart.autoAttachInternalConnectors} 直接连，从不进 {@code partNet}。
     * 只看 {@code partNet} 会让同一部件里的车身、炮塔、整排座椅退化成互不相连的孤立节点。</p>
     *
     * <p>簇表用 {@link LinkedHashMap}：并列时取先枚举者（枚举序由部件 UUID 决定），因此同一台车
     * 在同一次载入里结果稳定，不需要 {@code SubPart#id} 这类会话计数器做 tie-break。</p>
     */
    private static SubPart pickReference(List<SubPart> subParts, VehicleCore vehicle) {
        if (subParts.isEmpty()) return null;
        Map<SubPart, SubPart> parents = new IdentityHashMap<>();
        for (SubPart sub : subParts) parents.put(sub, sub);

        for (SubPart sub : subParts) {
            for (AbstractConnector connector : sub.connectors.values()) {
                if (!rigid(connector)) continue;
                AbstractConnector other = connector.attachedConnector;
                if (other == null || other.subPart == null) continue;
                // 只处理本装配体内的连接；另一侧可能属于别的装配体（跨载具对接的中间态）
                if (other.subPart.part.assembly != vehicle) continue;
                if (!parents.containsKey(other.subPart)) continue;
                union(parents, sub, other.subPart);
            }
        }

        Map<SubPart, ClusterInfo> clusters = new LinkedHashMap<>();
        for (SubPart sub : subParts) {
            SubPart root = find(parents, sub);
            ClusterInfo info = clusters.computeIfAbsent(root, key -> new ClusterInfo());
            info.mass += Math.max(0f, sub.getAttr().getMass());
            if (sub.getMaxWheelRadius() > 0f) info.hasWheel = true;
            if (hasDriverSeat(sub)) info.hasDriverSeat = true;
        }

        SubPart bestRoot = null;
        ClusterInfo best = null;
        for (Map.Entry<SubPart, ClusterInfo> entry : clusters.entrySet()) {
            if (entry.getValue().hasDriverSeat) {
                bestRoot = entry.getKey();
                best = entry.getValue();
                break;
            }
        }
        if (bestRoot == null) {
            for (Map.Entry<SubPart, ClusterInfo> entry : clusters.entrySet()) {
                ClusterInfo info = entry.getValue();
                if (info.hasWheel) continue;
                if (best == null || info.mass > best.mass) {
                    bestRoot = entry.getKey();
                    best = info;
                }
            }
        }
        if (bestRoot == null) {
            // 只剩轮子簇（例如拆到只剩底盘车轮）时不做排除，直接取质量最大者
            for (Map.Entry<SubPart, ClusterInfo> entry : clusters.entrySet()) {
                if (best == null || entry.getValue().mass > best.mass) {
                    bestRoot = entry.getKey();
                    best = entry.getValue();
                }
            }
        }

        SubPart ref = null;
        for (SubPart sub : subParts) {
            if (find(parents, sub) != bestRoot) continue;
            if (ref == null || betterAsReference(sub, ref)) ref = sub;
        }
        return ref;
    }

    /** 簇内排序键：质量 → 竖直投影面积；两者都并列时取先枚举者。 */
    private static boolean betterAsReference(SubPart candidate, SubPart current) {
        float candidateMass = candidate.getAttr().getMass();
        float currentMass = current.getAttr().getMass();
        if (Math.abs(candidateMass - currentMass) > 1e-4f) return candidateMass > currentMass;
        double candidateArea = verticalProjectedArea(candidate);
        double currentArea = verticalProjectedArea(current);
        if (Math.abs(candidateArea - currentArea) > 1e-6) return candidateArea > currentArea;
        return false;
    }

    private static double verticalProjectedArea(SubPart sub) {
        return sub.projectedArea == null ? 0d : sub.projectedArea.y;
    }

    private static boolean hasDriverSeat(SubPart sub) {
        for (AbstractSubsystem subsystem : sub.ownSubsystems()) {
            if (subsystem instanceof SeatSubsystem seat && isDriverName(seat.getName())) return true;
        }
        return false;
    }

    private static boolean isDriverName(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).contains("driver");
    }

    /** 刚性边：Advanced 连接点没有配置任何关节轴；simple 侧的 joint_attrs 不参与 */
    private static boolean rigid(AbstractConnector connector) {
        return connector instanceof AdvancedConnector && connector.attr.getJointAttrs().isEmpty();
    }

    private static SubPart find(Map<SubPart, SubPart> parents, SubPart sub) {
        SubPart root = sub;
        while (parents.get(root) != root) root = parents.get(root);
        SubPart cursor = sub;
        while (parents.get(cursor) != root) {
            SubPart next = parents.get(cursor);
            parents.put(cursor, root);
            cursor = next;
        }
        return root;
    }

    private static void union(Map<SubPart, SubPart> parents, SubPart a, SubPart b) {
        SubPart rootA = find(parents, a);
        SubPart rootB = find(parents, b);
        if (rootA != rootB) parents.put(rootA, rootB);
    }

    private static final class ClusterInfo {
        float mass;
        boolean hasWheel;
        boolean hasDriverSeat;
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
