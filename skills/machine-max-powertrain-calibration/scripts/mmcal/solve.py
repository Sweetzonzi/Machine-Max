"""求解：由整车事实与传动参数算出档位表、极速与绑定约束。

纯静态包线，不含动态项。所有输入都来自内容包，没有需要人填的量。
"""

from __future__ import annotations

import math
from dataclasses import dataclass, field

from .constants import BLOCK_FRICTION, BLOCK_ROLLING, G, KMH, RHO
from .aero import DEFAULT_QUADRATIC, drag_area
from .driveline import Material, Transmission
from .geometry import hitbox_boxes, silhouette_area, sub_part_area
from .source import PowerCurve
from .vehicle import Vehicle


@dataclass
class Solution:
    curb_mass: float
    occupant_mass: float
    mass: float
    r: float
    drive_kind: str
    n_driven: int
    n_total: int
    max_drive_torque: float
    area: tuple
    area_sum: tuple
    cd_area: float
    aero_source: str
    c_rr: float
    mu: float
    gears: list
    top: dict
    top_upper: dict
    peak_power: tuple
    power_util: float
    rated_power: float
    transmission: object
    transmission_ratio: float
    notes: list = field(default_factory=list)


def solve(vehicle: Vehicle, source: PowerCurve, gearbox, drive: dict,
          material: Material, area_override=None,
          transmission: Transmission | None = None, occupant_mass: float = 60.0,
          cd_area_override: float | None = None) -> Solution:
    mass = vehicle.mass + occupant_mass
    r = float(drive["radius"])
    notes = list(drive["notes"])
    radii = list(drive["radii"])
    if drive["kind"] == "wheel" and len(radii) > 1:
        notes.append(f"轮面半径不唯一 {radii}，按最大者 {r:.5f} 计算")

    # 只有 max_drive_torque > 0 的驱动端被驱动；其余是随动轮，只分担质量。
    ends = drive["ends"]
    driven = [e for e in ends if e.max_drive_torque > 0] or ends
    n_driven = len(driven)
    n_total = max(int(drive["total"]), len(ends), n_driven)
    drive_limits = [e.max_drive_torque for e in driven]
    mdf_max = max(drive_limits)
    if len({round(x, 3) for x in drive_limits}) > 1:
        notes.append(f"驱动端力矩上限不一致 {sorted({round(x, 1) for x in drive_limits})}，"
                     f"按各自上限夹紧")
    if n_driven < n_total:
        notes.append(f"{n_total} 个车轮里 {n_driven} 个被驱动，"
                     f"附着上限按驱动轮占比 {n_driven / n_total:.2f} 折算")

    # 最高档能跑多快还有一条独立上限：履带主动轮的角速度上限。
    speed_caps = [e.max_speed * r for e in driven if e.max_speed is not None]
    v_cap = min(speed_caps) if speed_caps else None

    # 挡位之后的固定减速：变速箱终传比 × 传动箱减速比。
    trans_ratio = transmission.driven_ratio({e.name for e in driven}, notes) \
        if transmission is not None else 1.0
    stage_ratio = gearbox.final_ratio * trans_ratio

    # 逐子零件 AABB 相加：代码直接施加阻力的口径，不含遮挡，是阻力的上界。
    boxes = [b for sp in vehicle.sub_parts for b in hitbox_boxes(sp)]
    area_sum = [0.0, 0.0, 0.0]
    for sp in vehicle.sub_parts:
        for i, value in enumerate(sub_part_area(sp)):
            area_sum[i] += value
    # 轮廓：所有包围盒投影后的并集面积，接近物理正投影。
    area_union = tuple(silhouette_area(boxes, axis) for axis in (0, 1, 2))
    if area_override:
        area = tuple(float(x) for x in area_override)
        area_sum = area
    else:
        area = area_union
        explicit = [sp for sp in vehicle.sub_parts if any(p > 0 for p in sp.projected_area)]
        if explicit:
            notes.append(f"{len(explicit)} 个子零件显式声明了 projected_area，"
                         f"轮廓面积仍按模型包围盒计算")

    mu = material.friction_xy[1] * BLOCK_FRICTION
    c_rr = material.c_rr * BLOCK_ROLLING    # 地面方块倍数
    grip = mu * mass * G * n_driven / n_total

    # 气动：逐子零件按包内气动点求前向 Cd·A；未声明气动点的零件按默认系数 1.0 计入。
    aero = drag_area(vehicle)
    if cd_area_override is not None:
        cd_area = float(cd_area_override)
        aero_source = "override"
    else:
        cd_area = aero["total"]
        aero_source = "pack"
    if aero["default"]:
        notes.append(f"{aero['default']} 个子零件未声明 hydrodynamics，"
                     f"前向二阶系数按默认 {DEFAULT_QUADRATIC:g} 计入")

    forward = gearbox.forward()
    gears = []
    for index, ratio in enumerate(forward, start=1):
        total_ratio = ratio * stage_ratio
        tau_w = source.max_torque * total_ratio / n_driven
        source_force = source.max_torque * total_ratio / r
        motor_force = sum(min(tau_w, limit) for limit in drive_limits) / r
        force = min(motor_force, grip)
        if grip < motor_force:
            bound = "附着"
        elif motor_force < source_force:
            bound = "轮端夹紧"
        else:
            bound = "动力源"
        gears.append(dict(gear=index, ratio=total_ratio, tau_w=tau_w,
                          force=force, accel=force / mass, bound=bound))

    def traction(v: float, total_ratio: float) -> float:
        # 红线以上不再给扭矩；求解只在 v ≤ v_red 取点，夹住转速只为消掉浮点边界
        rpm = min(v * total_ratio / r * 30 / math.pi, source.red_line_rpm)
        tau = source.net_torque(rpm)
        if tau <= 0:
            return 0.0
        tau_w = tau * total_ratio / n_driven
        return sum(min(tau_w, limit) for limit in drive_limits) / r

    def solve_top(drag: float) -> dict:
        def resistance(v: float) -> float:
            return 0.5 * RHO * drag * v * v + c_rr * mass * G

        def feasible(v: float, total_ratio: float) -> bool:
            return traction(v, total_ratio) >= resistance(v)

        # 牵引力随转速先升后降，阻力随车速单调升，所以 F−R 可能有两个零点：
        # 高档位在低转速下拉不动（第一个零点以下跑不起来），真正要的是**最高**那个。
        # 从红线往低扫，找到最高的可行采样点，再在它与上一个采样点之间二分。
        steps = 200
        best = (0.0, 0, 0.0)
        for index, ratio in enumerate(forward, start=1):
            total_ratio = ratio * stage_ratio
            v_red = source.red_line_rpm * math.pi / 30 * r / total_ratio
            v = 0.0
            for i in range(steps, -1, -1):
                x = v_red * i / steps
                if feasible(x, total_ratio):
                    if i == steps:
                        v = v_red
                        break
                    lo, hi = x, v_red * (i + 1) / steps
                    for _ in range(60):
                        mid = (lo + hi) / 2
                        if feasible(mid, total_ratio):
                            lo = mid
                        else:
                            hi = mid
                    v = lo
                    break
            if v > best[0]:
                best = (v, index, v * total_ratio / r * 30 / math.pi)


        v, gear, rpm = best
        total_ratio = gears[gear - 1]["ratio"]
        red_top = source.red_line_rpm * math.pi / 30 * r / total_ratio
        bound = "红线" if abs(v - red_top) < 0.05 else "功率"
        if v_cap is not None and v >= v_cap - 1e-6:
            v, bound = v_cap, "履带转速"
            rpm = v * total_ratio / r * 30 / math.pi
        return dict(
            kmh=v * KMH, gear=gear, rpm=rpm,
            red_kmh=red_top * KMH,
            bound=bound,
            forces=dict(
                traction=traction(v, total_ratio),
                aero=0.5 * RHO * drag * v * v,
                rolling=c_rr * mass * G,
                damping=source.damping_torque(rpm) * total_ratio / r,
            ))

    top = solve_top(cd_area)
    top_upper = top

    peak = source.peak_power()
    rated = peak[0]
    net_peak = source.peak_net_power()[0]
    util = net_peak / rated if rated else 0.0

    if abs(rated - source.max_power) / source.max_power > 0.01:
        notes.append(
            f"max_power 字段 {source.max_power:.0f} W 与曲线峰值 {rated:.0f} W 偏差 "
            f"{abs(rated - source.max_power) / source.max_power * 100:.1f}% > 1%")

    return Solution(curb_mass=vehicle.mass, occupant_mass=occupant_mass, mass=mass,
                    r=r, drive_kind=drive["kind"],
                    n_driven=n_driven, n_total=n_total, max_drive_torque=mdf_max,
                    area=area, area_sum=tuple(area_sum),
                    cd_area=cd_area, aero_source=aero_source,
                    c_rr=c_rr, mu=mu, gears=gears, top=top, top_upper=top_upper,
                    peak_power=peak, power_util=util, rated_power=rated,
                    transmission=transmission, transmission_ratio=trans_ratio, notes=notes)
