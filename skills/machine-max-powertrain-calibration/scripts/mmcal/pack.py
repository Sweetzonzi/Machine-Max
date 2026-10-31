"""内容包索引：按目录名与 meta.json 建立 id → 定义文件的映射。

内容 id 是 `<namespace>/<文件名>`，与所在包无关，所以同一个 id 可能被多个包定义；
后载入的覆盖先载入的，并留一条警告。
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path

from .jsonc import load_jsonc

KINDS = ("parts", "subsystems", "materials", "templates", "connectors", "fittings")


@dataclass
class Pack:
    path: Path
    pack_id: str
    namespace: str
    root: Path          # 包内内容目录 <pack>/<namespace>
    dependencies: list


@dataclass
class Entry:
    pack: Pack
    path: Path
    data: dict


def default_pack_root(script_dir: Path) -> Path:
    """从脚本位置推断内容包根目录：<repo>/src/main/resources/spark_modules。"""
    for candidate in (script_dir.parent.parent.parent / "src/main/resources/spark_modules",
                      script_dir.parent / "spark_modules"):
        if candidate.is_dir():
            return candidate
    raise SystemExit("找不到内容包目录，请用 --pack-root 指定")


class Registry:
    def __init__(self):
        self.packs: dict[str, Pack] = {}
        self.entries: dict[str, dict[str, Entry]] = {k: {} for k in KINDS}
        self.warnings: list[str] = []

    # -- 载入

    def load_root(self, root: Path):
        for child in sorted(p for p in root.iterdir() if p.is_dir()):
            meta_file = child / "meta.json"
            if not meta_file.exists():
                continue
            try:
                meta = load_jsonc(meta_file)
            except json.JSONDecodeError as exc:
                self.warnings.append(f"{meta_file}: 解析失败 ({exc})")
                continue
            pack_id = str(meta.get("id", child.name))
            namespace = pack_id.split(":")[0]
            pack = Pack(child, pack_id, namespace, child / namespace,
                        meta.get("dependencies") or [])
            self.packs[pack_id] = pack
            self._index(pack)

    def _index(self, pack: Pack):
        for kind in KINDS:
            base = pack.root / kind
            if not base.is_dir():
                continue
            for path in sorted(base.rglob("*.json")):
                if "docs" in path.parts:
                    continue
                try:
                    data = load_jsonc(path)
                except json.JSONDecodeError as exc:
                    self.warnings.append(f"{path}: 解析失败 ({exc})")
                    continue
                if not isinstance(data, dict):
                    continue
                cid = f"{pack.namespace}:{path.stem}"
                prev = self.entries[kind].get(cid)
                if prev is not None:
                    self.warnings.append(
                        f"{kind} 重复 id {cid}: {prev.path} 与 {path}（取后者）")
                self.entries[kind][cid] = Entry(pack, path, data)

    # -- 查询

    def get(self, kind: str, cid: str) -> Entry | None:
        if not cid:
            return None
        if ":" not in cid:
            cid = f"machine_max:{cid}"
        return self.entries[kind].get(cid)

    def need(self, kind: str, cid: str, where: str) -> Entry:
        entry = self.get(kind, cid)
        if entry is None:
            raise SystemExit(f"找不到 {kind} 定义 {cid}（引用自 {where}）")
        return entry

    def ids(self, kind: str) -> list:
        return sorted(self.entries[kind])


def model_path(pack: Pack, model_id: str, where: str) -> Path:
    """按模型 id 在包内 models/ 下找 .geo.json。"""
    name = model_id.split(":", 1)[1] if ":" in model_id else model_id
    base = pack.root / "models"
    if base.is_dir():
        for candidate in sorted(base.rglob(f"{name}.json")):
            return candidate
    raise SystemExit(f"找不到模型 {model_id}（引用自 {where}）")
