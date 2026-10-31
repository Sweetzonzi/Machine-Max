"""报告输出：Markdown 文本与 JSON。"""

from __future__ import annotations

import json


def fmt(value, digits=1):
    if isinstance(value, float):
        return f"{value:,.{digits}f}"
    return str(value)


def show(value, unit: str) -> str:
    if value is None:
        return "—"
    if unit == "W":
        return f"{value / 1000:.1f} kW"
    if unit == "N":
        return f"{value / 1000:.2f} kN"
    if unit == "档":
        return f"{value:.1f}"
    return f"{value:.2f}"


def report_text(vehicle, source, gearbox, material, sol, reqs) -> str:
    lines = []
    push = lines.append
    push(f"# {vehicle.name} 动力标定")
    push("")
    push("## 整车")
    push("| 项 | 值 |")
    push("|---|---|")
    push(f"| 整备质量 | {fmt(sol.mass, 0)} kg |")
    push(f"| 驱动轮数 | {sol.n_driven} / {sol.n_total} |")
    push(f"| 轮端力矩上限 | {sol.max_drive_force:,.0f} N·m |")
    push(f"| 轮半径 | {sol.r:.5f} m（模型轮面球） |")
    push(f"| 气动面积 X/Y/Z（轮廓） | {sol.area[0]:.2f} / {sol.area[1]:.2f} / {sol.area[2]:.2f} m² |")
    push(f"| 气动面积（逐零件相加） | {sol.area_sum[0]:.2f} / {sol.area_sum[1]:.2f} / "
         f"{sol.area_sum[2]:.2f} m² |")
    push(f"| 轮胎材质 | `C_rr` {sol.c_rr:.4f}，纵向 μ {sol.mu:.3f} |")
    push(f"| 比功率 | {sol.rated_power / 1000 / (sol.mass / 1000):.1f} kW/t |")
    push("")
    push("## 动力源")
    push("| 项 | 值 |")
    push("|---|---|")
    for label, value in source.rows():
        push(f"| {label} | {value} |")
    push(f"| 曲线峰值功率 | {sol.peak_power[0] / 1000:.1f} kW @ {sol.peak_power[1]:.0f} rpm |")
    push(f"| 功率利用率 | {sol.power_util:.3f} |")
    push("")
    push("## 传动")
    push("| 项 | 值 |")
    push("|---|---|")
    if gearbox.source:
        push(f"| 变速箱 | `{gearbox.source}` |")
        push(f"| 终传比 | {gearbox.final_ratio:g} |")
        push(f"| 换挡时间 | {gearbox.switch_time:g} s |")
    else:
        push("| 变速箱 | 无 |")
    if sol.transmission is None:
        push("| 传动箱 | 无 |")
        push("| 挡位之后的固定减速 | 仅终传比 |")
    else:
        outputs = len(sol.transmission.ratios)
        push(f"| 传动箱 | `{sol.transmission.source}` |")
        push(f"| 输出端 / 驱动轮 | {outputs} / {sol.n_driven} |")
        push(f"| 传动箱减速比 | {sol.transmission_ratio:g} |")
        push(f"| 挡位之后的固定减速 | {gearbox.final_ratio:g} × {sol.transmission_ratio:g} = "
             f"{gearbox.final_ratio * sol.transmission_ratio:g} |")
    push("")
    push("## 档位")
    push("| 档 | 总传动比 | τ_w (N·m) | F_total (kN) | a (m/s²) | 绑定约束 |")
    push("|---|---|---|---|---|---|")
    for gear in sol.gears:
        push(f"| {gear['gear']} | {gear['ratio']:.2f} | {gear['tau_w']:.0f} | "
             f"{gear['force'] / 1000:.1f} | {gear['accel']:.2f} | {gear['bound']} |")
    ratios = [g["ratio"] for g in sol.gears]
    steps = [ratios[i] / ratios[i + 1] for i in range(len(ratios) - 1)]
    push("")
    if steps:
        push(f"级比：{' / '.join(f'{s:.3f}' for s in steps)}")
        push(f"换挡收益：{' / '.join(f'{(s - 1) * 100:.0f}%' for s in steps)}")
    else:
        push(f"单档，总传动比 {ratios[0]:.2f}，无换挡损失")
    push("")
    push("## 极速")
    push("| 项 | 值 |")
    push("|---|---|")
    push(f"| 红线极速 | {sol.top['red_kmh']:.1f} km/h |")
    push(f"| 实际极速（轮廓面积） | {sol.top['kmh']:.1f} km/h"
         f"（{sol.top['gear']} 档 {sol.top['rpm']:.0f} rpm，{sol.top['bound']}绑定） |")
    push(f"| 实际极速（逐零件相加，上界） | {sol.top_upper['kmh']:.1f} km/h"
         f"（{sol.top_upper['gear']} 档 {sol.top_upper['rpm']:.0f} rpm） |")
    forces = sol.top["forces"]
    push(f"| 极速处牵引力 | {forces['traction'] / 1000:.2f} kN |")
    push(f"| 极速处阻力 | 气动 {forces['aero'] / 1000:.2f} + 滚阻 "
         f"{forces['rolling'] / 1000:.2f} + 内阻 {forces['damping'] / 1000:.2f} kN |")
    push("")
    push("逐零件相加不含遮挡，是阻力的上界；真实值落在两行极速之间，"
         "要缩窄就用 `--area` 指定实测正投影面积。")
    if reqs:
        push("## 残差")
        push("| 约束 | 需求 | 可用 | 残差 | 结论 |")
        push("|---|---|---|---|---|")
        for row in reqs:
            if row["ok"] is None:
                push(f"| {row['name']} | — | {show(row['avail'], row['unit'])} | — | {row['note']} |")
                continue
            verdict = "通过" if row["ok"] else "**不通过**"
            push(f"| {row['name']} | {show(row['need'], row['unit'])} | "
                 f"{show(row['avail'], row['unit'])} | {show(row['residual'], row['unit'])} | "
                 f"{verdict}：{row['note']} |")
        push("")
    if sol.notes:
        push("## 注意")
        for note in sol.notes:
            push(f"- {note}")
        push("")
    return "\n".join(lines)


def report_json(vehicle, source, gearbox, material, sol, reqs) -> str:
    return json.dumps(dict(
        vehicle=dict(name=vehicle.name, template=str(vehicle.template_path),
                     mass=sol.mass, driven=sol.n_driven, wheels=sol.n_total, radius=sol.r,
                     area=dict(x=sol.area[0], y=sol.area[1], z=sol.area[2]),
                     area_sum=dict(x=sol.area_sum[0], y=sol.area_sum[1], z=sol.area_sum[2]),
                     c_rr=sol.c_rr, mu=sol.mu),
        power_source=dict(kind=source.kind, max_power=source.max_power,
                          max_torque=source.max_torque,
                          red_line_rpm=source.red_line_rpm,
                          peak_power=sol.peak_power[0], peak_rpm=sol.peak_power[1],
                          power_util=sol.power_util, source=source.source),
        gearbox=dict(source=gearbox.source, final_ratio=gearbox.final_ratio,
                     switch_time=gearbox.switch_time, gears=sol.gears),
        transmission=dict(source=sol.transmission.source if sol.transmission else None,
                          ratio=sol.transmission_ratio,
                          outputs=sol.transmission.ratios if sol.transmission else {}),
        wheel=dict(max_drive_force=sol.max_drive_force, driven=sol.n_driven,
                   total=sol.n_total),
        material=dict(source=material.source, c_rr=material.c_rr,
                      friction=list(material.friction_xy)),
        top_speed=dict(top=sol.top, upper=sol.top_upper),
        residuals=reqs, notes=sol.notes,
    ), ensure_ascii=False, indent=2)
