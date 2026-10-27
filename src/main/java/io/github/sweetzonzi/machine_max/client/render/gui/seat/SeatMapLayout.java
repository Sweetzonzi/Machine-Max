package io.github.sweetzonzi.machine_max.client.render.gui.seat;

import com.jme3.bounding.BoundingBox;
import com.jme3.math.Quaternion;
import io.github.sweetzonzi.machine_max.common.mech.part.AbstractPart;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.seat.SeatOrder;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 座位图的布局快照：参考系、排好序的座位、标签与适配缩放。
 *
 * <p>快照只存<strong>不随帧变化</strong>的东西：参考子零件引用、局部前向轴、适配缩放与包围盒中心、
 * 座位列表与标签。<strong>世界坐标、原点、偏航都不进快照</strong>——绘制时按同一个 {@code partialTick}
 * 从参考子零件的插值世界矩阵现取。缓存住会出现两种错位：车开走时地图按旧原点平移，而座位点用新的
 * 世界坐标，标记整体漂出面板；转向后车头不再朝上，模型转了而标记没转。</p>
 *
 * <p>重算时机：进入座位时算一次，结构事件置脏后下一个 tick 重算，装配体 uuid 变了无条件重建。
 * 耐久、毁坏、炮塔转动、悬挂行程、占用变化都不触发重算——占用只刷新标记颜色。</p>
 */
public final class SeatMapLayout {

    /** 面板尺寸（GUI 像素） */
    public static final int PANEL_W = 156;
    public static final int PANEL_H = 128;
    /** 地图区域内边距 */
    public static final int MAP_PAD = 8;
    /** 标题栏高度（顶部强调线 + 一行文字） */
    public static final int HEADER_H = 14;
    /** 页脚高度（图例 + 键位提示） */
    public static final int FOOTER_H = 26;

    /**
     * 一次布局计算的结果。
     *
     * @param assemblyId 装配体 uuid，变了就无条件重建
     * @param reference  车体参考系，绘制时逐帧采样
     * @param seats      按几何顺序排好的座位，下标即编号减一
     * @param subParts   参与绘制的子零件，绘制时直接用实时对象
     * @param fit        世界单位 → GUI 像素的缩放
     * @param centerSx   包围盒中心在参考系横轴上的坐标
     * @param centerSy   包围盒中心在参考系纵轴上的坐标（屏幕向下为正）
     */
    public record Snapshot(UUID assemblyId, SeatOrder.Reference reference, List<SeatSubsystem> seats,
                           List<SubPart> subParts,
                           float fit, float centerSx, float centerSy) {

        /** 当前座位在顺序里的下标；不在其中时返回 -1 */
        public int indexOf(@Nullable SeatSubsystem seat) {
            return seat == null ? -1 : seats.indexOf(seat);
        }
    }

    @Nullable
    private static Snapshot cached;
    private static boolean dirty = true;

    private SeatMapLayout() {
    }

    /** 结构事件回调里只置脏：一次结构变化只重算一次，避免在子系统握手与物理激活中途递归进来 */
    public static void markDirty() {
        dirty = true;
    }

    /** 离开座位、换车时清空 */
    public static void reset() {
        cached = null;
        dirty = true;
    }

    /** 地图区域尺寸（面板内可画车体的矩形） */
    public static int mapWidth() {
        return PANEL_W - MAP_PAD * 2;
    }

    public static int mapHeight() {
        return PANEL_H - HEADER_H - FOOTER_H - MAP_PAD * 2;
    }

    /**
     * 构造“世界坐标 → GUI 像素”的矩阵，车体与标记共用。
     *
     * <p>顺序固定为「面板中心 → 居中偏移（像素，缩放之后）→ 适配缩放 → 俯视 {@code X(-90)}
     * → 偏航 {@code Y(180-heading)} → 平移 -原点」。俯视必须先于偏航：反过来写会把世界高度混进横轴，
     * {@code heading = 90°} 时车头退化到屏幕中心。居中偏移必须在缩放之前乘进链里——它表达的是
     * 屏幕像素位移，若写在旋转之后，平移会作用在世界 X/Y 上，与车体朝向无关的偏移就对不上。
     * 验收判据：任意朝向都满足 {@code front → (0,-1)}、{@code right → (+1,0)}（GUI 坐标 y 向下）。</p>
     *
     * @param fit        世界单位 → 像素的缩放
     * @param centerSx   包围盒中心在参考系横轴上的坐标
     * @param centerSy   包围盒中心在参考系纵轴上的坐标
     * @param mapCenterX 地图区域中心的 GUI x
     * @param mapCenterY 地图区域中心的 GUI y
     * @param origin     参考子零件的插值世界位置
     * @param headingDeg 车头偏航角，{@code atan2(front.x, front.z)} 的角度值
     */
    public static Matrix4f mapMatrix(float fit, float centerSx, float centerSy,
                                     float mapCenterX, float mapCenterY,
                                     Vector3f origin, float headingDeg) {
        return new Matrix4f()
                .translate(mapCenterX, mapCenterY, 0f)
                // 居中偏移按像素计，必须叠在缩放之后（写在链的更左侧）
                .translate(-centerSx * fit, -centerSy * fit, 0f)
                // 深度取反：GUI 的 +z 朝向观察者，而俯视要求“高处的零件更近”，
                // 否则画出来的是仰视图（车顶被车身底面挡住）
                .scale(fit, fit, -fit)
                .rotateX((float) Math.toRadians(-90f))
                .rotateY((float) Math.toRadians(180f - headingDeg))
                .translate(-origin.x, -origin.y, -origin.z);
    }

    /**
     * 取当前快照，必要时重建。
     *
     * @param current 当前座位，用于车头朝向的回退判定
     */
    @Nullable
    public static Snapshot snapshot(SeatSubsystem current) {
        VehicleCore vehicle = vehicleOf(current);
        if (vehicle == null) {
            cached = null;
            dirty = true;
            return null;
        }
        UUID assemblyId = vehicle.getAssemblyId();
        if (cached == null && !dirty) return null;
        if (cached == null || dirty || !assemblyId.equals(cached.assemblyId())) {
            cached = build(vehicle, current);
            dirty = false;
        }
        return cached;
    }

    @Nullable
    private static VehicleCore vehicleOf(SeatSubsystem seat) {
        if (seat == null || seat.getSubPart() == null) return null;
        return seat.getSubPart().getPart().assembly instanceof VehicleCore vehicle ? vehicle : null;
    }

    private static Snapshot build(VehicleCore vehicle, SeatSubsystem current) {
        SeatOrder.Layout layout = SeatOrder.layout(vehicle, current);
        if (layout.reference() == null) return null;
        List<SeatSubsystem> seats = layout.seats();
        List<SubPart> subParts = new ArrayList<>();
        for (AbstractPart<?> part : vehicle.partMap.values()) subParts.addAll(part.subParts.values());
        Bounds bounds = measure(vehicle, layout.reference());
        float fit = Math.min(mapWidth() / Math.max(bounds.width, 0.5f),
                mapHeight() / Math.max(bounds.height, 0.5f));
        return new Snapshot(vehicle.getAssemblyId(), layout.reference(), List.copyOf(seats),
                List.copyOf(subParts), fit, bounds.centerSx, bounds.centerSy);
    }

    /** 车体在参考系横/纵轴上的包围盒中心与尺寸 */
    private record Bounds(float centerSx, float centerSy, float width, float height) {
    }

    /**
     * 用各子零件碰撞形状的轴对齐包围盒量出车体足迹。
     *
     * <p>投影按<strong>车体坐标</strong>做（偏航归零），所以所得缩放对行驶与转向都不变；
     * 炮塔转动只改变角点在车体坐标里的位置，需要时跟着结构事件一起重算。</p>
     */
    private static Bounds measure(VehicleCore vehicle, SeatOrder.Reference reference) {
        Matrix4f refMatrix = reference.ref().getWorldPositionMatrix(1f);
        Vector3f origin = refMatrix.getTranslation(new Vector3f());
        Vector3f front = refMatrix.transformDirection(new Vector3f(reference.frontLocal()));
        front.y = 0f;
        if (front.lengthSquared() < 1e-6f) front.set(0f, 0f, -1f);
        front.normalize();
        Vector3f right = new Vector3f(front).cross(0f, 1f, 0f);
        if (right.lengthSquared() < 1e-6f) right.set(1f, 0f, 0f);
        right.normalize();

        float minX = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        Vector3f corner = new Vector3f();
        for (AbstractPart<?> part : vehicle.partMap.values()) {
            for (SubPart sub : part.subParts.values()) {
                BoundingBox box;
                try {
                    box = sub.collisionShape.boundingBox(new com.jme3.math.Vector3f(), Quaternion.IDENTITY, null);
                } catch (Exception e) {
                    continue;
                }
                if (box == null) continue;
                Matrix4f bodyToWorld = sub.getWorldPositionMatrix(1f);
                float ex = box.getXExtent();
                float ey = box.getYExtent();
                float ez = box.getZExtent();
                for (int sx = -1; sx <= 1; sx += 2) {
                    for (int sy = -1; sy <= 1; sy += 2) {
                        for (int sz = -1; sz <= 1; sz += 2) {
                            corner.set(sx * ex, sy * ey, sz * ez);
                            bodyToWorld.transformPosition(corner);
                            float dx = corner.x - origin.x;
                            float dz = corner.z - origin.z;
                            float px = dx * right.x + dz * right.z;
                            float py = -(dx * front.x + dz * front.z);
                            if (px < minX) minX = px;
                            if (px > maxX) maxX = px;
                            if (py < minY) minY = py;
                            if (py > maxY) maxY = py;
                        }
                    }
                }
            }
        }
        if (!Float.isFinite(minX) || !Float.isFinite(minY)) {
            return new Bounds(0f, 0f, 1f, 1f);
        }
        return new Bounds((minX + maxX) * 0.5f, (minY + maxY) * 0.5f,
                Math.max(maxX - minX, 0.1f), Math.max(maxY - minY, 0.1f));
    }
}
