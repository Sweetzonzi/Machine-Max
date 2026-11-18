#!/usr/bin/env python3
"""内容包名字键的引用解析检查。

配套文档：docs/内容包命名词表.md

名字键（`subsystem.` / `sub_part.` / `connector.` / `interact.` / `controlGroup.`）
除了出现在内容 JSON 的键位上，还会以字符串形式被别处引用：

  * 动画 MoLang —— `local.connector_offset('connector.machine_max.hull_left_road_wheel_01', 1)`
  * 碰撞箱 / 交互区的 `condition` —— MoLang 表达式，可含 `local.has_connector(...)`
  * 碰撞箱的 `subsystem` 字段、子系统的 `connector` 字段、`signal_targets` 的取值
  * 源码与 GameTest 内联的 JSON

引用写错或没跟着改名时，游戏里只表现为动画不跟动、条件恒假、交互区不亮，日志里没有痕迹。
解析分三层作用域：

  1. 变体自解析 —— 每个变体的 JSON 里引用的名字必须在该变体自己的子零件里定义
     （`AbstractPart.findConnector` / `findSubsystem` 只查本部件的子零件）
  2. 动画解析 —— 动画文件的引用须在引用它的那些变体的名字并集内
     （一份动画常被同一装配体的多个零件共用，每个零件只认自己有的连接点）
  3. 全局解析 —— 源码字符串与其余文件，按全部内容包的定义并集核对

    python tools/naming/mm_refs.py             # 全量检查
    python tools/naming/mm_refs.py --kind connector
    python tools/naming/mm_refs.py --verbose   # 连同已解析的引用一并列出

退出码：有未解析引用时为 1。
"""

import argparse
import difflib
import json
import os
import re
import sys

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
MODULES = os.path.join(ROOT, "src", "main", "resources", "spark_modules")
SOURCE_DIRS = [
    os.path.join(ROOT, "src", "main", "java"),
    os.path.join(ROOT, "src", "main", "kotlin"),
    os.path.join(ROOT, "src", "test"),
]

KINDS = ("subsystem", "sub_part", "connector", "interact", "controlGroup")
KEY_RE = re.compile(r"(?<![a-z0-9_:./-])(" + "|".join(KINDS) + r")\.([a-z0-9_]+)\.([a-z0-9_]+)\b")
STRING_RE = re.compile(r'"((?:[^"\\\n]|\\.)*)"')

# 音效 id 也占用 `subsystem.` 前缀（见 sounds/*.json 的键与 subtitles），
# 由音效 id 及其点前缀构成的集合不当作名字引用。
SOUND_SPACE = set()
# 残余的同形非名字空间：GameTest 运行期自造的命名空间，以及没有音效文件兜底的默认音效 id。
EXEMPT_PREFIXES = ("subsystem.machine_max_test.",)
EXEMPT_TOKENS = {
    "subsystem.launcher.dry_fire": "LauncherSubsystemStaticAttr 的默认空放音效 id，无同名声效文件",
}


def is_name(token):
    """token 是否应按内容名字解析。"""
    return token not in SOUND_SPACE and token not in EXEMPT_TOKENS and not token.startswith(EXEMPT_PREFIXES)


def strip_jsonc(text):
    """去掉 // 与 /* */ 注释，字符串内容原样保留。"""
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
        if text.startswith("//", i):
            j = text.find("\n", i)
            i = n if j < 0 else j
            continue
        if text.startswith("/*", i):
            j = text.find("*/", i + 2)
            i = n if j < 0 else j + 2
            continue
        out.append(c)
        i += 1
    return "".join(out)


def load_jsonc(path):
    try:
        return json.loads(strip_jsonc(open(path, encoding="utf-8").read()))
    except json.JSONDecodeError as error:
        raise SystemExit(f"JSON 解析失败：{path}: {error}")


def walk(base, suffixes):
    for dirpath, dirnames, filenames in os.walk(base):
        dirnames[:] = sorted(d for d in dirnames if d not in {".git", "build", "__pycache__"})
        for name in sorted(filenames):
            if name.endswith(suffixes):
                yield os.path.join(dirpath, name)


def rel(path):
    return os.path.relpath(path, ROOT).replace(os.sep, "/")


def name(value):
    """从 JSON 片段里取出所有名字键。"""
    out = set()
    stack = [value]
    while stack:
        node = stack.pop()
        if isinstance(node, dict):
            stack.extend(node.keys())
            stack.extend(node.values())
        elif isinstance(node, list):
            stack.extend(node)
        elif isinstance(node, str):
            out.update(m.group(0) for m in KEY_RE.finditer(node))
    return out


def sub_part_attrs(variant):
    """返回变体的子零件属性列表，兼容 `sub_parts` 的单 SubPartAttr 简写。"""
    section = variant.get("sub_parts")
    if not isinstance(section, dict):
        return []
    if any(f in section for f in ("hit_boxes", "start_bone", "connectors", "subsystems")):
        return [section]          # 简写：整段就是一个子零件
    return [v for v in section.values() if isinstance(v, dict)]


def variant_names(variant):
    """一个变体自己定义的名字键：子零件名 + 各子零件的子系统 / 连接点 / 交互区。"""
    out = set()
    section = variant.get("sub_parts")
    if isinstance(section, dict) and not any(
        f in section for f in ("hit_boxes", "start_bone", "connectors", "subsystems")
    ):
        out.update(k for k in section if KEY_RE.fullmatch(k))
    for attr in sub_part_attrs(variant):
        for field in ("subsystems", "connectors", "interact_boxes"):
            sub = attr.get(field)
            if isinstance(sub, dict):
                out.update(k for k in sub if KEY_RE.fullmatch(k))
    return out


def keys_in(value):
    """JSON 对象里作为键位出现的名字键。"""
    out = set()
    stack = [value]
    while stack:
        node = stack.pop()
        if isinstance(node, dict):
            out.update(k for k in node if KEY_RE.fullmatch(k))
            stack.extend(node.values())
        elif isinstance(node, list):
            stack.extend(node)
    return out


def values_in(value):
    """JSON 对象里作为字符串出现的名字键（引用）。"""
    out = set()
    stack = [value]
    while stack:
        node = stack.pop()
        if isinstance(node, dict):
            stack.extend(node.values())
        elif isinstance(node, list):
            stack.extend(node)
        elif isinstance(node, str):
            out.update(m.group(0) for m in KEY_RE.finditer(node))
    return out


def variants_of(definition):
    """返回 [(变体名, 变体对象)]，兼容单变体简写。"""
    variants = definition.get("variants")
    if not isinstance(variants, dict):
        return []
    if "sub_parts" in variants:
        return [("default", variants)]
    return [(k, v) for k, v in variants.items() if isinstance(v, dict)]


def defined_keys(section, kind):
    return {k for k in section if k.split(".", 1)[0] == kind}


def report(unresolved, defined, title):
    """unresolved: {token: [sites]}；按名字聚合打印，附近似建议。"""
    if not unresolved:
        return False
    print(f"\n{title}（{len(unresolved)} 个名字，{sum(len(v) for v in unresolved.values())} 处）：")
    for token, sites in sorted(unresolved.items()):
        guesses = difflib.get_close_matches(token, defined, n=2, cutoff=0.6)
        hint = f"  → 现存近似：{', '.join(guesses)}" if guesses else ""
        print(f"  {token}{hint}")
        for site in sorted(set(sites)):
            print(f"      {site}")
    return True


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--kind", choices=KINDS, action="append", help="只检查某一类")
    parser.add_argument("--verbose", action="store_true", help="连同已解析的引用一并列出")
    args = parser.parse_args()
    kinds = set(args.kind) if args.kind else set(KINDS)

    def wanted(token):
        return token.split(".", 1)[0] in kinds

    # ---------- 第一层：内容包结构 ----------
    parts = []            # (path, 变体名, 变体对象)
    anim_refs = {}        # 动画路径 -> 引用集合
    other_refs_json = []  # (path, 名字键) —— 模板等非变体 JSON 的引用
    global_defined = set()
    lang_keys = []        # (path, 名字键)

    for path in walk(MODULES, ".json"):
        slash = path.replace(os.sep, "/")
        if "/docs/" in slash:
            continue
        if "/lang/" in slash:
            for line in open(path, encoding="utf-8"):
                m = re.match(r'\s*"((?:' + "|".join(KINDS) + r')\.[^"]+)"\s*:', line)
                if m:
                    lang_keys.append((path, m.group(1)))
            continue

        text = open(path, encoding="utf-8").read()
        if slash.endswith(".animation.json"):
            tokens = {t for t in (m.group(0) for m in KEY_RE.finditer(text)) if wanted(t)}
            if tokens:
                anim_refs[path] = tokens
            continue

        data = load_jsonc(path)
        if not isinstance(data, dict):
            continue

        if "/sounds/" in slash:
            # 音效 id 形如 `subsystem.gearbox.up.light`，与名字键同前缀空间，整体排除
            for sound_id in data:
                if isinstance(sound_id, str):
                    pieces = sound_id.split(".")
                    for i in range(1, len(pieces) + 1):
                        SOUND_SPACE.add(".".join(pieces[:i]))
            continue

        if "/parts/" in slash:
            for variant_name, variant in variants_of(data):
                parts.append((path, variant_name, variant))
                global_defined |= variant_names(variant)
                global_defined |= keys_in(variant)
            continue

        # 模板把各零件的连接点配成一条线：里面的名字键（键位与 `advConnectorName` 这类字段）
        # 都是引用，定义只在 parts 与控制组里出现。
        if "/templates/" in slash:
            other_refs_json.extend((path, token) for token in keys_in(data) | values_in(data) if wanted(token))
            continue

        # 子系统静态属性、材质、HUD 布局等：键位是定义，字符串值是引用
        global_defined |= keys_in(data)
        if "/control_groups/" in slash:
            # 控制组的名字写在 name 字段里
            global_defined |= values_in(data)
            continue
        other_refs_json.extend((path, token) for token in values_in(data) if wanted(token))

    # ---------- 第一层：变体自解析 ----------
    attr_unresolved = {}
    per_variant = []      # (path, 变体名, 名字集合, 动画名)
    for path, variant_name, variant in parts:
        own = variant_names(variant)
        anim_id = variant.get("animations")
        anim_name = anim_id.rsplit(":", 1)[-1] if isinstance(anim_id, str) else None
        per_variant.append((path, variant_name, own, anim_name))
        for token in values_in(variant):
            if wanted(token) and token not in own:
                attr_unresolved.setdefault(token, []).append(f"{rel(path)}#{variant_name}")

    # ---------- 第二层：动画解析 ----------
    # 变体的 `animations` 是「忽略前导目录的目录名」：machine_max:ae86 命中 animations/ 下
    # 目录名为 ae86 的所有动画文件（见 variant_attr.schema.json），所以按目录段匹配。
    # 同一份动画可能被一个装配体的多个变体共用（Civilian 的各车身件共用一份车身动画），
    # 每份变体只认自己有的连接点，所以按引用方作用域的并集解析。
    anim_unresolved = {}
    anim_scope = {}
    anim_orphans = []
    for path, tokens in anim_refs.items():
        segments = set(os.path.dirname(path[len(MODULES):].lstrip(os.sep)).replace(os.sep, "/").split("/"))
        owners = [v for v in per_variant if v[3] and v[3] in segments]
        if not owners:
            anim_orphans.append(path)
            continue
        scope = set().union(*(owner[2] for owner in owners))
        anim_scope[path] = scope
        for token in tokens - scope:
            anim_unresolved.setdefault(token, []).append(
                ", ".join(f"{rel(owner[0])}#{owner[1]}" for owner in owners)
            )

    # ---------- 第三层：其余文件按全局定义并集解析 ----------
    other_unresolved = {}
    other_refs = [(rel(path), token) for path, token in other_refs_json]
    for base in SOURCE_DIRS:
        if not os.path.isdir(base):
            continue
        for path in walk(base, (".java", ".kt")):
            text = open(path, encoding="utf-8", errors="ignore").read()
            for literal in STRING_RE.findall(text):
                other_refs.extend((rel(path), m.group(0)) for m in KEY_RE.finditer(literal))
    for site, token in other_refs:
        if wanted(token) and token not in global_defined:
            other_unresolved.setdefault(token, []).append(site)

    # ---------- 语言表孤儿键 ----------
    orphans = {}
    for path, key in lang_keys:
        if wanted(key) and key not in global_defined:
            orphans.setdefault(key, []).append(rel(path))

    # ---------- 输出 ----------
    # 音效 id 与 GameTest 自造名字在整轮扫描后才齐备，这里统一剔除
    attr_unresolved = {k: v for k, v in attr_unresolved.items() if is_name(k)}
    anim_unresolved = {k: v for k, v in anim_unresolved.items() if is_name(k)}
    other_unresolved = {k: v for k, v in other_unresolved.items() if is_name(k)}
    failed = False
    failed |= report(attr_unresolved, global_defined, "变体内未解析的名字引用")
    failed |= report(anim_unresolved, global_defined, "动画里未解析的名字引用")
    failed |= report(other_unresolved, global_defined, "源码 / 其余文件里未解析的名字引用")
    failed |= report(orphans, global_defined, "语言表里的孤儿名字键（没有对应的内容定义）")

    if args.verbose:
        print()
        for path, scope in sorted(anim_scope.items()):
            print(f"ok   {rel(path)}: 引用 {len(anim_refs[path])}，作用域 {len(scope)}")
        for path in sorted(anim_orphans):
            print(f"??   {rel(path)}: 没有任何变体的 animations 指向它")

    if not failed:
        print(
            f"名字键引用全部解析：定义 {len(global_defined)} 个，"
            f"变体 {len(parts)} 个、动画 {len(anim_scope)}/{len(anim_refs)} 份、"
            f"源码与模板引用 {len(other_refs)} 处"
        )
        return 0
    return 1


if __name__ == "__main__":
    sys.exit(main())
