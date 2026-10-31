"""动力源曲线：内燃机与电机。

两种曲线的形状不同，但对外接口一致：`torque(rpm)` / `net_torque(rpm)` /
`curve_power(rpm)` / `peak_power()`。求解器只依赖这组接口，所以换动力源不换公式。
"""

from __future__ import annotations

import math

from .constants import DEFAULT_ENGINE
from .pack import Entry


class PowerCurve:
    """两种动力源共用的功率与峰值求值。"""

    max_power: float
    max_torque: float
    red_line_rpm: float
    damping: list
    source: str
    kind = ""

    def torque(self, rpm: float) -> float:      # pragma: no cover - 由子类实现
        raise NotImplementedError

    def raw_torque(self, rpm: float) -> float:
        """未施加额外上限的扭矩。只有内燃机有「折线 + `max_power` 封顶」两层，故默认同 `torque`。"""
        return self.torque(rpm)

    def damping_torque(self, rpm: float) -> float:  # pragma: no cover
        raise NotImplementedError

    def net_torque(self, rpm: float) -> float:
        return max(self.torque(rpm) - self.damping_torque(rpm), 0.0)

    def curve_power(self, rpm: float) -> float:
        """曲线功率 τ(n)·ω，不含内阻；`max_power` 应等于它的峰值。"""
        return self.torque(rpm) * rpm * math.pi / 30

    def net_power(self, rpm: float) -> float:
        return self.net_torque(rpm) * rpm * math.pi / 30

    def peak_power(self) -> tuple:
        return self._peak(self.curve_power)

    def peak_net_power(self) -> tuple:
        return self._peak(self.net_power)

    def _peak(self, fn) -> tuple:
        best = (0.0, 0.0)
        rpm = 1.0
        while rpm <= self.red_line_rpm:
            value = fn(rpm)
            if value > best[0]:
                best = (value, rpm)
            rpm += 1.0
        return best

    def rows(self) -> list:      # pragma: no cover - 由子类实现
        raise NotImplementedError


class Engine(PowerCurve):
    """与 `EngineSubsystem.getTorqueAtSpeed` 同口径的四段折线 + 功率上限。"""

    kind = "内燃机"

    def __init__(self, max_power: float, max_torque: float, idle_rpm: float, idle_ratio: float,
                 max_torque_rpm: float, red_line_rpm: float, red_ratio: float,
                 damping: list, source: str):
        self.max_power = max_power
        self.max_torque = max_torque
        self.idle_rpm = idle_rpm
        self.idle_ratio = idle_ratio
        self.max_torque_rpm = max_torque_rpm
        self.red_line_rpm = red_line_rpm
        self.red_ratio = red_ratio
        self.damping = damping
        self.source = source

    @classmethod
    def from_entry(cls, entry: Entry) -> "Engine":
        data = entry.data
        if "max_power" not in data:
            raise SystemExit(f"{entry.path}: 缺 max_power（必填字段）")
        if "max_torque" not in data:
            raise SystemExit(f"{entry.path}: 缺 max_torque（必填字段）")
        return cls(
            float(data["max_power"]), float(data["max_torque"]),
            float(data.get("idle_rpm", DEFAULT_ENGINE["idle_rpm"])),
            float(data.get("idle_rpm_torque_ratio", DEFAULT_ENGINE["idle_rpm_torque_ratio"])),
            float(data.get("max_torque_rpm", DEFAULT_ENGINE["max_torque_rpm"])),
            float(data.get("red_line_rpm", DEFAULT_ENGINE["red_line_rpm"])),
            float(data.get("red_line_torque_ratio", DEFAULT_ENGINE["red_line_torque_ratio"])),
            [float(x) for x in data.get("damping_factors", DEFAULT_ENGINE["damping_factors"])],
            f"{entry.pack.namespace}:{entry.path.stem}")

    def raw_torque(self, rpm: float) -> float:
        """折线部分的扭矩，不含 `max_power` 封顶；红线以上为 0。"""
        if rpm <= 0:
            return 0.0
        idle_speed = self.idle_rpm * math.pi / 30
        max_torque_speed = self.max_torque_rpm * math.pi / 30
        red_speed = self.red_line_rpm * math.pi / 30
        w = rpm * math.pi / 30
        if w <= idle_speed:
            return w / max(idle_speed, 1e-9) * self.max_torque * self.idle_ratio
        if w <= max_torque_speed:
            k = (w - idle_speed) / max(max_torque_speed - idle_speed, 1e-9)
            return k * self.max_torque + (1 - k) * self.max_torque * self.idle_ratio
        if w <= red_speed:
            k = (w - max_torque_speed) / max(red_speed - max_torque_speed, 1e-9)
            return k * self.max_torque * self.red_ratio + (1 - k) * self.max_torque
        return 0.0

    def torque(self, rpm: float) -> float:
        """折线扭矩与功率上限取小，与 `EngineSubsystem.getTorqueAtSpeed` 同口径。"""
        raw = self.raw_torque(rpm)
        if raw <= 0:
            return 0.0
        w = rpm * math.pi / 30
        return min(raw, self.max_power / max(w, 1e-9))

    def damping_torque(self, rpm: float) -> float:
        w = rpm * math.pi / 30
        if abs(w) <= self.idle_rpm * math.pi / 30:
            return 0.0
        total = 0.0
        for order, coeff in enumerate(self.damping):
            total += coeff * abs(w) ** order
        return total

    def rows(self) -> list:
        return [
            ("类型", self.kind),
            ("怠速 / 最大扭矩 / 红线", f"{self.idle_rpm:.0f} / {self.max_torque_rpm:.0f} / "
                                     f"{self.red_line_rpm:.0f} rpm"),
            ("对应扭矩", f"{self.max_torque * self.idle_ratio:.0f} / {self.max_torque:.0f} / "
                     f"{self.max_torque * self.red_ratio:.0f} N·m"),
            ("max_power 字段", f"{self.max_power:,.0f} W"),
        ]


class Motor(PowerCurve):
    """与 `MotorSubsystem.getTorqueAtSpeed` 同口径：基速以下恒扭矩，以上按功率衰减。"""

    kind = "电机"

    def __init__(self, max_power: float, max_torque: float, red_line_rpm: float,
                 red_line_power_ratio: float, damping: list, source: str):
        self.max_power = max_power
        self.max_torque = max_torque
        self.red_line_rpm = red_line_rpm
        self.red_line_power_ratio = red_line_power_ratio
        self.damping = damping
        self.source = source

    @classmethod
    def from_entry(cls, entry: Entry) -> "Motor":
        data = entry.data
        if "max_power" not in data:
            raise SystemExit(f"{entry.path}: 缺 max_power（必填字段）")
        return cls(
            float(data["max_power"]),
            float(data.get("max_torque", 100.0)),
            float(data.get("red_line_rpm", 10000.0)),
            float(data.get("red_line_power_ratio", 0.8)),
            [float(x) for x in data.get("damping_factors", [10.0, 0.1, 0.00005])],
            f"{entry.pack.namespace}:{entry.path.stem}")

    @property
    def base_rpm(self) -> float:
        """恒扭矩区与恒功率区的转折转速。"""
        return self.max_power / max(self.max_torque, 0.01) * 30 / math.pi

    def torque(self, rpm: float) -> float:
        if rpm <= 0:
            return 0.0
        base_speed = self.max_power / max(self.max_torque, 0.01)
        red_speed = self.red_line_rpm * math.pi / 30
        w = rpm * math.pi / 30
        if w <= base_speed:
            return self.max_torque
        if w <= red_speed:
            k = (w - base_speed) / max(red_speed - base_speed, 1e-9)
            power = self.max_power * (1 - k * (1 - self.red_line_power_ratio))
            return power / w
        return (self.max_power * self.red_line_power_ratio / w
                * 0.5 ** ((w - red_speed) / (red_speed * 0.1)))

    def damping_torque(self, rpm: float) -> float:
        # 代码里低于 0.5 × 400 rpm 换算出的角速度时内阻为 0，实车转速够不到，等价于不计内阻
        w = rpm * math.pi / 30
        if abs(w) <= 0.5 * 400.0 / math.pi * 30:
            return 0.0
        return sum(coeff * abs(w) ** order for order, coeff in enumerate(self.damping))

    def rows(self) -> list:
        return [
            ("类型", self.kind),
            ("基速 / 红线", f"{self.base_rpm:.0f} / {self.red_line_rpm:.0f} rpm"),
            ("峰值扭矩", f"{self.max_torque:.0f} N·m"),
            ("红线功率比", f"{self.red_line_power_ratio:.2f}"),
            ("max_power 字段", f"{self.max_power:,.0f} W"),
        ]


SOURCES = {"engine": Engine, "motor": Motor}


def from_entry(entry: Entry, kind: str) -> PowerCurve:
    if kind not in SOURCES:
        raise SystemExit(f"未知动力源类型 {kind}（可选：{', '.join(SOURCES)}）")
    return SOURCES[kind].from_entry(entry)
