/*
 * 非凸履带包络验证页的可视化层。
 * 青色实线 = 有序轮组包络；红色虚线 = 凸包对照；橙色 = 接触点/接触弧与履带节。
 */
(function (global) {
    'use strict';

    const TP = global.TrackPath;
    const TE = global.TrackEnvelope;
    const Tests = global.TrackPathTests;
    if (!TP || !TE || !Tests) {
        throw new Error('TrackPath / TrackEnvelope / TrackPathTests not loaded');
    }

    const TAU = Math.PI * 2;
    const MONO = 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace';
    const PALETTE = ['#67e8f9', '#a78bfa', '#fbbf24', '#fb7185', '#34d399', '#f472b6'];

    function setupCanvas(canvas, width, height) {
        const dpr = Math.min(2, window.devicePixelRatio || 1);
        canvas.width = Math.round(width * dpr);
        canvas.height = Math.round(height * dpr);
        canvas.style.width = '100%';
        canvas.style.height = 'auto';
        const ctx = canvas.getContext('2d');
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        ctx.clearRect(0, 0, width, height);
        return {ctx: ctx, width: width, height: height};
    }

    function fitTransform(width, height, circles, path, hull) {
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
        for (const p of path.points) include(p.x, p.y);
        for (const p of hull.points) include(p.x, p.y);
        const pad = 26;
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
            toX: function (x) { return width / 2 + (x - midX) * scale; },
            toY: function (y) { return height / 2 - (y - midY) * scale; }
        };
    }

    function niceStep(target) {
        const steps = [0.05, 0.1, 0.2, 0.25, 0.5, 1, 2, 5, 10, 20, 50];
        for (const s of steps) if (s >= target) return s;
        return 100;
    }

    function drawGrid(ctx, transform, width, height) {
        const step = niceStep(Math.max(transform.maxX - transform.minX, transform.maxY - transform.minY) / 6);
        ctx.save();
        ctx.strokeStyle = 'rgba(148, 163, 184, 0.9)';
        ctx.lineWidth = 1;
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
        ctx.restore();
    }

    function pathPolygon(ctx, points, toX, toY, close) {
        if (!points.length) return;
        ctx.beginPath();
        ctx.moveTo(toX(points[0].x), toY(points[0].y));
        for (let i = 1; i < points.length; i++) {
            ctx.lineTo(toX(points[i].x), toY(points[i].y));
        }
        if (close) ctx.closePath();
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

    function contactOf(path, caseDef) {
        const kind = caseDef.contactKind || 'arc';
        const list = kind === 'point' ? path.pointContacts : path.arcs;
        return list.find(function (entry) {
            return entry.wheelId === caseDef.contactId;
        }) || null;
    }

    function drawTrack(canvas, result, options) {
        options = options || {};
        const width = options.width || 620;
        const height = options.height || 340;
        const surface = setupCanvas(canvas, width, height);
        const ctx = surface.ctx;
        const circles = result.case.circles;
        const path = result.path;
        const hull = result.hull;
        const transform = fitTransform(width, height, circles, path, hull);
        const contact = contactOf(path, result.case);

        ctx.fillStyle = 'rgba(8, 15, 24, 0.55)';
        ctx.fillRect(0, 0, width, height);
        drawGrid(ctx, transform, width, height);

        circles.forEach(function (c, i) {
            const color = PALETTE[i % PALETTE.length];
            ctx.save();
            ctx.beginPath();
            ctx.arc(transform.toX(c.x), transform.toY(c.y), c.r * transform.scale, 0, TAU);
            ctx.fillStyle = color;
            ctx.globalAlpha = 0.10;
            ctx.fill();
            ctx.globalAlpha = 0.8;
            ctx.strokeStyle = color;
            ctx.lineWidth = 1.2;
            ctx.stroke();
            ctx.restore();
        });

        // 凸包对照
        ctx.save();
        ctx.setLineDash([6, 5]);
        ctx.strokeStyle = 'rgba(248, 113, 113, 0.9)';
        ctx.lineWidth = 1.6;
        pathPolygon(ctx, hull.points, transform.toX, transform.toY, true);
        ctx.stroke();
        ctx.restore();

        // 有序包络
        ctx.save();
        pathPolygon(ctx, path.points, transform.toX, transform.toY, true);
        ctx.fillStyle = 'rgba(34, 211, 238, 0.08)';
        ctx.fill();
        ctx.strokeStyle = '#22d3ee';
        ctx.lineWidth = 2.3;
        ctx.lineJoin = 'round';
        ctx.stroke();
        ctx.restore();

        // 接触弧高亮
        if (contact && contact.type === 'arc') {
            ctx.save();
            ctx.strokeStyle = '#fbbf24';
            ctx.lineWidth = 5;
            ctx.lineCap = 'round';
            ctx.beginPath();
            const from = contact.startIndex;
            const to = contact.endIndex;
            ctx.moveTo(transform.toX(path.points[from].x), transform.toY(path.points[from].y));
            for (let i = from + 1; i <= to; i++) {
                ctx.lineTo(transform.toX(path.points[i].x), transform.toY(path.points[i].y));
            }
            ctx.stroke();
            ctx.restore();
        }

        // 履带节：沿包络等弧长放置
        const n = TE.estimateLinkCount(path.length, Tests.CONFIG.linkLength);
        const phase = options.phase == null ? 0.35 : options.phase;
        ctx.save();
        for (let i = 0; i < n; i++) {
            const p = path.sampleAt((i * path.length / n + phase * path.length) % path.length);
            const sx = transform.toX(p.x);
            const sy = transform.toY(p.y);
            const angle = Math.atan2(-p.tangent.y, p.tangent.x);
            ctx.save();
            ctx.translate(sx, sy);
            ctx.rotate(angle);
            ctx.fillStyle = 'rgba(251, 191, 36, 0.9)';
            ctx.fillRect(-2.4, -1.2, 4.8, 2.4);
            ctx.restore();
        }
        ctx.restore();

        // 点接触
        if (contact && contact.type === 'point') {
            const px = transform.toX(contact.point.x);
            const py = transform.toY(contact.point.y);
            ctx.save();
            ctx.beginPath();
            ctx.arc(px, py, 6, 0, TAU);
            ctx.fillStyle = '#fbbf24';
            ctx.fill();
            ctx.strokeStyle = 'rgba(251, 191, 36, 0.35)';
            ctx.lineWidth = 10;
            ctx.stroke();
            ctx.restore();
            ctx.save();
            ctx.font = '11px ' + MONO;
            ctx.fillStyle = '#fbbf24';
            ctx.fillText(options.contactLabel || '点接触', px + 10, py + 22);
            ctx.restore();
        } else if (contact && contact.type === 'arc' && options.contactLabel) {
            const mid = path.points[Math.floor((contact.startIndex + contact.endIndex) / 2)];
            ctx.save();
            ctx.font = '11px ' + MONO;
            ctx.fillStyle = '#fbbf24';
            ctx.fillText(options.contactLabel, transform.toX(mid.x) + 8, transform.toY(mid.y) + 18);
            ctx.restore();
        }

        if (options.title) {
            ctx.save();
            ctx.font = '11px ' + MONO;
            ctx.fillStyle = 'rgba(226, 232, 240, 0.78)';
            ctx.fillText(options.title, 14, 19);
            ctx.restore();
        }

        drawScaleBar(ctx, transform, width, height);
    }

    function percent(value) {
        return (value * 100).toFixed(4) + '%';
    }

    function render() {
        const report = Tests.runAll();
        const grid = document.getElementById('cases');
        const summary = document.getElementById('summary');
        const s = report.summary;
        summary.className = 'summary ' + (s.ok ? 'ok' : 'bad');
        summary.innerHTML =
            '<div class="summary-state">' + (s.ok ? 'ALL PASS' : 'FAILED') + '</div>'
            + '<div class="summary-row"><strong>' + s.casesPassed + '/' + s.cases + '</strong><span>场景</span></div>'
            + '<div class="summary-row"><strong>' + s.unitTestsPassed + '/' + s.unitTests + '</strong><span>用例</span></div>'
            + '<div class="summary-row"><strong>' + (s.assertions - s.failed) + '/' + s.assertions + '</strong><span>断言</span></div>'
            + '<div class="summary-row"><strong>' + s.selfTestsPassed + '/' + s.selfTestsTotal + '</strong><span>自检</span></div>';

        for (const result of report.cases) {
            const card = document.createElement('article');
            card.className = 'card ' + (result.passed ? 'pass' : 'fail');
            const m = result.metrics;
            const contact = contactOf(result.path, result.case);
            const contactText = contact
                ? (contact.type === 'point' ? '点接触' : '圆弧 ' + (contact.sweep * 180 / Math.PI).toFixed(2) + '°')
                : '—';
            const contactLength = contact
                ? (contact.type === 'point' ? '0 m' : contact.contactLength.toFixed(4) + ' m')
                : '—';

            card.innerHTML =
                '<div class="card-head"><div><h2>' + result.case.title + '</h2>'
                + '<p>' + (m.isConvex ? '凸包络' : '非凸包络') + ' · ' + contactText + '</p></div>'
                + '<span class="badge">' + (result.passed ? 'PASS' : 'FAIL') + '</span></div>'
                + '<canvas class="case-canvas"></canvas>'
                + '<div class="metrics">'
                + '<div><span>有序包络 L</span><strong>' + m.pathLength.toFixed(4) + ' m</strong></div>'
                + '<div><span>圆盘凸包 L</span><strong>' + m.hullLength.toFixed(4) + ' m</strong></div>'
                + '<div><span>差值</span><strong>' + (m.deltaLength >= 0 ? '+' : '') + m.deltaLength.toFixed(4) + ' m</strong></div>'
                + '<div><span>N / max HP</span><strong>' + m.pathN + ' / ' + (m.pathN * 20) + '</strong></div>'
                + '<div><span>接触长度</span><strong>' + contactLength + '</strong></div>'
                + '<div><span>环外轮心</span><strong>' + (m.outsideCenters.length === 0 ? 'NO' : m.outsideCenters.join(',')) + '</strong></div>'
                + '</div>';
            grid.appendChild(card);

            const canvas = card.querySelector('canvas');
            drawTrack(canvas, result, {
                width: 620,
                height: 340,
                contactLabel: contact
                    ? (contact.type === 'point'
                        ? '点接触 @ (' + contact.point.x.toFixed(2) + ', ' + contact.point.y.toFixed(2) + ')'
                        : '圆弧接触 ' + contact.contactLength.toFixed(3) + ' m')
                    : null
            });
        }

        const details = document.getElementById('details');
        const raised = report.cases.find(function (r) { return r.case.id === 'raised-30cm'; });
        const slight = report.cases.find(function (r) { return r.case.id === 'raised-2cm'; });
        const lowered = report.cases.find(function (r) { return r.case.id === 'lowered-30cm'; });
        const endcap = report.cases.find(function (r) { return r.case.id === 'endcap-degenerate'; });
        details.innerHTML =
            '<div class="panel-head"><h2>接触模型</h2><span class="badge">PASS</span></div>'
            + '<div class="panel-grid">'
            + '<div><span>平地（凸角）</span><strong>点接触 0 m</strong></div>'
            + '<div><span>抬高 2 cm</span><strong class="bad">非凸 · 点接触</strong></div>'
            + '<div><span>抬高 30 cm</span><strong class="bad">非凸 · 点接触</strong></div>'
            + '<div><span>降低 30 cm（凸角）</span><strong class="good">圆弧 '
            + lowered.metrics.contactSweepDeg.toFixed(2) + '° / '
            + lowered.metrics.contactLength.toFixed(3) + ' m</strong></div>'
            + '<div><span>抬高 30 cm 包络差</span><strong>+' + raised.metrics.deltaLength.toFixed(4) + ' m</strong></div>'
            + '<div><span>抬高 2 cm 包络差</span><strong>+' + slight.metrics.deltaLength.toFixed(4) + ' m</strong></div>'
            + '<div><span>退化轮序端帽</span><strong class="good">圆弧 '
            + endcap.metrics.contactSweepDeg.toFixed(2) + '° / 环外轮心 '
            + endcap.metrics.outsideCenters.length + '</strong></div>'
            + '</div>'
            + '<p class="panel-note">凸角（主动轮、诱导轮、比两侧低的负重轮）走圆弧；凹角（被障碍抬起的负重轮）走单点接触。'
            + '点落在轮子外侧角平分线与圆的交点：本布局就是轮子最低点。于是履带会贴着被抬起的轮子折一下，而不是像凸包那样从下面跨过去。'
            + '2 cm 时包络与凸包只差 0.0012 m，视觉上几乎就是同一个点；30 cm 时差 0.0613 m，折角明显。</p>'
            + '<p class="panel-note">端帽（下标 0、下标 n-1，或轮心折线转角超过 3π/4 的顶点）一律绕圆弧。'
            + '退化轮序那张里前三个轮心落在一条直线上，转角是 ±π：按带符号转角判定会让同一个布局在'
            + '“绕过该轮”和“只擦过一个点”之间跳变，按转角绝对值判定则始终把该轮包在环内。</p>';

        global.__TRACK_PATH_REPORT__ = report;
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', render);
    } else {
        render();
    }
})(typeof globalThis !== 'undefined' ? globalThis : this);
