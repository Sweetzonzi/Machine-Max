"""悬挂：静挠度、行程占用与关节钳位余量。

关节弹簧由 `AbstractConnector.adjustJoint()` 在装配时钳一次。上限来自显式积分的
稳定域，`0.95` 只是余量，不是可调参数：

    m_eff = m_A·m_B/(m_A+m_B)              # 调和平均，被轻的一侧决定
    k_max = 0.95·4·m_eff/Δt²
    c_max = 0.95·(4 − k/(tps²·m_eff))/2·m_eff·tps

平移轴用质量，转动轴用绕该轴的转动惯量。因为 `m_eff ≤ min(m_A, m_B)`，车体有多重
都不影响：**任何一条轴能否保留作者写的 k/c，只取决于轮子自己的质量（或惯量）**。
这也是为什么「把车体拆散件」对这条约束毫无帮助。

关键推论写成轮重下限：

    m_轮 ≥ ζ·ω_n·m_簧上/100      （ω_n 与 m_簧上同属该轮）
        ≈ 2ζ·√(g/静挠度)·m_簧上/190

ζ=0.4、静挠度 10 cm 时约为每轮簧上质量的 4%；真实车辆的簧下比是 6~15%，
所以正常调校下这条约束比现实更宽松，只有短行程高阻尼（赛道、硬派军用）才会撞墙。
"""

from __future__ import annotations

import math
from dataclasses import dataclass, field

from .constants import G
from .geometry import wheel_radii
from .pack import Registry
from .vehicle import Vehicle

TPS = 100.0        # 服务端物理步频 baseStep(5) × 20；钳位只在服务端生效
SAFE = 0.95        # adjustJoint 的安全系数
MIN_MARGIN = 1.2   # 轮重 / 钳位下限，低于此值提示余量不足
DEG = math.pi / 180.0

TRANSLATION_AXES = ("x", "y", "z")
ROTATION_AXES = ("xr", "yr", "zr")


@dataclass
class SpringRow:
    connector: str
    axis: str
    rotational: bool
    stiffness: float          # SI：N/m 或 N·m/rad
    damping: float            # SI：N·s/m 或 N·m·s/rad
    member: str               # 子零件名，匹配不到时带 * 后缀
    member_size: float        # kg；转动轴时为估算转动惯量 kg·m²
    floor: float              # 不被钳位所需的最小质量 / 惯量
    margin: float
    carrying: bool = True
    k_max: float = 0.0
    c_max: float = 0.0
    sag: float | None = None      # m
    static: float | None = None   # 静平衡位置（关节坐标）
    lower: float | None = None
    upper: float | None = None
    omega: float | None = None    # rad/s
    zeta: float | None = None
    flags: list = field(default_factory=list)

    def as_dict(self):
        return dict(connector=self.connector, axis=self.axis,
                    rotational=self.rotational, stiffness=self.stiffness,
                    damping=self.damping, member=self.member,
                    member_size=self.member_size, floor=self.floor,
                    margin=self.margin, carrying=self.carrying,
                    k_max=self.k_max, c_max=self.c_max, sag=self.sag,
                    static=self.static, lower=self.lower, upper=self.upper,
                    omega=self.omega, zeta=self.zeta, flags=list(self.flags))


def clamp_limits(stiffness: float, size: float, tps: float = TPS,
                 safe: float = SAFE) -> tuple:
    """该轴在该质量（或惯量）上允许的 (k_max, c_max)，与 adjustJoint 同式。"""
    k_max = safe * 4.0 * size * tps * tps
    k_eff = min(stiffness, k_max) if stiffness > 0 else 0.0
    s = k_eff / (tps * tps * size)
    if s >= 4.0:
        return k_max, 0.0
    return k_max, safe * (4.0 - s) / 2.0 * size * tps


def required_size(stiffness: float, damping: float, tps: float = TPS,
                  safe: float = SAFE) -> float:
    """要保住这组 (k, c)，成员至少要有多少质量（或惯量）。二分。"""
    lo, hi = 1e-4, 1e6
    if clamp_limits(stiffness, hi, tps, safe)[1] < damping:
        return float("inf")
    for _ in range(200):
        mid = (lo + hi) / 2.0
        if clamp_limits(stiffness, mid, tps, safe)[1] >= damping:
            hi = mid
        else:
            lo = mid
    return hi


def inertia_estimate(mass: float, radius: float) -> float:
    """轮子刚体的转动惯量估算。

    轮面碰撞形状由球体（地形接触）加圆柱（一般判定）拼成，球体占大头，实心球
    绕任意轴都是 2/5·m·r²，因此对平动与转动轴都能用同一个估值。
    """
    return 0.4 * mass * radius * radius


def _match_member(wheels: list, connector_name: str):
    """连接点 → 被它悬挂的成员：逐段去掉前缀，取第一个能唯一定位的后缀。

    与 `vehicle._driven_wheel` 同一套命名约定（`hull_left_roadwheel_1` →
    `left_roadwheel_1`）。轮子做成独立零件、子零件统一叫 `main` 时匹配不到，
    调用方退回全车最轻的轮子。
    """
    tokens = connector_name.split(".")[-1].split("_")
    for start in range(1, len(tokens)):
        suffix = "_".join(tokens[start:])
        hits = [sp for sp in wheels if sp.name.split(".")[-1] == suffix]
        if len(hits) == 1:
            return hits[0]
    return None


def _spring_axes(registry: Registry, connector_id: str, overwrite: dict | None) -> dict:
    entry = registry.get("connectors", connector_id)
    if entry is None:
        registry.warnings.append(f"找不到连接点定义 {connector_id}")
        return {}
    data = entry.data
    if data.get("type") != "Advanced":
        return {}
    joint_attrs = data.get("joint_attrs") or {}
    # 与 ConnectorAttr.getEffectiveAttr 同语义：overwrite 整块替换 joint_attrs
    if overwrite and "joint_attrs" in overwrite:
        joint_attrs = overwrite["joint_attrs"] or {}
    return {axis: attr for axis, attr in joint_attrs.items()
            if isinstance(attr, dict) and (attr.get("stiffness") is not None
                                           or attr.get("damping") is not None)}


def _equilibrium(axes: dict) -> float:
    return float((axes.get("y") or {}).get("equilibrium") or 0.0)


def suspensions(registry: Registry, vehicle: Vehicle, load_members: int | None = None,
                tps: float = TPS) -> dict:
    """逐连接点解析悬挂，给出钳位、静挠度、行程占用与阻尼比。

    承重成员按 `equilibrium < 0` 认：只有真正吃车重的轮子才会把平衡位置压到装配位
    以下（好让车体被抬起来、同时留出压缩行程），诱导轮一类不受车重的成员写 0。
    `--load-members` 可覆盖这个推断。
    """
    wheels = [sp for sp in vehicle.sub_parts if wheel_radii(sp)]
    if not wheels:
        raise SystemExit("整车没有任何 type=wheel 的碰撞箱，无法解析悬挂")
    gear_mass = sum(sp.mass for sp in wheels)
    housing = [sp for sp in vehicle.sub_parts if sp not in wheels]
    min_wheel = min(wheels, key=lambda sp: sp.mass)

    mounts = []      # 悬挂安装座：至少有一条平动轴带弹簧
    for sp in housing:
        for cname, cdef in (sp.connectors or {}).items():
            axes = _spring_axes(registry, str(cdef.get("definition") or ""),
                                cdef.get("overwrite"))
            # 悬挂装在竖直的 y 轴上；z 轴弹簧是火炮驻退机一类别的东西
            if "y" in axes:
                mounts.append((sp, cname, cdef, axes))
    if not mounts:
        raise SystemExit("整车没有任何带弹簧的悬挂安装座，无从分析")

    carrying = [m for m in mounts if _equilibrium(m[3]) < 0]
    if not carrying:
        carrying = mounts
    members = load_members or len(carrying)
    if members <= 0:
        raise SystemExit("--load-members 必须为正")
    sprung_total = vehicle.mass - gear_mass
    if sprung_total <= 0:
        raise SystemExit("行动装置质量不小于整备质量，模板里的质量分配有问题")
    sprung_per_member = sprung_total / members
    carrying_ids = {id(m[2]) for m in carrying}

    rows = []
    for sp, cname, cdef, axes in mounts:
        matched = _match_member(wheels, cname)
        member = matched or min_wheel
        label = member.name.split(".")[-1] + ("" if matched else "*")
        radius = max(wheel_radii(member)) if wheel_radii(member) else 0.0
        let_carry = id(cdef) in carrying_ids
        for axis, attr in sorted(axes.items()):
            if axis not in TRANSLATION_AXES + ROTATION_AXES:
                continue
            rotational = axis in ROTATION_AXES
            raw_k = float(attr.get("stiffness") or 0.0)
            raw_c = float(attr.get("damping") or 0.0)
            if rotational:            # JSON 里是 N·m/deg 与 N·m·s/deg
                raw_k /= DEG
                raw_c /= DEG
            size = inertia_estimate(member.mass, radius) if rotational else member.mass
            k_max, c_max = clamp_limits(raw_k, size, tps)
            if raw_c > 0:
                floor = required_size(raw_k, raw_c, tps)
            elif raw_k > 0:
                floor = raw_k / (SAFE * 4.0 * tps * tps)
            else:
                floor = 0.0
            row = SpringRow(
                connector=str(cdef.get("definition")), axis=axis,
                rotational=rotational, stiffness=raw_k, damping=raw_c,
                member=label, member_size=size, floor=floor,
                margin=(size / floor) if floor > 0 else float("inf"),
                carrying=(let_carry and not rotational),
                k_max=k_max, c_max=c_max,
                lower=attr.get("lower_limit"), upper=attr.get("upper_limit"))
            if raw_k > 0 and raw_k > k_max:
                row.flags.append("刚度被钳")
            if raw_c > 0 and raw_c > c_max:
                row.flags.append("阻尼被钳")
            if raw_c > 0 and row.margin < MIN_MARGIN:
                row.flags.append("余量不足")
            if row.carrying:
                k_eff = min(raw_k, k_max) if raw_k > 0 else 0.0
                if k_eff > 0:
                    row.sag = sprung_per_member * G / k_eff
                    row.omega = math.sqrt(k_eff / sprung_per_member)
                    c_eff = min(raw_c, c_max) if raw_c > 0 else 0.0
                    if c_eff > 0:
                        row.zeta = c_eff / (2.0 * math.sqrt(k_eff * sprung_per_member))
                    row.static = _equilibrium(axes) + row.sag
                if row.lower is not None and row.upper is not None:
                    lo, hi = float(row.lower), float(row.upper)
                    if row.static is not None and lo <= hi:
                        if row.static > hi:
                            row.flags.append("压到限位")
                        elif row.static < lo:
                            row.flags.append("脱离限位")
            rows.append(row)
    return dict(vehicle=vehicle.name, mass=vehicle.mass, gear_mass=gear_mass,
                unsprung_ratio=gear_mass / vehicle.mass,
                equivalent_mass=0.5 * gear_mass, mounts=len(mounts),
                members=members, sprung_per_member=sprung_per_member,
                rows=rows, flags=[r for r in rows if r.flags])


def as_json(data: dict) -> dict:
    """把结果转成可 JSON 序列化的形状（行是 dataclass）。"""
    out = {key: value for key, value in data.items() if key not in ("rows", "flags")}
    out["rows"] = [row.as_dict() for row in data["rows"]]
    out["flags"] = [row.as_dict() for row in data["flags"]]
    return out


def report_text(data: dict) -> str:
    push = [
        f"# {data['vehicle']} 悬挂",
        "",
        "## 整车",
        "| 项 | 值 |",
        "|---|---|",
        f"| 整备质量 | {data['mass']:,.0f} kg |",
        f"| 行动装置（轮类子零件） | {data['gear_mass']:,.0f} kg，"
        f"占整备质量 {data['unsprung_ratio'] * 100:.1f}% |",
        f"| 悬挂安装座 | {data['mounts']} 个（带平动弹簧的连接点） |",
        f"| 承重成员 | {data['members']} 个（`equilibrium < 0`） |",
        f"| 每成员簧上质量 | {data['sprung_per_member']:,.0f} kg |",
        f"| 轮系转动惯量等效平移质量 | {data['equivalent_mass']:,.0f} kg"
        f"（0.5·Σm，加速估算未含此项） |",
        "",
        "## 逐连接点",
        "| 连接点 | 轴 | k | c | 成员 | 质量/惯量 | 钳位下限 | 余量 | 静挠度 | 静平衡 | 行程 | ω_n | ζ | 判定 |",
        "|---|---|---|---|---|---|---|---|---|---|---|---|---|---|",
    ]
    for row in data["rows"]:
        unit_k = "N·m/rad" if row.rotational else "N/m"
        size_label = (f"{row.member_size:.2f} kg·m²" if row.rotational
                      else f"{row.member_size:,.0f} kg")
        floor_label = (f"{row.floor:.2f} kg·m²" if row.rotational
                       else f"{row.floor:,.0f} kg")
        margin = "—" if row.floor <= 0 else f"{row.margin:.2f}×"
        sag = "—" if row.sag is None else f"{row.sag * 100:.1f} cm"
        static = "—" if row.static is None else f"{row.static * 100:+.1f} cm"
        travel = ("—" if row.lower is None or row.upper is None
                  else f"{float(row.lower) * 100:+.0f}~{float(row.upper) * 100:+.0f} cm")
        omega = "—" if row.omega is None else f"{row.omega:.1f}"
        zeta = "—" if row.zeta is None else f"{row.zeta:.2f}"
        verdict = "、".join(row.flags) or "OK"
        if not row.carrying and not row.rotational:
            verdict += "（不承重）"
        push.append(f"| `{row.connector.split(':')[-1]}` | {row.axis} | "
                    f"{row.stiffness:,.0f} {unit_k} | {row.damping:,.0f} | {row.member} | "
                    f"{size_label} | {floor_label} | {margin} | {sag} | {static} | {travel} | "
                    f"{omega} | {zeta} | {verdict} |")
    push += [
        "",
        "## 判据",
        "",
        "- **钳位下限**：把这组 k/c 原样保留所需的成员质量（转动轴为惯量）。低于它，"
        "`adjustJoint()` 会把 k 压到 `0.95·4·m·Δt⁻²`、把 c 压到 `0.95·(4−s)/2·m·tps`；"
        "刚度一旦饱和，阻尼会被压到几乎为零。",
        "- **静平衡**：`equilibrium + m_簧上·g/k`，即装配后车体停下来的关节坐标；"
        "越过限位说明没有压缩余量，整台车坐在限位上。",
        "- **ζ** 用钳位后的 c 计算，与游戏里实际生效的阻尼一致。",
        "- 带 `*` 的成员是连接点名匹配不到时按全车最轻的轮子估的（轮子做成独立零件、"
        "子零件统一叫 `main` 时会这样），钳位结论仍是保守的那一侧。",
        "",
        f"行动装置质量还进传动链：履带用星形 GearJoint 把所有成员与主动轮锁成刚性带、"
        f"轮式直接滚动，两种情况都按 0.5·Σm 折算成等效平移质量，"
        f"这里是 {data['equivalent_mass']:,.0f} kg"
        f"（{data['equivalent_mass'] / data['mass'] * 100:.1f}% 整备质量）。",
    ]
    return "\n".join(push)
