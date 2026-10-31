"""CLI 公共部分：内容包定位、模板选择、警告输出。

四个入口脚本都要做同样三件事，集中在这里，避免各自抄一份。
"""

from __future__ import annotations

import sys
from pathlib import Path

from .pack import Pack, Registry, default_pack_root


def add_common(parser):
    parser.add_argument("--pack-root", type=Path, help="内容包根目录（含各个 Machine-Max_* 包）")
    parser.add_argument("--pack", help="载具内容包目录名或包 id，如 Machine-Max_K17")
    parser.add_argument("--list", action="store_true", help="列出可用内容包与模板后退出")


def print_packs(registry: Registry, root: Path):
    print(f"内容包根目录：{root}")
    for key in sorted(registry.packs):
        item = registry.packs[key]
        tpl_dir = item.root / "templates"
        names = sorted(p.stem for p in tpl_dir.glob("*.json")) if tpl_dir.is_dir() else []
        print(f"- {key}  ({item.path.name})  模板：{', '.join(names) if names else '无'}")


def load_pack(args, script_dir: Path):
    """载入内容包索引。`--list` 时打印清单并返回 (root, registry, None)。"""
    root = args.pack_root or default_pack_root(script_dir)
    registry = Registry()
    registry.load_root(root)
    if args.list:
        print_packs(registry, root)
        return root, registry, None
    if not args.pack:
        raise SystemExit("需要 --pack；用 --list 查看可用内容包")
    pack = registry.packs.get(args.pack)
    if pack is None:
        matches = [p for p in registry.packs.values() if p.path.name == args.pack]
        if not matches:
            raise SystemExit(f"--pack {args.pack} 未知；可用：{', '.join(sorted(registry.packs))}")
        pack = matches[0]
    return root, registry, pack


def pick_template(args, pack: Pack) -> str:
    templates = sorted(p.stem for p in (pack.root / "templates").glob("*.json"))
    if args.template:
        return args.template
    if len(templates) == 1:
        return templates[0]
    if not templates:
        raise SystemExit(f"{pack.path.name} 没有模板，工具需要一份整车模板；"
                         f"用 --template 指定，或在该包里放一份 templates/*.json")
    raise SystemExit(f"{pack.path.name} 有 {len(templates)} 个模板，请用 --template 指定其一："
                     f"{', '.join(templates)}")


def flush_warnings(registry: Registry):
    if not registry.warnings:
        return
    print("\n警告：", file=sys.stderr)
    for warning in registry.warnings:
        print(f"- {warning}", file=sys.stderr)
