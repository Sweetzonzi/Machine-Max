"""传动与驱动端侧的定义：变速箱、传动箱、驱动端、轮胎材质。"""

from __future__ import annotations

from dataclasses import dataclass

from .constants import DEFAULT_GEARBOX, DEFAULT_TRACK, DEFAULT_WHEEL
from .pack import Entry


@dataclass
class Gearbox:
    final_ratio: float
    ratios: list
    switch_time: float
    source: str

    @classmethod
    def from_entry(cls, entry: Entry, name: str = "") -> "Gearbox":
        data = entry.data
        return cls(float(data.get("final_ratio", DEFAULT_GEARBOX["final_ratio"])),
                   [float(x) for x in data.get("ratios", DEFAULT_GEARBOX["ratios"])],
                   float(data.get("switch_time", DEFAULT_GEARBOX["switch_time"])),
                   name or f"{entry.pack.namespace}:{entry.path.stem}")

    def forward(self) -> list:
        return [r for r in self.ratios if r > 0]


@dataclass
class Transmission:
    """分动箱 / 差速器。

    `power_outputs` 是「输出端子系统名 → 该端固定减速比」的映射，写在部件 JSON 的
    `subsystem_data` 里而不是子系统静态属性里。它的减速比与变速箱挡位串联：
    轮端转速 = 曲轴转速 / (挡位传动比 × 终传比 × 本端减速比)。
    """

    ratios: dict
    source: str

    @classmethod
    def from_ref(cls, ref: dict) -> "Transmission":
        outputs = ref.get("power_outputs") or {}
        return cls({str(name): float(ratio) for name, ratio in outputs.items()},
                   str(ref.get("definition") or "传动箱"))

    def driven_ratio(self, driven_names, notes: list) -> float:
        """取出驱动轮所在输出端的减速比。

        各输出端减速比不一致时，轮端转速与扭矩按各自减速比分化，本工具不建模这种分化：
        按驱动轮各端均值折算，并在注意里点名。
        """
        ratios = {name: r for name, r in self.ratios.items() if name in driven_names}
        if not ratios:
            notes.append(f"传动箱 `{self.source}` 的 power_outputs 里没有驱动轮（{len(self.ratios)} 个输出端），"
                         f"按减速比 1.0 计算；请确认功率链是否接到了车轮上")
            return 1.0
        values = sorted({round(r, 6) for r in ratios.values()})
        if len(values) == 1:
            return values[0]
        mean = sum(ratios.values()) / len(ratios)
        notes.append(f"传动箱各驱动端减速比不一致 {values}，按均值 {mean:.3f} 折算；"
                     f"各轮实际转速与扭矩会随本端减速比不同")
        return mean


@dataclass
class DriveEnd:
    """一个驱动端：一个车轮，或一条履带的主动轮。

    两种驱动的数值语义相同——该端能施加的驱动力矩（N·m），只用来夹紧轮端输出；
    `kind` 只影响报告措辞。履带另有 `max_speed`：主动轮角速度上限（rad/s），
    会变成一条独立的极速上限；车轮没有这个上限，取 `None`。
    """

    name: str
    max_drive_torque: float
    max_brake_torque: float
    max_hand_brake_torque: float
    source: str
    kind: str = "wheel"
    max_speed: float | None = None

    @classmethod
    def from_wheel_entry(cls, entry: Entry, name: str) -> "DriveEnd":
        """车轮驱动：力矩上限写在 `roll` 下，字段沿用 `*_force` 的名字。"""
        roll = entry.data.get("roll") or {}
        return cls(name,
                   float(roll.get("max_drive_force", DEFAULT_WHEEL["max_drive_force"])),
                   float(roll.get("max_brake_force", DEFAULT_WHEEL["max_brake_force"])),
                   float(roll.get("max_hand_brake_force", DEFAULT_WHEEL["max_hand_brake_force"])),
                   f"{entry.pack.namespace}:{entry.path.stem}")

    @classmethod
    def from_track_entry(cls, entry: Entry, name: str) -> "DriveEnd":
        data = entry.data
        return cls(name,
                   float(data.get("max_drive_torque", DEFAULT_TRACK["max_drive_torque"])),
                   float(data.get("max_brake_torque", DEFAULT_TRACK["max_brake_torque"])),
                   float(data.get("max_hand_brake_torque",
                                  DEFAULT_TRACK["max_hand_brake_torque"])),
                   f"{entry.pack.namespace}:{entry.path.stem}",
                   kind="track",
                   max_speed=float(data.get("max_speed", DEFAULT_TRACK["max_speed"])))


@dataclass
class Material:
    c_rr: float
    friction_xy: tuple
    source: str

    @classmethod
    def from_entry(cls, entry: Entry) -> "Material":
        physics = entry.data.get("physics") or {}
        friction = physics.get("friction") or [1.0, 1.0, 1.0]
        if isinstance(friction, (int, float)):
            friction = [float(friction)] * 3
        return cls(float(physics.get("rolling_friction", 0.02)),
                   (float(friction[0]), float(friction[1])),
                   f"{entry.pack.namespace}:{entry.path.stem}")
