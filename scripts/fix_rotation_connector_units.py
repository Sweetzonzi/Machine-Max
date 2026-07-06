#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
修复旋转轴连接器 stiffness/damping 的单位转换。
Java 代码已将转换系数从 π/180 修正为 180/π，
此脚本将 JSON 中旋转轴 (键名以 'r' 结尾) 的 stiffness/damping 缩放 (π/180)²，
以保持现有物理行为不变。

用法:
  python fix_rotation_connector_units.py --test    # 仅测试单文件，打印修改前后对比
  python fix_rotation_connector_units.py            # 执行全部修改
"""

import json
import math
import os
import re
import sys
import glob

# 缩放因子: (π/180)²，补偿 Java 代码中从 π/180 → 180/π 的修正
SCALE_FACTOR = (math.pi / 180.0) ** 2


def strip_json_comments(text: str) -> str:
    """移除 JSON 中的 // 行注释，返回标准 JSON 字符串"""
    lines = text.splitlines()
    result = []
    for line in lines:
        # 找到不在字符串内的 //
        in_string = False
        i = 0
        while i < len(line):
            c = line[i]
            if c == '"' and (i == 0 or line[i - 1] != '\\'):
                in_string = not in_string
            elif c == '/' and not in_string and i + 1 < len(line) and line[i + 1] == '/':
                line = line[:i]
                break
            i += 1
        result.append(line)
    return '\n'.join(result)

# 三个需要处理的目录
TARGET_DIRS = [
    r"d:\Files\Project_MinecraftMods\Machine-Max\src\main\resources\spark_modules\Machine-Max_Official_Pack\machine_max\connectors",
    r"d:\Files\Project_MinecraftMods\Machine-Max\src\main\resources\spark_modules\machine_max.builtin\machine_max\connectors",
    r"d:\Files\Project_MinecraftMods\Machine-Max\src\main\resources\spark_modules\sdkfz\machine_max\connectors\sdkfz234",
]


def is_rotation_axis(key: str) -> bool:
    """判断 joint_attrs 的键是否为旋转轴（以 'r' 结尾）"""
    return key.endswith("r") and len(key) == 2


def process_file(filepath: str, dry_run: bool = False) -> dict:
    """
    处理单个 JSON 文件，返回修改摘要。
    dry_run=True 时不写入文件，仅返回信息。
    """
    with open(filepath, "r", encoding="utf-8") as f:
        raw_text = f.read()

    clean_text = strip_json_comments(raw_text)
    data = json.loads(clean_text)

    modified = False
    changes = []

    joint_attrs = data.get("joint_attrs", {})
    for axis_key, axis_attrs in joint_attrs.items():
        if not is_rotation_axis(axis_key):
            continue

        for param in ("stiffness", "damping"):
            if param in axis_attrs and isinstance(axis_attrs[param], (int, float)):
                old_val = axis_attrs[param]
                new_val = round(old_val * SCALE_FACTOR, 6)
                if abs(old_val - new_val) > 1e-9:
                    changes.append({
                        "axis": axis_key,
                        "param": param,
                        "old": old_val,
                        "new": new_val,
                    })
                    if not dry_run:
                        axis_attrs[param] = new_val
                    modified = True

    if modified and not dry_run:
        with open(filepath, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
            f.write("\n")

    return {"file": filepath, "modified": modified, "changes": changes}


def main():
    dry_run = "--test" in sys.argv

    print(f"缩放因子: (π/180)² = {SCALE_FACTOR:.10f}")
    print()

    all_files = []
    for d in TARGET_DIRS:
        if not os.path.isdir(d):
            print(f"[警告] 目录不存在: {d}")
            continue
        all_files.extend(glob.glob(os.path.join(d, "**", "*.json"), recursive=True))

    if dry_run:
        # 测试模式：只处理第一个有旋转轴的文件
        print("=== 测试模式：仅预览第一个匹配文件 ===\n")
        for fp in sorted(all_files):
            result = process_file(fp, dry_run=True)
            if result["modified"]:
                print(f"文件: {os.path.basename(fp)}")
                for ch in result["changes"]:
                    print(f"  {ch['axis']}.{ch['param']}: {ch['old']} → {ch['new']}")
                print()
                break
        else:
            print("未找到需要修改的文件。")
        return

    # 正式模式
    print("=== 正式模式：修改所有文件 ===\n")
    total_modified = 0
    for fp in sorted(all_files):
        result = process_file(fp, dry_run=False)
        if result["modified"]:
            total_modified += 1
            print(f"[修改] {os.path.basename(fp)}")
            for ch in result["changes"]:
                print(f"  {ch['axis']}.{ch['param']}: {ch['old']} → {ch['new']}")
            print()

    print(f"共修改 {total_modified} 个文件。")


if __name__ == "__main__":
    main()
