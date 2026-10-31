"""整车：模板 → 子零件列表，以及驱动端解析。"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from .driveline import DriveEnd
from .geometry import load_geo, wheel_radii
from .pack import Entry, Pack, Registry, model_path


@dataclass
class SubPartSpec:
    part_id: str
    variant: str | None
    name: str
    mass: float
    projected_area: tuple
    hitboxes: dict
    subsystems: dict
    geo: dict
    hydro: dict


@dataclass
class Vehicle:
    name: str
    template_path: Path
    sub_parts: list

    @property
    def mass(self) -> float:
        return sum(sp.mass for sp in self.sub_parts)

    def subsystem_refs(self, type_suffix: str):
        """遍历整车声明的子系统：产出 (子零件, 子系统名, 声明)。"""
        for sp in self.sub_parts:
            for name, ref in sp.subsystems.items():
                if str(ref.get("type", "")).endswith(type_suffix):
                    yield sp, name, ref

    def power_source_ref(self):
        """取动力源声明：返回 (类型, (子零件, 名称, 声明), 是否混动)。

        类型为 `None` 表示整车没有动力源；混动时按内燃机算，并把第二个返回值标为混动。
        """
        engine = next(self.subsystem_refs(":engine"), None)
        motor = next(self.subsystem_refs(":motor"), None)
        if engine:
            return "engine", engine, motor is not None
        if motor:
            return "motor", motor, False
        return None, None, False


def resolve_vehicle(registry: Registry, pack: Pack, template_id: str) -> Vehicle:
    entry = registry.get("templates", template_id)
    if entry is None:
        stem = template_id.split(":")[-1]
        entry = registry.get("templates", f"{pack.namespace}:{stem}")
    if entry is None:
        raise SystemExit(f"找不到模板 {template_id}")
    return build_vehicle(registry, entry)


def build_vehicle(registry: Registry, entry: Entry) -> Vehicle:
    data = entry.data
    parts = data.get("parts") or {}
    if not parts:
        raise SystemExit(f"{entry.path}: 模板里没有 parts")
    sub_parts = []
    geo_cache = {}
    for uuid, part in parts.items():
        if not isinstance(part, dict):
            continue
        part_id = part.get("registry_key")
        if not part_id:
            continue
        part_entry = registry.need("parts", part_id, f"模板 {entry.path.name}")
        variant_name = part.get("variant")
        variants = part_entry.data.get("variants") or {}
        if "sub_parts" in variants:
            variant = variants
        else:
            variant = variants.get(variant_name) or {}
            if not variant and variants:
                variant = next(iter(variants.values()))
        if not variant:
            registry.warnings.append(f"{part_entry.path}: 变体 {variant_name} 不存在")
            continue
        model_id = variant.get("model")
        geo = {}
        if model_id:
            if model_id not in geo_cache:
                geo_cache[model_id] = load_geo(
                    model_path(part_entry.pack, model_id, part_entry.path.name))
            geo = geo_cache[model_id]
        parts_map = variant.get("sub_parts") or {}
        # sub_parts 也可写成单个无名子零件（字段直接平铺），此时不按名字匹配
        if "hit_boxes" in parts_map or "start_bone" in parts_map:
            wanted = [("main", parts_map)]
        else:
            wanted = [(name, sp) for name, sp in parts_map.items()
                      if name in (part.get("sub_parts") or {})]
        for sp_name, sp in wanted:
            if not isinstance(sp, dict):
                continue
            projected = sp.get("projected_area") or [-1, -1, -1]
            projected = [float(x) for x in (list(projected) + [-1, -1, -1])[:3]]
            sub_parts.append(SubPartSpec(
                part_id, variant_name, sp_name,
                float(sp.get("mass", 0.0)), tuple(projected),
                sp.get("hit_boxes") or {}, sp.get("subsystems") or {}, geo,
                sp.get("hydrodynamics") or {}))
    if not sub_parts:
        raise SystemExit(f"{entry.path}: 没有解析出任何子零件")
    return Vehicle(entry.path.stem, entry.path, sub_parts)


def drive_system(vehicle: Vehicle, registry: Registry) -> dict:
    """驱动端：车轮或履带主动轮，以及折算车速用的半径。

    返回 `kind` / `ends` / `radius` / `radii` / `total` / `notes`。`total` 是参与附着
    分摊的轮位数：轮式取整车全部轮子（含备胎、随动轮），履带取驱动端数——履带把
    车重摊在整条履带接地上，没有"只有两个轮子吃力"这回事。

    履带的半径取主动轮，不取最大轮面：`TrackDriveSubsystem.radius()` 读的是被驱动
    那个子零件的碰撞球，负重轮半径再大也不参与车速换算。主动轮在模板里没有显式
    指向，按 `track_drive` 的 `connector` 名去掉前缀后与子零件名精确匹配。
    """
    wheel_refs = list(vehicle.subsystem_refs(":wheel_driver"))
    if wheel_refs:
        ends = []
        for sub_part, name, ref in wheel_refs:
            definition = ref.get("definition")
            if not definition:
                continue
            entry = registry.need("subsystems", definition, sub_part.part_id)
            ends.append(DriveEnd.from_wheel_entry(entry, name))
        if not ends:
            raise SystemExit("车轮子系统都没有 definition")
        wheel_subs = [sp for sp in vehicle.sub_parts if wheel_radii(sp)]
        if not wheel_subs:
            raise SystemExit("整车没有任何 type=wheel 的碰撞箱，无法取轮半径")
        radii = sorted({round(r, 5) for sp in wheel_subs for r in wheel_radii(sp)})
        return dict(kind="wheel", ends=ends, radius=radii[-1], radii=radii,
                    total=len(wheel_subs), notes=[])

    track_refs = list(vehicle.subsystem_refs(":track_drive"))
    if not track_refs:
        raise SystemExit("整车没有 machine_max:wheel_driver 或 machine_max:track_drive 子系统")
    ends, notes, radii = [], [], []
    for sub_part, name, ref in track_refs:
        definition = ref.get("definition")
        if not definition:
            raise SystemExit(f"履带子系统 {name} 没有 definition")
        ends.append(DriveEnd.from_track_entry(
            registry.need("subsystems", definition, sub_part.part_id), name))
        driven = _driven_wheel(vehicle, str(ref.get("connector") or ""), name)
        radii.append(round(max(wheel_radii(driven)), 5))
    radii = sorted(set(radii))
    if len(radii) > 1:
        notes.append(f"两条履带的主动轮半径不一致 {radii}，按最大者 {radii[-1]:.5f} 计算")
    return dict(kind="track", ends=ends, radius=radii[-1], radii=radii,
                total=len(ends), notes=notes)


def _driven_wheel(vehicle: Vehicle, connector_name: str, where: str):
    """履带 drive 连接点对应的被驱动子零件。

    `connector.machine_max.hull_left_sprocket` → 子零件 `left_sprocket`：从连接点名
    逐段去掉前缀，取第一个能唯一定位到某个轮类子零件的后缀。
    """
    tokens = connector_name.split(".")[-1].split("_")
    wheels = [sp for sp in vehicle.sub_parts if wheel_radii(sp)]
    for start in range(1, len(tokens)):
        suffix = "_".join(tokens[start:])
        hits = [sp for sp in wheels if sp.name.split(".")[-1] == suffix]
        if len(hits) == 1:
            return hits[0]
    raise SystemExit(f"履带子系统 {where} 的连接点 {connector_name} 找不到同名主动轮子零件；"
                     f"主动轮子零件需以连接点名去掉 hull_ 之类前缀后的名字命名")

