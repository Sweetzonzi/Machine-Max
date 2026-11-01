package io.github.sweetzonzi.machine_max.common.mech.subsystem.seat;

import com.jme3.math.Transform;
import io.github.sweetzonzi.machine_max.common.mech.part.AbstractPart;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AdvancedConnector;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 座位几何顺序：车体参考系、座位排序与“下一个座位”。
 *
 * <p>这个类只读装配体拓扑与座位实时位姿，不写任何状态；服务端只做校验，从不调用这里的排序，
 * 因此两端即使因网络延迟得到不同的顺序也不影响换座结果——请求里携带的是具体座位，
 * 不是方向或序号。</p>
 *
 * <p>参考系：装配体里的子零件按“刚性连接”聚类，取含驾驶员座位（否则质量最大、排除轮子簇）
 * 的簇内质量最大的子零件作为参考。参考系只缓存参考子零件本身；原点与旋转随车辆行驶、转向
 * 每帧变化，因此不进任何缓存。车头轴按项目约定取该零件局部坐标系的 {@code -Z}
 * （与 {@code DestroyableObject#getFrontVector()}、地面控制器与投射物的前向一致）。</p>
 *
 * <p>线程：主线程。读取的位姿字段都是双端可用的同步值。</p>
 */
public final class SeatOrder {

    /** 排序坐标的量化步长，减少两端浮点误差导致的顺序翻转 */
    private static final double COORD_QUANTUM = 0.05;
    /**
     * 车头轴：模型与刚体的局部 {@code -Z}。项目内所有前向判定都取这个轴
     * （{@code DestroyableObject#getFrontVector()}、地面控制器的姿态遥测、投射物姿态），
     * 内容包按 -Z 建模即正面朝前，因此它不依赖座位位置，也不随座次变化。
     */
    private static final Vector3f FRONT_AXIS = new Vector3f(0f, 0f, -1f);

    private SeatOrder() {
    }

    /**
     * 一次布局计算的结果。
     *
     * @param reference 车体参考子零件，绘制时逐帧采样；装配体还没有子零件时为 null
     * @param seats     按几何顺序排好的座位，下标即显示编号减一
     */
    public record Layout(@Nullable SubPart reference, List<SeatSubsystem> seats) {

        /** 当前座位在顺序里的下标；不在其中时返回 -1 */
        public int indexOf(@Nullable SeatSubsystem seat) {
            return seat == null ? -1 : seats.indexOf(seat);
        }
    }

    /**
     * 列出装配体内的全部座位，不排序。遍历顺序由拓扑决定，只用于枚举。
     */
    public static List<SeatSubsystem> seats(VehicleCore vehicle) {
        List<SeatSubsystem> result = new ArrayList<>();
        for (AbstractPart<?> part : vehicle.partMap.values()) {
            for (SubPart sub : part.subParts.values()) {
                for (AbstractSubsystem subsystem : sub.getSubsystems().values()) {
                    if (subsystem instanceof SeatSubsystem seat) result.add(seat);
                }
            }
        }
        return result;
    }

    /**
     * 计算参考系与排序。
     *
     * @param vehicle 装配体
     */
    public static Layout layout(VehicleCore vehicle) {
        List<SeatSubsystem> all = seats(vehicle);
        SubPart ref = pickReference(vehicle);
        if (ref == null) return new Layout(null, List.of());
        return new Layout(ref, order(all, ref));
    }

    /**
     * 只算排序，不保留参考系。按键路径用这个，HUD 用 {@link #layout} 缓存参考系。
     */
    public static List<SeatSubsystem> order(VehicleCore vehicle) {
        return layout(vehicle).seats();
    }

    /**
     * 按几何顺序找下一个可用座位。
     *
     * @param vehicle 装配体
     * @param from    当前座位
     * @param forward true 顺向、false 逆向
     * @return 目标座位；没有其它可用座位时返回 null
     */
    @Nullable
    public static SeatSubsystem next(VehicleCore vehicle, SeatSubsystem from, boolean forward) {
        List<SeatSubsystem> order = order(vehicle);
        int index = order.indexOf(from);
        if (index < 0 || order.size() < 2) return null;
        int direction = forward ? 1 : -1;
        for (int step = 1; step < order.size(); step++) {
            SeatSubsystem candidate = order.get(Math.floorMod(index + step * direction, order.size()));
            if (candidate != from && available(candidate)) return candidate;
        }
        return null;
    }

    /**
     * 座位此刻是否可以被换入。
     *
     * <p>只读 {@code occupied}：它由服务端维护并同步，两端语义一致。
     * {@code passenger} 只有服务端有值，客户端读它会把所有座位当成空位。</p>
     */
    public static boolean available(SeatSubsystem seat) {
        return seat.isActive() && !seat.isDestroyed()
                && !seat.getSubPart().isDestroyed()
                && seat.getSubPart().getEntity() != null
                && !seat.isOccupied();
    }

    // ===== 参考系 =====

    /**
     * 刚性簇：只有 Advanced 一侧的连接点带关节配置，simple 侧的 {@code joint_attrs} 是无操作配置。
     *
     * <p>边必须从连接点关系建立：{@code partNet} 的节点是部件，部件内部子零件之间的连接由
     * {@code AbstractPart.autoAttachInternalConnectors} 直接连，从不进 {@code partNet}。
     * 只看 {@code partNet} 会让同一部件里的车身、炮塔、整排座椅退化成互不相连的孤立节点。</p>
     */
    private static SubPart pickReference(VehicleCore vehicle) {
        List<SubPart> subParts = new ArrayList<>();
        Map<SubPart, SubPart> parents = new IdentityHashMap<>();
        for (AbstractPart<?> part : vehicle.partMap.values()) {
            for (SubPart sub : part.subParts.values()) {
                subParts.add(sub);
                parents.put(sub, sub);
            }
        }
        // 装配体还没有子零件（创建/分裂的中间态）：没有参考系可用，调用方按“没有座位图”处理
        if (subParts.isEmpty()) return null;

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

        Map<SubPart, ClusterInfo> clusters = new HashMap<>();
        for (SubPart sub : subParts) {
            SubPart root = find(parents, sub);
            ClusterInfo info = clusters.computeIfAbsent(root, k -> new ClusterInfo());
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

    /** 簇内排序键：质量 → 竖直投影面积 → 子零件 id 升序 */
    private static boolean betterAsReference(SubPart candidate, SubPart current) {
        float candidateMass = candidate.getAttr().getMass();
        float currentMass = current.getAttr().getMass();
        if (Math.abs(candidateMass - currentMass) > 1e-4f) return candidateMass > currentMass;
        double candidateArea = verticalProjectedArea(candidate);
        double currentArea = verticalProjectedArea(current);
        if (Math.abs(candidateArea - currentArea) > 1e-6) return candidateArea > currentArea;
        return candidate.getId() < current.getId();
    }

    private static double verticalProjectedArea(SubPart sub) {
        return sub.projectedArea == null ? 0d : sub.projectedArea.y;
    }

    private static boolean hasDriverSeat(SubPart sub) {
        for (AbstractSubsystem subsystem : sub.getSubsystems().values()) {
            if (subsystem instanceof SeatSubsystem seat && isDriverName(seat.getName())) return true;
        }
        return false;
    }

    private static boolean isDriverName(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).contains("driver");
    }

    // ===== 排序 =====

    /**
     * 排序键 {@code (sy, sx, seatName, subPartId)} 升序：先上后下、先左后右，名字与宿主 id 做稳定 tie-break。
     * <p>用非插值的 locator 世界位姿，同一 tick 内取到稳定值；插值版本只用于标记的逐帧位置。
     * 坐标按水平面投影，坡度只压缩前后间距、不改变先后次序，所以同一台车的编号在整个行程里稳定。</p>
     */
    private static List<SeatSubsystem> order(List<SeatSubsystem> seats, SubPart ref) {
        if (seats.size() < 2) return List.copyOf(seats);
        Matrix4f refMatrix = ref.getWorldPositionMatrix(1f);
        Vector3f origin = refMatrix.getTranslation(new Vector3f());
        Vector3f front = refMatrix.transformDirection(new Vector3f(FRONT_AXIS));
        front.y = 0f;
        if (front.lengthSquared() < 1e-6f) front.set(0f, 0f, -1f);
        front.normalize();
        Vector3f right = new Vector3f(front).cross(0f, 1f, 0f);
        if (right.lengthSquared() < 1e-6f) right.set(1f, 0f, 0f);
        right.normalize();

        List<Keyed> keyed = new ArrayList<>(seats.size());
        for (SeatSubsystem seat : seats) {
            Transform locator = seat.getSeatPointWorldTransform();
            com.jme3.math.Vector3f world = locator == null ? new com.jme3.math.Vector3f() : locator.getTranslation();
            double dx = world.x - origin.x;
            double dz = world.z - origin.z;
            double sx = dx * right.x + dz * right.z;
            double sy = -(dx * front.x + dz * front.z);
            keyed.add(new Keyed(seat, quantize(sy), quantize(sx)));
        }
        keyed.sort(Comparator
                .comparingDouble((Keyed k) -> k.sy)
                .thenComparingDouble(k -> k.sx)
                .thenComparing(k -> k.seat.getName())
                .thenComparingInt(k -> k.seat.getSubPart().getId()));
        List<SeatSubsystem> result = new ArrayList<>(keyed.size());
        for (Keyed entry : keyed) result.add(entry.seat);
        return result;
    }

    private static double quantize(double value) {
        return Math.round(value / COORD_QUANTUM) * COORD_QUANTUM;
    }

    private record Keyed(SeatSubsystem seat, double sy, double sx) {
    }

    // ===== 刚性簇 =====

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
}
