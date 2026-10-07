# common/mech/projectile/ — 数据驱动投射物系统

**范围**: 投射物类型定义、SoA 管理器、弹道投射物、运动学触发体、制导与战斗部组件、BallisticsFramework 弹道集成。

**文件数**: 13 个 | **总行数**: 4963 行

## 结构

```
projectile/
├── BallisticProjectile.java          # 弹道投射物（1132行）：SoA 运动 + 命中解析 + 伤害发起/伤害目标
├── ProjectileManager.java            # SoA 管理器（2291行），三趟主循环
├── ProjectileHitBox.java             # 运动学触发体宿主（125行）：PhysicsHost + BFHitResolver
├── ProjectileType.java               # 投射物类型抽象基类（294行），JSON Codec dispatch
├── type/
│   └── BallisticProjectileType.java  # 飞行弹丸类型（552行）：外弹道 / 终点效应 / 受击 / 命中检测 / 制导 / 战斗部
└── component/
    ├── effect/                       # 战斗部世界效果（WorldEffect / ExplosionWorldEffect ...）
    └── guidance/                     # 制导律（GuidanceLaw / PurePursuitGuidance ...）
```

## 快速定位

| 任务 | 文件 | 说明 |
|------|------|------|
| 新增投射物类型 | `type/BallisticProjectileType.java` | 在 `spark_modules/**/projectiles/*.json` 中定义，通过 ProjectileModule 加载 |
| 修改 SoA 循环 | `ProjectileManager.java` | 三趟主循环：推进、触发体位姿同步、命中判定与分派 |
| 修改命中逻辑 | `BallisticProjectile.java` | `onTerrainHit()` / `onPartHit()` / `onEntityHit()` |
| 修改弹道模型 | `BallisticProjectile.java` | 速度-伤害幂函数，速度-穿深模型 |
| 修改受击语义 | `BallisticProjectile.java` | `getRHA()` / `getArmorLevel()` / `isArmorPenetrated()` / `hurt()`，配置来自 `vulnerability` 块 |
| 修改触发体 | `ProjectileHitBox.java` | 零质量运动学刚体；`syncPosition()` 由趟二调用 |
| 修改命中检测原语 | `type/BallisticProjectileType.java` | `hit_detection`（`ray` / `sweep`）与其在趟三的分派 |
| 新增制导律 | `component/guidance/` | 实现 `GuidanceLaw`，并在其 CODEC 的 dispatch 中注册 |
| 新增战斗部效果 | `component/effect/` | 实现 `WorldEffect`，并在其 CODEC 的 dispatch 中注册 |
| 集成 BallisticsFramework | `ProjectileManager.java` | BFHurtAPI 分步命中处理 |

## SoA 架构说明

`ProjectileManager` 使用 **Structure of Arrays** 而非传统的 Array of Structures：

```java
// SoA（当前实现）
float[] posX, posY, posZ;     // 位置
float[] velX, velY, velZ;     // 速度
int[] typeIndex;               // 投射物类型索引
// ... 更多原始数组

// swap-remove 删除：
// 将尾部元素覆盖到待删位置，count--，O(1) 无碎片
```

**优势**：缓存友好、批量操作高效、无 GC 压力。
**约束**：所有数组长度一致，通过共享 `count` 变量管理。新增字段必须在 `addProjectileInternal()`、`swapRemove()` 与 `ensureCapacity()` 中同步操作。

## 弹道管线

```
物理线程（Bullet 步进，100Hz）：
  ProjectileManager.prePhysicsTick(physicsLevel)
    ├── forEachPrePhysicsTick()          # 各投射物 prePhysicsTick()
    └── updateProjectiles(physicsLevel)  # 仅服务端，三趟
          ├── 趟一 推进：死条目清扫 → 暂停恢复 → 运动积分（重力/阻力/推力/制导）→ 写回 SoA
          ├── 趟二 同步：把持有触发体的条目按 SoA 位姿刷新物理体积
          └── 趟三 判定：命中查询（rayTest / sweepTest）→ 归一化 HitEntry → 排序 → 分派

主线程（20tps）：
  ProjectileManager.preTick()
    ├── tickAndPreTick()      # 寿命递减 + 各投射物 preTick()；寿命到期同样经 destroy() 出列
    ├── tryRecreateEntities() # 重建因区块卸载丢失的 MMProjectileEntity
    └── clientExtrapolate()   # 仅客户端：死条目清理扫描（保留一次供渲染）+ 5 子步自主外推
  ProjectileManager.postTick()
    ├── cleanOrphanedEntities()      # 延迟清理代理实体
    ├── flushProjectileEntities()    # 生成包（ProjectilesSpawnPayload）
    ├── flushPendingHitSyncs()       # 命中包
    ├── flushPendingDetonations()    # 战斗部世界效果
    ├── flushAuthoritativeState()    # 制导弹权威位姿快照
    └── postTickAndSync()            # 各投射物 postTick() + SoA 回写
```

趟二与趟三的先后顺序是硬约束：触发体的位姿必须在同一物理步的任何命中查询之前全部写完，否则攻击弹会按目标上一物理步的位置求交。

## 受击与拦截

投射物既是伤害发起方也是伤害目标，两个方向的开关彼此正交：

| 维度 | 由谁表达 | 取值与效果 |
|------|----------|-----------|
| 能否被别人击中损毁 | `vulnerability` 块的有无 | 有 → 持有 `ProjectileHitBox` 触发体、可掉 `durability`、耐久归零即损毁；无 → 不持有触发体、`getMaxDurability()` 为 1、`hurt` 恒 false |
| 自己如何探测别人 | `hit_detection` | `ray`（质心射线，命中阈值只有目标半径）/ `sweep`（自身半径球扫掠，阈值 = 攻击弹半径 + 目标半径） |

代码里的唯一判据是 `BallisticProjectile.isInterceptable()`（等价于 `getProjectileType().getVulnerability() != null`），触发体创建、HUD 与验证都走它。

`vulnerability.rha` 是硬门槛而非软阈值：命中管线先判穿甲，未击穿时默认的 `calculateFinalDamage` 返回 0，`hurt` 因此在 `amount <= 0f` 处早退，耐久不减少。要让某型拦截弹打得动某型目标，就把它配到能击穿对应厚度。

## 约定

- **SoA 数组同步**：添加/删除投射物时，所有数组必须同步操作。
- **线程分离**：`updateProjectiles()` 在物理线程调用；`preTick()` / `postTick()` 在主线程。
- **命中队列**：物理线程入队到 `ConcurrentLinkedQueue`（`pendingHitSyncs` / `pendingDetonations`），主线程在 `flushPending*()` 中清空。
- **类型分派**：JSON 的 `"type"` 字段唯一取值为 `"ballistic"`，由 `ProjectileType.CODEC` 的 dispatch 路由到 `BallisticProjectileType`；`typeIndex` 索引到 `ProjectileManager.typeCache`（`BallisticProjectileType[]`）。
- **双重角色**：`BallisticProjectile` 既是伤害发起方（实现 `BFDamageHandler`），也是伤害目标（经 `DestroyableObject` 实现 `BFHurtTarget`）。
- **触发体只在服务端创建**：客户端不建刚体；触发体随 `destroy()` 摘除，因此三条出列路径都收敛到同一个清理点。
- **碰撞组**：触发体是 `CollisionGroups.PROJECTILE`、`collideWith = NONE`。射线与扫掠查询**不检查碰撞掩码**，可见性由每个调用方自己的组白名单决定。
- **BallisticsFramework 集成**：使用 `BFDamageApi.hurt()`，命中目标经 `BFDamageApi.resolveHitTarget()` 解析，上下文通过 `BFDamageContext.Builder` 构造。
- **曳光渲染**：客户端通过 `ClientProjectileRenderer` 读取 SoA 位置数组渲染。
- **客户端死条目保留一 tick**：`deadRetained` 让死条目多留一次清理扫描，`ClientProjectileRenderer` 借此画出"出膛即命中销毁"那一发的曳光；保留时长由该标记而非 `lifetime` 决定——`tickAndPreTick()` 与 `clientExtrapolate()` 的寿命递减都被 `alive` 检查挡在死条目之外，死条目的寿命恒定不变，用"寿命已小于上限"作判据的条目会永久留在数组里。

## 反模式

- **严禁主线程直接写入 SoA 数组**：所有位置/速度变更必须在物理线程完成。
- **严禁破坏数组一致性**：添加/删除时漏操作一个数组 = 数据错位 Bug。
- **严禁在主线程调用 `updateProjectiles()`**：服务端积分必须在物理线程（Bullet 步进）内。
- **严禁把触发体建在客户端**：位姿无人刷新、判定无人查询，只是白占一个刚体。
- **严禁绕过 BFHurtAPI**：统一弹道管线，不要直接调用 `entity.hurt()`。
- **严禁混 JME/JOML**：物理侧用 JME，渲染侧用 JOML。

## 参考文件

- `docs/wiki/4-物理与战斗机制/4.7-投射物.md` — 投射物系统现行说明
- `docs/plan/已实现/武器系统-投射物拦截与受击判定计划.md` — 投射物拦截与受击判定计划
- `docs/plan/进行中/武器系统-组件化投射物与类型体系设计.md` — 组件化投射物、引信与战斗部设计
- `docs/plan/已实现/武器系统-制导组件实现备忘.md` — 制导组件实现备忘
- `docs/wiki/3-子系统详解/3.8-武器系统.md` — 武器子系统与弹药供给/装填
