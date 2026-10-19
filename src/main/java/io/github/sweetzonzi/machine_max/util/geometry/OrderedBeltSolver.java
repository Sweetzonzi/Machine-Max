package io.github.sweetzonzi.machine_max.util.geometry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/**
 * {@link BeltPath2D#build(List, double, boolean)} 的有向接触求解。
 * 每个铺设轮提供一个必经轮缘点；跨段在所有圆盘的公切线可见图中求最短路。
 * 圆弧只沿铺设绕向连接，额外支撑轮可以出现在任意跨段。
 */
final class OrderedBeltSolver {
    private static final double EPS = 1e-8;
    private static final double TAU = 2 * Math.PI;
    private static final double ARC_STEP = Math.PI / 180;

    private record Pin(int wheel, Vec2 point) { }
    private record Piece(int fromWheel, int toWheel, Vec2 start, Vec2 end,
                         double startAngle, double sweep, double length) {
        boolean arc() { return Math.abs(sweep) > EPS; }
    }
    private record Hop(int to, Piece piece) { }
    private record Visit(int node, double distance) { }
    private record Node(int wheel, Vec2 point) { }
    private record RoutedSpan(Pin from, Pin to, List<Piece> pieces) { }

    private final List<Circle2D> circles;
    private final int sign;
    private final double turnEpsilon;

    private OrderedBeltSolver(List<Circle2D> circles, double turnEpsilon, int sign) {
        this.circles = circles;
        this.turnEpsilon = turnEpsilon;
        this.sign = sign;
    }

    static BeltPath2D solve(List<Circle2D> circles, double turnEpsilon, int sign) {
        return new OrderedBeltSolver(circles, turnEpsilon, sign).solve();
    }

    private BeltPath2D solve() {
        List<Pin> pins = primaryPins();
        for (int attempt = 0; attempt < circles.size() * 4; attempt++) {
            List<RoutedSpan> spans = new ArrayList<>();
            List<Piece> pieces = new ArrayList<>();
            for (int i = 0; i < pins.size(); i++) {
                Pin a = pins.get(i), b = pins.get((i + 1) % pins.size());
                List<Piece> route = new VisibilityGraph(a, b).route();
                spans.add(new RoutedSpan(a, b, route));
                pieces.addAll(route);
            }
            pieces = mergeArcs(pieces);
            BeltPath2D result = assemble(pieces);
            int outside = -1;
            for (int i = 0; i < circles.size(); i++) {
                if (!result.containsPoint(circles.get(i).center())) {
                    outside = i;
                    break;
                }
            }
            if (outside < 0) {
                if (selfIntersects(result.points()) || sign * result.signedArea() <= EPS) {
                    throw new IllegalArgumentException("ordered belt contacts produce a crossing loop");
                }
                return result;
            }
            // 圆盘完全越过跨段后，距离检测不再相交；包围判据仍要求跨段从它外侧经过。
            insertOuterContact(pins, spans, outside);
        }
        throw new IllegalArgumentException("ordered belt enclosure did not converge");
    }

    private List<Pin> primaryPins() {
        List<Pin> pins = new ArrayList<>();
        for (int i = 0; i < circles.size(); i++) {
            Circle2D prev = circles.get((i + circles.size() - 1) % circles.size());
            Circle2D cur = circles.get(i);
            Circle2D next = circles.get((i + 1) % circles.size());
            Vec2 incoming = cur.center().sub(prev.center()).normalized();
            Vec2 outgoing = next.center().sub(cur.center()).normalized();
            if (incoming.lengthSquared() <= EPS || outgoing.lengthSquared() <= EPS) {
                throw new BeltPath2D.DegenerateLayoutException("adjacent wheel centers coincide");
            }
            double turn = Math.atan2(incoming.cross(outgoing), incoming.dot(outgoing));
            Vec2 a = outward(incoming), b = outward(outgoing);
            boolean doublesBack = Math.abs(turn) > Math.PI * .75
                    && (passesWheel(prev, cur.center(), next.center(), cur.radius())
                    || passesWheel(next, cur.center(), prev.center(), cur.radius()));
            boolean cap = i == 0 || i == circles.size() - 1 || doublesBack;
            Vec2 normal;
            if (cap) {
                double sweep = sign * positive(sign * (b.angle() - a.angle()));
                normal = Vec2.fromAngle(a.angle() + sweep * .5, 1);
            } else {
                normal = a.add(b).normalized();
                if (normal.lengthSquared() <= EPS) normal = b;
            }
            pins.add(new Pin(i, cur.center().add(normal.mul(cur.radius()))));
        }
        return pins;
    }

    private static boolean passesWheel(Circle2D wheel, Vec2 from, Vec2 to, double radius) {
        Vec2 direction = to.sub(from);
        double t = wheel.center().sub(from).dot(direction) / direction.lengthSquared();
        return t > EPS && t < 1 - EPS
                && pointSegmentDistance(wheel.center(), from, to) < radius + wheel.radius();
    }

    private void insertOuterContact(List<Pin> pins, List<RoutedSpan> spans, int wheel) {
        Circle2D circle = circles.get(wheel);
        int bestSpan = -1;
        Vec2 bestNormal = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < spans.size(); i++) {
            RoutedSpan span = spans.get(i);
            if (span.from.wheel == wheel || span.to.wheel == wheel) continue;
            for (Piece piece : span.pieces) {
                if (piece.arc() || piece.length <= EPS) continue;
                Vec2 delta = piece.end.sub(piece.start);
                double t = circle.center().sub(piece.start).dot(delta) / delta.lengthSquared();
                Vec2 normal = outward(delta.normalized());
                if (t <= EPS || t >= 1 - EPS
                        || circle.center().sub(piece.start).dot(normal) <= EPS) continue;
                double distance = pointSegmentDistance(circle.center(), piece.start, piece.end);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestSpan = i;
                    bestNormal = normal;
                }
            }
        }
        if (bestSpan < 0) {
            throw new IllegalArgumentException("wheel " + wheel + " is outside the ordered belt");
        }
        Pin pin = new Pin(wheel, circle.center().add(bestNormal.mul(circle.radius())));
        for (Pin existing : pins) {
            if (existing.wheel == wheel && existing.point.distance(pin.point) < EPS) {
                throw new IllegalArgumentException("repeated enclosure contact at wheel " + wheel);
            }
        }
        pins.add(bestSpan + 1, pin);
    }

    private final class VisibilityGraph {
        private final List<Node> nodes = new ArrayList<>();
        private final List<List<Hop>> edges = new ArrayList<>();
        private final List<List<Integer>> rings = new ArrayList<>();
        private final Pin source, target;

        VisibilityGraph(Pin source, Pin target) {
            this.source = source;
            this.target = target;
            for (int i = 0; i < circles.size(); i++) rings.add(new ArrayList<>());
        }

        private int node(int wheel, Vec2 point) {
            for (int id : rings.get(wheel)) {
                if (nodes.get(id).point.distance(point) <= EPS) return id;
            }
            int id = nodes.size();
            nodes.add(new Node(wheel, point));
            edges.add(new ArrayList<>());
            rings.get(wheel).add(id);
            return id;
        }

        private void line(int wa, Vec2 a, int wb, Vec2 b) {
            if (a.distance(b) <= EPS || !visible(a, b)) return;
            int from = node(wa, a), to = node(wb, b);
            edges.get(from).add(new Hop(to, new Piece(wa, wb, a, b, 0, 0, a.distance(b))));
        }

        private void pointTangents(Pin pin, boolean fromPoint) {
            for (int i = 0; i < circles.size(); i++) {
                Circle2D circle = circles.get(i);
                double distance = pin.point.distance(circle.center());
                if (distance <= circle.radius() + EPS) continue;
                double base = pin.point.sub(circle.center()).angle();
                double angle = Math.acos(Math.min(1, circle.radius() / distance));
                for (int branch : new int[]{-1, 1}) {
                    Vec2 point = circle.pointAt(base + branch * angle);
                    Vec2 tangent = point.sub(circle.center()).normalized().leftNormal().mul(sign);
                    Vec2 direction = fromPoint ? point.sub(pin.point) : pin.point.sub(point);
                    if (direction.normalized().dot(tangent) < 1 - EPS) continue;
                    if (fromPoint) line(pin.wheel, pin.point, i, point);
                    else line(i, point, pin.wheel, pin.point);
                }
            }
        }

        private void circleTangents(int i, int j) {
            Circle2D a = circles.get(i), b = circles.get(j);
            Vec2 delta = b.center().sub(a.center());
            double distance = delta.length();
            double alpha = (a.radius() - b.radius()) / distance;
            if (Math.abs(alpha) >= 1) return;
            Vec2 u = delta.div(distance);
            Vec2 normal = u.mul(alpha).add(outward(u).mul(Math.sqrt(1 - alpha * alpha)));
            line(i, a.center().add(normal.mul(a.radius())),
                    j, b.center().add(normal.mul(b.radius())));
        }

        List<Piece> route() {
            int start = node(source.wheel, source.point), end = node(target.wheel, target.point);
            for (int i = 0; i < circles.size(); i++) {
                for (int j = 0; j < circles.size(); j++) {
                    if (i != j) circleTangents(i, j);
                }
            }
            pointTangents(source, true);
            pointTangents(target, false);
            line(source.wheel, source.point, target.wheel, target.point);
            for (int i = 0; i < circles.size(); i++) {
                Circle2D circle = circles.get(i);
                List<Integer> ring = rings.get(i);
                ring.sort(Comparator.comparingDouble(id -> positive(sign
                        * nodes.get(id).point.sub(circle.center()).angle())));
                if (ring.size() < 2) continue;
                for (int j = 0; j < ring.size(); j++) {
                    int from = ring.get(j), to = ring.get((j + 1) % ring.size());
                    double a = nodes.get(from).point.sub(circle.center()).angle();
                    double b = nodes.get(to).point.sub(circle.center()).angle();
                    double sweep = sign * positive(sign * (b - a));
                    edges.get(from).add(new Hop(to, new Piece(i, i, nodes.get(from).point,
                            nodes.get(to).point, a, sweep, Math.abs(sweep) * circle.radius())));
                }
            }
            double[] distances = new double[nodes.size()];
            Arrays.fill(distances, Double.POSITIVE_INFINITY);
            int[] previous = new int[nodes.size()];
            Hop[] hops = new Hop[nodes.size()];
            PriorityQueue<Visit> queue = new PriorityQueue<>(Comparator.comparingDouble(Visit::distance));
            distances[start] = 0;
            queue.add(new Visit(start, 0));
            while (!queue.isEmpty()) {
                Visit visit = queue.remove();
                if (visit.distance > distances[visit.node]) continue;
                if (visit.node == end) break;
                for (Hop hop : edges.get(visit.node)) {
                    double distance = visit.distance + hop.piece.length;
                    if (distance < distances[hop.to] - 1e-12) {
                        distances[hop.to] = distance;
                        previous[hop.to] = visit.node;
                        hops[hop.to] = hop;
                        queue.add(new Visit(hop.to, distance));
                    }
                }
            }
            if (!Double.isFinite(distances[end])) {
                throw new IllegalArgumentException("no clear belt span from wheel "
                        + source.wheel + " to " + target.wheel);
            }
            List<Piece> result = new ArrayList<>();
            for (int at = end; at != start; at = previous[at]) result.add(hops[at].piece);
            Collections.reverse(result);
            return result;
        }
    }

    private boolean visible(Vec2 a, Vec2 b) {
        for (Circle2D circle : circles) {
            if (pointSegmentDistance(circle.center(), a, b) < circle.radius() - EPS) return false;
        }
        return true;
    }

    private List<Piece> mergeArcs(List<Piece> input) {
        List<Piece> result = new ArrayList<>();
        for (Piece piece : input) {
            if (piece.length <= EPS) continue;
            if (!result.isEmpty() && mergeable(result.get(result.size() - 1), piece)) {
                result.set(result.size() - 1, merge(result.get(result.size() - 1), piece));
            } else result.add(piece);
        }
        if (result.size() > 1 && mergeable(result.get(result.size() - 1), result.get(0))) {
            result.set(0, merge(result.remove(result.size() - 1), result.get(0)));
        }
        return result;
    }

    private boolean mergeable(Piece a, Piece b) {
        return a.arc() && b.arc() && a.fromWheel == b.fromWheel && a.end.distance(b.start) <= EPS;
    }

    private Piece merge(Piece a, Piece b) {
        return new Piece(a.fromWheel, a.toWheel, a.start, b.end,
                a.startAngle, a.sweep + b.sweep, a.length + b.length);
    }

    private BeltPath2D assemble(List<Piece> pieces) {
        List<Vec2> points = new ArrayList<>();
        List<BeltPath2D.ArcContact> arcs = new ArrayList<>();
        List<BeltPath2D.PointContact> contacts = new ArrayList<>();
        List<BeltPath2D.Vertex> vertices = new ArrayList<>();
        List<Circle2D> ordered = new ArrayList<>();
        for (int i = 0; i < pieces.size(); i++) {
            Piece piece = pieces.get(i);
            addPoint(points, piece.start);
            if (piece.arc()) {
                Circle2D circle = circles.get(piece.fromWheel);
                int count = Math.max(2, (int) Math.ceil(Math.abs(piece.sweep) / ARC_STEP));
                List<Vec2> samples = new ArrayList<>();
                for (int k = 0; k <= count; k++) {
                    Vec2 point = k == count ? piece.end
                            : circle.pointAt(piece.startAngle + piece.sweep * k / count);
                    samples.add(point);
                    addPoint(points, point);
                }
                arcs.add(new BeltPath2D.ArcContact(piece.fromWheel, piece.startAngle,
                        piece.sweep, piece.length, samples));
                vertices.add(new BeltPath2D.Vertex(piece.fromWheel, BeltPath2D.VertexType.CONVEX,
                        piece.sweep, circle.pointAt(piece.startAngle + piece.sweep * .5)));
                ordered.add(circle);
            } else {
                Piece next = pieces.get((i + 1) % pieces.size());
                addPoint(points, piece.end);
                if (!next.arc() && piece.toWheel == next.fromWheel) {
                    Vec2 a = piece.end.sub(piece.start).normalized();
                    Vec2 b = next.end.sub(next.start).normalized();
                    double turn = Math.atan2(a.cross(b), a.dot(b));
                    contacts.add(new BeltPath2D.PointContact(piece.toWheel, piece.end));
                    vertices.add(new BeltPath2D.Vertex(piece.toWheel,
                            Math.abs(turn) <= turnEpsilon ? BeltPath2D.VertexType.STRAIGHT
                                    : BeltPath2D.VertexType.CONCAVE, turn, piece.end));
                    ordered.add(circles.get(piece.toWheel));
                }
            }
        }
        if (points.size() > 1 && points.get(0).distance(points.get(points.size() - 1)) <= EPS) {
            points.remove(points.size() - 1);
        }
        // 对外接触表从主动轮开始，附加接触只扩充这张表，不修改成员表。
        int first = -1;
        for (int i = 0; i < vertices.size(); i++) if (vertices.get(i).wheelIndex() == 0) { first = i; break; }
        if (first > 0) {
            Collections.rotate(vertices, -first);
            Collections.rotate(ordered, -first);
        }
        return BeltPath2D.fromData(circles, ordered, vertices, points, arcs, contacts, false);
    }

    private Vec2 outward(Vec2 direction) { return direction.rightNormal().mul(sign); }

    private static double positive(double angle) {
        double result = angle % TAU;
        return result < 0 ? result + TAU : result;
    }

    private static void addPoint(List<Vec2> points, Vec2 point) {
        if (points.isEmpty() || points.get(points.size() - 1).distance(point) > EPS) points.add(point);
    }

    private static double pointSegmentDistance(Vec2 point, Vec2 a, Vec2 b) {
        Vec2 ab = b.sub(a);
        double t = Math.max(0, Math.min(1, point.sub(a).dot(ab) / ab.lengthSquared()));
        return point.distance(a.add(ab.mul(t)));
    }

    private static boolean selfIntersects(List<Vec2> points) {
        for (int i = 0; i < points.size(); i++) {
            Vec2 a = points.get(i), b = points.get((i + 1) % points.size()), ab = b.sub(a);
            for (int j = i + 2; j < points.size(); j++) {
                if ((j + 1) % points.size() == i) continue;
                Vec2 c = points.get(j), d = points.get((j + 1) % points.size()), cd = d.sub(c);
                double denominator = ab.cross(cd);
                if (Math.abs(denominator) <= EPS) continue;
                double t = c.sub(a).cross(cd) / denominator;
                double u = c.sub(a).cross(ab) / denominator;
                if (t > EPS && t < 1 - EPS && u > EPS && u < 1 - EPS) return true;
            }
        }
        return false;
    }
}
