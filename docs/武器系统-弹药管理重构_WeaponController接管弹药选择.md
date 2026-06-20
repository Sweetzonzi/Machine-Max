# 武器系统 — WeaponController 接管弹药管理重构

> 状态：设计完成 · 创建时间：2026-06-21 · 修订：2026-06-21（v4，优先级从 Launcher ammo_inputs 获取，简化 HUD 数据路径）
> 依赖：
> - `IAmmoSupplier.java` — 弹药供给接口
> - `IAmmoConsumer.java` — 弹药消费接口（仅 Launcher 实现）
> - `AmmoLoaderSubsystem.java` — 物理弹药装弹机（仅 `IAmmoSupplier`）
> - `RegenLoaderSubsystem.java` — 再生/能量装弹机（仅 `IAmmoSupplier`）
> - `LauncherSubsystem.java` — 发射器（`IAmmoConsumer`）
> - `WeaponControllerSubsystem.java` — 武器火控控制器

---

## 一、背景与问题

### 1.1 当前架构

现有的弹药供应链沿用 `Loader → Launcher` 的两层直连模型：

```
Loader (IAmmoSupplier) ──handshake──▶ Launcher (IAmmoConsumer)
    │                                       │
    │                               WeaponController 仅控制瞄准+开火
    │                               弹药切换：遍历 launcher → setCurrentSupplier(index+1)
```

Loader（`AmmoLoaderSubsystem` / `RegenLoaderSubsystem`）通过 `handShake()` 在配置的 discovery 频道发送空信号，Launcher 在 `onSignalUpdated("callback", sender)` 回调中调用 `addSupplier()` 注册该 Loader。

WeaponController 通过 `ammo_switch` 信号触发弹药切换，逻辑为：

```java
// WeaponControllerSubsystem.onPrePhysicsTick() 当前实现
if (ammoSwitchPressed && ammoSwitchCooldown <= 0) {
    for (LauncherSubsystem launcher : launchers.keySet()) {
        if (launcher.getSuppliers().size() > 1) {
            int current = launcher.getSuppliers().indexOf(launcher.getCurrentSupplier());
            int next = (current + 1) % launcher.getSuppliers().size();
            launcher.setCurrentSupplier(next);
        }
    }
}
```

此外，`AmmoLoaderSubsystem` 当前同时实现 `IAmmoSupplier` 和 `IAmmoConsumer`，支持"车体主弹仓 → 自动装弹机 → 发射器"的多级弹药链。但该设计引入额外复杂度且实际用例不明——**本次重构移除 AmmoLoader 的 IAmmoConsumer 实现，简化弹药链为单级。**

### 1.2 痛点

| # | 痛点 | 详细描述 |
|---|------|----------|
| 1 | **无法按弹种选择** | 弹药切换只能按 Loader 索引轮转。若 3 个 Loader 分别提供 APFSDS / HEAT / HE，从 APFSDS 切到 HE 需按两次按钮，且无法跳跃式指定弹种 |
| 2 | **弹仓内容不可见** | `AmmoLoaderSubsystem` 的 FIFO 容器内可能混装多种弹药，但 `getSuppliedType()` 只返回 FIFO 第一个类型，Controller 和 HUD 无从得知弹仓内的完整弹种分布 |
| 3 | **多 Launcher 行为不一致** | 武器控制器下有 N 个 Launcher，各自独立维护 `suppliers` 列表和 `selectedSupplierIndex`。切换弹药时每个 Launcher 各自轮转，可能切到不同弹种 |
| 4 | **不兼容弹药处理分散** | `handleIncompatibleAmmo()` 逻辑存在于 Launcher 内部——Launcher 负责兼容性校验、归还不兼容弹药、切换供给者。Controller 对弹药状态无全局视图 |
| 5 | **无优先级路由** | 无法区分"待发弹药架（0.5s 装填）"和"车体弹药架（2.0s 装填）"的优先级。Controller 不知道该优先从哪个 Loader 取弹 |

### 1.3 核心需求

- **弹种级选择**：用户选择一种弹药类型（如 HEAT），Controller 负责为所有 Launcher 找到最优的供给来源
- **弹药池聚合视图**：Controller 跨所有 Launcher + Loader 构建统一的弹药类型→数量映射，供 HUD 直接使用
- **自动最优路由**：Controller 在所有能提供选中弹种的 Loader 中自动选装填最快的。频道优先级由 Launcher 的 `ammo_inputs` 列表顺序隐式声明，频道信息用于 HUD 显示弹药来源
- **穿透式弹种查询**：`AmmoLoaderSubsystem` 需暴露弹仓内全部弹种的明细，而非仅 FIFO 第一发
- **物理拓扑保留**：不改变 Loader ↔ Launcher 的信号握手机制，保留"哪些 Loader 能喂哪些 Launcher"的物理连接关系

---

## 二、设计决策

### 2.1 移除多级弹药链（AmmoLoader 不再实现 IAmmoConsumer）

**决策**：`AmmoLoaderSubsystem` 移除 `IAmmoConsumer` 接口实现，删除所有上游供给者逻辑（`upstreamSuppliers`、`waitingUpstream`、`getCurrentUpstream`、`autoRequestUpstream` 等）。

**理由**：
- 多级弹药链（车体弹仓 → 自动装弹机 → 发射器）的实际用例不明，目前无 content pack 依赖此特性
- 玩家仍可通过手动搬运弹药来调整弹药架内容，无需自动化的多级补给
- 大幅简化 `AmmoLoaderSubsystem` 的代码和状态管理
- 消除 `IAmmoConsumer` 接口变更对 AmmoLoader 的波及

### 2.2 不采用「WeaponController 作为 IAmmoConsumer 代理」

经分析，若 Controller 同时实现 `IAmmoSupplier` 和 `IAmmoConsumer`，将形成双层装填计时器，导致弹药通路延迟翻倍。此外会丢失物理拓扑信息（如双管机炮弹链的 `Loader_A → Launcher_A`、`Loader_B → Launcher_B` 不可交叉约束）。

### 2.3 采用「Controller 作为协调者 + 拉取聚合」

```
                    ┌── WeaponController ──┐
                    │  弹药池聚合视图       │
                    │  弹种选择 → 路由决策  │
                    └──┬──────────────┬───┘
                       │ onTick 拉取   │ setCurrentSupplier(ref)
                       │ suppliers     │
                       ▼               ▼
Launcher_A ←──handshake──→ Loader_待发架 (APFSDS×2, HEAT×3)
Launcher_B ←──handshake──→ Loader_待发架 (同上, 多消费者)
Launcher_B ←──handshake──→ Loader_弹链B (APFSDS×50, 单消费者)
```

- Loader ↔ Launcher 的 3 步协议（`requestRound → isRoundReady → consumeReadyRound`）完全不动
- Controller 通过 **onTick 拉取** `launcher.getSuppliers()` 和 `launcher.getSupplierChannels()` 构建弹药池
- Controller 负责弹种选择的**路由决策**——为每个 Launcher 设置最优的 `currentSupplier`
- 用户只需选择弹种，Controller 自动按优先级频道 + 装填速度选择 Loader

### 2.4 多 Launcher 异种弹药场景的抽象边界

若需要两门炮分别使用不同弹种，应由 UGC 作者将其分成两个独立的 WeaponController。一个 WeaponController = 一个统一弹种选择——选 APFSDS 则 Launcher_2 无弹可打，自然停火。这不是设计缺陷，而是正确的抽象边界。

### 2.5 多 Controller 共享 Loader

一个 Loader 可以被多个 WeaponController 下的 Launcher 同时注册。Loader 通过 `canSupplyMultiple()` 声明是否支持多消费者。支持时，Loader 为每个消费者（Launcher）维护独立的装填计时器（现有 `reloadTimers` / `deliveryTimers` 已经是 per-consumer），无竞争条件。

若 `canSupplyMultiple() = false`，先到先得——第一个调用 `requestRound()` 的消费者获得服务，其他消费者等待。

---

## 三、接口变更

### 3.1 `IAmmoSupplier` — 新增 `getAmmoBreakdown()`

```java
/**
 * 弹药明细拆解。<br>
 * 返回此供给者当前持有的每种弹药类型及其可用数量。
 * AmmoLoader 遍历 FIFO 容器统计，RegenLoader 报告其固定类型。
 *
 * @return 弹种 → 可用数量，空映射表示无弹药
 */
default Map<ProjectileType, Integer> getAmmoBreakdown() {
    ProjectileType type = getSuppliedType();
    if (type == null) return Map.of();
    int count = getRemainingCount();
    return count > 0 ? Map.of(type, count) : Map.of();
}
```

`AmmoLoaderSubsystem` 重写此方法，遍历 `SimpleContainer` 统计每种弹药的 ItemStack 数量。

### 3.2 `IAmmoConsumer` — 修改

```java
// 修改 setCurrentSupplier：由 int index 改为直接引用
void setCurrentSupplier(@Nullable IAmmoSupplier supplier);

// 新增：获取按频道分组的供给者映射，供 Controller 拉取
Map<String, List<IAmmoSupplier>> getSupplierChannels();

// addSupplier 原有方法改为 default 转发到带频道名的重载
default void addSupplier(IAmmoSupplier supplier) {
    addSupplier(supplier, "unknown");
}

// 新增重载：带频道名的注册
void addSupplier(IAmmoSupplier supplier, String channelName);
```

**影响范围**：
- `LauncherSubsystem` — 唯一实现者，需适配全部新方法
- `AmmoLoaderSubsystem` — 不再实现 `IAmmoConsumer`，不受影响
- Loader 侧回调 — 调用 `consumer.addSupplier(this, channelName)` 而非 `consumer.addSupplier(this)`

### 3.3 `LauncherSubsystem` — 变更清单

| 变更 | 说明 |
|------|------|
| `suppliers: List<IAmmoSupplier>` + `selectedSupplierIndex: int` | → `supplierChannels: LinkedHashMap<String, List<IAmmoSupplier>>` + `currentSupplier: IAmmoSupplier`（LinkedHashMap 保证迭代顺序 = ammo_inputs 声明顺序） |
| `addSupplier(IAmmoSupplier)` | 移除；实现 `addSupplier(IAmmoSupplier, String)` |
| `setCurrentSupplier(int)` | → `setCurrentSupplier(IAmmoSupplier)` |
| `getSuppliers()` | 保留，返回所有频道的扁平列表（兼容 `IAmmoConsumer` 接口） |
| `handleIncompatibleAmmo()` | **移除**（见 §3.3.1） |
| `cachedSummaries` + `refreshCachedSummaries()` | **移除** — Controller 提供统一视图 |
| `getSupplierSummaries()` override | **移除** — Controller 接管。同时 `IAmmoConsumer` 接口上的 `SupplierSummary` / `SupplierSummaries` record 和 `getSupplierSummaries()` default 方法可移除或标记 `@Deprecated` |
| `onSignalUpdated()` 中回调处理 | 因 Loader 不再实现 IAmmoConsumer，回调仅来自 Loader handshake，调用 `addSupplier(sender, channelName)` |

#### 3.3.1 `handleIncompatibleAmmo` 处理

保留 `tryLoadChamber()` 中的 `canAccept()` 校验作为防御性编程，但不兼容时不再执行"退弹→轮转→重试"逻辑。改为：

```java
// tryLoadChamber() 中
if (supplier.isRoundReady(this)) {
    ProjectileType offered = supplier.consumeReadyRound(this);
    if (offered != null && canAccept(offered)) {
        chamberedType = offered; reloading = false; return true;
    } else {
        // 防御性处理：Controller 不应选不兼容的 Loader
        MachineMax.LOGGER.warn("Launcher {} 收到不兼容弹药 {}，归还并等待 Controller 重路由", name, offered);
        if (supplier.canEject()) supplier.returnRound(offered);
        reloading = false;
        return false;
    }
}
```

Controller 在下个 tick 的 `routeAmmoToLaunchers()` 中会重新路由。

### 3.4 `WeaponControllerSubsystem` — 新增/变更

**新增字段：**

```java
/** 用户当前选择的弹种注册名，null = 无选择（不装填但可发射膛内已有弹药） */
@Nullable
private ResourceLocation selectedProjectileType = null;

/** 
 * 聚合弹药池快照。<br>
 * key = 弹种注册名，value = 提供该弹种的所有 Loader 条目（按装填速度升序）。
 * 载具结构变化时由 onVehicleStructureChanged 触发完整重建，volatile 保证跨线程可见。
 */
private volatile Map<ResourceLocation, List<LoaderEntry>> ammoPool = Map.of();

/** Loader 条目（弹药池视图中的元素，供 HUD 和路由使用） */
public record LoaderEntry(
    IAmmoSupplier loader,
    int availableCount,       // 该 Loader 中此弹种的可用数量
    int reloadTimeTicks,      // 装填耗时
    String channel            // 所属频道名（HUD 显示用，不影响路由）
) {}
```

**新增方法：**

```java
/**
 * 弹药池完整重建。<br>
 * 由 onVehicleStructureChanged() 触发，遍历所有 launcher 的 supplierChannels 重建 ammoPool。
 * 此外每 10 tick 做一次轻量刷新（仅更新 availableCount）。
 */
private void rebuildAmmoPool() { ... }

/**
 * 在可用弹种列表中循环切换选中的弹种。<br>
 * 弹种按 ResourceLocation 自然顺序排列，保证可预测的切换顺序。
 */
private void cycleSelectedType() { ... }

/**
 * 为指定 launcher 查找能提供 selectedType 的最优 Loader。<br>
 * 按 launcher.supplierChannels 的迭代顺序（= LauncherStaticAttr.ammo_inputs 列表顺序），
 * 在第一个有匹配弹种的频道中选 reloadTimeTicks 最小的 Loader。
 * supplierChannels 使用 LinkedHashMap 保证迭代顺序与 ammo_inputs 一致。
 *
 * @return 最优 Loader，若无任何 loader 能提供选中弹种则返回 null
 */
@Nullable
private IAmmoSupplier findBestLoaderFor(LauncherSubsystem launcher) { ... }

/**
 * 弹药路由：为每个 launcher 设置最优 currentSupplier。<br>
 * 若膛内弹种与 selectedType 不匹配，自动退膛。
 * 在 onPrePhysicsTick 中瞄准/开火逻辑之前调用。
 */
private void routeAmmoToLaunchers() { ... }

/**
 * 获取当前弹药池的公开视图，供 HUD 读取。<br>
 * HUD 遍历 seat → activeWeaponControllers → 每个 WC 调用此方法聚合。
 *
 * @return 弹种注册名 → 按优先级排序的 Loader 条目列表
 */
public Map<ResourceLocation, List<LoaderEntry>> getAmmoPool() {
    return ammoPool;
}

/** 当前选中的弹种，null = 无选择 */
@Nullable
public ResourceLocation getSelectedProjectileType() {
    return selectedProjectileType;
}

/** 可用弹种列表（排序后），供 HUD 弹种选择菜单 */
public List<ResourceLocation> getAvailableProjectileTypes() {
    return ammoPool.keySet().stream().sorted().toList();
}
```

**`onTick()` 变更：**

- 每 10 tick 做一次轻量刷新（仅更新 `ammoPool` 中各 `LoaderEntry.availableCount`）
- 新增弹药切换输入处理（`ammoSwitchPressed` → `cycleSelectedType()`）
- 保留原有 `readInputSignals()`

**`onPrePhysicsTick()` 变更：**

- 在瞄准/开火逻辑之前，新增 `routeAmmoToLaunchers()` 调用
- 保留原有 `turrets` 驱动 + `launchers` 开火逻辑

### 3.5 `AmmoLoaderSubsystem` — 变更

**移除项：**
- `implements IAmmoConsumer` — 不再作为弹药消费者
- `upstreamSuppliers`、`selectedSupplierIndex`、`waitingUpstream` — 上游供给者逻辑
- `getCurrentUpstream()`、`autoRequestUpstream()` — 辅助方法
- `onTick()` 中向上游自动请求补充的逻辑（② 弹仓未满时自动向上游请求补充）
- `requestRound()` 中向上游请求补充的 fallback 逻辑

**新增/修改项：**

```java
// 仅实现 IAmmoSupplier
public class AmmoLoaderSubsystem extends BasicSubsystem implements IAmmoSupplier {

    @Override
    public Map<ProjectileType, Integer> getAmmoBreakdown() {
        Map<ProjectileType, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            ProjectileType type = getProjectileTypeFromItem(stack);
            if (type != null) {
                result.merge(type, stack.getCount(), Integer::sum);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    // requestRound 简化为仅在本地有弹药时启动装填计时器，无弹药直接返回 false
    @Override
    public boolean requestRound(IAmmoConsumer consumer) {
        if (reloadTimers.containsKey(consumer) || readyConsumers.contains(consumer)) {
            return true; // 幂等
        }
        if (!hasAmmo()) return false;
        reloadTimers.put(consumer, (int) (attr.staticAttribute.getReloadTime() * 20f));
        return true;
    }

    // onTick 简化为仅推进装填计时器（移除自动向上游请求补充逻辑）
    @Override
    public void onTick() {
        super.onTick();
        if (!isActive() || isDestroyed()) return;
        // 仅推进装填计时器
        Iterator<Map.Entry<IAmmoConsumer, Integer>> iter = reloadTimers.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<IAmmoConsumer, Integer> entry = iter.next();
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                iter.remove();
                readyConsumers.add(entry.getKey());
            } else {
                entry.setValue(remaining);
            }
        }
    }
}
```

**回调处理修改：**

```java
@Override
public SignalResult onSignalUpdated(String channelName, ISignalSender sender) {
    if (channelName.equals("callback") && sender instanceof IAmmoConsumer consumer) {
        if (consumer instanceof AbstractSubsystem sub) {
            if (sub.getOwner().getSubPart().getPart().assembly
                    != this.getOwner().getSubPart().getPart().assembly) {
                return SignalResult.PASS;
            }
        }
        // 从信号频道读取回调携带的频道名
        Object callbackValue = getSignalChannel("callback").get(sender);
        String discoveryChannel = callbackValue instanceof String s ? s : "unknown";
        consumer.addSupplier(this, discoveryChannel);
        return SignalResult.CONSUME;
    }
    return super.onSignalUpdated(channelName, sender);
}
```

### 3.6 `RegenLoaderSubsystem` — 变更

仅修改 `onSignalUpdated` 中的回调处理（与 AmmoLoader 相同的频道名传递逻辑），其余无需变更。default 实现的 `getAmmoBreakdown()` 已满足需求。

---

## 四、调用流程

### 4.1 handShake 发现流程（频道名传递）

```
Loader.handShake()
  │
  ├─ sendSignalToAllTargetsWithCallback("turret_ready_rack", EmptySignal.INSTANCE, false)
  │                                         ↑                                ↑
  │                                     discovery 频道名              回调返回频道名
  │
  ▼
Launcher（接受 "turret_ready_rack" 频道）
  │
  ├─ 收到 EmptySignal
  ├─ respondCallbackToSender 自动触发：
  │     callbackReturnsSignalValue = false
  │     → cbValue = "turret_ready_rack"  （频道名字符串）
  │     → 向 Loader 发送 callback 信号，值为 "turret_ready_rack"
  │
  ▼
Loader.onSignalUpdated("callback", launcher)
  │
  ├─ 从 getSignalChannel("callback").get(launcher) 读取 → "turret_ready_rack"
  ├─ 同 assembly 校验
  └─ launcher.addSupplier(this, "turret_ready_rack")
       │
       └─ Launcher: supplierChannels.put("turret_ready_rack", [..., this])
```

### 4.2 弹药池刷新

```
WeaponController.onVehicleStructureChanged()
  │
  └─ 完整重建 ammoPool：
       ammoPool = new HashMap<>()
       for (launcher : launchers.keys)：
            for (channel, loaderList : launcher.getSupplierChannels())：
                 for (loader : loaderList)：
                      for (type, count : loader.getAmmoBreakdown())：
                           ammoPool.computeIfAbsent(type.registryKey, _ → new ArrayList<>())
                                   .add(new LoaderEntry(loader, count, reloadTime, channel))
       每个弹种内：按 reloadTimeTicks 升序排序 LoaderEntry

WeaponController.onTick() (每 10 tick)
  │
  └─ 轻量刷新：仅更新每个 LoaderEntry.availableCount，不改结构
```

### 4.3 弹药切换 + 路由

```
用户按下弹药切换键
  │
  ▼
WeaponController.onPrePhysicsTick()
  │
  ├─ routeAmmoToLaunchers()：
  │    for (launcher : launchers.keys)：
  │       if (selectedType == null)：
  │           launcher.setCurrentSupplier(null)  // 不清膛，可发射膛内已有弹药
  │           continue
  │       best = findBestLoaderFor(launcher)
  │       if (best == null)：
  │           // 该 launcher 没有能提供选中弹种的 loader → 退膛
  │           if (launcher.getChamberedType() != null) launcher.ejectRound()
  │           launcher.setCurrentSupplier(null)
  │       else if (launcher.getCurrentSupplier() != best)：
  │           // 膛内弹种不匹配 → 退膛后由 Launcher 下次 tryLoadChamber 从新 supplier 取
  │           if (launcher.getChamberedType() != null
  │               && !launcher.getChamberedType().getRegistryKey().equals(selectedType))：
  │               launcher.ejectRound()
  │           launcher.setCurrentSupplier(best)
  │
  ├─ findBestLoaderFor(launcher)：
  │    按 launcher.supplierChannels 迭代顺序（= ammo_inputs 声明顺序）：
  │      在该频道的所有 loader 中：
  │        - getAmmoBreakdown() 包含 selectedType
  │        - reloadTimeTicks 最小
  │      若找到 → 立即返回（高优先级频道优先）
  │    return null
  │
  └─ 继续原有瞄准 + 开火逻辑...
```

**关于退膛后弹药去向**：`launcher.ejectRound()` 尝试归还给当前 supplier（`returnRound` 放入第一个空槽位）。这是预期行为——退弹回到原弹药架，不丢失。若弹药架满则 `returnRound` 静默跳过（后续可扩展为生成 ItemEntity）。

### 4.4 Launcher 的 ammo 逻辑（精简后）

```
Launcher.onTick()
  │
  ├─ if (chamberedType == null)：tryLoadChamber()
  │    │
  │    ├─ supplier = getCurrentSupplier()
  │    ├─ if (supplier == null)：return false
  │    ├─ if (supplier.isRoundReady(this))：
  │    │    type = supplier.consumeReadyRound(this)
  │    │    if (canAccept(type))：chamberedType = type; reloading = false
  │    │    else：
  │    │         // 防御性处理 → warn + 归还（见 §3.3.1）
  │    ├─ else if (!reloading)：
  │    │    supplier.requestRound(this); reloading = true
  │    │
  │    └─ return chamberedType != null
  │
  ├─ 开火循环（不变）
  │    每发后：tryRequestNextRound()
  │
  └─ 移除：handleIncompatibleAmmo、refreshCachedSummaries
```

---

## 五、刷新时机与性能

### 5.1 弹药池刷新策略

| 触发条件 | 操作 | 开销 |
|----------|------|------|
| suppliers 拓扑变化（组装/拆卸部件触发 `onVehicleStructureChanged`） | 重建 `ammoPool`，重新统计弹种分布 | O(N)，N = 所有 launcher 的所有 loader |
| 每 10 tick | 轻量刷新 availableCount（仅遍历 Loader 调 `getAmmoBreakdown()`） | O(N)，但每个 Loader 只遍历容器 |
| 其他 tick | 直接读取 `ammoPool` 缓存 | O(1) |

### 5.2 完整重建触发

`ammoPool` 完整重建由以下事件触发：
- `onVehicleStructureChanged()` — 载具部件组装/拆卸，supplier 拓扑已通过 `handShake` 更新
- 首次初始化（`onAttach()` 完成后）

数量变化（弹药消耗/再生）不触发完整重建，由每 10 tick 的轻量刷新覆盖。

---

## 六、HUD 适配

### 6.1 数据路径简化

**当前路径（两层 record 包装）：**

```
HUD → launcher.getSupplierSummaries()
        └─ 内部遍历 suppliers，构建 SupplierSummary record
        └─ 外层 SupplierSummaries record（含预计算 totalByType）
```

**新路径（直接从 Controller 读）：**

```
HUD → controller.getAmmoPool()          → Map<ResourceLocation, List<LoaderEntry>>
     controller.getSelectedProjectileType() → 当前选中弹种
     controller.getAvailableProjectileTypes() → 可用弹种列表（排序）
```

HUD 需要的所有数据已在 Controller 端聚合好：
- **弹种列表**：`ammoPool.keySet()`（可用弹种去重）
- **每弹种总数**：`sum(LoaderEntry.availableCount)`（跨所有 Loader）
- **当前选中**：`selectedProjectileType`
- **状态/进度**（逐发显示时）：通过 `LoaderEntry.loader.getStatus(launcher)` / `getReloadProgress(launcher)` 获取

### 6.2 显示策略选择

两种方案：

| 方案 | 显示内容 | 适用场景 |
|------|----------|----------|
| **(a) 按 WC 聚合** | 每个 WC 一行：`[APFSDS×15] [HEAT×8] [HE×3]`，选中弹种高亮 | 单 WC、简单布局 |
| **(b) 按 WC + Launcher 分组** | WC 标题行 + 每个 Launcher 显示当前装填状态和进度条 | 多 Launcher、需要装填进度反馈 |

建议默认采用 **(b)**（保持与现有 HUD 类似），但数据源改为 Controller：

```java
// AmmoHud.render() 新数据流
for (WeaponControllerSubsystem wc : wcs) {
    // WC 标题行
    lines.add(header(wc.name));
    
    for (var entry : wc.getLaunchers().entrySet()) {
        LauncherSubsystem launcher = entry.getKey();
        ProjectileType ammoType = launcher.getCurrentAmmoType();
        IAmmoSupplier supplier = launcher.getCurrentSupplier();
        
        // 从 Controller 的 ammoPool 获取该弹种总数
        ResourceLocation typeKey = ammoType != null ? ammoType.getRegistryKey() : null;
        int totalCount = 0;
        if (typeKey != null) {
            var entries = wc.getAmmoPool().get(typeKey);
            if (entries != null) {
                totalCount = entries.stream().mapToInt(LoaderEntry::availableCount).sum();
            }
        }
        
        // 当前数量和状态直接从 supplier 读取（不需要 SupplierSummary）
        int curCount = supplier != null && supplier.isRoundByRound() ? 1 
                       : (supplier != null ? supplier.getRemainingCount() : 0);
        IAmmoSupplier.SupplierStatus status = supplier != null 
                       ? supplier.getStatus(launcher) : IAmmoSupplier.SupplierStatus.EMPTY;
        float progress = supplier != null 
                       ? supplier.getReloadProgress(launcher) : 0f;
        
        lines.add(new Line(ammoLabel, curCount, totalCount, status, progress));
    }
}
```

### 6.3 多 WC 场景

HUD 遍历 `seat.getActiveWeaponControllers()`，每个 WC 独立管理自己的 `selectedProjectileType`。若需聚合多个 WC 的弹药池（如总览面板），可使用辅助方法：

```java
// WeaponControllerSubsystem 静态工具方法
public static Map<ResourceLocation, Integer> aggregateTotalByType(
        List<WeaponControllerSubsystem> controllers) {
    Map<ResourceLocation, Integer> result = new HashMap<>();
    for (var wc : controllers) {
        for (var entry : wc.getAmmoPool().entrySet()) {
            int sum = entry.getValue().stream().mapToInt(LoaderEntry::availableCount).sum();
            result.merge(entry.getKey(), sum, Integer::sum);
        }
    }
    return result;
}
```

### 6.4 移除项

`IAmmoConsumer` 上的 `SupplierSummary` / `SupplierSummaries` record 和 `getSupplierSummaries()` default 方法可移除（仅 Launcher 使用，Controller 接管后 HUD 不再需要）。若担心破坏性，保留但标记 `@Deprecated`。

---

## 七、实现步骤

| 步骤 | 文件 | 说明 |
|------|------|------|
| 1 | `AmmoLoaderSubsystem.java` | 移除 `IAmmoConsumer` 实现，删除所有上游供给者逻辑，简化 `requestRound()` 和 `onTick()`，修改 `onSignalUpdated()` 传频道名 |
| 2 | `RegenLoaderSubsystem.java` | 修改 `onSignalUpdated()` 传频道名（与 AmmoLoader 同步） |
| 3 | `IAmmoSupplier.java` | 新增 `getAmmoBreakdown()` default 方法 |
| 4 | `AmmoLoaderSubsystem.java` | 重写 `getAmmoBreakdown()`，穿透式遍历容器 |
| 5 | `IAmmoConsumer.java` | `setCurrentSupplier(int)` → `setCurrentSupplier(IAmmoSupplier)`；新增 `getSupplierChannels()`；`addSupplier(IAmmoSupplier)` 改为 default 转发 |
| 6 | `LauncherSubsystem.java` | `suppliers` + `selectedSupplierIndex` → `supplierChannels` + `currentSupplier`；移除 `handleIncompatibleAmmo`、`cachedSummaries`；简化 `tryLoadChamber()`；实现新接口方法 |
| 7 | `WeaponControllerSubsystem.java` | 新增 `selectedProjectileType`、`ammoPool`、`rebuildAmmoPool()`、`cycleSelectedType()`、`findBestLoaderFor()`、`routeAmmoToLaunchers()`；在 `onVehicleStructureChanged()` 中调用 `rebuildAmmoPool()`；修改 `onTick()`（每 10 tick 轻量刷新 + ammo 切换）和 `onPrePhysicsTick()`（路由 + 开火） |
| 8 | HUD 适配 | 武器 HUD 从 Controller 的 `getAmmoPool()` / `getSelectedProjectileType()` 读取 |
| 9 | 测试 | 多 Launcher 多 Loader 场景验证 |

---

## 八、风险评估

| 风险 | 等级 | 缓解措施 |
|------|------|----------|
| Loader 的 `addSupplier` 签名变更破坏现有实现 | 低 | `addSupplier(IAmmoSupplier)` 保留为 default 方法转发到 `addSupplier(supplier, "unknown")` |
| Controller 路由决策延迟导致膛室空转 | 低 | `routeAmmoToLaunchers()` 在 `prePhysicsTick` 执行，同 tick 内完成路由+开火。预请求机制保持不变 |
| 弹药池 `availableCount` 与实际不符 | 低 | 每 10 tick 轻量刷新。HUD 延迟上限 0.5s，可接受 |
| 多 Controller 共享同一 Loader 时的并发 | 低 | Loader 的 `reloadTimers` / `deliveryTimers` 已经是 per-consumer（Map key = IAmmoConsumer）。`canSupplyMultiple=false` 时先到先得 |
| 退膛时弹药架已满导致弹药丢失 | 极低 | 当前 `returnRound()` 在弹仓满时静默跳过。若需防止丢失，后续可扩展"生成 ItemEntity"逻辑 |
| 移除多级弹药链影响现有 content pack | 无 | 目前无 content pack 使用 `AmmoLoader` 的 `IAmmoConsumer` 特性 |
