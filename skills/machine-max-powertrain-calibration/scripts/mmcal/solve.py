"""求解：由整车事实与传动参数算出档位表、极速与绑定约束。

纯静态包线，不含动态项。所有输入都来自内容包，没有需要人填的量。
"""

from __future__ import annotations

import math
from dataclasses import dataclass, field

from .constants import BLOCK_FRICTION, BLOCK_ROLLING, CD, G, KMH, RHO
from .driveline import Material, Transmission
from .geometry import hitbox_boxes, silhouette_area, sub_part_area, wheel_radii
from .source import PowerCurve
from .vehicle import Vehicle


@dataclass
class Solution:
    mass: float
    r: float
    n_driven: int
    n_total: int
    max_drive_force: float
    area: tuple
    area_sum: tuple
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


def solve(vehicle: Vehicle, source: PowerCurve, gearbox, wheel_defs: list,
          n_total_wheels: int, material: Material, area_override=None,
          transmission: Transmission | None = None) -> Solution:
    mass = vehicle.mass
    wheel_subs = [sp for sp in vehicle.sub_parts if wheel_radii(sp)]
    if not wheel_subs:
        raise SystemExit("整车没有任何 type=wheel 的碰撞箱，无法取轮半径")
    radii = sorted({round(r, 5) for sp in wheel_subs for r in wheel_radii(sp)})
    r = max(radii)
    notes = []
    if len(radii) > 1:
        notes.append(f"轮面半径不唯一 {radii}，按最大者 {r:.5f} 计算")

    # 只有 max_drive_force > 0 的车轮被驱动；其余是随动轮，只分担质量。
    driven = [w for w in wheel_defs if w.max_drive_force > 0] or wheel_defs
    n_driven = len(driven)
    n_total = max(n_total_wheels, len(wheel_defs), n_driven)
    drive_limits = [w.max_drive_force for w in driven]
    mdf_max = max(drive_limits)
    if len({round(x, 3) for x in drive_limits}) > 1:
        notes.append(f"驱动轮力矩上限不一致 {sorted({round(x, 1) for x in drive_limits})}，"
                     f"按各自上限夹紧")
    if n_driven < n_total:
        notes.append(f"{n_total} 个车轮里 {n_driven} 个被驱动，"
                     f"附着上限按驱动轮占比 {n_driven / n_total:.2f} 折算")

    # 挡位之后的固定减速：变速箱终传比 × 传动箱减速比。
    trans_ratio = transmission.driven_ratio({w.name for w in driven}, notes) \
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

    forward = gearbox.forward()
    gears = []
    for index, ratio in enumerate(forward, start=1):
        total_ratio = ratio * stage_ratio
        tau_w = source.max_torque * total_ratio / n_driven
        force = source.max_torque * total_ratio / r
        if tau_w > mdf_max:
            bound = "轮端夹紧"
        elif force > grip:
            bound = "附着"
        else:
            bound = "动力源"
        gears.append(dict(gear=index, ratio=total_ratio, tau_w=tau_w,
                          force=force, accel=force / mass, bound=bound))

    def traction(v: float, total_ratio: float) -> float:
        rpm = v * total_ratio / r * 30 / math.pi
        tau = source.net_torque(rpm)
        if tau <= 0:
            return 0.0
        tau_w = tau * total_ratio / n_driven
        return sum(min(tau_w, limit) for limit in drive_limits) / r

    def solve_top(front_area: float) -> dict:
        def resistance(v: float) -> float:
            return 0.5 * RHO * CD * front_area * v * v + c_rr * mass * G

        best = (0.0, 0, 0.0)
        for index, ratio in enumerate(forward, start=1):
            total_ratio = ratio * stage_ratio
            v_red = source.red_line_rpm * math.pi / 30 * r / total_ratio
            if traction(v_red, total_ratio) >= resistance(v_red):
                v = v_red
            else:
                lo, hi = 0.0, v_red
                for _ in range(60):
                    mid = (lo + hi) / 2
                    if traction(mid, total_ratio) >= resistance(mid):
                        lo = mid
                    else:
                        hi = mid
                v = lo
            if v > best[0]:
                best = (v, index, v * total_ratio / r * 30 / math.pi)

        v, gear, rpm = best
        total_ratio = gears[gear - 1]["ratio"]
        red_top = source.red_line_rpm * math.pi / 30 * r / total_ratio
        return dict(
            kmh=v * KMH, gear=gear, rpm=rpm,
            red_kmh=red_top * KMH,
            bound="红线" if abs(v - red_top) < 0.05 else "功率",
            forces=dict(
                traction=traction(v, total_ratio),
                aero=0.5 * RHO * CD * front_area * v * v,
                rolling=c_rr * mass * G,
                damping=source.damping_torque(rpm) * total_ratio / r,
            ))

    top = solve_top(area[2])
    top_upper = solve_top(area_sum[2])

    peak = source.peak_power()
    rated = peak[0]
    net_peak = source.peak_net_power()[0]
    util = net_peak / rated if rated else 0.0

    if abs(rated - source.max_power) / source.max_power > 0.01:
        notes.append(
            f"max_power 字段 {source.max_power:.0f} W 与曲线峰值 {rated:.0f} W 偏差 "
            f"{abs(rated - source.max_power) / source.max_power * 100:.1f}% > 1%")

    return Solution(mass=mass, r=r, n_driven=n_driven, n_total=n_total, max_drive_force=mdf_max,
                    area=area, area_sum=tuple(area_sum),
                    c_rr=c_rr, mu=mu, gears=gears, top=top, top_upper=top_upper,
                    peak_power=peak, power_util=util, rated_power=rated,
                    transmission=transmission, transmission_ratio=trans_ratio, notes=notes)
