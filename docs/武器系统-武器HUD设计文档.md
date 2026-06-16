# 武器系统 — 武器 HUD 设计文档

> 版本 1.0 · 2026-06-16

---

## 一、设计范围

本 HUD 仅负责**武器弹药状态**的展示与交互。以下功能由其他 HUD 负责，不在本文范围：

| 功能 | 负责模块 |
|------|----------|
| 测距、瞄准误差指示 | SightHud / 炮镜 UGC 分划 |
| 炮管位置十字线 | SightHud |
| 目标锁定状态 | 另行设计 |
| 炮塔角度、射击模式图标 | 另行设计（或 UGC 3D HUD） |

---

## 二、信息架构

### 2.1 层级树

```
当前激活的控制组
 │
 ├─ WeaponController "ctrl_main"          ← 武器站（一组共用瞄准目标的炮塔+发射器）
 │    ├─ Launcher "barrel_L"              ← 单个发射管/炮闩
 │    │    ├─ Loader "auto_L"             ← 当前选中的供给者
 │    │    ├─ Loader "backup_L"           ← 后备供给者
 │    │    └─ Loader "regen_L"            ← 后备供给者
 │    └─ Launcher "barrel_R"
 │         ├─ Loader "auto_R"             ← 当前选中
 │         └─ Loader "backup_R"           ← 后备
 │
 └─ WeaponController "ctrl_secondary"
      └─ Launcher "mg"
           └─ Loader "belt"               ← 唯一供给者
```

### 2.2 数据获取路径（无需新增网络包）

所有数据通过服务端已有的引用链直接获取：

```
WeaponControllerSubsystem.launchers       → Map<LauncherSubsystem, String>
  └─ LauncherSubsystem.suppliers          → List<IAmmoSupplier>
       └─ IAmmoSupplier.getLabel()        → 弹种标签
       └─ IAmmoSupplier.getRemainingCount() → 剩余数量
       └─ IAmmoSupplier.getSuppliedType() → ProjectileType (可空)
```

`WeaponControllerSubsystem` 的 `turrets` / `launchers` 字段已经是 public getter（`@Getter`），`LauncherSubsystem` 的 `suppliers` / `chamberedType` / `reloading` 也已可访问。不需要新增网络载荷——数据随现有的 `VehicleData` 同步机制一起到客户端。

---

## 三、UI 元素定义

### 3.1 武器站卡片（WeaponStation）

一个可折叠的容器。折叠态显示摘要，展开态显示内部发射器列表。

| 元素 | 内容 | 数据来源 |
|------|------|----------|
| 标题 | 武器站名称（如 "主炮"） | WeaponController 的名称（通过握手时登记的 key） |
| 射击模式标签 | SALVO / RIPPLE | `attr.staticAttribute.getDefaultFireMode()` |
| 瞄准状态 | "2/3 已瞄准" | 遍历 `launchers` 调用 `isAimedAt()` |
| 展开 / 折叠箭头 | ▶ / ▼ | 交互控制 |

### 3.2 发射器行（LauncherRow）

武器站展开后显示，一个 Launcher 一行。

| 元素 | 内容 | 数据来源 |
|------|------|----------|
| 发射器名 | "L" / "R" / "MG" | Launcher 的注册名 |
| 膛内状态 | 弹种图标 + 或 "空" | `chamberedType` (null=空膛) |
| 装填指示 | 旋转动画 / "装填中..." | `reloading == true && chamberedType == null` |
| 展开 / 折叠箭头 | ▶ / ▼ | 仅当 `suppliers.size() > 1` 时显示 |

### 3.3 供给者列表（LoaderList）

发射器展开后显示，列出一个 Launcher 连接的全部供给者。
数据来源：`launcher.getSupplierSummaries()` 一次性返回。

| 元素 | 内容 | 数据来源（SupplierSummary 字段） |
|------|------|----------|
| 选中标记 | 高亮边框 / ✓ 图标 | `isSelected` |
| 弹种标签 | "APFSDS" / "HEAT" 等 | `label` |
| 余量数字 | "12" 或 "∞" | `remaining`（-1 显示为 ∞） |
| 容量 | "/15" | `capacity` |
| 进度条 | `████████░░` 比例填充 | `remaining / capacity` |
| 状态文字 | "冷却中..." / "无弹药" | `status` + `statusProgress` |

### 3.4 全局元素

| 元素 | 内容 | 数据来源 |
|------|------|----------|
| 武器站数量 | "武器站: 2" | 缓存列表大小 |

---

## 四、交互模型

### 4.1 展开 / 折叠

```
点击武器站标题 → 展开/折叠 Launcher 列表
点击发射器行   → 展开/折叠 Loader 列表（仅多供给者时有效）
```

展开状态为纯客户端 UI 状态，不需要网络同步。

### 4.2 弹药切换

```
点击某个 Loader（非当前选中）→ 发送弹药切换指令
```

**服务端路径**：
```
客户端 → 网络载荷 → 服务端 →
  LauncherSubsystem.setCurrentSupplier(clickedIndex)
```

需要的变动：
- 新增 `AmmoSwitchPayload` 网络包（携带 launcherId + supplierIndex）
- `WeaponControllerSubsystem` 需要能按标识找到特定 Launcher
- 或直接操作 `LauncherSubsystem`（如果座椅能和 Launcher 直接通信）

**简化方案（先不做 per-Launcher 切换）**：
维持现有 `"ammo_switch"` 频道统一轮询，仅展示但不可点击。后续按需扩展。

### 4.3 面板开关

按指定按键（如 `G` 或 TAB 面板中的武器页签），用 AUI `ApricityUI.getDocument()` 模式打开 HTML 面板。

面板打开时暂停游戏输入（AUI 默认行为），关闭后恢复。

---

## 五、所需工具方法

### 5.1 设计思路

HUD 不应自己遍历 `Launcher.getSuppliers()` 然后做 `instanceof AmmoLoader / RegenLoader` 分支。
更好的方式是由消费者（`IAmmoConsumer`，即 Launcher）自行聚合下游供给者数据，
对外暴露统一的摘要列表。新增 `SupplierSummary` 记录和接口默认方法。

### 5.2 IAmmoSupplier — 新增 `getCapacity()` + `getStatus()` + `getReloadProgress()`

供给者自行报告状态，消除消费者侧的 `instanceof` 分支。

```java
/** 供给者的最大容量。-1 表示无容量概念（默认）。 */
default int getCapacity() {
    return -1;
}

/** 供给者当前工作状态（per-consumer） */
default SupplierStatus getStatus(IAmmoConsumer consumer) {
    return getRemainingCount() > 0 ? SupplierStatus.READY : SupplierStatus.EMPTY;
}

/**
 * 装填/再生进度（0~1）。
 * @param consumer 请求进度的消费者。AmmoLoader 按 consumer 区分装填计时器；
 *                RegenLoader 忽略此参数（共享弹药池）。
 */
default float getReloadProgress(IAmmoConsumer consumer) {
    return 0f;
}
```

AmmoLoaderSubsystem 和 RegenLoaderSubsystem 各自重写：

```java
// AmmoLoaderSubsystem
@Override public int getCapacity() { return attr.staticAttribute.getMagazineCapacity(); }
// getAmmoCount() 改为 public
@Override
public SupplierStatus getStatus(IAmmoConsumer consumer) {
    if (reloadTimers.containsKey(consumer)) return SupplierStatus.RELOADING;
    if (getAmmoCount() <= 0) return SupplierStatus.EMPTY;
    return SupplierStatus.READY;
}
@Override
public float getReloadProgress(IAmmoConsumer consumer) {
    Integer remaining = reloadTimers.get(consumer);
    if (remaining == null) return 0f;
    int total = attr.staticAttribute.getReloadTimeTicks();
    return total > 0 ? 1f - (float) remaining / total : 1f;
}

// RegenLoaderSubsystem
@Override public int getCapacity() { return attr.staticAttribute.getMagazineCapacity(); }
@Override
public SupplierStatus getStatus(IAmmoConsumer consumer) {
    if (isBatchReloading) return SupplierStatus.RELOADING;
    if (ammoCount <= 0) return SupplierStatus.EMPTY;
    return SupplierStatus.READY;
}
@Override
public float getReloadProgress(IAmmoConsumer consumer) {
    int cap = attr.staticAttribute.getMagazineCapacity();
    if (cap <= 0) return 0f;
    if (isBatchReloading) {
        // batchCooldownTicks 需公开或用 tickCount 推算
        return ...;
    }
    // 非 batch 模式：当前余量比例
    return (float) ammoCount / cap;
}
```

### 5.3 IAmmoConsumer — 新增 `SupplierSummary` + 聚合方法

`getSupplierSummaries()` 不再需要 instanceof，直接调用供给者自身的方法。

```java
/**
 * 供给者摘要，供 HUD 一次性获取全部弹药信息。
 * 字段值来自 IAmmoSupplier 自身报告的方法，不做 instanceof 分支。
 */
record SupplierSummary(
    /** 弹种标签，如 "APFSDS"、"HEAT" */
    String label,
    /** 当前弹药数量 */
    int remaining,
    /** 最大容量，-1 表示无上限 */
    int capacity,
    /** 是否为当前选中的供给者 */
    boolean isSelected,
    /** 供给者当前状态（由供给者自行报告） */
    SupplierStatus status,
    /** 状态数值（装填进度 0~1、再生进度 0~1 等） */
    float statusProgress
) {}

/** 供给者工作状态 */
enum SupplierStatus {
    /** 正常可用 */
    READY,
    /** 弹药耗尽 */
    EMPTY,
    /** 装填/冷却中 */
    RELOADING
}

/**
 * 获取所有已连接供给者的摘要列表，供 HUD 直接使用。
 * 数据全部来自 IAmmoSupplier 自身方法，不依赖具体实现类。
 */
default List<SupplierSummary> getSupplierSummaries() {
    List<SupplierSummary> result = new ArrayList<>();
    IAmmoSupplier selected = getCurrentSupplier();
    for (IAmmoSupplier supplier : getSuppliers()) {
        result.add(new SupplierSummary(
             supplier.getLabel(),
             supplier.getRemainingCount(),
             supplier.getCapacity(),
             supplier == selected,
             supplier.getStatus(this),
             supplier.getReloadProgress(this)
         ));
    }
    return result;
}
```

### 5.4 WeaponControllerSubsystem — 快捷查询

```java
/** 获取已瞄准的发射器数量 */
public int getAimedLauncherCount(Vec3 target, float tolDeg) {
    int count = 0;
    for (LauncherSubsystem launcher : launchers.keySet()) {
        if (!launcher.isDestroyed() && launcher.isActive() && launcher.isAimedAt(target, tolDeg)) {
            count++;
        }
    }
    return count;
}
```

### 5.5 LauncherSubsystem 公开弹药容量（可选）

如果弹药信息完全由 Loader 侧提供，则 Launcher 自身只需要 `chamberedType` + `reloading`。

---

## 六、客户端架构

### 6.1 类结构

```
client/render/gui/hud/
├── CustomHud.java              (已有) — 不修改
├── SightHud.java               (已有) — 不修改
└── WeaponHudManager.java       (★ 新建) — 武器弹药面板管理器

client/render/gui/
└── VehicleInfoPanelManager.java (已有) — 参考其 AUI Document 用法
```

`WeaponHudManager` 职责：
1. 通过 `AbstractControllableSubsystem` 的握手机制发现同载具内全部 `WeaponControllerSubsystem`
2. 每 tick 从 `WeaponController → Launcher → Loader` 链读取最新数据
3. 构建/更新 HTML DOM 元素
4. 处理点击事件（展开/折叠、弹药切换）

### 6.2 握手发现

**不需要新增专门的发现频道配置**。直接利用已有的控制组输出目标来握手：
aim input 和 fire input 频道的接收者就是 WeaponController。

**利用回调的信号值携带控制组名**。`sendSignalToAllTargetsWithCallback(channel, signalValue, true)`
中 `callbackReturnsSignalValue=true` 会让回调把信号值（而非频道名）回传给发送者。
因此以控制组名作为信号值，即可在回调中精确知道 WeaponController 属于哪个控制组。

握手由 `AbstractControllableSubsystem` 在 `onAttach` / `onVehicleStructureChanged` 时
通过 `ControlGroupSet` 中的控制组逐组执行。

```java
/**
 * 按控制组索引的武器控制器发现结果。
 * key = 控制组名（baseGroup 固定 "base"，子组用 group.name），
 * value = 该控制组通过握手发现的 WeaponController 列表。
 *
 * 控制组切换时，HUD 只显示当前激活组对应的 WeaponController。
 */
@Getter
protected final Map<String, List<WeaponControllerSubsystem>> weaponControllersByGroup =
        new ConcurrentHashMap<>();

/** 武器控制器发现握手：遍历所有控制组的全部输出频道，通过回调发现 WeaponController */
protected void weaponControllerDiscoveryHandshake() {
    weaponControllersByGroup.clear();
    // baseGroup 固定 key = "base"
    discoverWeaponControllersForGroup("base", controlGroupSet.baseGroup);
    // 子控制组 key = 组名
    for (ControlGroup group : controlGroupSet.groups) {
        discoverWeaponControllersForGroup(group.name, group);
    }
}

private void discoverWeaponControllersForGroup(String groupKey, ControlGroup group) {
    weaponControllersByGroup.put(groupKey, new ArrayList<>());

    // 收集该控制组全部输出频道
    Set<String> channels = new HashSet<>();
    channels.addAll(group.moveTargets.keySet());
    channels.addAll(group.viewTargets.keySet());
    channels.addAll(group.regularTargets.keySet());
    for (ControlBinding binding : group.bindings) {
        channels.add(binding.channel);
    }

    // ★ 以控制组名作为信号值，callbackReturnsSignalValue=true，
    //    回调中拿到的 callbackValue 就是 groupKey，从而精确归类
    for (String channel : channels) {
        if (channel.isEmpty()) continue;
        sendSignalToAllTargetsWithCallback(channel, groupKey, true);
    }
}
```

在 `onSignalUpdated("callback", sender)` 中追加：

```java
if (sender instanceof WeaponControllerSubsystem wc) {
    if (wc.getOwner().getSubPart().getPart().assembly
            == this.getOwner().getSubPart().getPart().assembly) {
        // 从回调信号值中取出控制组名
        Object callbackValue = getSignalChannel("callback").get(sender);
        if (callbackValue instanceof String groupKey) {
            List<WeaponControllerSubsystem> list =
                    weaponControllersByGroup.computeIfAbsent(groupKey, k -> new ArrayList<>());
            if (!list.contains(wc)) {
                list.add(wc);
            }
        }
    }
}
```

**为什么能精确归类**：`sendSignalToAllTargetsWithCallback(channel, groupKey, true)` 中
`callbackReturnsSignalValue=true`，基础设施 `respondCallbackToSender` 会将信号值
（即 `groupKey`）作为回调内容回传。每个控制组的回调都携带各自的组名，
因此无需猜测归属。一个 WeaponController 可能被多个控制组共用（如 baseGroup
和某个子组同时指向同一控制器），此时它会自然出现在多个组列表中。`getActiveWeaponControllers()`
合并去重后正确。

**按控制组获取当前激活组的 WeaponController**——合并 baseGroup 与激活子组：

```java
/** 获取当前激活控制组对应的 WeaponController 列表（HUD 显示用）。
 *  合并 baseGroup 和当前激活子组的 WeaponController，去重。 */
public List<WeaponControllerSubsystem> getActiveWeaponControllers() {
    List<WeaponControllerSubsystem> base =
            weaponControllersByGroup.getOrDefault("base", List.of());
    List<WeaponControllerSubsystem> result = new ArrayList<>(base);

    ControlGroup active = controlGroupSet.getActiveGroup();
    if (active != null) {
        List<WeaponControllerSubsystem> sub =
                weaponControllersByGroup.getOrDefault(active.name, List.of());
        for (WeaponControllerSubsystem wc : sub) {
            if (!result.contains(wc)) result.add(wc);
        }
    }
    return result;
}
```

### 6.3 客户端数据获取

WeaponControllerSubsystem、LauncherSubsystem 等子系统同时存在于逻辑服务端和逻辑客户端。
客户端侧的副本通过 Minecraft 实体数据同步机制（`VehicleData` → `PartData` → `SubPartData` 链）
已经与服务端保持同步。因此客户端 `WeaponHudManager` 可以直接从客户端侧的
`AbstractControllableSubsystem.weaponControllersByGroup` 拿到引用，读取其
`launchers` map，遍历获取 loader 数据——这些都是已同步的客户端侧对象，数据可靠。

**无需新增网络载荷**。现有的实体同步管道已覆盖弹药余量（NBT 持久化在
`AmmoLoaderSubsystem.saveData/loadData`、`RegenLoaderSubsystem.saveData/loadData`）、
装填状态、膛内弹药等字段。

---

## 七、显示联动

### 7.1 控制组切换时

`ControlGroupSet.activate(index)` 切换激活的子控制组时：

- 调用 `getActiveWeaponControllers()` 获取当前激活组对应的 WeaponController 列表
- `WeaponHudManager` 刷新显示，只渲染激活组关联的武器站
- 未激活组对应的武器站不显示

映射关系在握手时已建立（`weaponControllersByGroup` 按组名索引），切换时直接按组名取即可，无需额外计算。

### 7.2 载具结构变化时

触发 `onVehicleStructureChanged()` → 清空 `weaponControllersByGroup` → 重新握手发现。

### 7.3 退出载具时

面板自动关闭（类似 `VehicleInfoPanelManager` 检测 `SeatSubsystem` 不再存在的逻辑）。

---

## 八、纳入 MoLang 的考量

武器弹药数据也可能在 3D HUD 模型（如座舱仪表）中引用。若需要，可在 `HudMolangContext` 中新增查询：

| MoLang 表达式 | 返回值 | 说明 |
|---------------|--------|------|
| `weapon.ammo_count` | float | 当前武器站的总弹药（所有 Launcher 当前 loader 的余量之和） |
| `weapon.ammo_capacity` | float | 总容量 |
| `weapon.reloading` | 0/1 | 是否有 Launcher 在装填 |
| `weapon.selected_ammo_label` | string | 当前弹种标签 |

此项属于可选的增强，不在首期实现范围。

---

## 九、首期实现范围

| 内容 | 优先级 |
|------|--------|
| 握手发现 WeaponController | 高 |
| WeaponHudManager 基础框架 + AUI HTML 面板 | 高 |
| 武器站卡片 + 发射器行（折叠/展开） | 高 |
| Loader 弹药余量 + 进度条显示 | 高 |
| 装填中指示 | 中 |
| 弹药切换交互（点击 Loader） | 低（先只读） |
| MoLang 集成 | 低 |
| 专用服务器网络同步 | 低 |

---

## 十、涉及文件与改动量

✓ = 已完成，☐ = 待实施

| 文件 | 改动 | 说明 | 状态 |
|------|------|------|------|
| `IAmmoSupplier.java` | +15行 | `getCapacity()` + `getStatus()` + `getStatusProgress()` default 方法 | ☐ |
| `IAmmoConsumer.java` | +50行 | `SupplierSummary` record + `SupplierStatus` enum + `getSupplierSummaries()` default | ☐ |
| `AmmoLoaderSubsystem.java` | +3行 | `getCapacity()` 重写 + `getAmmoCount()` 改 public | ☐ |
| `RegenLoaderSubsystem.java` | +10行 | `getCapacity()` + `getStatus()` + `getStatusProgress()` 重写 | ☐ |
| `WeaponControllerSubsystem.java` | +18行 | `getAimedLauncherCount()`（含参数 null 守卫） | ✓ |
| `AbstractControllableSubsystem.java` | +75行 | `weaponControllersByGroup` 字段 + 按组握手机制 + `discoverWeaponControllersForGroup()` + `getActiveWeaponControllers()` + `onSignalUpdated` 回调处理 + `onAttach`/`onVehicleStructureChanged` 接入 | ✓ |
| `SeatSubsystem.java` | 0行 | 无需修改——握手调用已在 `AbstractControllableSubsystem.onAttach()`/`onVehicleStructureChanged()` 中添加，子类继承自动生效 | — |
| `client/.../WeaponHudManager.java` | 新建，~300行 | HTML 面板管理器 | ☐ |
| `resources/.../weapon_panel.html` | 新建，~80行 | AUI HTML 骨架 | ☐ |
| `resources/.../weapon_panel.css` | 新建，~100行 | 面板样式 | ☐ |

### 说明

- **SeatSubsystem 无需修改**：武器控制器握手调用直接加在 `AbstractControllableSubsystem.onAttach()` 和 `onVehicleStructureChanged()` 中，所有子类（包括 SeatSubsystem）自动继承。
- **已编译验证**：`./gradlew build` 通过，0 错误 0 警告。

总计：已实施 ~122 行改动（5 个源文件修改）。
