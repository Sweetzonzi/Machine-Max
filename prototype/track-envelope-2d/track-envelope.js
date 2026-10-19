/*
 * TrackEnvelope2D — 履带包络线的纯 2D 参考实现。
 *
 * 输入是一组圆（轮心 + 半径），输出是这些圆盘并集的凸包折线。
 * 算法：
 *   1. 每个圆按固定段数采样边界点（v1 = 64）；
 *   2. 对全部采样点做 monotone chain 凸包；
 *   3. 把凸包包装成可沿弧长采样的对象（长度 / 切线 / 外法线）。
 *
 * 这里刻意不依赖任何渲染或 Minecraft 类型，浏览器与 Node 共用同一份文件。
 */
(function (global) {
    'use strict';

    const TAU = Math.PI * 2;
    const EPS = 1e-9;
    const HULL_EPS = 1e-12;

    function requireFinite(value, name) {
        if (typeof value !== 'number' || !Number.isFinite(value)) {
            throw new TypeError(name + ' must be a finite number, got ' + value);
        }
        return value;
    }

    function comparePoints(a, b) {
        if (a.x !== b.x) return a.x - b.x;
        return a.y - b.y;
    }

    function cross(o, a, b) {
        return (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x);
    }

    function distance(a, b) {
        return Math.hypot(a.x - b.x, a.y - b.y);
    }

    function dot(a, b) {
        return a.x * b.x + a.y * b.y;
    }

    /**
     * 对任意点集求逆时针凸包。共线点被剔除（周长不变）。
     * 退化输入返回 0/1/2 个点。
     */
    function convexHull(points) {
        if (!Array.isArray(points)) {
            throw new TypeError('points must be an array');
        }
        if (points.length <= 1) {
            return points.slice();
        }

        const sorted = points.slice().sort(comparePoints);
        const unique = [];
        for (const p of sorted) {
            const last = unique[unique.length - 1];
            if (!last || Math.abs(p.x - last.x) > HULL_EPS || Math.abs(p.y - last.y) > HULL_EPS) {
                unique.push(p);
            }
        }
        if (unique.length <= 1) {
            return unique;
        }

        const lower = [];
        for (const p of unique) {
            while (lower.length >= 2 && cross(lower[lower.length - 2], lower[lower.length - 1], p) <= HULL_EPS) {
                lower.pop();
            }
            lower.push(p);
        }

        const upper = [];
        for (let i = unique.length - 1; i >= 0; i--) {
            const p = unique[i];
            while (upper.length >= 2 && cross(upper[upper.length - 2], upper[upper.length - 1], p) <= HULL_EPS) {
                upper.pop();
            }
            upper.push(p);
        }

        lower.pop();
        upper.pop();
        const hull = lower.concat(upper);
        return hull.length > 0 ? hull : [unique[0]];
    }

    function emptyEnvelope(segments, circles) {
        return {
            segments: segments,
            circles: circles,
            points: [],
            edges: [],
            samples: [],
            length: 0,
            area: 0,
            centroid: {x: 0, y: 0},
            isEmpty: true,
            isDegenerate: true,
            sampleAt: function (s) {
                throw new RangeError('cannot sample an empty envelope: ' + s);
            },
            containsPoint: function () {
                return false;
            },
            distanceToBoundary: function () {
                return Infinity;
            },
            support: function () {
                return -Infinity;
            }
        };
    }

    /**
     * @param {{x:number,y:number,r:number,id?:* ,color?:string}[]} circles
     * @param {{segments?:number}} [options]
     */
    function buildEnvelope(circles, options) {
        if (!Array.isArray(circles)) {
            throw new TypeError('circles must be an array');
        }
        options = options || {};
        const segments = options.segments == null ? 64 : options.segments;
        if (!Number.isInteger(segments) || segments < 3) {
            throw new RangeError('segments must be an integer >= 3, got ' + segments);
        }

        const normalized = circles.map(function (circle, index) {
            if (!circle || typeof circle !== 'object') {
                throw new TypeError('circle[' + index + '] must be an object');
            }
            const r = requireFinite(circle.r, 'circle[' + index + '].r');
            if (r < 0) {
                throw new RangeError('circle[' + index + '].r must be >= 0');
            }
            return {
                id: circle.id == null ? index : circle.id,
                x: requireFinite(circle.x, 'circle[' + index + '].x'),
                y: requireFinite(circle.y, 'circle[' + index + '].y'),
                r: r,
                color: circle.color
            };
        });

        if (normalized.length === 0) {
            return emptyEnvelope(segments, normalized);
        }

        const samples = [];
        for (const circle of normalized) {
            if (circle.r <= EPS) {
                samples.push({x: circle.x, y: circle.y, source: circle.id});
                continue;
            }
            for (let i = 0; i < segments; i++) {
                const angle = (i / segments) * TAU;
                samples.push({
                    x: circle.x + circle.r * Math.cos(angle),
                    y: circle.y + circle.r * Math.sin(angle),
                    source: circle.id
                });
            }
        }

        const points = convexHull(samples);

        // 去掉零长度边；退化输入（单点）得到空边集。
        const edges = [];
        let perimeter = 0;
        for (let i = 0; i < points.length; i++) {
            const a = points[i];
            const b = points[(i + 1) % points.length];
            const dx = b.x - a.x;
            const dy = b.y - a.y;
            const len = Math.hypot(dx, dy);
            if (len <= HULL_EPS) {
                continue;
            }
            edges.push({
                a: a,
                b: b,
                dx: dx,
                dy: dy,
                length: len,
                start: perimeter,
                tangent: {x: dx / len, y: dy / len},
                // 逆时针多边形，外法线 = 切线顺时针旋转 90°。
                normal: {x: dy / len, y: -dx / len}
            });
            perimeter += len;
        }

        let twiceArea = 0;
        let cx = 0;
        let cy = 0;
        for (let i = 0; i < points.length; i++) {
            const p = points[i];
            const q = points[(i + 1) % points.length];
            const f = p.x * q.y - q.x * p.y;
            twiceArea += f;
            cx += (p.x + q.x) * f;
            cy += (p.y + q.y) * f;
        }
        const area = twiceArea / 2;
        let centroid;
        if (Math.abs(twiceArea) > EPS) {
            centroid = {x: cx / (3 * twiceArea), y: cy / (3 * twiceArea)};
        } else {
            let sx = 0;
            let sy = 0;
            for (const p of points) {
                sx += p.x;
                sy += p.y;
            }
            centroid = points.length > 0
                ? {x: sx / points.length, y: sy / points.length}
                : {x: 0, y: 0};
        }

        const empty = edges.length === 0;

        function sampleAt(s) {
            if (!Number.isFinite(s)) {
                throw new TypeError('s must be a finite number, got ' + s);
            }
            if (empty) {
                const p = points[0] || {x: 0, y: 0};
                return {
                    x: p.x,
                    y: p.y,
                    tangent: {x: 1, y: 0},
                    normal: {x: 0, y: -1},
                    segmentIndex: 0,
                    s: 0
                };
            }

            let u = s % perimeter;
            if (u < 0) {
                u += perimeter;
            }

            // 找最后一个 start <= u 的边；顶点处取离开该顶点的边。
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
            return {
                x: edge.a.x + edge.dx * t,
                y: edge.a.y + edge.dy * t,
                tangent: {x: edge.tangent.x, y: edge.tangent.y},
                normal: {x: edge.normal.x, y: edge.normal.y},
                segmentIndex: lo,
                s: u
            };
        }

        function containsPoint(p, eps) {
            if (!p || !Number.isFinite(p.x) || !Number.isFinite(p.y)) {
                return false;
            }
            const tolerance = eps == null ? 1e-9 : eps;
            if (points.length === 0) {
                return false;
            }
            if (points.length === 1) {
                return distance(p, points[0]) <= tolerance;
            }
            if (points.length === 2) {
                return distanceToBoundary(p) <= tolerance;
            }
            for (const edge of edges) {
                const side = edge.dx * (p.y - edge.a.y) - edge.dy * (p.x - edge.a.x);
                if (side < -tolerance) {
                    return false;
                }
            }
            return true;
        }

        function distanceToSegment(p, a, b) {
            const dx = b.x - a.x;
            const dy = b.y - a.y;
            const lenSq = dx * dx + dy * dy;
            if (lenSq <= HULL_EPS) {
                return distance(p, a);
            }
            let t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / lenSq;
            t = Math.max(0, Math.min(1, t));
            return Math.hypot(p.x - (a.x + dx * t), p.y - (a.y + dy * t));
        }

        function distanceToBoundary(p) {
            if (points.length === 0) {
                return Infinity;
            }
            if (points.length === 1) {
                return distance(p, points[0]);
            }
            let best = Infinity;
            for (const edge of edges) {
                best = Math.min(best, distanceToSegment(p, edge.a, edge.b));
            }
            return best;
        }

        function support(direction) {
            if (!direction || !Number.isFinite(direction.x) || !Number.isFinite(direction.y)) {
                throw new TypeError('direction must be a finite 2D vector');
            }
            let best = -Infinity;
            for (const p of points) {
                best = Math.max(best, p.x * direction.x + p.y * direction.y);
            }
            return best;
        }

        return {
            segments: segments,
            circles: normalized,
            points: points,
            edges: edges,
            samples: samples,
            length: perimeter,
            area: area,
            centroid: centroid,
            isEmpty: points.length === 0,
            isDegenerate: edges.length === 0,
            sampleAt: sampleAt,
            containsPoint: containsPoint,
            distanceToBoundary: distanceToBoundary,
            support: support
        };
    }

    /** 圆盘采样 v1 的单圆弓高误差上界 r * (1 - cos(pi / segments))。 */
    function samplingSagitta(radius, segments) {
        requireFinite(radius, 'radius');
        if (!Number.isInteger(segments) || segments < 3) {
            throw new RangeError('segments must be an integer >= 3');
        }
        return radius * (1 - Math.cos(Math.PI / segments));
    }

    /** 服务端与客户端共用的节数公式：N = max(1, round(L / linkLength))。 */
    function estimateLinkCount(length, linkLength) {
        requireFinite(length, 'length');
        requireFinite(linkLength, 'linkLength');
        if (linkLength <= 0) {
            throw new RangeError('linkLength must be > 0');
        }
        return Math.max(1, Math.round(length / linkLength));
    }

    function maxDurability(linkDurability, linkCount) {
        requireFinite(linkDurability, 'linkDurability');
        if (linkDurability < 0) {
            throw new RangeError('linkDurability must be >= 0');
        }
        if (!Number.isInteger(linkCount) || linkCount < 1) {
            throw new RangeError('linkCount must be an integer >= 1');
        }
        return linkDurability * linkCount;
    }

    /** 圆盘并集的真实支撑函数：max_i (c_i · u + r_i)。 */
    function circleSupport(circles, direction) {
        requireFinite(direction.x, 'direction.x');
        requireFinite(direction.y, 'direction.y');
        let best = -Infinity;
        for (let i = 0; i < circles.length; i++) {
            const c = circles[i];
            best = Math.max(best, c.x * direction.x + c.y * direction.y + c.r);
        }
        return best;
    }

    const api = {
        TAU: TAU,
        buildEnvelope: buildEnvelope,
        convexHull: convexHull,
        samplingSagitta: samplingSagitta,
        estimateLinkCount: estimateLinkCount,
        maxDurability: maxDurability,
        circleSupport: circleSupport
    };

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = api;
    }
    global.TrackEnvelope = api;
})(typeof globalThis !== 'undefined' ? globalThis : this);
