# 工程模型

本文集中放置动力标定的公式、单位与常量；主要实现入口列在 §八。文中的数字默认为字段的生效值，不是代码里的兜底默认值。

## 单位速查

| 参数 | 所在定义 | 单位 | 标定含义 |
|---|---|---|---|
| `max_power` | 引擎 / 电机 | W | 引擎的功率硬上限；电机的峰值功率 |
| `max_torque` | 引擎 / 电机 | N·m | 峰值扭矩 |
| `idle_rpm` / `max_torque_rpm` / `red_line_rpm` | 引擎 | rpm | 曲线转折点 |
| `idle_rpm_torque_ratio` / `red_line_torque_ratio` | 引擎 | 无量纲 | 两端扭矩相对峰值的比例 |
| `red_line_rpm` / `red_line_power_ratio` | 电机 | rpm / 无量纲 | 红线与红线处功率占比 |
| `damping_factors` | 引擎 / 电机 | N·m/(rad/s)ⁿ | 常数、一次、二次内阻系数 |
| `gear_ratios[i]` / `final_ratio` | 变速箱 | 无量纲 | 档位比与终传比 |
| `switch_time` | 变速箱 | s | 换挡断流时长 |
| `power_outputs` | 传动箱的部件 JSON | 无量纲 | 各输出端固定减速比 |
| `max_drive_force` / `max_brake_force` / `max_hand_brake_force` | 车轮 | N·m | 轮端力矩上限，牵引力须再除轮半径 |
| `max_drive_torque` / `max_brake_torque` / `max_hand_brake_torque` | 履带驱动 | N·m | 主动轮端力矩上限，语义与车轮那组相同 |
| `max_speed` | 履带驱动 | rad/s | 主动轮角速度上限，折成车速要多乘主动轮半径 |
| `abs_wheel_radius` | 车轮 | m | 只用于滑移率估算，不用于牵引力换算 |
| `rolling_friction` | 轮胎材质 | 无量纲 | 滚阻系数 `C_rr` |
| `friction` | 材质 | 无量纲三元组 | 各轴摩擦系数 |
| `projected_area` | 部件 | m² | 非正值触发碰撞形状 AABB 自动计算 |

## 一、驱动力链代数

动力从发动机到接地点经过四级换算，每一级只改变扭矩与转速的分配，不改变功率。

```
ω_gb = ω_e / i_total
τ_in = τ_e · i_total
τ_w  = τ_in / N              （开放差速，N = 驱动轮数）
F    = τ_w / r_wheel
```

`i_total` 是挡位传动比、终传比与传动箱减速比三段的乘积：

```
i_total = gear_ratios[i] × final_ratio × power_outputs[该轮]
```

`power_outputs` 是传动箱子系统自带的一张「输出端 → 固定减速比」表（`TransmissionSubsystemStaticAttr` 之外的动态字段，写在部件 JSON 的 `subsystem_data` 里）。`GearboxSubsystem.distributePower` 把转速除以挡位传动比，`TransmissionSubsystem.distributeOpenDiff` 再除以本端 `power_outputs`，扭矩两次按同一比值放大。单速车（没有变速箱）只有这一段，`gear_ratios[i]` 与 `final_ratio` 都为 1；没有传动箱时 `power_outputs` 那段为 1。

代入得单轮牵引力：

```
F = τ_e · i_total / (N · r_wheel)
```

整车牵引力 `F_total = N · F`，加速度 `a = F_total / M`。

三个独立上限：

```
F_engine = τ_e(n) · i_total / (N · r)
F_motor  = max_drive_force / r          // max_drive_force 单位是 N·m
F_grip   = μ · N_wheel                  // 单轮法向载荷
```

`F_total` 取三者中的最小值。某一档的绑定约束是哪一项，决定了该调哪个参数。

> 传动在锁止分支下按输入转速换算扭矩，开放分支按传动比直接分发；两支共用 `τ_in / N` 作为基准，轮速不同的轮子不会互相偷扭矩。

履带车把 `wheel_driver` 换成 `track_drive`，左右两条各是一个驱动端：`N` 取驱动端数（通常是 2），`r_wheel` 换成主动轮半径，`max_drive_force` 换成 `max_drive_torque`。附着项不再按占比折算——两条履带都是驱动的，法向载荷摊在整条履带接地上，`F_grip = μ·M·g`。

## 二、轮端执行

车轮子系统从动力链收到的是功率与目标转速，反推成轮端力矩：

```java
// WheelDriverSubsystem.onPrePhysicsTick
torque = totalPower / Math.max(Math.abs(speed), MIN_SPEED_FOR_TORQUE);  // MIN_SPEED_FOR_TORQUE = 0.25 rad/s
torque = Math.clamp(torque, -MAX_DRIVE_FORCE, MAX_DRIVE_FORCE);
torque -= brakeTorque + handBrakeTorque;
rollingMotor.set(MotorParam.MaxMotorForce, Math.abs(torque));
rollingMotor.set(MotorParam.TargetVelocity, torque > 0 ? -clamp(speed, ±MAX_SPEED) : 0);
```

- `MIN_SPEED_FOR_TORQUE = 0.25 rad/s` 是除法保护，决定了停车瞬间的扭矩上限；它同时是一个标定量，不能当实现细节。
- 刹车不带宽矩上限之外的夹紧：制动扭矩在驱动夹紧之后相减，因此 `max_brake_force` 可以大于 `max_drive_force`。
- 制动与驱动的伺服目标相反：驱动跟随动力链下发的转速，制动把目标转速压到 0。

履带驱动走同一套算式，只是作用在主动轮的滚动轴上（`TrackDriveSubsystem.onPrePhysicsTick`）：

```java
torque = Σ TrackMath.torqueFromPower(power, speed, 0.25f);   // 多路功率汇合
torque = clamp(torque, ±MAX_DRIVE_TORQUE) * clutchEngagement;
targetOmega = control.targetSpeed() / radius();               // radius = 被驱动子零件的碰撞球半径
TrackMath.singleFlowMotor(clamp(omega, ±MAX_SPEED), torque, ...);
```

因此 `max_speed` 是主动轮的角速度上限，折成车速要乘主动轮半径。它与红线车速是两条独立的上限，谁先到谁绑定极速；脚本把这一条单独标为「履带转速」绑定。

## 三、行驶阻力

### 3.1 气动

每个子部件在未声明 `hydrodynamics` 时，自动获得一条以空串为键的默认配置：

```java
// SubPartAttr.java:117
HydrodynamicAttr.MAP_CODEC.optionalFieldOf("hydrodynamics",
        Map.of("", HydrodynamicAttr.DEFAULT))
```

`HydrodynamicAttr.DEFAULT`（`HydrodynamicAttr.java:94`）：六个方向均为 `[0.1, 1.0]`，`scale = 1`，`effective_range = 1`，`transonic_amplifier = 5`，`advanced = false`。系数向量第一位是一阶（粘性）项，第二位是二阶（压差）项：

```
F₁ = μ_air · c₀ · |v| · A_wet
F₂ = 0.5 · ρ · c₁ · |v|² · A_axis
```

`ρ = 1.29 kg/m³`、`μ_air = 1.8e-5 Pa·s` 在 `SubPart` 中硬编码。空气的一阶项比二阶项小六个数量级，实际只用二阶项。

面积（`AbstractPart.java:728`）：`projected_area` 的任一分量为 `≤0` 时，按碰撞形状包围盒计算：

```
A_x = 4 · extentY · extentZ
A_y = 4 · extentX · extentZ
A_z = 4 · extentX · extentY
```

即包围盒的三个投影面积。`A_wet = (A_x + A_y + A_z) · 2`，只进一阶项。

遮挡：从气动点沿来流方向做 `effective_range` 米射线检测，命中同装配体且 `hydroPriority ≥` 自身的子部件时，`occlusion = hitFraction`，力乘以该系数。默认射程 1 m，处理贴合部件之间的遮蔽。

力作用在气动点（默认配置的点为刚体原点），因此会产生俯仰与偏航力矩，不只是纯阻力。

### 3.2 滚动

接触材质的 `physics.rolling_friction` 是无量纲滚阻系数 `C_rr`，对应轮胎工程的常规定义：

```
F_roll = C_rr · N
τ_roll = F_roll · r
```

`N` 是接触点的法向载荷，`r` 是轮面半径。滚阻与法向载荷成正比，与速度、物理步长都无关。

只有轮面（`type: "wheel"` 的球形接触面）产生滚阻。非轮面接触不使用该值，车体蹭地由 `friction` 的滑动摩擦负责。这条对履带同样成立：主动轮、负重轮、诱导轮都是 `type: "wheel"` 的轮面，**它们有没有轮驱子系统与滚阻无关**，每个接地轮各自按本点法向冲量记一份 `C_rr·N_i`。

履带成员的材质不是轮面碰撞箱上写的那个。`CollisionHandler.setupFriction` 先问 `subPart.isTrackContact()`：履带存活且命中的是本零件的轮面时，整套物理参数换成**履带配件**（`track_type` → `fittings/*.json` 的 `material`）的，滚阻、摩擦、恢复系数一起换（`SubPart.contactMaterial`）。所以履带车的 `C_rr` 与 `μ` 要读那条链，读 `off_road_tire` 会差一倍。

地面方块的 `rolling_friction`（默认 `1.0`）是乘数：泥地填 `3` 表示该处滚阻是硬地的三倍。整车滑行减速度为

```
a = C_rr · block_rolling · g
```

与质量无关：载荷按轮数分摊，八轮载具每轮承担 `M·g/8`，合起来仍是 `C_rr·M·g`。履带同理：接地轮多少个、半径多大都不改合力，只影响每点各自的角冲量上限。

量级参考：滚动轮胎 0.010~0.015，越野胎 0.03~0.05，钢制履带 0.03~0.08（铺装/硬地到软土）。履带这一项是**整条履带总成**的系数，含履带板销轴摩擦、主动轮与诱导轮损失，不只是钢对地面的滚阻，所以明显高于轮胎。地形没有逐块配倍率时这个值就是全地形的取值，取中间偏硬（`track_steel` 用 0.04）意味着默认地面按硬地算。

#### 3.2.1 轮面半径来自模型

`type: "wheel"` 的碰撞箱会生成两个子形状（`SubPartAttr.java:247`）：

- **球**，半径取模型 cube 的 y 向尺寸的一半，登记为轮面，地形接触与滚阻力臂都由它承担；
- 外壳**圆柱**，轴沿子部件局部 X，只用于一般命中判定。

球的低点低于圆柱，所以落地先碰的是球。滚动的力臂就是这个半径 `r = cube.size.y / 2`，与 JSON 里的任何半径字段都无关，只能从模型查——标定用的是它。

### 3.3 内阻

引擎与电机用多项式声明各阶内阻，系数依次为常数项、一次项、二次项，单位 `N·m/(rad/s)ⁿ`：

```
τ_res = c₀ + c₁·ω + c₂·ω²
```

内阻从曲轴扭矩中扣除，因此也降低可用功率。二次项在红线上才显著。

## 四、附着

地形接触的合成摩擦由 `CollisionHandler` 逐点覆写，不由 Bullet 自动合成：

```
μ_combined = body.friction · μ_eff · blockFriction · wetFactor
μ_eff      = 椭圆合成( μ_front(slipRatio), μ_side(slipAngle) )
```

- `body.friction` 固定 `1.0`（`SubPart.java:148`）。
- `μ_front` 取材质 `friction` 三元组的 **y** 分量，`μ_side` 取 **x** 分量；越野胎为 `1.5` 与 `1.2`。
- 两者各自乘以滑移曲线的 base / peak / kinetic 缩放，曲线由材质 `slip_curve` 给出。
- `blockFriction` 取接触处方块物理数据的 `friction`，默认 `0.7`。
- `wetFactor` 由方块湿滑系数与材质 `slip_adaptation` 决定。

抓地因此不是常数：纵向峰值出现在 `peak_slip_ratio` 附近，越过峰值后按 `kinetic_scale` 下降；双轴同时滑移时由摩擦椭圆折减。

材质 `friction` 在两条互斥的路径上生效：轮胎（`type: "wheel"` 的轮面）不走刚体的各向异性摩擦（该处被置为 `none`），系数只经上式合成摩擦与摩擦椭圆计入一次；非轮面部件与履带轮则把材质摩擦写进刚体的各向异性缩放（刚体自身 `friction` 固定 `1.0`），方向性由该向量承担。静态分析按单一系数取值（越野胎 `1.5 × 0.7 = 1.05`），最终以实测为准。

## 五、极速

极速由两条件共同决定，取较小者。

红线条件（纯几何）：

```
v_red = ω_red · r / i_top
```

功率平衡条件（解三次方程）：

```
( F_aero(v) + F_roll + τ_res(ω_e)·i_top/r ) · v = P_wheel
```

气动项用整车 `Cd·A`：各子零件按自己的前向二阶系数与前向投影面积求积再求和，遮挡不复现，因此该值给的是阻力上界。同一台车的 `Cd·A` 主要由大块车壳决定，改一个零件的 `front_drag` 系数就能整体挪动极速。

功率平衡可能有**两个**零点：牵引力随转速先升后降，阻力随车速单调升。低转速端引擎扭矩不够，高档位在那里根本拉不动，`F−R` 先负后正再负。极速要的是最高的那个零点，二分区间必须从红线一侧往下夹；从 0 起二分会在低转速那个假零点上收住，把最高档判成开不动、退回低一档的红线车速。脚本按 200 点扫一遍取最高的可行采样点，再在它与上一个采样点之间二分。

## 六、扭矩曲线

引擎的扭矩曲线由三个转速、两个比例与一个峰值扭矩确定，全部是 JSON 字段（`max_torque`、`idle_rpm`、`idle_rpm_torque_ratio`、`max_torque_rpm`、`red_line_rpm`、`red_line_torque_ratio`）：

```
n ≤ n_idle        τ = (n / n_idle) · τ_max · r_idle
n_idle < n ≤ n_τ  τ = lerp(τ_max·r_idle → τ_max)
n_τ < n ≤ n_red   τ = lerp(τ_max → τ_max·r_red)
n > n_red         τ = 5^(−10(n−n_red)/n_red) · min(τ_max·r_red, P_max/ω)
```

`getTorqueAtSpeed`（`EngineSubsystem.java:245`）对每一段都再执行一次 `τ ← min(τ, P_max/ω)`，随后乘以耐久折减 `0.3 + 0.7·√(hp/hp_max)`。满耐久标定时后者为 1。

**未被 `max_power` 夹紧的功率峰值位置。** 令 `a = n_τ`、`b = n_red`、`r = r_red`，折线功率在 `[a, b]` 上是 `n·(c − k·n)` 型的二次式，极值在

```
n_peak = clamp( (b − r·a) / (2(1 − r)),  a, b )
```

`r·b ≥ a` 时该式大于 `b`，峰值就在红线上；扭矩掉得快时峰值落在 `n_τ` 或区间内部。K17（`a = 2000`、`b = 3000`、`r = 0.8`）算出 3500，夹到 3000，与卡片里的「301.6 kW @ 3000 rpm」一致。

这给出折线本身的峰值；`max_power` 若在较低转速提前夹紧，限幅后的功率可能形成平台。标定时检查两项：

- 「峰值额定功率 @ `n_p`」应对照限幅后的完整曲线，而不能只看未限幅折线的极值。
- `max_power` 应与曲线可达的峰值功率一致；偏差超过 1% 就是参数错误。取小了会削掉峰值附近的一段，实际功率峰被压到 `P_max`；取大了只影响显示。

### 6.2 电机

`getTorqueAtSpeed`（`MotorSubsystem.java:260`）只有恒扭矩与恒功率两段，没有 `max_power` 那层额外夹紧：

```
ω_base = P_max / τ_max
ω ≤ ω_base          τ = τ_max
ω_base < ω ≤ ω_red  P = P_max·(1 − k·(1 − r_p))，k = (ω − ω_base)/(ω_red − ω_base)，τ = P/ω
ω > ω_red           τ = P_max·r_p/ω · 0.5^((ω − ω_red)/(0.1·ω_red))
```

- 基速换算成 rpm 是 `P_max/τ_max · 30/π`。恒扭矩区越宽，起步越猛；功率越大、扭矩越小，基速越高。
- `r_p = 1` 是理想恒功率，最高档与次高档的牵引力接近，换挡没有收益；`r_p` 越小，高转掉得越快。
- 内阻阈值在代码里写成 `0.5 × 400 rpm ÷ π × 30`（≈1909 rad/s），实际转速够不到，**按 0 计**。
- 没有变速箱时挡位传动比按 1.0，但传动箱减速比照常计入；把 `power_outputs` 也当成 1 会让单速电车的极速掉到真值的三分之一以下。

## 七、约束求解

把需求写成方程再解，比对着参数猜要快得多。整车侧固定的量是运行质量 `M`（部件 `mass` 之和加乘员质量；单人默认加 60 kg，空车为 0）、轮半径 `r`（模型 `type: "wheel"` 的 cube 在 y 向尺寸的一半，见 §3.2.1）、驱动轮数 `N`、传动箱减速比 `r_trans`、前向阻力面积 `Cd·A`、滚阻 `C_rr`、附着系数 `μ`；要求解的是 `i_top`、`i_1`、档数 `n_g`、`τ_max`、`n_τ`、`n_red`、`P_max`、`max_drive_force`。

单人质量默认值的运行期依据：座位在 `src/main/java/io/github/sweetzonzi/machine_max/common/mech/subsystem/SeatSubsystem.java#setPassenger` 读取 `src/main/java/io/github/sweetzonzi/machine_max/util/mechanic/MassUtil.java#getEntityMass` 的乘员质量，再经 `src/main/java/io/github/sweetzonzi/machine_max/common/mech/vehicle/SubPart.java#onMassChange` 加到刚体。该质量由实体包围盒体积乘 92.6 kg/m³ 得出；普通玩家站姿约为 `0.6 × 1.8 × 0.6 × 92.6 ≈ 60 kg`。其他实体体型、姿态或子系统额外质量需要按实际情况调整。

| # | 约束 | 方程 | 解出或校验 |
|---|---|---|---|
| C1 | 目标极速（设计时给定） | `i_top = ω_red·r / (1.03·v*)` | 解出建议 `i_top`；纯诊断不填 |
| C2 | 功率下界 | `P_max ≥ F_res(v*)·v* / 0.85` | 校验给定功率，或解出 `P_max` |
| C3 | 曲线峰值 @ `n_p` | `τ_max ≥ P_max/ω_p`，且 `n_p ≥ n_τ` | 校验给定扭矩/转速 |
| C4 | 一档 / 爬坡 | `i_1 = M·g·(sinθ + C_rr·cosθ)·r / τ_max` | 解出 `i_1` |
| C5 | 坡面附着 | `M·g·(sinθ + C_rr·cosθ) ≤ μ·M·g·cosθ·N_d/N_total`（各轮均分载荷） | 校验驱动轮能否提供所需牵引力 |
| C6 | 加速时间 | `P_max ≥ M·v² / (2·0.85·t)` | 校验给定时间 |
| C7 | 档数 | `n_g ≤ 1 + ln(i_1/i_top) / ln(1.176)` | 校验相邻档总传动比差，实际换挡收益另查曲线 |
| C8 | 轮端夹紧 | `max_drive_force ≥ τ_max·i_1 / N` | 解出 `max_drive_force` |
| C9 | 制动 | `max_brake_force ≥ M·a_b·r / N`，`a_b ≤ μ·g` | 解出或校验 |

`i_top` 与 `i_1` 都是「挡位传动比 × `final_ratio` × `power_outputs`」的乘积。传动箱减速比由子系统固定，缺口落在档位传动比与 `final_ratio` 上：给定额定功率的载具改不到极速时，先看是不是这两个值没有随传动箱一起调。

`F_res(v) = ½·ρ·C_d·A·v² + C_rr·M·g + τ_res(ω_e)·i_top/r`，最后一项在极速处才会进入百分位量级。

**残差表**逐行算 `可用 − 需求`，在报告中列出；需要长期基线时可随卡片保存：

| 约束 | 上限/下界 | 实际 | 残差 |
|---|---|---|---|
| C2 功率 | | | |
| C5 坡面附着 | | | |
| C7 档数 | | | |

任一行残差为负即不可行。**不可行时按下面的顺序处理，不要直接改数字凑合**：

1. 复核换算：马力↔千瓦、km/h↔m/s、轮半径是否取自模型、面积是否按包围盒。
2. 复核模型侧：`A` 用包围盒会偏大，`C_rr` 与 `μ` 是否取了本车型的量级。
3. 放松软约束：加速、爬坡、档数，按对玩家观感影响从小到大。
4. 作者明确要求的硬约束（如极速、实车功率）不放松，除非作者同意调整目标。

**不可行的三种典型形态**：

- **功率追不上极速**：C2 残差为负。真实功率配真实极速却不可行时，先核对 `Cd·A`（气动点系数与投影面积），而不是改功率。
- **坡面附着不足**：爬坡目标所需牵引力超过驱动轮附着上限时，C5 残差为负；继续增大一档传动比只会增加打滑风险。
- **档数与跨度不匹配**：跨度较小而给定档数过多时，C7 残差为负，级比过密。跨度很大而档数较少则可能换挡后掉出功率带，需要另查换挡落点；这不由 C7 判定。

## 八、代码位置

| 内容 | 位置 |
|---|---|
| 气动施加、遮挡检测 | `common/mech/vehicle/SubPart.java` `prePhysicsTick` |
| 气动公式 | `util/mechanic/DynamicUtil.java` `aeroDynamicForce` |
| 气动默认配置 | `common/mech/vehicle/attr/SubPartAttr.java:117` |
| 气动默认系数 | `common/mech/vehicle/attr/HydrodynamicAttr.java:94` |
| 投影面积自动计算 | `common/mech/part/AbstractPart.java:728` |
| 摩擦写入 | `common/mech/vehicle/collision/CollisionHandler.java` `applyFriction` |
| 滚阻换算（`C_rr` → 角冲量上限） | `common/mech/vehicle/collision/CollisionHandler.java` `setupContactFriction` |
| 轮端力矩与夹紧 | `common/mech/subsystem/WheelDriverSubsystem.java` `onPrePhysicsTick` |
| 扭矩曲线与功率上限 | `common/mech/subsystem/EngineSubsystem.java` `getTorqueAtSpeed` |
| 挡位减速与断流 | `common/mech/subsystem/GearboxSubsystem.java` `distributePower` |
| 传动分发 | `common/mech/subsystem/TransmissionSubsystem.java` `distributeOpenDiff` |
| 关节刚度/阻尼钳位 | `common/mech/vehicle/connector/AbstractConnector.java` `adjustJoint` |
| 轮端速度伺服（目标转速 = 动力链转速） | `common/mech/subsystem/WheelDriverSubsystem.java` `onPrePhysicsTick` |
| 履带速度伺服与功率反推扭矩 | `common/mech/subsystem/TrackDriveSubsystem.java` + `vehicle/track/TrackMath.java` `singleFlowMotor` |
| 动力源转速积分（`inertia` 在这里） | `common/mech/subsystem/EngineSubsystem.java` `onPrePhysicsTick` |
| 行车记录仪（服务端 CSV 采样） | `common/mech/subsystem/DriveTelemetry.java` + `AbstractGroundControllerSubsystem#sampleTelemetry` |

## 九、易错点

1. **`max_drive_force` 是力矩。** 折成牵引力要除以轮半径，一档是否被夹紧要按 `τ_w = τ_e·i/N` 判断，不能拿它和牛顿作比较。
2. **`max_power` 参与物理。** 它是逐转速生效的功率上限，写在曲线峰值以下就真的会削扭矩，写在以上才只是显示值。
3. **耐久不是满的。** 扭矩按 `0.3 + 0.7·√(hp/hp_max)` 折减，标定与实测都按满耐久取值，否则两边都不可比。
4. **半径有两个来源，力只认模型。** 滚动力臂、接触高度、牵引力换算都用模型 `type: "wheel"` 球的半径（cube 的 y 尺寸一半，已按 1 格 = 16 单位换算成米）。`abs_wheel_radius` 只进 `CarControllerSubsystem.calculateSlipRatio`，写错会让 ABS 与防滑的判定时机偏，但一牛顿的力都不改。两者不一致时以模型为准，并把 JSON 改成同值。
5. **省略字段不等于关闭。** 下列字段未声明时仍取生效值，标定前先按表核对：

   | 字段 | 省略时的生效值 |
   |---|---|
   | 部件 `hydrodynamics` | 一整条气动配置：六向系数 `[0.1, 1.0]`，射程 1 m，缩放 1 |
   | 部件 `projected_area` 任一分量 ≤ 0 | 该轴按碰撞形状包围盒计算 |
   | 车轮 `max_drive_force` | 10 000 N·m |
   | 车轮 `max_brake_force` | 3 500 N·m |
   | 车轮 `max_hand_brake_force` | 0 N·m |
   | 方块 `friction` | 0.7 |
   | 方块 `rolling_friction` | 1.0 |

6. **阻力三项不能合并。** 气动是 `v²`、滚阻正比于载荷、内阻随 `ω` 变化，合成一条曲线会掩盖绑定关系。
7. **气动参数来自模型与部件 JSON。** `projected_area` 为负时按包围盒自动算，`hydrodynamics` 没写时前向二阶系数是 1.0。换模型或补一个系数都等于换气动参数，需在卡片里记录 `Cd·A`。
8. **传动箱减速比是独立一段。** `power_outputs` 写在部件 JSON 的 `subsystem_data` 里：既不在变速箱的属性里，也不在传动箱的静态属性里。查总传动比时三段都要读到，否则单速电车会按 1:1 算成极速二三十 km/h。

9. **钳位只在装配时算一次。** `adjustJoint()` 在 `attachJoint()` 里跑，之后改部件质量、换子系统都不会重算；改完质量要重新组装，否则关节还停在旧质量对应的上限上。
10. **轮端指令是转速，不是扭矩。** 决定整车能多快提速的不是轮端力矩上限，而是动力源转速能爬多快（`inertia`）；档位表里的牵引加速度只是接触面能承受的上限。低挡大速比会把转子放大成几百吨的等效质量，见 §十二。

## 十、实测口径

静态结论要靠实测确认。记录时有两项约束，不成立则量到的不是这些公式描述的力。

- **以物理步计时。** `PhysicsLevel` 每步 `0.01 s`，与主线程 20 tps 不同步，同一段游戏时间对应的物理步数会浮动。滑行距离、减速度、换挡延迟都按物理步换算，不拿游戏 tick 当秒。
- **先确认在滚。** 滚阻的减速度是 `C_rr·g` 量级（硬地 0.1~0.4 m/s²），滑动摩擦是 `μg` 量级（数 m/s²），两者差一个数量级以上。量滑行减速前先看轮速与车速是否满足 `v = ω·r`；不满足时量到的是滑动摩擦，与滚阻无关。

记录仪的命令、文件位置、逐列口径与读法见[行车记录仪](telemetry-recorder.md)。

## 十一、悬挂与关节钳位

车轮靠一根六自由度关节挂在车体上，垂直方向的弹簧在**装配时**由 `adjustJoint()` 换算成 Bullet 的刚度与阻尼。上限来自显式积分的稳定域，超出即被压下来：

```
m_eff = m_A·m_B/(m_A+m_B)                  # 调和平均
k_max = 0.95·4·m_eff/Δt²
c_max = 0.95·(4 − k/(tps²·m_eff))/2·m_eff·tps
```

`Δt = 1/tps = 0.01 s`（服务端 `baseStep 5 × 20`），`0.95` 只是安全系数，放宽这个系数没有空间——它就是阻尼振荡器的边际稳定曲线。

- **`m_eff` 被轻的一侧决定。** 车体有多重都不影响：把 18 t 的车体拆成 4.5 t 的四块，`m_eff` 只从 178 kg 掉到 173 kg，约束反而更紧。能动的只有轮子自己的质量。
- **阻尼比刚度紧得多。** `k_max` 对应 200 rad/s 的固有频率，正常悬挂到不了；`c_max ≈ 190·m` 才是常态约束。反解成轮重下限：

  `m_轮 ≥ ζ·ω_n·m_簧上/100 ≈ 2ζ·√(g/静挠度)·m_簧上/190`

  ζ=0.4、静挠度 10 cm 时约为每轮簧上质量的 4%，而真实车辆的簧下比是 6~15%，所以正常调校下这条约束比现实更宽松；只有短行程高阻尼（赛道、硬派军用）才会撞墙。
- **转动轴的预算小一圈。** 转动轴用绕该轴的转动惯量代替质量，轮子 `I ≈ 0.4·m·r²`，比质量小一个 `r²` 量级：r=0.3 m、14 kg 的轮子，平移轴撑得住 2660 N·s/m，绕直径只有 76 N·m·s/rad。作者写的外倾、转向弹簧一旦超限，**刚度饱和会把阻尼一起压到接近零**（s→3.8 时 `c_max` 只剩 5%），整条轴等于没有减震器。

装配后的静平衡位置是 `equilibrium + m_簧上·g/k`。越过 `lower_limit`/`upper_limit` 说明没有行程余量，整车压在关节限位上，弹簧不再承重。`equilibrium < 0` 是承重轮的固定写法：把平衡位置压到装配位以下，让车体被抬起来并留出压缩行程；不受车重的成员（如履带诱导轮）写 `0`。

## 十二、起步：动力源转子折算成的等效质量

轮端执行器不是力矩源，是**速度伺服**：`WheelDriverSubsystem` 与 `TrackDriveSubsystem` 把滚动电机的 `TargetVelocity` 设成动力链下发的转速（`receivedPower.speed()`），`MaxMotorForce` 才是由功率反推的扭矩 `P/|ω|`。动力链下发的这个转速就是动力源当前转速除以该挡总传动比，所以车轮的角速度被钉在动力源转速上：

```
ω_轮 = ω_src / i ,   v = ω_src·r/i ,   v̇ = (τ_net / I)·r/i
```

`I` 是动力源的 `inertia`（kg·m²）。整车的动能有一部分存在这个转子里，折算成平动质量：

```
m_转子 = I·i²/r²          起步段可用加速度 a₁ ≈ (τ_max/I)·(r/i₁)
```

- **只与 `i₁²` 成正比，与整车质量无关。** 同样的 `I`，挡位浅的车（`i₁ ≈ 10~15`）折算几十千克，越野车与履带（`i₁ = 30~50`）折算几百吨。2S38（`inertia = 10`、`i₁ = 44.77`、`r = 0.384`）折成 **136 t**，是 21.5 t 车重的 6.3 倍，一挡起步 `a₁ = 1.29 m/s²`；同一台车若沿用 builtin 卡车柴油机的 `inertia = 50`，折算变成 680 t、`a₁ = 0.26 m/s²`：0→8 km/h 要 9 秒，而档位表按接触面力算给出 6.18 m/s²。
- **高速段与它无关。** 极速和高速加速由功率与阻力决定，转子只把低挡段拖慢，所以症状是「起步像挂了拖车、极速正常」，容易被当成传动或悬挂问题。
- **量它的办法是空挡自由加速。** 动力源在无负载时的转速积分是 `ω += τ_net/I·dt`，怠速到红线的时间约为 `I·Δω/τ_净`。2S38 的 `I = 10` 对应 1.5 秒左右，`I = 50` 要 7~8 秒，而物理上 500 hp 柴油机是 0.5~1 秒；这个读数不用上车，进游戏空挡踩一脚就能看出来。
- **两个旋钮**：`inertia` 按机型的真实曲轴+飞轮取值（乘用车 0.2~0.4，卡车 1~3），以及一挡总传动比。一挡下限由爬坡与轮端力矩上限决定，2S38 按 35° 爬坡只需要 `i₁ ≈ 33`，44.77 是十二挡等比分挡带出来的。

静态脚本只把 `m_转子` 与 `a₁` 报出来（「整车」表的 `一挡折算惯量`），不把起步过程积分成秒数：真值取决于伺服跟随与打滑，属于游戏内实测。
