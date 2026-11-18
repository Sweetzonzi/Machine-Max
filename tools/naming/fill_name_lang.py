"""把五类名字键缺失的译文补进各命名空间的语言文件。

只做文本插入：按前缀分组，插到同组最后一条之后，保留原文件的 BOM / 行尾 / 缩进，
最后统一修一遍行尾逗号（每行后面还有键行时补逗号）。

用法：python tools/naming/fill_name_lang.py [--apply]
"""

import json
import sys

ROOT = "src/main/resources/spark_modules"

CONNECTORS = {
    "axis": ("铰链轴", "Hinge Axis"),
    "lock": ("锁扣", "Latch"),
    "hull": ("车壳连接点", "Hull Connector"),
    "hull_axis": ("车壳门轴", "Hull Hinge"),
    "hull_lock": ("车壳锁扣", "Hull Latch"),
    "hull_front": ("车壳前部连接点", "Front Hull Connector"),
    "hull_back": ("车壳后部连接点", "Rear Hull Connector"),
    "chassis_axis": ("车架门轴", "Chassis Hinge"),
    "chassis_lock": ("车架锁扣", "Chassis Latch"),
    "chassis_front": ("车架前部连接点", "Front Chassis Connector"),
    "chassis_back": ("车架后部连接点", "Rear Chassis Connector"),
    "trailer": ("牵引销", "Trailer Coupler"),
    "trunk": ("后备箱连接点", "Trunk Connector"),
    "front_armor": ("前部装甲挂点", "Front Armor Mount"),
    "backup_wheel": ("备胎挂点", "Spare Wheel Mount"),
    "back_left": ("后部左侧连接点", "Rear Left Connector"),
    "back_right": ("后部右侧连接点", "Rear Right Connector"),
    "top_front": ("顶部前方连接点", "Top Front Connector"),
    "top_left_front": ("顶部左前连接点", "Top Front Left Connector"),
    "top_left_back": ("顶部左后连接点", "Top Rear Left Connector"),
    "top_right_front": ("顶部右前连接点", "Top Front Right Connector"),
    "top_right_back": ("顶部右后连接点", "Top Rear Right Connector"),
    "front_light_left": ("左前灯挂点", "Front Light Left Mount"),
    "front_light_right": ("右前灯挂点", "Front Light Right Mount"),
    "right_front_wheel_chassis": ("右前轮车架连接点", "Front Right Wheel Chassis Connector"),
}
for index in (1, 2, 3):
    CONNECTORS[f"top_left_{index}"] = (f"顶部左侧 {index} 号连接点", f"Top Left Connector {index}")
    CONNECTORS[f"top_right_{index}"] = (f"顶部右侧 {index} 号连接点", f"Top Right Connector {index}")
for index in range(1, 6):
    CONNECTORS[f"left_{index}"] = (f"左侧 {index} 号挂点", f"Left Mount {index}")
    CONNECTORS[f"mid_{index}"] = (f"中部 {index} 号挂点", f"Center Mount {index}")
    CONNECTORS[f"right_{index}"] = (f"右侧 {index} 号挂点", f"Right Mount {index}")
for index in (1, 2, 3):
    CONNECTORS[f"left_wheel_{index}"] = (f"左侧 {index} 号车轮挂点", f"Left Wheel Mount {index}")
    CONNECTORS[f"right_wheel_{index}"] = (f"右侧 {index} 号车轮挂点", f"Right Wheel Mount {index}")
for index in (1, 2):
    CONNECTORS[f"left_back_wheel_{index}"] = (f"左后 {index} 号车轮挂点", f"Rear Left Wheel Mount {index}")
    CONNECTORS[f"right_back_wheel_{index}"] = (f"右后 {index} 号车轮挂点", f"Rear Right Wheel Mount {index}")

SUBSYSTEMS = {
    "back_wheel_driver": ("后轮驱动", "Rear Wheel Drive"),
    "front_wheel_driver": ("前轮驱动", "Front Wheel Drive"),
    "motorbike_controller": ("摩托车控制器", "Motorcycle Controller"),
    "copilot_seat": ("副驾驶座", "Passenger Seat"),
    "left_seat": ("左座", "Left Seat"),
    "right_seat": ("右座", "Right Seat"),
    "left_back_seat": ("左后座", "Rear Left Seat"),
    "right_back_seat": ("右后座", "Rear Right Seat"),
    "glass": ("玻璃", "Glass"),
    "front_glass": ("前窗玻璃", "Windshield"),
    "left_back_glass": ("左后窗玻璃", "Rear Left Window"),
    "right_back_glass": ("右后窗玻璃", "Rear Right Window"),
    "left_main_light": ("左前照灯", "Left Headlight"),
    "right_main_light": ("右前照灯", "Right Headlight"),
    "left_front_light": ("左前灯", "Front Left Light"),
    "right_front_light": ("右前灯", "Front Right Light"),
    "left_fog_light": ("左雾灯", "Left Fog Light"),
    "right_fog_light": ("右雾灯", "Right Fog Light"),
    "left_front_fog_light": ("左前雾灯", "Front Left Fog Light"),
    "right_front_fog_light": ("右前雾灯", "Front Right Fog Light"),
    "left_back_light": ("左后灯", "Rear Left Light"),
    "right_back_light": ("右后灯", "Rear Right Light"),
    "left_light": ("左灯", "Left Light"),
    "middle_light": ("中间灯", "Center Light"),
    "right_light": ("右灯", "Right Light"),
    "left_back_wheel_1_driver": ("左后 1 号轮驱动", "Rear Left Wheel 1 Drive"),
    "left_back_wheel_2_driver": ("左后 2 号轮驱动", "Rear Left Wheel 2 Drive"),
    "right_back_wheel_1_driver": ("右后 1 号轮驱动", "Rear Right Wheel 1 Drive"),
    "right_back_wheel_2_driver": ("右后 2 号轮驱动", "Rear Right Wheel 2 Drive"),
}
for index in (1, 2, 3, 4):
    SUBSYSTEMS[f"light{index}"] = (f"灯 {index}", f"Light {index}")

INTERACTS = {
    "left_door": ("进入左侧座位", "Enter Left Seat"),
    "right_door": ("进入右侧座位", "Enter Right Seat"),
}

TARGETS = [
    (f"{ROOT}/Machine-Max_Builtin/machine_max/lang", "machine_max",
     {"connector": CONNECTORS, "subsystem": SUBSYSTEMS, "interact": INTERACTS},
     {"interact.machine_max.golden.seat": "interact.machine_max.golden_seat"}),
    (f"{ROOT}/Machine-Max_M1A2_SEP/m1a2_sep/lang", "m1a2_sep",
     {"connector": {"crows_terminal_mount": ("遥控武器站终端挂点", "Remote Weapon Station Terminal Mount")},
      "subsystem": {"crows_terminal": ("遥控武器站终端", "Remote Weapon Station Terminal")},
      "controlGroup": {"loader_m240": ("装填手机枪", "Loader Machine Gun")}},
     {}),
]


def load(path):
    with open(path, "rb") as handle:
        raw = handle.read()
    bom = raw.startswith(b"\xef\xbb\xbf")
    text = raw.decode("utf-8-sig")
    return json.loads(text), bom, text


def key_of(line):
    stripped = line.strip()
    if not stripped.startswith('"'):
        return None
    end = stripped.find('":')
    if end < 0:
        return None
    return stripped[1:end]


def insert(path, namespace, groups, renames, apply):
    data, bom, text = load(path)
    eol = "\r\n" if "\r\n" in text else "\n"
    lines = text.split(eol)

    for old, new in renames.items():
        if old in data:
            if new in data:
                raise SystemExit(f"{path}：{new} 已存在，先删掉旧键 {old}")
            lines = [line.replace('"' + old + '":', '"' + new + '":') for line in lines]

    existing = set(data)
    for old, new in renames.items():
        if old in existing:
            existing.discard(old)
            existing.add(new)

    added = 0
    for kind in ("sub_part", "subsystem", "connector", "interact", "controlGroup"):
        entries = groups.get(kind)
        if not entries:
            continue
        fresh = []
        for name in sorted(entries):
            key = f"{kind}.{namespace}.{name}"
            if key in existing:
                existing.add(key)
                continue
            zh, en = entries[name]
            fresh.append((key, zh if path.endswith("zh_cn.json") else en))
        if not fresh:
            continue
        prefix = '"' + kind + "."
        anchor = None
        indent = None
        for index, line in enumerate(lines):
            if (key_of(line) or "").startswith(kind + "."):
                anchor = index
                indent = line[: len(line) - len(line.lstrip())]
        if anchor is None:
            closing = max(index for index, line in enumerate(lines) if line.strip() == "}")
            indent = "  "
            fresh_lines = [f'{indent}"{key}": {json.dumps(value, ensure_ascii=False)},'
                           for key, value in fresh]
            lines[closing:closing] = fresh_lines
        else:
            fresh_lines = [f'{indent}"{key}": {json.dumps(value, ensure_ascii=False)},'
                           for key, value in fresh]
            lines[anchor + 1:anchor + 1] = fresh_lines
        added += len(fresh)

    # 统一修行尾逗号：键行后面还有键行就补逗号，紧邻 } 的键行去掉逗号。
    last_key_line = max(index for index, line in enumerate(lines) if key_of(line))
    for index in range(len(lines)):
        if key_of(lines[index]) is None:
            continue
        stripped = lines[index].rstrip()
        has_comma = stripped.endswith(",")
        if index == last_key_line:
            lines[index] = stripped[:-1] if has_comma else stripped
        elif not has_comma:
            lines[index] = stripped + ","

    output = eol.join(lines)
    if bom:
        output = "\ufeff" + output
    if apply:
        with open(path, "w", encoding="utf-8", newline="") as handle:
            handle.write(output)
    print(f"{'写入' if apply else '预演'} {path}：新增 {added} 条，改名 {len(renames)} 条")
    return added


def main():
    apply = "--apply" in sys.argv
    total = 0
    for directory, namespace, groups, renames in TARGETS:
        for language in ("zh_cn", "en_us"):
            total += insert(f"{directory}/{language}.json", namespace, groups, renames, apply)
    print(f"合计新增 {total} 条")


if __name__ == "__main__":
    main()
