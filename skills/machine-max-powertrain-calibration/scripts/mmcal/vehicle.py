"""整车：模板 → 子零件列表。"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from .geometry import load_geo
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
