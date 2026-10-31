"""需求残差：把作者给的约束写成「需求 / 可用」两列。"""

from __future__ import annotations

import math
import re

from .constants import CD, G, KMH, PS, RHO


def parse_power(text: str) -> float:
    match = re.match(r"\s*([0-9.]+)\s*(kw|ps|hp)?", text, re.I)
    if not match:
        raise SystemExit(f"无法解析功率 {text}")
    value = float(match.group(1))
    unit = (match.group(2) or "kw").lower()
    if unit == "ps":
        return value * PS
    if unit == "hp":
        return value * 745.6998715822702
    return value * 1000.0


def parse_accel(text: str) -> tuple:
    match = re.match(r"\s*([0-9.]+)\s*[:@]\s*([0-9.]+)", text)
    if not match:
        raise SystemExit(f"无法解析加速需求 {text}（形如 50:3.0，表示 0→50 km/h 用 3 秒）")
    return float(match.group(1)) / KMH, float(match.group(2))


def requirements(args, source, gearbox, sol) -> list:
    """把命令行需求写成「需求 / 可用」两列，残差 = 可用 − 需求。"""
    rows = []

    def add(name, need, avail, unit, note):
        residual = avail - need
        rows.append(dict(name=name, need=need, avail=avail, residual=residual,
                         unit=unit, ok=residual >= -1e-9, note=note))

    if args.top_speed:
        target = args.top_speed / KMH
        top_ratio = sol.gears[-1]["ratio"]
        rpm = target * top_ratio / sol.r * 30 / math.pi
        resistance = (0.5 * RHO * CD * sol.area[2] * target * target + sol.c_rr * sol.mass * G
                      + source.damping_torque(rpm) * top_ratio / sol.r)
        need = resistance * target / 0.85
        recommended = source.red_line_rpm * math.pi / 30 * sol.r / (target * 1.03)
        fixed = f"，其中传动箱固定 {sol.transmission_ratio:g}" \
            if sol.transmission_ratio != 1 else ""
        rows.append(dict(name="最高档总传动比", need=None, avail=top_ratio,
                         residual=None, unit="", ok=None,
                         note=f"目标极速 {args.top_speed:.0f} km/h 建议 i_top ≈ "
                              f"{recommended:.2f}{fixed}"))
        if args.power:
            add("极速处功率", need, parse_power(args.power), "W",
                f"{args.top_speed:.0f} km/h 处阻力需要 {need / 1000:.1f} kW 轮端功率（含 0.85 余量）")
        else:
            add("极速处功率", need, sol.rated_power, "W",
                f"{args.top_speed:.0f} km/h 处阻力需要 {need / 1000:.1f} kW 轮端功率（含 0.85 余量）")

    if args.accel:
        v, seconds = parse_accel(args.accel)
        need = sol.mass * v * v / (2 * 0.85 * seconds)
        add(f"0→{v * KMH:.0f} km/h {seconds:g}s 所需功率", need, sol.rated_power, "W",
            f"需要 {need / 1000:.1f} kW 曲轴峰值功率")

    if args.grade:
        theta = math.radians(args.grade)
        first = sol.gears[0]
        need = sol.mass * G * (math.sin(theta) + sol.c_rr * math.cos(theta))
        add(f"{args.grade:g}° 爬坡牵引力", need, first["force"], "N", "一档满扭矩牵引力")
        add(f"{args.grade:g}° 坡面附着", first["force"], sol.mu * sol.mass * G * math.cos(theta), "N",
            "驱动轮合计附着上限")

    if args.gears:
        spread = sol.gears[0]["ratio"] / sol.gears[-1]["ratio"]
        limit = 1 + math.log(spread) / math.log(1.176) if spread > 1 else 0
        add("档数", args.gears, limit, "档",
            f"一档到最高档跨 {spread:.1f} 倍，按 15% 换挡收益最多放 {limit:.1f} 档")

    return rows
