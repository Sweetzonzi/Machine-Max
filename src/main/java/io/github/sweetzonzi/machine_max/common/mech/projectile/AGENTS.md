# common/mech/projectile/ — 数据驱动投射物系统

**范围**: 投射物类型定义、SoA 管理器、弹道投射物、制导与战斗部组件、BallisticsFramework 弹道集成。

**文件数**: 12 个 | **总行数**: 4403 行

## 结构

```
projectile/
├── BallisticProjectile.java          # 弹道投射物（989行）：SoA 运动 + 射线命中 + 伤害发起/伤害目标
├── ProjectileManager.java            # SoA 管理器（2110行），核心循环
├── ProjectileType.java               # 投射物类型抽象基类（294行），JSON Codec dispatch
├── type/
│   └── BallisticProjectileType.java  # 飞行弹丸类型（441行）：外弹道 / 终点效应 / 制导 / 战斗部
└── component/
    ├── effect/                       # 战斗部世界效果（WorldEffect / ExplosionWorldEffect ...）
    └── guidance/                     # 制导律（GuidanceLaw / PurePursuitGuidance ...）
```

## 快速定位

| 任务 | 文件 | 说明 |
|------|------|------|
| 新增投射物类型 | `type/BallisticProjectileType.java` | 在 `spark_modules/**/projectiles/*.json` 中定义，通过 ProjectileModule 加载 |
| 修改 SoA 循环 | `ProjectileManager.java` | 主循环：运动积分、碰撞检测、命中处理 |
| 修改命中逻辑 | `BallisticProjectile.java` | `onTerrainHit()` / `onPartHit()` / `onEntityHit()` |
| 修改弹道模型 | `BallisticProjectile.java` | 速度-伤害幂函数，速度-穿深模型 |
| 修改类型字段 | `type/BallisticProjectileType.java` | 外弹道、终点效应、制导、战斗部 |
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
**约束**：所有数组长度一致，通过共享 `count` 变量管理。新增字段必须在 `addProjectileInternal()` 和 `swapRemove()` 中同步操作。

## 弹道管线

```
物理线程（Bullet 步进，100Hz）：
  ProjectileManager.prePhysicsTick(physicsLevel)
    ├── forEachPrePhysicsTick()          # 各投射物 prePhysicsTick()
    └── updateProjectiles(physicsLevel)  # 仅服务端
          ├── 死条目清扫
          ├── 恢复命中待决条目（消费主线程写回的结果）
          ├── 运动积分（重力 / 阻力 / 推力 / 制导，半隐式 Euler）
          ├── 命中检测（world.rayTest 与 DDA 逐方块展开）
          └── 命中分派 → BF 管线；结果入队（命中同步包 / 战斗部起爆请求）

主线程（20tps）：
  ProjectileManager.preTick()
    ├── tickAndPreTick()      # 寿命递减 + 各投射物 preTick()
    ├── tryRecreateEntities() # 重建因区块卸载丢失的 MMProjectileEntity
    └── clientExtrapolate()   # 仅客户端：5 子步自主外推
  ProjectileManager.postTick()
    ├── cleanOrphanedEntities()      # 延迟清理代理实体
    ├── flushProjectileEntities()    # 生成包（ProjectilesSpawnPayload）
    ├── flushPendingHitSyncs()       # 命中包
    ├── flushPendingDetonations()    # 战斗部世界效果
    ├── flushAuthoritativeState()    # 制导弹权威位姿快照
    └── postTickAndSync()            # 各投射物 postTick() + SoA 回写
```

## 约定

- **SoA 数组同步**：添加/删除投射物时，所有数组必须同步操作。
- **线程分离**：`updateProjectiles()` 在物理线程调用；`preTick()` / `postTick()` 在主线程。
- **命中队列**：物理线程入队到 `ConcurrentLinkedQueue`（`pendingHitSyncs` / `pendingDetonations`），主线程在 `flushPending*()` 中清空。
- **类型分派**：JSON 的 `"type"` 字段唯一取值为 `"ballistic"`，由 `ProjectileType.CODEC` 的 dispatch 路由到 `BallisticProjectileType`；`typeIndex` 索引到 `ProjectileManager.typeCache`（`BallisticProjectileType[]`）。
- **双重角色**：`BallisticProjectile` 既是伤害发起方（实现 `BFDamageHandler`），也是伤害目标（经 `DestroyableObject` 实现 `BFHurtTarget`）。
- **BallisticsFramework 集成**：使用 `BFDamageApi.hurt()`，上下文通过 `BFDamageContext.Builder` 构造。
- **曳光渲染**：客户端通过 `ClientProjectileRenderer` 读取 SoA 位置数组渲染。

## 反模式

- **严禁主线程直接写入 SoA 数组**：所有位置/速度变更必须在物理线程完成。
- **严禁破坏数组一致性**：添加/删除时漏操作一个数组 = 数据错位 Bug。
- **严禁在主线程调用 `updateProjectiles()`**：服务端积分必须在物理线程（Bullet 步进）内。
- **严禁绕过 BFHurtAPI**：统一弹道管线，不要直接调用 `entity.hurt()`。
- **严禁混 JME/JOML**：物理侧用 JME，渲染侧用 JOML。

## 参考文件

- `docs/wiki/4-物理与战斗机制/4.7-投射物.md` — 投射物系统现行说明
- `docs/武器系统-投射物拦截与受击判定计划.md` — 投射物拦截与受击判定计划
- `docs/武器系统-组件化投射物与类型体系设计.md` — 组件化投射物、引信与战斗部设计
- `docs/武器系统-制导组件实现备忘.md` — 制导组件实现备忘
- `docs/wiki/3-子系统详解/3.8-武器系统.md` — 武器子系统与弹药供给/装填
