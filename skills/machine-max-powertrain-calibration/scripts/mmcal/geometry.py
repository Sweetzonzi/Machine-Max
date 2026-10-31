"""模型几何：把 Blockbench 的 cube 换算成格，并求气动面积与轮半径。

口径两条，都与模组源码对齐：

- cube 的坐标除以 16，绕自身 pivot 旋转，X/Y 分量取反（Spark-Core 的 `ModelModule` 与 `OCube`）。
- 气动面积按 `AbstractPart.calculateProjectedArea` 的 `4 × 半长 × 半长`。

轮半径不走 JSON 的 `abs_wheel_radius`：物理用的是 `type: "wheel"` 的碰撞球，
半径就是该 cube 在 y 向尺寸的一半。
"""

from __future__ import annotations

import math
from pathlib import Path

from .jsonc import load_jsonc


def load_geo(path: Path) -> dict:
    """返回 bone 名 -> {"cubes": [cube(dict)], "locators": [定位器名]}。"""
    data = load_jsonc(path)
    geometries = data.get("minecraft:geometry") or []
    if not geometries:
        raise SystemExit(f"{path}: 没有 minecraft:geometry")
    bones = {}
    for bone in geometries[0].get("bones", []):
        locators = bone.get("locators") or {}
        bones[bone.get("name", "")] = {
            "cubes": bone.get("cubes", []),
            "locators": sorted(locators.keys()) if isinstance(locators, dict) else [],
        }
    return bones


def bone_cubes(geo: dict, name: str) -> list:
    """某骨骼下的 cube 列表；缺失时为空。"""
    bone = geo.get(name)
    if not bone:
        return []
    return (bone.get("cubes") or []) if isinstance(bone, dict) else bone


def bone_locator_count(geo: dict, name: str) -> int:
    """一个气动键实际展开成几个气动计算点。

    配置的键若是骨骼名，则展开为该骨骼下的全部定位器；否则键本身就是定位器名，
    空串按子零件原点算一个点。数量决定同一份系数施加几遍。
    """
    if not name:
        return 1
    bone = geo.get(name)
    if not isinstance(bone, dict):
        return 1
    return max(1, len(bone.get("locators") or []))


def rotate_point(point, pivot, rotation) -> tuple:
    """绕 pivot 旋转（角度制，X/Y 取反），返回旋转后的点。"""
    ax, ay, az = (math.radians(-rotation[0]), math.radians(-rotation[1]),
                  math.radians(rotation[2]))
    cx, sx = math.cos(ax), math.sin(ax)
    cy, sy = math.cos(ay), math.sin(ay)
    cz, sz = math.cos(az), math.sin(az)
    # R = Rz · Ry · Rx
    matrix = ((cz * cy, cz * sy * sx - sz * cx, cz * sy * cx + sz * sx),
              (sz * cy, sz * sy * sx + cz * cx, sz * sy * cx - cz * sx),
              (-sy, cy * sx, cy * cx))
    dx, dy, dz = (point[0] - pivot[0], point[1] - pivot[1], point[2] - pivot[2])
    return tuple(matrix[i][0] * dx + matrix[i][1] * dy + matrix[i][2] * dz + pivot[i]
                 for i in range(3))


def cube_box(cube) -> tuple:
    """cube 的轴对齐包围盒（格）。"""
    origin = cube.get("origin") or [0, 0, 0]
    size = cube.get("size") or [0, 0, 0]
    rotation = cube.get("rotation") or [0, 0, 0]
    pivot = cube.get("pivot") or [origin[i] + size[i] / 2 for i in range(3)]
    points = []
    for dx in (0.0, size[0]):
        for dy in (0.0, size[1]):
            for dz in (0.0, size[2]):
                points.append(rotate_point((origin[0] + dx, origin[1] + dy, origin[2] + dz),
                                           pivot, rotation))
    lo = [min(p[i] for p in points) / 16.0 for i in range(3)]
    hi = [max(p[i] for p in points) / 16.0 for i in range(3)]
    return (lo[0], lo[1], lo[2], hi[0], hi[1], hi[2])


def union(boxes):
    boxes = [b for b in boxes if b]
    if not boxes:
        return None
    return (min(b[0] for b in boxes), min(b[1] for b in boxes), min(b[2] for b in boxes),
            max(b[3] for b in boxes), max(b[4] for b in boxes), max(b[5] for b in boxes))


def box_areas(box) -> tuple:
    """代码 calculateProjectedArea 的口径：4 × 半长 × 半长。"""
    if box is None:
        return (0.0, 0.0, 0.0)
    ex = (box[3] - box[0]) / 2
    ey = (box[4] - box[1]) / 2
    ez = (box[5] - box[2]) / 2
    return (4 * ey * ez, 4 * ex * ez, 4 * ex * ey)


def hitbox_boxes(sub_part) -> list:
    """子零件各碰撞箱的包围盒（格）；轮子按碰撞球折算成等高的方盒。"""
    boxes = []
    for name, hitbox in sub_part.hitboxes.items():
        cubes = bone_cubes(sub_part.geo, name)
        kind = hitbox.get("type")
        for cube in cubes:
            if kind == "wheel":
                box = cube_box(cube)
                cy = (box[1] + box[4]) / 2
                r = (box[4] - box[1]) / 2
                boxes.append((box[0], cy - r, box[2], box[3], cy + r, box[5]))
            else:
                boxes.append(cube_box(cube))
    return boxes


def silhouette_area(boxes, axis: int, cell: float = 0.1) -> float:
    """把各包围盒投影到垂直于 axis 的平面上，栅格化求并集面积（m²）。"""
    others = [i for i in (0, 1, 2) if i != axis]
    covered = set()
    for box in boxes:
        lo_u, hi_u = box[others[0]], box[others[0] + 3]
        lo_v, hi_v = box[others[1]], box[others[1] + 3]
        for i in range(int(math.floor(lo_u / cell)), int(math.ceil(hi_u / cell))):
            for j in range(int(math.floor(lo_v / cell)), int(math.ceil(hi_v / cell))):
                covered.add((i, j))
    return len(covered) * cell * cell


def sub_part_area(sub_part) -> tuple:
    """单个子零件的三轴气动面积：显式 `projected_area` 优先，其余按包围盒。"""
    areas = box_areas(union(hitbox_boxes(sub_part)))
    return tuple(a if p <= 0 else p for a, p in zip(areas, sub_part.projected_area))


def wheel_radii(sub_part) -> list:
    """该子零件上所有 `type: "wheel"` 碰撞球的半径（米）。"""
    radii = []
    for name, hitbox in sub_part.hitboxes.items():
        if hitbox.get("type") != "wheel":
            continue
        for cube in bone_cubes(sub_part.geo, name):
            size = cube.get("size") or [0, 0, 0]
            radii.append(abs(size[1]) / 32.0)
    return radii


def wheel_material(sub_part) -> str | None:
    for hitbox in sub_part.hitboxes.values():
        if hitbox.get("type") == "wheel" and hitbox.get("material"):
            return hitbox["material"]
    return None
