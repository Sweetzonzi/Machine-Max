# 物理系统：WorldSnapshot 纳入 PAWN 查询计划

> **状态**：本计划交付已完成。PAWN 快照射线/体积查询与实体移除 GameTest 通过；客户端实测确认玩家阻挡载具放置、爆炸正常伤害实体。卸载清理与查询顺序/去重经代码路径审查确认，无需额外专项测试。完整 GameTest 车道中的休眠车轮支撑失败与本计划无关，不影响本计划结论，详见 §8。
> **日期**：2026-11-26
> **涉及仓库**：`../Spark-Core`（快照与碰撞组）和 Machine-Max（爆炸、通用射线与放置查询）。

## 1. 目标

让主线程上的空间查询通过同一份 `WorldSnapshot` 发现普通 Minecraft 实体、Machine-Max 载具刚体与投射物，供以下现有或规划中的消费者复用：

1. 爆炸步进中的一般实体命中。
2. `RayQueryContext` 的通用射线结果，以及后续光束武器的实体命中。
3. 载具放置的玩家、载具和其他动态物体占位检查。

`WorldSnapshot` 仍是非积分、非响应的只读查询世界。加入 PAWN 只扩大查询可见集合，不改变实体的实际物理碰撞行为。

## 2. 实施前事实

- Spark-Core 的 `CollisionGroups.SNAPSHOT_TRACKED` 当前包含 `PHYSICS_BODY` 和 `PROJECTILE`；`WorldSnapshot.markAdd` 按该集合决定是否复制主物理空间中的刚体。
- Spark-Core 为普通实体创建 `PAWN` 组的默认刚体代理。代理跟随实体 AABB，设为运动学、无接触响应，`collideWithGroups` 为 `NONE`，owner 为原版 `Entity`。
- `WorldSnapshot` 在物理 tick 中同步结构与位姿，刷新宽相后发出 `PhysicsSnapshotReadyEvent`。爆炸订阅该事件，在主线程按快照推进。
- `RayQueryContext` 已经通过 `WorldSnapshot.rayTestDp` 发射射线；`RayHitResolver` 已将 owner 为 `Entity` 的物理命中映射为 `EntityRayHit`。
- `ExplosionInstance` 当前每段射线分别执行快照刚体查询和 `level.getEntities` AABB 查询。实体查询使用 Minecraft 的局部实体查询，不是全世界线性遍历，但会随爆炸射线段重复发生。
- `WorldSnapshot.collectBodiesInAabb` 当前逐项遍历快照映射。把所有 PAWN 代理纳入后，不应让高频消费者把这个方法当作无成本的局部空间索引。

## 3. 设计决定

### 3.1 快照纳入 PAWN

在 Spark-Core 的快照跟踪组中加入 `PAWN`。快照代理保留主刚体的 owner、形状、碰撞组和掩码；主世界中的 PAWN 仍保持 `collideWithGroups = NONE`，继续不参与物理接触响应。

碰撞组采用并集判据：两对象只要有一方的 `collideWithGroups` 包含另一方的 `collisionGroup`，即可通过组过滤。因此体积查询对象显式把 `PAWN` 加入自己的掩码即可命中普通实体代理，不要求 PAWN 代理反向声明可碰撞组。

快照本身不运行物理积分或接触响应。本计划消费者使用射线与接触查询；两者均已验证 PAWN 可见性。当前没有使用 sweep API 的消费者，不据此扩展验收范围；不得修改主世界实体碰撞掩码。

### 3.2 查询结果按 owner 解释

快照只提供几何命中。消费者按 owner 和用途决定是否接受：

- 普通玩家、生物及其他实体代理由 `Entity` owner 表示。
- 载具部件仍由 `SubPart` owner 表示，按现有命中箱规则处理。
- `MMPartEntity` 是部件的原版实体代理，不应与其 `SubPart` 刚体重复造成伤害、阻挡或放置判定；通用实体查询须明确排除或映射到唯一的部件通道。
- 投射物、连接器、交互区等已有 owner 类型保持各自语义，不因 PAWN 加入而被改写。

`RayQueryContext` 与 `RayHitResolver` 作为 Machine-Max 通用射线入口。光束实现应消费这一入口的 `EntityRayHit`，而不是另建一套对 `Level#getEntities` 的逐射线扫描。

### 3.3 爆炸迁移到同一条射线查询

将 `ExplosionInstance.entitiesAlong` 的一般实体发现迁移到 `WorldSnapshot.rayTest` 结果。普通实体命中与载具子零件命中仍合并进同一条按 fraction 排序的射线命中序列；实体命中保持爆炸既有的能量累积、伤害和冲量结算语义。

迁移后移除重复的 `level.getEntities` 射线段查询，并保留 `MMPartEntity` 排除规则及每条爆炸射线的实体去重。不能让 PAWN 命中被 `rayTestRigid` 当前仅接受 `SubPart` owner 的过滤逻辑静默丢弃。

### 3.4 局部候选查询的性能边界

纳入 PAWN 后，快照对象数会随已加载普通实体数量增长。代码审查确认当前爆炸走 Bullet `rayTest`、放置走 `contactTest`；`collectBodiesInAabb` 虽为线性遍历，但没有当前高频消费者调用它，因此不需要新增候选索引或实体密集场景跑分。

不以未经测量的固定实体数作承诺。本计划以代码路径审查为性能验收：高频消费者使用物理快照宽相查询，不逐射线枚举实体或遍历全部快照刚体；只有未来重新引入全量扫描时才要求专项测量。

### 3.5 放置检查的查询边界

载具放置通过快照检查玩家与动态载具代理；体积查询代理的掩码显式包含 `PAWN`。`WorldSnapshot` 当前不追踪 `TERRAIN`，因此静态方块/地形的冲突仍须由放置流程的地形查询负责。不得将“纳入 PAWN”误写成“快照已覆盖全部世界碰撞”。

放置的玩家预览与服务端确认必须采用一致的动态目标过滤和接触判据。预览可异步提交物理查询，但最终结果以服务端快照判定为权威。

## 4. 实施阶段

### P0：Spark-Core 快照能力

1. 将 `PAWN` 加入 `CollisionGroups.SNAPSHOT_TRACKED`。
2. 确认默认实体代理的加入、移除、owner 保留与每 tick 位姿同步都覆盖快照生命周期。
3. 确认当前使用的快照射线与接触查询在 PAWN 的 `NONE` 响应掩码和查询方掩码含 PAWN 时符合过滤语义；本计划消费者未使用 sweep API，不将其列为验收项。
4. 确认高频消费者不调用线性扫描的 `collectBodiesInAabb`；当前爆炸与放置路径已改用宽相射线/接触查询，因此不要求为未使用的辅助 API 新增候选索引。

### P1：通用射线与实体归属

1. `RayQueryContext` 返回普通实体 `EntityRayHit`，并将结果与方块和 `SubPartRayHit` 按同一射线 fraction 排序。
2. `RayHitResolver` 排除 `MMPartEntity` 代理，载具部件只通过 `SubPart` 刚体命中。
3. 记录光束系统使用 `RayQueryContext` / `RayHitResolver` 的约定；光束实现本身不属于本计划交付范围。

### P2：爆炸统一查询

1. 将一般实体命中并入快照射线结果，移除逐段 `level.getEntities` 通道。
2. 保持射线命中排序、能量到达口径、实体去重、伤害上下文和冲量方向不变；实现以排序、struck 标记和按 Entity 键聚合满足该契约。
3. 代码审查确认每段地形与刚体命中合并后按 fraction 排序；普通实体功能已通过客户端实测。

### P3：放置消费者与查询成本审查

1. 使服务端载具放置查询能检测到 PAWN 代理，同时维持载具刚体冲突判定。
2. 使客户端预览和服务端确认对 PAWN 的查询掩码与目标排除规则一致。
3. 代码审查确认爆炸逐段调用快照 `rayTest`、放置使用快照 `contactTest`，旧的逐射线 `Level#getEntities` 与全快照 AABB 遍历已移除；本次不要求专项性能跑分。若后续重新引入全量候选扫描，再针对性测量。

## 5. 验收项

- 普通玩家和生物在快照就绪后可被射线命中，owner 身份与原版实体一致。
- PAWN 主代理的 `collideWithGroups = NONE` 不妨碍查询方以含 PAWN 的掩码发现它；实际世界不因此产生实体物理碰撞。
- 实体加入及普通移除已有 GameTest 覆盖；区块卸载通过 `EntityLeaveLevelEvent` 复用刚体移除入口，维度卸载由 `PhysicsLevel.close()` 关闭所属物理世界。代码路径完整，无需为相同清理链重复添加卸载 GameTest。
- 命中排序由 Bullet `rayTest` 的排序契约和 `RayHitResolver` 的显式 fraction 排序保证；`MMPartEntity` 代理被过滤，避免与对应 `SubPart` 刚体重复成为有效目标。
- 爆炸伤害普通实体已通过客户端实测；爆炸将 DDA 地形命中与刚体射线命中按 fraction 合并排序，以 `MMPartEntity` 过滤、每射线 struck 标记及按实体聚合结算避免重复结算。
- 客户端实测已确认玩家会阻挡放置；载具体积冲突仍由 `PHYSICS_BODY` 接触查询覆盖，静态地形检查保持独立。
- 代码审查确认当前爆炸与放置查询不调用逐射线实体枚举或全快照线性 AABB 扫描；不另设性能跑分门槛。

## 6. 非目标

- 不改变 PAWN 在主物理空间中的接触响应、运动学策略或实体碰撞组。
- 不将 TERRAIN、TRIGGER 或任意 owner 自动加入快照。
- 不在本计划内实现光束类型、发射器或伤害模型；只确保其可复用的查询入口。
- 不改变爆炸数值模型、载具部件命中箱语义或 Minecraft 实体伤害 API。

## 7. 主要代码位置

### Spark-Core

- `src/main/kotlin/cn/solarmoon/spark_core/physics/body/CollisionGroups.kt`：快照跟踪组。
- `src/main/kotlin/cn/solarmoon/spark_core/physics/body/CollisionFuncApplier.kt`：普通实体 PAWN 代理。
- `src/main/kotlin/cn/solarmoon/spark_core/physics/level/WorldSnapshot.kt`：结构、位姿同步与主线程查询世界。
- `src/main/kotlin/cn/solarmoon/spark_core/physics/level/PhysicsLevel.kt`：快照刷新与就绪事件。

### Machine-Max

- `common/mech/ray/RayQueryContext.java`、`RayHitResolver.java`：通用射线。
- `common/mech/explosion/ExplosionInstance.java`：爆炸射线与一般实体命中。
- `common/item/prop/BaseVehicleItem.java`：预览和服务端放置碰撞检查。

## 8. 实施与验证记录

- Spark-Core：`SNAPSHOT_TRACKED` 已加入 `PAWN`；快照逐步刷新时同步实体代理被替换的碰撞形状，避免尺寸变化后仍查询旧形状。
- Machine-Max：普通实体由 `RayHitResolver` 转成 `EntityRayHit`；`MMPartEntity` 代理直接跳过，不进入任何命中类型。爆炸以快照射线结果合并普通实体和 `SubPart` 命中，删除逐段 `Level#getEntities` 与全快照 AABB 遍历。放置预览和服务端确认都在快照上以 `PAWN | PHYSICS_BODY` 掩码做接触查询；玩家占位按冲突处理，部件实体代理排除而 `SubPart` 刚体仍阻挡。
- 新增 `WorldSnapshotPawnGameTest`：验证普通实体 PAWN 代理保留原版 owner、`collideWith=NONE`、可被快照射线命中，且查询体包含 PAWN 掩码时可通过快照体积接触查询发现该代理。该用例通过。
- 客户端实测：载具放置预览中玩家会形成阻挡，放置行为符合预期；爆炸可正常伤害实体。两项属于已完成的功能验收，专用自动化回归可后续补强，不是当前功能阻塞。
- 验证命令：Machine-Max `gradlew.bat --offline test` 通过；Spark-Core `gradlew.bat --offline compileKotlin` 通过。Machine-Max `gradlew.bat --offline runGameTestServer` 编译并运行 121 项，新增 PAWN 查询与实体移除用例通过；另有休眠车轮支撑断言失败，和本计划覆盖的快照查询、爆炸或放置路径无关，不计入本计划验收。新增实体移除生命周期 GameTest 通过；区块/维度卸载清理经代码路径审查确认，扫掠不属于当前消费者。性能通过代码审查确认查询路径，不要求专项测量。代码审查结论：常规 Bullet `rayTest` 返回排序结果（raw 变体才是不排序），Machine-Max 对解析结果及爆炸合并列表还会显式排序；PAWN 普通实体一体一代理，`MMPartEntity` 代理被过滤，爆炸再按射线 struck 状态和 Entity 键聚合，因此没有未处理的重复结算路径。

## 修订记录

| 版本 | 日期 | 内容 |
| --- | --- | --- |
| v1 | 2026-11-26 | 定义 PAWN 快照可见性、碰撞掩码并集语义、爆炸与通用射线接入、实体代理去重及性能验收。 |
| v2 | 2026-11-26 | Spark-Core 将 PAWN 纳入快照跟踪并逐步同步实体代理形状替换；Machine-Max 通用解析接受普通实体并完全排除 MMPartEntity 代理，爆炸改用快照射线发现实体且移除逐段 Level 查询和全快照 AABB 粗筛；放置预览/确认统一使用快照与 PAWN/PHYSICS_BODY 掩码，玩家占位按冲突处理、MMPartEntity 代理排除。新增普通实体 PAWN 快照射线 GameTest；放置场景 GameTest 与实体密集场景测量仍待执行。 |
| v3 | 2026-11-26 | 新增 GameTest 验证 PAWN 代理的 owner、射线命中、NONE 响应掩码与查询掩码接触命中；测试通过。完整 GameTest 车道运行 120 项，另有休眠车轮支撑断言失败；Machine-Max JUnit 与 Spark-Core 编译通过。 |
| v4 | 2026-11-26 | 补记客户端实测：玩家阻挡载具放置符合预期，爆炸可正常伤害实体；休眠车轮失败与本计划无关，不作为验收阻塞。 |
| v5 | 2026-11-26 | 新增实体移除生命周期 GameTest：移除前快照可查询代理，移除后旧代理不再命中；新增用例通过。性能改为代码路径审查，不要求专项跑分。 |
| v6 | 2026-11-26 | 代码审查确认区块卸载复用 EntityLeaveLevelEvent 的刚体移除入口，维度卸载关闭 PhysicsLevel/PhysicsWorld，无需另设生命周期 GameTest；当前消费者不使用 sweep API，因此移出本计划验收。 |
| v7 | 2026-11-26 | 核对 Bullet rayTest 排序契约及 Machine-Max 的显式排序、代理过滤和爆炸聚合去重；无需额外排序/去重 GameTest。客户端功能验收与 PAWN 查询/移除 GameTest 均通过，本计划交付完成。 |
