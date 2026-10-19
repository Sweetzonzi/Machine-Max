/*
 * 履带包络 2D 验证页的可视化层。
 * 只做 Canvas 2D 绘制，不参与任何断言；所有 PASS/FAIL 来自 track-envelope-tests.js。
 */
(function (global) {
    'use strict';

    const TE = global.TrackEnvelope;
    const Tests = global.TrackEnvelopeTests;
    if (!TE || !Tests) {
        throw new Error('TrackEnvelope / TrackEnvelopeTests not loaded');
    }

    const TAU = Math.PI * 2;
    const PALETTE = [
        '#67e8f9', '#a78bfa', '#fbbf24', '#fb7185',
        '#34d399', '#f472b6', '#93c5fd', '#facc15'
    ];
    const MONO = 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace';

    function setupCanvas(canvas, logicalWidth, logicalHeight) {
        const dpr = Math.min(2, window.devicePixelRatio || 1);
        canvas.width = Math.round(logicalWidth * dpr);
        canvas.height = Math.round(logicalHeight * dpr);
        canvas.style.width = '100%';
        canvas.style.height = 'auto';
        const ctx = canvas.getContext('2d');
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        ctx.clearRect(0, 0, logicalWidth, logicalHeight);
        return {ctx: ctx, width: logicalWidth, height: logicalHeight};
    }

    function fitTransform(width, height, circles, envelope) {
        let minX = Infinity;
        let maxX = -Infinity;
        let minY = Infinity;
        let maxY = -Infinity;
        function include(x, y) {
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        for (const c of circles) {
            include(c.x - c.r, c.y - c.r);
            include(c.x + c.r, c.y + c.r);
        }
        for (const p of envelope.points) {
            include(p.x, p.y);
        }
        if (!Number.isFinite(minX)) {
            minX = -1;
            maxX = 1;
            minY = -1;
            maxY = 1;
        }
        const pad = 24;
        const spanX = Math.max(maxX - minX, 1e-6);
        const spanY = Math.max(maxY - minY, 1e-6);
        const scale = Math.min((width - 2 * pad) / spanX, (height - 2 * pad) / spanY);
        const midX = (minX + maxX) / 2;
        const midY = (minY + maxY) / 2;
        return {
            scale: scale,
            minX: minX,
            maxX: maxX,
            minY: minY,
            maxY: maxY,
            toX: function (x) {
                return width / 2 + (x - midX) * scale;
            },
            toY: function (y) {
                return height / 2 - (y - midY) * scale;
            }
        };
    }

    function niceStep(target) {
        const steps = [0.05, 0.1, 0.2, 0.25, 0.5, 1, 2, 5, 10, 20, 50];
        for (const s of steps) {
            if (s >= target) return s;
        }
        return 100;
    }

    function drawGrid(ctx, transform, width, height) {
        const step = niceStep(Math.max(transform.maxX - transform.minX, transform.maxY - transform.minY) / 6);
        ctx.save();
        ctx.lineWidth = 1;
        ctx.strokeStyle = 'rgba(148, 163, 184, 0.9)';
        ctx.globalAlpha = 0.10;
        const startX = Math.ceil(transform.minX / step) * step;
        for (let x = startX; x <= transform.maxX + 1e-9; x += step) {
            const sx = transform.toX(x);
            ctx.beginPath();
            ctx.moveTo(sx, 0);
            ctx.lineTo(sx, height);
            ctx.stroke();
        }
        const startY = Math.ceil(transform.minY / step) * step;
        for (let y = startY; y <= transform.maxY + 1e-9; y += step) {
            const sy = transform.toY(y);
            ctx.beginPath();
            ctx.moveTo(0, sy);
            ctx.lineTo(width, sy);
            ctx.stroke();
        }
        ctx.globalAlpha = 0.20;
        ctx.strokeStyle = 'rgba(148, 163, 184, 0.9)';
        if (transform.minY <= 0 && transform.maxY >= 0) {
            const sy = transform.toY(0);
            ctx.beginPath();
            ctx.moveTo(0, sy);
            ctx.lineTo(width, sy);
            ctx.stroke();
        }
        if (transform.minX <= 0 && transform.maxX >= 0) {
            const sx = transform.toX(0);
            ctx.beginPath();
            ctx.moveTo(sx, 0);
            ctx.lineTo(sx, height);
            ctx.stroke();
        }
        ctx.restore();
    }

    function pathPolygon(ctx, points, toX, toY, close) {
        if (points.length === 0) return;
        ctx.beginPath();
        ctx.moveTo(toX(points[0].x), toY(points[0].y));
        for (let i = 1; i < points.length; i++) {
            ctx.lineTo(toX(points[i].x), toY(points[i].y));
        }
        if (close) ctx.closePath();
    }

    function drawArrow(ctx, x1, y1, x2, y2, color, width) {
        const dx = x2 - x1;
        const dy = y2 - y1;
        const len = Math.hypot(dx, dy);
        if (len < 1e-6) return;
        const ux = dx / len;
        const uy = dy / len;
        const head = Math.min(7, len * 0.25);
        ctx.save();
        ctx.strokeStyle = color;
        ctx.fillStyle = color;
        ctx.lineWidth = width == null ? 1.8 : width;
        ctx.lineCap = 'round';
        ctx.beginPath();
        ctx.moveTo(x1, y1);
        ctx.lineTo(x2, y2);
        ctx.stroke();
        ctx.beginPath();
        ctx.moveTo(x2, y2);
        ctx.lineTo(x2 - ux * head - uy * head * 0.55, y2 - uy * head + ux * head * 0.55);
        ctx.lineTo(x2 - ux * head + uy * head * 0.55, y2 - uy * head - ux * head * 0.55);
        ctx.closePath();
        ctx.fill();
        ctx.restore();
    }

    function drawScaleBar(ctx, transform, width, height) {
        const meters = transform.scale >= 55 ? 1 : 0.5;
        const px = meters * transform.scale;
        const x = 18;
        const y = height - 18;
        ctx.save();
        ctx.strokeStyle = 'rgba(226, 232, 240, 0.75)';
        ctx.fillStyle = 'rgba(226, 232, 240, 0.85)';
        ctx.lineWidth = 1.5;
        ctx.beginPath();
        ctx.moveTo(x, y);
        ctx.lineTo(x + px, y);
        ctx.moveTo(x, y - 3);
        ctx.lineTo(x, y + 3);
        ctx.moveTo(x + px, y - 3);
        ctx.lineTo(x + px, y + 3);
        ctx.stroke();
        ctx.font = '11px ' + MONO;
        ctx.fillText(meters + ' m', x + px + 6, y + 4);
        ctx.restore();
    }

    function linkPoints(envelope, phaseFraction) {
        const n = TE.estimateLinkCount(envelope.length, Tests.CONFIG.linkLength);
        const phase = ((phaseFraction || 0) % 1) * envelope.length;
        const points = [];
        for (let i = 0; i < n; i++) {
            const s = (i * envelope.length / n + phase) % envelope.length;
            points.push(envelope.sampleAt(s));
        }
        return points;
    }

    function drawScene(canvas, circles, envelope, options) {
        options = options || {};
        const width = options.width || 520;
        const height = options.height || 300;
        const surface = setupCanvas(canvas, width, height);
        const ctx = surface.ctx;
        const transform = fitTransform(width, height, circles, envelope);

        ctx.fillStyle = options.background || 'rgba(8, 15, 24, 0.55)';
        ctx.fillRect(0, 0, width, height);
        drawGrid(ctx, transform, width, height);

        // 圆盘本身
        circles.forEach(function (c, i) {
            const color = c.color || PALETTE[i % PALETTE.length];
            ctx.save();
            ctx.beginPath();
            ctx.arc(transform.toX(c.x), transform.toY(c.y), c.r * transform.scale, 0, TAU);
            ctx.fillStyle = color;
            ctx.globalAlpha = 0.10;
            ctx.fill();
            ctx.globalAlpha = 0.85;
            ctx.strokeStyle = color;
            ctx.lineWidth = 1.3;
            ctx.stroke();
            ctx.globalAlpha = 1;
            ctx.beginPath();
            ctx.arc(transform.toX(c.x), transform.toY(c.y), 2, 0, TAU);
            ctx.fillStyle = color;
            ctx.fill();
            ctx.restore();
        });

        // 原始边界采样点
        if (options.showSamples !== false) {
            ctx.save();
            ctx.fillStyle = 'rgba(203, 213, 225, 0.65)';
            for (const p of envelope.samples) {
                ctx.beginPath();
                ctx.arc(transform.toX(p.x), transform.toY(p.y), 1.25, 0, TAU);
                ctx.fill();
            }
            ctx.restore();
        }

        // 负对照：圆心凸包
        if (options.showCenterHull) {
            const centerEnvelope = TE.buildEnvelope(
                circles.map(function (c) {
                    return {id: c.id, x: c.x, y: c.y, r: 0, color: c.color};
                }),
                {segments: 64}
            );
            ctx.save();
            ctx.setLineDash([6, 5]);
            ctx.strokeStyle = 'rgba(248, 113, 113, 0.95)';
            ctx.lineWidth = 1.8;
            pathPolygon(ctx, centerEnvelope.points, transform.toX, transform.toY, true);
            ctx.stroke();
            ctx.restore();
        }

        // 圆盘包络
        ctx.save();
        pathPolygon(ctx, envelope.points, transform.toX, transform.toY, true);
        ctx.fillStyle = 'rgba(34, 211, 238, 0.11)';
        ctx.fill();
        ctx.strokeStyle = '#22d3ee';
        ctx.lineWidth = options.hullWidth || 2.4;
        ctx.lineJoin = 'round';
        ctx.stroke();
        ctx.restore();

        // 凸包顶点
        if (options.showVertices !== false) {
            ctx.save();
            ctx.fillStyle = 'rgba(236, 254, 255, 0.95)';
            for (const p of envelope.points) {
                ctx.beginPath();
                ctx.arc(transform.toX(p.x), transform.toY(p.y), 1.8, 0, TAU);
                ctx.fill();
            }
            ctx.restore();
        }

        // 履带节位置
        if (options.showLinks !== false) {
            const links = linkPoints(envelope, options.phase);
            ctx.save();
            if (options.linkStyle === 'segments') {
                const linkPixel = Math.min(20, Math.max(2, (envelope.length / links.length) * transform.scale * 0.78));
                links.forEach(function (p, i) {
                    const sx = transform.toX(p.x);
                    const sy = transform.toY(p.y);
                    const angle = Math.atan2(-p.tangent.y, p.tangent.x);
                    ctx.save();
                    ctx.translate(sx, sy);
                    ctx.rotate(angle);
                    ctx.fillStyle = i === 0 ? '#fbbf24' : 'rgba(251, 191, 36, 0.82)';
                    ctx.fillRect(-linkPixel / 2, -1.7, linkPixel, 3.4);
                    ctx.restore();
                });
            } else {
                ctx.fillStyle = '#fbbf24';
                links.forEach(function (p, i) {
                    ctx.beginPath();
                    ctx.arc(transform.toX(p.x), transform.toY(p.y), i === 0 ? 3 : 2.2, 0, TAU);
                    ctx.fill();
                });
            }
            ctx.restore();
        }

        // 切线 / 外法线（细节页）
        if (options.showNormals) {
            const links = linkPoints(envelope, options.phase);
            ctx.save();
            links.forEach(function (p, i) {
                if (i % 4 !== 0) return;
                const sx = transform.toX(p.x);
                const sy = transform.toY(p.y);
                drawArrow(ctx, sx, sy, sx + p.tangent.x * 22, sy - p.tangent.y * 22, '#60a5fa', 1.4);
                drawArrow(ctx, sx, sy, sx + p.normal.x * 16, sy - p.normal.y * 16, '#facc15', 1.4);
            });
            ctx.restore();
        }

        // 支撑方向 / 注释
        if (options.supportDirection) {
            const u = options.supportDirection;
            const origin = options.supportOrigin || {x: 0, y: 0};
            const len = options.supportLength || 1.2;
            drawArrow(
                ctx,
                transform.toX(origin.x),
                transform.toY(origin.y),
                transform.toX(origin.x + u.x * len),
                transform.toY(origin.y + u.y * len),
                '#facc15',
                2
            );
        }
        if (options.annotations) {
            ctx.save();
            ctx.font = '12px ' + MONO;
            for (const a of options.annotations) {
                ctx.fillStyle = a.color || 'rgba(226, 232, 240, 0.9)';
                ctx.fillText(a.text, transform.toX(a.x), transform.toY(a.y));
            }
            ctx.restore();
        }

        if (options.title) {
            ctx.save();
            ctx.font = '12px ' + MONO;
            ctx.fillStyle = 'rgba(226, 232, 240, 0.8)';
            ctx.fillText(options.title, 16, 20);
            ctx.restore();
        }

        drawScaleBar(ctx, transform, width, height);
        return {transform: transform, links: options.showLinks !== false ? linkPoints(envelope, options.phase) : []};
    }

    function formatPercent(value) {
        if (value == null || !Number.isFinite(value)) return '—';
        return (value * 100).toFixed(4) + '%';
    }

    function formatNumber(value, digits) {
        if (value == null || !Number.isFinite(value)) return '—';
        return value.toFixed(digits == null ? 4 : digits);
    }

    function renderIndex(report) {
        const summary = document.getElementById('summary');
        const meta = document.getElementById('meta');
        const grid = document.getElementById('cases');
        if (!grid) return;

        const s = report.summary;
        summary.className = 'summary ' + (s.ok ? 'ok' : 'bad');
        summary.innerHTML =
            '<div class="summary-state">' + (s.ok ? 'ALL PASS' : 'FAILED') + '</div>'
            + '<div class="summary-row"><strong>' + s.casesPassed + '/' + s.cases + '</strong><span>场景</span></div>'
            + '<div class="summary-row"><strong>' + (s.assertions - s.failed) + '/' + s.assertions + '</strong><span>断言</span></div>'
            + '<div class="summary-row"><strong>' + s.fuzzPassed + '/' + s.fuzzTotal + '</strong><span>随机模糊</span></div>';

        meta.innerHTML = [
            ['算法', '圆盘采样 → monotone chain → 弧长参数化'],
            ['圆采样', Tests.CONFIG.segments + ' 段 / 圆'],
            ['参考采样', Tests.CONFIG.denseSegments + ' 段 / 圆'],
            ['节长', Tests.CONFIG.linkLength + ' m'],
            ['每节耐久', String(Tests.CONFIG.linkDurability)],
            ['支撑方向', Tests.CONFIG.supportDirections + ' 个'],
            ['生成', new Date(report.generatedAt).toLocaleString('zh-CN', {hour12: false})]
        ].map(function (row) {
            return '<div class="meta-item"><span>' + row[0] + '</span><strong>' + row[1] + '</strong></div>';
        }).join('');

        for (const result of report.cases) {
            const card = document.createElement('article');
            card.className = 'card ' + (result.passed ? 'pass' : 'fail');

            const header = document.createElement('div');
            header.className = 'card-head';
            header.innerHTML =
                '<div><h2>' + result.case.title + '</h2><p>' + result.case.subtitle + '</p></div>'
                + '<span class="badge">' + (result.passed ? 'PASS' : 'FAIL') + '</span>';
            card.appendChild(header);

            const canvas = document.createElement('canvas');
            canvas.className = 'case-canvas';
            card.appendChild(canvas);

            const m = result.metrics;
            const metrics = document.createElement('div');
            metrics.className = 'metrics';
            metrics.innerHTML = [
                ['L', formatNumber(m.perimeter, 4) + ' m'],
                ['N', String(m.linkCount)],
                ['max HP', String(m.maxDurability)],
                ['顶点', String(m.hullVertices)],
                ['加密误差', formatPercent(m.denseRelError)],
                ['支撑亏损', formatNumber(m.supportDeficit, 5)]
            ].map(function (row) {
                return '<div><span>' + row[0] + '</span><strong>' + row[1] + '</strong></div>';
            }).join('');
            card.appendChild(metrics);

            const passCount = result.assertions.filter(function (a) {
                return a.ok;
            }).length;
            const details = document.createElement('details');
            details.className = 'assertions';
            details.open = !result.passed;
            details.innerHTML = '<summary>断言 ' + passCount + '/' + result.assertions.length
                + (result.passed ? '' : ' · 有失败') + '</summary><ul>'
                + result.assertions.map(function (a) {
                    return '<li class="' + (a.ok ? 'ok' : 'bad') + '"><span>' + (a.ok ? '✓' : '×') + '</span>'
                        + a.name + (a.detail ? '<em>' + a.detail + '</em>' : '') + '</li>';
                }).join('') + '</ul>';
            card.appendChild(details);

            grid.appendChild(card);

            drawScene(canvas, result.case.circles, result.envelope, {
                width: 520,
                height: 300,
                showCenterHull: !!result.case.centerHullNegative,
                showLinks: true,
                phase: 0.35,
                annotations: result.case.centerHullNegative ? [
                    {x: -1.55, y: 1.28, text: '圆盘包络', color: '#22d3ee'},
                    {x: -1.55, y: -0.62, text: '圆心凸包（错误）', color: '#f87171'}
                ] : null
            });
        }

        const fuzz = document.getElementById('fuzz');
        const big = report.cases.find(function (r) {
            return r.case.id === 'big-center-inside';
        });
        fuzz.innerHTML =
            '<div class="panel-head"><h2>随机模糊 + 负对照</h2><span class="badge ' + (report.fuzz.failed === 0 ? 'pass' : 'fail') + '">'
            + (report.fuzz.failed === 0 ? 'PASS' : 'FAIL') + '</span></div>'
            + '<div class="panel-grid">'
            + '<div><span>随机布局</span><strong>' + report.fuzz.passed + ' / ' + report.fuzz.total + '</strong></div>'
            + '<div><span>随机种子</span><strong>0x' + report.fuzz.seed.toString(16).toUpperCase() + '</strong></div>'
            + '<div><span>退化输入自检</span><strong>' + report.selfTests.passed + ' / ' + report.selfTests.total + '</strong></div>'
            + '<div><span>圆心凸包 +Y 支撑</span><strong class="bad">'
            + (big ? formatNumber(big.metrics.centerHullSupport, 3) : '—') + ' m（预期失败）</strong></div>'
            + '<div><span>圆盘凸包 +Y 支撑</span><strong class="good">'
            + (big ? formatNumber(big.metrics.discSupport, 3) : '—') + ' m</strong></div>'
            + '<div><span>负对照</span><strong class="good">已捕获</strong></div>'
            + '</div>'
            + '<p class="panel-note">随机模糊对每组圆检查凸性、采样点包含、支撑函数上下界；退化输入覆盖空集、重合圆、零半径、线段、非法参数。圆心凸包只连轮心，在 +Y 方向只能到 0.000 m；圆盘包络应到 1.200 m，测试会抓住这个差异。</p>';

        global.__TRACK_ENVELOPE_REPORT__ = report;
    }

    function renderDetail(report) {
        const centerCanvas = document.getElementById('detail-center');
        const realisticCanvas = document.getElementById('detail-realistic');
        if (!centerCanvas || !realisticCanvas) return;

        const center = report.cases.find(function (r) {
            return r.case.id === 'big-center-inside';
        });
        const realistic = report.cases.find(function (r) {
            return r.case.id === 'realistic';
        });

        drawScene(centerCanvas, center.case.circles, center.envelope, {
            width: 1100,
            height: 300,
            showCenterHull: true,
            showLinks: false,
            supportDirection: {x: 0, y: 1},
            supportOrigin: {x: 0, y: 0},
            supportLength: 1.2,
            annotations: [
                {x: -0.02, y: 1.26, text: '圆盘 +Y 支撑 1.200', color: '#22d3ee'},
                {x: -0.02, y: -0.28, text: '圆心凸包 +Y 支撑 0.000', color: '#f87171'}
            ]
        });

        const phaseInput = document.getElementById('phase');
        const phaseLabel = document.getElementById('phase-label');
        function redrawRealistic() {
            const phase = phaseInput ? Number(phaseInput.value) : 0.35;
            if (phaseLabel) {
                phaseLabel.textContent = 'phase = ' + phase.toFixed(3) + ' L';
            }
            const drawn = drawScene(realisticCanvas, realistic.case.circles, realistic.envelope, {
                width: 1100,
                height: 520,
                showLinks: true,
                linkStyle: 'segments',
                showNormals: true,
                phase: phase
            });
            const readout = document.getElementById('link-readout');
            if (readout) {
                readout.textContent = 'N = ' + drawn.links.length
                    + ' · L = ' + realistic.metrics.perimeter.toFixed(4) + ' m'
                    + ' · 间距 = ' + (realistic.metrics.perimeter / drawn.links.length).toFixed(4) + ' m';
            }
        }
        if (phaseInput) {
            phaseInput.addEventListener('input', redrawRealistic);
        }
        redrawRealistic();

        const values = document.getElementById('detail-values');
        if (values) {
            values.innerHTML = [
                ['场景', realistic.case.title],
                ['周长 L', realistic.metrics.perimeter.toFixed(6) + ' m'],
                ['节数 N', String(realistic.metrics.linkCount)],
                ['最大耐久', String(realistic.metrics.maxDurability)],
                ['加密误差', formatPercent(realistic.metrics.denseRelError)],
                ['支撑亏损', realistic.metrics.supportDeficit.toFixed(6) + ' m'],
                ['凸包顶点', String(realistic.metrics.hullVertices)]
            ].map(function (row) {
                return '<div><span>' + row[0] + '</span><strong>' + row[1] + '</strong></div>';
            }).join('');
        }

        const centerValues = document.getElementById('center-values');
        if (centerValues) {
            centerValues.innerHTML = [
                ['圆盘包络 +Y 支撑', formatNumber(center.metrics.discSupport, 6) + ' m'],
                ['圆心凸包 +Y 支撑', formatNumber(center.metrics.centerHullSupport, 6) + ' m'],
                ['差值', formatNumber(center.metrics.discSupport - center.metrics.centerHullSupport, 6) + ' m'],
                ['负对照判定', '已捕获（圆心凸包 < 0.5 m）']
            ].map(function (row) {
                return '<div><span>' + row[0] + '</span><strong>' + row[1] + '</strong></div>';
            }).join('');
        }

        global.__TRACK_ENVELOPE_REPORT__ = report;
    }

    function boot() {
        const report = Tests.runAll();
        if (document.getElementById('cases')) {
            renderIndex(report);
        }
        if (document.getElementById('detail-realistic')) {
            renderDetail(report);
        }
    }

    global.TrackEnvelopeVisual = {
        drawScene: drawScene,
        linkPoints: linkPoints
    };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', boot);
    } else {
        boot();
    }
})(typeof globalThis !== 'undefined' ? globalThis : this);
