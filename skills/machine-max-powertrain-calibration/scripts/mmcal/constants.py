"""物理常量与 JSON 缺省值。

数值全部取自模组源码，与 SKILL.md 的单位约定一致。
"""

G = 9.81          # 重力加速度 m/s²
RHO = 1.29        # 空气密度 kg/m³
CD = 1.0          # 气动系数，按包围盒估面积时的保守取值
BLOCK_FRICTION = 0.7     # 地面方块默认 friction
BLOCK_ROLLING = 1.0      # 地面方块默认 rolling_friction
PS = 735.49875    # 公制马力 W
KMH = 3.6

DEFAULT_ENGINE = dict(idle_rpm=500.0, idle_rpm_torque_ratio=1 / 3, max_torque_rpm=5200.0,
                      red_line_rpm=7500.0, red_line_torque_ratio=0.9,
                      damping_factors=[20.0, 0.1, 0.00005])
DEFAULT_GEARBOX = dict(final_ratio=10.0, ratios=[-3.5, 3.5, 2.5, 1.7, 1.4, 1.1], switch_time=0.3)
DEFAULT_WHEEL = dict(max_drive_force=10000.0, max_brake_force=3500.0, max_hand_brake_force=0.0)
DEFAULT_TRACK = dict(max_drive_torque=30000.0, max_brake_torque=20000.0,
                     max_hand_brake_torque=20000.0, max_speed=120.0)
