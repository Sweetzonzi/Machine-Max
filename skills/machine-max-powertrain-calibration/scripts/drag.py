#!/usr/bin/env python3
"""阻力分解：逐子零件的气动面积、阻力系数与各速度点的阻力。

回答的是「阻力花在哪」：哪块零件最吃风、它的前向二阶系数是多少、滚阻占多少、
某个速度需要多少轮端功率。不涉及动力源与传动，所以任何整车都能跑。

阻力口径与游戏一致：只有声明了 `hydrodynamics` 的子零件才有气动力，
前向二阶阻力 = 0.5 × ρ × c₂ × v² × A_z。整车按各气动点求和，不含遮挡。

用法示例：

    python drag.py --pack Machine-Max_K17
    python drag.py --pack Machine-Max_Sdkfz234 --speeds 30,60,90
    python drag.py --pack Machine-Max_Civilian --template senna_gtr
"""

from __future__ import annotations

import argparse
import json
import math
import sys
from pathlib import Path

from mmcal import (BLOCK_ROLLING, G, KMH, RHO, Material, drag_area, resolve_vehicle,
                   wheel_material)
from mmcal.aero import DEFAULT_QUADRATIC
from mmcal.cli import add_common, flush_warnings, load_pack, pick_template

DEFAULT_SPEEDS = (20, 40, 60, 80, 100, 120)


def aero(cd_area: float, speed: float) -> float:
    """气动阻力，速度取 m/s，`cd_area` 是 Cd·A（m²）。"""
    return 0.5 * RHO * cd_area * speed * speed


def rolling(c_rr: float, mass: float) -> float:
    return c_rr * mass * G


def part_rows(vehicle) -> list:
    """逐子零件：质量、前向面积、气动点与折出的 Cd·A，按 Cd·A 降序。"""
    rows = []
    for part in drag_area(vehicle)["parts"]:
        area = part["area"]
        rows.append(dict(name=f"{part['part'].split(':')[-1]}/{part['sub_part']}",
                         mass=part["mass"], area=area, locators=part["locators"],
                         cd_area=part["cd_area"],
                         c2=(part["cd_area"] / area) if area > 0 else 0.0))
    return sorted(rows, key=lambda r: r["cd_area"], reverse=True)


def report_text(vehicle, material, speeds, cd_override, occupant_mass=60.0):
    aero_info = drag_area(vehicle)
    parts = part_rows(vehicle)
    area_sum = sum(r["area"] for r in parts)
    cd_declared = aero_info["total"]
    cd_area = cd_override if cd_override is not None else cd_declared
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
    push(f"| 前向 `Cd·A` | {cd_declared:.2f} m² |")
    push(f"| 未声明气动系数的子零件 | {aero_info['default']} / {len(parts)}"
         f"（按默认 {DEFAULT_QUADRATIC:g} 计入） |")
    push(f"| 前向投影面积（逐零件相加） | {area_sum:.2f} m² |")
    push("")
    push("## 逐子零件气动面积")
    push("| 子零件 | 气动点 | A_z (m²) | 前向 c₂ | `Cd·A` (m²) | @ 100 km/h (N) |")
    push("|---|---|---|---|---|---|")
    for row in parts:
        push(f"| {row['name']} | {row['locators'] or '—'} | {row['area']:.2f} | "
             f"{row['c2']:.3f} | {row['cd_area']:.2f} | {aero(row['cd_area'], 100 / KMH):.0f} |")
    push(f"| **合计** | — | {area_sum:.2f} | — | {cd_declared:.2f} | "
         f"{aero(cd_declared, 100 / KMH):.0f} |")
    push("")
    push("> 「气动点」列是子零件声明的 `hydrodynamics` 键数；0（显示 `—`）表示没写，"
         "游戏按默认前向二阶系数 1、作用在子零件原点算一个点，「前向 c₂」列因此给 1.000。"
         "键写成骨骼名时展开成该骨骼下的全部定位器，几份系数就叠几遍力。"
         "求和不含遮挡：沿来流被同装配体里优先级不低的零件挡住时，实际阻力按命中比例折减。")
    push("")
    push("## 阻力 @ 速度")
    push("| v (km/h) | 气动 | 滚阻 | 合计 | 轮端功率 |")
    push("|---|---|---|---|---|")
    for kmh in speeds:
        v = kmh / KMH
        f_aero = aero(cd_area, v)
        total = f_aero + f_roll
        push(f"| {kmh:g} | {f_aero / 1000:.2f} kN | {f_roll / 1000:.2f} kN | "
             f"{total / 1000:.2f} kN | {total * v / 1000:.1f} kW |")
    push("")
    push("轮端功率是匀速维持该车速所需的功率；折算成曲轴功率要除以传动效率。")
    return "\n".join(lines), dict(
        vehicle=vehicle.name, mass=vehicle.mass, operating_mass=mass,
        occupant_mass=occupant_mass, c_rr=c_rr, rolling_force=f_roll,
        cd_area=cd_area, cd_area_declared=cd_declared,
        aero_parts_default=aero_info["default"], front_area_sum=area_sum,
        parts=parts,
        speeds=[dict(kmh=kmh, aero=aero(cd_area, kmh / KMH),
                     rolling=f_roll, total=aero(cd_area, kmh / KMH) + f_roll,
                     wheel_power=(aero(cd_area, kmh / KMH) + f_roll) * kmh / KMH)
                for kmh in speeds],
    )


def main(argv=None):
    parser = argparse.ArgumentParser(description="逐子零件气动面积与阻力分解")
    add_common(parser)
    parser.add_argument("--template", help="模板名；包内有多个模板时必填")
    parser.add_argument("--speeds", default=",".join(str(s) for s in DEFAULT_SPEEDS),
                        help=f"速度点 km/h，逗号分隔，默认 {','.join(map(str, DEFAULT_SPEEDS))}")
    parser.add_argument("--cd-area", type=float,
                        help="直接指定前向 Cd·A（m²），覆盖包内气动点")
    parser.add_argument("--json", action="store_true", help="输出 JSON 而不是 Markdown")
    parser.add_argument("--occupant-mass", type=float, default=60.0,
                        help="一名乘员的质量 kg，默认 60；空车用 0")
    args = parser.parse_args(argv)
    if not math.isfinite(args.occupant_mass) or args.occupant_mass < 0:
        parser.error("--occupant-mass 必须是非负有限数")
    if args.cd_area is not None and (not math.isfinite(args.cd_area) or args.cd_area < 0):
        parser.error("--cd-area 必须是非负有限数")

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

    text, data = report_text(vehicle, material, speeds, args.cd_area, args.occupant_mass)
    print(json.dumps(data, ensure_ascii=False, indent=2) if args.json else text)
    flush_warnings(registry)
    return 0


if __name__ == "__main__":
    sys.exit(main())
