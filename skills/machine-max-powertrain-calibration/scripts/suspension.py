#!/usr/bin/env python3
"""悬挂检查：静挠度、行程占用与关节钳位余量。

回答的是「这组弹簧装得上吗」：作者写的刚度/阻尼会不会在装配时被 `adjustJoint()`
压下来、车体装配后停在哪、轮子质量够不够撑住这组参数。不涉及动力源，任何整车都能跑。

轮重与弹簧是耦合的：钳位的有效质量是 `m_A·m_B/(m_A+m_B)`，被轻的一侧决定，
所以车体多重都不影响结论，只有轮子自己的质量算数。

用法示例：

    python suspension.py --pack Machine-Max_2S38
    python suspension.py --pack Machine-Max_MTLB --template mtlb
    python suspension.py --pack Machine-Max_Civilian --template ae86 --strict
"""

from __future__ import annotations

import argparse
import json
import math
import sys
from pathlib import Path

from mmcal import resolve_vehicle
from mmcal.cli import add_common, flush_warnings, load_pack, pick_template
from mmcal.suspension import TPS, as_json, report_text, suspensions


def main(argv=None):
    parser = argparse.ArgumentParser(description="悬挂静挠度、行程与关节钳位余量")
    add_common(parser)
    parser.add_argument("--template", help="模板名；包内有多个模板时必填")
    parser.add_argument("--load-members", type=int,
                        help="承重的悬挂成员数，缺省按带弹簧的连接点个数均分；"
                             "履带上不着地的诱导轮、主动轮不计入时传实际承重轮数")
    parser.add_argument("--tps", type=float, default=TPS,
                        help=f"物理步频，默认 {TPS:g}（服务端 baseStep 5 × 20）")
    parser.add_argument("--strict", action="store_true",
                        help="任一行被钳位、坐限位或余量不足时退出码 1")
    parser.add_argument("--json", action="store_true", help="输出 JSON 而不是 Markdown")
    args = parser.parse_args(argv)
    if not math.isfinite(args.tps) or args.tps <= 0:
        parser.error("--tps 必须是正有限数")
    if args.load_members is not None and args.load_members <= 0:
        parser.error("--load-members 必须为正整数")

    script_dir = Path(__file__).resolve().parent
    _, registry, pack = load_pack(args, script_dir)
    if pack is None:
        return 0
    vehicle = resolve_vehicle(registry, pack, pick_template(args, pack))
    data = suspensions(registry, vehicle, args.load_members, args.tps)
    print(json.dumps(as_json(data), ensure_ascii=False, indent=2)
          if args.json else report_text(data))
    flush_warnings(registry)
    return 1 if (args.strict and data["flags"]) else 0


if __name__ == "__main__":
    sys.exit(main())
