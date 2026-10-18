/*
 * 履带包络 2D 测试套件。
 *
 * 同一份用例同时服务两种验证通道：
 *   - Node：`node track-envelope-tests.js` 输出断言与退出码；
 *   - 浏览器：visualize.js 读取 runAll() 的结果并画图，截图人工复核。
 *
 * 断言分三层：
 *   1. 解析解：单圆、等半径双圆、不等半径双圆、共线三圆、包含圆；
 *   2. 独立性质：凸性、逆时针、采样点包含、支撑函数上下界；
 *   3. 随机模糊：300 组随机圆，检查同一组性质。
 */
(function (global) {
    'use strict';

    const TE = global.TrackEnvelope
        || (typeof require !== 'undefined' ? require('./track-envelope.js') : null);
    if (!TE) {
        throw new Error('TrackEnvelope is not loaded');
    }

    const CONFIG = {
        segments: 64,
        denseSegments: 2048,
        linkLength: 0.2,
        linkDurability: 20,
        fuzzCases: 300,
        fuzzSeed: 0x5EED1234,
        supportDirections: 256
    };

    function distance(a, b) {
        return Math.hypot(a.x - b.x, a.y - b.y);
    }

    function dot(a, b) {
        return a.x * b.x + a.y * b.y;
    }

    function cross(a, b, c) {
        return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
    }

    /** 两个外离/外切圆盘的凸包周长解析解。 */
    function twoCirclePerimeter(r1, r2, d) {
        const dr = r1 - r2;
        if (d <= Math.abs(dr) + 1e-12) {
            throw new RangeError('one circle must not contain the other');
        }
        const phi = Math.acos(dr / d);
        return 2 * Math.sqrt(d * d - dr * dr)
            + r1 * (2 * Math.PI - 2 * phi)
            + r2 * (2 * phi);
    }

    const CASES = [
        {
            id: 'single',
            title: '单圆 / 单轮',
            subtitle: '最小包络：64 段圆内接折线',
            circles: [{id: 'sprocket', x: 0, y: 0, r: 1}],
            expectedPerimeter: 2 * Math.PI,
            expectedHullVertices: 64
        },
        {
            id: 'two-equal',
            title: '等半径双圆',
            subtitle: '标准 stadium：两段半圆 + 两条直边',
            circles: [
                {id: 'left', x: -1.5, y: 0, r: 1},
                {id: 'right', x: 1.5, y: 0, r: 1}
            ],
            expectedPerimeter: 2 * Math.PI + 2 * 3
        },
        {
            id: 'two-unequal',
            title: '不等半径双圆',
            subtitle: '外公切线倾斜，两端圆弧不是半圆',
            circles: [
                {id: 'large', x: -2, y: 0, r: 2},
                {id: 'small', x: 2, y: 0, r: 1}
            ],
            expectedPerimeter: twoCirclePerimeter(2, 1, 4)
        },
        {
            id: 'three-collinear',
            title: '共线三圆',
            subtitle: '中间轮不应改变“直线段 + 两端弧”',
            circles: [
                {id: 'left', x: -2, y: 0, r: 1},
                {id: 'middle', x: 0, y: 0, r: 1},
                {id: 'right', x: 2, y: 0, r: 1}
            ],
            expectedPerimeter: 2 * Math.PI + 2 * 4,
            containedIds: ['middle'],
            middleCenter: {x: 0, y: 0}
        },
        {
            id: 'contained',
            title: '包含圆',
            subtitle: '小轮完全在大轮内，包络只应保留大轮',
            circles: [
                {id: 'outer', x: 0, y: 0, r: 3},
                {id: 'inner', x: 0.8, y: 0, r: 1}
            ],
            expectedPerimeter: 2 * Math.PI * 3,
            containedIds: ['inner'],
            outerId: 'outer'
        },
        {
            id: 'big-center-inside',
            title: '大轮圆心在圆心凸包内',
            subtitle: '关键反例：圆心凸包会切掉大轮的上/下圆盘',
            circles: [
                {id: 'left-small', x: -1, y: 0, r: 0.25},
                {id: 'big', x: 0, y: 0, r: 1.2},
                {id: 'right-small', x: 1, y: 0, r: 0.25}
            ],
            centerHullNegative: {
                direction: {x: 0, y: 1},
                maxExpectedSupport: 0.5
            }
        },
        {
            id: 'overlap',
            title: '重叠三圆',
            subtitle: '轮子重叠/包含时仍应得到光滑外凸包',
            circles: [
                {id: 'left', x: -0.6, y: 0, r: 1},
                {id: 'right', x: 0.6, y: 0, r: 1},
                {id: 'top', x: 0, y: 0.6, r: 0.9}
            ]
        },
        {
            id: 'triangle-unequal',
            title: '不等半径三角布局',
            subtitle: '三个圆各自贡献一段外轮廓',
            circles: [
                {id: 'a', x: -2, y: 0, r: 1},
                {id: 'b', x: 1.5, y: -0.5, r: 1.5},
                {id: 'c', x: 0.2, y: 1.8, r: 0.8}
            ]
        },
        {
            id: 'realistic',
            title: '真实履带布局',
            subtitle: '1 主动轮 + 1 诱导轮 + 4 负重轮',
            circles: [
                {id: 'sprocket', x: 0, y: 0.55, r: 0.40},
                {id: 'idler', x: -0.95, y: 0.45, r: 0.34},
                {id: 'road-1', x: -0.70, y: -0.45, r: 0.32},
                {id: 'road-2', x: -0.15, y: -0.55, r: 0.32},
                {id: 'road-3', x: 0.40, y: -0.45, r: 0.32},
                {id: 'road-4', x: 0.90, y: 0.10, r: 0.30}
            ]
        }
    ];

    function makeResult(caseDef) {
        return {
            case: caseDef,
            envelope: null,
            dense: null,
            assertions: [],
            metrics: {},
            passed: true
        };
    }

    function addCheck(result, name, ok, detail) {
        result.assertions.push({name: name, ok: !!ok, detail: detail == null ? '' : String(detail)});
        if (!ok) {
            result.passed = false;
        }
    }

    function validateConvex(points) {
        if (points.length < 3) {
            return true;
        }
        for (let i = 0; i < points.length; i++) {
            const a = points[i];
            const b = points[(i + 1) % points.length];
            const c = points[(i + 2) % points.length];
            if (cross(a, b, c) < -1e-7) {
                return false;
            }
        }
        return true;
    }

    function supportExtremes(envelope, circles) {
        let maxDeficit = 0;
        let maxExcess = 0;
        for (let i = 0; i < CONFIG.supportDirections; i++) {
            const angle = (i / CONFIG.supportDirections) * Math.PI * 2;
            const direction = {x: Math.cos(angle), y: Math.sin(angle)};
            const trueSupport = TE.circleSupport(circles, direction);
            const hullSupport = envelope.support(direction);
            maxDeficit = Math.max(maxDeficit, trueSupport - hullSupport);
            maxExcess = Math.max(maxExcess, hullSupport - trueSupport);
        }
        return {maxDeficit: maxDeficit, maxExcess: maxExcess};
    }

    /**
     * 把边界上的点投影回折线，独立于 sampleAt 的 s 字段计算它的弧长。
     * 用于验证 sampleAt(s) 确实落在弧长 s 处，而不是只相信返回的 s。
     */
    function boundaryArcAt(envelope, p) {
        let bestDistance = Infinity;
        let bestArc = 0;
        for (const edge of envelope.edges) {
            const dx = edge.b.x - edge.a.x;
            const dy = edge.b.y - edge.a.y;
            const lenSq = dx * dx + dy * dy;
            let t = 0;
            if (lenSq > 1e-15) {
                t = ((p.x - edge.a.x) * dx + (p.y - edge.a.y) * dy) / lenSq;
                t = Math.max(0, Math.min(1, t));
            }
            const qx = edge.a.x + dx * t;
            const qy = edge.a.y + dy * t;
            const d = Math.hypot(p.x - qx, p.y - qy);
            if (d < bestDistance) {
                bestDistance = d;
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

    function runCase(caseDef) {
        const result = makeResult(caseDef);
        const circles = caseDef.circles;

        const envelope = TE.buildEnvelope(circles, {segments: CONFIG.segments});
        const dense = TE.buildEnvelope(circles, {segments: CONFIG.denseSegments});
        result.envelope = envelope;
        result.dense = dense;

        const maxRadius = circles.reduce(function (m, c) {
            return Math.max(m, c.r);
        }, 0);
        const supportTolerance = maxRadius
            * (1 - Math.cos(Math.PI / CONFIG.segments)) * 2.5 + 1e-6;
        const support = supportExtremes(envelope, circles);
        const denseRelError = dense.length > 0
            ? Math.abs(envelope.length - dense.length) / dense.length
            : 0;
        const analytic = caseDef.expectedPerimeter == null ? null : caseDef.expectedPerimeter;
        const analyticRelError = analytic == null
            ? null
            : Math.abs(envelope.length - analytic) / analytic;

        const N = TE.estimateLinkCount(envelope.length, CONFIG.linkLength);
        const totalDurability = TE.maxDurability(CONFIG.linkDurability, N);

        let sampleSpacingError = 0;
        let sampleArcError = 0;
        for (let i = 0; i < N; i++) {
            const sA = (i * envelope.length) / N;
            const sB = ((i + 1) * envelope.length) / N;
            const a = envelope.sampleAt(sA);
            const b = envelope.sampleAt(sB);
            const arcA = boundaryArcAt(envelope, a);
            const arcB = boundaryArcAt(envelope, b);
            sampleSpacingError = Math.max(
                sampleSpacingError,
                Math.abs(forwardArcDiff(arcA.arc, arcB.arc, envelope.length) - envelope.length / N)
            );
            sampleArcError = Math.max(
                sampleArcError,
                Math.abs(forwardArcDiff(0, arcA.arc, envelope.length) - (sA % envelope.length))
            );
        }

        const centroid = envelope.centroid;
        let normalOutwardMin = Infinity;
        let sampleBoundaryError = 0;
        let tangentUnitError = 0;
        for (let i = 0; i < CONFIG.segments; i++) {
            const p = envelope.sampleAt(((i + 0.37) * envelope.length) / CONFIG.segments);
            const tangentLength = Math.hypot(p.tangent.x, p.tangent.y);
            const normalLength = Math.hypot(p.normal.x, p.normal.y);
            tangentUnitError = Math.max(
                tangentUnitError,
                Math.abs(tangentLength - 1),
                Math.abs(normalLength - 1),
                Math.abs(dot(p.tangent, p.normal))
            );
            normalOutwardMin = Math.min(
                normalOutwardMin,
                dot(p.normal, {x: p.x - centroid.x, y: p.y - centroid.y})
            );
            sampleBoundaryError = Math.max(
                sampleBoundaryError,
                envelope.distanceToBoundary({x: p.x, y: p.y})
            );
        }

        result.metrics = {
            perimeter: envelope.length,
            analyticPerimeter: analytic,
            analyticRelError: analyticRelError,
            densePerimeter: dense.length,
            denseRelError: denseRelError,
            hullVertices: envelope.points.length,
            area: envelope.area,
            linkCount: N,
            maxDurability: totalDurability,
            supportDeficit: support.maxDeficit,
            supportExcess: support.maxExcess,
            supportTolerance: supportTolerance,
            sampleSpacingError: sampleSpacingError,
            sampleArcError: sampleArcError,
            normalOutwardMin: normalOutwardMin,
            sampleBoundaryError: sampleBoundaryError,
            tangentUnitError: tangentUnitError,
            centerHullSupport: null
        };

        addCheck(result, '包络非空', envelope.length > 0, 'L=' + envelope.length.toFixed(6));
        addCheck(result, '逆时针且面积为正', envelope.area > 0, 'area=' + envelope.area.toFixed(6));
        addCheck(result, '凸包无凹点', validateConvex(envelope.points), 'vertices=' + envelope.points.length);
        addCheck(
            result,
            '全部原始采样点在包络内',
            envelope.samples.every(function (p) {
                return envelope.containsPoint(p, 1e-7);
            }),
            'samples=' + envelope.samples.length
        );
        addCheck(
            result,
            '每个支撑圆都参与外轮廓',
            circles.every(function (c) {
                if (caseDef.containedIds && caseDef.containedIds.indexOf(c.id) >= 0) {
                    return true;
                }
                return envelope.points.some(function (p) {
                    return distance(p, c) <= c.r + 0.005;
                });
            }),
            'circles=' + circles.length
        );

        if (analytic != null) {
            addCheck(
                result,
                '周长匹配解析解',
                analyticRelError <= 0.003,
                'actual=' + envelope.length.toFixed(6)
                + ' expected=' + analytic.toFixed(6)
                + ' rel=' + (analyticRelError * 100).toFixed(4) + '%'
            );
        }

        addCheck(
            result,
            '64 段采样逼近 2048 段参考',
            denseRelError <= 0.005,
            'dense=' + dense.length.toFixed(6)
            + ' rel=' + (denseRelError * 100).toFixed(4) + '%'
        );
        addCheck(
            result,
            '支撑函数不低于真实圆盘',
            support.maxDeficit <= supportTolerance,
            'deficit=' + support.maxDeficit.toFixed(6)
            + ' tol=' + supportTolerance.toFixed(6)
        );
        addCheck(
            result,
            '支撑函数不超出真实圆盘',
            support.maxExcess <= 1e-6,
            'excess=' + support.maxExcess.toExponential(3)
        );
        addCheck(
            result,
            'sampleAt(0) == sampleAt(L)',
            distance(envelope.sampleAt(0), envelope.sampleAt(envelope.length)) <= 1e-7,
            ''
        );
        addCheck(
            result,
            'sampleAt 按等弧长分布',
            sampleSpacingError <= 1e-6,
            'max arc spacing error=' + sampleSpacingError.toExponential(3)
        );
        addCheck(
            result,
            'sampleAt(s) 的几何位置对应弧长 s',
            sampleArcError <= 1e-6,
            'max arc error=' + sampleArcError.toExponential(3)
        );
        addCheck(
            result,
            '切线与法线单位正交',
            tangentUnitError <= 1e-6,
            'max error=' + tangentUnitError.toExponential(3)
        );
        addCheck(
            result,
            '外法线朝外',
            normalOutwardMin >= -1e-6,
            'min dot=' + normalOutwardMin.toFixed(6)
        );
        addCheck(
            result,
            'sampleAt 落在边界上',
            sampleBoundaryError <= 1e-6,
            'max distance=' + sampleBoundaryError.toExponential(3)
        );
        addCheck(
            result,
            'N 与最大耐久推导',
            N >= 1 && Math.abs(N * CONFIG.linkLength - envelope.length) <= CONFIG.linkLength / 2 + 1e-9,
            'N=' + N + ' maxHp=' + totalDurability
        );

        if (caseDef.expectedHullVertices != null) {
            addCheck(
                result,
                '凸包顶点数符合单圆采样',
                envelope.points.length === caseDef.expectedHullVertices,
                'actual=' + envelope.points.length
            );
        }

        if (caseDef.middleCenter) {
            addCheck(
                result,
                '共线中间轮被包络包含',
                envelope.containsPoint(caseDef.middleCenter, 1e-9),
                ''
            );
        }

        if (caseDef.containedIds && caseDef.outerId) {
            const outer = circles.find(function (c) {
                return c.id === caseDef.outerId;
            });
            addCheck(
                result,
                '包含圆不产生外侧顶点',
                envelope.points.every(function (p) {
                    return Math.hypot(p.x - outer.x, p.y - outer.y) <= outer.r + 1e-6;
                }),
                'outer r=' + outer.r
            );
            addCheck(
                result,
                '包含圆面积仍等于外圆',
                Math.abs(envelope.area - Math.PI * outer.r * outer.r) / (Math.PI * outer.r * outer.r) <= 0.005,
                'area=' + envelope.area.toFixed(6)
            );
        }

        if (caseDef.centerHullNegative) {
            const centerOnly = circles.map(function (c) {
                return {id: c.id, x: c.x, y: c.y, r: 0};
            });
            const centerEnvelope = TE.buildEnvelope(centerOnly, {segments: CONFIG.segments});
            const direction = caseDef.centerHullNegative.direction;
            const centerSupport = centerEnvelope.support(direction);
            const discSupport = envelope.support(direction);
            result.metrics.centerHullSupport = centerSupport;
            result.metrics.discSupport = discSupport;
            addCheck(
                result,
                '负对照：圆心凸包被支撑函数击穿',
                centerSupport < caseDef.centerHullNegative.maxExpectedSupport,
                'center=' + centerSupport.toFixed(6)
                + ' disc=' + discSupport.toFixed(6)
                + ' threshold=' + caseDef.centerHullNegative.maxExpectedSupport
            );
            addCheck(
                result,
                '大轮圆盘上缘未被切掉',
                discSupport >= Math.max.apply(null, circles.map(function (c) {
                    return c.r;
                })) - 0.01,
                'disc=' + discSupport.toFixed(6)
            );
        }

        return result;
    }

    function runSelfTests() {
        const assertions = [];
        function check(name, ok, detail) {
            assertions.push({name: name, ok: !!ok, detail: detail == null ? '' : String(detail)});
        }

        const empty = TE.buildEnvelope([]);
        check('空输入返回空包络', empty.isEmpty && empty.length === 0, 'L=' + empty.length);
        let threw = false;
        try {
            empty.sampleAt(0);
        } catch (e) {
            threw = true;
        }
        check('空包络采样抛错', threw, '');

        const one = TE.buildEnvelope([{x: 0, y: 0, r: 1}], {segments: 64});
        const duplicate = TE.buildEnvelope([
            {x: 0, y: 0, r: 1},
            {x: 0, y: 0, r: 1}
        ], {segments: 64});
        check(
            '重合圆不改变包络',
            Math.abs(duplicate.length - one.length) <= 1e-9,
            'one=' + one.length.toFixed(9) + ' duplicate=' + duplicate.length.toFixed(9)
        );

        const withZero = TE.buildEnvelope([
            {x: 0, y: 0, r: 1},
            {x: 0.2, y: 0.1, r: 0}
        ], {segments: 64});
        check(
            '零半径圆不改变包络',
            Math.abs(withZero.length - one.length) <= 1e-9,
            'L=' + withZero.length.toFixed(9)
        );

        const segment = TE.buildEnvelope([
            {x: 0, y: 0, r: 0},
            {x: 2, y: 0, r: 0}
        ], {segments: 64});
        check('两零半径点退化为线段（往返周长 4）', Math.abs(segment.length - 4) <= 1e-9, 'L=' + segment.length);

        let invalidSegments = false;
        try {
            TE.buildEnvelope([{x: 0, y: 0, r: 1}], {segments: 2});
        } catch (e) {
            invalidSegments = true;
        }
        check('segments < 3 抛错', invalidSegments, '');

        let invalidLength = false;
        try {
            TE.estimateLinkCount(1, 0);
        } catch (e) {
            invalidLength = true;
        }
        check('linkLength <= 0 抛错', invalidLength, '');

        const a = TE.buildEnvelope(CASES[8].circles, {segments: 64});
        const b = TE.buildEnvelope(CASES[8].circles, {segments: 64});
        check(
            '同一输入确定性地产生同一包络',
            a.length === b.length
            && a.points.length === b.points.length
            && a.points.every(function (p, i) {
                return p.x === b.points[i].x && p.y === b.points[i].y;
            }),
            'L=' + a.length.toFixed(12)
        );

        const wrapped = a.sampleAt(-a.length / 4);
        const wrappedEquivalent = a.sampleAt((a.length * 3) / 4);
        check(
            '负弧长与超过 L 的弧长正确回绕',
            distance(wrapped, wrappedEquivalent) <= 1e-7,
            ''
        );

        check('L=0 时 N 至少为 1', TE.estimateLinkCount(0, 0.2) === 1, '');
        check('N=31 时最大耐久为 620', TE.maxDurability(20, 31) === 620, '');

        const passed = assertions.filter(function (x) {
            return x.ok;
        }).length;
        return {
            name: '自检 / 退化输入',
            assertions: assertions,
            passed: passed,
            total: assertions.length,
            ok: passed === assertions.length
        };
    }

    function mulberry32(seed) {
        let a = seed >>> 0;
        return function () {
            a = (a + 0x6D2B79F5) >>> 0;
            let t = a;
            t = Math.imul(t ^ (t >>> 15), t | 1);
            t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
            return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
        };
    }

    function validateRandomCase(circles, envelope) {
        const issues = [];
        if (envelope.isEmpty || !(envelope.length > 0)) {
            issues.push('empty envelope');
        }
        if (envelope.points.some(function (p) {
            return !Number.isFinite(p.x) || !Number.isFinite(p.y);
        })) {
            issues.push('non-finite hull point');
        }
        if (!Number.isFinite(envelope.length)) {
            issues.push('non-finite length');
        }
        if (!validateConvex(envelope.points)) {
            issues.push('non-convex hull');
        }
        if (envelope.area <= 0) {
            issues.push('non-positive area');
        }
        if (!envelope.samples.every(function (p) {
            return envelope.containsPoint(p, 1e-6);
        })) {
            issues.push('sample outside hull');
        }
        const maxRadius = circles.reduce(function (m, c) {
            return Math.max(m, c.r);
        }, 0);
        const tolerance = maxRadius * (1 - Math.cos(Math.PI / CONFIG.segments)) * 2.5 + 1e-6;
        const support = supportExtremes(envelope, circles);
        if (support.maxDeficit > tolerance) {
            issues.push('support deficit ' + support.maxDeficit.toFixed(6) + ' > ' + tolerance.toFixed(6));
        }
        if (support.maxExcess > 1e-6) {
            issues.push('support excess ' + support.maxExcess.toExponential(3));
        }
        return issues;
    }

    function runFuzz() {
        const rand = mulberry32(CONFIG.fuzzSeed);
        let passed = 0;
        const failures = [];
        for (let i = 0; i < CONFIG.fuzzCases; i++) {
            const count = 1 + Math.floor(rand() * 8);
            const circles = [];
            for (let j = 0; j < count; j++) {
                circles.push({
                    id: 'r' + j,
                    x: rand() * 6 - 3,
                    y: rand() * 6 - 3,
                    r: 0.05 + rand() * 1.45
                });
            }
            let issues;
            try {
                const envelope = TE.buildEnvelope(circles, {segments: CONFIG.segments});
                issues = validateRandomCase(circles, envelope);
            } catch (e) {
                issues = ['exception: ' + e.message];
            }
            if (issues.length === 0) {
                passed++;
            } else if (failures.length < 5) {
                failures.push({index: i, circles: circles, issues: issues});
            }
        }
        return {
            total: CONFIG.fuzzCases,
            passed: passed,
            failed: CONFIG.fuzzCases - passed,
            failures: failures,
            seed: CONFIG.fuzzSeed
        };
    }

    function runAll() {
        const cases = CASES.map(runCase);
        const selfTests = runSelfTests();
        const fuzz = runFuzz();

        let assertions = 0;
        let failed = 0;
        for (const result of cases) {
            assertions += result.assertions.length;
            failed += result.assertions.filter(function (a) {
                return !a.ok;
            }).length;
        }
        assertions += selfTests.total;
        failed += selfTests.total - selfTests.passed;

        return {
            generatedAt: new Date().toISOString(),
            config: CONFIG,
            cases: cases,
            selfTests: selfTests,
            fuzz: fuzz,
            summary: {
                cases: cases.length,
                casesPassed: cases.filter(function (r) {
                    return r.passed;
                }).length,
                assertions: assertions,
                failed: failed,
                selfTestsPassed: selfTests.passed,
                selfTestsTotal: selfTests.total,
                fuzzPassed: fuzz.passed,
                fuzzTotal: fuzz.total,
                ok: failed === 0 && fuzz.failed === 0 && selfTests.ok
            }
        };
    }

    const api = {
        CONFIG: CONFIG,
        CASES: CASES,
        twoCirclePerimeter: twoCirclePerimeter,
        runCase: runCase,
        runAll: runAll
    };

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = api;
    }
    global.TrackEnvelopeTests = api;

    if (typeof module !== 'undefined' && require.main === module) {
        const report = runAll();
        const s = report.summary;
        console.log('TrackEnvelope2D');
        console.log('  cases: ' + s.casesPassed + '/' + s.cases
            + '   assertions: ' + (s.assertions - s.failed) + '/' + s.assertions
            + '   fuzz: ' + s.fuzzPassed + '/' + s.fuzzTotal);
        for (const result of report.cases) {
            const failedChecks = result.assertions.filter(function (a) {
                return !a.ok;
            });
            const m = result.metrics;
            console.log('  [' + (result.passed ? 'PASS' : 'FAIL') + '] '
                + result.case.id.padEnd(18)
                + ' L=' + m.perimeter.toFixed(4)
                + ' denseErr=' + (m.denseRelError * 100).toFixed(4) + '%'
                + ' N=' + m.linkCount
                + (m.analyticRelError == null ? '' : ' analyticErr=' + (m.analyticRelError * 100).toFixed(4) + '%'));
            for (const check of failedChecks) {
                console.log('       FAIL ' + check.name + ' :: ' + check.detail);
            }
        }
        if (!report.selfTests.ok) {
            for (const check of report.selfTests.assertions) {
                if (!check.ok) {
                    console.log('  [FAIL] self :: ' + check.name + ' :: ' + check.detail);
                }
            }
        }
        if (report.fuzz.failed > 0) {
            for (const failure of report.fuzz.failures) {
                console.log('  [FAIL] fuzz #' + failure.index + ' :: ' + failure.issues.join('; '));
            }
        }
        console.log('  RESULT: ' + (report.summary.ok ? 'PASS' : 'FAIL'));
        process.exitCode = report.summary.ok ? 0 : 1;
    }
})(typeof globalThis !== 'undefined' ? globalThis : this);
