# 工程模型

本文是动力标定用到的全部公式与常量。每一条都标注了代码位置，参数名与 JSON 字段一一对应。文中的数字默认为该字段的生效值，不是代码里的兜底默认值。

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

只有轮面（`type: "wheel"` 的球形接触面）产生滚阻。非轮面接触不使用该值，车体蹭地由 `friction` 的滑动摩擦负责。

地面方块的 `rolling_friction`（默认 `1.0`）是乘数：泥地填 `3` 表示该处滚阻是硬地的三倍。整车滑行减速度为

```
a = C_rr · block_rolling · g
```

与质量无关：载荷按轮数分摊，八轮载具每轮承担 `M·g/8`，合起来仍是 `C_rr·M·g`。

量级参考：滚动轮胎 0.010~0.015，越野胎 0.03~0.05，钢制履带 0.05~0.08。履带这一项是**整条履带总成**的系数，含履带板销轴摩擦、主动轮与诱导轮损失，不只是钢对地面的滚阻，所以明显高于轮胎。

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

同一份材质同时出现在碰撞箱的 `friction` 与刚体的各向异性摩擦两处，求解器还会按各向异性系数缩放摩擦方向，两处是否重复计入尚未用测量确认。静态分析暂按单一系数取值（越野胎 `1.5 × 0.7 = 1.05`），最终以实测为准。

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

气动面积按包围盒估算、`C_d = 1.0` 时结果偏保守（包围盒大于真实轮廓），可作为上界使用。

## 六、扭矩曲线

引擎的扭矩曲线由三个转速、两个比例与一个峰值扭矩确定，全部是 JSON 字段（`max_torque`、`idle_rpm`、`idle_rpm_torque_ratio`、`max_torque_rpm`、`red_line_rpm`、`red_line_torque_ratio`）：

```
n ≤ n_idle        τ = (n / n_idle) · τ_max · r_idle
n_idle < n ≤ n_τ  τ = lerp(τ_max·r_idle → τ_max)
n_τ < n ≤ n_red   τ = lerp(τ_max → τ_max·r_red)
n > n_red         τ = 5^(−10(n−n_red)/n_red) · min(τ_max·r_red, P_max/ω)
```

`getTorqueAtSpeed`（`EngineSubsystem.java:245`）对每一段都再执行一次 `τ ← min(τ, P_max/ω)`，随后乘以耐久折减 `0.3 + 0.7·√(hp/hp_max)`。满耐久标定时后者为 1。

**功率峰值的位置。** 令 `a = n_τ`、`b = n_red`、`r = r_red`，功率在 `[a, b]` 上是 `n·(c − k·n)` 型的二次式，极值在

```
n_peak = clamp( (b − r·a) / (2(1 − r)),  a, b )
```

`r·b ≥ a` 时该式大于 `b`，峰值就在红线上；扭矩掉得快时峰值落在 `n_τ` 或区间内部。K17（`a = 2000`、`b = 3000`、`r = 0.8`）算出 3500，夹到 3000，与卡片里的「301.6 kW @ 3000 rpm」一致。

由此得到两条硬性一致要求：

- 「额定功率 @ `n_p`」的 `n_p` 不得小于 `n_τ`——`n_τ` 以下扭矩与转速同时增长，功率不可能在那里见顶。
- `max_power` 取 `max(P(n))`，偏差超过 1% 就是参数错误。取小了会削掉峰值附近的一段，实际功率峰被压到 `P_max`；取大了只影响显示。

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

把需求写成方程再解，比对着参数猜要快得多。整车侧固定的量是质量 `M`（部件 `mass` 之和）、轮半径 `r`（模型 `type: "wheel"` 的 cube 在 y 向尺寸的一半，见 §3.2.1）、驱动轮数 `N`、传动箱减速比 `r_trans`、迎风面积 `A`、滚阻 `C_rr`、附着系数 `μ`；要求解的是 `i_top`、`i_1`、档数 `n_g`、`τ_max`、`n_τ`、`n_red`、`P_max`、`max_drive_force`。

| # | 约束 | 方程 | 解出或校验 |
|---|---|---|---|
| C1 | 极速（必给） | `i_top = ω_red·r / (1.03·v*)` | 解出 `i_top` |
| C2 | 功率下界 | `P_max ≥ F_res(v*)·v* / 0.85` | 校验给定功率，或解出 `P_max` |
| C3 | 曲线峰值 @ `n_p` | `τ_max ≥ P_max/ω_p`，且 `n_p ≥ n_τ` | 校验给定扭矩/转速 |
| C4 | 一档 / 爬坡 | `i_1 = M·g·(sinθ + C_rr·cosθ)·r / τ_max` | 解出 `i_1` |
| C5 | 一档附着 | `τ_max·i_1 / r ≤ μ·M·g·cosθ` | 校验（不可行时先破这条） |
| C6 | 加速时间 | `P_max ≥ M·v² / (2·0.85·t)` | 校验给定时间 |
| C7 | 档数 | `n_g ≤ 1 + ln(i_1/i_top) / ln(1.176)` | 校验换挡收益 |
| C8 | 轮端夹紧 | `max_drive_force ≥ τ_max·i_1 / N` | 解出 `max_drive_force` |
| C9 | 制动 | `max_brake_force ≥ M·a_b·r / N`，`a_b ≤ μ·g` | 解出或校验 |

`i_top` 与 `i_1` 都是「挡位传动比 × `final_ratio` × `power_outputs`」的乘积。传动箱减速比由子系统固定，缺口落在档位传动比与 `final_ratio` 上：给定额定功率的载具改不到极速时，先看是不是这两个值没有随传动箱一起调。

`F_res(v) = ½·ρ·C_d·A·v² + C_rr·M·g + τ_res(ω_e)·i_top/r`，最后一项在极速处才会进入百分位量级。

**残差表**就是逐行算 `左 − 右`，写成表格随卡片存档：

| 约束 | 上限/下界 | 实际 | 残差 |
|---|---|---|---|
| C2 功率 | | | |
| C5 一档附着 | | | |
| C7 档数 | | | |

任一行残差为负即不可行。**不可行时按下面的顺序处理，不要直接改数字凑合**：

1. 复核换算：马力↔千瓦、km/h↔m/s、轮半径是否取自模型、面积是否按包围盒。
2. 复核模型侧：`A` 用包围盒会偏大，`C_rr` 与 `μ` 是否取了本车型的量级。
3. 放松软约束：加速、爬坡、档数，按对玩家观感影响从小到大。
4. 硬约束（极速、实车功率）不放松，除非作者确认这台车本来就不是那样。

**不可行的三种典型形态**：

- **功率追不上极速**：C2 残差为负。真实功率配真实极速却不可行时，先怀疑气动面积估算偏大，而不是改功率。
- **一档打滑**：C4 定出的 `i_1` 让 C5 破掉。爬坡目标超过 `μ` 能支撑的坡度时无解——附着是硬物理，传动比再大也只是空转。
- **档数塞不下跨度**：`i_1/i_top` 很大而档数又给得少，C7 残差为负。代价不是不可行，而是级比过大、换挡后掉出扭矩平台，表现为「换完档就没劲」。

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
7. **面积来自模型。** `projected_area` 为负时按包围盒自动算，换模型等于换气动参数，需在卡片里记录面积值。
8. **传动箱减速比是独立一段。** `power_outputs` 写在部件 JSON 的 `subsystem_data` 里：既不在变速箱的属性里，也不在传动箱的静态属性里。查总传动比时三段都要读到，否则单速电车会按 1:1 算成极速二三十 km/h。

## 十、实测口径

静态结论要靠实测确认。记录时有两项约束，不成立则量到的不是这些公式描述的力。

- **以物理步计时。** `PhysicsLevel` 每步 `0.01 s`，与主线程 20 tps 不同步，同一段游戏时间对应的物理步数会浮动。滑行距离、减速度、换挡延迟都按物理步换算，不拿游戏 tick 当秒。
- **先确认在滚。** 滚阻的减速度是 `C_rr·g` 量级（硬地 0.1~0.4 m/s²），滑动摩擦是 `μg` 量级（数 m/s²），两者差一个数量级以上。量滑行减速前先看轮速与车速是否满足 `v = ω·r`；不满足时量到的是滑动摩擦，与滚阻无关。

