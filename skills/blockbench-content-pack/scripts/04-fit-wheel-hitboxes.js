(function () {
    var CONFIG = {
        dryRun: true,
        fit: [
            { pattern: '/^Roadwheel_(Left|Right)_[1-6]$/', diameter: 13.94 },
            { pattern: '/^SprocketWheel_(Left|Right)$/', diameter: 10.59 },
            { pattern: '/^IdlerWheel_(Left|Right)$/', diameter: 10.74 }
        ]
    };

    function round(v) { return Math.round(v * 1000) / 1000; }

    function toRegex(pattern) {
        if (pattern.charAt(0) === '/' && pattern.charAt(pattern.length - 1) === '/') {
            return new RegExp(pattern.slice(1, -1));
        }
        return new RegExp('^' + pattern.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '$');
    }

    function hitBoxCubes(bone) {
        var cubes = [];
        (bone.children || []).forEach(function (child) {
            if (!child.children || !/HitBox/i.test(child.name)) return;
            child.children.forEach(function (cube) {
                if (cube instanceof Cube) cubes.push(cube);
            });
        });
        return cubes;
    }

    var planned = [];
    CONFIG.fit.forEach(function (rule) {
        var regex = toRegex(rule.pattern);
        Group.all.forEach(function (bone) {
            if (!regex.test(bone.name)) return;
            var cubes = hitBoxCubes(bone);
            if (cubes.length !== 1) {
                planned.push({ bone: bone.name, skipped: 'hit box cubes = ' + cubes.length });
                return;
            }
            var cube = cubes[0];
            var half = rule.diameter / 2;
            var centre = [
                (cube.from[0] + cube.to[0]) / 2,
                (cube.from[1] + cube.to[1]) / 2,
                (cube.from[2] + cube.to[2]) / 2
            ];
            var halfX = Math.abs(cube.to[0] - cube.from[0]) / 2;
            var next = {
                from: [centre[0] - halfX, centre[1] - half, centre[2] - half],
                to: [centre[0] + halfX, centre[1] + half, centre[2] + half]
            };
            var same = Math.abs(next.from[1] - cube.from[1]) < 0.0005 &&
                Math.abs(next.from[2] - cube.from[2]) < 0.0005 &&
                Math.abs(next.to[1] - cube.to[1]) < 0.0005 &&
                Math.abs(next.to[2] - cube.to[2]) < 0.0005;
            planned.push({
                bone: bone.name, cube: cube.name, diameter: rule.diameter, unchanged: same,
                before: { from: [round(cube.from[0]), round(cube.from[1]), round(cube.from[2])], to: [round(cube.to[0]), round(cube.to[1]), round(cube.to[2])] },
                after: { from: [round(next.from[0]), round(next.from[1]), round(next.from[2])], to: [round(next.to[0]), round(next.to[1]), round(next.to[2])] },
                cubeRef: cube, next: next
            });
        });
    });

    var changed = planned.filter(function (p) { return p.cubeRef && !p.unchanged; });
    if (!CONFIG.dryRun && changed.length) {
        Undo.initEdit({ outliner: true });
        changed.forEach(function (p) {
            var cube = p.cubeRef;
            for (var i = 0; i < 3; i++) {
                cube.from[i] = p.next.from[i];
                cube.to[i] = p.next.to[i];
            }
            if (cube.preview_controller) {
                cube.preview_controller.updateGeometry(cube);
                cube.preview_controller.updateTransform(cube);
            }
        });
        Undo.finishEdit('fit wheel hit boxes');
    }

    return {
        dryRun: CONFIG.dryRun,
        planned: planned.map(function (p) {
            var copy = {};
            Object.keys(p).forEach(function (k) { if (k !== 'cubeRef' && k !== 'next') copy[k] = p[k]; });
            return copy;
        }),
        toChange: changed.length
    };
})()
