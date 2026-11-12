# FMOD bank 事件与参数参考

## 数据来源与读取口径

资源来自音效工程师提供的 `Desktop-11-11-14-51.zip`，整理日期为 2026-11-11。完整事件路径、2D／3D 属性、单次／持续属性、参数名、上下界、默认值和读写标记，均由 FMOD Engine 2.03.15 实际加载 bank 后读取。

行为定义以工程师随资源提供的说明为依据。只从事件名或参数名判断的用途在本文标为“命名推断”，具体数值映射待工程师确认。`persistent` 在本文写作“持续”，表示 Studio 未将事件标为 oneshot，具体循环与收尾由事件工程控制。

## 资源目录与 bank ID

下表路径相对于 `src/main/resources/spark_modules/`。配套的 Master 与字符串表统一放在 Builtin，`abrams.bank` 放在美系型谱包，M1A2 包通过 `meta.json` 的硬依赖使用两者。

| 文件 | 内容包内路径 | bank ID | 事件数 |
| --- | --- | --- | --- |
| Master.bank | `Machine-Max_Builtin/machine_max/fmod/Master.bank` | `machine_max:master` | 0 |
| Master.strings.bank | `Machine-Max_Builtin/machine_max/fmod/Master.strings.bank` | `machine_max:master.strings` | 0 |
| basic_vehicle_sounds.bank | `Machine-Max_Builtin/machine_max/fmod/basic_vehicle_sounds.bank` | `machine_max:basic_vehicle_sounds` | 39 |
| mfx.bank | `Machine-Max_Builtin/machine_max/fmod/mfx.bank` | `machine_max:mfx` | 25 |
| abrams.bank | `Machine-Max_US_Basic_Kit/machine_max/fmod/abrams.bank` | `machine_max:abrams` | 8 |

`mfx.bank` 的 Studio bank 路径为 `bank:/Mfx`。配置使用文件路径生成的 `machine_max:mfx`，事件使用下列大小写精确的 `event:/...` 路径。路径转 bank ID 的规则见 `../Spark-Core/src/main/kotlin/cn/solarmoon/spark_core/pack/modules/FModModule.kt#read`。

## 参数定义域与控制方式

范围均为闭区间。同名参数在本批所有包含它的事件中具有相同的范围、默认值和读写标记。每个事件实际拥有的参数以事件表为准。除 `Distance` 和 `load_trigger` 外，表中参数均为事件实例的局部可写参数。

| 参数 | 定义域 | 默认值 | 类型与来源 | 功能 |
| --- | --- | --- | --- | --- |
| `firing` | `{0, 1}` | 1 | 离散，工程师确认 | 1 表示连续开火，运行中由 1 设为 0 触发尾音。初始设为 0 再启动事件可触发单发 |
| `overheat` | `[0, 1]` | 0 | 连续，工程师确认端点行为 | 设为 1 停止枪声，触发散热与故障声。中间值的混音及故障后的恢复方式待确认 |
| `deaf` | `[0, 1]` | 0 | 连续，工程师确认端点行为 | 设为 1 触发循环耳鸣，设为 0 自动进入尾音。中间值行为待确认 |
| `interior` | `[0, 1]` | 0 | 连续，舱内外混音 | 0 表示舱外，1 表示舱内。SparkCore 绑定声源的播放路径按实时听者判定维护此值 |
| `rpm` | `[0, 100]` | 0 | 连续 | 发动机将怠速映射为 0、红线映射为 100；轮胎将实际 0～1200 r/min 映射为 0～100 并钳位；普通零件接触固定为 0。履带换算待定。轮胎材质纹理独立于 rpm |
| `load` | `[0, 1]` | 0 | 连续 | 发动机现有接线传入有效油门开度 |
| `load_trigger` | `[0, 1]` | 0 | 只读，bank 实测 | 事件内部的负载触发参数，由 Studio 工程维护 |
| `broken` | `[0, 1]` | 0 | 连续，命名推断 | 故障混音控制，端点含义、恢复方式与触发规则待确认 |
| `turn_speed` | `[0, 1]` | 0 | 连续，命名推断 | 炮塔转动速度混音控制，归一化基准及角速度换算待确认 |
| `amount` | `[0, 1]` | 0 | 连续，命名推断 | 履带／轮胎尖叫声的强度；轮胎接线将实际滑动速度 3～8 m/s 映射为 0～1 并钳位。该换算是代码侧标定，履带映射待定 |
| `surface` | `[0, 1]` | 0 | 连续，工程师确认 | 接触表面的材质纹理。0 水泥、0.2 泥土、0.4 雪、0.6 泥浆、0.8 木制、1 冰，中间值混合纹理 |
| `contact` | `[0, 1]` | 0 | 连续，工程师确认 | surface 所控制的材质声音纹理强度，该纹理独立于 rpm |
| `Distance` | `[0, 300]` | 0 | 自动、只读，bank 实测 | Studio 自动距离参数，由声源与听者空间状态驱动 |

发动机映射见 `src/main/java/io/github/sweetzonzi/machine_max/common/mech/subsystem/EngineSubsystem.java#tickFmodSound`。听者参数维护和可写参数筛选见 `../Spark-Core/src/main/kotlin/cn/solarmoon/spark_core/client/sound/FModSoundPlayer.kt#updateInterior` 与 `../Spark-Core/src/main/kotlin/cn/solarmoon/spark_core/client/sound/FModSoundPlayer.kt#isWritableParameter`。

## 已确认的事件行为

### 轮胎材质纹理与滑动摩擦

`event:/Default_Vehicles/Transmission/Tracks_and_Tires/tire_noise` 使用 surface 选择接触材质，使用 contact 连续控制该材质声音纹理的强度。工程师确认这层纹理独立于转速，rpm 为 0 时也能正常播放。

| surface | 接触表面 |
| --- | --- |
| 0 | 水泥 |
| 0.2 | 泥土 |
| 0.4 | 雪 |
| 0.6 | 泥浆 |
| 0.8 | 木制 |
| 1 | 冰 |

相邻档位之间的数值会混合材质纹理，例如 surface=0.3 混合泥土与雪。具体混合曲线由音效工程控制。

此事件可用于滑动摩擦音。将 rpm 固定为 0，用 surface 选择摩擦表面，用 contact 控制材质纹理强度。业务接线需根据接触与摩擦状态驱动 contact，并为静止或失去接触的状态处理音效强度或实例生命周期。

### 接触音效运行时接线

轮胎与普通零件的地形接触由 `src/main/java/io/github/sweetzonzi/machine_max/common/mech/vehicle/collision/CollisionEffectManager.java#tick` 在客户端播放。事件来自 `machine_max:basic_vehicle_sounds`，路径为 `event:/Default_Vehicles/Transmission/Tracks_and_Tires/tire_noise`；轮胎尖叫使用同目录的 `tire_screech`。接触音不读取材料的 sounds.fmod.bank，该字段供材料事件扩展预留。资源缺失、原生库不可用或实例创建失败时静音，持续活动期间每 20 tick 至多重试一次。

轮胎面由 `src/main/java/io/github/sweetzonzi/machine_max/common/mech/vehicle/SubPart.java#isWheelSurface` 判定，半径通过 `src/main/java/io/github/sweetzonzi/machine_max/common/mech/vehicle/SubPart.java#getWheelRadius` 查询，并同时要求该形状属于轮胎碰撞箱。轮胎面取零件局部 X 轴上的角速度绝对值，普通接触面的角速度与半径取 0。实际每分钟转数为 `|ω| × 30 / π`，事件 rpm 为 `clamp(实际转速 / 1200, 0, 1) × 100`。1200 r/min 是代码侧统一钳位基准，可按听感标定。

滑动速度 v_slip 是接触点相对对方的速度在接触平面内的长度，包含轮面自转贡献；滚动速度 v_roll 为 `|ω| × 半径`。当前强度换算如下，速度单位均为 m/s：

```text
roll    = clamp((v_roll - 0.5) / (20 - 0.5), 0, 1)
slip    = clamp((v_slip - 0.5) / (8 - 0.5), 0, 1)
contact = min(max(roll, slip), 轮胎面 ? 0.35 : 1)
amount  = clamp((v_slip - 3) / (8 - 3), 0, 1)
```

因此正常滚动由滚动项驱动纹理；锁死拖滑与普通零件刮擦可在 rpm=0 时由滑动项发声。轮胎面的材质纹理 contact 上限为 0.35，普通零件刮擦的范围为 0～1；钳位常量位于 `src/main/java/io/github/sweetzonzi/machine_max/common/mech/vehicle/collision/CollisionEffectManager.java#MAX_TIRE_CONTACT`。轮胎尖叫由实际滑动速度驱动，高滑移率本身不足以触发。强度计算位于 `CollisionEffectManager.java#CollisionSnapshot`，音高由事件参数控制，播放器使用默认音高倍率 1。

surface 使用接触方块状态：雪与覆雪方块为 0.4，冰为 1，泥与沾泥的红树根为 0.6，木质及斧采掘标签为 0.8，土、草、沙、羊毛及铲／锄采掘标签为 0.2，石、金属和未知方块为 0。雪覆盖优先于基底材质，分类实现见 `CollisionEffectManager.java#getSurface`。切换材质只更新同一实例的 surface，直接设置目标档位。

物理线程在物理步完成后发布不可变接触快照。每个 SubPart 持有轮胎、普通接触和轮胎尖叫三个独立声源，空间位置使用各自最近的接触点，多普勒速度使用零件平动速度；轮胎自转仅参与业务参数。静止或断触时将强度及 rpm 置 0，保留实例两 tick 后请求 Studio 停止过渡。部件销毁、载具区块卸载时立即停止实例；听者的 interior 按第一人称且正在控制任一子系统的规则实时维护。生命周期实现见 `CollisionEffectManager.java#ContactSound` 与 `src/main/java/io/github/sweetzonzi/machine_max/common/mech/vehicle/VehicleCore.java#setInLoadedChunk`。

健康履带的轮面由 `src/main/java/io/github/sweetzonzi/machine_max/common/mech/vehicle/track/TrackAudio.java#tick` 管理。断带或解散的成员轮面进入本节的轮胎路径。普通接触面的持续刮擦接线涵盖原版地形和 Create 装置方块，撞击单次音效与 Master FX 接线独立。

### 机枪

四个机枪事件均有 `firing`、`overheat`、`interior` 和自动 `Distance`。

| 场景 | 调用时序 |
| --- | --- |
| 单发 | 创建实例，将初始 firing 设为 0，然后启动 |
| 连射 | 初始 firing 设为 1，然后启动并维持同一实例 |
| 停火尾音 | 对正在连射的实例将 firing 设为 0，让事件完成自身尾音 |
| 过热或故障 | 对实例将 overheat 设为 1，触发工程定义的散热与故障声 |

`firing` 的 bank 默认值是 1，单发需要显式传入初始值 0。工程师说明中的尾音由参数变化触发。SparkCore 的 `fadeFmodSound` 对应 Studio 停止命令，入口见 `../Spark-Core/src/main/kotlin/cn/solarmoon/spark_core/client/sound/FModSoundPlayer.kt#fadeOut`。

### Master FX

| 分类 | 事件行为与选择依据 |
| --- | --- |
| Flyby | 各种弹丸、火箭和车辆经过听者的音效，全部为 3D 单次事件，具有空间定位，没有事件参数 |
| Explode | small、medium、big、huge 四档爆炸，具有 interior 与自动 Distance |
| Destruction | building、ground、water 三类环境破坏，具有 interior 与自动 Distance |
| Shells_Hit | Small、Medium、Big 三组命中，每组提供 armour、ground、water。Explode 子组提供 he_small、he_big 装药破片弹药爆炸。均具有 interior 与自动 Distance |
| Effects/deaf | 2D 持续耳鸣，deaf=1 进入循环，deaf=0 进入尾音 |

工程师确认了分类用途。具体口径阈值、爆炸档位的威力阈值、远近层切换距离和各档混音曲线待确认。`flyby_large_slow` 表达慢速大口径的命名，事件名本身未给出速度阈值。

### 传动、操作与炮塔

| 事件或分组 | 用途与元数据 |
| --- | --- |
| Default_Vehicles/Transmission/Gears/gears_rattle | 传动齿轮声，3D 单次，带 interior 与自动 Distance。rattle 的具体工况为命名推断 |
| Default_Vehicles/Transmission/Gears/gears_grind | 传动齿轮声，3D 单次，带 interior 与自动 Distance。grind 的具体工况为命名推断 |
| Default_Vehicles/Cabin_Movements/lever_pull | 工程师指定用于手刹／换挡的操作音，3D 单次，带 interior 与自动 Distance |
| Default_Vehicles/Armament/Turret/*_manual | 手摇炮塔双机，horizontal 对应水平，vertical 对应俯仰。均为 3D 持续 |
| Default_Vehicles/Armament/Turret/*_hydraulic | 自动炮塔双机，按工程命名为液压型，horizontal 对应水平，vertical 对应俯仰。均为 3D 持续 |

四个炮塔事件各自使用 `turn_speed`、`broken`、`interior` 与自动 `Distance`。手摇／自动通过选择不同事件表达。

### 换挡与手刹运行时接线

第一方变速箱配置 `sounds.fmod.bank` 与 `sounds.fmod.shift`，持续监测同步后的挡位变化。客户端每 tick 最多播放一次 `event:/Default_Vehicles/Transmission/Gears/gears_rattle`，升挡和降挡共用该事件。首次收到含挡位的同步快照只建立声音基线；首次 tick 和区块恢复后的首次 tick 也只记录状态。自动换挡和手动换挡共用实际挡位变化入口，按键请求但挡位未改变时没有换挡声。实现见 `src/main/java/io/github/sweetzonzi/machine_max/common/mech/subsystem/GearboxSubsystem.java#tickClientSounds` 与同类的 `onSyncedDataUpdated`。

汽车、摩托车和履带车控配置 `sounds.fmod.bank` 与 `sounds.fmod.handbrake`。各客户端通过既有操作同步独立计算车控，不添加音效同步字段。有效手刹制动量 actualHandBrake 在 0.1 阈值两侧变化时，各播放一次 `event:/Default_Vehicles/Cabin_Movements/lever_pull`；长按或保持驻车状态不重复播放。首次取样和区块恢复后的首次取样只建立基线。实现见 `src/main/java/io/github/sweetzonzi/machine_max/common/mech/subsystem/AbstractGroundControllerSubsystem.java#tickHandBrakeSound`。

两种事件均绑定到所属子系统，在客户端主线程通过 `ISoundSpreader.tryPlayFmodSound` 传入空业务参数表。公共声源的位置、平动速度和实时车内判定由 `src/main/java/io/github/sweetzonzi/machine_max/common/mech/subsystem/AbstractSubsystem.java#getPosition`、`getSpeed` 和 `shouldApplyInteriorEffect` 提供；播放器维护 interior 和自动空间效果。实例播放结束后由 SparkCore 释放。配置了 FMOD 的操作声在资源缺失或播放失败时静音；未配置 FMOD 的定义使用 sounds 中的 OGG 操作音。

变速箱的离合接合／分离音使用 clutch_in／clutch_out 配置的 OGG，与换挡音分别按状态变化触发。脚刹与气刹使用轮驱的 brake_on／brake_off 配置。当前 bank 没有独立的离合、踏板或气刹事件，gears_grind 没有运行时触发判据。

## 完整事件目录

每个路径均已通过 Studio 按路径解析，并核对属于对应 bank。参数名对应本文“参数定义域与控制方式”中的范围与默认值。

所有 72 个事件的 GetMinMaxDistance 元数据均为 1～20，自动 Distance 参数的定义域为 0～300。两者分别记录事件距离元数据和参数范围，实际衰减、远近层与可听范围还取决于工程的空间化与混音设置。

### 通用载具

bank ID 为 `machine_max:basic_vehicle_sounds`。

#### `event:/Default_Vehicles/Armament/Guns/Loop`

| 完整事件路径 | 空间 | 生命周期 | 参数 |
| --- | --- | --- | --- |
| `event:/Default_Vehicles/Armament/Guns/Loop/762fast` | 3D | 持续 | `Distance`、`firing`、`interior`、`overheat` |
| `event:/Default_Vehicles/Armament/Guns/Loop/m2hb` | 3D | 持续 | `Distance`、`firing`、`interior`、`overheat` |

#### `event:/Default_Vehicles/Armament/Guns/Single`

| 完整事件路径 | 空间 | 生命周期 | 参数 |
| --- | --- | --- | --- |
| `event:/Default_Vehicles/Armament/Guns/Single/main_gun_big` | 3D | 单次 | `Distance`、`interior` |
| `event:/Default_Vehicles/Armament/Guns/Single/main_gun_huge` | 3D | 单次 | `Distance`、`interior` |
| `event:/Default_Vehicles/Armament/Guns/Single/main_gun_large` | 3D | 单次 | `Distance`、`interior` |
| `event:/Default_Vehicles/Armament/Guns/Single/main_gun_medium` | 3D | 单次 | `Distance`、`interior` |
| `event:/Default_Vehicles/Armament/Guns/Single/main_gun_small` | 3D | 单次 | `Distance`、`interior` |

#### `event:/Default_Vehicles/Armament/Reload`

| 完整事件路径 | 空间 | 生命周期 | 参数 |
| --- | --- | --- | --- |
| `event:/Default_Vehicles/Armament/Reload/cannon_reload_manual_3s` | 2D | 单次 | 无 |
| `event:/Default_Vehicles/Armament/Reload/cannon_reload_manual_5s` | 2D | 单次 | 无 |
| `event:/Default_Vehicles/Armament/Reload/cannon_reload_manual_7s` | 2D | 单次 | 无 |

#### `event:/Default_Vehicles/Armament/Turret`

| 完整事件路径 | 空间 | 生命周期 | 参数 |
| --- | --- | --- | --- |
| `event:/Default_Vehicles/Armament/Turret/horizontal_hydraulic` | 3D | 持续 | `broken`、`Distance`、`interior`、`turn_speed` |
| `event:/Default_Vehicles/Armament/Turret/horizontal_manual` | 3D | 持续 | `broken`、`Distance`、`interior`、`turn_speed` |
| `event:/Default_Vehicles/Armament/Turret/vertical_hydraulic` | 3D | 持续 | `broken`、`Distance`、`interior`、`turn_speed` |
| `event:/Default_Vehicles/Armament/Turret/vertical_manual` | 3D | 持续 | `broken`、`Distance`、`interior`、`turn_speed` |

#### `event:/Default_Vehicles/Cabin_Movements`

| 完整事件路径 | 空间 | 生命周期 | 参数 |
| --- | --- | --- | --- |
| `event:/Default_Vehicles/Cabin_Movements/lever_pull` | 3D | 单次 | `Distance`、`interior` |

#### `event:/Default_Vehicles/Engine/Cars`

| 完整事件路径 | 空间 | 生命周期 | 参数 |
| --- | --- | --- | --- |
| `event:/Default_Vehicles/Engine/Cars/car_drive_2c` | 3D | 持续 | `broken`、`Distance`、`interior`、`load`、`load_trigger`、`rpm` |
| `event:/Default_Vehicles/Engine/Cars/car_drive_4c` | 3D | 持续 | `broken`、`Distance`、`interior`、`load`、`load_trigger`、`rpm` |
| `event:/Default_Vehicles/Engine/Cars/car_drive_6c` | 3D | 持续 | `broken`、`Distance`、`interior`、`load`、`load_trigger`、`rpm` |
| `event:/Default_Vehicles/Engine/Cars/car_drive_dsl` | 3D | 持续 | `broken`、`Distance`、`interior`、`load`、`load_trigger`、`rpm` |
| `event:/Default_Vehicles/Engine/Cars/car_start_2c` | 3D | 单次 | `Distance`、`interior` |
| `event:/Default_Vehicles/Engine/Cars/car_start_4c` | 3D | 单次 | `Distance`、`interior` |
| `event:/Default_Vehicles/Engine/Cars/car_start_6c` | 3D | 单次 | `Distance`、`interior` |
| `event:/Default_Vehicles/Engine/Cars/car_start_dsl` | 3D | 单次 | `Distance`、`interior` |
| `event:/Default_Vehicles/Engine/Cars/car_stop_2c` | 3D | 单次 | `Distance`、`interior` |
| `event:/Default_Vehicles/Engine/Cars/car_stop_4c` | 3D | 单次 | `Distance`、`interior` |
| `event:/Default_Vehicles/Engine/Cars/car_stop_6c` | 3D | 单次 | `Distance`、`interior` |
| `event:/Default_Vehicles/Engine/Cars/car_stop_dsl` | 3D | 单次 | `Distance`、`interior` |

#### `event:/Default_Vehicles/Engine/Tanks`

| 完整事件路径 | 空间 | 生命周期 | 参数 |
| --- | --- | --- | --- |
| `event:/Default_Vehicles/Engine/Tanks/tank_drive_uni` | 3D | 持续 | `broken`、`Distance`、`interior`、`load`、`load_trigger`、`rpm` |
| `event:/Default_Vehicles/Engine/Tanks/tank_start_uni` | 3D | 单次 | `Distance`、`interior` |
| `event:/Default_Vehicles/Engine/Tanks/tank_stop_uni` | 3D | 单次 | `Distance`、`interior` |

#### `event:/Default_Vehicles/Transmission/Gears`

| 完整事件路径 | 空间 | 生命周期 | 参数 |
| --- | --- | --- | --- |
| `event:/Default_Vehicles/Transmission/Gears/gears_grind` | 3D | 单次 | `Distance`、`interior` |
| `event:/Default_Vehicles/Transmission/Gears/gears_rattle` | 3D | 单次 | `Distance`、`interior` |

#### `event:/Default_Vehicles/Transmission/Tracks_and_Tires`

| 完整事件路径 | 空间 | 生命周期 | 参数 |
| --- | --- | --- | --- |
| `event:/Default_Vehicles/Transmission/Tracks_and_Tires/tire_noise` | 3D | 持续 | `contact`、`Distance`、`interior`、`rpm`、`surface` |
| `event:/Default_Vehicles/Transmission/Tracks_and_Tires/tire_screech` | 3D | 持续 | `amount`、`Distance`、`interior` |
| `event:/Default_Vehicles/Transmission/Tracks_and_Tires/track_screech` | 3D | 持续 | `amount`、`Distance`、`interior` |
| `event:/Default_Vehicles/Transmission/Tracks_and_Tires/tracks_heavy` | 3D | 持续 | `Distance`、`interior`、`rpm` |
| `event:/Default_Vehicles/Transmission/Tracks_and_Tires/tracks_light` | 3D | 持续 | `Distance`、`interior`、`rpm` |
| `event:/Default_Vehicles/Transmission/Tracks_and_Tires/tracks_medium` | 3D | 持续 | `Distance`、`interior`、`rpm` |
| `event:/Default_Vehicles/Transmission/Tracks_and_Tires/tracks_muted` | 3D | 持续 | `Distance`、`interior`、`rpm` |

### Master FX

bank ID 为 `machine_max:mfx`。

#### `event:/Master_FX/Destruction`

| 完整事件路径 | 空间 | 生命周期 | 参数 |
| --- | --- | --- | --- |
| `event:/Master_FX/Destruction/destruction_building` | 3D | 单次 | `Distance`、`interior` |
| `event:/Master_FX/Destruction/destruction_ground` | 3D | 单次 | `Distance`、`interior` |
| `event:/Master_FX/Destruction/destruction_water` | 3D | 单次 | `Distance`、`interior` |

#### `event:/Master_FX/Effects`

| 完整事件路径 | 空间 | 生命周期 | 参数 |
| --- | --- | --- | --- |
| `event:/Master_FX/Effects/deaf` | 2D | 持续 | `deaf` |

#### `event:/Master_FX/Explode`

| 完整事件路径 | 空间 | 生命周期 | 参数 |
| --- | --- | --- | --- |
| `event:/Master_FX/Explode/explode_big` | 3D | 单次 | `Distance`、`interior` |
| `event:/Master_FX/Explode/explode_huge` | 3D | 单次 | `Distance`、`interior` |
| `event:/Master_FX/Explode/explode_medium` | 3D | 单次 | `Distance`、`interior` |
| `event:/Master_FX/Explode/explode_small` | 3D | 单次 | `Distance`、`interior` |

#### `event:/Master_FX/Flyby`

| 完整事件路径 | 空间 | 生命周期 | 参数 |
| --- | --- | --- | --- |
| `event:/Master_FX/Flyby/flyby_cars` | 3D | 单次 | 无 |
| `event:/Master_FX/Flyby/flyby_large` | 3D | 单次 | 无 |
| `event:/Master_FX/Flyby/flyby_large_slow` | 3D | 单次 | 无 |
| `event:/Master_FX/Flyby/flyby_medium` | 3D | 单次 | 无 |
| `event:/Master_FX/Flyby/flyby_rocket` | 3D | 单次 | 无 |
| `event:/Master_FX/Flyby/flyby_small` | 3D | 单次 | 无 |

#### `event:/Master_FX/Shells_Hit/Big`

| 完整事件路径 | 空间 | 生命周期 | 参数 |
| --- | --- | --- | --- |
| `event:/Master_FX/Shells_Hit/Big/hit_armour` | 3D | 单次 | `Distance`、`interior` |
| `event:/Master_FX/Shells_Hit/Big/hit_ground` | 3D | 单次 | `Distance`、`interior` |
| `event:/Master_FX/Shells_Hit/Big/hit_water` | 3D | 单次 | `Distance`、`interior` |

#### `event:/Master_FX/Shells_Hit/Explode`

| 完整事件路径 | 空间 | 生命周期 | 参数 |
| --- | --- | --- | --- |
| `event:/Master_FX/Shells_Hit/Explode/he_big` | 3D | 单次 | `Distance`、`interior` |
| `event:/Master_FX/Shells_Hit/Explode/he_small` | 3D | 单次 | `Distance`、`interior` |

#### `event:/Master_FX/Shells_Hit/Medium`

| 完整事件路径 | 空间 | 生命周期 | 参数 |
| --- | --- | --- | --- |
| `event:/Master_FX/Shells_Hit/Medium/hit_armour` | 3D | 单次 | `Distance`、`interior` |
| `event:/Master_FX/Shells_Hit/Medium/hit_ground` | 3D | 单次 | `Distance`、`interior` |
| `event:/Master_FX/Shells_Hit/Medium/hit_water` | 3D | 单次 | `Distance`、`interior` |

#### `event:/Master_FX/Shells_Hit/Small`

| 完整事件路径 | 空间 | 生命周期 | 参数 |
| --- | --- | --- | --- |
| `event:/Master_FX/Shells_Hit/Small/hit_armour` | 3D | 单次 | `Distance`、`interior` |
| `event:/Master_FX/Shells_Hit/Small/hit_ground` | 3D | 单次 | `Distance`、`interior` |
| `event:/Master_FX/Shells_Hit/Small/hit_water` | 3D | 单次 | `Distance`、`interior` |

### M1A2 SEP

bank ID 为 `machine_max:abrams`。

#### `event:/Abrams`

| 完整事件路径 | 空间 | 生命周期 | 参数 |
| --- | --- | --- | --- |
| `event:/Abrams/tank_drive_abrams` | 3D | 持续 | `broken`、`Distance`、`interior`、`load`、`load_trigger`、`rpm` |
| `event:/Abrams/tank_start_abrams` | 3D | 单次 | `Distance`、`interior` |
| `event:/Abrams/tank_stop_abrams` | 3D | 单次 | `Distance`、`interior` |

#### `event:/Abrams/Armament`

| 完整事件路径 | 空间 | 生命周期 | 参数 |
| --- | --- | --- | --- |
| `event:/Abrams/Armament/main_gun_abrams` | 3D | 单次 | `Distance`、`interior` |
| `event:/Abrams/Armament/mg_127_abrams` | 3D | 持续 | `Distance`、`firing`、`interior`、`overheat` |
| `event:/Abrams/Armament/mg_762_abrams` | 3D | 持续 | `Distance`、`firing`、`interior`、`overheat` |
| `event:/Abrams/Armament/reload_main_abrams` | 2D | 单次 | 无 |
| `event:/Abrams/Armament/reload_mg_abrams` | 2D | 单次 | 无 |

## 配置结构与继承

`sounds.fmod` 按用途指定事件，同一组共用一个 `bank`。事件路径须以 `event:/` 开头，字段大小写以事件目录为准。

| 配置归属 | FMOD 字段 | 配置要求 |
| --- | --- | --- |
| 引擎 | bank、drive、start、stop | bank 必填，drive／start／stop 至少一项 |
| 变速箱 | bank、shift | 两项必填，升挡与降挡共用单次事件 |
| 汽车／摩托车／履带车控 | bank、handbrake | 两项必填，手刹拉起与释放共用单次事件 |
| 电机 | bank、drive | 两项必填 |
| 发射器 | bank、fire | 两项必填，机枪实例用 firing 参数控制开火形态 |
| 装弹机 | bank、reload | 两项必填 |
| 材料 | bank | 为材料音效预留，填写 fmod 时 bank 必填 |

变速箱的 sounds 配置示例：

```json
{
  "sounds": {
    "fmod": {
      "bank": "machine_max:basic_vehicle_sounds",
      "shift": "event:/Default_Vehicles/Transmission/Gears/gears_rattle"
    }
  }
}
```

地面车控的 sounds 配置示例，汽车、摩托车和履带车共用此结构：

```json
{
  "sounds": {
    "fmod": {
      "bank": "machine_max:basic_vehicle_sounds",
      "handbrake": "event:/Default_Vehicles/Cabin_Movements/lever_pull"
    }
  }
}
```

引擎配置示例，放在子系统静态属性中。drive 用于持续工况，start 与 stop 用于启动和停机单次音效。

```json
{
  "sounds": {
    "fmod": {
      "bank": "machine_max:basic_vehicle_sounds",
      "drive": "event:/Default_Vehicles/Engine/Tanks/tank_drive_uni",
      "start": "event:/Default_Vehicles/Engine/Tanks/tank_start_uni",
      "stop": "event:/Default_Vehicles/Engine/Tanks/tank_stop_uni"
    }
  }
}
```

材料配置示例，放在 materials 下的材料定义中，预留 bank 供材料音效扩展使用。履带型号通过 material 引用该材料，轮胎通过 wheel 碰撞箱的 material 引用。

```json
{
  "sounds": {
    "fmod": {
      "bank": "machine_max:mfx"
    }
  }
}
```

履带型号的定制示例，放在 fittings 下的履带型号中。只修改材料音效的 bank，命中音继承所引用的材料。

```json
{
  "material": "machine_max:track_steel",
  "overwrite": {
    "sounds": {
      "fmod": {
        "bank": "machine_max:mfx"
      }
    }
  }
}
```

命中音 hit_pen、hit_un_pen 与 FMOD bank 按字段合并。未写字段继承材料，空 sounds 对象继承整组音效。基础材料未配置 FMOD 时，覆写可单独添加 bank。

读取规则见 `src/main/java/io/github/sweetzonzi/machine_max/common/mech/vehicle/attr/MaterialAttr.java#SoundOverwriteAttr` 与 `src/main/java/io/github/sweetzonzi/machine_max/common/mech/vehicle/attr/MaterialAttr.java#MaterialFmodSoundAttr`。履带使用有效材料的入口为 `src/main/java/io/github/sweetzonzi/machine_max/common/mech/vehicle/track/TrackAttr.java#sounds`。

## 当前接入边界

发动机持续工况由 `src/main/java/io/github/sweetzonzi/machine_max/common/mech/subsystem/EngineSubsystem.java#tickFmodSound` 播放。以下定义同时配置同系列的 drive、start 和 stop，运行时持续工况读取 drive。除 M1A2 使用 `machine_max:abrams` 外，其余使用 `machine_max:basic_vehicle_sounds`。

| 发动机定义对应对象 | 持续工况事件 |
| --- | --- |
| AE86、AE86 全地形版、Wine Fox、Van、Builtin light_car_engine | `event:/Default_Vehicles/Engine/Cars/car_drive_4c` |
| Jeep、Builtin medium_car_engine | `event:/Default_Vehicles/Engine/Cars/car_drive_6c` |
| 大运、Builtin truck_diesel_engine | `event:/Default_Vehicles/Engine/Cars/car_drive_dsl` |
| 2S38、MT-LB、T-90A、59 式、K17、Sd.Kfz.234 | `event:/Default_Vehicles/Engine/Tanks/tank_drive_uni` |
| M1A2 | `event:/Abrams/tank_drive_abrams` |

各发动机将自身怠速到红线的区间归一化为 rpm=0～100，load 使用同步的油门开度。K17 与 Sd.Kfz.234 使用通用军用引擎音色。Builtin 的通用定义与各车专用定义分别配置，专用定义由相应车包的零件引用。单缸的 R700、Builtin motorcycle_engine 和 V8 的 Senna GTR、Builtin sport_car_engine 使用其采样或合成工况音；当前 bank 的汽车系列为 2c、4c、6c、dsl。

美系型谱包的发射器属性 sounds.fmod.fire 携带 `event:/Abrams/Armament/main_gun_abrams`、`event:/Abrams/Armament/mg_127_abrams` 或 `event:/Abrams/Armament/mg_762_abrams` 引用，发射器运行时的 FMOD 播放接线待实现。属性入口见 `src/main/java/io/github/sweetzonzi/machine_max/common/mech/subsystem/attr/static_attr/LauncherSubsystemStaticAttr.java#LauncherSoundAttr`。

轮胎与普通零件的地形接触已接入 tire_noise，轮胎尖叫已接入 tire_screech，参数与生命周期见本文“接触音效运行时接线”。

第一方变速箱和地面车控已配置 FMOD 换挡与手刹单次事件，触发规则见本文“换挡与手刹运行时接线”。

引擎的 sounds.fmod.start／stop、电机的 sounds.fmod.drive、装填的 sounds.fmod.reload 已提供配置入口，运行时触发接线待实现。材料的 sounds.fmod 预留 bank。履带持续运转音、传动打齿、炮塔和 Master FX 的 FMOD 运行时触发接线待实现。

## 可复核记录

资源从压缩包逐项提取，仓库内五个 bank 与压缩包对应条目的 SHA-256 一致。FMOD 在无声输出模式下加载五个 bank，枚举并核对 72 个事件路径及其 bank 归属。此检查验证了装载和事件元数据，工程师说明中的循环、尾音与混音行为尚待游戏内听感验收。

读取使用 `FMOD_Studio_Bank_GetEventList`、`FMOD_Studio_EventDescription_GetPath`、`FMOD_Studio_EventDescription_Is3D`、`FMOD_Studio_EventDescription_IsOneshot`、`FMOD_Studio_EventDescription_GetMinMaxDistance`、`FMOD_Studio_EventDescription_GetParameterDescriptionByIndex`，并用 `FMOD_Studio_System_GetEvent` 核对路径。只读、自动和离散类型来自参数 flags。

| 文件 | SHA-256 |
| --- | --- |
| `Desktop-11-11-14-51.zip` | `664f10bb63a75a5c5f6107345a62b3475f82d56f4addf4db355636af6cf6de96` |
| `Master.bank` | `3e8b27b3a6c7e83636b27bbc103a07972942869d816fec4f50d40951fbba400f` |
| `Master.strings.bank` | `dc8dc0cbda9b7839f6a1e7246d5919024739b5693b056e3b2e54325c98b55fd9` |
| `basic_vehicle_sounds.bank` | `cd0a07165d302bfc2670d7b2fb6fa655001d59c687fd86724995fd941852dcd6` |
| `mfx.bank` | `c0bbc98b31166a5ae5b12f696f3f9d061b363246333b580a7caa741d4198e0ce` |
| `abrams.bank` | `50b5470222117913fea7b6ae089f3c9b0349720f05869c7c7184d42a64e0d062` |
