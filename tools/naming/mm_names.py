#!/usr/bin/env python3
"""内容包命名词表的扫描与改名工具。

配套文档：docs/内容包命名词表.md

    python tools/naming/mm_names.py scan                  # 按词表规则列出违规项
    python tools/naming/mm_names.py scan --rule axis      # 只跑一条规则
    python tools/naming/mm_names.py rename --map m.json    # 预演改名，不写盘
    python tools/naming/mm_names.py rename --map m.json --apply

映射文件是一个扁平 JSON 对象，键为完整旧键、值为完整新键（下例用占位名，避免示例本身
随迁移过时）：

    {
      "subsystem.<命名空间>.旧名": "subsystem.<命名空间>.新名",
      "connector.<车包命名空间>.旧名": "connector.machine_max.新名"
    }

跨命名空间改名（把接口通名收进 `machine_max`）同样写在映射里；语言键需要跟着搬到目标
命名空间的语言文件，`rename` 不代劳这一点。

改名按文本替换执行，因此保留 JSONC 注释、缩进与原文件的换行风格。替换带边界断言，
`subsystem.x.foo` 不会命中 `subsystem.x.foo_bar`。
"""

import argparse
import collections
import glob
import json
import os
import re
import sys

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
MODULES = os.path.join(ROOT, "src", "main", "resources", "spark_modules")

KINDS = ("subsystem", "sub_part", "connector", "interact")
DIR_RANK = {"top": 0, "bottom": 0, "middle": 0, "left": 1, "right": 1, "front": 2, "back": 2}
DIR_SYNONYMS = {"aft", "port", "rear", "up", "down", "center", "mid"}
# 型号名与口径都不属于角色词
MODEL_WORDS = {"pktm", "pkt", "mg34", "2a42", "2a90", "50mm", "csamm", "crows", "762"}
AXIS_WORDS = {"yaw", "pitch"}
# 英文译文标题式的例外：单位保持小写，虚词只在非首词位置保持小写
EN_UNIT = {"mm", "km/h", "km", "kg", "t", "m", "x", "s"}
EN_STOP = {"of", "and", "the", "with", "for", "in", "on", "to", "a", "an", "or", "per", "at", "by", "vs"}
EN_KIND_PREFIXES = ("subsystem.", "sub_part.", "connector.", "interact.", "controlGroup.")

# 名字末段 ↔ 子系统类型
TYPE_SUFFIX = {
    "ammo_loader": "_loader",
    "regen_ammo_rack": "_rack",
    "ammo_rack": "_rack",
    "weapon_controller": "_controller",
    "camera": "_optics",
    "sight": "_sight",
    "launcher": "_launcher",
    "seat": "_seat",
}
# 中文译文里不采用的写法（docs/内容包命名词表.md 4.1）。只收录能按子串判定的词：
# `控制`、`电机`、`装弹机` 这类与正词重叠的写法（`自动装弹机` 含 `装弹机`）不收，靠人工过。
BAD_TERMS = {
    "动力系统": "发动机",
    "传动机构": "传动系统",
    "车控系统": "行驶控制器",
    "履带控制器": "履带行驶控制器",
    "驾驶员入口": "进入驾驶座",
    "入口": "进入<角色>座",
    "舱口": "进入<角色>座",
    "头灯": "前照灯",
}
# 有角色词时座位写「座」（`进入车长座`），不写「位」；只有方位或排数时才写「座位」。
BAD_PATTERNS = (
    re.compile(r"(?:驾驶|车长|炮手|装填手|载员|乘员)位"),
)


def strip_jsonc(text):
    out, i, n, in_str, esc = [], 0, len(text), False, False
    while i < n:
        c = text[i]
        if in_str:
            out.append(c)
            if esc:
                esc = False
            elif c == "\\":
                esc = True
            elif c == '"':
                in_str = False
            i += 1
            continue
        if c == '"':
            in_str = True
            out.append(c)
            i += 1
            continue
        if c == "/" and i + 1 < n and text[i + 1] == "/":
            while i < n and text[i] not in "\r\n":
                i += 1
            continue
        if c == "/" and i + 1 < n and text[i + 1] == "*":
            i += 2
            while i + 1 < n and not (text[i] == "*" and text[i + 1] == "/"):
                i += 1
            i += 2
            continue
        out.append(c)
        i += 1
    return "".join(out)


def load_jsonc(path):
    with open(path, encoding="utf-8-sig") as fh:
        return json.loads(strip_jsonc(fh.read()))


def content_files():
    for path in glob.glob(os.path.join(MODULES, "**", "*.json"), recursive=True):
        posix = path.replace("\\", "/")
        if "/docs/" in posix:
            continue
        yield path


def lang_files():
    for path in glob.glob(os.path.join(MODULES, "**", "lang", "*.json"), recursive=True):
        yield path


def collect():
    """返回 (names, types)：names 是 类 -> 完整键集合，types 是 子系统完整键 -> 类型。"""
    names = {k: set() for k in KINDS}
    types = {}
    bucket = {
        "sub_parts": ("sub_part", "sub_part."),
        "connectors": ("connector", "connector."),
        "interact_boxes": ("interact", "interact."),
    }
    for path in content_files():
        try:
            data = load_jsonc(path)
        except ValueError as exc:
            print("! 解析失败 %s: %s" % (path, exc), file=sys.stderr)
            continue

        def walk(node):
            if isinstance(node, dict):
                for key, value in node.items():
                    if key == "subsystems" and isinstance(value, dict):
                        for name, attr in value.items():
                            if isinstance(attr, dict) and "type" in attr:
                                names["subsystem"].add(name)
                                types[name] = str(attr["type"])
                    elif key in bucket and isinstance(value, dict):
                        kind, prefix = bucket[key]
                        for name in value:
                            if name.startswith(prefix):
                                names[kind].add(name)
                    walk(value)
            elif isinstance(node, list):
                for item in node:
                    walk(item)

        walk(data)
    return names, types


def tail(full):
    return full.split(".", 2)[2]


def segments(full):
    return tail(full).split("_")


# --- 规则 ---------------------------------------------------------------

def rule_dot(full, _type=None):
    return full.count(".") != 2


def rule_dir_order(full, _type=None):
    segs = segments(full)
    positions = [i for i, s in enumerate(segs) if s in DIR_RANK]
    if len(positions) < 2:
        return False
    ranks = [DIR_RANK[segs[i]] for i in positions]
    return ranks != sorted(ranks)


def rule_dir_synonym(full, _type=None):
    if "rearview" in tail(full):
        return False
    return any(s in DIR_SYNONYMS for s in segments(full))


def rule_numbering(full, _type=None):
    text = tail(full)
    if re.search(r"(?<!\d)_\d(?!\d)", text):
        return True
    # light1：编号没有用下划线分隔（`16x16x8` 这类尺寸字面量用 x 分隔，不算）
    return bool(re.search(r"[a-wyz]\d$", text))


def rule_model(full, _type=None):
    return any(s in MODEL_WORDS for s in segments(full))


def rule_axis(full, _type=None):
    return any(s in AXIS_WORDS for s in segments(full))


def rule_suffix(full, type_name=None):
    if type_name is None:
        return False
    short = type_name.split(":")[-1]
    suffix = TYPE_SUFFIX.get(short)
    if not suffix:
        return False
    name = tail(full)
    # 编号跟在它所区分的名词之后（3.2），因此判后缀前先剥掉末尾的 `_<数字>`：
    # `left_atgm_launcher_01` 判作 `left_atgm_launcher`，`passenger_seat_3` 判作
    # `passenger_seat`（3.4 的 `passenger_seat_<编号>` 一行）。
    name = re.sub(r"_\d+$", "", name)
    if name == short:  # 裸名，只能在唯一件上出现，不由本规则判定
        return False
    return not name.endswith(suffix)


RULES = {
    "dot": rule_dot,
    "dir_order": rule_dir_order,
    "dir_synonym": rule_dir_synonym,
    "numbering": rule_numbering,
    "model": rule_model,
    "axis": rule_axis,
    "suffix": rule_suffix,
}


def bare_name_hits():
    """同一变体内同类型安装块超过一个时，裸名（名字就是类型短名）违反 3.4。

    单名判据（RULES 里的那些）看不到「本变体里同类有几件」，所以这条单独走一遍变体。
    裸名本身合法，只有在同型多件、需要靠名字区分彼此时才失败。
    """
    hits = []
    for path in content_files():
        try:
            data = load_jsonc(path)
        except ValueError:
            continue
        if not isinstance(data, dict) or not isinstance(data.get("variants"), dict):
            continue
        variants = data["variants"]
        if "sub_parts" in variants:
            pairs = [("default", variants)]
        else:
            pairs = [(k, v) for k, v in variants.items() if isinstance(v, dict)]
        for _variant_name, variant in pairs:
            section = variant.get("sub_parts")
            if not isinstance(section, dict):
                continue
            if any(f in section for f in ("hit_boxes", "start_bone", "connectors", "subsystems")):
                attrs = [section]      # 简写：整段就是一个子零件
            else:
                attrs = [v for v in section.values() if isinstance(v, dict)]
            by_type = collections.defaultdict(list)
            for attr in attrs:
                for name, sub in (attr.get("subsystems") or {}).items():
                    if isinstance(sub, dict) and "type" in sub:
                        by_type[str(sub["type"]).split(":")[-1]].append(name)
            for short, group in by_type.items():
                if len(group) < 2:
                    continue
                hits.extend(sorted(n for n in group if tail(n) == short))
    return hits


def en_case_hits():
    """英文译文用标题式：实词首字母大写，缩写全大写（4.2）。

    判据落在词上：词首小写即命中。放过单位（`mm`）、非首词的虚词（`of` / `and`），
    以及带大写字母或数字的词——型号 `M256`、`6P49`、`T-90A` 一类不需要再判断。
    """
    en = {}
    for path in lang_files():
        if os.path.basename(path) != "en_us.json":
            continue
        en.update(load_jsonc(path))
    hits = []
    for full in sorted(en):
        if not full.startswith(EN_KIND_PREFIXES):
            continue
        value = en[full]
        if not isinstance(value, str):
            continue
        for idx, token in enumerate(value.split(" ")):
            word = re.sub(r"^[^A-Za-z0-9]+|[^A-Za-z0-9]+$", "", token)
            if not word:
                continue
            low = word.lower()
            if low in EN_UNIT or (idx and low in EN_STOP):
                continue
            if any(c.isupper() for c in word) or any(c.isdigit() for c in word):
                continue
            if word[0].islower():
                hits.append("%s = %s" % (full, value))
                break
    return hits


def cmd_scan(args):
    names, types = collect()
    total = 0
    for rule_name, fn in RULES.items():
        if args.rule and args.rule not in ("all", rule_name):
            continue
        hits = []
        for kind in KINDS:
            for full in sorted(names[kind]):
                type_name = types.get(full) if kind == "subsystem" else None
                if fn(full, type_name):
                    hits.append(full)
        total += len(hits)
        print("[%s] %d" % (rule_name, len(hits)))
        if args.verbose:
            for full in hits:
                print("    " + full)

    if args.rule in (None, "all", "bare"):
        hits = bare_name_hits()
        total += len(hits)
        print("[bare] %d" % len(hits))
        if args.verbose:
            for full in hits:
                print("    " + full)

    if args.rule in (None, "all", "lang"):
        missing = []
        zh = {}
        for path in lang_files():
            if os.path.basename(path) != "zh_cn.json":
                continue
            zh.update(load_jsonc(path))
        for full in sorted(names["subsystem"]):
            if full not in zh:
                missing.append(full)
        print("[lang_missing] %d" % len(missing))
        for full in missing:
            print("    " + full)

    if args.rule in (None, "all", "term"):
        zh = {}
        for path in lang_files():
            if os.path.basename(path) != "zh_cn.json":
                continue
            zh.update(load_jsonc(path))
        hits = [
            (k, v)
            for k, v in zh.items()
            if isinstance(v, str) and not k.startswith("description.")
            and (any(b in v for b in BAD_TERMS) or any(p.search(v) for p in BAD_PATTERNS))
        ]
        print("[term] %d" % len(hits))
        for key, value in hits:
            print("    %s = %s" % (key, value))

    if args.rule in (None, "all", "en"):
        hits = en_case_hits()
        total += len(hits)
        print("[en_case] %d" % len(hits))
        if args.verbose:
            for hit in hits:
                print("    " + hit)

    print("合计违规（含重叠）：%d" % total)
    return 0


def cmd_rename(args):
    with open(args.map, encoding="utf-8") as fh:
        mapping = json.load(fh)
    if not isinstance(mapping, dict) or not mapping:
        print("映射文件必须是非空 JSON 对象", file=sys.stderr)
        return 2
    for old, new in mapping.items():
        if old == new:
            print("! 映射里新旧同名：%s" % old, file=sys.stderr)
            return 2
        if old.split(".")[0] != new.split(".")[0]:
            print("! 跨类改名不被支持：%s -> %s" % (old, new), file=sys.stderr)
            return 2

    targets = list(content_files()) + list(lang_files())
    unchanged = []
    for old, new in mapping.items():
        pattern = re.compile(re.escape(old) + r"(?![0-9a-z_])")
        touched = 0
        for path in targets:
            with open(path, encoding="utf-8", newline="") as fh:
                text = fh.read()
            hits = len(pattern.findall(text))
            if not hits:
                continue
            # 撞名判据同样带右边界：新名是旧名的前缀时（`driver_hatch` ⊂ `driver_hatch_interact`）
            # 不算撞名；只有同一个完整键已经存在才拒绝改写该文件。
            if re.search(re.escape(new) + r"(?![0-9a-z_])", text):
                print("! %s 已出现在 %s，跳过该文件以免撞名" % (new, os.path.relpath(path, ROOT)), file=sys.stderr)
                continue
            touched += hits
            if args.apply:
                with open(path, "w", encoding="utf-8", newline="") as fh:
                    fh.write(pattern.sub(new, text))
        if touched == 0:
            unchanged.append(old)
        print("%-52s -> %-52s %d 处" % (old, new, touched))

    for old in unchanged:
        print("! 未命中任何引用：%s" % old, file=sys.stderr)
    print("%s：%d 条映射" % ("已写盘" if args.apply else "预演（未写盘）", len(mapping)))
    return 0


def main(argv=None):
    parser = argparse.ArgumentParser(description="内容包命名词表工具")
    sub = parser.add_subparsers(dest="command", required=True)

    scan = sub.add_parser("scan", help="按词表规则列出违规项")
    scan.add_argument("--rule", help="只跑一条规则：%s、bare、en、lang、term、all" % "、".join(RULES))
    scan.add_argument("-v", "--verbose", action="store_true", help="列出每个违规键")
    scan.set_defaults(func=cmd_scan)

    rename = sub.add_parser("rename", help="按映射改名（默认预演）")
    rename.add_argument("--map", required=True, help="映射文件路径")
    rename.add_argument("--apply", action="store_true", help="写盘")
    rename.set_defaults(func=cmd_rename)

    args = parser.parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
