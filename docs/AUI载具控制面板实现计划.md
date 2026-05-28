# AUI 载具控制面板 — 实现计划

**版本**：1.1  
**日期**：2026-05-28  
**目标**：使用 AUI（ApricityUI）实现 Machine-Max 的载具信息查看与控制系统可视化编辑

---

## 一、背景与现状

### 1.1 已有基础设施

| 组件 | 文件 | 状态 |
|------|------|------|
| AUI 集成 | 已作为 NeoForge 依赖引入 | ✅ 就绪 |
| 控制面板管理器 | [`VehicleInfoPanelManager.java`](file:///d:/Files/Project_MinecraftMods/Machine-Max/src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/VehicleInfoPanelManager.java) | ⚠️ 概念验证阶段，使用 mock 数据 |
| AUI 模板 | [`vehicle_info_panel.html`](file:///d:/Files/Project_MinecraftMods/Machine-Max/src/main/resources/assets/apricityui/apricity/machine_max/vehicle_info_panel.html) | ⚠️ 仅实现单页：控制组卡片列表 + 简版编辑 |
| 按键绑定 | `RawInputHandler.java:335` — Tab 键打开/关闭面板 | ✅ 就绪 |
| 控制系统数据模型 | `common/mech/control/` 全部类（ControlGroupSet / ControlGroup / ControlBinding / AbstractGuiAction 等） | ✅ 就绪 |
| 网络同步 | `ControlBindingPayload`、`ControlGroupSet.STREAM_CODEC` | ✅ 就绪 |
| 3D HUD 渲染基础设施 | `Hud3DContext` / `AssemblyHud3D`（零部件投影渲染） | ✅ 就绪（可复用投影逻辑） |

### 1.2 当前 VehicleInfoPanelManager 的实现方式（POC）

- **走轻量 AUI 模式**：POC 阶段通过 `ApricityUI.openScreen(path)` 打开纯 AUI 窗口，`Client.java` 全局事件订阅自动处理输入与渲染。**正式方案将替换为自定义 Screen**
- **纯 Java DOM 操作**：HTML 模板只有布局骨架 + CSS，所有卡片和数据通过 `Document.createElement()` / `querySelector()` / `innerText` / `addEventListener()` 动态构建
- **使用 mock 数据**：`FOR_GUI_TEST` 字段持有硬编码的测试 `ControlGroupSet`
- **交互方式**：AUI 的 `mousedown` / `mousemove` / `mouseup` 事件，不需要转发

### 1.3 技术结论

1. **不需要 JS / KubeJS**：AUI 的 Java DOM API 完整，所有交互可在 Java 中处理
2. **需要自定义 Screen 类**：当前 POC 通过 `ApricityUI.openScreen()` 打开的纯 AUI 窗口无法渲染 3D 预览和处理自定义鼠标输入。正式实现需 `VehicleControlScreen extends Screen`，在 Screen 中管理 AUI Document 生命周期并处理 3D 渲染
3. **AUI 的 Document 独立于 Screen**：`Document.create()` → 全局 tick 自动推进。Screen 只是外壳，AUI 的渲染和输入事件由 `Client.java` 全局订阅自动处理，Screen 无需逐个转发
4. **不需要 Container / Slot 绑定**：Tab 01-03 的控制 UI 不涉及物品栏。Tab 04 库存管理可选绑定
5. **文档生命周期**：`Document.create()` 注册到全局列表 → Screen 关闭时 `doc.remove()` 清理

---

## 二、目标范围

### 2.1 总体目标

将原型 `prototype/vehicle-ui/` 的 4 个 Tab 面板（除去 3D 预览）在 AUI 中实现，绑定真实控制系统数据。

### 2.2 四个 Tab 面板

```
┌──────────────────────────────────────────────────────────────────┐
│ Tab 01 载具概览（Vehicle Overview）                                │
│   ├── 控制组切换条（baseGroup + 子组横向卡片）                      │
│   ├── 载具状态（durability / energy / speed / heading / altitude）  │
│   ├── 警告信息列表                                                │
│   └── 当前控制模式                                                │
├──────────────────────────────────────────────────────────────────┤
│ Tab 02 设备控制（Device Control）                                  │
│   ├── 控制组切换条                                                │
│   ├── PULSE 按钮区（guiActions 中 type=PULSE）                    │
│   ├── TOGGLE 开关区（guiActions 中 type=TOGGLE）                  │
│   └── SLIDER 滑块区（guiActions 中 type=SLIDER）                  │
├──────────────────────────────────────────────────────────────────┤
│ Tab 03 编辑配置（Edit Configuration）— 最复杂                     │
│   ├── 左栏：控制组列表（含增删、重置按钮）                          │
│   ├── 中栏：组详情 + Key Bindings 列表 + GUI Items 列表            │
│   └── 右栏：选中项的详情编辑表单                                    │
├──────────────────────────────────────────────────────────────────┤
│ Tab 04 库存管理（Inventory）— ⏸️ 暂停                              │
│   └── ⚠️ 留空占位，后续版本实现                                    │
└──────────────────────────────────────────────────────────────────┘
```

### 2.3 不在此次范围和推迟项

- **3D 预览**：由 Screen 的 `renderBackground()` 阶段使用模型渲染管线实现（详见附录 A）
- **网络协议修改**：复用已有 `ControlBindingPayload` 等网络包
- **服务端逻辑修改**：`AbstractControllableSubsystem` 等不在此范围修改
- **Tab 04 库存管理**（⏸️ 暂停）：HTML 中保留空 page div 占位，`PanelInventory.java` 仅输出一行日志表明未实现。Tab 按钮禁用点击。后续版本再实现。

---

## 三、AUI 能力边界参考

> 本节是 AUI（ApricityUI）在 Machine-Max 面板开发中可用的能力清单，基于对该框架源码的完整调研。
> 底层渲染引擎：Java 实现 DOM/CSS 布局 + CPU 绘制（通过 `PoseStack`），非 WebView。

### 3.1 HTML 标签支持

| 标签 | 说明 | 本项目用途 |
|------|------|-----------|
| `body` / `div` / `span` | 通用块级和内联容器 | 布局骨架 |
| `input` | 单行文本输入、checkbox、radio，支持 type/placeholder/value/checked/maxlength | 编辑页表单 |
| `textarea` | 多行文本输入 | 描述字段 |
| `select` / `option` | 下拉选择器，点击 option 设置 value 并失焦 | 模式选择、类型选择 |
| `img` | 图片渲染 | 图标 |
| `sprite` | 精灵图动画，支持 src/steps/duration/loop/autoplay/fit | 可选动画 |
| `canvas` | 2D 渲染画布，完整 CanvasRenderingContext2D API | 线框预览（降级方案） |
| `slot` / `container` | 物品槽和容器绑定系统 | Tab 04 库存管理 |
| `translation` | 使用 Minecraft `Component.translatable()` 翻译文本 | i18n |

### 3.2 CSS 布局能力

| 布局模式 | 支持 | 说明 |
|---------|------|------|
| **Flexbox** | ✅ 完整 | `flex-direction` / `justify-content` / `align-items` / `gap` / `flex-grow/shrink` |
| **Grid** | ✅ 完整 | `grid-template-columns/rows`（仅 px/auto）、`grid-row/column` + span、gap |
| **Normal Flow** | ✅ | block（换行堆叠）、inline/inline-block（同行排列） |
| **Position** | ✅ | `static` / `relative` / `absolute` / `fixed` |
| **Overflow** | ✅ | `visible` / `hidden` / `scroll` / `auto`，带缓动滚动动画 |
| **z-index** | ✅ | 堆叠上下文在 `position != static` 或 `z-index != auto` 时创建 |

**不支持的布局**：
- ❌ `float` / `clear` → 用 flexbox 替代
- ❌ `position: sticky`
- ❌ `fr` 单位 → 用 px 或 auto 替代
- ❌ `grid-template-areas`
- ❌ `calc()` / `minmax()`（AUI 不支持 CSS 函数表达式）

### 3.3 CSS 属性支持

| 类别 | 支持的属性 | 说明 |
|------|-----------|------|
| **尺寸** | `width` / `height` / `min/max-*` / `box-sizing` | 仅 `px` 和 `%` 单位。**不支持 `em`/`rem`/`vw`/`vh`** |
| **盒模型** | `margin` / `padding` / `border` / `border-radius` | 简写和单独方向均支持 |
| **背景** | `background-color` / `background-image`(url/linear-gradient) / `background-repeat/size/position` | |
| **文本** | `color` / `font-size` / `font-weight` / `font-family` / `line-height` / `text-align` / `letter-spacing` / `white-space` / `text-overflow: ellipsis` / `text-stroke` | 字号通过 `fontSize/16*9` 转换到 Minecraft 字体 |
| **变换** | `transform: translate/rotate/scale`（含 3D 变体 `translatex/y/z` / `rotatex/y/z`） | 默认 `transform-origin` 为中心 |
| **滤镜** | `filter: blur/brightness/grayscale/invert/hue-rotate/opacity/drop-shadow` | |
| **阴影** | `box-shadow`（多值逗号分隔） | |
| **光标** | `cursor: default/pointer/text/crosshair/ew-resize/ns-resize` + `url(...)` 自定义图像光标 | 自定义光标使用伪光标渲染 |
| **动画** | `@keyframes` + `animation-*`（duration/delay/iteration-count/direction/fill-mode/timing-function） | 只支持 `linear` 和 `steps()` 缓动 |
| **过渡** | `transition` | ⚠️ 不稳定，源码标注"似乎不大好用"。**推荐 JS class 切换替代** |
| **自定义属性** | `--*` + `var()` | 支持，如 `--aui-slot-size: 18` |

**不支持的 CSS**：
- ❌ `calc()`、`clamp()`、`min()`、`max()` CSS 函数
- ❌ `em` / `rem` / `vw` / `vh` / `vmin` / `vmax` 单位
- ❌ `::before` / `::after` 伪元素
- ❌ `backdrop-filter`（属性可解析但不渲染）
- ❌ `clip-path`（属性可解析但不渲染）
- ❌ `mix-blend-mode`
- ❌ `+`（相邻兄弟）、`~`（通用兄弟）、`:not()`、`:nth-of-type()` 选择器
- ❌ `text-shadow`（有 `text-stroke` 作为替代）

### 3.4 JS / Java DOM 操作

本项目使用 **纯 Java DOM API**，不依赖 `<script>` 标签。暴露的 Java 方法：

```java
// 选择器
document.querySelector("#id");         // 按 CSS 选择器查找（返回第一个）
document.querySelectorAll(".class");   // 查找所有匹配
document.getElementById("id");         // 按 ID 查找

// DOM 操作
document.createElement("div");         // 创建新元素
element.append(child);                 // 追加子元素
element.prepend(child);                // 头部追加
element.remove();                      // 从 DOM 移除
element.setAttribute("class", "val");  // 设置/移除属性
element.removeAttribute("id");
element.getAttribute("src");

// 数据读写
element.innerText = "text";            // 读写文本内容
element.value = "input text";          // 读写 input 值
element.className = "abc";             // 读写 class
element.id = "my-id";                  // 读写 ID

// 事件监听
element.addEventListener("click", handler);  // 支持的类型：
// mousedown / mouseup / mousemove / click
// keydown / keyup
// scroll / change / focus / blur / load

// 样式操作
element.setAttribute("style", "display:flex; color:red;");
```

### 3.5 事件系统

| 特性 | 说明 |
|------|------|
| **事件触发** | AUI 通过 Neoforge `InputEvent.MouseButton` / `InputEvent.Key` 全局拦截，自动分发给所有 Document |
| **事件传播** | 支持捕获 → 目标 → 冒泡 三阶段 |
| **`stopPropagation()`** | 支持 |
| **`preventDefault()`** | ❌ **不支持**。只有 `stopPropagation()` |
| **点击检测** | AUI 根据 DOM 元素的 layout 位置自动命中，不需要自己计算坐标 |
| **3D 预览交互** | 需在 `VehicleControlScreen` 中通过 `mouseDragged` / `mouseScrolled` 单独处理 |
| **输入分离** | AUI 全局 handler 先于 Screen 触发，点击 UI 元素时 AUI 自动消耗事件。Screen 只需处理 3D 预览专用的交互 |

### 3.6 常用实例化的预期

```
Content:                  Input:                    Result:
─────────────────────────────────────────────────────────────────────
Minecraft 默认 GUI 缩放    427×240 像素             可用的屏幕空间
一个 4 Tab 面板           每 Tab 约 400×200         足够容纳三栏布局
编辑页三栏布局             左 130px / 中 1fr / 右 150px  在 427px 宽内可行，但紧凑
卡片列表项                每项约 24px 高             单个 tab 可容纳约 8 项（有滚动条）
按钮 / 开关 / 滑块         最小可交互尺寸 ~20px       可行
input 文本输入             单行 ~14px 高             可行
select 下拉                展开后 ~120px 高          可行，但展开高度有限
```

### 3.7 字体

AUI 内置字库 `lxgw`（霞鹜文楷），以 `@font-face` 注册于 `global.css`。所有字体须通过 `font-family: lxgw` 引用（或其他 Java AWT 可加载的字体）。无法使用 Google Fonts（无网络请求能力）。

---

## 四、架构设计

### 4.1 核心思路：Minecraft Screen + AUI Document 混合架构

当前方案使用 `ApricityUI.openScreen()` 将 AUI 文档作为独立屏幕打开。此模式**无法**做到：

- 渲染 3D 载具预览（Panel 01/02 的核心需求）
- 处理 3D 视角拖拽旋转等自定义鼠标输入

因此采用**混合架构**：

```
┌──────────────────────────────────────────────┐
│  VehicleControlScreen extends Screen          │
│                                               │
│  renderBackground()                           │
│    → 若为 Tab 01/02：渲染 3D 载具预览          │
│    → 若为 Tab 03/04：绘制半透明深色背景遮罩     │
│                                               │
│  render()                                     │
│    → super.render() (Minecraft 默认 UI)       │
│    → AUI 的全局事件自动绘制 Document (叠加)    │
│    → Cursor.drawPseudoCursor()               │
│                                               │
│  mouseClicked / mouseDragged / keyPressed     │
│    → 3D 旋转/缩放  → 由 Screen 处理          │
│    → UI 按钮/开关/滑块 → 由 AUI 全局事件处理  │
└──────────────────────────────────────────────┘
                      │
  AUI Document (独立层，叠加在 Screen 上方)
  ┌──────────────────────────────────────────┐
  │  Header Strip + 4 Tab 面板                │
  │  Client.java 全局事件自动处理:             │
  │    - 渲染: ScreenEvent.Render.Post       │
  │    - 输入: InputEvent.MouseButton/Key    │
  │    - 光标: ScreenEvent.Render.Post       │
  └──────────────────────────────────────────┘
```

**渲染与输入分离**：

| 层级 | 负责 |
|------|------|
| `VehicleControlScreen` | 3D 载具预览渲染、3D 视角拖拽旋转、Document 生命周期管理、Panel 协调 |
| AUI `Client.java`（全局） | AUI Document 渲染（自动，无需 Screen 调用）、AUI UI 元素输入事件（自动 mousedown/mousemove/mouseup/keydown）、光标渲染 |

**输入分流细节**：
- NeoForge 事件调度顺序：`InputEvent.MouseButton.Post`（Forge 事件总线）→ `Screen.mouseClicked()`（原生 GLFW 回调）
- AUI 注册在 `InputEvent.MouseButton.Post` 上，**晚于** `Screen.mouseClicked()` 触发
- 因此 `VehicleControlScreen.mouseClicked()` 需要主动判断点击区域：
  - 如果在 AUI 面板区域内 → `return true`（不处理，留给 AUI）
  - 如果在 3D 预览区域内 → 记录 `isDragging3d = true`，`return true`
  - 否则 → `return super.mouseClicked()`
- 3D 拖拽旋转仅在 `mouseDragged()` 中处理，受 `isDragging3d` 守卫

### 4.2 文件组织

#### 4.2.1 HTML / CSS 资源

```
machine_max/src/main/resources/assets/apricityui/apricity/machine_max/
├── vehicle_control.html       ← 主面板模板（布局骨架 + CSS + 静态节点）
└── vehicle_control.css        ← 面板样式（从 prototype/style.css 适配）
```

#### 4.2.2 Java 类

```
client/render/gui/
├── screen/
│   └── VehicleControlScreen.java      ← ⭐ 自定义 Screen（3D 预览 + 生命周期入口 + Panel 协调）
├── panel/
│   ├── PanelTabBar.java               ← Tab 导航栏渲染（Header Strip）
│   ├── PanelOverview.java             ← Tab 01 载具概览
│   ├── PanelDeviceControl.java        ← Tab 02 设备控制
│   ├── PanelConfigEditor.java         ← Tab 03 编辑配置
│   ├── PanelInventory.java            ← Tab 04 库存管理（⏸️ 仅占位）
│   ├── GroupStripRenderer.java        ← 控制组切换条（共享组件）
│   └── ControlDataAccessor.java       ← ControlGroupSet 数据获取适配器
```

**不再需要 `VehicleInfoPanelManager`**。原 POC 中使用 `@EventBusSubscriber` + `onClientTick` 轮询是由 `ApricityUI.openScreen()` 无 Screen 可挂载的权宜之计。自定义 Screen 的 `init()` 直接管理 Document 生命周期，消除 tick 延迟。

### 4.3 VehicleControlScreen — 核心入口

```java
@OnlyIn(Dist.CLIENT)
public class VehicleControlScreen extends Screen {

    private Document auiDocument;

    // ── 面板状态 ──
    private int activeTab = 0;
    private ControlGroupSet controlSet;

    // ── 3D 预览状态 ──
    private float previewRotX = 25f;
    private float previewRotY = -45f;
    private float previewZoom = 1.0f;
    private boolean isDragging3d;

    public VehicleControlScreen() {
        super(Component.literal("Vehicle Control"));
    }

    @Override
    protected void init() {
        // 1. 获取控制系统数据
        //    ✅ 验证通过：sub.getControlGroupSet() 由 Lombok @Getter 自动生成
        controlSet = ControlDataAccessor.getCurrentControlSet(Minecraft.getInstance());
        if (controlSet == null || controlSet == ControlGroupSet.EMPTY) {
            onClose();
            return;
        }

        // 2. 创建 AUI Document
        auiDocument = Document.create("machine_max/vehicle_control.html");
        if (auiDocument == null) {
            onClose();
            return;
        }

        // 3. 初始化所有 Panel（同步，无 tick 延迟）
        PanelTabBar.init(auiDocument, this::switchTab);
        PanelOverview.render(auiDocument, controlSet);
        PanelDeviceControl.render(auiDocument, controlSet);
        PanelConfigEditor.render(auiDocument, controlSet);
        // PanelInventory 暂不实现，Tab 04 按钮在 Header Strip 中禁用
    }

    private void switchTab(int index) {
        if (index == activeTab) return;
        if (index == 3) return; // Tab 04 未实现，忽略切换
        activeTab = index;
        PanelTabBar.setActive(auiDocument, index);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (activeTab == 0 || activeTab == 1) {
            // Tab 01/02：渲染 3D 载具预览作为背景
            render3dPreview(graphics, partialTick);
        } else {
            // Tab 03：无 3D 预览，绘制半透明深色遮罩防止透视游戏世界
            graphics.fillGradient(0, 0, width, height, 0xC0101010, 0xC0101010);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // AUI Document 的渲染由 Client.java 的 ScreenEvent.Render.Post 自动处理
        // 无需在这里调用 Base.drawDocument()

        // 自定义光标
        Cursor.drawPseudoCursor(graphics);
    }

    // ── 输入分流：点击 AUI 区域不处理，仅处理 3D 预览区 ──
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isInPreviewArea(mouseX, mouseY) && (activeTab == 0 || activeTab == 1)) {
            isDragging3d = true;
            return true;
        }
        // 点击在 AUI 面板区域内 → 留给 AUI 处理
        if (isInAuiPanelArea(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDragging3d && button == 0) {
            previewRotY += dragX * 0.5f;
            previewRotX += dragY * 0.5f;
            previewRotX = Math.clamp(previewRotX, -90f, 90f);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) isDragging3d = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isInPreviewArea(mouseX, mouseY)) {
            previewZoom = (float) Math.clamp(previewZoom - scrollY * 0.1, 0.5, 3.0);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ── 关闭清理 ──
    @Override
    public void onClose() {
        if (auiDocument != null) auiDocument.remove();
        Cursor.resetToDefault();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false; // 不暂停游戏
    }
}
```

**关键设计决策**：

1. **不调用 `super.render()`** — 不需要 Minecraft 默认 widget，由 AUI 接管 UI 渲染
2. **不在 Screen 的 `render()` 中调用 `Base.drawDocument()`** — AUI 的 `Client.java` 在 `ScreenEvent.Render.Post` 中自动绘制所有非世界 Document。叠加顺序：3D 预览（Screen.renderBackground）→ AUI UI（Post 事件）→ 光标（Screen.render末尾）。如果需要精确控制渲染顺序，也可以关闭 AUI 全局渲染，在 `VehicleControlScreen.render()` 中手动按序调用。
3. **输入事件分流** — Screen 的 `mouseClicked()` 主动判断点击区域，AUI 面板区域内的事件直接 `return true` 留给 AUI 处理，3D 预览区开始拖拽。不再依赖"自然分流"。
4. **Tab 03 背景遮罩** — 无 3D 预览时绘制半透明深色背景，防止面板边缘透视游戏世界。

### 4.4 打开方式

Tab 键触发时，替换当前的 `ApricityUI.openScreen()` 为直接打开自定义 Screen：

```java
// RawInputHandler.java — Tab 键回调
private long lastTabPressTime = 0;
private static final long TAB_DEBOUNCE_MS = 200;

new KeyHooks.EVENT(KeyBinding.generalVehicleInfoKey)
    .OnKeyDown(() -> {
        // 防抖：防止快速连按导致 Screen 栈异常
        long now = System.currentTimeMillis();
        if (now - lastTabPressTime < TAB_DEBOUNCE_MS) return;
        lastTabPressTime = now;

        var mc = Minecraft.getInstance();
        if (mc.screen instanceof VehicleControlScreen) {
            mc.setScreen(null);  // 关闭
        } else if (mc.player instanceof IEntityMixin mixin
                && mixin.machine_Max$getControllingSubsystem() != null) {
            mc.setScreen(new VehicleControlScreen());  // 打开
        }
    });
```

### 4.5 数据流

```
服务端 ControlGroupSet (SeatSubsystem / AbstractControllableSubsystem)
  │
  │ player → IEntityMixin → .machine_Max$getControllingSubsystem()
  │ → .getControlGroupSet() (✅ 客户端直接可读，@Getter 生成)
  ▼
ControlDataAccessor (封装获取逻辑)
  ▼
VehicleControlScreen.init()
  ├── PanelOverview.render(doc, data)      → 只读展示
  ├── PanelDeviceControl.render(doc, data) → 发送操作指令 (GuiActionPayload C2S)
  ├── PanelConfigEditor.render(doc, data)  → 编辑配置 (ControlGroupSetEditPayload C2S)
  └── PanelInventory.render(doc, data)     → ⏸️ 未实现

网络回写：
  客户端编辑 → ControlGroupSetEditPayload → 服务端
    → ObjectManager.getDestroyableObject(level, subPartId)
      → AbstractControllableSubsystem.setControlGroupSet()
        → NBT 持久化
```

**数据获取链路验证**（代码审查确认）：
```
player
  → cast IEntityMixin
    → .machine_Max$getControllingSubsystem()
      → AbstractControllableSubsystem (@Nullable)
        → .getControlGroupSet()  ← ✅ Lombok @Getter 自动生成，返回 ControlGroupSet
        → .getOwner().getSubPart()  ← SubPart 对象
          → .getId()  ← 全局 subPartId（用于网络包）
```

### 4.6 页面路由（不变）

```
HTML 模板中的 page div：

<div id="tab-overview"  class="page active">  → PanelOverview
<div id="tab-device"    class="page">         → PanelDeviceControl
<div id="tab-config"    class="page">         → PanelConfigEditor
<div id="tab-inventory" class="page">         → PanelInventory（⏸️ 禁用切换）

通过设置 class="page active" / class="page" 切换显示
实测通过 style="display:flex/none" 更可靠
```

---

## 五、分阶段实现计划

### 阶段 1：基础框架 — Screen + 多 Tab + Header Strip

**目标**：创建自定义 Screen，实现 4 Tab 切换框架

**文件变化**：
- **新建** `screen/VehicleControlScreen.java`：自定义 Screen 类，3D 预览骨架 + Document 生命周期管理
- 修改 `RawInputHandler.java`：Tab 键改为 `mc.setScreen(new VehicleControlScreen())`（含 200ms 防抖）
- 重写 `vehicle_control.html`：Header Strip + 4 个 page div 容器（Tab 04 按钮置灰/禁用）
- 重写 `vehicle_control.css`：适配 prototype/style.css 的 Neo-Brutalism 风格
- 新建 `panel/PanelTabBar.java`：Header Strip 折叠式导航（Tab 04 按钮 `disabled`）
- 删除 `panel/VehicleInfoPanelManager.java`：**删除前搜索全项目引用**，确保 FOR_GUI_TEST 和 @EventBusSubscriber 无残留引用

**CSS 适配注意事项**：
- AUI 不支持 `clamp()`、`clamp()` CSS 函数不可用 → 用固定 px 值
- 不支持 `vw` / `vh` → 用 `%` 相对于父容器
- 不支持 `::-webkit-scrollbar` 样式 → 用 AUI 自带滚动条，不自定义
- 字体使用 AUI 自带的 `lxgw`，不引用 Google Fonts
- `backdrop-filter` 和 `clip-path` 不可用 → 不依赖它们
- `transition` 不稳定 → 改用 JS 驱动的样式切换（class 切换即可）
- `@keyframes` 可用但须简单

**验收标准**：
- 按下 Tab 键弹出面板，显示 4 个 Tab 的 Header Strip（Tab 04 禁用）
- 点击折叠的 Tab 编号能切换面板（Tab 04 不可点击）
- 切换动画流畅，当前 Tab 展开显示标题
- Tab 03 切换后有半透明深色背景遮罩
- 快速连按 Tab 键不会导致 Screen 栈异常

---

### 阶段 2：Tab 01 & 02 — 控制组切换条 + 设备查看/控制

**目标**：实现载具概览和设备控制两个面板

**2a. 控制组切换条（共享组件）**

文件：`GroupStripRenderer.java`

功能：
- 横向大卡片：baseGroup（始终白色） + 子组列表
- 每个卡片三行：名称 / 编号(GROUP 00) / 状态(ACTIVE / STAND BY)
- 点击切换激活子组 → 发送网络包到服务端
- 当 `activeIndex` 变化时高亮对应卡片

数据来源：`ControlGroupSet.baseGroup` + `ControlGroupSet.groups` + `ControlGroupSet.activeIndex`

**2b. Panel 01 — 载具概览**

文件：`PanelOverview.java`

功能：
- 控制组切换条 + 状态区 + 警告区 + 控制模式信息
- 状态条（durability / energy / thrust / speed）— 使用 div + 百分比宽度
- 警告列表 — 从子系统获取当前警告
- 控制模式显示 — 从当前激活的子组读取 `controlMode`

数据来源：
- 载具状态 → `VehicleCore` 或对应子系统的运行时数据
- 警告 → ⚠️ **待确认**：服务端是否已有警告推送机制。如无，Tab 01 的警告区暂时留空或使用 mock 数据

**2c. Panel 02 — 设备控制**

文件：`PanelDeviceControl.java`

功能：
- 控制组切换条 + PULSE 按钮区 + TOGGLE 开关区 + SLIDER 滑块区
- PULSE 按钮：点击发送单次信号 → `GuiPulseAction`
- TOGGLE 开关：点击在 ON/OFF 间切换 → `GuiToggleAction`
- SLIDER 滑块：拖拽调整连续值 → `GuiSliderAction`

数据来源：`ControlGroupSet.guiActions`

滑块实现方式：
- AUI 中不支持 `getBoundingClientRect()`（Element.java 无此方法）
- 使用 AUI 的 `MouseEvent.clientX` 配合 mousedown→mousemove→mouseup 模式
- 参考 `VehicleInfoPanelManager.java:400-417` 已有的拖拽实现

操作反馈：
- 单击按钮 → 发送 `ControlBindingPayload` 或等效包
- 切换开关 → 反转 toggleState，同步服务端
- 滑条值变更 → `GuiSliderAction.setValue()` + 发送网络包

**验收标准**：
- 控制组切换条正确显示 base + 子组，点击能切换
- PULSE 按钮点击有视觉反馈（短暂高亮）
- TOGGLE 开关正确反映 `active` 状态
- SLIDER 滑块可拖拽，显示当前值

---

### 阶段 3：Tab 03 — 编辑配置（最复杂）

**目标**：实现原型中的三栏配置编辑器

文件：`PanelConfigEditor.java`

**3a. 左栏 — 控制组列表**

功能：
- baseGroup（不可删除，标记"永远激活"）+ 子组列表
- 每项显示：编号、名称、模式标签、描述、绑定数
- "添加控制组"按钮 → 创建新 `ControlGroup` 追加到 groups
- 删除按钮 → 移除子组
- "重置预设"按钮 → `restoreControlGroupPreset()`
- 选中项高亮（白色背景黑字）

**3b. 中栏 — 组详情 + 绑定列表 + GUI 元素列表**

功能：
- 上半：组详情表单（名称 input / 模式 select / 描述 input）
- 下半分两个区：
  - KEY BINDINGS 区：按键绑定列表 + 添加按钮
  - CONFIGURATION ITEMS 区：GUI 交互元素列表 + 添加按钮
- 每项可以选中（高亮），对应的详情出现在右栏
- 悬浮显示删除按钮

**3c. 右栏 — 详情编辑**

根据选中项类型显示不同表单：

| 选中项 | 表单内容 |
|--------|---------|
| Key Binding | NAME, BINDING TYPE (PRESS/HOLD/TOGGLE), KEY (trigger), CHANNEL, TARGETS, SENSITIVITY (slider), DEAD ZONE (slider), REVERSE AXIS (toggle) |
| GUI Pulse | LABEL, TYPE, CHANNEL, TARGETS |
| GUI Toggle | LABEL, TYPE, CHANNEL, TARGETS, ACTIVE (toggle) |
| GUI Slider | LABEL, TYPE, CHANNEL, TARGETS, MIN (slider), MAX (slider), STEP (slider) |

**3d. 数据持久化**

- 编辑在客户端本地 `ControlGroupSet` 副本上操作
- 保存时通过 `ControlGroupSet.STREAM_CODEC` 或新建网络包同步服务端
- 服务端收到后更新 `AbstractControllableSubsystem.controlGroupSet`
- 服务端通过 NBT 持久化（已有代码）

**技术挑战**：
- 三栏布局中，左/中栏需要滚动列表；AUI 的 `overflow: auto/scroll` 可用
- 滑块控件需要拖拽交互，参考已有 `VehicleInfoPanelManager` 实现
- input/select 元素在 AUI 中可用，但有风格限制
- 大量动态元素（数十个卡片 + 表单）的创建和移除；AUI 的 `element.remove()` 可用

**验收标准**：
- 三栏布局正确显示
- 能新增/删除控制组
- 能新增/删除/编辑按键绑定
- 能新增/删除/编辑 GUI 交互元素
- 修改后数据正确同步

---

### 阶段 4：Tab 04 — 库存管理（⏸️ 暂停，留空占位）

**目标**：Tab 04 按钮禁用，HTML 中保留 `<div class="page" id="tab-inventory">` 空容器

**文件变化**：
- 新建 `panel/PanelInventory.java`：构造函数中打印 `"PanelInventory: not implemented yet"`，所有方法为空
- `vehicle_control.html`：保留空 page div
- `PanelTabBar.java`：Tab 04 按钮添加 `disabled` 类，点击事件跳过 index=3

**注意**：Container bind 技术方案（`<container bind="entity">`）需要 AUI Screen 模式支持验证。
当前不确定 AUI 的 Container 组件能否在非 Menu 体系下独立工作。留作后续单独评估。

**验收标准**：
- Tab 04 按钮显示为禁用状态（灰色，不可点击）
- 切换到其他 Tab 正常

---

### 阶段 5：真实数据接入

**目标**：移除 mock 数据，从真实载具子系统获取 `ControlGroupSet`，实现完整读写链路

#### 5a. 客户端直接读取 ControlGroupSet

```java
Minecraft client = Minecraft.getInstance();
if (client.player instanceof IEntityMixin mixin
        && mixin.machine_Max$getControllingSubsystem() instanceof AbstractControllableSubsystem sub) {

    // ✅ 直接从子系统读取 ControlGroupSet（@Getter 自动生成）
    ControlGroupSet cgs = sub.getControlGroupSet();

    // 获取 SubPart 全局 ID（用于网络包中的 subPartId）
    int subPartId = sub.getOwner().getSubPart().getId();
    String subSystemName = sub.name;
}
```

**结论**：`ControlGroupSet` 在服务端已通过 `AbstractControllableSubsystem` 持久化，客户端通过 `player → IEntityMixin → subsystem` 链即可直接访问。**不需要额外新建同步通道来读取完整数据。**

#### 5b. 网络同步方案

**现状**：
- `ControlGroupSet.STREAM_CODEC` 已定义，但**目前无任何 Payload 使用它**
- `ControlBindingPayload` 已存在，用于单个按键绑定的触发事件（PRESS/RELEASE）
- 缺少：整体 `ControlGroupSet` 的编辑结果同步包、GUI 操作同步包

**需要新建的网络包**：

| Payload | 方向 | 用途 |
|---------|------|------|
| `ControlGroupSetEditPayload` | C2S | 编辑器保存配置时，将整个修改后的 `ControlGroupSet` 发送到服务端 |
| `GuiActionPayload` | C2S | GUI 控件操作（点击 PULSE、切换 TOGGLE、拖拽 SLIDER）发送到服务端 |

**ControlGroupSetEditPayload 设计**：

```java
// 注册到 MMPayloadRegistry
public record ControlGroupSetEditPayload(
    int subPartId,              // 从 sub.getOwner().getSubPart().getId() 获取
    String subSystemName,       // 子系统名称
    ControlGroupSet controlGroupSet  // 使用已有的 STREAM_CODEC
) implements CustomPacketPayload
```

**服务端处理**：
```java
// Handler
DestroyableObject object = ObjectManager.getDestroyableObject(context.player().level(), payload.subPartId());
if (object instanceof SubPart subPart) {
    AbstractControllableSubsystem target = findSubsystemByName(subPart, payload.subSystemName());
    if (target != null) {
        target.setControlGroupSet(payload.controlGroupSet());
        target.saveDataToSubsPart();  // 触发 NBT 持久化
    }
}
```

**注意事项**：`ControlGroupSet` 可能包含较多子组和绑定项（数十个）。单个 Payload 全量发送在大部分场景下不会超过 Minecraft 网络包上限（~32KB），但建议在 STREAM_CODEC 中增加简单的大小检查，或考虑后续做增量同步优化。

**GuiActionPayload 设计**：

```java
public record GuiActionPayload(
    int subPartId,
    String subSystemName,
    int actionIndex,           // guiActions 列表中的索引
    GuiActionType actionType,  // PULSE/TOGGLE/SLIDER
    float value                // TOGGLE: 0/1, SLIDER: 当前值, PULSE: 忽略
) implements CustomPacketPayload
```

#### 5c. ControlDataAccessor 接口

```java
/**
 * 控制数据获取适配器。
 * 封装从客户端 Player 到 ControlGroupSet 的读取写入路径。
 */
@OnlyIn(Dist.CLIENT)
public class ControlDataAccessor {

    /** 从当前控制子系统获取 ControlGroupSet */
    @Nullable
    public static ControlGroupSet getCurrentControlSet(Minecraft mc) {
        if (mc.player instanceof IEntityMixin mixin
                && mixin.machine_Max$getControllingSubsystem() instanceof AbstractControllableSubsystem sub) {
            return sub.getControlGroupSet();
        }
        return null;
    }

    /** 获取当前控制子系统的 subPartId，-1 表示不存在 */
    public static int getCurrentSubPartId(Minecraft mc) {
        if (mc.player instanceof IEntityMixin mixin
                && mixin.machine_Max$getControllingSubsystem() instanceof AbstractControllableSubsystem sub) {
            return sub.getOwner().getSubPart().getId();
        }
        return -1;
    }

    /** 获取当前子系统名称 */
    @Nullable
    public static String getCurrentSubSystemName(Minecraft mc) {
        if (mc.player instanceof IEntityMixin mixin
                && mixin.machine_Max$getControllingSubsystem() instanceof AbstractControllableSubsystem sub) {
            return sub.name;
        }
        return null;
    }

    /** 保存 ControlGroupSet → 发送到服务端 */
    public static void saveControlSet(ControlGroupSet modified) {
        // 发送 ControlGroupSetEditPayload
    }

    /** 发送 GUI 控件操作 */
    public static void sendGuiAction(int actionIndex, GuiActionType type, float value) {
        // 发送 GuiActionPayload
    }
}
```

#### 5d. VehicleControlScreen 中的使用

```java
// VehicleControlScreen.init() 中：
ControlGroupSet data = ControlDataAccessor.getCurrentControlSet(Minecraft.getInstance());
if (data == null || data == ControlGroupSet.EMPTY) {
    onClose();
    return;
}
// 同步直接初始化，无 tick 延迟
PanelOverview.render(auiDocument, data);
PanelDeviceControl.render(auiDocument, data);
PanelConfigEditor.render(auiDocument, data);
```

**验收标准**：
- 面板启动时直接从玩家控制子系统读取 `ControlGroupSet`，无需等待网络同步
- 编辑配置后点"保存"，通过 `ControlGroupSetEditPayload` 发送到服务端
- GUI 控件操作（按钮/开关/滑块）通过 `GuiActionPayload` 即时发送
- 服务端通过 `ObjectManager.getDestroyableObject(level, subPartId)` 找到 SubPart，更新并持久化
- 重新打开面板显示最新配置

---

## 六、HTML 模板设计要点

### 6.1 核心布局结构

```html
<body>
  <!-- 折叠式标题导航 -->
  <div class="header-strip" id="header-strip">
    <div class="header-item active" id="tab-btn-0">
      <span class="header-number">01</span>
      <div class="header-content">
        <span class="header-title">VEHICLE OVERVIEW</span>
        <span class="header-subtitle">/// STATUS</span>
      </div>
    </div>
    <div class="header-item" id="tab-btn-1">...</div>
    <div class="header-item" id="tab-btn-2">...</div>
    <div class="header-item disabled" id="tab-btn-3">...</div>  <!-- ⏸️ 禁用 -->
  </div>

  <!-- 面板内容 -->
  <div class="panel-content" id="panel-body">
    <!-- Tab 01: 载具概览 -->
    <div class="page active" id="tab-overview">
      <div class="group-strip" id="group-strip-0"></div>
      <div class="info-bar">
        <div class="info-col" id="status-col"></div>
        <div class="info-col" id="warning-col"></div>
        <div class="info-col" id="extra-col"></div>
      </div>
    </div>

    <!-- Tab 02: 设备控制 -->
    <div class="page" id="tab-device">
      <div class="group-strip" id="group-strip-1"></div>
      <div class="device-bar">
        <div class="device-section" id="pulse-section"></div>
        <div class="device-section" id="toggle-section"></div>
        <div class="device-section" id="slider-section"></div>
      </div>
    </div>

    <!-- Tab 03: 编辑配置 — 三栏 -->
    <div class="page" id="tab-config">
      <div class="config-layout">
        <div class="config-left" id="config-left"></div>
        <div class="config-middle" id="config-middle"></div>
        <div class="config-right" id="config-right"></div>
      </div>
    </div>

    <!-- Tab 04: 库存 — ⏸️ 空占位 -->
    <div class="page" id="tab-inventory">
      <div class="inventory-layout" id="inventory-layout"></div>
    </div>
  </div>
</body>
```

### 6.2 CSS 适配策略

| 原 prototype CSS | AUI 适配 |
|---|---|
| `font-family: 'Oswald', 'Rajdhani'` | `font-family: lxgw`（AUI 内置字库） |
| `clamp(16px, 1.8vw, 26px)` | `font-size: 14px`（固定值） |
| `backdrop-filter` | 移除，用纯色背景 |
| `clip-path` | 移除 |
| `transition: ...` | 用 JS class 切换替代 |
| `overflow-y: auto` | 可用 |
| `display: flex` / `display: grid` | 完整支持 |
| `position: absolute` | 可用 |
| `border: 2px solid #...` | 可用 |
| `background-color` | 可用 |
| `box-shadow` | 可用 |
| `gap` (flex/grid) | 可用 |
| `::before` / `::after` | 不可用。用额外 div 替代 |

### 6.3 颜色方案

从 prototype 移植，每个 Tab 有独立主题色：

| Tab | 主题基色 | 强调色 |
|-----|---------|--------|
| Overview | `#080602` → `#141008` | `#C89520`（琥珀） |
| Device Control | `#04080F` → `#0A1424` | `#2E5A90`（深蓝） |
| Edit Config | `#0A0404` → `#180A06` | `#8A2A2A`（锈红） |
| Inventory（⏸️） | `#06040E` → `#100A1A` | `#6850B8`（深紫） |

通过在每个 page div 上设置 CSS 变量实现主题切换。

---

## 七、与现有 VehicleInfoPanelManager 的关系

`VehicleInfoPanelManager.java` 是 POC 阶段的产物，在 `ApricityUI.openScreen()` 模式下需要 `@EventBusSubscriber` + `onClientTick` 轮询来管理 Document 生命周期。

**正式方案中该文件将被删除**，其职责全部移至 `VehicleControlScreen`：

| 原 VehicleInfoPanelManager 职责 | 新位置 |
|---|---|
| Document 生命周期管理 | `VehicleControlScreen.init()` / `onClose()` |
| 获取 ControlGroupSet | `ControlDataAccessor`（独立工具类） |
| Panel 初始化与协调 | `VehicleControlScreen.init()` 直接调用各 Panel |
| 事件绑定（如 toast 定时器、滑条拖拽） | 各 Panel 类内部自行管理 |

**删除前检查清单**：
- [ ] 搜索全项目对 `VehicleInfoPanelManager.FOR_GUI_TEST` 的引用
- [ ] 搜索全项目对 `VehicleInfoPanelManager` 类名的引用
- [ ] 确认 `@EventBusSubscriber` 注册已从事件的订阅列表中移除
- [ ] 确认 `registerRecipe()` 等方法无外部调用

**优势**：消除 `onClientTick` 一帧延迟，`init()` 中同步完成所有初始化。面板内容在 Screen 打开瞬间即就绪。

---

## 八、风险与待确认

| 风险 | 严重度 | 对策 |
|------|-------|------|
| **3D 预览 + AUI 渲染顺序冲突** | 🟡 中 | AUI 在 `ScreenEvent.Render.Post` 绘制，位于 Screen 之后。如需精确控制顺序，关闭 AUI 全局渲染，在 `VehicleControlScreen.render()` 中手动按序绘制 |
| **3D 输入事件与 AUI 输入事件争抢** | 🟡 中 | ✅ 已修复：Screen `mouseClicked()` 主动判断区域。AUI 面板区直接 `return true`，3D 预览区设置拖拽标志。不再依赖"自然分流" |
| **客户端 ControlGroupSet 数据获取路径** | 🟢 已确认 | `player → IEntityMixin → .getControllingSubsystem() → .getControlGroupSet()`（✅ `@Getter` 自动生成），代码审查已验证 |
| **全量编辑后同步** | 🟡 中 | 需新建 `ControlGroupSetEditPayload`（C2S），使用已有 `STREAM_CODEC`。注意 Payload 大小问题 |
| **GUI 控件操作同步** | 🟡 中 | 需新建 `GuiActionPayload`（C2S），注册到 `MMPayloadRegistry` |
| **AUI overflow:auto 滚动性能** | 🟡 中 | 长列表需测试实际帧率 |
| **Slider 拖拽中 getBoundingClientRect 缺失** | 🟡 中 | 已有 `VehicleInfoPanelManager` 拖拽实现可参考，基于 MouseEvent clientX |
| **多玩家同步** | 🟢 低 | 每个玩家有独立座椅，通过 subPartId 区分，无并发冲突 |
| **AUI 文档生命周期管理** | 🟢 低 | Screen.onClose() 时调用 doc.remove()，明确可控 |
| **Tab 开/关防抖** | 🟢 低 | ✅ 已修复：增加 200ms 防抖 |
| **Tab 03 无 3D 预览时的背景** | 🟢 低 | ✅ 已修复：`renderBackground()` 中 Tab 03/04 绘制半透明遮罩 |

---

## 九、文件清单

### 新建文件

```
src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/
├── screen/
│   └── VehicleControlScreen.java      ← ⭐ 自定义 Screen（3D 预览渲染 + 生命周期）
├── panel/
│   ├── PanelTabBar.java               ← Tab 导航渲染
│   ├── PanelOverview.java             ← Tab 01
│   ├── PanelDeviceControl.java        ← Tab 02
│   ├── PanelConfigEditor.java         ← Tab 03
│   ├── PanelInventory.java            ← Tab 04（⏸️ 仅占位）
│   ├── GroupStripRenderer.java        ← 控制组切换条
│   └── ControlDataAccessor.java       ← 数据适配器

src/main/resources/assets/apricityui/apricity/machine_max/
└── vehicle_control.css                 ← 面板样式

src/main/java/io/github/sweetzonzi/machine_max/network/payload/
├── ControlGroupSetEditPayload.java     ← C2S：全量配置保存
└── GuiActionPayload.java              ← C2S：GUI 控件操作
```

### 修改文件

```
src/main/resources/assets/apricityui/apricity/machine_max/
└── vehicle_control.html                ← 扩展为 4 Tab 布局（Tab 04 空占位）

src/main/java/io/github/sweetzonzi/machine_max/client/
├── render/gui/panel/VehicleInfoPanelManager.java ← 删除（先搜索全项目引用）
└── input/RawInputHandler.java                    ← Tab 键改为打开 Screen（含防抖）

src/main/java/io/github/sweetzonzi/machine_max/network/
└── MMPayloadRegistry.java                         ← 注册两个新 Payload
```

---

## 十、实现顺序建议

```
阶段 1 ─── 基础框架 ─────── 2-3 天
  ├── HTML 骨架 + CSS 样式移植
  ├── PanelTabBar（Header Strip，Tab 04 禁用）
  ├── Tab 切换路由 + 背景遮罩逻辑
  ├── Tab 开关防抖
  └── 搜索引用 → 删除 VehicleInfoPanelManager

阶段 0.5 ─ 网络包前置 ─── 1 天（与阶段 1 并行）
  ├── ControlGroupSetEditPayload
  ├── GuiActionPayload
  └── MMPayloadRegistry 注册

阶段 2 ─── Tab 01 & 02 ──── 3-4 天
  ├── GroupStripRenderer（共享组件）
  ├── PanelOverview
  ├── PanelDeviceControl
  └── 3D 预览（初版，详见附录 A）

阶段 3 ─── Tab 03 ───────── 5-7 天
  ├── 三栏布局
  ├── 绑定列表 CRUD
  ├── GUI 元素列表 CRUD
  └── 详情编辑表单

阶段 4 ─── Tab 04 ───────── ⏸️ 暂停
  └── 空占位，不分配工时

阶段 5 ─── 数据接入 ─────── 2-3 天
  ├── ControlDataAccessor
  ├── 真实数据挂载 + mock 数据移除
  ├── 网络包联调
  └── 测试与修复
```

**主要变更**：
1. 阶段 0.5 新增：网络包提前到与阶段 1 并行，确保从第一个可运行的 Screen 使用真实数据
2. 阶段 4 暂停：Tab 04 留空
3. 阶段 2 增加 3D 预览初版
4. 阶段 5 工时减少（网络包已提前完成）

---

## 十一、参考资料

### 核心参考（3D 预览）
- [`VehicleAnimatable.java`](file:///d:/Files/Project_MinecraftMods/Machine-Max/src/main/java/io/github/sweetzonzi/machine_max/common/visual/VehicleAnimatable.java) — **整车级动画体**，3D 预览核心（`alignToAxesByFirstSubPart`、`subParts` 遍历）
- [`SubPartAnimatable.java`](file:///d:/Files/Project_MinecraftMods/Machine-Max/src/main/java/io/github/sweetzonzi/machine_max/common/visual/SubPartAnimatable.java) — 子零件动画体（`getRenderWorldPositionMatrix`、`getBones`）
- [`CustomModelItemRenderer.java`](file:///d:/Files/Project_MinecraftMods/Machine-Max/src/main/java/io/github/sweetzonzi/machine_max/client/render/renderer/CustomModelItemRenderer.java) — 物品 GUI 中整车渲染参考（`renderVehicleAnimatable`）
- [`PartAssemblyRenderer.java`](file:///d:/Files/Project_MinecraftMods/Machine-Max/src/main/java/io/github/sweetzonzi/machine_max/client/render/renderer/PartAssemblyRenderer.java) — 世界放置预览整车渲染参考（`renderVehicleProjection`）
- [`PartEntityRenderer.java`](file:///d:/Files/Project_MinecraftMods/Machine-Max/src/main/java/io/github/sweetzonzi/machine_max/client/render/renderer/PartEntityRenderer.java) — 世界实体渲染管线（骨骼渲染模式参考）

### 参考（控制系统）
- [common/mech/control/](file:///d:/Files/Project_MinecraftMods/Machine-Max/src/main/java/io/github/sweetzonzi/machine_max/common/mech/control/) — 控制系统数据模型

### 参考（原型与现有实现）
- [prototype/vehicle-ui/](file:///d:/Files/Project_MinecraftMods/Machine-Max/prototype/vehicle-ui/) — HTML 原型
- [AUI 现有实现](file:///d:/Files/Project_MinecraftMods/Machine-Max/src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/VehicleInfoPanelManager.java) — 当前工作的 POC

---

## 十二、技术笔记与踩坑记录

> 本章记录实际开发中遇到的 AUI 陷阱和 CSS/HTML 布局问题，供后续开发者参考。

### 12.1 AUI body 默认 display:block，所有 flex 子属性静默失效

**踩坑日期**：2026-05-28  
**严重度**：🔴 高（布局根因）  
**现象**：`panel-content { flex: 1; }` 完全不生效，面板内容无法填充 header-strip 下方的剩余空间。所有内容堆叠在顶部，底部信息栏位置不对。  
**根因**：AUI 的 `Style.java` 中 `display` 默认值为 `"block"`。`global.css` 中 body 只设了 `width/height: 100%`，**没有声明 `display: flex`**。`flex: 1` 只在 flex 容器的子元素上生效，block 容器中的 `flex` 属性被静默忽略。  
**修复**：
```css
body {
    display: flex;
    flex-direction: column;
}
```
**教训**：AUI 的 body 不像浏览器有 user-agent 样式表提供默认 flex 行为。**每次写 AUI 布局时，第一步必须在最外层容器上显式声明 `display: flex`。**

### 12.2 position:absolute + height:100% 的百分比高度链路

**踩坑日期**：2026-05-28  
**严重度**：🟡 中  
**现象**：`.page { position: absolute; height: 100%; }` 的 `height: 100%` 解析为 0，页面内容不显示。  
**根因**：CSS 规范中，百分比高度需要**包含块**（最近的 position 非 static 的祖先）有确定的高度。如果祖先链中某一层的 `height` 依赖于子内容（auto），则百分比无法解析。  
**链路**：
```
body (height:100% → 窗口高度, ✅ 确定)
  └─ panel-content (flex:1 → body是flex容器, ✅ 确定)
       └─ .page (position:absolute, height:100% → 100% of panel-content, ✅ 确定)
```
**教训**：如果 12.1 中 body 没有设为 flex，则 panel-content 的高度依赖子内容 → `.page` 的 `height:100%` 无法解析。**两步必须同时修复。**

### 12.3 AUI Element 没有 querySelector 方法

**踩坑日期**：2026-05-28  
**严重度**：🟡 中  
**现象**：需要在 tab 切换时控制 `header-content` 子元素的显隐，但无法通过 `element.querySelector(".header-content")` 查找。  
**根因**：AUI 的 `Element` 类只暴露了 `document.querySelector()` 和 `document.querySelectorAll()`（在 Document 级别），Element 自身没有子树查询方法。  
**修复**：给每个 `header-content` 加 `id="tab-content-0/1/2/3"`，在 Java 中用 `doc.getElementById()` 精确定位。  
**教训**：AUI 中任何需要 Java DOM 操作的子元素，**必须预先在 HTML 中分配唯一 ID**。不能依赖 class 选择器做父子层级查找。

### 12.4 CSS 复合类选择器在 AUI 中可用但应有 Java 双重保障

**踩坑日期**：2026-05-28  
**严重度**：🟢 低  
**现象**：使用 `.header-item.active .header-content { display: flex; }` 来通过 CSS 控制显隐。  
**验证**：AUI 的 CSS 引擎**支持**复合类选择器（如 `.header-item.active`）和后代选择器（如 `.group-card.active`），因为现有 CSS 中已有 `.group-card.active` 和 `.header-item.active .header-number` 在使用。  
**但**：CSS 选择器的行为依赖 AUI 内部的 `Selector.match()` 实现，未来版本可能变化。因此在 [PanelTabBar.java](file:///d:/Files/Project_MinecraftMods/Machine-Max/src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/panel/PanelTabBar.java) 中同时用 `element.setAttribute("style", ...)` 显式设置 `display`，确保 CSS 失效时 Java 侧仍能正常工作。  
**教训**：关键的布局切换（如 Tab 折叠）**不要只依赖 CSS 选择器**，应在 Java 中同步设置内联 style 作为保底。

### 12.5 AUI CSS 不支持的选择器（补充确认）

**踩坑日期**：2026-05-28  
**已确认不支持**：`+`（相邻兄弟）、`~`（通用兄弟）、`:not()`、`:nth-of-type()`  
**已确认支持**：复合类选择器 `.class1.class2`、后代选择器 `.parent .child`、ID 选择器 `#id`、元素选择器 `div`  
**替代方案**：
| 需求 | 浏览器方案 | AUI 替代 |
|------|-----------|---------|
| "所有非 active 的 header-item" | `.header-item:not(.active)` | Java 循环设置内联 style |
| "active 元素的下一个兄弟" | `.active + .sibling` | Java 中查找相邻元素并设置 style |
| "第 N 个子元素" | `.child:nth-of-type(N)` | 给每个子元素分配 ID |

### 12.6 AUI transition 不可靠

**踩坑日期**：2026-05-28  
**严重度**：🟢 低  
**现象**：AUI 源码（Style.java 注释）标注 `transition` "似乎不大好用"。  
**修复**：移除所有 `transition` 属性，改为瞬间生效的 class/style 切换。  
**教训**：不依赖 AUI 的 CSS 过渡动画。如需动画效果，用 `@keyframes` + `animation` 替代（AUI 支持 `linear` 和 `steps()` 缓动），或在 Java 中手动 tick 驱动。

### 12.7 固定像素高度在不同分辨率下的比例失调

**踩坑日期**：2026-05-28  
**严重度**：🔴 高  
**现象**：`info-bar { height: 160px; }` 和 `device-bar { height: 160px; }` 在默认 GUI 缩放下（427×240 像素可用空间）占据了 67% 的屏幕高度，导致 3D 预览区域几乎没有空间。  
**根因**：AUI 渲染窗口的像素尺寸取决于 Minecraft GUI 缩放设置。`height: 160px` 是绝对值，不会随窗口缩放。  
**修复**：移除固定 `height`，改为 `flex-shrink: 0` + 内容自适应。让 `preview-spacer { flex: 1; }` 填充中间区域。  
**教训**：AUI 不支持 `vh`/`vw`/`calc()` 等相对单位。在需要响应式布局的场景中，**优先使用 flex 布局链（flex:1 + flex-shrink:0）来分配空间**，而非固定像素值。

### 12.8 预览区域占位技巧（AUI + Screen 混合渲染）

**踩坑日期**：2026-05-28  
**严重度**：🟢 低（技巧）  
**背景**：AUI 面板需要在中间区域显示 3D 载具预览，但 3D 预览由 `VehicleControlScreen.renderBackground()` 在 Screen 层渲染，AUI 无法直接渲染 3D 内容。  
**方案**：在 HTML 中放置一个**透明的占位 div**（`.preview-spacer { flex: 1; min-height: 0; }`），它在 AUI 布局中占据空间（将 info-bar 推到底部），但因为 `background: transparent`（默认），Screen 层渲染的 3D 内容可以透过它显示。  
**关键约束**：`min-height: 0` 是必须的，否则 flex 子元素的最小高度默认为内容高度（可能撑开），导致占位区域不是真正的"弹性空间"。

### 12.9 querySelector 与 querySelectorAll 的 Document 级可用性

**踩坑日期**：2026-05-28  
**严重度**：🟢 低  
**AUI 暴露的 Java DOM API**：
```java
document.querySelector("#id");         // ✅ 按 CSS 选择器查找（返回第一个匹配）
document.querySelectorAll(".class");   // ✅ 查找所有匹配
document.getElementById("id");         // ✅ 按 ID 查找（最快）
```
**限制**：这些都是 Document 级方法，不能在 Element 上调用。如果需要查找特定子树中的元素，只能通过 ID 定位。

### 12.10 AUI CSS Grid 不支持 `fr` 单位

**踩坑日期**：2026-05-28  
**严重度**：🔴 高（布局方案选择）  
**现象**：`grid-template-columns: 1fr 1fr 1fr` 导致只有最后一个网格项显示，其余项宽度为 0。  
**根因**：AUI 的 CSS 引擎（Java 实现）不支持 `fr`（fractional）单位。`fr` 是 CSS Grid 规范中的相对单位，但 AUI 只支持 `px` 和 `auto` 作为 `grid-template-columns/rows` 的值。  
**验证**：`grid-template-columns: 180px 120px 120px`（固定 px 值）正常工作，三列正确分配。  
**替代方案**：
| 需求 | 浏览器方案 | AUI 替代 |
|------|-----------|---------|
| 等宽多列 Grid | `grid-template-columns: repeat(N, 1fr)` | 使用 `flex: 1` + `min-width: 0` 的 Flexbox |
| 按比例分配 Grid | `grid-template-columns: 3fr 2fr 2fr` | 使用固定 `px` 值：`180px 120px 120px` |
| 动态列数等宽 | `grid-template-columns: repeat(N, 1fr)` | Java 中用 StringBuilder 拼接 `px` 值 |

**教训**：在 AUI 中需要等宽布局时，**优先使用 Flexbox（`flex: 1`）**而非 CSS Grid。Grid 仅在列宽固定为 `px` 值时可用。

### 12.11 AUI CSS Grid 可用但受限

**踩坑日期**：2026-05-28  
**严重度**：🟡 中  
**已验证可用**：
- `display: grid` ✅
- `grid-template-columns: 180px 120px 120px`（固定 px 值）✅
- `grid-template-rows`（固定 px 值）✅
- `grid-row` / `grid-column` + span ✅
- `gap`（grid 间距）✅

**已验证不可用**：
- `fr` 单位 ❌（导致列宽为 0）
- `minmax()` ❌（CSS 函数表达式不支持）
- `grid-template-areas` ❌（未验证，但文档标注不支持）

**实际应用**：信息栏三列布局最初使用 `grid-template-columns: 180px 120px 120px`，但发现子元素内容（进度条）不受列宽约束会溢出。**最终改用 Flexbox + 固定宽度**（`width: 180px; flex-shrink: 0`）实现可靠约束。

### 12.12 控制组卡片等宽分配策略

**踩坑日期**：2026-05-28  
**严重度**：🟡 中  
**问题**：控制组切换条中的卡片需要等宽排列，但 BASE 卡片（白底）因内容较多看起来比其他卡片宽。  
**尝试过的方案**：
1. `flex: 1` + `min-width: 0` — 基本可用，但 BASE 卡片视觉上偏宽
2. `flex: 1 1 0` + `width: 0` — 过度压缩，内容被裁剪
3. CSS Grid `1fr` — AUI 不支持 `fr` 单位，失败
4. CSS Grid 固定 `px` — 列数动态变化时不方便

**最终方案**：使用 `flex: 1` + `min-width: 0` + `overflow: hidden`，接受 AUI flex 引擎的微小宽度差异。视觉差异可通过统一 padding 和 font-size 缓解。

### 12.13 导航栏激活态内容溢出

**踩坑日期**：2026-05-28  
**严重度**：🟡 中  
**现象**：激活态导航项（白色背景）的标题文字 "VEHICLE OVERVIEW" 延伸到相邻的非激活数字 "02" 上方，产生遮挡。  
**根因**：`.header-item.active` 使用 `flex: 0 0 auto`，宽度由内容决定。标题文字较长时，白色背景区域与相邻项重叠。  
**修复**：添加 `overflow: hidden` + `z-index: 1` 到 `.header-item.active`，确保白色背景区域不溢出，且层级高于相邻项。

### 12.14 AUI Grid 子元素不受列宽约束（内容溢出）

**踩坑日期**：2026-05-28  
**严重度**：🔴 高  
**现象**：使用 `display: grid; grid-template-columns: 180px 120px 120px` 时，信息栏三列的列标题（VEHICLE STATUS / WARNINGS / CONTROL MODE）正确分离，但列内的进度条（`width: 100%`）横穿整个屏幕，跨越多个列。  
**根因**：AUI 的 Grid 布局虽然支持固定 `px` 列宽，但**子元素的 `width: 100%` 不受 Grid 列宽约束**。Grid 列宽只是设置了轨道大小，子元素的内容仍可溢出到相邻列。  
**修复**：放弃 Grid，改用 **Flexbox + 固定宽度**：
```css
.info-bar { display: flex; flex-direction: row; }
#status-col { width: 180px; flex-shrink: 0; }
#warning-col { width: 120px; flex-shrink: 0; }
#extra-col { width: 120px; flex-shrink: 0; }
```
同时在 `.info-col` 和 `.stat-bar` 上添加 `overflow: hidden` 确保内容不溢出。  
**教训**：AUI 的 Grid 布局**不适合需要严格约束子元素宽度的场景**。Flexbox + `flex-shrink: 0` + 固定 `width` 是更可靠的选择。

---

## 十三、当前实现进度（2026-05-28）

### 13.1 已完成的阶段

| 阶段 | 状态 | 说明 |
|------|------|------|
| 阶段 1 — 基础框架 | ✅ 基本完成 | Tab 切换路由、header 折叠效果、Tab 04 禁用、200ms 防抖 |
| 阶段 2a — 控制组切换条 | ✅ 完成 | GroupStripRenderer 共享组件，控制组卡片渲染 |
| 阶段 2b — PanelOverview | ✅ 基本完成 | 状态条 + 列标题 + 控制模式信息，缺失 3D 预览 |
| 阶段 2c — PanelDeviceControl | ✅ 基本完成 | PULSE/TOGGLE/SLIDER 控件渲染，缺失 3D 预览 |
| 阶段 4 — Tab 04 库存 | ✅ 占位 | 空 div 占位，按钮禁用 |
| Mock 数据 | ✅ 完成 | `ControlDataAccessor` 使用 `VehicleInfoPanelManager` 中的丰富 Mock 数据（4 组 + 6 GUI 控件） |

### 13.2 进行中的阶段

| 阶段 | 状态 | 说明 |
|------|------|------|
| 阶段 1 — CSS 样式移植 | ⏳ 持续迭代 | Neo-Brutalism 风格已基本移植，正在配合效果图微调间距/配色/比例 |

### 13.3 待完成的阶段

| 阶段 | 优先级 | 说明 |
|------|--------|------|
| 阶段 2 — 3D 预览 | 🟡 中 | 需实现 `VehicleControlScreen.render3dPreview()`，复用 `VehicleAnimatable` |
| 阶段 3 — Tab 03 编辑配置 | 🔴 高 | 三栏布局框架已就绪，但 `PanelConfigEditor.render()` 当前为空实现，需填充 CRUD 逻辑 |
| 阶段 0.5 — 网络包 | 🟡 中 | `ControlGroupSetEditPayload` + `GuiActionPayload` + `MMPayloadRegistry` 注册 |
| 阶段 5 — 真实数据接入 | 🟢 低 | 当前使用 Mock 数据，需切换到真实子系统数据 |

### 13.4 已修复的已知问题

| 问题 | 修复日期 | 修复文件 |
|------|---------|---------|
| body 缺少 `display:flex` 导致 flex 子属性失效 | 2026-05-28 | [vehicle_control.css](file:///d:/Files/Project_MinecraftMods/Machine-Max/src/main/resources/assets/apricityui/apricity/machine_max/vehicle_control.css) |
| info-bar/device-bar 固定 160px 高度比例失调 | 2026-05-28 | [vehicle_control.css](file:///d:/Files/Project_MinecraftMods/Machine-Max/src/main/resources/assets/apricityui/apricity/machine_max/vehicle_control.css) |
| header 折叠效果缺失，所有 Tab 等宽显示 | 2026-05-28 | [vehicle_control.css](file:///d:/Files/Project_MinecraftMods/Machine-Max/src/main/resources/assets/apricityui/apricity/machine_max/vehicle_control.css) + [PanelTabBar.java](file:///d:/Files/Project_MinecraftMods/Machine-Max/src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/panel/PanelTabBar.java) |
| info 列缺少标题（VEHICLE STATUS / WARNINGS / CONTROL MODE） | 2026-05-28 | [PanelOverview.java](file:///d:/Files/Project_MinecraftMods/Machine-Max/src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/panel/PanelOverview.java) |
| Toggle 行顺序反了（标签or开关） | 2026-05-28 | [PanelDeviceControl.java](file:///d:/Files/Project_MinecraftMods/Machine-Max/src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/panel/PanelDeviceControl.java) |
| 控制组卡片主题色不随 Tab 切换 | 2026-05-28 | [vehicle_control.css](file:///d:/Files/Project_MinecraftMods/Machine-Max/src/main/resources/assets/apricityui/apricity/machine_max/vehicle_control.css)（CSS 变量方案） |
| Header 主题色不随 Tab 切换 | 2026-05-28 | [PanelTabBar.java](file:///d:/Files/Project_MinecraftMods/Machine-Max/src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/panel/PanelTabBar.java)（TAB_ACCENT 数组） |
| info 列宽度分配不合理（warning 列空时被挤压） | 2026-05-28 | [vehicle_control.css](file:///d:/Files/Project_MinecraftMods/Machine-Max/src/main/resources/assets/apricityui/apricity/machine_max/vehicle_control.css)（flex:3/2/2 分配） |
| AUI 热重载后面板内容丢失 | 2026-05-28 | [VehicleControlScreen.java](file:///d:/Files/Project_MinecraftMods/Machine-Max/src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/screen/VehicleControlScreen.java)（tick UUID 检测） |
| 控制组 BASE 卡片非白底/编号错误/状态文本错误 | 2026-05-28 | [GroupStripRenderer.java](file:///d:/Files/Project_MinecraftMods/Machine-Max/src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/panel/GroupStripRenderer.java)（base 类 + GROUP 00 + ALWAYS ACTIVE） |
| 导航栏激活态白色背景 + 大号黑色编号 | 2026-05-28 | [vehicle_control.css](file:///d:/Files/Project_MinecraftMods/Machine-Max/src/main/resources/assets/apricityui/apricity/machine_max/vehicle_control.css) + [PanelTabBar.java](file:///d:/Files/Project_MinecraftMods/Machine-Max/src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/panel/PanelTabBar.java) |
| 信息栏三列宽度不均（进度条撑开列宽） | 2026-05-28 | [vehicle_control.css](file:///d:/Files/Project_MinecraftMods/Machine-Max/src/main/resources/assets/apricityui/apricity/machine_max/vehicle_control.css)（改用 grid 固定列宽） |
| 导航栏激活态标题被相邻数字遮挡 | 2026-05-28 | [vehicle_control.css](file:///d:/Files/Project_MinecraftMods/Machine-Max/src/main/resources/assets/apricityui/apricity/machine_max/vehicle_control.css)（overflow:hidden + z-index:1） |

---

## 附录 A：3D 载具预览技术方案

### A.1 概述

VehicleControlScreen 的 3D 预览使用 [`VehicleAnimatable`](file:///d:/Files/Project_MinecraftMods/Machine-Max/src/main/java/io/github/sweetzonzi/machine_max/common/visual/VehicleAnimatable.java) 渲染**整车**而非单个零件。`VehicleAnimatable` 是整车级动画体，内部持有 `Map<Integer, SubPartAnimatable>`，每个 `SubPartAnimatable` 通过 `getRenderWorldPositionMatrix()` 返回模型原点的世界位姿矩阵，直接作为 `poseStack.mulPose()` 的参数。

参考实现：
- [`CustomModelItemRenderer.renderVehicleAnimatable()`](file:///d:/Files/Project_MinecraftMods/Machine-Max/src/main/java/io/github/sweetzonzi/machine_max/client/render/renderer/CustomModelItemRenderer.java) — 物品 GUI 中的整车渲染
- [`PartAssemblyRenderer.renderVehicleProjection()`](file:///d:/Files/Project_MinecraftMods/Machine-Max/src/main/java/io/github/sweetzonzi/machine_max/client/render/renderer/PartAssemblyRenderer.java) — 世界放置预览中的整车渲染

### A.2 数据获取链路

从当前控制子系统回溯到 `VehicleCore`，构造 `VehicleAnimatable`：

```
ControlDataAccessor.getCurrentControlSet(mc)
  → sub (AbstractControllableSubsystem)
    → sub.getOwner().getSubPart()
      → .part
        → .vehicle            ← VehicleCore
          → new VehicleAnimatable(vehicle)
```

关键方法调用：
1. `new VehicleAnimatable(vehicle)` — 从 VehicleCore 构造，自动读取 PartData/SubPartData
2. `alignToAxesByFirstSubPart()` — 以最小 ID 零件为基准，将整车旋转归零、居中到质心（GUI 预览核心步骤）
3. `setTransform(new Transform())` — 重置到原点

### A.3 核心渲染流程

```
┌────────────────────────────────────────────────────────────┐
│  VehicleControlScreen.renderBackground()                    │
│                                                             │
│  1. 从 VehicleCore 构造 VehicleAnimatable（仅首次打开时）   │
│     vehicleAnim = new VehicleAnimatable(vehicle)            │
│     vehicleAnim.alignToAxesByFirstSubPart()                 │
│     vehicleAnim.setTransform(new Transform())               │
│                                                             │
│  2. 设置投影变换（Screen 空间，无需摄像机偏移）             │
│     poseStack.pushPose()                                    │
│     ├── translate(预览区域中心X, 预览区域中心Y, 500)       │
│     │   // Z=500 确保在 GUI 深度中位于 AUI 面板后方        │
│     ├── scale(previewZoom, previewZoom, previewZoom)       │
│     │   // 正数缩放，无需镜像；GUI Z 轴默认指向屏幕内      │
│     ├── mulPose(视角旋转)  // 用户拖拽控制                 │
│     └── scale(全局适配缩放, 全局适配缩放, 全局适配缩放)    │
│                                                             │
│  3. 遍历所有 SubPartAnimatable                              │
│     for (subPart : vehicleAnim.subParts.values())           │
│       poseStack.pushPose()                                  │
│       poseStack.mulPose(subPart.getRenderWorldPositionMatrix(partialTick))
│       ├── 此矩阵已包含：质心→模型原点偏移                  │
│       ├── 骨骼直接在模型坐标空间定义                         │
│       └── 因此直接遍历 bones 渲染即可                       │
│       for (bone : subPart.getBones().values())              │
│         ModelRenderHelperKt.render(bone, ...)               │
│       poseStack.popPose()                                   │
│                                                             │
│  4. 结束绘制                                                │
│     poseStack.popPose()                                     │
│     bufferSource.endBatch()  // 提交批处理                   │
└────────────────────────────────────────────────────────────┘
```

**关键点**：与 `PartEntityRenderer`（世界渲染器）不同，Screen 中的 3D 预览**不需要** `poseStack.translate(-camPos.x, -camPos.y, -camPos.z)` 抵消摄像机偏移，因为 Screen 的 `GuiGraphics.poseStack` 已是屏幕投影空间。

### A.4 VehicleControlScreen 中的 3D 渲染代码

```java
// ── 字段 ──
private VehicleAnimatable vehicleAnimatable;  // 缓存的载具动画体
private float previewScale = 1.0f;             // 整体缩放系数

private void buildVehicleAnimatable() {
    // 从 ControlDataAccessor 链路回溯到 VehicleCore
    Minecraft mc = Minecraft.getInstance();
    if (!(mc.player instanceof IEntityMixin mixin)) return;
    AbstractControllableSubsystem sub = mixin.machine_Max$getControllingSubsystem();
    if (sub == null) return;
    SubPart subPart = sub.getOwner().getSubPart();
    if (subPart == null || subPart.part == null || subPart.part.vehicle == null) return;
    VehicleCore vehicle = subPart.part.vehicle;

    // 构造 VehicleAnimatable 并对齐到预览姿态
    vehicleAnimatable = new VehicleAnimatable(vehicle);
    vehicleAnimatable.alignToAxesByFirstSubPart();
    vehicleAnimatable.setTransform(new Transform());
}

private void render3dPreview(GuiGraphics graphics, float partialTick) {
    if (vehicleAnimatable == null) {
        buildVehicleAnimatable();
    }
    if (vehicleAnimatable == null || vehicleAnimatable.getSubParts().isEmpty()) return;

    PoseStack poseStack = graphics.pose();
    MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

    // 预览区域位置和尺寸（Tab 01/02 时的屏幕右侧区域）
    int previewX = (int)(width * 0.52f);
    int previewY = (int)(height * 0.08f);
    int previewW = (int)(width * 0.46f);
    int previewH = (int)(height * 0.84f);

    poseStack.pushPose();
    // 移动到预览区域中心，Z 轴推到 GUI 后方（AUI 面板之后）
    poseStack.translate(previewX + previewW / 2f, previewY + previewH / 2f, 500);
    // 整体缩放（鼠标滚轮控制）
    poseStack.scale(previewScale, previewScale, previewScale);
    // 视角旋转（鼠标拖拽控制）
    poseStack.mulPose(Axis.XP.rotationDegrees(previewRotX));
    poseStack.mulPose(Axis.YP.rotationDegrees(previewRotY));

    // 自动适配整车到预览区域
    float fitScale = resolveVehicleFitScale(vehicleAnimatable, previewW, previewH);
    poseStack.scale(fitScale, fitScale, fitScale);

    // 渲染每个 SubPart
    for (SubPartAnimatable subPart : vehicleAnimatable.getSubParts().values()) {
        poseStack.pushPose();
        // getRenderWorldPositionMatrix 返回模型原点的世界位姿矩阵（含质心偏移补偿）
        poseStack.mulPose(subPart.getRenderWorldPositionMatrix(partialTick));
        for (OBone bone : subPart.getBones().values()) {
            ModelRenderHelperKt.render(
                bone,
                subPart.getModelInstance().getPose(),
                poseStack,
                bufferSource.getBuffer(MMRenderTypes.alwaysVisibleSolid()),
                Brightness.FULL_BRIGHT.pack(),
                OverlayTexture.NO_OVERLAY,
                0xFF_FFFFFF,  // 全不透明
                partialTick,
                false
            );
        }
        poseStack.popPose();
    }

    poseStack.popPose();
    bufferSource.endBatch();
}

private float resolveVehicleFitScale(VehicleAnimatable vehicle, float targetW, float targetH) {
    // 遍历所有 SubPart 的 AABB，计算最佳适配缩放
    // 参考 PartAssemblyRenderer 中的类似计算
    // 简单实现：取一个经验基准值，后续可以根据实际载具尺寸调整
    return 0.8f;
}
```

### A.5 与 CustomModelItemRenderer 的关键差异

| 方面 | CustomModelItemRenderer（物品 GUI） | VehicleControlScreen（本方案） |
|------|-----------------------------------|-------------------------------|
| 渲染时机 | `ItemRenderer` 管线（GUI/手部/地面） | `Screen.renderBackground()` |
| 上下文 | `PoseStack` + `ItemDisplayContext` | `GuiGraphics.poseStack` |
| 缩放 | `vehicleScale` 来自物品 NBT 缩尺比 | `previewScale` 由鼠标滚轮控制 |
| 视角 | 固定 45° 旋转 | 用户拖拽自由旋转 |
| 对齐 | 居中到物品槽 (0.5, 0.5, 0.5) | 居中到预览区域中心 |
| 生命周期 | 每帧按物品数据重建 | Screen init 时创建，onClose 时释放 |

### A.6 视角控制（鼠标交互）

- **拖拽旋转**：`mouseDragged()` 中累加 `previewRotX` / `previewRotY`
  - `previewRotX` 范围 `[-90°, 90°]`（俯仰）
  - `previewRotY` 范围 `[-180°, 180°]`（偏航）
- **滚轮缩放**：`mouseScrolled()` 中调整 `previewScale`（范围 0.25 ~ 5.0）
- **自动适配**：`resolveVehicleFitScale()` 根据整车 AABB 计算初始缩放倍数
- **初始视角**：默认从左前上方观察（`rotX=25°, rotY=-45°`）
- **重置**：双击预览区域恢复默认视角

### A.7 依赖的工具方法

| 方法 | 来源 | 用途 |
|------|------|------|
| `VehicleAnimatable(vehicle)` | [VehicleAnimatable.java:70](file:///d:/Files/Project_MinecraftMods/Machine-Max/src/main/java/io/github/sweetzonzi/machine_max/common/visual/VehicleAnimatable.java#L70) | 从 VehicleCore 构造整车动画体 |
| `alignToAxesByFirstSubPart()` | [VehicleAnimatable.java:172](file:///d:/Files/Project_MinecraftMods/Machine-Max/src/main/java/io/github/sweetzonzi/machine_max/common/visual/VehicleAnimatable.java#L172) | 整车旋转归零，居中到质心 |
| `getRenderWorldPositionMatrix(partialTick)` | `SubPartAnimatable` | 模型原点世界位姿（含质心偏移补偿） |
| `getBones()` | `SubPartAnimatable` | 获取过滤后的骨骼列表 |
| `ModelRenderHelperKt.render(bone, ...)` | Spark-Core | 渲染单个骨骼 |
| `MMRenderTypes.alwaysVisibleSolid()` | Machine-Max | 始终可见的实体渲染类型 |
| `bufferSource.endBatch()` | Minecraft | 提交渲染批处理 |

### A.8 性能注意事项

- Screen 的 `renderBackground()` 每帧调用，3D 渲染可能影响帧率
- `bufferSource.endBatch()` 在每帧渲染结束时调用，确保批处理提交
- 整车可能有 2-10 个 SubPart，每个 10-50 个骨骼，总骨骼数通常在 100 以内，开销可控
- 如果发现性能问题，可以：
  - 降低预览帧率（例如每 2 帧渲染一次）
  - 使用 `MMRenderTypes` 的简化渲染类型
  - 仅在 Tab 01/02 激活时渲染（已实现）
