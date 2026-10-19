/* TrackPath2D: 铺设顺序为主动轮、负重轮、顶轮；ordered-belt-solver.js 与 OrderedBeltSolver.java#solve 对照。 */
(function (global) {
    'use strict';

    const TAU = Math.PI * 2;
    const EPS = 1e-9;
    const NORMALIZE_EPS = 1e-12;
    const ARC_STEP = Math.PI / 180;
    const END_CAP_TURN = Math.PI * 0.75;
    const OVERLAP_TOLERANCE = 1e-6;
    const FALLBACK_SEGMENTS = 128;
    const HULL_EPS = 1e-12;
    const orderedSolver = global.OrderedBeltSolver
        || (typeof require !== 'undefined' ? require('./ordered-belt-solver.js') : null);

    /** 相邻轮心重合等无法建立接触方向的布局。 */
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
     * @returns {boolean} 是否顺时针绕行
     */
    function determineClockwise(circles) {
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
            return right.y >= 0;
        }

        return false;
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
    function buildTwo(input, sign, normalizeInput) {
        const a = input[0];
        const b = input[1];
        const separation = Math.hypot(a.x - b.x, a.y - b.y);
        if (separation <= Math.abs(a.r - b.r) + EPS) {
            return buildContainedTwo(input, sign);
        }

        // 统一成左圆 -> 右圆，使输出为逆时针。
        const firstIndex = !normalizeInput || a.x < b.x || (a.x === b.x && a.y < b.y) ? 0 : 1;
        const left = input[firstIndex];
        const right = input[1 - firstIndex];

        const lower = externalTangent(left, right, sign);
        const upper = externalTangent(left, right, -sign);
        if (!lower || !upper) {
            return buildContainedTwo(input, sign);
        }

        const leftStart = angleOf(upper.tA, left);
        const leftEnd = angleOf(lower.tA, left);
        const leftSweep = sign * positiveSweep(sign * leftStart, sign * leftEnd);
        const rightStart = angleOf(lower.tB, right);
        const rightEnd = angleOf(upper.tB, right);
        const rightSweep = sign * positiveSweep(sign * rightStart, sign * rightEnd);

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

    function buildContainedTwo(input, sign) {
        const a = input[0];
        const b = input[1];
        const outerIndex = a.r >= b.r ? 0 : 1;
        const innerIndex = 1 - outerIndex;
        const outer = input[outerIndex];
        const inner = input[innerIndex];

        const fullArc = sampleArc(outer, 0, sign * TAU, circlePoint(outer, 0));
        const points = fullArc.slice(0, fullArc.length - 1).map(function (p) {
            return vec(p.x, p.y);
        });
        const arcs = [
            arcRecord(outerIndex, outer, 0, sign * TAU, fullArc, 0, points.length - 1)
        ];
        const vertices = [
            {
                wheelIndex: outerIndex,
                type: 'convex',
                turn: sign * TAU,
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
     * 重叠轮盘等无法建立有序接触路径时的凸包回退。
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
     * @param {{turnEpsilon?:number,clockwise?:boolean}} [options]
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
            return buildTwo(normalized, options.clockwise ? -1 : 1, options.clockwise === undefined);
        }
        const clockwise = options.clockwise === undefined ? determineClockwise(normalized) : options.clockwise;
        const sign = clockwise ? -1 : 1;
        if (hasOverlappingWheels(normalized)) throw new RangeError('ordered wheel disks overlap');
        return orderedSolver(normalized, turnEpsilon, sign, {fromData:fromData,arcRecord:arcRecord});
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
