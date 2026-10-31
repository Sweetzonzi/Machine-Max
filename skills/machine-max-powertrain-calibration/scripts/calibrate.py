#!/usr/bin/env python3
"""Machine-Max 动力链静态标定器（整车）。

从内容包 JSON 直接读出整车、动力源、变速箱、传动箱、车轮与阻力参数，算出档位表、
极速、功率利用率与约束残差。只用标准库，Python 3.9+ 即可运行。

用法示例：

    python calibrate.py --pack Machine-Max_K17
    python calibrate.py --pack Machine-Max_K17 --json
    python calibrate.py --pack Machine-Max_K17 --top-speed 60 --power "500 PS" --accel 50:3.0

只关心单个侧面时用同目录的 `curve.py`（功率曲线）、`drag.py`（阻力分解）、
`gears.py`（传动比链）。单位与语义以 SKILL.md、references/engineering-model.md 为准。
"""

from __future__ import annotations

import argparse
import math
import sys
from pathlib import Path

from mmcal import (Engine, Gearbox, Material, Motor, Transmission, drive_system,
                   requirements, resolve_vehicle, solve, wheel_material, wheel_radii)
from mmcal.cli import add_common, flush_warnings, load_pack, pick_template
from mmcal.report import report_json, report_text


def add_arguments(parser):
    add_common(parser)
    parser.add_argument("--template", help="模板 id 或文件名；包内有多个模板时必填")
    parser.add_argument("--json", action="store_true", help="输出 JSON 而不是 Markdown")
    parser.add_argument("--strict", action="store_true", help="有残差为负时以退出码 1 结束")
    parser.add_argument("--top-speed", type=float, help="目标极速 km/h")
    parser.add_argument("--power", help='给定功率，如 "500 PS"、"368 kW"')
    parser.add_argument("--accel", help="加速需求，形如 50:3.0（0→50 km/h 用 3 秒）")
    parser.add_argument("--grade", type=float, help="最大爬坡度（角度，度）")
    parser.add_argument("--gears", type=int, help="给定档位数，校验换挡收益")
    parser.add_argument("--area", help="覆盖包围盒气动面积，形如 10 或 5,8,10（X,Y,Z，m²）")
    parser.add_argument("--cd-area", type=float,
                        help="直接指定前向 Cd·A（m²），覆盖包内气动点")
    parser.add_argument("--occupant-mass", type=float, default=60.0,
                        help="一名乘员的质量 kg，默认 60；空车用 0")


def main(argv=None):
    parser = argparse.ArgumentParser(description="Machine-Max 动力链静态标定器")
    add_arguments(parser)
    args = parser.parse_args(argv)
    if not math.isfinite(args.occupant_mass) or args.occupant_mass < 0:
        parser.error("--occupant-mass 必须是非负有限数")

    script_dir = Path(__file__).resolve().parent
    _, registry, pack = load_pack(args, script_dir)
    if pack is None:
        return 0
    vehicle = resolve_vehicle(registry, pack, pick_template(args, pack))

    kind, source_ref, hybrid = vehicle.power_source_ref()
    if source_ref is None:
        raise SystemExit("整车没有 machine_max:engine 或 machine_max:motor 子系统")
    if hybrid:
        registry.warnings.append("整车同时有内燃机与电机（混动），本工具只取内燃机")
    sub_part, name, ref = source_ref
    entry = registry.need("subsystems", ref["definition"], sub_part.part_id)
    source = (Engine if kind == "engine" else Motor).from_entry(entry)

    gearbox_ref = next(vehicle.subsystem_refs(":gearbox"), None)
    transmission_ref = next(vehicle.subsystem_refs(":transmission"), None)
    drive = drive_system(vehicle, registry)

    if gearbox_ref:
        gearbox = Gearbox.from_entry(
            registry.need("subsystems", gearbox_ref[2]["definition"], gearbox_ref[0].part_id),
            name=str(gearbox_ref[2]["definition"]))
    else:
        gearbox = Gearbox(1.0, [1.0], 0.0, "")
        registry.warnings.append("整车没有 gearbox，挡位固定为 1.00")
    transmission = Transmission.from_ref(transmission_ref[2]) if transmission_ref else None

    wheel_subs = [sp for sp in vehicle.sub_parts if wheel_radii(sp)]
    material_ids = {wheel_material(sp) for sp in wheel_subs if wheel_material(sp)}
    if not material_ids:
        raise SystemExit("找不到轮面材质")
    if len(material_ids) > 1:
        registry.warnings.append(
            f"前后轮用了不同材质 {sorted(material_ids)}，按 {sorted(material_ids)[0]} 计算滚阻与附着")
    material = Material.from_entry(registry.need("materials", sorted(material_ids)[0], "轮面碰撞箱"))

    area_override = None
    if args.area:
        parts = [float(x) for x in args.area.split(",")]
        area_override = parts if len(parts) == 3 else [parts[0]] * 3

    sol = solve(vehicle, source, gearbox, drive, material, area_override,
                transmission, args.occupant_mass, args.cd_area)
    reqs = requirements(args, source, gearbox, sol)

    print(report_json(vehicle, source, gearbox, material, sol, reqs) if args.json
          else report_text(vehicle, source, gearbox, material, sol, reqs))
    flush_warnings(registry)
    if args.strict and any(row["ok"] is False for row in reqs):
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
