/*
 * TrackPath2D 测试：有序轮组的履带包络。
 *
 * 与 src/test/java/io/github/sweetzonzi/machine_max/util/geometry/BeltPath2DTest.java
 * 覆盖同一组场景：凸布局、平地、抬起/降低负重轮、锚定顺序、退化轮序下的端帽、
 * 回程二次接触、2 轮 stadium/不等半径/包含、反向输入、弧长采样、无自交、不切入轮子。
 *
 * 分三层：
 *   - CASES：可视化场景，每个都渲染一张卡片；
 *   - UNIT_TESTS：Java 几何回归的代表性布局与旋转不变量；
 *   - 自检：非法输入与退化输入。
 */
(function (global) {
    'use strict';

    const TP = global.TrackPath
        || (typeof require !== 'undefined' ? require('./track-path.js') : null);
    const TE = global.TrackEnvelope
        || (typeof require !== 'undefined' ? require('./track-envelope.js') : null);
    if (!TP || !TE) {
        throw new Error('TrackPath / TrackEnvelope not loaded');
    }

    const TAU = Math.PI * 2;
    const CONFIG = {
        linkLength: 0.2,
        linkDurability: 20,
        contactEpsilon: 1e-6
    };

    // ---------------------------------------------------------------- 几何辅助

    function distance(a, b) {
        return Math.hypot(a.x - b.x, a.y - b.y);
    }

    function dot(a, b) {
        return a.x * b.x + a.y * b.y;
    }

    function cross(a, b) {
        return a.x * b.y - a.y * b.x;
    }

    function normalizeAngle(angle) {
        let value = angle % TAU;
        if (value > Math.PI) {
            value -= TAU;
        }
        if (value < -Math.PI) {
            value += TAU;
        }
        return value;
    }

    function distancePointSegment(p, a, b) {
        const dx = b.x - a.x;
        const dy = b.y - a.y;
        const lenSq = dx * dx + dy * dy;
        if (lenSq <= 1e-15) {
            return distance(p, a);
        }
        let t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / lenSq;
        t = Math.max(0, Math.min(1, t));
        return Math.hypot(p.x - (a.x + dx * t), p.y - (a.y + dy * t));
    }

    function polygonContains(points, p) {
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

    function boundaryArcAt(path, p) {
        let bestDistance = Infinity;
        let bestArc = 0;
        for (const edge of path.edges) {
            const d = distancePointSegment(p, edge.a, edge.b);
            if (d < bestDistance) {
                bestDistance = d;
                const t = Math.max(0, Math.min(1,
                    dot({x: p.x - edge.a.x, y: p.y - edge.a.y}, edge.tangent) / edge.length));
                bestArc = edge.start + t * edge.length;
            }
        }
        return {arc: bestArc, distance: bestDistance};
    }

    function forwardArcDiff(a, b, length) {
        let d = (b - a) % length;
        if (d < 0) {
            d += length;
        }
        return d;
    }

    function segmentIntersects(a, b, c, d) {
        const ab = {x: b.x - a.x, y: b.y - a.y};
        const cd = {x: d.x - c.x, y: d.y - c.y};
        const denominator = cross(ab, cd);
        if (Math.abs(denominator) < 1e-12) {
            return false;
        }
        const ac = {x: c.x - a.x, y: c.y - a.y};
        const t = cross(ac, cd) / denominator;
        const u = cross(ac, ab) / denominator;
        return t > 1e-9 && t < 1 - 1e-9 && u > 1e-9 && u < 1 - 1e-9;
    }

    function findSelfIntersection(points) {
        const n = points.length;
        for (let i = 0; i < n; i++) {
            const a = points[i];
            const b = points[(i + 1) % n];
            if (distance(a, b) <= 1e-12) {
                continue;
            }
            for (let j = i + 1; j < n; j++) {
                if (j === i || j === (i + 1) % n || i === (j + 1) % n) {
                    continue;
                }
                const c = points[j];
                const d = points[(j + 1) % n];
                if (distance(c, d) <= 1e-12) {
                    continue;
                }
                if (segmentIntersects(a, b, c, d)) {
                    return [i, j];
                }
            }
        }
        return null;
    }

    function minWheelClearance(path, circles) {
        let worst = Infinity;
        for (const wheel of circles) {
            for (const edge of path.edges) {
                worst = Math.min(worst, distancePointSegment(wheel, edge.a, edge.b) - wheel.r);
            }
        }
        return worst;
    }

    /** 圆弧采样点的半径误差与终点角度误差。 */
    function arcGeometryError(path) {
        let radial = 0;
        let endpoint = 0;
        for (const arc of path.arcs) {
            const circle = {x: arc.center.x, y: arc.center.y, r: arc.radius};
            for (let i = arc.startIndex; i <= arc.endIndex; i++) {
                radial = Math.max(radial, Math.abs(distance(path.points[i], circle) - arc.radius));
            }
            const last = arc.contactPoints[arc.contactPoints.length - 1];
            const endAngle = Math.atan2(last.y - circle.y, last.x - circle.x);
            endpoint = Math.max(endpoint,
                Math.abs(normalizeAngle(endAngle - (arc.startAngle + arc.sweep))));
        }
        return {radial: radial, endpoint: endpoint};
    }

    function pointContactOf(path, wheelId) {
        return path.pointContacts.find(function (c) { return c.wheelId === wheelId; }) || null;
    }

    function arcContactOf(path, wheelId) {
        return path.arcs.find(function (a) { return a.wheelId === wheelId; }) || null;
    }

    // ---------------------------------------------------------------- 场景数据

    /** 主动轮在左上、诱导轮在右上；中间负重轮高度可调。 */
    function makeTrack(road2Y) {
        return [
            {id: 'sprocket', x: -2, y: 1, r: 0.40},
            {id: 'road1', x: -1.5, y: -0.6, r: 0.35},
            {id: 'road2', x: 0, y: road2Y, r: 0.35},
            {id: 'road3', x: 1.5, y: -0.6, r: 0.35},
            {id: 'idler', x: 2, y: 1, r: 0.40}
        ];
    }

    function squareCircles() {
        return [
            {id: 'a', x: 0, y: 0, r: 0.2},
            {id: 'b', x: 2, y: 0, r: 0.2},
            {id: 'c', x: 2, y: 2, r: 0.2},
            {id: 'd', x: 0, y: 2, r: 0.2}
        ];
    }

    /** 三个等半径轮，中间轮抬起 0.2 m：下行程轮底点接触 + 回程轮顶圆弧。 */
    function liftedThreeCircles() {
        return [
            {id: 'left', x: -1, y: 0, r: 0.4},
            {id: 'lifted', x: 0, y: 0.2, r: 0.4},
            {id: 'right', x: 1, y: 0, r: 0.4}
        ];
    }

    /**
     * 退化轮序：前三个轮心严格共线，第四个把平面撑开使布局仍可定义。
     *
     * 这是“几个不可动轮在悬挂压到极限时落到一条直线上”的形态。轮心折线在
     * 下标 1 处回折，转角是 ±π；`sign * turn` 的符号只由共线的
     * 哪一侧决定，微小位姿噪声就会翻掉它。带路必须始终绕过该轮、把它包在环内。
     */
    function degenerateEndCapCircles(idlerY) {
        return [
            {id: 'sprocket', x: 0.0, y: 0.0, r: 0.4},
            {id: 'endcap', x: -1.45, y: 0.0, r: 0.4},
            {id: 'road', x: 4.55, y: 0.0, r: 0.4},
            {id: 'idler', x: 6.55, y: idlerY, r: 0.4}
        ];
    }

    const CASES = [
        {
            id: 'square',
            title: '正方形四轮（凸）',
            circles: squareCircles(),
            expectedConvex: true,
            contactId: 'a',
            contactKind: 'arc',
            expectedSweepDeg: 90
        },
        {
            id: 'flat-track',
            title: '平地五轮（凸）',
            circles: makeTrack(-0.6),
            expectedConvex: true,
            contactId: 'road2',
            contactKind: 'point',
            expectedContactPoint: {x: 0, y: -0.95},
            expectedContact: 0
        },
        {
            id: 'raised-2cm',
            title: '中间轮抬高 2 cm',
            circles: makeTrack(-0.58),
            expectedConvex: false,
            contactId: 'road2',
            contactKind: 'point',
            expectedContactPoint: {x: 0, y: -0.93},
            expectHullMiss: true
        },
        {
            id: 'raised-30cm',
            title: '中间轮抬高 30 cm',
            circles: makeTrack(-0.30),
            expectedConvex: false,
            contactId: 'road2',
            contactKind: 'point',
            expectedContactPoint: {x: 0, y: -0.65},
            expectHullMiss: true
        },
        {
            id: 'lowered-30cm',
            title: '中间轮降低 30 cm',
            circles: makeTrack(-0.90),
            expectedConvex: true,
            contactId: 'road2',
            contactKind: 'arc',
            expectedSweepDeg: 22.62,
            expectedContactLength: 0.1382
        },
        {
            id: 'lifted-equal-three',
            title: '等半径三轮回程二次接触',
            circles: liftedThreeCircles(),
            expectedConvex: false,
            contactId: 'lifted',
            contactKind: 'arc',
            expectLongerThanHull: true,
            expectDualContact: true
        },
        {
            id: 'endcap-degenerate',
            title: '退化轮序的左端帽',
            circles: degenerateEndCapCircles(-0.1),
            expectedConvex: false,
            contactId: 'endcap',
            contactKind: 'arc',
            expectHullMiss: false,
            expectAllCentersInside: true
        }
    ];

    // ---------------------------------------------------------------- 场景断言

    function runCase(caseDef) {
        const result = {
            case: caseDef,
            path: null,
            hull: null,
            assertions: [],
            metrics: {},
            passed: true
        };
        const check = assertOn(result);

        const path = TP.buildBeltPath(caseDef.circles);
        const hull = TE.buildEnvelope(caseDef.circles, {segments: 64});
        result.path = path;
        result.hull = hull;

        const pathN = path.linkCount(CONFIG.linkLength);
        const hullN = TE.estimateLinkCount(hull.length, CONFIG.linkLength);
        const kind = caseDef.contactKind || 'arc';
        const contact = kind === 'point'
            ? pointContactOf(path, caseDef.contactId)
            : arcContactOf(path, caseDef.contactId);
        const wheel = caseDef.circles.find(function (c) { return c.id === caseDef.contactId; });
        const pathMinWheelDistance = Math.min.apply(null, path.points.map(function (p) {
            return distance(p, wheel);
        }));
        const hullMinWheelDistance = Math.min.apply(null, hull.points.map(function (p) {
            return distance(p, wheel);
        }));
        const arcError = arcGeometryError(path);

        let sampleSpacingError = 0;
        let sampleArcError = 0;
        for (let i = 0; i < pathN; i++) {
            const sA = (i * path.length) / pathN;
            const sB = ((i + 1) * path.length) / pathN;
            const a = path.sampleAt(sA);
            const b = path.sampleAt(sB);
            const arcA = boundaryArcAt(path, a);
            const arcB = boundaryArcAt(path, b);
            sampleSpacingError = Math.max(sampleSpacingError,
                Math.abs(forwardArcDiff(arcA.arc, arcB.arc, path.length) - path.length / pathN));
            sampleArcError = Math.max(sampleArcError,
                Math.abs(forwardArcDiff(0, arcA.arc, path.length) - (sA % path.length)));
        }

        const centroid = path.points.reduce(function (acc, p) {
            return {x: acc.x + p.x / path.points.length, y: acc.y + p.y / path.points.length};
        }, {x: 0, y: 0});
        let normalOutwardMin = Infinity;
        for (let i = 0; i < 64; i++) {
            const p = path.sampleAt((i + 0.37) * path.length / 64);
            const radial = {x: p.x - centroid.x, y: p.y - centroid.y};
            normalOutwardMin = Math.min(normalOutwardMin, dot(p.normal, radial));
        }

        const selfIntersection = findSelfIntersection(path.points);
        const clearance = minWheelClearance(path, caseDef.circles);
        const closed = distance(path.sampleAt(0), path.sampleAt(path.length));
        const outsideCenters = caseDef.circles.filter(function (c) {
            return !path.containsPoint(c);
        });

        result.metrics = {
            pathLength: path.length,
            hullLength: hull.length,
            deltaLength: path.length - hull.length,
            pathN: pathN,
            hullN: hullN,
            hasContact: !!contact,
            contactSweepDeg: contact && contact.type === 'arc' ? contact.sweep * 180 / Math.PI : null,
            contactLength: contact ? contact.contactLength : null,
            pathMinWheelDistance: pathMinWheelDistance,
            hullMinWheelDistance: hullMinWheelDistance,
            arcRadialError: arcError.radial,
            arcEndpointError: arcError.endpoint,
            sampleSpacingError: sampleSpacingError,
            sampleArcError: sampleArcError,
            normalOutwardMin: normalOutwardMin,
            selfIntersection: selfIntersection,
            clearance: clearance,
            closedError: closed,
            isConvex: path.isConvex,
            area: path.signedArea,
            points: path.points.length,
            outsideCenters: outsideCenters.map(function (c) { return c.id; })
        };

        check('路径闭合', closed <= 1e-7, '');
        check('路径长度有限且为正', Number.isFinite(path.length) && path.length > 0,
            'L=' + path.length.toFixed(6));
        check('圆弧采样点落在圆上', arcError.radial <= 1e-9,
            'max=' + arcError.radial.toExponential(3));
        check('圆弧扫角收口在几何终点', arcError.endpoint <= 1e-9,
            'max=' + arcError.endpoint.toExponential(3));
        check('圆弧扫角不超过整圈', path.arcs.every(function (a) {
            return Math.abs(a.sweep) <= TAU + 1e-9;
        }), 'max=' + Math.max.apply(null, path.arcs.map(function (a) {
            return Math.abs(a.sweep);
        })).toFixed(4));
        check('凸性判定符合预期', path.isConvex === caseDef.expectedConvex,
            'actual=' + path.isConvex + ' expected=' + caseDef.expectedConvex);
        check('包络贴住指定轮子', pathMinWheelDistance <= wheel.r + 1e-6,
            'minDist=' + pathMinWheelDistance.toFixed(6) + ' r=' + wheel.r);
        check('路径无自交', selfIntersection == null,
            selfIntersection ? 'segments ' + selfIntersection.join('/') : '');
        check('路径不切入任何轮子', clearance >= -1e-4,
            'min clearance=' + clearance.toExponential(3));
        check('sampleAt 按等弧长分布', sampleSpacingError <= 1e-6,
            'max=' + sampleSpacingError.toExponential(3));
        check('sampleAt(s) 的几何位置对应弧长 s', sampleArcError <= 1e-6,
            'max=' + sampleArcError.toExponential(3));
        check('外法线整体朝外', normalOutwardMin >= -1e-6,
            'min dot=' + normalOutwardMin.toExponential(3));
        check('sampleAt 落在边界上', [0.13, 0.37, 0.61, 0.89].every(function (u) {
            const p = path.sampleAt(u * path.length);
            return path.distanceToBoundary(p) <= 1e-6;
        }), '');
        check('N 与最大耐久推导',
            pathN >= 1 && Math.abs(pathN * CONFIG.linkLength - path.length) <= CONFIG.linkLength / 2 + 1e-9,
            'N=' + pathN + ' maxHp=' + (pathN * CONFIG.linkDurability));
        check('接触记录存在', !!contact,
            contact
                ? 'type=' + contact.type + ' sweep=' + (contact.sweep * 180 / Math.PI).toFixed(2) + 'deg'
                : '');
        check('成员轮心都在包络内', outsideCenters.length === 0,
            outsideCenters.length === 0
                ? ''
                : 'outside=' + outsideCenters.map(function (c) { return c.id; }).join(','));

        if (caseDef.expectedContactType != null) {
            check('接触类型符合预期',
                contact && contact.type === caseDef.expectedContactType,
                contact ? 'actual=' + contact.type : '');
        }
        if (contact && contact.type === 'point') {
            check('点接触长度为 0',
                Math.abs(contact.contactLength) <= 1e-9,
                'length=' + contact.contactLength.toFixed(6));
            check('点接触落在轮子外缘',
                Math.abs(distance(contact.point, wheel) - wheel.r) <= 1e-6,
                'dist=' + distance(contact.point, wheel).toFixed(6) + ' r=' + wheel.r);
        }
        if (caseDef.expectedContactPoint != null) {
            const target = caseDef.expectedContactPoint;
            check('接触点坐标符合预期',
                contact && Math.abs(contact.point.x - target.x) <= 1e-6
                && Math.abs(contact.point.y - target.y) <= 1e-6,
                contact
                    ? 'actual=(' + contact.point.x.toFixed(4) + ',' + contact.point.y.toFixed(4) + ')'
                    : '');
        }
        if (caseDef.expectedSweepDeg != null) {
            check('接触圆弧扫角符合预期',
                contact && Math.abs(Math.abs(contact.sweep) * 180 / Math.PI - caseDef.expectedSweepDeg) <= 0.02,
                contact ? 'actual=' + (contact.sweep * 180 / Math.PI).toFixed(2) + 'deg' : '');
        }
        if (caseDef.expectedContactLength != null) {
            check('接触长度符合预期',
                contact && Math.abs(contact.contactLength - caseDef.expectedContactLength) <= 0.001,
                contact ? 'actual=' + contact.contactLength.toFixed(4) : '');
        }
        if (caseDef.expectedContact != null) {
            check('平地接触长度为零',
                contact && Math.abs(contact.contactLength) <= 1e-6,
                contact ? contact.contactLength.toFixed(6) : '');
        }
        if (caseDef.expectDualContact) {
            const point = pointContactOf(path, caseDef.contactId);
            const arc = arcContactOf(path, caseDef.contactId);
            check('同一轮在下行程点接触、回程圆弧各出现一次', !!point && !!arc,
                'point=' + !!point + ' arc=' + !!arc);
            check('下行程点接触在轮底',
                point && Math.abs(point.point.y - (wheel.y - wheel.r)) <= 1e-6,
                point ? 'contactY=' + point.point.y.toFixed(6) : '');
        }
        if (caseDef.expectLongerThanHull) {
            check('非凸路径长于凸包', path.length > hull.length + 1e-6,
                'path=' + path.length.toFixed(6) + ' hull=' + hull.length.toFixed(6));
            check('非凸路径改变了 N 或长度口径', pathN >= hullN,
                'pathN=' + pathN + ' hullN=' + hullN);
        }
        if (caseDef.expectHullMiss) {
            check('非凸路径长于凸包', path.length > hull.length + 1e-6,
                'path=' + path.length.toFixed(6) + ' hull=' + hull.length.toFixed(6));
            check('凸包没有贴住该轮',
                hullMinWheelDistance > wheel.r + 1e-3,
                'hullMinDist=' + hullMinWheelDistance.toFixed(6) + ' r=' + wheel.r);
            check('非凸路径改变了 N 或长度口径', pathN >= hullN,
                'pathN=' + pathN + ' hullN=' + hullN);
        }
        if (caseDef.expectedConvex) {
            check('凸布局下与凸包周长一致',
                Math.abs(path.length - hull.length) <= 0.02,
                'path=' + path.length.toFixed(6) + ' hull=' + hull.length.toFixed(6));
        }

        return result;
    }

    // ---------------------------------------------------------------- 单元用例

    /** Java 侧用例的对应实现。每个用例独立，抛错即判失败。 */
    const UNIT_TESTS = [
        {
            id: 'global-contact-regressions',
            title: '全轮组接触：共线、抬轮、半径、非闭合跨段、旋转',
            run: function (check) {
                const row = heights => heights.map((y,x)=>({id:x,x,y,r:.4}));
                const layouts = [row([0,0,0,0,0]), row([0,0,.2,0,0]), row([0,.2,0,.2,0]),
                    row([0,.2,.2,0,0]), row([0,0,1,0,0]), row([0,0,3,0,0]), row([0,0,10,0,0]), row([0,.2,0,-.2,0]),
                    [{x:-1,y:0,r:.3},{x:0,y:.1,r:.6},{x:1,y:0,r:.3}],
                    [{x:-3,y:0,r:.4},{x:-1,y:.8,r:.4},{x:1,y:.8,r:.4},{x:3,y:0,r:.4},{x:0,y:.3,r:.2}]];
                layouts.forEach((circles,index)=>{
                    const path=TP.buildBeltPath(circles,{clockwise:false});
                    check('布局 '+index+' 不穿轮',minWheelClearance(path,circles)>=-1e-4);
                    check('布局 '+index+' 包住所有轮心',circles.every(c=>path.containsPoint(c)));
                    check('布局 '+index+' 圆弧同向',path.arcs.every(a=>a.sweep>=0));
                });
                const circles=row([0,0,.2,0,0]),baseline=TP.buildBeltPath(circles,{clockwise:false});
                check('底部点接触',baseline.pointContacts.some(p=>p.wheelIndex===2));
                check('顶部圆弧',baseline.arcs.some(a=>a.wheelIndex===2));
                check('肩轮小包角',baseline.arcs.filter(a=>a.wheelIndex===1||a.wheelIndex===3)
                    .every(a=>Math.abs(a.sweep)<Math.PI/4));
                for(let degrees=0;degrees<360;degrees+=30) {
                    const angle=degrees*Math.PI/180;
                    const rotated=circles.map(c=>({...c,x:c.x*Math.cos(angle)-c.y*Math.sin(angle),
                        y:c.x*Math.sin(angle)+c.y*Math.cos(angle)}));
                    const path=TP.buildBeltPath(rotated,{clockwise:false});
                    check('旋转 '+degrees+' 周长不变',Math.abs(path.length-baseline.length)<1e-5);
                    check('旋转 '+degrees+' 保留双接触',path.pointContacts.some(p=>p.wheelIndex===2)
                        &&path.arcs.some(a=>a.wheelIndex===2));
                }
            }
        },
        {
            id: 'square-quarter-arcs',
            title: '正方形：四条 90° 圆弧',
            run: function (check) {
                const path = TP.buildBeltPath(squareCircles());
                check('凸包络', path.isConvex);
                check('没有点接触', path.pointContacts.length === 0);
                check('四条圆弧', path.arcs.length === 4, 'n=' + path.arcs.length);
                for (const arc of path.arcs) {
                    check('扫角 = π/2', Math.abs(Math.abs(arc.sweep) - Math.PI / 2) <= 1e-5,
                        'sweep=' + arc.sweep);
                    check('接触长度 = r·π/2', Math.abs(arc.contactLength - 0.2 * Math.PI / 2) <= 1e-5,
                        'len=' + arc.contactLength);
                }
                check('周长 = 8 + 2πr',
                    Math.abs(path.length - (8 + 2 * Math.PI * 0.2)) <= 0.005,
                    'L=' + path.length.toFixed(6));
                check('逆时针', path.signedArea > 0);
            }
        },
        {
            id: 'flat-track',
            title: '平地五轮：中间轮点接触、与凸包等长',
            run: function (check) {
                const circles = makeTrack(-0.6);
                const path = TP.buildBeltPath(circles);
                const contact = pointContactOf(path, 'road2');
                check('凸包络', path.isConvex);
                check('只有一个点接触', path.pointContacts.length === 1,
                    'n=' + path.pointContacts.length);
                check('接触点在 (0, -0.95)',
                    contact && Math.abs(contact.point.x) <= 1e-7
                    && Math.abs(contact.point.y + 0.95) <= 1e-7,
                    contact ? '(' + contact.point.x + ', ' + contact.point.y + ')' : '');
                check('中间轮没有圆弧', arcContactOf(path, 'road2') == null);
                check('周长与圆盘凸包一致',
                    Math.abs(path.length - TE.buildEnvelope(circles, {segments: 64}).length) <= 0.01,
                    'L=' + path.length.toFixed(6));
                check('N = round(L / 0.2)', path.linkCount(0.2) === 64,
                    'N=' + path.linkCount(0.2));
            }
        },
        {
            id: 'lifted-middle',
            title: '顶起的中间轮：凹点接触 + 回程圆弧',
            run: function (check) {
                const circles = liftedThreeCircles();
                const path = TP.buildBeltPath(circles);
                const contact = pointContactOf(path, 'lifted');
                check('非凸', path.isConvex === false);
                check('3 个及以上不反转输入', path.reversedFromInput === false);
                check('下行程点接触在 (0, -0.2)',
                    contact && Math.abs(contact.point.x) <= 1e-7
                    && Math.abs(contact.point.y + 0.2) <= 1e-7,
                    contact ? '(' + contact.point.x + ', ' + contact.point.y + ')' : '');
                check('同一轮在回程还有圆弧', arcContactOf(path, 'lifted') != null);
                check('两端轮也是圆弧',
                    arcContactOf(path, 'left') != null && arcContactOf(path, 'right') != null);
                check('不切入轮子', minWheelClearance(path, circles) >= -1e-4);
                check('无自交', findSelfIntersection(path.points) == null);
                check('轮心都在包络内', circles.every(function (c) {
                    return path.containsPoint(c);
                }));
            }
        },
        {
            id: 'lifted-double-contact',
            title: '回程闭合边穿过抬起轮：同一轮两个顶点',
            run: function (check) {
                const circles = liftedThreeCircles();
                const path = TP.buildBeltPath(circles);
                check('顶点数 4（追加了一个回程接触）', path.vertices.length === 4,
                    'n=' + path.vertices.length);
                check('点接触 1 个', path.pointContacts.length === 1);
                check('圆弧 3 段', path.arcs.length === 3);
                check('点接触与圆弧指向同一轮',
                    pointContactOf(path, 'lifted') != null && arcContactOf(path, 'lifted') != null);
                check('sampleAt 可用',
                    Number.isFinite(path.sampleAt(path.length * 0.37).x));
                check('不切入轮子', minWheelClearance(path, circles) >= -1e-4);
                check('无自交', findSelfIntersection(path.points) == null);
            }
        },
        {
            id: 'endcap-stability',
            title: '退化轮序：端帽不随位姿抖动翻转',
            run: function (check) {
                // 下标 1 的端帽处轮心折线回折（|turn| ≈ π）。第四个轮子的高度
                // 决定符号落在回折的哪一侧，两种结果必须给出同一套拓扑。
                for (const idlerY of [-0.15, -0.1, -0.05, 0.05, 0.1, 0.15]) {
                    const circles = degenerateEndCapCircles(idlerY);
                    const path = TP.buildBeltPath(circles);
                    const vertex = path.vertices[1];
                    check('端帽保持凸角（idlerY=' + idlerY + '）',
                        vertex.type === 'convex',
                        'type=' + vertex.type + ' turn=' + (vertex.turn * 180 / Math.PI).toFixed(2) + 'deg');
                    check('端帽有圆弧（idlerY=' + idlerY + '）',
                        arcContactOf(path, 'endcap') != null);
                    check('端帽是端帽而不是擦点（idlerY=' + idlerY + '）',
                        Math.abs(vertex.turn) > Math.PI * 0.75,
                        '|turn|=' + (Math.abs(vertex.turn) * 180 / Math.PI).toFixed(2) + 'deg');
                    check('所有轮心都在环内（idlerY=' + idlerY + '）',
                        circles.every(function (c) { return path.containsPoint(c); }),
                        circles.filter(function (c) { return !path.containsPoint(c); })
                            .map(function (c) { return c.id; }).join(','));
                    check('无自交（idlerY=' + idlerY + '）',
                        findSelfIntersection(path.points) == null);
                }
            }
        },
        {
            id: 'triangle',
            title: '三角形三轮：地面行程直接连通',
            run: function (check) {
                const circles = [
                    {id: 'sprocket', x: 0, y: 1, r: 0.4},
                    {id: 'left', x: -1, y: 0, r: 0.4},
                    {id: 'right', x: 1, y: 0, r: 0.4}
                ];
                const path = TP.buildBeltPath(circles);
                check('凸包络', path.isConvex);
                check('没有点接触', path.pointContacts.length === 0);
                check('三段圆弧', path.arcs.length === 3);
                check('不切入轮子', minWheelClearance(path, circles) >= -1e-4);
                check('无自交', findSelfIntersection(path.points) == null);
            }
        },
        {
            id: 'anchored-order',
            title: '同一组轮心、不同锚定顺序给出不同包络',
            run: function (check) {
                const left = {id: 'left', x: -1, y: 0, r: 0.4};
                const middle = {id: 'middle', x: 0, y: 0.2, r: 0.4};
                const right = {id: 'right', x: 1, y: 0, r: 0.4};
                const lifted = TP.buildBeltPath([left, middle, right]);
                const triangleOrder = TP.buildBeltPath([left, right, middle]);

                check('抬起顺序是非凸的', lifted.isConvex === false);
                check('三角顺序是凸的', triangleOrder.isConvex === true);
                check('抬起顺序里中间轮有点接触', pointContactOf(lifted, 'middle') != null);
                check('三角顺序里中间轮没有点接触', pointContactOf(triangleOrder, 'middle') == null);
                check('三角顺序里中间轮是圆弧', arcContactOf(triangleOrder, 'middle') != null);
                check('两条包络长度不同', Math.abs(lifted.length - triangleOrder.length) > 1e-6,
                    'lifted=' + lifted.length.toFixed(6) + ' triangle=' + triangleOrder.length.toFixed(6));
                check('两条包络面积不同',
                    Math.abs(lifted.signedArea - triangleOrder.signedArea) > 1e-6);
            }
        },
        {
            id: 'reversed-input',
            title: '反向输入：周长不变、绕向跟随锚定顺序',
            run: function (check) {
                const forward = makeTrack(-0.30);
                const backward = forward.slice().reverse();
                const a = TP.buildBeltPath(forward);
                const b = TP.buildBeltPath(backward);

                check('3 个及以上不反转输入',
                    a.reversedFromInput === false && b.reversedFromInput === false);
                check('周长相同', Math.abs(a.length - b.length) <= 1e-6,
                    'a=' + a.length.toFixed(6) + ' b=' + b.length.toFixed(6));
                check('正向输入逆时针', a.signedArea > 0);
                check('反向输入顺时针', b.signedArea < 0);
                check('节数相同', a.linkCount(0.2) === b.linkCount(0.2));
            }
        },
        {
            id: 'arc-endpoint',
            title: '圆弧扫角落在几何终点',
            run: function (check) {
                const path = TP.buildBeltPath(makeTrack(-0.30));
                const error = arcGeometryError(path);
                check('每个圆弧的终点角度 = startAngle + sweep',
                    error.endpoint <= 1e-9, 'max=' + error.endpoint.toExponential(3));
                check('每个圆弧采样点都在圆上',
                    error.radial <= 1e-9, 'max=' + error.radial.toExponential(3));
            }
        },
        {
            id: 'arc-length-parameterization',
            title: 'sampleAt 是弧长参数化',
            run: function (check) {
                const path = TP.buildBeltPath(makeTrack(-0.30));
                const length = path.length;
                const count = path.linkCount(0.2);
                check('sampleAt(0) == sampleAt(L)',
                    distance(path.sampleAt(0), path.sampleAt(length)) <= 1e-7);
                let maxSpacingError = 0;
                for (let i = 0; i < count; i++) {
                    const a = path.sampleAt(i * length / count);
                    const b = path.sampleAt((i + 1) * length / count);
                    const arcA = boundaryArcAt(path, a);
                    const arcB = boundaryArcAt(path, b);
                    maxSpacingError = Math.max(maxSpacingError,
                        Math.abs(forwardArcDiff(arcA.arc, arcB.arc, length) - length / count));
                }
                check('等弧长分布', maxSpacingError <= 1e-6,
                    'max=' + maxSpacingError.toExponential(3));
                let unitError = 0;
                for (let i = 0; i < 64; i++) {
                    const sample = path.sampleAt((i + 0.37) * length / 64);
                    unitError = Math.max(unitError,
                        Math.abs(Math.hypot(sample.tangent.x, sample.tangent.y) - 1));
                    unitError = Math.max(unitError,
                        Math.abs(Math.hypot(sample.normal.x, sample.normal.y) - 1));
                    unitError = Math.max(unitError, Math.abs(dot(sample.tangent, sample.normal)));
                    unitError = Math.max(unitError, path.distanceToBoundary(sample));
                }
                check('切线与法线单位正交且落在边界上', unitError <= 1e-6,
                    'max=' + unitError.toExponential(3));
            }
        },
        {
            id: 'no-cut',
            title: '各高度下不切入轮子、不自交',
            run: function (check) {
                for (const road2Y of [-0.6, -0.58, -0.30, -0.90]) {
                    const circles = makeTrack(road2Y);
                    const path = TP.buildBeltPath(circles);
                    check('不切入轮子（road2Y=' + road2Y + '）',
                        minWheelClearance(path, circles) >= -1e-4);
                    check('无自交（road2Y=' + road2Y + '）',
                        findSelfIntersection(path.points) == null);
                }
            }
        },
        {
            id: 'two-equal',
            title: '两轮等半径：stadium',
            run: function (check) {
                const circles = [
                    {id: 'left', x: -1.5, y: 0, r: 1},
                    {id: 'right', x: 1.5, y: 0, r: 1}
                ];
                const path = TP.buildBeltPath(circles);
                check('凸包络', path.isConvex);
                check('没有点接触', path.pointContacts.length === 0);
                check('两段圆弧', path.arcs.length === 2);
                for (const arc of path.arcs) {
                    check('扫角 = π', Math.abs(Math.abs(arc.sweep) - Math.PI) <= 1e-6,
                        'sweep=' + arc.sweep);
                    check('接触长度 = πr', Math.abs(arc.contactLength - Math.PI) <= 1e-6);
                }
                check('周长 = 2πr + 2D', Math.abs(path.length - (2 * Math.PI + 6)) <= 0.005,
                    'L=' + path.length.toFixed(6));
                check('与圆盘凸包一致',
                    Math.abs(path.length - TE.buildEnvelope(circles, {segments: 2048}).length) <= 0.005);
            }
        },
        {
            id: 'two-unequal',
            title: '两轮不等半径：外公切线',
            run: function (check) {
                const circles = [
                    {id: 'left', x: -2, y: 0, r: 2},
                    {id: 'right', x: 2, y: 0, r: 1}
                ];
                const path = TP.buildBeltPath(circles);
                const dr = 1;
                const d = 4;
                const phi = Math.acos(dr / d);
                const expected = 2 * Math.sqrt(d * d - dr * dr)
                    + 2 * (TAU - 2 * phi) + 1 * (2 * phi);
                check('凸包络', path.isConvex);
                check('两段圆弧', path.arcs.length === 2);
                check('周长 = 外公切线 + 两段圆弧',
                    Math.abs(path.length - expected) <= 0.005,
                    'actual=' + path.length.toFixed(6) + ' expected=' + expected.toFixed(6));
            }
        },
        {
            id: 'two-contained',
            title: '两轮互相包含：退化为外圆',
            run: function (check) {
                const circles = [
                    {id: 'outer', x: 0, y: 0, r: 3},
                    {id: 'inner', x: 0.5, y: 0, r: 1}
                ];
                const path = TP.buildBeltPath(circles);
                check('凸包络', path.isConvex);
                check('一段整圈圆弧', path.arcs.length === 1);
                check('周长 = 2πR', Math.abs(path.length - TAU * 3) <= 0.01,
                    'L=' + path.length.toFixed(6));
                check('面积 = πR²', Math.abs(Math.abs(path.signedArea) - Math.PI * 9) <= 0.1,
                    'area=' + Math.abs(path.signedArea).toFixed(4));
            }
        },
        {
            id: 'two-reversed',
            title: '两轮顺时针输入：规范化为逆时针',
            run: function (check) {
                const forward = [
                    {id: 'left', x: -2, y: 0, r: 2},
                    {id: 'right', x: 2, y: 0, r: 1}
                ];
                const backward = [forward[1], forward[0]];
                const a = TP.buildBeltPath(forward);
                const b = TP.buildBeltPath(backward);
                check('正序不反转', a.reversedFromInput === false);
                check('逆序被标记为反转', b.reversedFromInput === true);
                check('周长相同', Math.abs(a.length - b.length) <= 1e-9);
            }
        },
        {
            id: 'convex-fallback',
            title: '全共线轮组的凸包回退',
            run: function (check) {
                const circles = [
                    {id: 'a', x: 0, y: 0, r: 0.4},
                    {id: 'b', x: 1.2, y: 0, r: 0.4},
                    {id: 'c', x: 2.4, y: 0, r: 0.4}
                ];
                const path = TP.convexFallback(circles);
                check('没有圆弧', path.arcs.length === 0);
                check('没有点接触', path.pointContacts.length === 0);
                check('凸包络', path.isConvex);
                check('周长 = 圆盘凸包',
                    Math.abs(path.length - TE.buildEnvelope(circles, {segments: 128}).length) <= 0.01,
                    'L=' + path.length.toFixed(6));
                check('sampleAt 可用',
                    Number.isFinite(path.sampleAt(0).x)
                    && Number.isFinite(path.sampleAt(path.length * 0.37).x));
                check('节数可用', path.linkCount(0.2) > 0);
            }
        },
        {
            id: 'overlapping-wheels',
            title: '悬挂压缩导致的轮心重叠可被识别',
            run: function (check) {
                check('重叠轮对被识别', TP.hasOverlappingWheels([
                    {id: 'a', x: 0, y: 0, r: 0.5},
                    {id: 'b', x: 0.4, y: 0, r: 0.5}
                ]) === true);
                check('外离轮对不被误判', TP.hasOverlappingWheels([
                    {id: 'a', x: 0, y: 0, r: 0.5},
                    {id: 'b', x: 1.2, y: 0, r: 0.5}
                ]) === false);
            }
        }
    ];

    function assertOn(report) {
        return function (name, ok, detail) {
            report.assertions.push({
                name: name,
                ok: !!ok,
                detail: detail == null ? '' : String(detail)
            });
            if (!ok) {
                report.passed = false;
            }
        };
    }

    function newReport(id, title) {
        return {id: id, title: title, assertions: [], passed: true};
    }

    function runUnit(def) {
        const report = newReport(def.id, def.title);
        const check = assertOn(report);
        try {
            def.run(check);
        } catch (error) {
            check('用例执行不抛异常', false, error && error.message ? error.message : String(error));
        }
        return report;
    }

    // ---------------------------------------------------------------- 自检

    function runSelfTests() {
        const report = newReport('self', '自检 / 退化输入');
        const check = assertOn(report);

        function throws(fn) {
            try {
                fn();
            } catch (error) {
                return true;
            }
            return false;
        }

        check('少于两个轮子抛错', throws(function () {
            TP.buildBeltPath([{x: 0, y: 0, r: 1}]);
        }));
        check('重叠轮盘抛错', throws(function () {
            TP.buildBeltPath([
                {x: 0, y: 0, r: 1},
                {x: 1, y: 0, r: 1},
                {x: 2, y: 0, r: 1}
            ]);
        }));
        check('非重叠共线轮正常构建', (function () {
            const path = TP.buildBeltPath([
                {x: 0, y: 0, r: .4}, {x: 1, y: 0, r: .4}, {x: 2, y: 0, r: .4}
            ]);
            return path.arcs.length === 2 && path.pointContacts.length === 1;
        })());
        check('零半径抛错', throws(function () {
            TP.buildBeltPath([
                {x: 0, y: 0, r: 1},
                {x: 1, y: 0, r: 0},
                {x: 0, y: 1, r: 1}
            ]);
        }));
        check('相邻轮心重合抛错', throws(function () {
            TP.buildBeltPath([
                {x: 0, y: 0, r: 0.4},
                {x: 0, y: 0, r: 0.4},
                {x: 1, y: 1, r: 0.4}
            ]);
        }));
        check('turnEpsilon 必须为正', throws(function () {
            TP.buildBeltPath(squareCircles(), {turnEpsilon: 0});
        }));
        check('convexFallback 少于两轮抛错', throws(function () {
            TP.convexFallback([{x: 0, y: 0, r: 1}]);
        }));

        // 2 轮是最小合法轮组：两个轮子走两圆外部凸包（stadium）。
        const two = TP.buildBeltPath([
            {id: 'left', x: -1.5, y: 0, r: 1},
            {id: 'right', x: 1.5, y: 0, r: 1}
        ]);
        check('两个轮子可构建', two.arcs.length === 2 && two.length > 0);

        const cw = squareCircles().slice().reverse();
        const cwPath = TP.buildBeltPath(cw);
        const ccwPath = TP.buildBeltPath(squareCircles());
        check('顺时针输入得到同一周长',
            Math.abs(cwPath.length - ccwPath.length) <= 1e-6,
            'cw=' + cwPath.length.toFixed(6) + ' ccw=' + ccwPath.length.toFixed(6));
        check('3 个及以上两个方向都不标记反转',
            cwPath.reversedFromInput === false && ccwPath.reversedFromInput === false);
        check('反向输入的环向相反',
            cwPath.signedArea < 0 && ccwPath.signedArea > 0);

        return report;
    }

    // ---------------------------------------------------------------- 汇总

    function runAll() {
        const cases = CASES.map(runCase);
        const unitTests = UNIT_TESTS.map(runUnit);
        const selfTests = runSelfTests();

        let assertions = 0;
        let failed = 0;
        for (const report of cases.concat(unitTests, [selfTests])) {
            assertions += report.assertions.length;
            failed += report.assertions.filter(function (a) { return !a.ok; }).length;
        }

        return {
            generatedAt: new Date().toISOString(),
            config: CONFIG,
            cases: cases,
            unitTests: unitTests,
            selfTests: selfTests,
            summary: {
                cases: cases.length,
                casesPassed: cases.filter(function (r) { return r.passed; }).length,
                unitTests: unitTests.length,
                unitTestsPassed: unitTests.filter(function (r) { return r.passed; }).length,
                selfTestsTotal: selfTests.assertions.length,
                selfTestsPassed: selfTests.assertions.filter(function (a) { return a.ok; }).length,
                assertions: assertions,
                failed: failed,
                ok: failed === 0
            }
        };
    }

    const api = {
        CONFIG: CONFIG,
        CASES: CASES,
        UNIT_TESTS: UNIT_TESTS,
        makeTrack: makeTrack,
        squareCircles: squareCircles,
        liftedThreeCircles: liftedThreeCircles,
        degenerateEndCapCircles: degenerateEndCapCircles,
        runCase: runCase,
        runAll: runAll
    };
    if (typeof module !== 'undefined' && module.exports) module.exports = api;
    global.TrackPathTests = api;

    if (typeof module !== 'undefined' && require.main === module) {
        const report = runAll();
        const s = report.summary;
        console.log('TrackPath2D');
        console.log('  场景: ' + s.casesPassed + '/' + s.cases
            + '   用例: ' + s.unitTestsPassed + '/' + s.unitTests
            + '   断言: ' + (s.assertions - s.failed) + '/' + s.assertions);
        for (const result of report.cases) {
            const m = result.metrics;
            console.log('  [' + (result.passed ? 'PASS' : 'FAIL') + '] '
                + result.case.id.padEnd(20)
                + ' L=' + m.pathLength.toFixed(4)
                + ' hull=' + m.hullLength.toFixed(4)
                + ' convex=' + m.isConvex
                + ' contact=' + (m.contactSweepDeg == null
                    ? (m.hasContact ? 'point' : '—')
                    : m.contactSweepDeg.toFixed(2) + 'deg/' + m.contactLength.toFixed(4) + 'm')
                + ' N=' + m.pathN + '/' + m.hullN
                + ' outside=[' + m.outsideCenters.join(',') + ']');
            for (const check of result.assertions.filter(function (a) { return !a.ok; })) {
                console.log('       FAIL ' + check.name + ' :: ' + check.detail);
            }
        }
        for (const result of report.unitTests) {
            if (!result.passed) {
                console.log('  [FAIL] ' + result.id);
                for (const check of result.assertions.filter(function (a) { return !a.ok; })) {
                    console.log('       ' + check.name + ' :: ' + check.detail);
                }
            }
        }
        for (const check of report.selfTests.assertions) {
            if (!check.ok) {
                console.log('  [FAIL] self :: ' + check.name + ' :: ' + check.detail);
            }
        }
        console.log('  RESULT: ' + (s.ok ? 'PASS' : 'FAIL'));
        process.exitCode = s.ok ? 0 : 1;
    }
})(typeof globalThis !== 'undefined' ? globalThis : this);
