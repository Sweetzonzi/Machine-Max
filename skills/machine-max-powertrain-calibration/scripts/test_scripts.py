#!/usr/bin/env python3
"""脚本自检。

    python test_scripts.py            # 在 scripts/ 目录下直接跑
    python -m unittest test_scripts   # 或用 unittest

断言里的数值都来自实际内容包，用作回归基线：整车侧取 Machine-Max_K17，
单速车与传动箱取 Machine-Max_Civilian 的 mini_ev。
"""

import contextlib
import io
import json
import math
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import calibrate  # noqa: E402
import curve  # noqa: E402
import drag  # noqa: E402
import gears  # noqa: E402
import mmcal  # noqa: E402

PACK_ROOT = Path(__file__).resolve().parents[3] / "src/main/resources/spark_modules"


def run_cli(module, argv):
    """跑一次入口脚本，返回 (退出码, stdout)。"""
    buffer = io.StringIO()
    with contextlib.redirect_stdout(buffer), contextlib.redirect_stderr(io.StringIO()):
        code = module.main(argv)
    return code, buffer.getvalue()


def run_json(module, argv):
    code, text = run_cli(module, argv)
    if code != 0:
        raise AssertionError(f"{module.__name__} 退出码 {code}")
    return json.loads(text)


def need_packs(cls):
    if not PACK_ROOT.is_dir():
        raise unittest.SkipTest(f"找不到内容包目录 {PACK_ROOT}")


class JsoncTest(unittest.TestCase):
    def test_strips_line_and_block_comments(self):
        text = '{\n  "a": 1, // 注释\n  /* 块注释 */\n  "b": "http://x//y"\n}'
        self.assertEqual(json.loads(mmcal.strip_jsonc(text)), {"a": 1, "b": "http://x//y"})

    def test_strips_trailing_commas(self):
        self.assertEqual(json.loads(mmcal.strip_jsonc('{"a": [1, 2,],}')), {"a": [1, 2]})


class ParseTest(unittest.TestCase):
    def test_power_units(self):
        self.assertAlmostEqual(mmcal.parse_power("500 PS"), 500 * mmcal.PS, places=3)
        self.assertAlmostEqual(mmcal.parse_power("368 kW"), 368000.0, places=3)
        self.assertAlmostEqual(mmcal.parse_power("500hp"), 500 * 745.6998715822702, places=3)

    def test_accel(self):
        v, seconds = mmcal.parse_accel("50:3.0")
        self.assertAlmostEqual(v, 50 / 3.6, places=6)
        self.assertAlmostEqual(seconds, 3.0)


class K17Test(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        need_packs(cls)
        cls.code, cls.text = run_cli(calibrate, ["--pack", "Machine-Max_K17",
                                                 "--pack-root", str(PACK_ROOT)])
        cls.json_code, cls.json_text = run_cli(
            calibrate, ["--pack", "Machine-Max_K17", "--pack-root", str(PACK_ROOT), "--json"])
        cls.data = json.loads(cls.json_text)

    def test_exit_ok(self):
        self.assertEqual(self.code, 0)
        self.assertEqual(self.json_code, 0)

    def test_mass_and_geometry(self):
        vehicle = self.data["vehicle"]
        self.assertAlmostEqual(vehicle["mass"], 22000.0, places=1)
        self.assertAlmostEqual(vehicle["operating_mass"], 22060.0, places=1)
        self.assertAlmostEqual(vehicle["occupant_mass"], 60.0, places=1)
        self.assertAlmostEqual(vehicle["power_to_weight_kw_per_t"],
                               self.data["power_source"]["peak_power"] / vehicle["operating_mass"])
        self.assertEqual(vehicle["wheels"], 8)
        self.assertAlmostEqual(vehicle["radius"], 0.84375, places=5)
        # 面积必须来自模型包围盒，不能等于 JSON 里的 -1
        self.assertGreater(vehicle["area"]["z"], 5.0)

    def test_material(self):
        self.assertAlmostEqual(self.data["vehicle"]["c_rr"], 0.04, places=4)
        self.assertAlmostEqual(self.data["vehicle"]["mu"], 1.5 * 0.7, places=4)

    def test_engine(self):
        source = self.data["power_source"]
        self.assertEqual(source["kind"], "内燃机")
        self.assertAlmostEqual(source["peak_power"], 551521.0, delta=200.0)
        self.assertAlmostEqual(source["peak_rpm"], 2401.0, places=0)
        self.assertAlmostEqual(source["power_util"], 0.992, delta=0.01)
        # max_power 是硬上限，必须与曲线峰值一致（≤1%）
        self.assertLess(abs(source["max_power"] - source["peak_power"]) / source["max_power"], 0.01)

    def test_gears(self):
        gears_data = self.data["gearbox"]["gears"]
        self.assertEqual(len(gears_data), 7)
        self.assertAlmostEqual(gears_data[0]["ratio"], 51.81, places=2)
        self.assertAlmostEqual(gears_data[-1]["ratio"], 9.54, places=2)
        self.assertAlmostEqual(gears_data[0]["force"] / 1000, 157.20, delta=0.05)
        self.assertTrue(all(g["bound"] == "动力源" for g in gears_data))

    def test_top_speed(self):
        top = self.data["top_speed"]["top"]
        self.assertAlmostEqual(top["red_kmh"], 100.1, delta=0.2)
        self.assertAlmostEqual(top["kmh"], 97.7, delta=0.2)
        self.assertEqual(top["bound"], "功率")
        self.assertEqual(top["gear"], 7)

    def test_aero_defaults_to_unit_coefficient(self):
        # 军事包没有写 hydrodynamics，游戏按默认 [0.1, 1.0] 计，Cd·A 等于逐零件前向面积和
        vehicle = self.data["vehicle"]
        self.assertEqual(vehicle["aero_source"], "pack")
        self.assertAlmostEqual(vehicle["cd_area"], vehicle["area_sum"]["z"], places=2)

    def test_empty_vehicle_uses_empty_mass_in_physics(self):
        empty = run_json(calibrate, ["--pack", "Machine-Max_K17",
                                     "--pack-root", str(PACK_ROOT),
                                     "--occupant-mass", "0", "--json"])
        self.assertEqual(empty["vehicle"]["operating_mass"], empty["vehicle"]["mass"])
        self.assertGreater(empty["gearbox"]["gears"][0]["accel"],
                           self.data["gearbox"]["gears"][0]["accel"])
        # 质量只通过滚阻与加速影响极速，减重后极速略升
        self.assertGreater(empty["top_speed"]["top"]["kmh"],
                           self.data["top_speed"]["top"]["kmh"])
        self.assertIn("## 摘要", self.text)

    def test_wheel_torque_limit_caps_gear_force(self):
        registry = mmcal.Registry()
        registry.load_root(PACK_ROOT)
        vehicle = mmcal.resolve_vehicle(registry, registry.packs["machine_max:k17"], "k17")
        source = mmcal.Engine.from_entry(registry.need("subsystems", "machine_max:k17_engine", "test"))
        gearbox = mmcal.Gearbox.from_entry(
            registry.need("subsystems", "machine_max:k17_gearbox", "test"))
        material = mmcal.Material.from_entry(
            registry.need("materials", "machine_max:off_road_tire", "test"))
        drive = mmcal.drive_system(vehicle, registry)
        drive["ends"] = [mmcal.DriveEnd(str(i), 100.0, 0.0, 0.0, "test") for i in range(8)]
        sol = mmcal.solve(vehicle, source, gearbox, drive, material)
        self.assertEqual(sol.gears[0]["bound"], "轮端夹紧")
        self.assertAlmostEqual(sol.gears[0]["force"], 800.0 / sol.r)
        self.assertAlmostEqual(sol.gears[0]["accel"], sol.gears[0]["force"] / sol.mass)


class TransmissionTest(unittest.TestCase):
    """传动箱 power_outputs 的减速比必须进总传动比。"""

    @classmethod
    def setUpClass(cls):
        need_packs(cls)
        _, cls.mini_text = run_cli(calibrate, ["--pack", "Machine-Max_Civilian",
                                               "--template", "mini_ev",
                                               "--pack-root", str(PACK_ROOT)])
        cls.mini = run_json(calibrate, ["--pack", "Machine-Max_Civilian", "--template", "mini_ev",
                                        "--pack-root", str(PACK_ROOT), "--json"])
        cls.k17 = run_json(calibrate, ["--pack", "Machine-Max_K17",
                                       "--pack-root", str(PACK_ROOT), "--json"])

    def test_mini_ev_ratio(self):
        transmission = self.mini["transmission"]
        self.assertEqual(transmission["ratio"], 8.0)
        self.assertEqual(transmission["outputs"],
                         {"subsystem.machine_max.left_back_wheel_driver": 8.0,
                          "subsystem.machine_max.right_back_wheel_driver": 8.0})
        # 无变速箱时总传动比就是传动箱减速比
        self.assertEqual(self.mini["gearbox"]["gears"][0]["ratio"], 8.0)

    def test_mini_ev_top_speed(self):
        top = self.mini["top_speed"]["top"]
        self.assertAlmostEqual(top["kmh"], 80.8, delta=0.5)
        self.assertAlmostEqual(top["red_kmh"], 162.7, delta=0.5)
        self.assertEqual(top["bound"], "功率")

    def test_k17_ratio_is_one(self):
        self.assertEqual(self.k17["transmission"]["ratio"], 1.0)
        # 7 档 = 0.867 × final_ratio 11 × r_trans 1
        self.assertAlmostEqual(self.k17["gearbox"]["gears"][-1]["ratio"], 9.54, places=2)

    def test_missing_gearbox_keeps_transmission(self):
        """没有变速箱时挡位退化为 1.00，传动箱减速比照常生效。"""
        self.assertEqual(self.mini["gearbox"]["source"], "")
        self.assertGreater(self.mini["transmission"]["ratio"], 1.0)
        self.assertIn("传动箱减速比", self.mini_text)
        self.assertIn("| 变速箱 | 无 |", self.mini_text)


class MotorTest(unittest.TestCase):
    def setUp(self):
        self.motor = mmcal.Motor(max_power=20000.0, max_torque=85.0, red_line_rpm=10000.0,
                                 red_line_power_ratio=0.8, damping=[2.0, 0.05, 0.00005],
                                 source="test")

    def test_base_speed(self):
        base_rad = 20000.0 / 85.0
        self.assertAlmostEqual(self.motor.base_rpm, base_rad * 30 / math.pi, places=3)

    def test_curve_shape(self):
        # 恒扭矩区、基速处满功率、红线处按比例衰减
        self.assertAlmostEqual(self.motor.torque(100.0), 85.0, places=6)
        self.assertAlmostEqual(self.motor.curve_power(self.motor.base_rpm), 20000.0, delta=1.0)
        self.assertAlmostEqual(self.motor.curve_power(10000.0), 16000.0, delta=1.0)

    def test_no_internal_damping_below_1909(self):
        self.assertEqual(self.motor.damping_torque(5000.0), 0.0)

    def test_raw_torque_defaults_to_torque(self):
        self.assertAlmostEqual(self.motor.raw_torque(5000.0), self.motor.torque(5000.0))


class CurveTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        need_packs(cls)
        cls.k17 = run_json(curve, ["--pack", "Machine-Max_K17",
                                   "--pack-root", str(PACK_ROOT), "--json"])
        cls.ev = run_json(curve, ["--pack", "Machine-Max_Civilian",
                                  "--subsystem", "machine_max:mini_ev_motor",
                                  "--pack-root", str(PACK_ROOT), "--json"])

    def test_engine_shape(self):
        self.assertAlmostEqual(self.k17["curve_peak"], 551520.9, delta=200.0)
        self.assertAlmostEqual(self.k17["torque_peak"], 2560.0, places=3)
        self.assertAlmostEqual(self.k17["torque_peak_rpm"], 2000.0, places=0)
        self.assertIsNone(self.k17["cap_onset_rpm"])

    def test_max_power_deviation(self):
        # max_power 与曲线峰值必须对上，硬上限才真正生效
        self.assertLess(abs(self.k17["max_power_deviation"]), 0.01)

    def test_power_band_covers_upper_rpm(self):
        band = self.k17["power_band"]
        self.assertAlmostEqual(band["high"], 3000.0, delta=1.0)
        self.assertGreater(band["high"] - band["low"], 1000.0)

    def test_motor_by_subsystem(self):
        self.assertEqual(self.ev["kind"], "电机")
        self.assertAlmostEqual(self.ev["max_power"], 20000.0, places=3)
        self.assertAlmostEqual(self.ev["torque_peak_rpm"], self.ev["curve_peak_rpm"] - 1, delta=2)
        self.assertGreater(self.ev["power_band"]["high"] - self.ev["power_band"]["low"], 3000.0)

    def test_csv_has_one_row_per_step(self):
        code, text = run_cli(curve, ["--pack", "Machine-Max_K17", "--csv", "--step", "500",
                                     "--pack-root", str(PACK_ROOT)])
        self.assertEqual(code, 0)
        lines = text.strip().splitlines()
        self.assertEqual(len(lines), 7)          # 表头 + 500…3000
        self.assertTrue(lines[1].startswith("500.0,"))


class DragTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        need_packs(cls)
        cls.data = run_json(drag, ["--pack", "Machine-Max_K17", "--speeds", "40,93",
                                   "--pack-root", str(PACK_ROOT), "--json"])

    def test_rolling(self):
        self.assertAlmostEqual(self.data["c_rr"], 0.04, places=4)
        self.assertAlmostEqual(self.data["operating_mass"], 22060.0)
        self.assertAlmostEqual(self.data["rolling_force"],
                               self.data["c_rr"] * self.data["operating_mass"] * mmcal.G)

    def test_areas(self):
        # 未声明气动点时按默认系数 1.0，Cd·A 等于逐零件前向面积和
        self.assertAlmostEqual(self.data["front_area_sum"], 21.94, delta=0.2)
        self.assertAlmostEqual(self.data["cd_area"], 21.94, delta=0.3)

    def test_parts_sum_to_total(self):
        self.assertAlmostEqual(sum(p["mass"] for p in self.data["parts"]),
                               self.data["mass"], places=1)
        self.assertAlmostEqual(sum(p["area"] for p in self.data["parts"]),
                               self.data["front_area_sum"], places=2)
        self.assertAlmostEqual(sum(p["cd_area"] for p in self.data["parts"]),
                               self.data["cd_area"], places=2)

    def test_hull_is_largest(self):
        self.assertEqual(self.data["parts"][0]["name"].split("/")[0], "k17_hull")

    def test_speeds(self):
        rows = self.data["speeds"]
        self.assertEqual([r["kmh"] for r in rows], [40.0, 93.0])
        self.assertAlmostEqual(rows[1]["total"], rows[1]["aero"] + rows[1]["rolling"], places=6)
        self.assertGreater(rows[1]["wheel_power"], rows[0]["wheel_power"])


class GearsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        need_packs(cls)
        cls.k17 = run_json(gears, ["--pack", "Machine-Max_K17",
                                   "--pack-root", str(PACK_ROOT), "--json"])
        cls.mini = run_json(gears, ["--pack", "Machine-Max_Civilian", "--template", "mini_ev",
                                    "--pack-root", str(PACK_ROOT), "--json"])

    def test_k17_chain(self):
        self.assertEqual(self.k17["transmission"]["ratio"], 1.0)
        self.assertEqual(len(self.k17["gears"]), 7)
        self.assertAlmostEqual(self.k17["gears"][-1]["ratio"], 9.54, places=2)
        self.assertAlmostEqual(self.k17["gears"][-1]["red_kmh"], 100.1, delta=0.2)

    def test_k17_steps(self):
        self.assertAlmostEqual(self.k17["gears"][0]["step"], 51.81 / 34.54, places=3)
        self.assertIsNone(self.k17["gears"][-1]["step"])

    def test_mini_ev_single_gear(self):
        self.assertEqual(self.mini["gearbox"]["source"], "")
        self.assertEqual(self.mini["transmission"]["ratio"], 8.0)
        self.assertEqual(len(self.mini["gears"]), 1)
        self.assertAlmostEqual(self.mini["gears"][0]["red_kmh"], 162.7, delta=0.5)

    def test_without_power_source(self):
        """动力源还没配时，传动比链照样能给出来，只是没有红线车速。"""
        original = mmcal.vehicle.Vehicle.power_source_ref
        mmcal.vehicle.Vehicle.power_source_ref = lambda self: (None, None, False)
        try:
            data = run_json(gears, ["--pack", "Machine-Max_K17",
                                    "--pack-root", str(PACK_ROOT), "--json"])
        finally:
            mmcal.vehicle.Vehicle.power_source_ref = original
        self.assertEqual(len(data["gears"]), 7)
        self.assertIsNone(data["gears"][0]["red_kmh"])
        self.assertAlmostEqual(data["gears"][0]["ratio"], 51.81, places=2)


class TrackTest(unittest.TestCase):
    """履带驱动：驱动端是主动轮，半径取主动轮碰撞球而不是最大的负重轮。"""

    @classmethod
    def setUpClass(cls):
        need_packs(cls)
        cls.two = run_json(calibrate, ["--pack", "Machine-Max_2S38",
                                       "--pack-root", str(PACK_ROOT), "--json"])
        cls.mtlb = run_json(calibrate, ["--pack", "Machine-Max_MTLB", "--template", "mtlb",
                                        "--pack-root", str(PACK_ROOT), "--json"])
        cls.pkt = run_json(calibrate, ["--pack", "Machine-Max_MTLB", "--template", "mtlb_pkt",
                                       "--pack-root", str(PACK_ROOT), "--json"])

    def test_drive_is_track(self):
        vehicle = self.two["vehicle"]
        self.assertEqual(vehicle["drive_kind"], "track")
        self.assertAlmostEqual(vehicle["mass"], 21500.0, places=1)
        # 主动轮 0.38378 m；车上最大的轮面是负重轮 0.34891 m，两者不能混
        self.assertAlmostEqual(vehicle["radius"], 0.38378, places=5)
        self.assertAlmostEqual(self.two["wheel"]["max_drive_torque"], 28000.0, places=1)

    def test_drive_system_skips_roadwheels(self):
        registry = mmcal.Registry()
        registry.load_root(PACK_ROOT)
        vehicle = mmcal.resolve_vehicle(registry, registry.packs["machine_max:mtlb"], "mtlb")
        drive = mmcal.drive_system(vehicle, registry)
        self.assertEqual(drive["kind"], "track")
        self.assertEqual(len(drive["ends"]), 2)
        self.assertEqual(drive["total"], 2)          # 两条履带都驱动，附着按全车重量
        self.assertAlmostEqual(drive["radius"], 0.33094, places=5)

    def test_2s38_top_speed_is_red_line_bound(self):
        top = self.two["top_speed"]["top"]
        self.assertAlmostEqual(top["red_kmh"], 70.1, delta=0.2)
        self.assertAlmostEqual(top["kmh"], 70.1, delta=0.2)
        self.assertEqual(top["bound"], "红线")
        self.assertEqual(top["gear"], 12)

    def test_2s38_engine_matches_rated_power(self):
        source = self.two["power_source"]
        self.assertAlmostEqual(source["peak_power"], 368000.0, delta=200.0)
        self.assertAlmostEqual(source["peak_rpm"], 2600.0, places=0)
        self.assertLess(abs(source["max_power"] - source["peak_power"]) / source["max_power"], 0.01)

    def test_contact_material_is_track_link(self):
        """滚阻与摩擦读履带配件的 `track_steel`，不是轮面碰撞箱的 `off_road_tire`。"""
        for data in (self.two, self.mtlb):
            self.assertAlmostEqual(data["vehicle"]["c_rr"], 0.05, places=4)
            self.assertAlmostEqual(data["vehicle"]["mu"], 0.9 * 0.7, places=4)

    def test_mtlb_mass_and_top_speed(self):
        self.assertAlmostEqual(self.mtlb["vehicle"]["mass"], 11920.0, places=1)
        top = self.mtlb["top_speed"]["top"]
        self.assertAlmostEqual(top["red_kmh"], 61.5, delta=0.2)
        self.assertEqual(top["bound"], "红线")
        # 6 档能到红线：牵引力在低转速拉不动，零点有两个，必须取高的那个
        self.assertEqual(top["gear"], 6)
        self.assertAlmostEqual(top["rpm"], 2100.0, delta=1.0)

    def test_pkt_keeps_chassis_calibration(self):
        """加装机枪塔只改质量与气动，动力链与极速不变。"""
        self.assertAlmostEqual(self.pkt["vehicle"]["mass"] - self.mtlb["vehicle"]["mass"], 102.0,
                               places=1)
        self.assertEqual([g["ratio"] for g in self.pkt["gearbox"]["gears"]],
                         [g["ratio"] for g in self.mtlb["gearbox"]["gears"]])
        self.assertEqual([g["bound"] for g in self.pkt["gearbox"]["gears"]],
                         [g["bound"] for g in self.mtlb["gearbox"]["gears"]])
        self.assertAlmostEqual(self.pkt["top_speed"]["top"]["kmh"],
                               self.mtlb["top_speed"]["top"]["kmh"], delta=0.1)
        self.assertGreater(self.pkt["vehicle"]["cd_area"], self.mtlb["vehicle"]["cd_area"])

    def test_track_speed_cap_binds_before_red_line(self):
        """履带主动轮的角速度上限是一条独立的极速上限。"""
        registry = mmcal.Registry()
        registry.load_root(PACK_ROOT)
        vehicle = mmcal.resolve_vehicle(registry, registry.packs["machine_max:mtlb"], "mtlb")
        source = mmcal.Engine.from_entry(
            registry.need("subsystems", "machine_max:mtlb_engine", "test"))
        gearbox = mmcal.Gearbox.from_entry(
            registry.need("subsystems", "machine_max:mtlb_gearbox", "test"))
        material = mmcal.Material.from_entry(
            registry.need("materials", "machine_max:off_road_tire", "test"))
        drive = mmcal.drive_system(vehicle, registry)
        for end in drive["ends"]:
            end.max_speed = 20.0
        sol = mmcal.solve(vehicle, source, gearbox, drive, material)
        self.assertEqual(sol.top["bound"], "履带转速")
        self.assertAlmostEqual(sol.top["kmh"], 20.0 * sol.r * mmcal.KMH, delta=0.05)

    def test_gears_accepts_track_vehicle(self):
        data = run_json(gears, ["--pack", "Machine-Max_MTLB", "--template", "mtlb",
                                "--pack-root", str(PACK_ROOT), "--json"])
        self.assertEqual(data["drive_kind"], "track")
        self.assertEqual(len(data["gears"]), 6)
        self.assertAlmostEqual(data["gears"][-1]["ratio"], 4.26, places=2)
        self.assertAlmostEqual(data["gears"][-1]["red_kmh"], 61.5, delta=0.2)


class RequirementTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        need_packs(cls)
        cls.code, cls.text = run_cli(
            calibrate, ["--pack", "Machine-Max_K17", "--pack-root", str(PACK_ROOT),
                        "--top-speed", "60", "--power", "500 PS",
                        "--accel", "50:3.0", "--grade", "33", "--gears", "7"])
        cls.strict_code, _ = run_cli(
            calibrate, ["--pack", "Machine-Max_K17", "--pack-root", str(PACK_ROOT),
                        "--accel", "50:3.0", "--strict"])

    def test_rows_present(self):
        for name in ("极速处功率", "0→50 km/h 3s 所需功率", "33° 爬坡牵引力", "档数"):
            self.assertIn(name, self.text)

    def test_infeasible_accel(self):
        rows = [line for line in self.text.splitlines() if line.startswith("| 0→50 km/h 3s")]
        self.assertEqual(len(rows), 1)
        self.assertIn("不通过", rows[0])

    def test_feasible_power(self):
        rows = [line for line in self.text.splitlines() if line.startswith("| 极速处功率")]
        self.assertIn("通过", rows[0])

    def test_strict_exit(self):
        self.assertEqual(self.strict_code, 1)
        self.assertEqual(self.code, 0)

    def test_partial_drive_grip_uses_driven_wheel_load(self):
        data = run_json(calibrate, ["--pack", "Machine-Max_Civilian",
                                    "--template", "mini_ev", "--pack-root", str(PACK_ROOT),
                                    "--grade", "35", "--json"])
        grip = next(row for row in data["residuals"] if row["name"] == "35° 坡面附着")
        self.assertAlmostEqual(grip["avail"],
                               data["vehicle"]["mu"] * data["vehicle"]["operating_mass"]
                               * mmcal.G * math.cos(math.radians(35)) * 2 / 4)
        self.assertFalse(grip["ok"])


class CliTest(unittest.TestCase):
    def test_list(self):
        code, text = run_cli(calibrate, ["--pack-root", str(PACK_ROOT), "--list"])
        self.assertEqual(code, 0)
        self.assertIn("machine_max:k17", text)

    def test_unknown_pack(self):
        with self.assertRaises(SystemExit):
            run_cli(calibrate, ["--pack", "no-such-pack", "--pack-root", str(PACK_ROOT)])

    def test_every_entry_point_lists(self):
        for module in (calibrate, curve, drag, gears):
            code, text = run_cli(module, ["--pack-root", str(PACK_ROOT), "--list"])
            self.assertEqual(code, 0)
            self.assertIn("模板：", text)


if __name__ == "__main__":
    unittest.main(verbosity=2)
