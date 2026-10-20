(function () {
  var CONFIG = {
    hullGroup: 'Hull',
    minFaceArea: 60,
    topPlanes: 12
  };

  function sub(a, b) { return [a[0] - b[0], a[1] - b[1], a[2] - b[2]]; }
  function dot(a, b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
  function cross(a, b) { return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]; }
  function len(a) { return Math.sqrt(dot(a, a)); }
  function norm(a) { var l = len(a) || 1; return [a[0] / l, a[1] / l, a[2] / l]; }
  function r2(v) { return Math.round(v * 100) / 100; }

  function countCubes(group) {
    var n = 0;
    (group.children || []).forEach(function (c) {
      if (c.type === 'cube') n++;
      else if (c.type === 'group') n += countCubes(c);
    });
    return n;
  }

  function tree(group, depth, out) {
    if (depth > 2) return;
    out.push({ depth: depth, name: group.name, pivot: group.origin.map(r2),
               cubes: countCubes(group), hidden: group.visibility === false, noExport: group.export === false });
    (group.children || []).forEach(function (c) {
      if (c.type === 'group') tree(c, depth + 1, out);
    });
  }

  var out = {
    project: {
      name: Project.name,
      saved: Project.saved,
      format: Project.format ? Project.format.id : null,
      box_uv: Project.box_uv,
      texture: [Project.texture_width, Project.texture_height],
      visible_box: Project.visible_box,
      groups: Project.groups.length,
      cubes: Project.elements.filter(function (e) { return e.type === 'cube'; }).length,
      locators: Project.elements.filter(function (e) { return e.type === 'locator'; }).length
    },
    tree: [],
    planes: []
  };

  (Project.outliner || []).forEach(function (g) {
    if (g.type === 'group') tree(g, 0, out.tree);
  });

  var hull = Project.groups.find(function (g) { return g.name === CONFIG.hullGroup; });
  if (hull) {
    var faces = [];
    hull.children.forEach(function (c) {
      if (c.type !== 'cube' || !c.mesh) return;
      c.mesh.updateMatrixWorld(true);
      var m = c.mesh.matrixWorld;
      var o = c.origin || [0, 0, 0];
      var a = c.from.map(function (v, i) { return v - o[i]; });
      var b = c.to.map(function (v, i) { return v - o[i]; });
      var lo = [Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2])];
      var hi = [Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2])];
      function W(p) { var v = new THREE.Vector3(p[0], p[1], p[2]).applyMatrix4(m); return [v.x, v.y, v.z]; }
      var defs = [
        [[hi[0], lo[1], lo[2]], [hi[0], hi[1], lo[2]], [hi[0], hi[1], hi[2]], [hi[0], lo[1], hi[2]]],
        [[lo[0], lo[1], lo[2]], [lo[0], lo[1], hi[2]], [lo[0], hi[1], hi[2]], [lo[0], hi[1], lo[2]]],
        [[lo[0], hi[1], lo[2]], [hi[0], hi[1], lo[2]], [hi[0], hi[1], hi[2]], [lo[0], hi[1], hi[2]]],
        [[lo[0], lo[1], lo[2]], [lo[0], lo[1], hi[2]], [hi[0], lo[1], hi[2]], [hi[0], lo[1], lo[2]]],
        [[lo[0], lo[1], hi[2]], [hi[0], lo[1], hi[2]], [hi[0], hi[1], hi[2]], [lo[0], hi[1], hi[2]]],
        [[lo[0], lo[1], lo[2]], [lo[0], hi[1], lo[2]], [hi[0], hi[1], lo[2]], [hi[0], lo[1], lo[2]]]
      ];
      defs.forEach(function (d) {
        var p = d.map(W);
        var cr = cross(sub(p[1], p[0]), sub(p[2], p[0]));
        var area = len(cr);
        if (area < CONFIG.minFaceArea) return;
        faces.push({ area: area, normal: norm(cr),
                     centre: [(p[0][0] + p[2][0]) / 2, (p[0][1] + p[2][1]) / 2, (p[0][2] + p[2][2]) / 2] });
      });
    });

    var planes = [];
    faces.forEach(function (f) {
      var s = (f.normal[0] > 0 || (f.normal[0] === 0 && (f.normal[1] > 0 || (f.normal[1] === 0 && f.normal[2] >= 0)))) ? 1 : -1;
      var n = [f.normal[0] * s, f.normal[1] * s, f.normal[2] * s];
      var off = dot(n, f.centre);
      var hit = planes.find(function (p) { return dot(p.normal, n) > 0.999 && Math.abs(p.offset - off) < 1.5; });
      if (!hit) { hit = { normal: n, offset: off, area: 0, count: 0, best: f }; planes.push(hit); }
      hit.area += f.area;
      hit.count++;
      if (f.area > hit.best.area) hit.best = f;
    });
    planes.sort(function (x, y) { return y.best.area - x.best.area; });
    out.planes = planes.slice(0, CONFIG.topPlanes).map(function (p) {
      return { normal: p.normal.map(r2), offset: r2(p.offset), maxArea: Math.round(p.best.area),
               sumArea: Math.round(p.area), faces: p.count, centre: p.best.centre.map(r2) };
    });
  }

  return JSON.stringify(out);
})()
