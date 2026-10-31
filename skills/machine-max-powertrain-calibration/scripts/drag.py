#!/usr/bin/env python3
"""阻力分解：逐子零件的气动面积与各速度点的阻力。

回答的是「阻力花在哪」：哪块零件最吃风、滚阻占多少、某个速度需要多少轮端功率。
不涉及动力源与传动，所以任何整车都能跑。

用法示例：

    python drag.py --pack Machine-Max_K17
    python drag.py --pack Machine-Max_K17 --speeds 30,60,90 --area 13.4
"""

from __future__ import annotations

import argparse
import json
import math
import sys
from pathlib import Path

from mmcal import (BLOCK_ROLLING, CD, G, KMH, RHO, Material, hitbox_boxes, resolve_vehicle,
                   silhouette_area, sub_part_area, wheel_material)
from mmcal.cli import add_common, flush_warnings, load_pack, pick_template

DEFAULT_SPEEDS = (20, 40, 60, 80, 100, 120)


def aero(area_z: float, speed: float) -> float:
    """气动阻力，速度取 m/s。"""
    return 0.5 * RHO * CD * area_z * speed * speed


def rolling(c_rr: float, mass: float) -> float:
    return c_rr * mass * G


def part_rows(vehicle) -> list:
    """逐子零件：质量与三轴气动面积，按迎风面积降序。"""
    rows = []
    for sp in vehicle.sub_parts:
        x, y, z = sub_part_area(sp)
        rows.append(dict(name=f"{sp.part_id.split(':')[-1]}/{sp.name}", mass=sp.mass,
                         x=x, y=y, z=z))
    return sorted(rows, key=lambda r: r["z"], reverse=True)


def report_text(vehicle, material, speeds, area_override, total_boxes, occupant_mass=60.0):
    parts = part_rows(vehicle)
    area_sum = [sum(r[k] for r in parts) for k in "xyz"]
    silhouette = tuple(silhouette_area(total_boxes, axis) for axis in (0, 1, 2))
    area = tuple(float(x) for x in area_override) if area_override else silhouette
    c_rr = material.c_rr * BLOCK_ROLLING
    mass = vehicle.mass + occupant_mass
    f_roll = rolling(c_rr, mass)

    lines = []
    push = lines.append
    mass_label = "空车运行质量" if occupant_mass == 0 else "单人运行质量"
    push(f"# {vehicle.name} 阻力分解")
    push("")
    push("## 整车")
    push("| 项 | 值 |")
    push("|---|---|")
    push(f"| 整备质量（空车） | {vehicle.mass:,.0f} kg |")
    push(f"| {mass_label} | {mass:,.0f} kg（乘员 {occupant_mass:g} kg） |")
    push(f"| 轮胎材质 | `{material.source}`，`C_rr` {c_rr:.4f} |")
    push(f"| 滚阻 | {c_rr:.4f} × {mass:,.0f} × 9.81 = {f_roll / 1000:.2f} kN |")
    push(f"| 气动面积 X/Y/Z（轮廓） | {area[0]:.2f} / {area[1]:.2f} / {area[2]:.2f} m² |")
    push(f"| 气动面积 X/Y/Z（逐零件相加） | {area_sum[0]:.2f} / {area_sum[1]:.2f} / "
         f"{area_sum[2]:.2f} m² |")
    push("")
    push("## 逐子零件气动面积")
    push("| 子零件 | 质量 (kg) | A_x (m²) | A_y (m²) | A_z (m²) | @ 100 km/h (N) |")
    push("|---|---|---|---|---|---|")
    for row in parts:
        push(f"| {row['name']} | {row['mass']:,.0f} | {row['x']:.2f} | {row['y']:.2f} | "
             f"{row['z']:.2f} | {aero(row['z'], 100 / KMH):.0f} |")
    push(f"| **合计（相加）** | {vehicle.mass:,.0f} | {area_sum[0]:.2f} | {area_sum[1]:.2f} | "
         f"{area_sum[2]:.2f} | {aero(area_sum[2], 100 / KMH):.0f} |")
    push("")
    push("> 逐零件相加不含遮挡；轮廓是包围盒投影的并集。游戏内遮挡与气动点会改变有效阻力，两者不是严格上下界。")
    push("")
    push("## 阻力 @ 速度")
    push("| v (km/h) | 气动（轮廓） | 气动（相加） | 滚阻 | 合计（轮廓） | 轮端功率（轮廓） |")
    push("|---|---|---|---|---|---|")
    for kmh in speeds:
        v = kmh / KMH
        f_aero = aero(area[2], v)
        f_aero_sum = aero(area_sum[2], v)
        total = f_aero + f_roll
        push(f"| {kmh:g} | {f_aero / 1000:.2f} kN | {f_aero_sum / 1000:.2f} kN | "
             f"{f_roll / 1000:.2f} kN | {total / 1000:.2f} kN | {total * v / 1000:.1f} kW |")
    push("")
    push("轮端功率是匀速维持该车速所需的功率；折算成曲轴功率要除以传动效率。")
    return "\n".join(lines), dict(
        vehicle=vehicle.name, mass=vehicle.mass, operating_mass=mass,
        occupant_mass=occupant_mass, c_rr=c_rr, rolling_force=f_roll,
        area=dict(x=area[0], y=area[1], z=area[2]),
        area_sum=dict(x=area_sum[0], y=area_sum[1], z=area_sum[2]),
        parts=parts,
        speeds=[dict(kmh=kmh, aero=aero(area[2], kmh / KMH),
                     aero_upper=aero(area_sum[2], kmh / KMH),
                     rolling=f_roll, total=aero(area[2], kmh / KMH) + f_roll,
                     wheel_power=(aero(area[2], kmh / KMH) + f_roll) * kmh / KMH)
                for kmh in speeds],
    )


def main(argv=None):
    parser = argparse.ArgumentParser(description="逐子零件气动面积与阻力分解")
    add_common(parser)
    parser.add_argument("--template", help="模板名；包内有多个模板时必填")
    parser.add_argument("--speeds", default=",".join(str(s) for s in DEFAULT_SPEEDS),
                        help=f"速度点 km/h，逗号分隔，默认 {','.join(map(str, DEFAULT_SPEEDS))}")
    parser.add_argument("--area", help="覆盖迎风面积，形如 13.4 或 28,42,13")
    parser.add_argument("--json", action="store_true", help="输出 JSON 而不是 Markdown")
    parser.add_argument("--occupant-mass", type=float, default=60.0,
                        help="一名乘员的质量 kg，默认 60；空车用 0")
    args = parser.parse_args(argv)
    if not math.isfinite(args.occupant_mass) or args.occupant_mass < 0:
        parser.error("--occupant-mass 必须是非负有限数")

    script_dir = Path(__file__).resolve().parent
    _, registry, pack = load_pack(args, script_dir)
    if pack is None:
        return 0
    vehicle = resolve_vehicle(registry, pack, pick_template(args, pack))

    material_ids = {wheel_material(sp) for sp in vehicle.sub_parts if wheel_material(sp)}
    if not material_ids:
        raise SystemExit("找不到轮面材质")
    if len(material_ids) > 1:
        registry.warnings.append(
            f"前后轮用了不同材质 {sorted(material_ids)}，按 {sorted(material_ids)[0]} 计算滚阻")
    material = Material.from_entry(registry.need("materials", sorted(material_ids)[0], "轮面碰撞箱"))

    try:
        speeds = [float(x) for x in args.speeds.split(",") if x.strip()]
    except ValueError:
        raise SystemExit(f"无法解析 --speeds {args.speeds}（形如 30,60,90）")
    area_override = None
    if args.area:
        parts = [float(x) for x in args.area.split(",")]
        area_override = parts if len(parts) == 3 else [parts[0]] * 3

    text, data = report_text(vehicle, material, speeds, area_override,
                             [b for sp in vehicle.sub_parts for b in hitbox_boxes(sp)],
                             args.occupant_mass)
    print(json.dumps(data, ensure_ascii=False, indent=2) if args.json else text)
    flush_warnings(registry)
    return 0


if __name__ == "__main__":
    sys.exit(main())
