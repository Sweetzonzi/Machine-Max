/*
 * TrackPath2D — 有序轮组的履带包络（非凸）参考实现。
 *
 * 与 src/main/java/io/github/sweetzonzi/machine_max/util/geometry/BeltPath2D.java
 * 是同一套算法的两份实现：改一侧必须同步另一侧，并各自跑测试。
 *
 * 输入是锚定有序的圆列表：第一个圆是主动轮，顺序为
 * “主动轮 → 下行程全部轮（含被顶起的）→ 上行程轮 → 回主动轮”。
 * 3 个及以上按这个顺序直接构建，输出方向跟随锚定顺序（可能顺时针，也可能逆时针）；
 * 2 个轮子走两圆外部凸包（stadium，互相包含时退化为外圆整圆）。
 *
 * 顶点分类：
 *   - 凸角：相邻轮外侧外公切线 + 圆弧；
 *   - 凹角 / 共线：单点接触，接触长度为 0；
 *   - 端帽：一律凸角，带子必须绕过该轮。
 *
 * 端帽的判据是下标 0、下标 n-1，或 |turn| > 3π/4。端帽处轮心折线接近回折、
 * 转角接近 ±π，`traversalSign * turn` 的符号会被微小位姿噪声翻掉；按转角绝对值
 * 判定，结果不依赖绕行方向，包络不会在“绕过该轮”与“擦过一点”之间跳变。
 *
 * 被顶起的下行程轮如果高过回程闭合边，同一轮会被追加到序列末尾再接触一次：
 * 下行程是轮底的点接触，回程是轮顶的圆弧。该轮的 wheelIndex 会在 vertices / arcs /
 * pointContacts 中出现两次，inputCircles 仍保持每个成员一次。
 *
 * 纯几何、双精度、无第三方依赖；浏览器与 Node 共用同一份文件。
 */
(function (global) {
    'use strict';

    const TAU = Math.PI * 2;
    const EPS = 1e-9;
    const NORMALIZE_EPS = 1e-12;
    const ARC_STEP = Math.PI / 180;
    const END_CAP_TURN = Math.PI * 0.75;
    const CUT_TOLERANCE = 1e-4;
    const OVERLAP_TOLERANCE = 1e-6;
    const FALLBACK_SEGMENTS = 128;
    const HULL_EPS = 1e-12;

    /** 轮心全共线、相邻轮心重合等无法定义闭合带路的退化布局。 */
    class DegenerateLayoutError extends Error {
        constructor(message) {
            super(message);
            this.name = 'DegenerateLayoutError';
        }
    }

    function finite(value, name) {
        if (typeof value !== 'number' || !Number.isFinite(value)) {
            throw new TypeError(name + ' must be a finite number, got ' + value);
        }
        return value;
    }

    function vec(x, y) {
        return {x: x, y: y};
    }

    function lengthSq(v) {
        return v.x * v.x + v.y * v.y;
    }

    function normalize(x, y) {
        const len = Math.hypot(x, y);
        if (len <= NORMALIZE_EPS) {
            return vec(0, 0);
        }
        return vec(x / len, y / len);
    }

    function dot(a, b) {
        return a.x * b.x + a.y * b.y;
    }

    function cross(a, b) {
        return a.x * b.y - a.y * b.x;
    }

    function distance(a, b) {
        return Math.hypot(a.x - b.x, a.y - b.y);
    }

    function circlePoint(circle, angle) {
        return vec(
            circle.x + Math.cos(angle) * circle.r,
            circle.y + Math.sin(angle) * circle.r
        );
    }

    function angleOf(point, circle) {
        return Math.atan2(point.y - circle.y, point.x - circle.x);
    }

    function signedArea(points) {
        let sum = 0;
        for (let i = 0; i < points.length; i++) {
            const p = points[i];
            const q = points[(i + 1) % points.length];
            sum += p.x * q.y - q.x * p.y;
        }
        return sum / 2;
    }

    function normalizePositive(angle) {
        let value = angle % TAU;
        if (value < 0) {
            value += TAU;
        }
        return value;
    }

    function angularDistance(a, b) {
        const diff = normalizePositive(a - b);
        return Math.min(diff, TAU - diff);
    }

    function distancePointSegment(point, a, b) {
        const abx = b.x - a.x;
        const aby = b.y - a.y;
        const lenSq = abx * abx + aby * aby;
        if (lenSq <= EPS * EPS) {
            return distance(point, a);
        }
        let t = ((point.x - a.x) * abx + (point.y - a.y) * aby) / lenSq;
        t = Math.max(0, Math.min(1, t));
        return Math.hypot(point.x - (a.x + abx * t), point.y - (a.y + aby * t));
    }

    function sameCircle(a, b) {
        return distance(a, b) <= EPS && Math.abs(a.r - b.r) <= EPS;
    }

    /** 与 Java 侧 List.indexOf 的值相等语义一致：重复圆取第一次出现。 */
    function indexOfCircle(circles, circle) {
        for (let i = 0; i < circles.length; i++) {
            if (sameCircle(circles[i], circle)) {
                return i;
            }
        }
        return -1;
    }

    function uniqueCircles(circles) {
        const unique = [];
        for (const circle of circles) {
            let duplicate = false;
            for (const existing of unique) {
                if (sameCircle(existing, circle)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                unique.push(circle);
            }
        }
        return unique;
    }

    function centroidOf(circles) {
        let x = 0;
        let y = 0;
        for (const circle of circles) {
            x += circle.x;
            y += circle.y;
        }
        return vec(x / circles.length, y / circles.length);
    }

    /**
     * 一条边朝外的法线。traversalSign = +1 时带路逆时针，外法线取有向边的右侧；
     * -1 时取左侧。
     */
    function outwardNormal(direction, traversalSign) {
        const right = vec(direction.y, -direction.x);
        return traversalSign >= 0 ? right : vec(-right.x, -right.y);
    }

    /**
     * 相邻轮的外公切线。side = +1 取有向边的右侧切点，-1 取左侧。
     * 两圆互相包含到切线不存在时返回 null。
     */
    function externalTangent(a, b, side) {
        const dx = b.x - a.x;
        const dy = b.y - a.y;
        const d = Math.hypot(dx, dy);
        if (d <= EPS) {
            return null;
        }
        const ux = dx / d;
        const uy = dy / d;
        const rx = uy;
        const ry = -ux;
        const alpha = (a.r - b.r) / d;
        if (Math.abs(alpha) >= 1 - 1e-12) {
            return null;
        }
        const beta = Math.sqrt(Math.max(0, 1 - alpha * alpha));
        const nx = alpha * ux + side * beta * rx;
        const ny = alpha * uy + side * beta * ry;
        return {
            normal: vec(nx, ny),
            tA: vec(a.x + a.r * nx, a.y + a.r * ny),
            tB: vec(b.x + b.r * nx, b.y + b.r * ny)
        };
    }

    /**
     * 从点 p 到圆 c 的切线。outward 用来在两条切线里选外侧那条。
     * p 落在圆内（含相切）时返回 null。
     */
    function tangentFromPoint(p, c, outward) {
        const vx = c.x - p.x;
        const vy = c.y - p.y;
        const d = Math.hypot(vx, vy);
        if (d <= c.r + EPS) {
            return null;
        }
        // 基点必须是 C->P；切线点角度 = 基点 ± acos(r/d)。
        const base = Math.atan2(p.y - c.y, p.x - c.x);
        const alpha = Math.acos(Math.min(1, c.r / d));
        let best = null;
        let bestScore = -Infinity;
        for (const sign of [-1, 1]) {
            const angle = base + sign * alpha;
            const point = circlePoint(c, angle);
            const score = (point.x - c.x) * outward.x + (point.y - c.y) * outward.y;
            if (score > bestScore) {
                bestScore = score;
                best = {
                    point: point,
                    angle: angle,
                    direction: normalize(point.x - p.x, point.y - p.y)
                };
            }
        }
        return best;
    }

    function addPoint(points, p) {
        const last = points[points.length - 1];
        if (last && distance(last, p) <= 1e-10) {
            return;
        }
        points.push(vec(p.x, p.y));
    }

    /**
     * 由“下行程优先”的锚定顺序确定整条带路的绕行方向。
     *
     * 闭合边（最后一个成员 → 主动轮）属于上行程，不参与判断；在其余边里取中点最低
     * 的一条作为下行程代表，要求它的外法线朝下。轮心多边形的绕向不作为判据：在
     * “共线三轮回中间抬起”的布局里，它与带路绕向恰好相反。
     *
     * @returns {number} +1 表示带路逆时针，-1 表示顺时针
     */
    function determineTraversalSign(circles) {
        const n = circles.length;
        let bestEdge = -1;
        let bestMidY = Infinity;
        for (let i = 0; i < n - 1; i++) {
            const a = circles[i];
            const b = circles[i + 1];
            const direction = vec(b.x - a.x, b.y - a.y);
            if (lengthSq(direction) <= EPS * EPS) {
                throw new DegenerateLayoutError('adjacent circle centers must not coincide');
            }
            const right = vec(direction.y, -direction.x);
            if (Math.abs(right.y) <= 1e-6) {
                // 竖直边没有“上/下外法线”可言，换下一条候选边。
                continue;
            }
            const midY = (a.y + b.y) * 0.5;
            if (midY < bestMidY - 1e-9) {
                bestMidY = midY;
                bestEdge = i;
            }
        }
        if (bestEdge >= 0) {
            const a = circles[bestEdge];
            const b = circles[bestEdge + 1];
            const right = vec(b.y - a.y, -(b.x - a.x));
            return right.y < 0 ? 1 : -1;
        }

        const centerArea = signedArea(circles);
        if (Math.abs(centerArea) <= EPS) {
            throw new DegenerateLayoutError('wheel centers are collinear; belt path is undefined');
        }
        return centerArea > 0 ? 1 : -1;
    }

    function buildEdges(points) {
        const edges = [];
        let length = 0;
        const n = points.length;
        for (let i = 0; i < n; i++) {
            const a = points[i];
            const b = points[(i + 1) % n];
            const dx = b.x - a.x;
            const dy = b.y - a.y;
            const len = Math.hypot(dx, dy);
            if (len <= EPS) {
                continue;
            }
            edges.push({
                a: a,
                b: b,
                dx: dx,
                dy: dy,
                length: len,
                start: length,
                tangent: vec(dx / len, dy / len)
            });
            length += len;
        }
        return {edges: edges, length: length};
    }

    function isConvexPolygon(points) {
        if (points.length < 3) {
            return false;
        }
        let sign = 0;
        for (let i = 0; i < points.length; i++) {
            const a = points[i];
            const b = points[(i + 1) % points.length];
            const c = points[(i + 2) % points.length];
            const turn = cross(
                vec(b.x - a.x, b.y - a.y),
                vec(c.x - b.x, c.y - b.y)
            );
            if (Math.abs(turn) <= EPS) {
                continue;
            }
            const current = turn > 0 ? 1 : -1;
            if (sign === 0) {
                sign = current;
            } else if (sign !== current) {
                return false;
            }
        }
        return true;
    }

    function arcRecord(wheelIndex, circle, startAngle, sweep, contactPoints, startIndex, endIndex) {
        return {
            wheelIndex: wheelIndex,
            wheelId: circle.id,
            type: 'arc',
            center: vec(circle.x, circle.y),
            radius: circle.r,
            startAngle: startAngle,
            sweep: sweep,
            contactLength: Math.abs(sweep) * circle.r,
            contactPoints: contactPoints,
            startIndex: startIndex,
            endIndex: endIndex
        };
    }

    /**
     * 锚定顺序构建。orderedInput 可能比 originalInput 多一个重复轮——被顶起的
     * 下行程轮如果高过闭合边，会在这里追加一个上行程接触。
     */
    function buildOrdered(orderedInput, originalInput, turnEpsilon, allowExpansion) {
        const ordered = orderedInput.slice();
        const n = ordered.length;
        const unique = uniqueCircles(originalInput);
        const centerArea = signedArea(unique);
        if (Math.abs(centerArea) <= EPS) {
            throw new DegenerateLayoutError('wheel centers are collinear; belt path is undefined');
        }
        const centroid = centroidOf(unique);
        const traversalSign = determineTraversalSign(ordered);

        const vertices = [];
        for (let i = 0; i < n; i++) {
            const prev = ordered[(i + n - 1) % n];
            const cur = ordered[i];
            const next = ordered[(i + 1) % n];
            const dPrev = normalize(cur.x - prev.x, cur.y - prev.y);
            const dNext = normalize(next.x - cur.x, next.y - cur.y);
            if (lengthSq(dPrev) <= EPS * EPS || lengthSq(dNext) <= EPS * EPS) {
                throw new DegenerateLayoutError('adjacent circle centers must not coincide');
            }
            const turn = Math.atan2(cross(dPrev, dNext), dot(dPrev, dNext));
            const nPrev = outwardNormal(dPrev, traversalSign);
            const nNext = outwardNormal(dNext, traversalSign);
            let outward = normalize(nPrev.x + nNext.x, nPrev.y + nNext.y);
            if (lengthSq(outward) <= EPS * EPS) {
                outward = nNext;
            }
            const support = vec(cur.x + outward.x * cur.r, cur.y + outward.y * cur.r);
            let type = traversalSign * turn > turnEpsilon
                ? 'convex'
                : (traversalSign * turn < -turnEpsilon ? 'concave' : 'straight');
            // 主动轮、闭合前的最后一个上行程轮，以及轮心折线接近回折的顶点都是端帽：
            // 带子必须绕过它们。
            if (i === 0 || i === n - 1 || Math.abs(turn) > END_CAP_TURN) {
                type = 'convex';
            }
            let wheelIndex = indexOfCircle(originalInput, cur);
            if (wheelIndex < 0) {
                wheelIndex = i;
            }
            vertices.push({
                wheelIndex: wheelIndex,
                type: type,
                turn: turn,
                support: support
            });
        }

        const interim = [];
        for (let i = 0; i < n; i++) {
            const a = ordered[i];
            const b = ordered[(i + 1) % n];
            const va = vertices[i];
            const vb = vertices[(i + 1) % n];
            const centerDir = normalize(b.x - a.x, b.y - a.y);
            const outward = outwardNormal(centerDir, traversalSign);
            let start;
            let end;

            if (va.type === 'convex' && vb.type === 'convex') {
                const tangent = externalTangent(a, b, traversalSign);
                if (!tangent) {
                    throw new RangeError('no external tangent between convex wheels ' + i
                        + ' and ' + ((i + 1) % n));
                }
                start = tangent.tA;
                end = tangent.tB;
            } else if (va.type !== 'convex' && vb.type === 'convex') {
                const tangent = tangentFromPoint(va.support, b, outward);
                if (!tangent) {
                    throw new RangeError('support point of wheel ' + i
                        + ' lies inside wheel ' + ((i + 1) % n));
                }
                start = va.support;
                end = tangent.point;
            } else if (va.type === 'convex' && vb.type !== 'convex') {
                const tangent = tangentFromPoint(vb.support, a, outward);
                if (!tangent) {
                    throw new RangeError('support point of wheel ' + ((i + 1) % n)
                        + ' lies inside wheel ' + i);
                }
                start = tangent.point;
                end = vb.support;
            } else {
                start = va.support;
                end = vb.support;
            }

            const direction = normalize(end.x - start.x, end.y - start.y);
            if (lengthSq(direction) <= EPS * EPS) {
                throw new RangeError('zero-length belt edge at wheel ' + i);
            }
            interim.push({start: start, end: end, direction: direction});
        }

        // 非相邻轮不能被直线段穿过。闭合边（最后成员 → 主动轮）穿过一个下行程轮时，
        // 说明该轮高过回程边，需要在回程上再接触一次：把同一轮追加到序列末尾，
        // 让它以上行程凸角再出现一次。其它边穿轮属于无法表示的布局，直接报错。
        let expandWheel = -1;
        outer:
        for (let i = 0; i < n; i++) {
            const edge = interim[i];
            for (let j = 0; j < n; j++) {
                if (j === i || j === (i + 1) % n) {
                    continue;
                }
                const wheel = ordered[j];
                // 同一物理轮可能在序列里出现两次（下行程点接触 + 回程圆弧）。
                // 它已经是边的端点，不能当成“非相邻轮被穿过”。
                if (sameCircle(wheel, ordered[i]) || sameCircle(wheel, ordered[(i + 1) % n])) {
                    continue;
                }
                const clearance = distancePointSegment(wheel, edge.start, edge.end) - wheel.r;
                if (clearance < -CUT_TOLERANCE) {
                    if (allowExpansion && i === n - 1 && j > 0 && j < n - 1) {
                        expandWheel = j;
                        break outer;
                    }
                    throw new RangeError('belt edge ' + i + ' cuts wheel ' + j
                        + ' (clearance ' + clearance + ')');
                }
            }
        }
        if (expandWheel >= 0) {
            const expanded = ordered.slice();
            expanded.push(ordered[expandWheel]);
            return buildOrdered(expanded, originalInput, turnEpsilon, false);
        }

        const points = [];
        const arcs = [];
        const pointContacts = [];

        for (let i = 0; i < n; i++) {
            const circle = ordered[i];
            const vertex = vertices[i];
            const prev = interim[(i + n - 1) % n];
            const next = interim[i];
            const tIn = prev.end;
            const tOut = next.start;

            if (vertex.type !== 'convex') {
                addPoint(points, vertex.support);
                pointContacts.push({
                    wheelIndex: vertex.wheelIndex,
                    wheelId: circle.id,
                    type: 'point',
                    point: vec(vertex.support.x, vertex.support.y),
                    contactLength: 0,
                    sweep: 0
                });
                addPoint(points, next.end);
                continue;
            }

            // 圆弧必须绕过轮子的外侧。两条边外法线求平均只能给出“短弧”一侧；
            // 端帽处带子可能包超过半圈，此时真正的外侧是平均方向的反方向。
            const nPrev = outwardNormal(prev.direction, traversalSign);
            const nNext = outwardNormal(next.direction, traversalSign);
            let bisector = normalize(nPrev.x + nNext.x, nPrev.y + nNext.y);
            if (lengthSq(bisector) <= EPS * EPS) {
                bisector = nNext;
            }
            const away = vec(circle.x - centroid.x, circle.y - centroid.y);
            let reference = bisector;
            if (lengthSq(away) > EPS * EPS && dot(bisector, away) < 0) {
                reference = vec(-bisector.x, -bisector.y);
            }

            const arcStart = Math.atan2(tIn.y - circle.y, tIn.x - circle.x);
            const arcEnd = Math.atan2(tOut.y - circle.y, tOut.x - circle.x);
            const ccwSweep = normalizePositive(arcEnd - arcStart);
            const cwSweep = ccwSweep - TAU;
            const referenceAngle = Math.atan2(reference.y, reference.x);
            const ccwDistance = angularDistance(arcStart + ccwSweep * 0.5, referenceAngle);
            const cwDistance = angularDistance(arcStart + cwSweep * 0.5, referenceAngle);
            const sweep = ccwDistance <= cwDistance ? ccwSweep : cwSweep;

            if (Math.abs(sweep) <= EPS) {
                const zeroStart = points.length === 0 ? 0 : points.length - 1;
                addPoint(points, tIn);
                arcs.push(arcRecord(
                    vertex.wheelIndex, circle, arcStart, 0,
                    [vec(tIn.x, tIn.y)], zeroStart, points.length - 1
                ));
                addPoint(points, next.end);
                continue;
            }

            const arcSamples = Math.max(2, Math.ceil(Math.abs(sweep) / ARC_STEP));
            const arcPoints = [];
            for (let k = 0; k <= arcSamples; k++) {
                const angle = arcStart + sweep * (k / arcSamples);
                arcPoints.push(circlePoint(circle, angle));
            }
            // 用几何终点收口，避免浮点误差在拼接处留下缝。
            arcPoints[arcPoints.length - 1] = vec(tOut.x, tOut.y);

            const startIndex = points.length === 0 ? 0 : points.length - 1;
            for (const point of arcPoints) {
                addPoint(points, point);
            }
            const endIndex = points.length - 1;

            arcs.push(arcRecord(
                vertex.wheelIndex, circle, arcStart, sweep,
                arcPoints, startIndex, endIndex
            ));
            addPoint(points, next.end);
        }

        return fromData(originalInput, ordered, vertices, points, arcs, pointContacts, false);
    }

    function sampleArc(circle, start, sweep, endPoint) {
        const samples = Math.abs(sweep) <= EPS
            ? 1
            : Math.max(2, Math.ceil(Math.abs(sweep) / ARC_STEP));
        const points = [];
        for (let i = 0; i <= samples; i++) {
            const angle = start + sweep * (i / samples);
            points.push(circlePoint(circle, angle));
        }
        points[points.length - 1] = vec(endPoint.x, endPoint.y);
        return points;
    }

    function positiveSweep(start, end) {
        let sweep = (end - start) % TAU;
        if (sweep < 0) {
            sweep += TAU;
        }
        return sweep;
    }

    /**
     * 2 轮布局：两个外离/重叠圆盘的外部凸包 = 两条外公切线 + 两段圆弧。
     * 两圆互相包含时退化为外圆整圆。输出规范化为逆时针，可能反转输入顺序。
     */
    function buildTwo(input) {
        const a = input[0];
        const b = input[1];
        const separation = Math.hypot(a.x - b.x, a.y - b.y);
        if (separation <= Math.abs(a.r - b.r) + EPS) {
            return buildContainedTwo(input);
        }

        // 统一成左圆 -> 右圆，使输出为逆时针。
        const firstIndex = (a.x < b.x || (a.x === b.x && a.y < b.y)) ? 0 : 1;
        const left = input[firstIndex];
        const right = input[1 - firstIndex];

        const lower = externalTangent(left, right, +1);
        const upper = externalTangent(left, right, -1);
        if (!lower || !upper) {
            return buildContainedTwo(input);
        }

        const leftStart = angleOf(upper.tA, left);
        const leftEnd = angleOf(lower.tA, left);
        const leftSweep = positiveSweep(leftStart, leftEnd);
        const rightStart = angleOf(lower.tB, right);
        const rightEnd = angleOf(upper.tB, right);
        const rightSweep = positiveSweep(rightStart, rightEnd);

        const leftArc = sampleArc(left, leftStart, leftSweep, lower.tA);
        const rightArc = sampleArc(right, rightStart, rightSweep, upper.tB);

        const points = [];
        for (const point of leftArc) {
            addPoint(points, point);
        }
        addPoint(points, rightArc[0]);
        for (const point of rightArc) {
            addPoint(points, point);
        }
        addPoint(points, leftArc[0]);

        const rightStartIndex = leftArc.length;
        const arcs = [
            arcRecord(firstIndex, left, leftStart, leftSweep, leftArc, 0, leftArc.length - 1),
            arcRecord(
                1 - firstIndex, right, rightStart, rightSweep, rightArc,
                rightStartIndex, leftArc.length + rightArc.length - 1
            )
        ];
        const vertices = [
            {
                wheelIndex: firstIndex,
                type: 'convex',
                turn: leftSweep,
                support: vec(leftArc[0].x, leftArc[0].y)
            },
            {
                wheelIndex: 1 - firstIndex,
                type: 'convex',
                turn: rightSweep,
                support: vec(rightArc[0].x, rightArc[0].y)
            }
        ];

        return fromData(input, [left, right], vertices, points, arcs, [], firstIndex !== 0);
    }

    function buildContainedTwo(input) {
        const a = input[0];
        const b = input[1];
        const outerIndex = a.r >= b.r ? 0 : 1;
        const innerIndex = 1 - outerIndex;
        const outer = input[outerIndex];
        const inner = input[innerIndex];

        const fullArc = sampleArc(outer, 0, TAU, circlePoint(outer, 0));
        const points = fullArc.slice(0, fullArc.length - 1).map(function (p) {
            return vec(p.x, p.y);
        });
        const arcs = [
            arcRecord(outerIndex, outer, 0, TAU, fullArc, 0, points.length - 1)
        ];
        const vertices = [
            {
                wheelIndex: outerIndex,
                type: 'convex',
                turn: TAU,
                support: circlePoint(outer, 0)
            },
            {
                wheelIndex: innerIndex,
                type: 'straight',
                turn: 0,
                support: vec(inner.x, inner.y)
            }
        ];

        return fromData(input, [outer, inner], vertices, points, arcs, [], false);
    }

    function fromData(inputCircles, orderedCircles, vertices, points, arcs, pointContacts, reversed) {
        const edgeInfo = buildEdges(points);
        const area = signedArea(points);
        const length = edgeInfo.length;

        function sampleAt(s) {
            if (!Number.isFinite(s)) {
                throw new TypeError('s must be finite, got ' + s);
            }
            if (edgeInfo.edges.length === 0 || length <= EPS) {
                const p = points.length > 0 ? points[0] : vec(0, 0);
                return {
                    x: p.x,
                    y: p.y,
                    tangent: vec(1, 0),
                    normal: vec(0, -1),
                    segmentIndex: 0,
                    s: 0
                };
            }
            let u = s % length;
            if (u < 0) {
                u += length;
            }
            const edges = edgeInfo.edges;
            let lo = 0;
            let hi = edges.length - 1;
            while (lo < hi) {
                const mid = (lo + hi + 1) >> 1;
                if (edges[mid].start <= u) {
                    lo = mid;
                } else {
                    hi = mid - 1;
                }
            }
            const edge = edges[lo];
            const t = Math.min(1, Math.max(0, (u - edge.start) / edge.length));
            const tangent = vec(edge.tangent.x, edge.tangent.y);
            // 逆时针时外法线 = 切线右转 90°；顺时针时相反。
            const normal = area >= 0
                ? vec(tangent.y, -tangent.x)
                : vec(-tangent.y, tangent.x);
            return {
                x: edge.a.x + edge.dx * t,
                y: edge.a.y + edge.dy * t,
                tangent: tangent,
                normal: normal,
                segmentIndex: lo,
                s: u
            };
        }

        function distanceToBoundary(p) {
            let best = Infinity;
            for (const edge of edgeInfo.edges) {
                best = Math.min(best, distancePointSegment(p, edge.a, edge.b));
            }
            return best;
        }

        function containsPoint(p) {
            // 射线法。边界上的点可能返回 false；需要边界判定时用 distanceToBoundary。
            let inside = false;
            for (let i = 0, j = points.length - 1; i < points.length; j = i++) {
                const a = points[i];
                const b = points[j];
                if (((a.y > p.y) !== (b.y > p.y))
                    && (p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x)) {
                    inside = !inside;
                }
            }
            return inside;
        }

        function linkCount(linkLength) {
            if (!(linkLength > 0)) {
                throw new RangeError('linkLength must be > 0');
            }
            return Math.max(1, Math.round(length / linkLength));
        }

        return {
            inputCircles: inputCircles,
            orderedCircles: orderedCircles,
            vertices: vertices,
            points: points,
            edges: edgeInfo.edges,
            arcs: arcs,
            pointContacts: pointContacts,
            contacts: pointContacts,
            circles: inputCircles,
            length: length,
            signedArea: area,
            area: area,
            isConvex: isConvexPolygon(points),
            reversedFromInput: reversed,
            reversed: reversed,
            sampleAt: sampleAt,
            containsPoint: containsPoint,
            distanceToBoundary: distanceToBoundary,
            linkCount: linkCount
        };
    }

    /**
     * 是否存在相互重叠（圆心距 < 半径和）的轮对。悬挂压缩时轮子可以重叠；
     * 这种布局的有序点接触模型可能无解，调用方可以据此决定是否回退凸包。
     */
    function hasOverlappingWheels(circles) {
        for (let i = 0; i < circles.length; i++) {
            for (let j = i + 1; j < circles.length; j++) {
                const a = circles[i];
                const b = circles[j];
                if (Math.hypot(a.x - b.x, a.y - b.y) < a.r + b.r - OVERLAP_TOLERANCE) {
                    return true;
                }
            }
        }
        return false;
    }

    function envelopeModule() {
        if (global.TrackEnvelope) {
            return global.TrackEnvelope;
        }
        if (typeof require !== 'undefined') {
            try {
                return require('./track-envelope.js');
            } catch (error) {
                return null;
            }
        }
        return null;
    }

    /** 圆盘采样凸包，与 ConvexHull2D.hullOfCircles(circles, segments) 一致。 */
    function hullOfCircles(circles, segments) {
        const envelope = envelopeModule();
        if (!envelope || typeof envelope.convexHull !== 'function') {
            throw new Error('convexFallback needs track-envelope.js (TrackEnvelope.convexHull)');
        }
        const samples = [];
        for (const circle of circles) {
            if (circle.r <= HULL_EPS) {
                samples.push(vec(circle.x, circle.y));
                continue;
            }
            for (let i = 0; i < segments; i++) {
                samples.push(circlePoint(circle, (TAU * i) / segments));
            }
        }
        return envelope.convexHull(samples);
    }

    /**
     * 全共线等无法定义闭合带路的退化布局的凸包回退。
     *
     * 返回的是圆盘集合的凸包折线——没有圆弧、没有凹口，只保证 length / linkCount /
     * sampleAt 可用。调用方应记录告警，并在轮位姿恢复后重新尝试 buildBeltPath。
     *
     * 只应在 buildBeltPath 抛 DegenerateLayoutError，或 hasOverlappingWheels 为 true
     * 时调用。支撑点落进邻轮、直线段穿过非相邻轮等几何失败在无重叠时不属于退化布局，
     * 回退会抹掉抬轮凹口。
     */
    function convexFallback(circles) {
        if (!Array.isArray(circles) || circles.length < 2) {
            throw new RangeError('at least 2 circles are required');
        }
        const normalized = circles.map(function (circle, index) {
            const r = finite(circle.r, 'circle[' + index + '].r');
            if (r <= EPS) {
                throw new RangeError('circle ' + index + ' radius must be > 0');
            }
            return {
                id: circle.id == null ? index : circle.id,
                x: finite(circle.x, 'circle[' + index + '].x'),
                y: finite(circle.y, 'circle[' + index + '].y'),
                r: r
            };
        });
        const hull = hullOfCircles(normalized, FALLBACK_SEGMENTS);
        if (hull.length < 3) {
            throw new RangeError('degenerate circle hull');
        }
        const vertices = hull.map(function (point) {
            return {
                wheelIndex: -1,
                type: 'straight',
                turn: 0,
                support: vec(point.x, point.y)
            };
        });
        const points = hull.map(function (point) {
            return vec(point.x, point.y);
        });
        return fromData(normalized, normalized, vertices, points, [], [], false);
    }

    /**
     * @param {{x:number,y:number,r:number,id?:*}[]} circles
     *        锚定轮组：首个为主动轮，顺序为“主动轮 → 下行程 → 上行程”；
     *        2 个轮子走凸包/stadium，3 个及以上按轮序走非凸带路
     * @param {{turnEpsilon?:number}} [options]
     */
    function buildBeltPath(circles, options) {
        if (!Array.isArray(circles) || circles.length < 2) {
            throw new RangeError('at least 2 circles are required');
        }
        options = options || {};
        const turnEpsilon = options.turnEpsilon == null ? EPS : options.turnEpsilon;
        if (!(turnEpsilon > 0)) {
            throw new RangeError('turnEpsilon must be > 0');
        }
        const normalized = circles.map(function (circle, index) {
            if (!circle || typeof circle !== 'object') {
                throw new TypeError('circle[' + index + '] must be an object');
            }
            return {
                id: circle.id == null ? index : circle.id,
                x: finite(circle.x, 'circle[' + index + '].x'),
                y: finite(circle.y, 'circle[' + index + '].y'),
                r: finite(circle.r, 'circle[' + index + '].r')
            };
        });
        for (let i = 0; i < normalized.length; i++) {
            if (normalized[i].r <= EPS) {
                throw new RangeError('circle ' + i + ' radius must be > 0');
            }
        }
        if (normalized.length === 2) {
            return buildTwo(normalized);
        }
        return buildOrdered(normalized, normalized, turnEpsilon, true);
    }

    const api = {
        TAU: TAU,
        EPS: EPS,
        ARC_STEP: ARC_STEP,
        END_CAP_TURN: END_CAP_TURN,
        DegenerateLayoutError: DegenerateLayoutError,
        buildBeltPath: buildBeltPath,
        convexFallback: convexFallback,
        hasOverlappingWheels: hasOverlappingWheels,
        externalTangent: externalTangent,
        signedArea: signedArea
    };
    if (typeof module !== 'undefined' && module.exports) module.exports = api;
    global.TrackPath = api;
})(typeof globalThis !== 'undefined' ? globalThis : this);
