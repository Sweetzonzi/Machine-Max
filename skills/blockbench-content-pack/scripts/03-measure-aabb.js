(function () {
    var CONFIG = {
        targets: ['Roadwheel_Left_1'],
        exclude: 'HitBox',
        alsoReportHitBoxes: true
    };

    Project.model_3d.updateMatrixWorld(true);

    function round(v) { return Math.round(v * 1000) / 1000; }
    function r3(a) { return [round(a[0]), round(a[1]), round(a[2])]; }

    function collectCubes(node, out, exclude) {
        (node.children || []).forEach(function (child) {
            if (child instanceof Cube) {
                out.push(child);
            }
            if (child.children && (!exclude || !exclude.test(child.name))) {
                collectCubes(child, out, exclude);
            }
        });
        return out;
    }

    function boxOfCubes(cubes) {
        var min = [Infinity, Infinity, Infinity];
        var max = [-Infinity, -Infinity, -Infinity];
        var v = new THREE.Vector3();
        cubes.forEach(function (cube) {
            var geo = cube.mesh.geometry;
            if (!geo.boundingBox) geo.computeBoundingBox();
            var bb = geo.boundingBox;
            for (var i = 0; i < 8; i++) {
                v.set(
                    (i & 1) ? bb.max.x : bb.min.x,
                    (i & 2) ? bb.max.y : bb.min.y,
                    (i & 4) ? bb.max.z : bb.min.z
                );
                v.applyMatrix4(cube.mesh.matrixWorld);
                var p = [v.x, v.y, v.z];
                for (var k = 0; k < 3; k++) {
                    if (p[k] < min[k]) min[k] = p[k];
                    if (p[k] > max[k]) max[k] = p[k];
                }
            }
        });
        if (min[0] === Infinity) return null;
        return {
            min: r3(min), max: r3(max),
            size: r3([max[0] - min[0], max[1] - min[1], max[2] - min[2]]),
            centre: r3([(min[0] + max[0]) / 2, (min[1] + max[1]) / 2, (min[2] + max[2]) / 2])
        };
    }

    function measure(bone, exclude) {
        var cubes = [];
        (bone.children || []).forEach(function (child) {
            if (child instanceof Cube) cubes.push(child);
            if (child.children && (!exclude || !exclude.test(child.name))) collectCubes(child, cubes, exclude);
        });
        var row = { bone: bone.name, parent: bone.parent ? bone.parent.name : null, cubes: cubes.length };
        var box = boxOfCubes(cubes);
        if (box) { row.min = box.min; row.max = box.max; row.size = box.size; row.centre = box.centre; }
        return row;
    }

    function findHitBoxCubes(bone) {
        var out = [];
        (bone.children || []).forEach(function (child) {
            if (!child.children) return;
            if (/HitBox/i.test(child.name)) collectCubes(child, out, null);
        });
        return out;
    }

    var exclude = CONFIG.exclude ? new RegExp(CONFIG.exclude) : null;
    var rows = [];
    var seen = {};
    CONFIG.targets.forEach(function (pattern) {
        var regex = (pattern.charAt(0) === '/' && pattern.charAt(pattern.length - 1) === '/')
            ? new RegExp(pattern.slice(1, -1))
            : new RegExp('^' + pattern.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '$');
        Group.all.forEach(function (bone) {
            if (!regex.test(bone.name) || seen[bone.uuid]) return;
            seen[bone.uuid] = true;
            var row = measure(bone, exclude);
            if (CONFIG.alsoReportHitBoxes) {
                var hitBox = boxOfCubes(findHitBoxCubes(bone));
                if (hitBox) row.hitBox = { size: hitBox.size, centre: hitBox.centre, min: hitBox.min, max: hitBox.max };
            }
            rows.push(row);
        });
    });

    return { count: rows.length, unit: 'bb units (blockbench model space)', rows: rows };
})()
