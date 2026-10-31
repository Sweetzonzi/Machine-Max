"""气动：按内容包声明的气动点求前向 `Cd·A`。

游戏内的阻力口径（`SubPart.applyHydrodynamic` → `DynamicUtil.aeroDynamicForce`）：

- 每个子零件至少有一个气动点。`hydrodynamics` 缺省时按
  `Map.of("", HydrodynamicAttr.DEFAULT)` 处理，即键为空串、力作用在子零件原点，
  六向系数 `[0.1, 1.0]`，`scale = 1`。
- 二阶（平方）阻力 = `0.5 × ρ × c₁ × v² × A_z × scale`，`c₁` 取 `front_drag` 的第二项，
  缺省为 1.0；`A_z` 是该子零件前向投影面积，显式 `projected_area` 优先，否则按碰撞箱包围盒。
- 配置的键若是骨骼名，会展开成该骨骼下的全部定位器，每个定位器各施加一份力。
- `hydro_priority` 只影响遮挡：沿来流方向被同装配体里优先级不低的子零件挡住时按命中比例折减。

本模块把上面几条折算成一个「前向阻力面积」`Cd·A`（m²），按子零件求和，遮挡不复现，
因此结果是阻力的上界。
"""

from __future__ import annotations

from .geometry import bone_locator_count, sub_part_area

# HydrodynamicAttr.DEFAULT 的 front_drag 第二项
DEFAULT_QUADRATIC = 1.0


def _front_coefficient(attr: dict) -> float:
    """`front_drag` 的二阶系数 × `scale`；缺省与 `HydrodynamicAttr.DEFAULT` 一致。"""
    coeff = attr.get("front_drag") or []
    quadratic = float(coeff[1]) if len(coeff) > 1 else DEFAULT_QUADRATIC
    return quadratic * float(attr.get("scale", 1.0))


def drag_area(vehicle) -> dict:
    """整车前向 `Cd·A` 及其逐子零件分解。"""
    total = 0.0
    tuned = 0
    parts = []
    for sp in vehicle.sub_parts:
        area = sub_part_area(sp)[2]
        if sp.hydro:
            contribution = 0.0
            for key, attr in sp.hydro.items():
                if not isinstance(attr, dict):
                    continue
                points = bone_locator_count(sp.geo, key)
                contribution += _front_coefficient(attr) * area * points
            tuned += 1
        else:
            contribution = DEFAULT_QUADRATIC * area
        total += contribution
        parts.append(dict(part=sp.part_id, sub_part=sp.name, mass=sp.mass, area=area,
                          locators=len(sp.hydro or {}), cd_area=contribution,
                          declared=bool(sp.hydro)))
    return dict(total=total, parts=parts, tuned=tuned,
                default=len(parts) - tuned)
