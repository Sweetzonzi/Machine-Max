"""`mmcal` —— Machine-Max 动力链静态标定的公共库。

各入口脚本（`calibrate.py` / `curve.py` / `drag.py` / `gears.py`）都只是这里的一层壳：

    Registry        内容包索引（id → 定义文件）
    solve / Solution  档位表、极速、绑定约束
    Engine / Motor  两种功率曲线
    Gearbox / Transmission / Wheel / Material  传动与车轮侧定义
    build_vehicle   模板 → 子零件列表
    geometry        模型几何、气动面积、轮半径

也可以当库用：

    from mmcal import Registry, solve, Engine
"""

from __future__ import annotations

from .constants import BLOCK_FRICTION, BLOCK_ROLLING, CD, G, KMH, PS, RHO
from .driveline import Gearbox, Material, Transmission, Wheel
from .geometry import (box_areas, cube_box, hitbox_boxes, load_geo, rotate_point,
                       silhouette_area, sub_part_area, union, wheel_material, wheel_radii)
from .jsonc import load_jsonc, strip_jsonc
from .pack import Entry, Pack, Registry, default_pack_root, model_path
from .requirements import parse_accel, parse_power, requirements
from .solve import Solution, solve
from .source import Engine, Motor, PowerCurve
from .vehicle import SubPartSpec, Vehicle, build_vehicle, resolve_vehicle

__all__ = [
    "BLOCK_FRICTION", "BLOCK_ROLLING", "CD", "G", "KMH", "PS", "RHO",
    "Engine", "Entry", "Gearbox", "Material", "Motor", "Pack", "PowerCurve",
    "Registry", "Solution", "SubPartSpec", "Transmission", "Vehicle", "Wheel",
    "box_areas", "build_vehicle", "cube_box", "default_pack_root", "hitbox_boxes",
    "load_geo", "load_jsonc", "model_path", "parse_accel", "parse_power",
    "requirements", "resolve_vehicle", "rotate_point", "silhouette_area", "solve",
    "strip_jsonc", "sub_part_area", "union", "wheel_material", "wheel_radii",
]
