#!/usr/bin/env python3
"""单个动力源的曲线形状评估。

回答的是「这条曲线好不好用」：峰值落点、功率带有多宽、红线还剩多少扭矩、
`max_power` 有没有在高转把扭矩削掉。不需要整车，也不需要传动。

用法示例：

    python curve.py --pack Machine-Max_K17                     # 整车的动力源
    python curve.py --pack Machine-Max_Civilian --subsystem machine_max:mini_ev_motor
    python curve.py --pack Machine-Max_K17 --csv > k17_curve.csv   # 逐 rpm 全表
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from mmcal.cli import add_common, flush_warnings, load_pack, pick_template
from mmcal.pack import Registry
from mmcal.source import SOURCES
from mmcal.vehicle import resolve_vehicle

POWER_BAND = 0.9      # 「功率带」= 功率不低于峰值的这个比例


def build_source(args, registry: Registry, pack):
    """按 `--subsystem` 取单个定义，否则取整车模板的动力源。"""
    if args.subsystem:
        entry = registry.need("subsystems", args.subsystem, "--subsystem")
        kind = str(entry.data.get("type", "")).split(":")[-1]
        if kind not in SOURCES:
            raise SystemExit(f"{entry.path}: 类型 {kind or '未知'} 不是动力源（可选 engine / motor）")
        return SOURCES[kind].from_entry(entry), entry.path.stem
    vehicle = resolve_vehicle(registry, pack, pick_template(args, pack))
    source_kind, ref, hybrid = vehicle.power_source_ref()
    if ref is None:
        raise SystemExit("整车没有 machine_max:engine 或 machine_max:motor 子系统")
    if hybrid:
        registry.warnings.append("整车同时有内燃机与电机（混动），只评估内燃机")
    sub_part, _, ref = ref
    entry = registry.need("subsystems", ref["definition"], sub_part.part_id)
    return SOURCES[source_kind].from_entry(entry), vehicle.name


def power_band(source) -> tuple:
    """功率不低于峰值 `POWER_BAND` 的转速区间，返回 (低, 高, 峰值, 峰值转速)。"""
    peak, peak_rpm = source.peak_power()
    if peak <= 0:
        return 0.0, 0.0, 0.0, 0.0
    lo = hi = None
    rpm = 1.0
    while rpm <= source.red_line_rpm:
        if source.curve_power(rpm) >= POWER_BAND * peak:
            lo = rpm if lo is None else lo
            hi = rpm
        rpm += 1.0
    return (lo or peak_rpm), (hi or peak_rpm), peak, peak_rpm


def torque_peak(source) -> tuple:
    """扭矩峰值，以及峰值区间的末速（电机是恒扭矩段的终点）。"""
    best = (0.0, 0.0)
    rpm = 1.0
    while rpm <= source.red_line_rpm:
        tau = source.torque(rpm)
        if tau > 1e-9 and tau >= best[0] - 1e-9:
            best = (tau, rpm)
        rpm += 1.0
    return best


def cap_onset(source) -> float | None:
    """`max_power` 开始削扭矩的转速；不削则为 None。"""
    rpm = 1.0
    while rpm <= source.red_line_rpm:
        if source.raw_torque(rpm) - source.torque(rpm) > 1e-6:
            return rpm
        rpm += 1.0
    return None


def sample_rpms(source, points: int) -> list:
    """沿转速轴等距取点，端点必取；0 rpm 不取（那里扭矩恒为 0）。"""
    top = source.red_line_rpm
    points = max(points, 2)
    return sorted({round(top * i / (points - 1), 1) for i in range(1, points)})


def rows_csv(source, step: float) -> list:
    rows = []
    rpm = step
    while rpm <= source.red_line_rpm:
        rows.append((rpm, source.torque(rpm), source.damping_torque(rpm),
                     source.net_torque(rpm), source.curve_power(rpm), source.net_power(rpm)))
        rpm = round(rpm + step, 6)
    return rows


def report_text(name, source, points):
    peak, peak_rpm = source.peak_power()
    net_peak, net_rpm = source.peak_net_power()
    tau_peak, tau_rpm = torque_peak(source)
    lo, hi, _, _ = power_band(source)
    onset = cap_onset(source)
    util = net_peak / peak if peak else 0.0
    red_tau = source.torque(source.red_line_rpm)
    reserve = tau_peak / red_tau if red_tau > 0 else 0.0
    deviation = abs(peak - source.max_power) / source.max_power if source.max_power else 0.0

    lines = []
    push = lines.append
    push(f"# {name} 动力源曲线")
    push("")
    push("## 参数")
    push("| 项 | 值 |")
    push("|---|---|")
    for label, value in source.rows():
        push(f"| {label} | {value} |")
    push(f"| 定义 | `{source.source}` |")
    push("")
    push("## 形状")
    push("| 指标 | 值 |")
    push("|---|---|")
    push(f"| 扭矩峰值 | {tau_peak:.0f} N·m @ {tau_rpm:.0f} rpm |")
    push(f"| 功率峰值（曲线） | {peak / 1000:.1f} kW @ {peak_rpm:.0f} rpm |")
    push(f"| 功率峰值（扣内阻） | {net_peak / 1000:.1f} kW @ {net_rpm:.0f} rpm |")
    push(f"| 功率利用率 | {util:.3f} |")
    push(f"| {POWER_BAND:.0%} 功率带 | {lo:.0f} → {hi:.0f} rpm"
         f"（{hi - lo:.0f} rpm，占红线 {(hi - lo) / source.red_line_rpm:.0%}） |")
    push(f"| 红线处扭矩 | {red_tau:.0f} N·m（峰值的 {1 / reserve:.0%}） |")
    push(f"| 扭矩储备系数 | {reserve:.2f} |")
    push(f"| 内阻 @ 红线 | {source.damping_torque(source.red_line_rpm):.0f} N·m |")
    push(f"| `max_power` 削扭矩起点 | "
         f"{'不生效（字段不低于曲线峰值）' if onset is None else f'{onset:.0f} rpm'} |")
    push("")
    push("## 采样")
    push("| rpm | τ (N·m) | 内阻 (N·m) | 净 τ (N·m) | P (kW) | 净 P (kW) |")
    push("|---|---|---|---|---|---|")
    for rpm in sample_rpms(source, points):
        push(f"| {rpm:.0f} | {source.torque(rpm):.0f} | {source.damping_torque(rpm):.1f} | "
             f"{source.net_torque(rpm):.0f} | {source.curve_power(rpm) / 1000:.1f} | "
             f"{source.net_power(rpm) / 1000:.1f} |")
    push("")
    push("## 诊断")
    push("")
    if deviation > 0.01:
        push(f"- `max_power` 字段 {source.max_power / 1000:.1f} kW 与曲线峰值 "
             f"{peak / 1000:.1f} kW 偏差 {deviation:.1%}，超出 1% 的容差。")
    else:
        push(f"- `max_power` 与曲线峰值偏差 {deviation:.2%}，在 1% 容差内。")
    if onset is not None:
        push(f"- 从 {onset:.0f} rpm 起功率上限真的削掉折线扭矩：这段的输出只看 `max_power`，"
             f"调 `max_torque` 不再有效。")
    push(f"- 功率不低于峰值 {POWER_BAND:.0%} 的区间是 {lo:.0f}~{hi:.0f} rpm，宽 "
         f"{hi - lo:.0f} rpm。换挡后的转速要落在这段之内，否则换完档就掉出功率带。")
    if reserve >= 1.05:
        push(f"- 扭矩储备 {reserve:.2f}：红线扭矩是峰值的 {1 / reserve:.0%}，高转仍有余量。")
    else:
        push(f"- 扭矩储备 {reserve:.2f}：扭矩曲线接近平直，牵引力随转速变化很小。")
    return "\n".join(lines), dict(
        source=source.source, kind=source.kind,
        max_power=source.max_power, curve_peak=peak, curve_peak_rpm=peak_rpm,
        net_peak=net_peak, net_peak_rpm=net_rpm, power_util=util,
        torque_peak=tau_peak, torque_peak_rpm=tau_rpm,
        red_line_torque=red_tau, reserve=reserve,
        power_band=dict(low=lo, high=hi, ratio=POWER_BAND),
        cap_onset_rpm=onset,
        damping_at_red_line=source.damping_torque(source.red_line_rpm),
        max_power_deviation=deviation,
    )


def main(argv=None):
    parser = argparse.ArgumentParser(description="动力源扭矩/功率曲线评估")
    add_common(parser)
    parser.add_argument("--template", help="模板名；包内有多个模板时必填")
    parser.add_argument("--subsystem", help="直接指定动力源定义 id，如 machine_max:k17_engine")
    parser.add_argument("--points", type=int, default=11, help="采样点数，默认 11")
    parser.add_argument("--csv", action="store_true", help="逐 rpm 输出 CSV（可直接画图）")
    parser.add_argument("--step", type=float, default=50.0, help="CSV 的转速步长 rpm，默认 50")
    parser.add_argument("--json", action="store_true", help="输出 JSON 而不是 Markdown")
    args = parser.parse_args(argv)

    script_dir = Path(__file__).resolve().parent
    _, registry, pack = load_pack(args, script_dir)
    if pack is None:
        return 0
    source, name = build_source(args, registry, pack)

    if args.csv:
        print("rpm,torque_nm,damping_nm,net_torque_nm,power_w,net_power_w")
        for row in rows_csv(source, max(args.step, 0.5)):
            print(f"{row[0]:.1f},{row[1]:.4f},{row[2]:.4f},{row[3]:.4f},{row[4]:.1f},{row[5]:.1f}")
        flush_warnings(registry)
        return 0

    text, data = report_text(name, source, args.points)
    print(json.dumps(data, ensure_ascii=False, indent=2) if args.json else text)
    flush_warnings(registry)
    return 0


if __name__ == "__main__":
    sys.exit(main())
