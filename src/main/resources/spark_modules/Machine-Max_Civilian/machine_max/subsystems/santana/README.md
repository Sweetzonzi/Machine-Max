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

发动机折线只约束已知的最大功率 / 最大扭矩点；怠速 850 rpm、红线 6000 rpm 和中间转速曲线仍是近似值。仍待实测的项目包括发动机阻尼与惯量、挡位间距、离合 / 换挡动态、轮胎滚阻与前向气动拟合。后续安装到 Part 时：

- 动力走「发动机 → 变速箱 → 开放式差速器 → 左右前轮驱动器」。四个驱动器各绑定车体对应的轮轴连接点；前轮连接点开放转向轴 `yr`，后轮锁定 `yr`，只接收制动与手刹，不接传动输出。轮子 Part 本身不安装驱动器。
- `EngineHitBox` 关联发动机；`GloveboxStorageInteract` 和 `TrunkStorageInteract` 分别指向手套箱与后备箱，使用瞄准交互。储物在车体上，不随盖板拆走；后备箱盖开合与储物交互分开接线。
- 四个座位分别使用现有座位定位器，驾驶输入由驾驶座提供。车门、引擎盖和后备箱盖的开合留给连接点配置，不新增作动器型号。

两份轮驱定义省略了顶层 `$schema` 字段，因为当前轮驱 schema 不接受这个编辑器提示字段。仍可将它们用[子系统静态属性 schema](../../docs/zh_cn/schemas/subsystem/subsystem_static_attr.schema.json)离线校验。
