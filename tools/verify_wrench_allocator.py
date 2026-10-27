#!/usr/bin/env python3
"""用 NumPy 的增广最小二乘（SVD）独立核对 Java 批量分配器，并可测量 Java 内核。

运行：python tools/verify_wrench_allocator.py [--benchmark] [--allocation] [--java-home JDK路径]
仅依赖 Python 的 NumPy；Java 端编译分配器和导出类，不需要 Gradle 或第三方 Java 库。
"""

import argparse
import json
import os
from pathlib import Path
import subprocess

import numpy as np


def reference(case):
    """保持相同批量钉界规则，但用增广系统求最小二乘，不复刻 Java 的 Cholesky。"""
    target = np.array(case["target"], dtype=float)
    count, rows = len(case["columns"]), len(target)
    columns = np.array(case["columns"], dtype=float).reshape(count, rows)
    lower, upper = np.array(case["lower"]), np.array(case["upper"])
    costs, scales = np.array(case["costs"]), np.array(case["scales"])
    active = np.array(case["active"], dtype=bool)
    controls = np.zeros(count)
    damping = 0.0
    decompositions = clamp_rounds = 0
    if not active.any():
        status = "NO_ACTIVE_ROWS"
    elif not target[active].any():
        status = "SOLVED"
    else:
        a = (columns[:, active] * scales[active]).T
        b = target[active] * scales[active]
        free = np.any(a != 0, axis=0) & (lower != upper)
        if not free.any():
            status = "NO_AUTHORITY"
        else:
            damping = max(case["floor"] ** 2,
                          case["ratio"] * np.sum(a[:, free] ** 2 / costs[free]) / active.sum())
            while free.any():
                # 增广系统直接表达跟踪误差和器件努力，维度与 Java 的行空间系统不同。
                af = a[:, free]
                augmented = np.vstack((af, np.diag(np.sqrt(damping * costs[free]))))
                rhs = np.concatenate((b - a[:, ~free] @ controls[~free], np.zeros(free.sum())))
                controls[free] = np.linalg.lstsq(augmented, rhs, rcond=None)[0]
                decompositions += 1
                bad = free & ((controls < lower) | (controls > upper))
                if not bad.any():
                    break
                controls[bad] = np.clip(controls[bad], lower[bad], upper[bad])
                free[bad] = False
                clamp_rounds += 1
            status = "SOLVED"
    achieved = columns.T @ controls
    achieved[~active] = 0
    residual = np.where(active, target - achieved, 0)
    return dict(controls=controls, achieved=achieved, residual=residual,
                damping_squared=damping, decompositions=decompositions,
                clamp_rounds=clamp_rounds, status=status)


def main():
    """编译独立探针并逐例比较，任何数值或状态差异均以非零退出码报告。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-home", default=os.environ.get("JAVA_HOME"))
    parser.add_argument("--benchmark", action="store_true")
    parser.add_argument("--allocation", action="store_true")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    suffix = ".exe" if os.name == "nt" else ""
    java = str(Path(args.java_home) / "bin" / ("java" + suffix)) if args.java_home else "java"
    javac = str(Path(args.java_home) / "bin" / ("javac" + suffix)) if args.java_home else "javac"
    output = root / "build" / "wrench-reference"
    output.mkdir(parents=True, exist_ok=True)
    sources = [root / "src/main/java/io/github/sweetzonzi/machine_max/util/WrenchAllocator.java",
               root / "src/test/java/io/github/sweetzonzi/machine_max/util/WrenchAllocatorReferenceProbe.java"]
    subprocess.run([javac, "-encoding", "UTF-8", "-d", str(output), *map(str, sources)], check=True)
    command = [java, "-cp", str(output),
               "io.github.sweetzonzi.machine_max.util.WrenchAllocatorReferenceProbe"]
    lines = subprocess.check_output(command, text=True, encoding="utf-8").splitlines()
    maximum_error = 0.0
    for line in lines:
        case = json.loads(line)
        expected = reference(case)
        for key in ("controls", "achieved", "residual", "damping_squared"):
            actual = np.asarray(case[key])
            np.testing.assert_allclose(actual, expected[key], rtol=1e-7, atol=1e-8,
                                       err_msg=f"{case['name']}: {key}")
            maximum_error = max(maximum_error, float(np.max(np.abs(actual - expected[key]), initial=0)))
        for key in ("status", "decompositions", "clamp_rounds"):
            assert case[key] == expected[key], (case["name"], key, case[key], expected[key])
    print(json.dumps(dict(cases=len(lines), maximum_absolute_error=maximum_error,
                          reference="NumPy augmented least squares / SVD"), ensure_ascii=False), flush=True)
    if args.benchmark:
        subprocess.run([*command, "--benchmark"], check=True)
    if args.allocation:
        subprocess.run([*command, "--allocation"], check=True)


if __name__ == "__main__":
    main()
