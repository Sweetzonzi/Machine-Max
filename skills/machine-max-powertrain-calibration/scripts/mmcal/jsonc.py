"""JSONC 读取：内容包的 JSON 里带 // 注释与尾逗号。"""

from __future__ import annotations

import json
import re
from pathlib import Path


def strip_jsonc(text: str) -> str:
    """去掉 // 与 /* */ 注释，再去掉尾逗号。字符串内的内容原样保留。"""
    out = []
    i, n = 0, len(text)
    in_str = escaped = False
    while i < n:
        ch = text[i]
        if in_str:
            out.append(ch)
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == '"':
                in_str = False
            i += 1
            continue
        if ch == '"':
            in_str = True
            out.append(ch)
            i += 1
            continue
        if ch == "/" and i + 1 < n and text[i + 1] == "/":
            j = text.find("\n", i)
            if j < 0:
                break
            i = j
            continue
        if ch == "/" and i + 1 < n and text[i + 1] == "*":
            j = text.find("*/", i + 2)
            i = n if j < 0 else j + 2
            continue
        out.append(ch)
        i += 1
    return re.sub(r",(\s*[}\]])", r"\1", "".join(out))


def load_jsonc(path: Path):
    return json.loads(strip_jsonc(path.read_text(encoding="utf-8")))
