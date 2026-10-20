#!/usr/bin/env python
"""Check an exported Bedrock .geo.json against MachineMax expectations.

Usage:
    python verify-geo.py path/to/model.geo.json [--expect-bones 87] [--no-track-prefix Track_]
"""
import argparse
import json
import sys


def load(path):
    with open(path, encoding="utf-8") as fh:
        data = json.load(fh)
    return data["minecraft:geometry"][0]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("path")
    ap.add_argument("--expect-bones", type=int, default=None)
    ap.add_argument("--track-prefix", default="Track_")
    args = ap.parse_args()

    geo = load(args.path)
    bones = geo["bones"]
    names = [b["name"] for b in bones]
    by_name = {b["name"]: b for b in bones}
    problems = []

    desc = geo.get("description", {})
    print("identifier      :", desc.get("identifier"))
    print("texture size    :", desc.get("texture_width"), "x", desc.get("texture_height"))
    print("visible bounds  :", desc.get("visible_bounds_width"), "x", desc.get("visible_bounds_height"),
          "offset", desc.get("visible_bounds_offset"))
    print("bones           :", len(bones))
    if args.expect_bones is not None and len(bones) != args.expect_bones:
        problems.append("bone count %d != expected %d" % (len(bones), args.expect_bones))

    tracks = [n for n in names if n.startswith(args.track_prefix) and not n.startswith(args.track_prefix + "_")]
    print("track-like bones:", len(tracks))
    if tracks:
        problems.append("%d track bones still exported (set export=false on them)" % len(tracks))

    empty_uv = 0
    textured = 0
    for b in bones:
        for c in b.get("cubes", []):
            if c.get("uv") == {}:
                empty_uv += 1
            elif c.get("uv"):
                textured += 1
    print("cubes uv {}     :", empty_uv)
    print("cubes textured  :", textured)
    if empty_uv == 0:
        problems.append("no cube exports with uv {} - hit box cubes keep UVs and will render")

    locators = {}
    for b in bones:
        for name, pos in (b.get("locators") or {}).items():
            if name in locators:
                problems.append("duplicate locator %s on %s and %s" % (name, locators[name][0], b["name"]))
            locators[name] = (b["name"], pos)
    pairs = []
    for name, (owner, pos) in locators.items():
        if name.startswith("hull_"):
            mirror = name[len("hull_"):]
            other = None
            if mirror.startswith(("left_", "right_")):
                side, rest = mirror.split("_", 1)
                other = "%s_%s_hull" % (side, rest)
            if other and other in locators:
                same = all(abs(pos[i] - locators[other][1][i]) < 0.01 for i in range(3))
                pairs.append((name, other, same))
                if not same:
                    problems.append("locator pair not aligned: %s vs %s" % (name, other))
    print("connector pairs :", len(pairs), "(aligned: %d)" % sum(1 for p in pairs if p[2]))
    print("locators total  :", len(locators))

    mesh = geo.get("bones", [])
    print("bones w/ cubes  :", sum(1 for b in mesh if b.get("cubes")))

    print()
    if problems:
        print("PROBLEMS:")
        for p in problems:
            print("  -", p)
        return 1
    print("OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
