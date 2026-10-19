package io.github.sweetzonzi.machine_max.util.geometry;

import java.util.ArrayList;
import java.util.List;

/**
 * 有序轮组的履带接触路径。铺设顺序是“主动轮 → 全部负重轮 → 顶轮 → 主动轮”。
 * {@link #build(List, double, boolean)} 接收连接拓扑的固定绕向，各跨段由
 * {@link OrderedBeltSolver#solve} 在全轮组公切线图中求解。
 *
 * <p>每个铺设轮的外侧轮缘点是必经约束；实际进出线决定该处是点接触还是圆弧。
 * 任意跨段都能增加支撑轮，同一物理轮可以在负重轮链点接触、在另一跨段圆弧接触。
 * {@link #vertices()} 和 {@link #orderedCircles()} 表达动态接触序列，
 * {@link #inputCircles()} 保留原成员表。全共线轮组可直接构建；3 轮及以上要求圆盘不重叠。
 * 2 轮允许重叠或包含，路径取两圆外轮廓。</p>
 *
 * <p>纯几何、双精度，无 Minecraft / JME / JOML 依赖。便利入口
 * {@link #build(List)} 以二维 Y 向上解释铺设约定，2 轮规范化为逆时针；
 * 运行时使用带显式绕向的入口。{@link #sampleAt(double)} 根据闭环有向面积给出外法线。</p>
 */
public final class BeltPath2D {
    private static final double EPS = 1e-9;
    private static final double ARC_STEP = Math.PI / 180.0;

    public enum VertexType {
        CONVEX,
        CONCAVE,
        STRAIGHT
    }

    /**
     * 相邻轮心重合等无法建立必经接触方向的布局。
     *
     * <p>与其它非法输入区分开，调用方只有对这种异常才应该回退到
     * {@link #convexFallback(List)}；支撑点落进邻轮等几何失败不应被静默抹平。</p>
     */
    public static final class DegenerateLayoutException extends IllegalArgumentException {
        public DegenerateLayoutException(String message) {
            super(message);
        }
    }

    /**
     * 动态接触节点。support 是点接触位置或圆弧中点，turn 是路径转角或圆弧扫角。
     * wheelIndex 对应原成员表，同一成员可以在接触序列中多次出现。
     */
    public record Vertex(int wheelIndex, VertexType type, double turn, Vec2 support) {
    }

    /**
     * 凸角上的圆弧接触。{@code sweep} 有符号：逆时针为正，顺时针为负。
     */
    public record ArcContact(
            int wheelIndex,
            double startAngle,
            double sweep,
            double contactLength,
            List<Vec2> contactPoints
    ) {
        public ArcContact {
            contactPoints = List.copyOf(contactPoints);
        }
    }

    /**
     * 凹角 / 共线轮上的单点接触。
     */
    public record PointContact(int wheelIndex, Vec2 point) {
    }

    /**
     * 包络折线的一段。
     */
    public record Edge(Vec2 start, Vec2 end, Vec2 direction, double length, double startDistance) {
        public Vec2 pointAt(double t) {
            return start.add(direction.mul(length * t));
        }
    }

    /**
     * 按弧长采样的结果。
     */
    public record Sample(Vec2 point, Vec2 tangent, Vec2 normal, int edgeIndex, double arcLength) {
    }

private record Tangent(Vec2 tA, Vec2 tB) {
    }

private final List<Circle2D> inputCircles;
    private final List<Circle2D> orderedCircles;
    private final List<Vertex> vertices;
    private final List<Vec2> points;
    private final List<Edge> edges;
    private final List<ArcContact> arcs;
    private final List<PointContact> pointContacts;
    private final double length;
    private final double signedArea;
    private final boolean convex;
    private final boolean reversedFromInput;

    private BeltPath2D(
            List<Circle2D> inputCircles,
            List<Circle2D> orderedCircles,
            List<Vertex> vertices,
            List<Vec2> points,
            List<Edge> edges,
            List<ArcContact> arcs,
            List<PointContact> pointContacts,
            double length,
            double signedArea,
            boolean convex,
            boolean reversedFromInput
    ) {
        this.inputCircles = inputCircles;
        this.orderedCircles = orderedCircles;
        this.vertices = vertices;
        this.points = points;
        this.edges = edges;
        this.arcs = arcs;
        this.pointContacts = pointContacts;
        this.length = length;
        this.signedArea = signedArea;
        this.convex = convex;
        this.reversedFromInput = reversedFromInput;
    }

    /**
     * @param circles 锚定轮组：首个为主动轮，顺序为“主动轮 → 下行程 → 上行程”；
     *                2 个轮子走凸包/stadium，3 个及以上按轮序走非凸带路
     */
    public static BeltPath2D build(List<Circle2D> circles) {
        return build(circles, EPS);
    }

    /**
     * @param circles 锚定轮组：至少 2 个、半径 > 0；3 个及以上时圆盘不重叠
     * @param turnEpsilon 凸/凹/共线判定阈值（弧度）
     */
    public static BeltPath2D build(List<Circle2D> circles, double turnEpsilon) {
        validateInput(circles, turnEpsilon);
        if (circles.size() == 2) return buildTwo(circles, 1, true);
        return OrderedBeltSolver.solve(circles, turnEpsilon, determineClockwise(circles) ? -1 : 1);
    }

    /**
     * @param clockwise 创建时确定的顺时针铺设绕向，随接触拓扑保存。
     */
    public static BeltPath2D build(List<Circle2D> circles, double turnEpsilon, boolean clockwise) {
        validateInput(circles, turnEpsilon);
        int sign = clockwise ? -1 : 1;
        if (circles.size() == 2) return buildTwo(circles, sign, false);
        return OrderedBeltSolver.solve(circles, turnEpsilon, sign);
    }

    private static void validateInput(List<Circle2D> circles, double turnEpsilon) {
        if (circles == null || circles.size() < 2) {
            throw new IllegalArgumentException("at least 2 circles are required");
        }
        if (!(turnEpsilon > 0)) {
            throw new IllegalArgumentException("turnEpsilon must be > 0");
        }
        for (int i = 0; i < circles.size(); i++) {
            Circle2D circle = circles.get(i);
            if (circle == null || circle.radius() <= EPS) {
                throw new IllegalArgumentException("circle " + i + " radius must be > 0");
            }
        }
        if (circles.size() > 2 && hasOverlappingWheels(circles)) {
            throw new IllegalArgumentException("ordered wheel disks overlap");
        }
    }

    /**
     * 是否存在相互重叠（圆心距 &lt; 半径和）的轮对。悬挂压缩时轮子可以重叠；
     * 这种布局的有序点接触模型可能无解，调用方可以据此决定是否回退凸包。
     */
    public static boolean hasOverlappingWheels(List<Circle2D> circles) {
        for (int i = 0; i < circles.size(); i++) {
            for (int j = i + 1; j < circles.size(); j++) {
                Circle2D a = circles.get(i);
                Circle2D b = circles.get(j);
                if (a.center().distance(b.center()) < a.radius() + b.radius() - 1e-6) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 由“下行程优先”的锚定顺序确定整条带路的绕行方向。
     *
     * <p>闭合边（最后一个成员 → 主动轮）属于上行程，不参与判断；在其余边里取中点
     * 最低的一条作为下行程代表，要求它的外法线朝下（主动轮局部 -Y）。这样得到的
     * 符号是便利入口的二维 Y 向上约定。轮心多边形的绕向不作为判据：
     * 在“共线三轮回中间抬起”的布局里，它与带路绕向恰好相反。</p>
     *
     * @return 是否顺时针绕行
     */
    private static boolean determineClockwise(List<Circle2D> circles) {
        int n = circles.size();
        int bestEdge = -1;
        double bestMidY = Double.POSITIVE_INFINITY;
        for (int i = 0; i < n - 1; i++) {
            Vec2 a = circles.get(i).center();
            Vec2 b = circles.get(i + 1).center();
            Vec2 direction = b.sub(a);
            if (direction.lengthSquared() <= EPS * EPS) {
                throw new DegenerateLayoutException("adjacent circle centers must not coincide");
            }
            Vec2 right = direction.rightNormal();
            if (Math.abs(right.y()) <= 1e-6) {
                // 竖直边没有“上/下外法线”可言，换下一条候选边。
                continue;
            }
            double midY = (a.y() + b.y()) * 0.5;
            if (midY < bestMidY - 1e-9) {
                bestMidY = midY;
                bestEdge = i;
            }
        }
        if (bestEdge >= 0) {
            Vec2 direction = circles.get(bestEdge + 1).center()
                    .sub(circles.get(bestEdge).center());
            return direction.rightNormal().y() >= 0;
        }

        return false;
    }

    /**
     * 重叠圆盘等无法建立有序接触路径时的凸包回退。
     *
     * <p>返回的是圆盘集合的凸包折线——没有圆弧、没有凹口，只保证
     * {@link #length()} / {@link #linkCount(double)} / {@link #sampleAt(double)}
     * 可用。调用方应记录告警，并在轮位姿恢复后重新尝试 {@link #build}。</p>
     *
     * <p>调用条件为 {@link #hasOverlappingWheels(List)}，或
     * {@link DegenerateLayoutException}。非重叠轮组的几何失败应交给调用方报告，
     * 凸包不能保留负重轮链的凹口。</p>
     *
     * <p>至少需要 2 个半径大于 0 的圆。</p>
     */
    public static BeltPath2D convexFallback(List<Circle2D> circles) {
        if (circles == null || circles.size() < 2) {
            throw new IllegalArgumentException("at least 2 circles are required");
        }
        for (int i = 0; i < circles.size(); i++) {
            Circle2D circle = circles.get(i);
            if (circle == null || circle.radius() <= EPS) {
                throw new IllegalArgumentException("circle " + i + " radius must be > 0");
            }
        }
        List<Vec2> hull = ConvexHull2D.hullOfCircles(circles, 128);
        if (hull.size() < 3) {
            throw new IllegalArgumentException("degenerate circle hull");
        }
        List<Vertex> vertices = new ArrayList<>(hull.size());
        for (Vec2 point : hull) {
            vertices.add(new Vertex(-1, VertexType.STRAIGHT, 0, point));
        }
        return fromData(circles, circles, vertices, hull, List.of(), List.of(), false);
    }

    /**
     * 2 轮布局：两个外离/重叠圆盘的外部凸包 = 两条外公切线 + 两段圆弧。
     * 两圆互相包含时退化为外圆整圆。
     */
    private static BeltPath2D buildTwo(List<Circle2D> input, int sign, boolean normalizeInput) {
        Circle2D a = input.get(0);
        Circle2D b = input.get(1);
        double distance = a.center().distance(b.center());
        if (distance <= Math.abs(a.radius() - b.radius()) + EPS) {
            return buildContainedTwo(input, sign);
        }

        // 显式铺设拓扑保持主动轮首位；便利入口规范化成左圆起点、逆时针。
        int firstIndex = !normalizeInput || a.x() < b.x() || (a.x() == b.x() && a.y() < b.y()) ? 0 : 1;
        Circle2D left = input.get(firstIndex);
        Circle2D right = input.get(1 - firstIndex);

        Tangent lower = externalTangent(left, right, sign);
        Tangent upper = externalTangent(left, right, -sign);
        if (lower == null || upper == null) {
            return buildContainedTwo(input, sign);
        }

        double leftStart = angleOf(upper.tA(), left);
        double leftEnd = angleOf(lower.tA(), left);
        double leftSweep = sign * positiveSweep(sign * leftStart, sign * leftEnd);
        double rightStart = angleOf(lower.tB(), right);
        double rightEnd = angleOf(upper.tB(), right);
        double rightSweep = sign * positiveSweep(sign * rightStart, sign * rightEnd);

        List<Vec2> leftArc = sampleArc(left, leftStart, leftSweep, lower.tA());
        List<Vec2> rightArc = sampleArc(right, rightStart, rightSweep, upper.tB());

        List<Vec2> points = new ArrayList<>();
        for (Vec2 point : leftArc) {
            addPoint(points, point);
        }
        addPoint(points, rightArc.get(0));
        for (Vec2 point : rightArc) {
            addPoint(points, point);
        }
        addPoint(points, leftArc.get(0));

        List<ArcContact> arcs = List.of(
                new ArcContact(
                        firstIndex,
                        leftStart,
                        leftSweep,
                        Math.abs(leftSweep) * left.radius(),
                        leftArc
                ),
                new ArcContact(
                        1 - firstIndex,
                        rightStart,
                        rightSweep,
                        Math.abs(rightSweep) * right.radius(),
                        rightArc
                )
        );
        List<Vertex> vertices = List.of(
                new Vertex(firstIndex, VertexType.CONVEX, leftSweep, leftArc.get(0)),
                new Vertex(1 - firstIndex, VertexType.CONVEX, rightSweep, rightArc.get(0))
        );

        return fromData(
                input,
                List.of(left, right),
                vertices,
                points,
                arcs,
                List.of(),
                firstIndex != 0
        );
    }

    private static BeltPath2D buildContainedTwo(List<Circle2D> input, int sign) {
        Circle2D a = input.get(0);
        Circle2D b = input.get(1);
        int outerIndex = a.radius() >= b.radius() ? 0 : 1;
        int innerIndex = 1 - outerIndex;
        Circle2D outer = input.get(outerIndex);
        Circle2D inner = input.get(innerIndex);

        List<Vec2> fullArc = sampleArc(outer, 0, sign * Math.PI * 2, outer.pointAt(0));
        List<Vec2> points = new ArrayList<>(fullArc.subList(0, fullArc.size() - 1));
        List<ArcContact> arcs = List.of(new ArcContact(
                outerIndex,
                0,
                sign * Math.PI * 2,
                Math.PI * 2 * outer.radius(),
                fullArc
        ));
        List<Vertex> vertices = List.of(
                new Vertex(outerIndex, VertexType.CONVEX, sign * Math.PI * 2, outer.pointAt(0)),
                new Vertex(innerIndex, VertexType.STRAIGHT, 0, inner.center())
        );

        return fromData(
                input,
                List.of(outer, inner),
                vertices,
                points,
                arcs,
                List.of(),
                false
        );
    }

    private static List<Vec2> sampleArc(Circle2D circle, double start, double sweep, Vec2 endPoint) {
        int samples = Math.abs(sweep) <= EPS
                ? 1
                : Math.max(2, (int) Math.ceil(Math.abs(sweep) / ARC_STEP));
        List<Vec2> points = new ArrayList<>(samples + 1);
        for (int i = 0; i <= samples; i++) {
            double angle = start + sweep * ((double) i / samples);
            points.add(circle.pointAt(angle));
        }
        points.set(points.size() - 1, endPoint);
        return points;
    }

    private static double angleOf(Vec2 point, Circle2D circle) {
        return Math.atan2(point.y() - circle.y(), point.x() - circle.x());
    }

    private static double positiveSweep(double start, double end) {
        double sweep = (end - start) % (Math.PI * 2);
        if (sweep < 0) {
            sweep += Math.PI * 2;
        }
        return sweep;
    }

    static BeltPath2D fromData(
            List<Circle2D> inputCircles,
            List<Circle2D> orderedCircles,
            List<Vertex> vertices,
            List<Vec2> points,
            List<ArcContact> arcs,
            List<PointContact> pointContacts,
            boolean reversed
    ) {
        List<Edge> edges = buildEdges(points);
        double length = edges.isEmpty() ? 0 : edges.get(edges.size() - 1).startDistance()
                + edges.get(edges.size() - 1).length();
        double area = signedArea(points);
        boolean isConvex = isConvex(points);

        return new BeltPath2D(
                List.copyOf(inputCircles),
                List.copyOf(orderedCircles),
                List.copyOf(vertices),
                List.copyOf(points),
                List.copyOf(edges),
                List.copyOf(arcs),
                List.copyOf(pointContacts),
                length,
                area,
                isConvex,
                reversed
        );
    }

    public List<Circle2D> inputCircles() {
        return inputCircles;
    }

    public List<Circle2D> orderedCircles() {
        return orderedCircles;
    }

    public List<Vertex> vertices() {
        return vertices;
    }

    public List<Vec2> points() {
        return points;
    }

    public List<Edge> edges() {
        return edges;
    }

    public List<ArcContact> arcs() {
        return arcs;
    }

    public List<PointContact> pointContacts() {
        return pointContacts;
    }

    public double length() {
        return length;
    }

    public double signedArea() {
        return signedArea;
    }

    public boolean isConvex() {
        return convex;
    }

    public boolean reversedFromInput() {
        return reversedFromInput;
    }

    /**
     * 与 TrackAttr 的节数公式一致：N = max(1, round(L / linkLength))。
     */
    public int linkCount(double linkLength) {
        if (!(linkLength > 0)) {
            throw new IllegalArgumentException("linkLength must be > 0");
        }
        return Math.max(1, (int) Math.round(length / linkLength));
    }

    public Sample sampleAt(double arcLength) {
        if (!Double.isFinite(arcLength)) {
            throw new IllegalArgumentException("arcLength must be finite");
        }
        if (edges.isEmpty() || length <= EPS) {
            Vec2 point = points.isEmpty() ? Vec2.ZERO : points.get(0);
            return new Sample(point, Vec2.UNIT_X, new Vec2(0, -1), 0, 0);
        }
        double u = arcLength % length;
        if (u < 0) {
            u += length;
        }

        int lo = 0;
        int hi = edges.size() - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (edges.get(mid).startDistance() <= u) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        Edge edge = edges.get(lo);
        double t = Math.min(1, Math.max(0, (u - edge.startDistance()) / edge.length()));
        Vec2 tangent = edge.direction();
        Vec2 normal = signedArea >= 0
                ? new Vec2(tangent.y(), -tangent.x())
                : new Vec2(-tangent.y(), tangent.x());
        return new Sample(edge.pointAt(t), tangent, normal, lo, u);
    }

    /**
     * 射线法。边界上的点可能返回 false；需要边界判定时用 {@link #distanceToBoundary(Vec2)}。
     */
    public boolean containsPoint(Vec2 point) {
        boolean inside = false;
        for (int i = 0, j = points.size() - 1; i < points.size(); j = i++) {
            Vec2 a = points.get(i);
            Vec2 b = points.get(j);
            if (((a.y() > point.y()) != (b.y() > point.y()))
                    && (point.x() < (b.x() - a.x()) * (point.y() - a.y())
                    / (b.y() - a.y()) + a.x())) {
                inside = !inside;
            }
        }
        return inside;
    }

    public double distanceToBoundary(Vec2 point) {
        double best = Double.POSITIVE_INFINITY;
        for (Edge edge : edges) {
            best = Math.min(best, distancePointSegment(point, edge.start(), edge.end()));
        }
        return best;
    }

    private static double distancePointSegment(Vec2 point, Vec2 a, Vec2 b) {
        Vec2 ab = b.sub(a);
        double lengthSq = ab.lengthSquared();
        if (lengthSq <= EPS * EPS) {
            return point.distance(a);
        }
        double t = point.sub(a).dot(ab) / lengthSq;
        t = Math.max(0, Math.min(1, t));
        return point.distance(a.add(ab.mul(t)));
    }

    private static List<Edge> buildEdges(List<Vec2> points) {
        List<Edge> result = new ArrayList<>(points.size());
        if (points.size() < 2) {
            return result;
        }
        double start = 0;
        for (int i = 0; i < points.size(); i++) {
            Vec2 a = points.get(i);
            Vec2 b = points.get((i + 1) % points.size());
            Vec2 direction = b.sub(a).normalized();
            double length = a.distance(b);
            if (length <= EPS) {
                continue;
            }
            result.add(new Edge(a, b, direction, length, start));
            start += length;
        }
        return result;
    }

    private static void addPoint(List<Vec2> points, Vec2 point) {
        if (points.isEmpty() || points.get(points.size() - 1).distance(point) > 1e-10) {
            points.add(point);
        }
    }

    private static double signedArea(List<Vec2> points) {
        double sum = 0;
        for (int i = 0; i < points.size(); i++) {
            Vec2 a = points.get(i);
            Vec2 b = points.get((i + 1) % points.size());
            sum += a.x() * b.y() - b.x() * a.y();
        }
        return sum / 2.0;
    }

    private static boolean isConvex(List<Vec2> points) {
        if (points.size() < 3) {
            return false;
        }
        int sign = 0;
        for (int i = 0; i < points.size(); i++) {
            Vec2 a = points.get(i);
            Vec2 b = points.get((i + 1) % points.size());
            Vec2 c = points.get((i + 2) % points.size());
            double cross = b.sub(a).cross(c.sub(b));
            if (Math.abs(cross) <= EPS) {
                continue;
            }
            int current = cross > 0 ? 1 : -1;
            if (sign == 0) {
                sign = current;
            } else if (sign != current) {
                return false;
            }
        }
        return true;
    }

    private static Tangent externalTangent(Circle2D a, Circle2D b, int side) {
        Vec2 delta = b.center().sub(a.center());
        double distance = delta.length();
        if (distance <= EPS) {
            return null;
        }
        Vec2 u = delta.div(distance);
        Vec2 right = u.rightNormal();
        double alpha = (a.radius() - b.radius()) / distance;
        if (Math.abs(alpha) >= 1 - 1e-12) {
            return null;
        }
        double beta = Math.sqrt(Math.max(0, 1 - alpha * alpha));
        Vec2 normal = u.mul(alpha).add(right.mul(side * beta));
        Vec2 tA = a.center().add(normal.mul(a.radius()));
        Vec2 tB = b.center().add(normal.mul(b.radius()));
        return new Tangent(tA, tB);
    }

}
