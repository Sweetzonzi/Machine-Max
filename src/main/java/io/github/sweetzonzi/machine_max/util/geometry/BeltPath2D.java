package io.github.sweetzonzi.machine_max.util.geometry;

import java.util.ArrayList;
import java.util.List;

/**
 * 有序轮组的履带包络（非凸 belt path）。
 *
 * <p>输入是 <b>有锚点的有序</b>圆列表：第一个圆是主动轮，方向是履带绕行方向。
 * 3 个及以上时按这个顺序直接构建，输出方向与输入一致（可能是顺时针，也可能是
 * 逆时针）；调用方可用 {@link #signedArea()} 的符号判断，{@link #sampleAt(double)}
 * 会按符号返回正确的外法线。</p>
 *
 * <ul>
 *   <li><b>凸角</b>（主动轮、诱导轮、比两侧低的负重轮）：相邻轮外侧外公切线 + 圆弧；</li>
 *   <li><b>凹角 / 共线</b>（被障碍抬起的负重轮、平地负重轮）：单点接触，接触长度为 0；</li>
 *   <li><b>2 个轮</b>：直接走两圆外部凸包（stadium），互相包含时退化为外圆整圆。</li>
 * </ul>
 *
 * <p>这不是对圆盘集合取凸包。抬起的负重轮会被凸包整个丢掉，而实际履带会贴住它；
 * 本类按锚定轮序保留这个凹口。锚定顺序的契约是“主动轮 → 下行程全部轮（含被顶起的）
 * → 上行程轮 → 回主动轮”，因此同一组轮心按不同顺序输入会得到不同的包络。</p>
 *
 * <p>被顶起的下行程轮如果高过回程闭合边，本类会把同一轮追加到序列末尾再接触一次：
 * 下行程是轮底的点接触，回程是轮顶的圆弧。该轮的 {@link Vertex#wheelIndex()} 会在
 * {@link #vertices()} 中出现两次，{@link #inputCircles()} 仍保持每个成员一次。</p>
 *
 * <p>纯几何、双精度、无 Minecraft / JME / JOML 依赖。{@link #reversedFromInput()}
 * 只对 2 轮布局有意义：2 轮路径会被规范化为逆时针并可能反转输入；3 个及以上不做
 * 输入反转，该标志恒为 false。</p>
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
     * 轮心全共线、相邻轮心重合等无法定义闭合带路的退化布局。
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
     * 轮序顶点分类。{@code support} 是外侧角平分线与圆的交点，凹角/共线时就是接触点。
     *
     * <p>被顶起的下行程轮如果高过回程闭合边，回程会追加同一轮的第二个顶点，
     * 因此同一个 {@code wheelIndex} 可能在 {@link #vertices()} 中出现两次。</p>
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

    private record InterimEdge(Vec2 start, Vec2 end, Vec2 direction) {
    }

    private record Tangent(Vec2 tA, Vec2 tB) {
    }

    private record TangentPoint(Vec2 point, double angle, Vec2 direction) {
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
     * @param circles 锚定轮组：至少 2 个、半径 > 0；3 个及以上时轮心不共线
     * @param turnEpsilon 凸/凹/共线判定阈值（弧度）
     */
    public static BeltPath2D build(List<Circle2D> circles, double turnEpsilon) {
        if (circles == null || circles.size() < 2) {
            throw new IllegalArgumentException("at least 2 circles are required");
        }
        if (!(turnEpsilon > 0)) {
            throw new IllegalArgumentException("turnEpsilon must be > 0");
        }
        for (int i = 0; i < circles.size(); i++) {
            Circle2D circle = circles.get(i);
            if (circle.radius() <= EPS) {
                throw new IllegalArgumentException("circle " + i + " radius must be > 0");
            }
        }
        if (circles.size() == 2) {
            return buildTwo(circles);
        }
        return buildOrdered(circles, circles, turnEpsilon, true);
    }

    /**
     * 锚定顺序构建。{@code orderedInput} 可能比 {@code originalInput} 多一个重复轮——
     * 被顶起的下行程轮如果高过闭合边，会在这里追加一个上行程接触。
     */
    private static BeltPath2D buildOrdered(List<Circle2D> orderedInput, List<Circle2D> originalInput,
                                           double turnEpsilon, boolean allowExpansion) {
        List<Circle2D> ordered = new ArrayList<>(orderedInput);
        int n = ordered.size();
        List<Circle2D> unique = uniqueCircles(originalInput);
        double centerArea = signedArea(unique.stream().map(Circle2D::center).toList());
        if (Math.abs(centerArea) <= EPS) {
            throw new DegenerateLayoutException("wheel centers are collinear; belt path is undefined");
        }
        Vec2 centroid = centroid(unique);
        int traversalSign = determineTraversalSign(ordered);

        List<Vertex> vertices = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Circle2D prev = ordered.get((i + n - 1) % n);
            Circle2D cur = ordered.get(i);
            Circle2D next = ordered.get((i + 1) % n);
            Vec2 dPrev = cur.center().sub(prev.center()).normalized();
            Vec2 dNext = next.center().sub(cur.center()).normalized();
            if (dPrev.lengthSquared() <= EPS * EPS || dNext.lengthSquared() <= EPS * EPS) {
                throw new DegenerateLayoutException("adjacent circle centers must not coincide");
            }
            double turn = Math.atan2(dPrev.cross(dNext), dPrev.dot(dNext));
            Vec2 nPrev = outwardNormal(dPrev, traversalSign);
            Vec2 nNext = outwardNormal(dNext, traversalSign);
            Vec2 outward = nPrev.add(nNext).normalized();
            if (outward.lengthSquared() <= EPS * EPS) {
                outward = nNext;
            }
            Vec2 support = cur.center().add(outward.mul(cur.radius()));
            VertexType type = traversalSign * turn > turnEpsilon
                    ? VertexType.CONVEX
                    : (traversalSign * turn < -turnEpsilon ? VertexType.CONCAVE : VertexType.STRAIGHT);
            // 主动轮、闭合前的最后一个上行程轮，以及轮心折线接近回折的顶点都是端帽：
            // 带子必须绕过它们。近共线布局里，端帽的转角接近 ±π，微小位姿噪声会让
            // 符号翻转；用转角绝对值判定，结果不依赖绕行方向。
            if (i == 0 || i == n - 1 || Math.abs(turn) > Math.PI * 0.75) {
                type = VertexType.CONVEX;
            }
            int wheelIndex = originalInput.indexOf(cur);
            if (wheelIndex < 0) {
                wheelIndex = i;
            }
            vertices.add(new Vertex(wheelIndex, type, turn, support));
        }

        List<InterimEdge> interim = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Circle2D a = ordered.get(i);
            Circle2D b = ordered.get((i + 1) % n);
            Vertex va = vertices.get(i);
            Vertex vb = vertices.get((i + 1) % n);
            Vec2 centerDir = b.center().sub(a.center()).normalized();
            Vec2 outward = outwardNormal(centerDir, traversalSign);
            Vec2 start;
            Vec2 end;

            if (va.type() == VertexType.CONVEX && vb.type() == VertexType.CONVEX) {
                Tangent tangent = externalTangent(a, b, traversalSign);
                if (tangent == null) {
                    throw new IllegalArgumentException("no external tangent between convex wheels " + i
                            + " and " + ((i + 1) % n));
                }
                start = tangent.tA();
                end = tangent.tB();
            } else if (va.type() != VertexType.CONVEX && vb.type() == VertexType.CONVEX) {
                TangentPoint tangent = tangentFromPoint(va.support(), b, outward);
                if (tangent == null) {
                    throw new IllegalArgumentException("support point of wheel " + i
                            + " lies inside wheel " + ((i + 1) % n));
                }
                start = va.support();
                end = tangent.point();
            } else if (va.type() == VertexType.CONVEX && vb.type() != VertexType.CONVEX) {
                TangentPoint tangent = tangentFromPoint(vb.support(), a, outward);
                if (tangent == null) {
                    throw new IllegalArgumentException("support point of wheel " + ((i + 1) % n)
                            + " lies inside wheel " + i);
                }
                start = tangent.point();
                end = vb.support();
            } else {
                start = va.support();
                end = vb.support();
            }

            Vec2 direction = end.sub(start).normalized();
            if (direction.lengthSquared() <= EPS * EPS) {
                throw new IllegalArgumentException("zero-length belt edge at wheel " + i);
            }
            interim.add(new InterimEdge(start, end, direction));
        }

        // 非相邻轮不能被直线段穿过。闭合边（最后成员 -> 主动轮）穿过一个下行程轮时，
        // 说明该轮高过回程边，需要在回程上再接触一次：把同一轮追加到序列末尾，
        // 让它以上行程凸角再出现一次。其它边穿轮属于无法表示的布局，直接报错。
        int expandWheel = -1;
        outer:
        for (int i = 0; i < n; i++) {
            InterimEdge edge = interim.get(i);
            for (int j = 0; j < n; j++) {
                if (j == i || j == (i + 1) % n) {
                    continue;
                }
                Circle2D wheel = ordered.get(j);
                // 同一物理轮可能在序列里出现两次（下行程点接触 + 回程圆弧）。
                // 它已经是边的端点，不能当成“非相邻轮被穿过”。
                if (sameCircle(wheel, ordered.get(i)) || sameCircle(wheel, ordered.get((i + 1) % n))) {
                    continue;
                }
                double clearance = distancePointSegment(wheel.center(), edge.start(), edge.end())
                        - wheel.radius();
                if (clearance < -1e-4) {
                    if (allowExpansion && i == n - 1 && j > 0 && j < n - 1) {
                        expandWheel = j;
                        break outer;
                    }
                    throw new IllegalArgumentException("belt edge " + i + " cuts wheel " + j
                            + " (clearance " + clearance + ")");
                }
            }
        }
        if (expandWheel >= 0) {
            List<Circle2D> expanded = new ArrayList<>(ordered);
            expanded.add(ordered.get(expandWheel));
            return buildOrdered(expanded, originalInput, turnEpsilon, false);
        }

        List<Vec2> points = new ArrayList<>();
        List<ArcContact> arcs = new ArrayList<>();
        List<PointContact> pointContacts = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            Circle2D circle = ordered.get(i);
            Vertex vertex = vertices.get(i);
            InterimEdge prev = interim.get((i + n - 1) % n);
            InterimEdge next = interim.get(i);
            Vec2 tIn = prev.end();
            Vec2 tOut = next.start();

            if (vertex.type() != VertexType.CONVEX) {
                addPoint(points, vertex.support());
                pointContacts.add(new PointContact(vertex.wheelIndex(), vertex.support()));
                addPoint(points, next.end());
                continue;
            }

            // 圆弧必须绕过轮子的外侧。两条边的外法线求平均只能给出“短弧”一侧；
            // 端帽处带子可能包超过半圈，此时真正的外侧是平均方向的反方向。
            Vec2 nPrev = outwardNormal(prev.direction(), traversalSign);
            Vec2 nNext = outwardNormal(next.direction(), traversalSign);
            Vec2 bisector = nPrev.add(nNext).normalized();
            if (bisector.lengthSquared() <= EPS * EPS) {
                bisector = nNext;
            }
            Vec2 away = circle.center().sub(centroid);
            Vec2 reference = away.lengthSquared() > EPS * EPS && bisector.dot(away) < 0
                    ? bisector.negate()
                    : bisector;

            double arcStart = Math.atan2(tIn.y() - circle.y(), tIn.x() - circle.x());
            double arcEnd = Math.atan2(tOut.y() - circle.y(), tOut.x() - circle.x());
            double ccwSweep = normalizePositive(arcEnd - arcStart);
            double cwSweep = ccwSweep - Math.PI * 2;
            double referenceAngle = Math.atan2(reference.y(), reference.x());
            double ccwDistance = angularDistance(arcStart + ccwSweep * 0.5, referenceAngle);
            double cwDistance = angularDistance(arcStart + cwSweep * 0.5, referenceAngle);
            double sweep = ccwDistance <= cwDistance ? ccwSweep : cwSweep;
            if (Math.abs(sweep) <= EPS) {
                addPoint(points, tIn);
                arcs.add(new ArcContact(vertex.wheelIndex(), arcStart, 0, 0, List.of(tIn)));
                addPoint(points, next.end());
                continue;
            }

            int arcSamples = Math.max(2, (int) Math.ceil(Math.abs(sweep) / ARC_STEP));
            List<Vec2> arcPoints = new ArrayList<>(arcSamples + 1);
            for (int k = 0; k <= arcSamples; k++) {
                double angle = arcStart + sweep * ((double) k / arcSamples);
                arcPoints.add(new Vec2(
                        circle.x() + Math.cos(angle) * circle.radius(),
                        circle.y() + Math.sin(angle) * circle.radius()
                ));
            }
            // 用几何终点收口，避免浮点误差在拼接处留下缝。
            arcPoints.set(arcPoints.size() - 1, tOut);
            for (Vec2 point : arcPoints) {
                addPoint(points, point);
            }
            arcs.add(new ArcContact(
                    vertex.wheelIndex(),
                    arcStart,
                    sweep,
                    Math.abs(sweep) * circle.radius(),
                    arcPoints
            ));
            addPoint(points, next.end());
        }

        return fromData(originalInput, ordered, vertices, points, arcs, pointContacts, false);
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

    private static boolean sameCircle(Circle2D a, Circle2D b) {
        return a.center().distance(b.center()) <= EPS
                && Math.abs(a.radius() - b.radius()) <= EPS;
    }

    private static List<Circle2D> uniqueCircles(List<Circle2D> circles) {
        List<Circle2D> unique = new ArrayList<>(circles.size());
        for (Circle2D circle : circles) {
            boolean duplicate = false;
            for (Circle2D existing : unique) {
                if (existing.center().distance(circle.center()) <= EPS
                        && Math.abs(existing.radius() - circle.radius()) <= EPS) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                unique.add(circle);
            }
        }
        return unique;
    }

    private static Vec2 centroid(List<Circle2D> circles) {
        double x = 0;
        double y = 0;
        for (Circle2D circle : circles) {
            x += circle.x();
            y += circle.y();
        }
        return new Vec2(x / circles.size(), y / circles.size());
    }

    /**
     * 由“下行程优先”的锚定顺序确定整条带路的绕行方向。
     *
     * <p>闭合边（最后一个成员 → 主动轮）属于上行程，不参与判断；在其余边里取中点
     * 最低的一条作为下行程代表，要求它的外法线朝下（主动轮局部 -Y）。这样得到的
     * 符号用于把“轮心折线的左/右转”翻译成带路的凸/凹。轮心多边形的绕向不作为判据：
     * 在“共线三轮回中间抬起”的布局里，它与带路绕向恰好相反。</p>
     *
     * @return +1 表示带路逆时针，-1 表示顺时针
     */
    private static int determineTraversalSign(List<Circle2D> circles) {
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
            return direction.rightNormal().y() < 0 ? 1 : -1;
        }

        double centerArea = signedArea(circles.stream().map(Circle2D::center).toList());
        if (Math.abs(centerArea) <= EPS) {
            throw new DegenerateLayoutException("wheel centers are collinear; belt path is undefined");
        }
        return centerArea > 0 ? 1 : -1;
    }

    private static Vec2 outwardNormal(Vec2 direction, int traversalSign) {
        Vec2 right = direction.rightNormal();
        return traversalSign >= 0 ? right : right.negate();
    }

    private static double normalizePositive(double angle) {
        double value = angle % (Math.PI * 2);
        if (value < 0) {
            value += Math.PI * 2;
        }
        return value;
    }

    private static double angularDistance(double a, double b) {
        double diff = normalizePositive(a - b);
        return Math.min(diff, Math.PI * 2 - diff);
    }

    /**
     * 全共线等无法定义闭合带路的退化布局的凸包回退。
     *
     * <p>返回的是圆盘集合的凸包折线——没有圆弧、没有凹口，只保证
     * {@link #length()} / {@link #linkCount(double)} / {@link #sampleAt(double)}
     * 可用。调用方应记录告警，并在轮位姿恢复后重新尝试 {@link #build}。</p>
     *
     * <p>只应在 {@link #build} 抛 {@link DegenerateLayoutException}，或
     * {@link #hasOverlappingWheels(List)} 为 true 时调用。支撑点落进邻轮、直线段
     * 穿过非相邻轮等几何失败在无重叠时不属于退化布局，回退会抹掉抬轮凹口。</p>
     *
     * <p>与 {@link #build} 不同，本方法不要求轮心不共线，但同样要求至少 2 个圆、
     * 半径 > 0。</p>
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
    private static BeltPath2D buildTwo(List<Circle2D> input) {
        Circle2D a = input.get(0);
        Circle2D b = input.get(1);
        double distance = a.center().distance(b.center());
        if (distance <= Math.abs(a.radius() - b.radius()) + EPS) {
            return buildContainedTwo(input);
        }

        // 统一成左圆 -> 右圆，使输出为逆时针。
        int firstIndex = (a.x() < b.x() || (a.x() == b.x() && a.y() < b.y())) ? 0 : 1;
        Circle2D left = input.get(firstIndex);
        Circle2D right = input.get(1 - firstIndex);

        Tangent lower = externalTangent(left, right, +1);
        Tangent upper = externalTangent(left, right, -1);
        if (lower == null || upper == null) {
            return buildContainedTwo(input);
        }

        double leftStart = angleOf(upper.tA(), left);
        double leftEnd = angleOf(lower.tA(), left);
        double leftSweep = positiveSweep(leftStart, leftEnd);
        double rightStart = angleOf(lower.tB(), right);
        double rightEnd = angleOf(upper.tB(), right);
        double rightSweep = positiveSweep(rightStart, rightEnd);

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

    private static BeltPath2D buildContainedTwo(List<Circle2D> input) {
        Circle2D a = input.get(0);
        Circle2D b = input.get(1);
        int outerIndex = a.radius() >= b.radius() ? 0 : 1;
        int innerIndex = 1 - outerIndex;
        Circle2D outer = input.get(outerIndex);
        Circle2D inner = input.get(innerIndex);

        List<Vec2> fullArc = sampleArc(outer, 0, Math.PI * 2, outer.pointAt(0));
        List<Vec2> points = new ArrayList<>(fullArc.subList(0, fullArc.size() - 1));
        List<ArcContact> arcs = List.of(new ArcContact(
                outerIndex,
                0,
                Math.PI * 2,
                Math.PI * 2 * outer.radius(),
                fullArc
        ));
        List<Vertex> vertices = List.of(
                new Vertex(outerIndex, VertexType.CONVEX, Math.PI * 2, outer.pointAt(0)),
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

    private static BeltPath2D fromData(
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

    /**
     * 从点 p 到圆 c 的切线。outward 用来在两条切线里选外侧那条。
     */
    private static TangentPoint tangentFromPoint(Vec2 p, Circle2D c, Vec2 outward) {
        Vec2 delta = c.center().sub(p);
        double distance = delta.length();
        if (distance <= c.radius() + EPS) {
            return null;
        }
        // 基点必须是 C->P；切线点角度 = 基点 ± acos(r/d)。
        double base = Math.atan2(p.y() - c.y(), p.x() - c.x());
        double alpha = Math.acos(Math.min(1, c.radius() / distance));
        TangentPoint best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int sign = -1; sign <= 1; sign += 2) {
            double angle = base + sign * alpha;
            Vec2 point = c.pointAt(angle);
            double score = point.sub(c.center()).dot(outward);
            if (score > bestScore) {
                bestScore = score;
                best = new TangentPoint(point, angle, point.sub(p).normalized());
            }
        }
        return best;
    }
}
