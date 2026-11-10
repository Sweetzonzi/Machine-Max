package io.github.sweetzonzi.machine_max.util.geometry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 有序轮组的履带接触路径。铺设顺序是“主动轮 → 全部负重轮 → 顶轮 → 主动轮”，
 * 弧长零点取主动轮的首个接触处，{@link #sampleAt(double)} 在 {@code 0} 处落在主动轮
 * 包裹段的起点（两轮布局与凸包回退取折线自身的首点）。
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
    /** 水平自由跨段的最大中点垂度（米），也是外部包围盒可使用的形变上界。 */
    public static final double MAX_SAG_HEIGHT = 0.12;
    private static final double SAG_COEFFICIENT = 0.02;
    private static final double MAX_SAG_PROFILE_SLOPE = 16 / (3 * Math.sqrt(3));
    private static final double MIN_SAG_PROJECTION = 1e-6;
    private static final int SAG_STEPS = 32;
    private static final double WHEEL_CLEARANCE = 0.0005;
    private static final double SAG_TOLERANCE = 0.0002;

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

    /**
     * 基础闭环的语义段，区分轮间直线跨段与同一轮上的整段圆弧。
     *
     * @param start 起点
     * @param end 终点
     * @param direction 第一条基础折线边的单位方向
     * @param startDistance 起点在基础路径中的累计距离（米）
     * @param baseLength 基础路径按折线计的段长（米）
     * @param fromWheel 起点接触轮在输入成员表中的索引
     * @param toWheel 终点接触轮在输入成员表中的索引
     * @param arc 圆弧接触信息，直线跨段为 {@code null}
     * @param firstEdge 此段第一条基础折线边的索引
     * @param endEdge 此段最后一条基础折线边的索引加一
     */
    public record Segment(Vec2 start, Vec2 end, Vec2 direction, double startDistance, double baseLength,
                          int fromWheel, int toWheel, ArcContact arc, int firstEdge, int endEdge) {
        public boolean isStraight() {
            return arc == null;
        }
    }

    /** 求解器记录的段首折线顶点及轮索引，由 {@link #fromData} 换算成语义段。 */
    record SegmentStart(int pointIndex, int fromWheel, int toWheel, ArcContact arc) {
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
    private final List<Segment> segments;
    private final SagBuffer sag;

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
            boolean reversedFromInput,
            List<Segment> segments,
            SagBuffer sag
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
        this.segments = segments;
        this.sag = sag;
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
        return convexFallback(circles, false);
    }

    /** 凸包回退使用铺设绕向，供运行时保持节序方向。 */
    public static BeltPath2D convexFallback(List<Circle2D> circles, boolean clockwise) {
        if (circles == null || circles.size() < 2) {
            throw new IllegalArgumentException("at least 2 circles are required");
        }
        for (int i = 0; i < circles.size(); i++) {
            Circle2D circle = circles.get(i);
            if (circle == null || circle.radius() <= EPS) {
                throw new IllegalArgumentException("circle " + i + " radius must be > 0");
            }
        }
        List<Vec2> hull = new ArrayList<>(ConvexHull2D.hullOfCircles(circles, 128));
        if (clockwise) java.util.Collections.reverse(hull);
        if (hull.size() < 3) {
            throw new IllegalArgumentException("degenerate circle hull");
        }
        List<Vertex> vertices = new ArrayList<>(hull.size());
        for (Vec2 point : hull) {
            vertices.add(new Vertex(-1, VertexType.STRAIGHT, 0, point));
        }
        return fromData(circles, circles, vertices, hull, List.of(), List.of(), false, List.of());
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
                firstIndex != 0,
                List.of(
                        new SegmentStart(0, firstIndex, firstIndex, arcs.get(0)),
                        new SegmentStart(leftArc.size() - 1, firstIndex, 1 - firstIndex, null),
                        new SegmentStart(leftArc.size(), 1 - firstIndex, 1 - firstIndex, arcs.get(1)),
                        new SegmentStart(leftArc.size() + rightArc.size() - 1, 1 - firstIndex, firstIndex, null))
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
                false,
                List.of(new SegmentStart(0, outerIndex, outerIndex, arcs.getFirst()))
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

    /** 以同一基础折线建立累计弧长索引与语义段表。 */
    static BeltPath2D fromData(
            List<Circle2D> inputCircles,
            List<Circle2D> orderedCircles,
            List<Vertex> vertices,
            List<Vec2> points,
            List<ArcContact> arcs,
            List<PointContact> pointContacts,
            boolean reversed,
            List<SegmentStart> starts
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
                reversed,
                buildSegments(points, edges, starts, length),
                null
        );
    }

    private static List<Segment> buildSegments(List<Vec2> points, List<Edge> edges,
                                               List<SegmentStart> starts, double length) {
        // 段首以顶点索引表达。只允许末点与闭环首点重合，避免退化边让索引错位。
        if (starts.isEmpty() || starts.getFirst().pointIndex() != 0
                || (edges.size() != points.size()
                && !(edges.size() == points.size() - 1 && points.getFirst().distance(points.getLast()) <= EPS))) {
            return List.of();
        }
        List<Segment> result = new ArrayList<>(starts.size());
        for (int i = 0; i < starts.size(); i++) {
            SegmentStart source = starts.get(i);
            int first = source.pointIndex();
            int end = i + 1 == starts.size() ? edges.size() : starts.get(i + 1).pointIndex();
            if (first < 0 || end <= first || end > edges.size()) return List.of();
            Edge firstEdge = edges.get(first);
            double endDistance = end == edges.size() ? length : edges.get(end).startDistance();
            result.add(new Segment(firstEdge.start(), edges.get(end - 1).end(), firstEdge.direction(),
                    firstEdge.startDistance(), endDistance - firstEdge.startDistance(),
                    source.fromWheel(), source.toWheel(), source.arc(), first, end));
        }
        return List.copyOf(result);
    }

    /** 返回按基础闭环绕向排列的语义段，凸包回退时为空。 */
    public List<Segment> segments() {
        return segments;
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
        if (sag != null) {
            List<Vec2> result = new ArrayList<>(sag.count);
            for (int i = 0; i < sag.count; i++) result.add(new Vec2(sag.x[i], sag.y[i]));
            return List.copyOf(result);
        }
        return points;
    }

    public List<Edge> edges() {
        return sag == null ? edges : List.copyOf(buildEdges(points()));
    }

    public List<ArcContact> arcs() {
        return arcs;
    }

    /** 指定轮的包裹段；凸包折线按轮缘点恢复接触来源，轮被包络遮住时返回 null。 */
    public ArcContact wheelContact(int wheelIndex) {
        for (ArcContact arc : arcs) if (arc.wheelIndex() == wheelIndex) return arc;
        Circle2D wheel = inputCircles.get(wheelIndex);
        int bestStart = -1, bestCount = 0;
        for (int i = 0; i < points.size(); i++) {
            int count = 0;
            while (count < points.size()) {
                Vec2 a = points.get((i + count) % points.size());
                Vec2 b = points.get((i + count + 1) % points.size());
                if (Math.abs(a.distance(wheel.center()) - wheel.radius()) > 1e-7
                        || Math.abs(b.distance(wheel.center()) - wheel.radius()) > 1e-7) break;
                count++;
            }
            if (count > bestCount) { bestStart = i; bestCount = count; }
        }
        if (bestCount == 0) return null;
        List<Vec2> samples = new ArrayList<>();
        double sweep = 0;
        for (int i = 0; i <= bestCount; i++) {
            Vec2 point = points.get((bestStart + i) % points.size());
            if (!samples.isEmpty()) {
                Vec2 prev = samples.getLast().sub(wheel.center()), next = point.sub(wheel.center());
                sweep += Math.atan2(prev.cross(next), prev.dot(next));
            }
            samples.add(point);
        }
        return new ArcContact(wheelIndex, samples.getFirst().sub(wheel.center()).angle(),
                sweep, Math.abs(sweep) * wheel.radius(), samples);
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
        return linkCount(linkLength, 1);
    }

    /**
     * 按最小重复单元的节数取整：N = max(K, round(L / (linkLength × K)) × K)。
     *
     * <p>闭环要求节数是 K 的整数倍，否则第 N − 1 节与第 0 节落在同一个槽位、两个不同铸件
     * 在同一处相撞。先按单元长度求单元数再乘回 K，比先求节数再向上取整更接近标称节距。</p>
     *
     * @param linkLength 标称节长（米）
     * @param period     最小重复单元的节数 K；{@code <= 1} 时与 {@link #linkCount(double)} 一致
     */
    public int linkCount(double linkLength, int period) {
        if (!(linkLength > 0)) {
            throw new IllegalArgumentException("linkLength must be > 0");
        }
        long k = Math.max(1, period);
        long units = Math.max(1L, Math.round(length / (linkLength * k)));
        long count = Math.max(k, units * k);
        return (int) Math.min(Integer.MAX_VALUE, count);
    }

    /**
     * 自由直线跨段的目标中点垂度。跨段越长越松，达到上限后只随重力投影变化。
     *
     * @param spanLength 基础跨段长度（米）
     * @param downAcrossSpan 世界单位下方向在跨段法线上的有符号投影
     * @return 非负中点垂度（米），与重力平行的跨段取零
     */
    public static double freeSagHeight(double spanLength, double downAcrossSpan) {
        double projection = Math.abs(downAcrossSpan);
        return projection > MIN_SAG_PROJECTION
                ? Math.min(MAX_SAG_HEIGHT, SAG_COEFFICIENT * spanLength * spanLength) * projection : 0;
    }

    /**
     * 四次下垂曲线相对基础直线的最大斜率，用于界定外表面随切线转动的位移。
     *
     * @param spanLength 非退化基础跨段长度（米）
     * @param height 实际中点垂度（米）
     */
    public static double sagSlopeBound(double spanLength, double height) {
        // f(u) = 16u²(1−u)²，最大 |f'(u)| 为 16/(3√3)。
        return MAX_SAG_PROFILE_SLOPE * height / spanLength;
    }

    /** 按闭环绕向把单位切向旋转为单位外法线。 */
    public Vec2 outwardNormal(Vec2 tangent) {
        return signedArea >= 0 ? tangent.rightNormal() : tangent.leftNormal();
    }

    /** 取平面重力在跨段法线方向上的分量，保留投影后的幅度。 */
    private static Vec2 sagDirection(Segment segment, Vec2 down) {
        Vec2 normal = segment.direction().rightNormal();
        return normal.mul(normal.dot(down));
    }

    /**
     * 以输入轮盘限制单段中点垂度。基础跨段与轮盘相切时允许保持相切。
     *
     * @param segment 基础路径的直线跨段
     * @param direction 跨段法线方向上的重力投影，可非单位
     * @param height 候选中点垂度（米）
     * @return 满足余隙的中点垂度上界（米）
     */
    public double limitSag(Segment segment, Vec2 direction, double height) {
        if (!(height > 0) || !Double.isFinite(height) || !segment.isStraight()) return 0;
        double magnitude = direction.length();
        if (!(magnitude > MIN_SAG_PROJECTION) || !Double.isFinite(magnitude)) return 0;
        double dx = direction.x() / magnitude;
        double dy = direction.y() / magnitude;
        double length = segment.baseLength();
        // 换到以跨段为 X 轴、形变方向为 Y 轴的坐标系，位于背侧的轮盘只会离曲线更远。
        for (Circle2D wheel : inputCircles) {
            double x = wheel.x() - segment.start().x();
            double y = wheel.y() - segment.start().y();
            double across = x * dx + y * dy;
            if (across <= 0) continue;
            double along = x * segment.direction().x() + y * segment.direction().y();
            double nearest = along - Math.clamp(along, 0, length);
            double radius = Math.max(wheel.radius(),
                    Math.min(wheel.radius() + WHEEL_CLEARANCE, Math.hypot(nearest, across)));
            if (across >= radius + height || along + radius < 0 || along - radius > length) continue;
            if ((along <= EPS || along >= length - EPS) && across >= radius - EPS) {
                // 切点附近，轮盘边界和四次形变都按离端点距离的平方变化，可直接比较系数。
                height = Math.min(height, length * length / (32 * across));
            } else if (!clearsDisk(length, height, along, across, radius * radius, 0, 1, 0)) {
                // 候选垂度越大，扫过区域越大。二分保留满足余隙的一侧。
                double low = 0, high = height;
                while (high - low > SAG_TOLERANCE) {
                    double mid = (low + high) * 0.5;
                    if (clearsDisk(length, mid, along, across, radius * radius, 0, 1, 0)) low = mid;
                    else high = mid;
                }
                height = low;
            }
            if (height == 0) break;
        }
        return height;
    }

    /** 细分跨段参数区间，用扫过区域的外接矩形保守排除轮盘相交。 */
    private static boolean clearsDisk(double length, double height, double along, double across,
                                      double radiusSquared, double from, double to, int depth) {
        // 曲线只沿 Y 变形，区间内的最大高度出现在全跨段中点，或区间中离该中点最近的一端。
        double x = along - Math.clamp(along, length * from, length * to);
        double y = Math.max(0, across - height * sagProfile(Math.clamp(0.5, from, to)));
        if (x * x + y * y >= radiusSquared - 1e-12) return true;
        if (depth == 12) return false;
        double mid = (from + to) * 0.5;
        return clearsDisk(length, height, along, across, radiusSquared, from, mid, depth + 1)
                && clearsDisk(length, height, along, across, radiusSquared, mid, to, depth + 1);
    }

    /** 单位中点垂度的四次曲线，两个端点的位置与导数均为零。 */
    private static double sagProfile(double u) {
        double shape = 4 * u * (1 - u);
        return shape * shape;
    }

    /**
     * 由基础包络构建下垂路径，保持闭环零点、绕向与轮缘切点。整圈校验失败时返回基础包络。
     *
     * @param down 世界单位下方向在履带平面内的投影
     * @param heights 按 {@link #segments()} 顺序给出的中点垂度（米），限幅结果写回此数组
     * @param buffer 调用方独占的采样缓冲。返回的形变路径有效至该缓冲下次被复用
     */
    public BeltPath2D withSag(Vec2 down, double[] heights, SagBuffer buffer) {
        if (segments.isEmpty()) return this;
        boolean changed = false;
        for (int i = 0; i < segments.size(); i++) {
            Segment span = segments.get(i);
            if (!(heights[i] > 0) || !span.isStraight()) {
                heights[i] = 0;
                continue;
            }
            heights[i] = limitSag(span, sagDirection(span, down), heights[i]);
            changed |= heights[i] > 0;
        }
        if (!changed) return this;

        buffer.begin(segments.size(), edges.size() + segments.size() * SAG_STEPS + 1);
        for (int i = 0; i < segments.size(); i++) {
            Segment span = segments.get(i);
            buffer.segmentEdge[i] = buffer.count;
            double height = heights[i];
            if (height > 0) {
                Vec2 direction = sagDirection(span, down);
                double scale = height / direction.length();
                buffer.offsetX[i] = direction.x() * scale;
                buffer.offsetY[i] = direction.y() * scale;
                // 每段不重复写终点，下一段的首点或闭环补点承担终点索引。
                for (int j = 0; j < SAG_STEPS; j++) {
                    double u = (double) j / SAG_STEPS;
                    double sag = sagProfile(u);
                    buffer.add(Math.fma(span.end().x() - span.start().x(), u, span.start().x()) + sag * buffer.offsetX[i],
                            Math.fma(span.end().y() - span.start().y(), u, span.start().y()) + sag * buffer.offsetY[i]);
                }
            } else {
                buffer.offsetX[i] = 0;
                buffer.offsetY[i] = 0;
                for (int j = span.firstEdge(); j < span.endEdge(); j++) {
                    Vec2 point = edges.get(j).start();
                    buffer.add(point.x(), point.y());
                }
            }
            buffer.bounds(i, span.end());
        }
        // 轮盘余隙逐段限制，整圈再检查绕向、自交与轮心包围。
        buffer.segmentEdge[segments.size()] = buffer.count;
        double renderLength = buffer.finish();
        double area = buffer.area;
        if (!Double.isFinite(renderLength) || !Double.isFinite(area) || Math.signum(area) != Math.signum(signedArea)
                || buffer.selfIntersects(heights, segments.size())) {
            Arrays.fill(heights, 0);
            return this;
        }
        for (Circle2D wheel : inputCircles) {
            if (!buffer.contains(wheel.x(), wheel.y())) {
                Arrays.fill(heights, 0);
                return this;
            }
        }
        return new BeltPath2D(inputCircles, orderedCircles, vertices, points, edges, arcs,
                pointContacts, renderLength, area, buffer.convex, reversedFromInput, segments, buffer);
    }

    /** 用采样弦长反解段内参数，点位和切向使用对应的解析直线、四次曲线或圆弧。 */
    private Sample sampleSag(double distance) {
        // 先定位弦，再找所属语义段，弦内距离线性反解为段内参数。
        int index = Arrays.binarySearch(sag.distance, 0, sag.count, distance);
        if (index < 0) index = -index - 2;
        int segmentIndex = Arrays.binarySearch(sag.segmentEdge, 0, segments.size(), index);
        if (segmentIndex < 0) segmentIndex = -segmentIndex - 2;
        Segment segment = segments.get(segmentIndex);
        int first = sag.segmentEdge[segmentIndex];
        int end = sag.segmentEdge[segmentIndex + 1];
        double fraction = (distance - sag.distance[index]) / (sag.distance[index + 1] - sag.distance[index]);
        double u = (index - first + fraction) / (end - first);
        Vec2 point, tangent;
        if (segment.arc() != null) {
            ArcContact arc = segment.arc();
            double angle = arc.startAngle() + arc.sweep() * u;
            point = u == 0 ? segment.start() : inputCircles.get(arc.wheelIndex()).pointAt(angle);
            tangent = Vec2.fromAngle(angle, 1).leftNormal().mul(Math.signum(arc.sweep()));
        } else if (sag.offsetX[segmentIndex] != 0 || sag.offsetY[segmentIndex] != 0) {
            double profile = sagProfile(u);
            double derivative = 32 * u * (1 - u) * (1 - 2 * u);
            double dx = segment.end().x() - segment.start().x();
            double dy = segment.end().y() - segment.start().y();
            point = new Vec2(segment.start().x() + u * dx + profile * sag.offsetX[segmentIndex],
                    segment.start().y() + u * dy + profile * sag.offsetY[segmentIndex]);
            tangent = new Vec2(dx + derivative * sag.offsetX[segmentIndex],
                    dy + derivative * sag.offsetY[segmentIndex]).normalized();
        } else {
            point = new Vec2(Math.fma(sag.x[index + 1] - sag.x[index], fraction, sag.x[index]),
                    Math.fma(sag.y[index + 1] - sag.y[index], fraction, sag.y[index]));
            tangent = segment.direction();
        }
        return new Sample(point, tangent, outwardNormal(tangent), index, distance);
    }

    /** 将形变路径上的点还原到段内参数，再通过同一张采样弦长表取累计距离。 */
    private double sagDistanceAt(Vec2 point) {
        double best = Double.POSITIVE_INFINITY, result = 0;
        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            double u, x, y;
            ArcContact arc = segment.arc();
            if (arc == null) {
                // 形变与基础切向垂直，沿基础直线投影即可还原曲线参数。
                u = Math.clamp(point.sub(segment.start()).dot(segment.direction()) / segment.baseLength(), 0, 1);
                double profile = sagProfile(u);
                x = Math.fma(segment.end().x() - segment.start().x(), u, segment.start().x()) + profile * sag.offsetX[i];
                y = Math.fma(segment.end().y() - segment.start().y(), u, segment.start().y()) + profile * sag.offsetY[i];
            } else {
                Circle2D wheel = inputCircles.get(arc.wheelIndex());
                double angle = Math.atan2(point.y() - wheel.y(), point.x() - wheel.x());
                double swept = Math.signum(arc.sweep()) * (angle - arc.startAngle()) % (2 * Math.PI);
                if (swept < 0) swept += 2 * Math.PI;
                u = swept <= Math.abs(arc.sweep()) ? swept / Math.abs(arc.sweep())
                        : point.distance(segment.start()) <= point.distance(segment.end()) ? 0 : 1;
                double onArc = arc.startAngle() + u * arc.sweep();
                x = wheel.x() + wheel.radius() * Math.cos(onArc);
                y = wheel.y() + wheel.radius() * Math.sin(onArc);
            }
            if (u == 0 || u == 1) {
                Vec2 endPoint = u == 0 ? segment.start() : segment.end();
                x = endPoint.x();
                y = endPoint.y();
            }
            double dx = point.x() - x, dy = point.y() - y;
            double error = dx * dx + dy * dy;
            if (error < best) {
                best = error;
                int first = sag.segmentEdge[i], end = sag.segmentEdge[i + 1];
                double sample = first + u * (end - first);
                int chord = Math.min((int) sample, end - 1);
                result = Math.fma(sample - chord, sag.distance[chord + 1] - sag.distance[chord], sag.distance[chord]);
            }
        }
        return result;
    }

    /**
     * 形变路径的工作缓冲。采样坐标、段界与累计距离使用基本类型数组，
     * 同一调用方可以逐帧复用，路径与缓冲由同一线程持有。
     */
    public static final class SagBuffer {
        private double[] x = {}, y = {}, distance = {};
        private double[] offsetX = {}, offsetY = {}, minX = {}, minY = {}, maxX = {}, maxY = {};
        private int[] segmentEdge = {};
        private int count;
        private double area;
        private boolean convex;

        private void begin(int segments, int capacity) {
            if (x.length < capacity) {
                x = new double[capacity];
                y = new double[capacity];
                distance = new double[capacity];
            }
            if (offsetX.length < segments) {
                offsetX = new double[segments];
                offsetY = new double[segments];
                minX = new double[segments];
                minY = new double[segments];
                maxX = new double[segments];
                maxY = new double[segments];
                segmentEdge = new int[segments + 1];
            }
            count = 0;
        }

        private void add(double px, double py) {
            x[count] = px;
            y[count] = py;
            distance[count] = count == 0 ? 0
                    : distance[count - 1] + Math.hypot(px - x[count - 1], py - y[count - 1]);
            count++;
        }

        private void bounds(int segment, Vec2 end) {
            minX[segment] = maxX[segment] = end.x();
            minY[segment] = maxY[segment] = end.y();
            for (int i = segmentEdge[segment]; i < count; i++) {
                minX[segment] = Math.min(minX[segment], x[i]);
                maxX[segment] = Math.max(maxX[segment], x[i]);
                minY[segment] = Math.min(minY[segment], y[i]);
                maxY[segment] = Math.max(maxY[segment], y[i]);
            }
        }

        /** 补齐闭环最后一条弦，求周长、绕向与凸性。 */
        private double finish() {
            add(x[0], y[0]);
            count--;
            area = 0;
            int sign = 0;
            convex = true;
            for (int i = 0; i < count; i++) {
                area += x[i] * y[i + 1] - x[i + 1] * y[i];
                int previous = i == 0 ? count - 1 : i - 1;
                double turn = cross(x[i] - x[previous], y[i] - y[previous],
                        x[i + 1] - x[i], y[i + 1] - y[i]);
                if (Math.abs(turn) > EPS) {
                    int next = turn > 0 ? 1 : -1;
                    convex &= sign == 0 || sign == next;
                    sign = next;
                }
            }
            area *= 0.5;
            return distance[count];
        }

        /** 检查涉及形变段的相交，先按段包围盒剔除，再检查非相邻弦。 */
        private boolean selfIntersects(double[] heights, int segments) {
            for (int a = 0; a < segments; a++) {
                if (heights[a] <= 0) continue;
                for (int b = 0; b < segments; b++) {
                    if (a == b || (b < a && heights[b] > 0)
                            || maxX[a] < minX[b] || maxX[b] < minX[a]
                            || maxY[a] < minY[b] || maxY[b] < minY[a]) continue;
                    for (int i = segmentEdge[a]; i < segmentEdge[a + 1]; i++) {
                        for (int j = segmentEdge[b]; j < segmentEdge[b + 1]; j++) {
                            int gap = Math.abs(i - j);
                            if (gap > 1 && gap < count - 1 && intersects(i, j)) return true;
                        }
                    }
                }
            }
            return false;
        }

        private boolean intersects(int i, int j) {
            if (Math.max(x[i], x[i + 1]) < Math.min(x[j], x[j + 1])
                    || Math.max(x[j], x[j + 1]) < Math.min(x[i], x[i + 1])
                    || Math.max(y[i], y[i + 1]) < Math.min(y[j], y[j + 1])
                    || Math.max(y[j], y[j + 1]) < Math.min(y[i], y[i + 1])) return false;
            double ax = x[i + 1] - x[i], ay = y[i + 1] - y[i];
            double bx = x[j + 1] - x[j], by = y[j + 1] - y[j];
            double dx = x[j] - x[i], dy = y[j] - y[i];
            return cross(ax, ay, dx, dy) * cross(ax, ay, dx + bx, dy + by) <= 0
                    && cross(bx, by, -dx, -dy) * cross(bx, by, ax - dx, ay - dy) <= 0;
        }

        private boolean contains(double px, double py) {
            boolean inside = false;
            for (int i = 0; i < count; i++) {
                if ((y[i] > py) != (y[i + 1] > py)
                        && px < x[i] + (x[i + 1] - x[i]) * (py - y[i]) / (y[i + 1] - y[i])) {
                    inside = !inside;
                }
            }
            return inside;
        }

        private static double cross(double ax, double ay, double bx, double by) {
            return ax * by - ay * bx;
        }
    }

    /** 按累计距离采样，超出周长的距离沿闭环回绕。 */
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

        if (sag != null) return sampleSag(u);

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
        return new Sample(edge.pointAt(t), tangent, outwardNormal(tangent), lo, u);
    }

    /** 路径点的累计距离。基础路径按折线投影，形变路径以段内参数查弦长表。 */
    public double distanceAt(Vec2 point) {
        if (sag != null) return sagDistanceAt(point);
        double best = Double.POSITIVE_INFINITY, distance = 0;
        for (Edge edge : edges) {
            double along = Math.max(0, Math.min(edge.length(), point.sub(edge.start()).dot(edge.direction())));
            double error = point.distance(edge.start().add(edge.direction().mul(along)));
            if (error < best) {
                best = error;
                distance = edge.startDistance() + along;
            }
        }
        return distance;
    }

    /**
     * 射线法。边界上的点可能返回 false；需要边界判定时用 {@link #distanceToBoundary(Vec2)}。
     */
    public boolean containsPoint(Vec2 point) {
        if (sag != null) return sag.contains(point.x(), point.y());
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
        for (Edge edge : edges()) {
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
