#!/usr/bin/env python3
"""传动比链：挡位传动比 × 终传比 × 传动箱减速比，以及各档红线车速。

不参与扭矩分配，所以动力源缺失也能跑；有动力源时多一列红线车速。
要 `τ_w`、牵引力与极速用 `calibrate.py`。

用法示例：

    python gears.py --pack Machine-Max_Civilian --template mini_ev
    python gears.py --pack Machine-Max_K17 --json
"""

from __future__ import annotations

import argparse
import json
import math
import sys
from pathlib import Path

from mmcal import (KMH, Engine, Gearbox, Motor, Transmission, drive_system,
                   resolve_vehicle)
from mmcal.cli import add_common, flush_warnings, load_pack, pick_template


def collect_drive(vehicle, registry) -> dict:
    """取传动三段与驱动半径；动力源缺失时 source 为 None。"""
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

    # 驱动端：力矩上限 > 0 的那几个；没有传动箱时无法从 power_outputs 区分
    driven_names = [e.name for e in drive["ends"] if e.max_drive_torque > 0]
    notes = list(drive["notes"])
    trans_ratio = transmission.driven_ratio(set(driven_names), notes) if transmission else 1.0

    source = None
    kind, ref, _ = vehicle.power_source_ref()
    if ref is not None:
        sub_part, _, source_ref = ref
        entry = registry.need("subsystems", source_ref["definition"], sub_part.part_id)
        source = (Engine if kind == "engine" else Motor).from_entry(entry)

    return dict(gearbox=gearbox, transmission=transmission, trans_ratio=trans_ratio,
                kind=drive["kind"], driven=driven_names, total_wheels=drive["total"],
                radius=drive["radius"], radii=drive["radii"], source=source, notes=notes)


def red_line_speed(source, radius: float, total_ratio: float) -> float:
    """该档在红线下能达到的车速 km/h。"""
    return source.red_line_rpm * math.pi / 30 * radius / total_ratio * KMH


def report_text(vehicle, drive) -> tuple:
    gearbox = drive["gearbox"]
    raw_ratios = gearbox.forward()
    fixed = gearbox.final_ratio * drive["trans_ratio"]
    ratios = [r * fixed for r in raw_ratios]
    source = drive["source"]

    lines = []
    push = lines.append
    push(f"# {vehicle.name} 传动比链")
    push("")
    push("## 传动")
    push("| 项 | 值 |")
    push("|---|---|")
    push(f"| 变速箱 | {f'`{gearbox.source}`' if gearbox.source else '无'} |")
    push(f"| 终传比 | {gearbox.final_ratio:g} |")
    if drive["transmission"] is None:
        push("| 传动箱 | 无 |")
        push("| 挡位之后的固定减速 | 仅终传比 |")
    else:
        push(f"| 传动箱 | `{drive['transmission'].source}` |")
        push(f"| 传动箱减速比 | {drive['trans_ratio']:g} |")
        push(f"| 挡位之后的固定减速 | {gearbox.final_ratio:g} × {drive['trans_ratio']:g} = "
             f"{fixed:g} |")
    push(f"| 驱动半径 | {drive['radius']:.5f} m（"
         f"{'主动轮' if drive['kind'] == 'track' else '轮面'}碰撞球） |")
    if drive["kind"] == "track":
        push(f"| 驱动方式 | 履带 {len(drive['driven'])} 条（左右各一） |")
    else:
        push(f"| 驱动轮 | {len(drive['driven'])} / {drive['total_wheels']} |")
    if len(drive["radii"]) > 1:
        others = ", ".join(f"{r:.5f}" for r in drive["radii"][:-1])
        push(f"| 其他半径 | {others} m（按最大者折算） |")
    push("")
    push("## 档位")
    head = "| 档 | 挡位传动比 | 总传动比 | 级比 | 换挡收益 |"
    if source is not None:
        push(head + " 红线车速 (km/h) |")
        push("|---|---|---|---|---|---|")
    else:
        push(head)
        push("|---|---|---|---|---|")
    for index, (raw, total) in enumerate(zip(raw_ratios, ratios)):
        if index + 1 < len(ratios):
            step = f"{ratios[index] / ratios[index + 1]:.3f}"
            gain = f"{(ratios[index] / ratios[index + 1] - 1) * 100:.0f}%"
        else:
            step = gain = "—"
        row = f"| {index + 1} | {raw:.3f} | {total:.2f} | {step} | {gain} |"
        if source is not None:
            row += f" {red_line_speed(source, drive['radius'], total):.1f} |"
        push(row)
    push("")
    if len(ratios) == 1:
        push(f"单档，总传动比 {ratios[0]:.2f}，无换挡损失。")
    else:
        spread = ratios[0] / ratios[-1]
        limit = 1 + math.log(spread) / math.log(1.176)
        push(f"一档到最高档跨 {spread:.2f} 倍。按 15% 换挡收益（级比 ≥ 1.176）折算，"
             f"这套跨度最多放 {limit:.1f} 档，当前 {len(ratios)} 档。")
    if source is None:
        push("")
        push("整车没有动力源定义，红线车速一列省略。")
    return "\n".join(lines), dict(
        vehicle=vehicle.name,
        gearbox=dict(source=gearbox.source, final_ratio=gearbox.final_ratio,
                     switch_time=gearbox.switch_time, ratios=raw_ratios),
        transmission=dict(source=drive["transmission"].source if drive["transmission"] else None,
                          ratio=drive["trans_ratio"]),
        radius=drive["radius"], driven=len(drive["driven"]), wheels=drive["total_wheels"],
        drive_kind=drive["kind"],
        gears=[dict(gear=i + 1, ratio=total,
                    step=(ratios[i] / ratios[i + 1]) if i + 1 < len(ratios) else None,
                    red_kmh=(red_line_speed(source, drive["radius"], total)
                             if source is not None else None))
               for i, total in enumerate(ratios)],
        notes=drive["notes"],
    )


def main(argv=None):
    parser = argparse.ArgumentParser(description="传动比链与各档红线车速")
    add_common(parser)
    parser.add_argument("--template", help="模板名；包内有多个模板时必填")
    parser.add_argument("--json", action="store_true", help="输出 JSON 而不是 Markdown")
    args = parser.parse_args(argv)

    script_dir = Path(__file__).resolve().parent
    _, registry, pack = load_pack(args, script_dir)
    if pack is None:
        return 0
    vehicle = resolve_vehicle(registry, pack, pick_template(args, pack))
    drive = collect_drive(vehicle, registry)

    text, data = report_text(vehicle, drive)
    print(json.dumps(data, ensure_ascii=False, indent=2) if args.json else text)
    flush_warnings(registry)
    for note in drive["notes"]:
        print(f"注意：{note}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
