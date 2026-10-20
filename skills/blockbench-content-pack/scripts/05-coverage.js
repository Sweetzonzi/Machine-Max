(function () {
  var CONFIG = {
    bones: ['AllHull'],
    ignore: '',
    collider: 'HitBox',
    drop: 'Interact',
    center: 'auto',
    rays: 2048,
    surface: 'outer',
    toleranceInward: 2.5,
    toleranceOutward: 1.0,
    maxDistance: 500,
    azimuthBins: 12,
    elevationBins: 6
  };

  Project.model_3d.updateMatrixWorld(true);

  function round(v) { return Math.round(v * 100) / 100; }
  function r3(a) { return [round(a[0]), round(a[1]), round(a[2])]; }

  function toRegex(p) {
    if (p.charAt(0) === '/' && p.charAt(p.length - 1) === '/') return new RegExp(p.slice(1, -1));
    return new RegExp('^' + p.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '$');
  }

  var ignore = CONFIG.ignore ? new RegExp(CONFIG.ignore) : null;
  var colliderRe = CONFIG.collider ? new RegExp(CONFIG.collider) : null;
  var dropRe = CONFIG.drop ? new RegExp(CONFIG.drop) : null;

  function collect(node, shell, hit, inCollider, inDrop) {
    (node.children || []).forEach(function (child) {
      if (ignore && ignore.test(child.name)) return;
      var isDrop = inDrop || (dropRe && dropRe.test(child.name));
      var isCollider = inCollider || (colliderRe && colliderRe.test(child.name));
      if (child instanceof Cube && !isDrop) (isCollider ? hit : shell).push(child);
      if (child.children) collect(child, shell, hit, isCollider, isDrop);
    });
  }

  var shellCubes = [], hitCubes = [];
  var seenBones = {};
  CONFIG.bones.forEach(function (pattern) {
    var regex = toRegex(pattern);
    Group.all.forEach(function (bone) {
      if (!regex.test(bone.name) || seenBones[bone.uuid]) return;
      seenBones[bone.uuid] = true;
      collect(bone, shellCubes, hitCubes, null, null);
    });
  });

  function makeObb(cube) {
    var g = cube.mesh.geometry;
    if (!g.boundingBox) g.computeBoundingBox();
    var bb = g.boundingBox;
    return {
      name: cube.name,
      bone: cube.parent ? cube.parent.name : '?',
      inv: new THREE.Matrix4().copy(cube.mesh.matrixWorld).invert(),
      lo: [bb.min.x, bb.min.y, bb.min.z],
      hi: [bb.max.x, bb.max.y, bb.max.z]
    };
  }

  var shellObbs = shellCubes.map(makeObb);
  var hitObbs = hitCubes.map(makeObb);

  var min = [Infinity, Infinity, Infinity], max = [-Infinity, -Infinity, -Infinity];
  shellObbs.forEach(function (o) {
    var m = new THREE.Matrix4().copy(o.inv).invert();
    var v = new THREE.Vector3();
    for (var i = 0; i < 8; i++) {
      v.set((i & 1) ? o.hi[0] : o.lo[0], (i & 2) ? o.hi[1] : o.lo[1], (i & 4) ? o.hi[2] : o.lo[2]);
      v.applyMatrix4(m);
      var a = [v.x, v.y, v.z];
      for (var k = 0; k < 3; k++) { if (a[k] < min[k]) min[k] = a[k]; if (a[k] > max[k]) max[k] = a[k]; }
    }
  });

  var center = CONFIG.center === 'auto'
    ? [(min[0] + max[0]) / 2, (min[1] + max[1]) / 2, (min[2] + max[2]) / 2]
    : CONFIG.center.slice();

  function rayInterval(o, ox, oy, oz, dx, dy, dz, maxT) {
    var m = o.inv.elements;
    var p = [
      m[0] * ox + m[4] * oy + m[8] * oz + m[12],
      m[1] * ox + m[5] * oy + m[9] * oz + m[13],
      m[2] * ox + m[6] * oy + m[10] * oz + m[14]
    ];
    var d = [
      m[0] * dx + m[4] * dy + m[8] * dz,
      m[1] * dx + m[5] * dy + m[9] * dz,
      m[2] * dx + m[6] * dy + m[10] * dz
    ];
    var t0 = -1e9, t1 = 1e9;
    for (var k = 0; k < 3; k++) {
      if (Math.abs(d[k]) < 1e-9) {
        if (p[k] < o.lo[k] || p[k] > o.hi[k]) return null;
        continue;
      }
      var a = (o.lo[k] - p[k]) / d[k], b = (o.hi[k] - p[k]) / d[k];
      if (a > b) { var t = a; a = b; b = t; }
      if (a > t0) t0 = a;
      if (b < t1) t1 = b;
      if (t0 > t1) return null;
    }
    if (t1 < 0 || t0 > maxT) return null;
    return [t0, t1];
  }

  var golden = Math.PI * (3 - Math.sqrt(5));
  var rays = CONFIG.rays;
  var hitShell = 0, sealed = 0, escaping = 0, protruding = 0;
  var reachHist = {}, load = {}, azBins = {}, elBins = {}, escapeEl = {}, escapeAz = {};
  var leaks = [];

  for (var i = 0; i < rays; i++) {
    var cz = 1 - 2 * (i + 0.5) / rays;
    var cr = Math.sqrt(Math.max(0, 1 - cz * cz));
    var phi = i * golden;
    var d = [Math.cos(phi) * cr, cz, Math.sin(phi) * cr];

    var surface = CONFIG.surface === 'outer' ? -1 : Infinity;
    for (var s = 0; s < shellObbs.length; s++) {
      var iv = rayInterval(shellObbs[s], center[0], center[1], center[2], d[0], d[1], d[2], CONFIG.maxDistance);
      if (!iv) continue;
      if (CONFIG.surface === 'outer') { if (iv[1] > surface) surface = iv[1]; }
      else if (iv[0] > 1e-6 && iv[0] < surface) surface = iv[0];
    }
    var az = Math.atan2(d[2], d[0]) * 180 / Math.PI;
    var el = Math.asin(Math.max(-1, Math.min(1, d[1]))) * 180 / Math.PI;
    var ab = Math.floor((az + 180) / 360 * CONFIG.azimuthBins);
    var eb = Math.floor((el + 90) / 180 * CONFIG.elevationBins);

    if (!isFinite(surface) || surface < 0) {
      escaping++;
      escapeAz[ab] = (escapeAz[ab] || 0) + 1;
      escapeEl[eb] = (escapeEl[eb] || 0) + 1;
      continue;
    }
    hitShell++;

    var t1max = -1, bestBone = null;
    for (var h = 0; h < hitObbs.length; h++) {
      var hv = rayInterval(hitObbs[h], center[0], center[1], center[2], d[0], d[1], d[2], CONFIG.maxDistance);
      if (!hv) continue;
      if (hv[1] > t1max) { t1max = hv[1]; bestBone = hitObbs[h].bone; }
    }

    if (t1max < 0) {
      reachHist.none = (reachHist.none || 0) + 1;
      leaks.push({ dir: r3(d), az: round(az), el: round(el), reach: null });
      azBins[ab] = (azBins[ab] || 0) + 1;
      elBins[eb] = (elBins[eb] || 0) + 1;
      continue;
    }

    var reach = t1max - surface;
    var key = reach < -5 ? '<-5' : reach < -2.5 ? '-5..-2.5' : reach < -0.5 ? '-2.5..-0.5' : reach < 0.5 ? '-0.5..0.5' : reach < 2 ? '0.5..2' : '>2';
    reachHist[key] = (reachHist[key] || 0) + 1;
    if (reach > CONFIG.toleranceOutward) protruding++;

    if (reach >= -CONFIG.toleranceInward) {
      sealed++;
      load[bestBone] = (load[bestBone] || 0) + 1;
    } else {
      leaks.push({ dir: r3(d), az: round(az), el: round(el), reach: round(reach) });
      azBins[ab] = (azBins[ab] || 0) + 1;
      elBins[eb] = (elBins[eb] || 0) + 1;
    }
  }

  function binRows(bins, n, label, from, span) {
    return Object.keys(bins).map(function (k) {
      var i = parseInt(k, 10);
      return { bin: label + ' ' + Math.round(from + i * span / n) + '..' + Math.round(from + (i + 1) * span / n) + '°', count: bins[k] };
    }).sort(function (a, b) { return b.count - a.count; });
  }

  return {
    config: CONFIG,
    bounds: { min: min.map(round), max: max.map(round) },
    center: r3(center),
    shellCubes: shellCubes.length,
    hitCubes: hitCubes.length,
    rays: rays,
    hitShell: hitShell,
    escaping: escaping,
    sealed: sealed,
    coverage: hitShell ? Math.round(sealed / hitShell * 1000) / 10 : null,
    leaks: hitShell - sealed,
    protruding: protruding,
    reachHistogram: reachHist,
    leaksByAzimuth: binRows(azBins, CONFIG.azimuthBins, 'az', -180, 360),
    leaksByElevation: binRows(elBins, CONFIG.elevationBins, 'el', -90, 180),
    escapingByElevation: binRows(escapeEl, CONFIG.elevationBins, 'el', -90, 180),
    loadPerBone: Object.keys(load).sort(function (a, b) { return load[b] - load[a]; })
      .map(function (k) { return { bone: k, rays: load[k] }; }),
    leakSamples: leaks.slice(0, 10)
  };
})()
