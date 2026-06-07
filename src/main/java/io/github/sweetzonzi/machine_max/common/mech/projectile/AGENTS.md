# common/mech/projectile/ — 数据驱动投射物系统

**范围**: 投射物类型定义、SoA 管理器、刚体/质点投射物、BallisticsFramework 弹道集成。

**文件数**: 6 个 | **总行数**: ~2200 行

## 结构

```
projectile/
├── IProjectile.java            # 投射物接口（334行），定义生命周期方法
├── ProjectileManager.java      # SoA 管理器（971行），核心循环
├── ProjectileType.java         # 投射物类型定义（JSON Codec）
├── ProjectileTypeEnum.java     # 投射物类型枚举（point / rigid）
├── PointProjectile.java        # 质点投射物（射线检测命中）
└── RigidProjectile.java        # 刚体投射物（Bullet SphereCollisionShape）
```

## 快速定位

| 任务 | 文件 | 说明 |
|------|------|------|
| 新增投射物类型 | `ProjectileType.java` | 在 JSON 中定义，通过 ProjectileModule 加载 |
| 修改 SoA 循环 | `ProjectileManager.java` | 主循环：运动积分、碰撞检测、命中处理 |
| 修改命中逻辑 | `IProjectile.java` | `onHitEntity()` / `onHitBlock()` 回调 |
| 修改弹道模型 | `IProjectile.java` | 速度-伤害幂函数，速度-穿深模型 |
| 修改刚体碰撞 | `RigidProjectile.java` | Bullet SphereCollisionShape，物理碰撞回调 |
| 修改质点碰撞 | `PointProjectile.java` | 射线检测穿透逻辑 |
| 集成 BallisticsFramework | `ProjectileManager.java` | BFHurtAPI 分步命中处理 |

## SoA 架构说明

`ProjectileManager` 使用 **Structure of Arrays** 而非传统的 Array of Structures：

```java
// SoA（当前实现）
float[] posX, posY, posZ;     // 位置
float[] velX, velY, velZ;     // 速度
int[] typeIds;                 // 投射物类型索引
// ... 更多原始数组

// swap-remove 删除：
// 将尾部元素覆盖到待删位置，size--，O(1) 无碎片
```

**优势**：缓存友好、批量操作高效、无 GC 压力。
**约束**：所有数组长度一致，通过共享 `size` 变量管理。新增字段必须在 `addProjectile()` 和 `removeProjectile()` 中同步操作。

## 弹道管线

```
物理线程（Bullet 步进）：
  ProjectileManager.tickPhysics(dt)
    ├── 运动积分（欧拉/Verlet）
    ├── 碰撞检测（射线/球体）
    └── 命中回调入队

主线程（20tps）：
  ProjectileManager.handleAccumulatedHits()
    ├── BFDamageApi.hurt() 分步处理
    │   ├── BFHurtTarget（完整管线）
    │   └── BFHurtTarget + BFArmorMaterial（双层管线）
    └── 特效/音效生成
```

## 约定

- **SoA 数组同步**：添加/删除投射物时，所有数组必须同步操作。
- **线程分离**：`tickPhysics()` 在物理线程调用；`handleAccumulatedHits()` 在主线程。
- **命中队列**：物理线程入队到 `ConcurrentLinkedQueue<HitEvent>`；主线程清空。
- **类型安全**：`typeIds` 索引到 `List<ProjectileType>`，`ProjectileTypeEnum` 区分 point/rigid。
- **BallisticsFramework 集成**：使用 `BFDamageApi.hurt()`，上下文通过 `BFDamageContext.Builder` 构造。
- **曳光渲染**：客户端通过 `ClientProjectileRenderer` 读取 SoA 位置数组渲染。

## 反模式

- **严禁主线程直接写入 SoA 数组**：所有位置/速度变更必须在物理线程完成。
- **严禁破坏数组一致性**：添加/删除时漏操作一个数组 = 数据错位 Bug。
- **严禁在主线程调用 `tickPhysics()`**：必须在物理线程（Bullet 步进）内。
- **严禁绕过 BFHurtAPI**：统一弹道管线，不要直接调用 `entity.hurt()`。
- **严禁混 JME/JOML**：物理侧用 JME，渲染侧用 JOML。

## 参考文件

- `docs/武器系统-数据驱动投射物设计文档.md` — 完整设计文档
- `docs/武器系统-弹药供给与装填系统设计文档.md` — 弹药供给与 Launcher/AmmoLoader 集成
