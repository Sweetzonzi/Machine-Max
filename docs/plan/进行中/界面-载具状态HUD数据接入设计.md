# 载具状态 HUD 数据接入设计

> 状态：部分实施。builtin 三种状态图与席位列表、vehicle.seats 动态席位、乘员实体名称查询已交付；通用共享值、独立耐久数值条和真实履带几何待实施。通用能力见 [MoLang 共享值与信号互通设计](../计划中/MoLang-共享值与信号互通设计.md)。

## 1. 显示目标

载具状态 HUD 用于速览零件、子系统和履带耐久，以及全部座位的名称、占用者、当前座位和下一个可换座位。图形版以实时模型或轮廓承载位置与损伤，简版使用席位名单。速度、转速、档位和稳定器不属于本方案的读数范围。

builtin 正式页面位于 `machine_max/documents/vehicle_hud/`，样式与独立布局分属 `huds/` 和 `hud_layouts/`：

| 名称 | 样式／布局资源键 |
| --- | --- |
| 方位跟随状态图 | `machine_max:vehicle_status_heading` |
| 视角跟随状态图 | `machine_max:vehicle_status_camera` |
| 固定俯视状态图 | `machine_max:vehicle_status_topdown` |
| 席位列表 | `machine_max:vehicle_seat_list` |

三种状态图使用 `<machinemax-vehicle-model>`，席位列表使用模板重复，只显示座位与乘员信息。页面通过 HUD 快照读取实际载具状态，模型由原生元素绘制。

## 2. 通用数据由既有存储提供

座位所在 Part 与履带轮、发动机、炮尾等状态来源可以各不相同。来源 Part 内的 `subpart.*` 或 `local.subsystem_*` 查询由该来源的程序入口使用；座位上的 HUD 不能直接用这些局部查询寻找其他 Part。

跨 Part 的计算结果通过公共 MoLang 接口写入装配体 `SubsystemController.signalStorage`，或经已有信号线路送达。HUD 使用现有 `global.get/get_str` 的快照读取语义，不要求 Part 变体提供 `hud_readouts` 字段，也不持有独立导出注册表。相同共享值还可供其他 Part 的动画、hitbox 条件及程序子系统使用。

例如，内容包可约定 `status.left_track.hp`、`status.left_track.max_hp`、`status.left_track.present` 等键；这些是作者定义的键，不是引擎内置查询。HUD 页面配置标题、图标、显示次序、显隐和配色。发布者的运行端、时序、缺失状态及键的来源约定属于通用数据契约。

`CustomHud.tick` 在客户端主线程采集 `HudPartSnapshot`，后者已复制宿主 Part 的 local 存储和所属装配体的 global 存储。`HudRuntimeElement.Frame` 提供稳定的单轮求值输入。HUD 不直接写物理线程活动对象，也不为显示建立第二份需要主动发布的全车值容器。

Spark 的基础变量桥接存在已实测的断层：编译后的 `v.*` 目前可能只访问上下文内部存储；不能将 `v.x` 写入视为已写回 Part 的信号容器。实际情况和修复契约见通用设计 §3。现有 `local.get`／`global.get` 的直接存储读取与此问题分别核对。

## 3. 简版席位名单

`vehicle.seats` 重复集合消费 `HudVehicleSnapshot` 的座次、名称、占用、可换入性、当前／下个／在途目标和乘员名称。行按 `(SubPart ID, 座位键)` 复用，载具归属变化不影响行身份。`seat.index` 为零基下标，`seat.number` 为从 1 开始的座号；`seat.name/entity_name/state` 等在当前项作用域读取。

`SeatSubsystem.getOccupantName()` 直接读取本端当前 `passenger.getName()`；未占用或实体引用暂缺时返回空文本。名字跟随实体状态，支持玩家名、自定义实体名和默认实体名的本地化。座位归属恢复属于独立工作，本功能依赖已有乘员绑定。

占用已知但名字暂缺时显示 `…`；空位的玩家栏显示 `---`；不可用状态独立于是否有人占用。当前席位和下个可换目标单独标记，在途请求不提前改变当前席位。服务端执行换座可用性校验。

`HudPageBinding` 接受 weapon.tubes、weapon.belts 和 vehicle.seats 三种集合。座位集合可用于普通和武器 HUD；管／弹链集合要求武器宿主。座位身份和观察者状态由快照提供。

## 4. 图形模型与履带

`VehicleModelElement` 当前绘制全车 SubPart 模型、按零件耐久着色的外层、按所属子系统耐久着色的内构和座位标记。它从 HUD 宿主解析装配体，几何可以跨 Part；无需先把全部几何耐久手动转存为 MoLang 键。

实际履带是 `TrackLink` 关系与程序化／配件几何。图形标签需要接入实际履带绘制及损伤着色，提供 MoLang 查询本身不会使履带几何出现。按真实履带身份去重，不能把成员轮数量解释为履带数量。

模型、履带与标记共用呈现位姿、投影和裁剪。原生几何的着色直接取对应对象的真实状态；页面上的定制耐久组件使用通用共享读数。未安装与解散状态使用中性占位或显式缺失显示，不能画成完好或毁坏。最大值与当前值的读取遵循通用发布时序，不推断多个键具备事务一致性。

## 5. 实施与验收

1. builtin 样式、乘员实体名称查询和动态名单通过 AUI 绑定／布局回归；GameTest 验证实体改名、引用缺失和离座的名称读数。
2. 通用层按共享值设计交付所需读写，核对 Spark 变量桥接。客户端验收覆盖换座竞争、断线、迟加入和维度切换。
3. 图形标签补实际履带几何与着色，选择具体样式及显示资源。覆盖拆装、分裂、合并、单车多条履带、不同高度座位与活动部件。
4. 实机检查 GUI scale、内容包重载、首／第三人称、炮镜显隐和帧耗时。

JUnit 固定查询、快照和动态重复项契约；真实载具、乘员与同步判据用 GameTest；几何与性能使用客户端验收。Gradle 命令使用 `--offline`。图形页采用已有损伤模型；真实履带绘制和自定义耐久数值发布属于待交付项。
