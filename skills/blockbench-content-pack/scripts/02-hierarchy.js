(function () {
  var CONFIG = {
    rootName: 'MTLB',
    rootPivot: [0, 0, 0],
    groups: [
      { name: 'AllHull', parent: 'Root', pivot: [0, 20, -5] },
      { name: 'Hull', parent: 'AllHull', pivot: [0, 20, -5] }
    ],
    moveCubesFrom: 'Root',
    moveCubesTo: 'Hull',
    reparent: ['Hatch_All', 'Suspension_All', 'Track__Left', 'Track__Right', 'Radiator', 'RearviewMirror', 'Light_All'],
    reparentTo: 'AllHull',
    pivots: {},
    noExport: ['Track_Left', 'Track_Right']
  };

  var log = { created: [], movedCubes: 0, reparented: [], pivots: [], noExport: [], errors: [] };

  function byName(n) {
    var hit = null;
    Project.groups.forEach(function (g) { if (g.name === n && !hit) hit = g; });
    return hit;
  }

  var root = Project.outliner.find(function (n) { return n.type === 'group' && n.name === CONFIG.rootName; });
  if (!root) return JSON.stringify({ errors: ['root group not found: ' + CONFIG.rootName] });

  Undo.initEdit({ outliner: true });
  try {
    root.name = 'Root';
    root.origin = CONFIG.rootPivot.slice();

    CONFIG.groups.forEach(function (spec) {
      var g = byName(spec.name);
      if (!g) {
        g = new Group({ name: spec.name, origin: spec.pivot.slice() }).init();
        log.created.push(spec.name);
      }
      var parent = byName(spec.parent);
      if (!parent) { log.errors.push('parent missing: ' + spec.parent); return; }
      if (g === parent) { log.errors.push('refuse self-parent: ' + spec.name); return; }
      g.addTo(parent);
      g.origin = spec.pivot.slice();
    });

    var from = byName(CONFIG.moveCubesFrom);
    var to = byName(CONFIG.moveCubesTo);
    if (from && to) {
      from.children.filter(function (c) { return c.type === 'cube'; }).forEach(function (c) {
        c.addTo(to);
        log.movedCubes++;
      });
    }

    var target = byName(CONFIG.reparentTo);
    CONFIG.reparent.forEach(function (name) {
      var g = byName(name);
      if (!g || !target) { log.errors.push('reparent target missing: ' + name); return; }
      if (g === target || g.isChildOf(target)) { log.errors.push('refuse cycle: ' + name); return; }
      g.addTo(target);
      log.reparented.push(name);
    });

    Object.keys(CONFIG.pivots).forEach(function (name) {
      var g = byName(name);
      if (!g) { log.errors.push('pivot target missing: ' + name); return; }
      g.origin = CONFIG.pivots[name].slice();
      log.pivots.push(name);
    });

    CONFIG.noExport.forEach(function (name) {
      var g = byName(name);
      if (!g) { log.errors.push('no-export target missing: ' + name); return; }
      g.export = false;
      log.noExport.push(name);
      (g.children || []).forEach(function (c) { if (c.type === 'group') c.export = false; });
    });
  } catch (e) {
    log.errors.push(String((e && e.stack) || e));
  } finally {
    Undo.finishEdit('restructure hierarchy');
  }

  var selfRef = Project.groups.filter(function (g) { return (g.children || []).indexOf(g) >= 0; });
  log.selfReferencingGroups = selfRef.map(function (g) { return g.name; });
  log.counts = { groups: Project.groups.length, cubes: Project.elements.filter(function (e) { return e.type === 'cube'; }).length };
  return JSON.stringify(log);
})()
