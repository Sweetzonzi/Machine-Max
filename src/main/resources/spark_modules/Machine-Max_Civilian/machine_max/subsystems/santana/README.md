# 桑塔纳子系统

本目录定义桑塔纳内容包使用的子系统型号。动力参照 2007 款桑塔纳经典 1.8 手动景畅（SVW7180LED）的公开规格：70 kW / 5200 rpm、145 N·m / 3000 rpm、最高车速 165 km/h、整备质量 1100 kg；车型公告资料将发动机代码列为 BSA。包内发动机注册 ID 为 `machine_max:bsa_engine`。

动力链尚未对照实车齿轮铭牌或游戏内遥测：五挡齿比仍为适配占位值，终传比 4.941454 是按当前模型车轮碰撞半径 0.34105 m 与 165 km/h 目标，使发动机净功率峰值落在目标车速附近的拟合值，不代表原厂齿比。车体质量取 908 kg，使当前车体与车门、盖板、车轮合计整备质量为 1100 kg；分件质量仍是占位拆分。

发动机阻尼暂取较小占位值，以免将未标定的内部损耗误算成极速短板；其余动力特性仍需游戏内验证。前向阻力用模型碰撞体自动投影面积 2.979 m² × 0.342193475 二阶系数拟合，使当前整车静态模型在 165 km/h 闭合，等效 Cd·A 约 1.019 m²。这是内容包拟合值，不是已查证的原车风洞数据，需在游戏内用平直路面遥测复核。模型几何保持原样，Blockbench 坐标按 16:1 换算为游戏长度。

| 车体上的实例 | `definition` | 来源 |
| --- | --- | --- |
| 发动机 | `machine_max:bsa_engine` | BSA 1.8 L |
| 变速箱 | `machine_max:santana_gearbox` | 桑塔纳 |
| 车辆控制器 | `machine_max:santana_car_controller` | 桑塔纳 |
| 左右前轮驱动器，各一个 | `machine_max:santana_front_wheel_driver` | 桑塔纳 |
| 左右后轮驱动器，各一个 | `machine_max:santana_back_wheel_driver` | 桑塔纳 |
| 差速器 | `machine_max:open_differential` | builtin |
| 驾驶座 | `machine_max:default_seat` | builtin |
| 副驾、左后座、右后座，各一个 | `machine_max:default_passenger_seat` | builtin |
| 手套箱 | `machine_max:glove_box` | builtin |
| 后备箱 | `machine_max:default_trunk` | builtin |
| 车门、引擎盖、后备箱盖铰链驱动器，各一个 | `machine_max:rotation_joint_driver` | builtin |
| 四扇车门、引擎盖、后备箱盖锁扣，每处一个 | `machine_max:door_latch` | builtin |

发动机折线只约束已知的最大功率 / 最大扭矩点；怠速 850 rpm、红线 6000 rpm 和中间转速曲线仍是近似值。仍待实测的项目包括发动机阻尼与惯量、挡位间距、离合 / 换挡动态、轮胎滚阻与前向气动拟合。后续安装到 Part 时：

- 动力走「发动机 → 变速箱 → 开放式差速器 → 左右前轮驱动器」。四个驱动器各绑定车体对应的轮轴连接点；前轮连接点开放转向轴 `yr`，后轮锁定 `yr`，只接收制动与手刹，不接传动输出。轮子 Part 本身不安装驱动器。
- `EngineHitBox` 关联发动机；`GloveboxStorageInteract` 和 `TrunkStorageInteract` 分别指向手套箱与后备箱，使用瞄准交互。储物在车体上，不随盖板拆走；后备箱盖开合与储物交互分开接线。
- 四个座位分别使用现有座位定位器，驾驶输入由驾驶座提供。
- 四扇车门、引擎盖与后备箱盖各挂一个 `joint_driver`，绑定车体上对应的铰链连接点，型号统一取 builtin 的 `machine_max:rotation_joint_driver`（三根旋转轴都配置，铰链上真正转动的轴由连接点限位决定，另外两轴的目标角恒为 0、只起锁轴作用）。交互区长在被操作的那块零件上：四扇车门各在自己车门零件里，引擎盖与后备箱盖在各自的盖板零件里；统一用 `accurate`，点击把 `interact_*_door` / `interact_hood` / `interact_trunk_door` 交给装配体广播给全车部件，由车体的状态机接住。车门交互区的体积是包裹整个车门（连同它的命中框）的单个轴对齐盒，即 `LeftFrontDoorInteract` 等骨骼里的一根无贴图方块，随车门一起开合、车门拆走时一起消失；盒比门自己的命中框大一圈，射线打向车门时先碰到的是盒，点击因此总能选中车门而不是被命中框挡掉。动画与状态机都留在车体（`animations/part/santana.geo/`、`controllers/part/santana.geo/`），车体的状态机在开、关两个状态间切换，驱动器再按动画里铰链骨骼的姿态驱动关节。盖板抬起后交互区随盖板离开原位，车体上的后备箱储物交互区可以直接瞄准。
- 六处可开合的部件（四扇车门、引擎盖、后备箱盖）除铰链外各有一对锁扣连接点：车体侧 `connector.machine_max.<位置>_lock` 装一台 `machine_max:door_latch`，可拆件侧是 `connector.machine_max.lock`——可拆件侧只写接口词，是哪一扇门、哪块盖板由零件名与变体承担。车体侧的位置词取 `left_front_door` / `right_front_door` / `left_back_door` / `right_back_door` / `hood` / `trunk_door`。门侧定义按左右取 `machine_max:fixed_left` / `machine_max:fixed_right`，法线指向舱内，与车体侧 `machine_max:fixed_rigid` 反向对接；两块盖板在车体上方，两侧都取竖直法线（盖板侧 `machine_max:fixed_down`、车体侧 `fixed_rigid` 覆写 `direction: yp`）。车体侧的锁扣点落在被扣件的自由边端面上：车门在后边缘的把手高度，比门轴低 0.375 m，避开同一根立柱上那扇门的铰链；引擎盖在前缘中点的下方（`hull_hood_latch`，模型坐标 0, 23.8, -51），后备箱盖在后缘中点的下方（`hull_trunk_latch`，模型坐标 0, 23.5, 56），都落在盖板底面与车身支架之间。模板与存档都保留这对锁扣连接，生成与读档时按连接记录直接重建、锁扣一开始就扣着；运行时解扣后进入等待分离，关合到位再按几何判据重扣。点击先到铰链端口，再由车体铰链连接点的 `signal_translations` 把 `interact_<位置>_door` / `interact_hood` / `interact_trunk_door` 转成锁扣接受的 `interact` 频道；同一次点击仍要投给 `global`，车体的状态机才切得动动画。型号取 `capture_distance` 0.04 m、`lock_distance` 0.015 m、`max_capture_force` 0、`max_lock_speed` 0.25 m/s、重武装倍率 2，都是待实机标定的起始值。
- 引擎盖与后备箱盖的铰链带一点弹簧与阻尼，平衡位置放在全开处，但当前刚度（2.3 / 1.4 N·m/rad，全开位 3 / 1.8 N·m）相对盖板自重绕铰链的力矩（约 150 / 80 N·m）可以忽略，因此这副弹簧不承担气压撑杆的作用：盖板关着时由自重压在下限上、扣上锁扣后由锁扣承力，抬起过程中几乎感不到助力，阻尼只用来抑制接近开度时的过冲。开片段是循环（盖板抬起后靠驱动器持续给目标角才不落回），关片段是单次（走完松手，由锁扣承力）。车门不配这个弹簧：绕竖直轴开合的门没有重力回正力矩，弹簧只会把门推开或拉住，起不到同样的提示作用。
- 车体有控制器集，六台控制器各管一扇门或一块盖板的开、关两个状态，播的片段都是被自己状态 `animations` 引用的，因此随状态切换启停。方向盘与踏板的常驻片段 `parallel0` 不写进任何状态，由引擎在客户端持续播放动画集里所有未被控制器引用的 `loop:true` 片段；它只驱动方向盘与三个踏板骨骼，与车门铰链骨骼不交叠，舱内表现与门的开闭互不影响。常驻片段不需要为它单独写控制器。

两份轮驱定义省略了顶层 `$schema` 字段，因为当前轮驱 schema 不接受这个编辑器提示字段。仍可将它们用[子系统静态属性 schema](../../docs/zh_cn/schemas/subsystem/subsystem_static_attr.schema.json)离线校验。
