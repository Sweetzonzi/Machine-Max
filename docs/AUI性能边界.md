# AUI（ApricityUI）性能边界与能力调研

**版本**: v1.1.3dev · NeoForge 1.21.1  
**调研日期**: 2026-05-28（第二次调研，对比 v1.1.2 → v1.1.3dev 变更）  
**源码路径**: [`d:\Files\Project_MinecraftMods\AUI`](file:///d:/Files/Project_MinecraftMods/AUI)  
**目的**: 评估 AUI 作为 Machine-Max 载具控制面板渲染引擎的可行性、性能极限与限制

---

## 一、概述

AUI 是一个使用 HTML + CSS + JS 构建 Minecraft UI 的框架。**底层渲染引擎是纯 Java 实现的 DOM/CSS 布局引擎 + CPU 绘制**（通过 Minecraft 的 `PoseStack`），**非 WebView**，不依赖任何浏览器内核。

> **JavaScript 注意事项**：AUI 的 `<script>` 标签和 JS `eval()` 功能依赖 **KubeJS** 模组（通过 Rhino JS 引擎执行）。**本项目中未引入 KubeJS 依赖**，因此 `vehicle_control.html` 等模板中**不应使用 `<script>` 标签**。所有交互逻辑均通过 Java DOM API（`document.querySelector`/`element.addEventListener`/`element.setAttribute` 等）实现。详见 [3.5 JS / Java DOM 操作](#35-js--java-dom-操作)。

**版本演进**（最近 40 次提交）：
- **`v1.1.3dev`** — **CSS 变量 `var()` 自动解析**（`Style.resolveVarReferences()`）、`visibility` 继承修复、容器屏幕重构（`SlotDataBinder` 分离）、字体渲染深度修复、滚动抖动修复、Mask 蒙版支持物品纹理裁剪、`BodyRenderNodeProvider`、stencil buffer
- `v1.1.2` — `FollowFacingWorldWindow`、`Selector.Index` 选择器索引缓存、`StyleFrameCache` 渲染阶段动画推进
- `v1.1.1` — NeoForge 版本追更
- `v1.1.0` — Maven 发布，WorldWindow z-fighting 修复
- 之前：1.20.1 到 1.21.1 移植

---

## 二、CSS 变量（`--*`）完整分析

### 2.1 存储机制

在 [`Style.java`](file:///d:/Files/Project_MinecraftMods/AUI/src/main/java/com/sighs/apricityui/init/Style.java) 中，CSS 变量存储在与标准属性独立的 `HashMap` 中：

```java
// Style.java:136
private Map<String, String> customProperties = new HashMap<>();

// 写入（Style.update() line 478-481）
if (name.startsWith("--")) {
    customProperties.put(normalizeCustomPropertyName(name), value);
    return;  // ← 直接返回，不经过标准属性 Field 反射
}

// 读取（Style.get() line 632-636）
if (name.startsWith("--")) {
    return customProperties.get(normalizeCustomPropertyName(name));
}

// 独立读取方法（Style.getCustomProperty() line 650-653）
public String getCustomProperty(String name) {
    return customProperties.get(normalizeCustomPropertyName(name));
}
```

关键行为：
- `normalizeCustomPropertyName()`: 无论传入 `foo` 还是 `--foo`，统一以 `--` 开头的 key 存储
- `clone()` 时做深拷贝：`style.customProperties = new HashMap<>(this.customProperties)`
- `toCss()` / `toString()` 会序列化所有自定义属性

### 2.2 继承机制

[`Element.java`](file:///d:/Files/Project_MinecraftMods/AUI/src/main/java/com/sighs/apricityui/init/Element.java) 暴露两个读取方法：

```java
// 2.2a — 只读当前元素自身的 customProperties（不冒泡）
// Element.java:188-190
public String getCustomProperty(String name) {
    return getRawComputedStyle().getCustomProperty(name);
}

// 2.2b — 沿父链冒泡查找（Slots 大量使用）
// Element.java:207-215
public String getCustomPropertyInherit(String name) {
    Element current = this;
    while (current != null) {
        String value = current.getCustomProperty(name);
        if (value != null && !value.isBlank()) return value;
        current = current.parentElement;
    }
    return null;
}
```

### 2.3 `var()` 自动解析（v1.1.3dev 新增）

**这是本次更新的核心变化。`var()` 在 CSS 属性值中现在会被自动解析替换。**

#### 2.3.1 解析入口

在 `Element.getRawComputedStyle()` 中，CSS 缓存构建完成后立即调用 `resolveVarReferences()`：

```java
// Element.java:344-359
public Style getRawComputedStyle() {
    Style computedStyle;
    Style cache = renderElement.computedStyle.get();
    if (cache != null) {
        computedStyle = cache;
    } else {
        computedStyle = new Style();
        cssCache.forEach(computedStyle::update);
        computedStyle.merge(getAttribute("style"));
        // 先缓存当前构建中的 Style，避免 var() 解析阶段递归进入
        renderElement.computedStyle.set(computedStyle);
        computedStyle.resolveVarReferences(this);  // ← v1.1.3dev 新增
        isPointerEnabled = computedStyle.pointerEvents.equals("auto");
        isVisible = Style.isVisible(this);
    }
    return computedStyle;
}
```

#### 2.3.2 解析算法

[`Style.java#L660-L792`](file:///d:/Files/Project_MinecraftMods/AUI/src/main/java/com/sighs/apricityui/init/Style.java#L660-L792)：

```java
public void resolveVarReferences(Element context) {
    for (Field field : STYLE_FIELDS) {     // 遍历所有 String 类型字段
        String value = (String) field.get(this);
        if (value == null || !value.contains("var(")) continue;
        String resolved = resolveVarInValue(value, context, 0);
        if (!resolved.equals(value)) {
            field.set(this, resolved);      // 直接修改字段值
        }
    }
}
```

**解析能力**：

| 特性 | 支持 |
|------|------|
| 基本引用 `var(--name)` | ✅ |
| Fallback `var(--name, default)` | ✅ 以顶层逗号为界 |
| 嵌套 var `var(--a, var(--b))` | ✅ 递归解析 |
| 非标准属性字段 | ✅ 仅含 `var(` 的字段才处理 |
| 最大递归深度 | 8 层（`VAR_MAX_DEPTH`） |
| 嵌套括号处理 | ✅ 深度计数找匹配 `)` |

#### 2.3.3 变量查找链（`lookupVar()`）

```java
// Style.java:776-792
private String lookupVar(String varName, Element context) {
    // 1. 先查当前 Style 自身的 customProperties
    String local = customProperties.get(normalized);
    if (local != null && !local.isBlank()) return local;

    // 2. 沿 DOM 继承链向上查找（使用 getRawCustomProperty 避免重入）
    Element current = context;
    while (current != null) {
        String inherited = current.getRawCustomProperty(normalized);
        if (inherited != null && !inherited.isBlank()) return inherited;
        current = current.parentElement;
    }
    return null;
}
```

查找优先级：
1. **当前 Style 的 `customProperties`**（CSS 选择器匹配 + inline style 中的 `--*` 声明）
2. **DOM 父链**（通过 `Element.getRawCustomProperty()`，该方法是 v1.1.3dev 新增，用于避免重入 computed style 构建）

> **`getRawCustomProperty()` 与 `getCustomProperty()` 的区别**：
> - `getCustomProperty()` 会触发完整的 `getRawComputedStyle()`，可能递归进入 `resolveVarReferences()`
> - `getRawCustomProperty()` 在 var() 解析过程中使用，如果已有缓存则读取缓存，否则构建一个**临时** Style（不触发 `resolveVarReferences()`）仅用于提取 custom property 值

#### 2.3.4 使用示例

现在可以直接在 CSS 中写：
```css
container[layout="preset:player"] {
    --aui-player-gap: 1px;
    --aui-player-columns: 9;
    display: grid;
    gap: var(--aui-player-gap);           /* ✅ 自动解析为 "1px" */
    grid-template-columns: var(--aui-player-columns);  /* ✅ 自动解析为 "9" */
}

.page.tab-overview {
    --theme-bg: #141008;
    --theme-accent: #C89520;
}
.page.tab-overview .info-col {
    background-color: var(--theme-bg);     /* ✅ 从父元素继承 */
}
```

### 2.4 剩余限制

| 限制 | 说明 |
|------|------|
| 查找链只在**当前 Document** 内 | 不跨 Document |
| 不包含 `@property` 规则 | AUI 不支持 `@property`，变量类型始终为 String |
| var() 替换后仍是 String | 不进行类型校验，`var(--size)` 解析后值仍由 `Size.parse()` 后续处理 |
| 不支持 `var()` 在 CSS 选择器中 | 只在属性值中有效 |

---

## 三、帧调度与渲染管线

### 3.1 双阶段帧调度（FrameScheduler）

[`FrameScheduler.java`](file:///d:/Files/Project_MinecraftMods/AUI/src/main/java/com/sighs/apricityui/init/FrameScheduler.java) 实现了 **tick（逻辑）** 与 **render（渲染）** 分离：

```mermaid
tick() [ClientTick 事件]
  ├── StyleAsyncHandler.INSTANCE.tickApplyQueue()    — 样式异步加载结果 apply
  ├── ImageAsyncHandler.INSTANCE.tickApplyQueue()    — 图片异步解码结果 apply
  └── Document.tickFrame() [对每个 Document]
       ├── commitStyleRecalc()    — flushPendingStyleUpdates() 统一刷新
       ├── stepMotion()           — 动画/过渡 tick 推进（当前 no-op）
       ├── tickElements()         — 滚动缓动、文本变化检测、逐帧逻辑
       ├── commitStyleRecalc()    — 二次刷新（tick 中脚本可能产生新失效）
       ├── stepMotion()           — 二次推进
       └── commitRenderState()    — dirty flags → paintList 增量更新

render() [ScreenEvent.Render.Post]
  ├── FrameScheduler.renderBegin()   — Drain RenderSystem fenced tasks
  ├── RectFrameCache.begin()         — Rect 帧缓存激活
  ├── StyleFrameCache.begin()        — Motion 帧缓存激活
  ├── FilterRenderer.beginFrame()    — 滤镜帧开始
  ├── document.stepMotionRender()    — 动画/过渡推进，写入 StyleFrameCache
  ├── paintList.forEach(Node.render) — 实际绘制
  └── StyleFrameCache.end() / FilterRenderer.endFrame()
```

### 3.2 样式失效机制

**设计原则**：所有样式变更延迟到 tick 阶段统一处理，避免输入事件/渲染路径中频繁重算 CSS。

```java
// Element.java — 样式失效入口
public final void invalidateStyle() {
    invalidateStyleCaches();    // 清空 computedStyle 缓存
    requestStyleRecalc();       // 入队 pendingStyleRoots
}

// Document.java — 统一刷新
public void flushPendingStyleUpdates() {
    ArrayList<Element> candidates = new ArrayList<>(pendingStyleRoots);
    pendingStyleRoots.clear();
    candidates.sort(Comparator.comparingInt(Element::getDepth));
    // 按深度排序，去重（祖先覆盖子孙），逐个 recomputeStyleSubtree
    for (Element root : roots) {
        recomputeStyleSubtree(root);  // DFS 遍历子树
    }
}
```

**`recomputeStyleSelf()` 路径**：
```java
void recomputeStyleSelf() {
    Style originStyle = getComputedStyle();           // 旧值（含 motion）
    cssCache = Selector.matchCSS(this);               // CSS 选择器匹配
    invalidateStyleCaches();                          // 清空 computedStyle/FrameCache
    Style currentStyle = getRawComputedStyle();       // 新值（重组 cssCache + inline style）
    RenderElement.observeStyle(this, originStyle, currentStyle);  // 差异检测 → 设置 dirty flags
    Transition.create(this, originStyle, currentStyle);  // 启动过渡
}
```

#### 3.2.1 `visibility` 继承修复（v1.1.3dev）

此前 `visibility` 属性仅作用于当前元素，不会沿 DOM 树继承。v1.1.3dev 修复了此问题：

- `Style.visibility` 默认值从 `"visible"` 改为 `"unset"`
- 新增 `Style.getVisibility(Element)` — 沿 `element.getRoute()` 向上查找第一个显式 visibility
- 新增 `Style.isVisible(Element)` — 替代 Element 字段的 `isVisible` 判断
- `RenderNode.shouldRender()` 改为调用 `target.isVisible`（由 `getRawComputedStyle()` 设置）
- 在 `Style.update()` 中新增 `visibility` 的归一化处理

```java
// Style.java:501-503
if ("visibility".equals(styleName)) {
    value = normalizeVisibility(value);
}
```

这意味着 `display:none` 的行为不变（子树完全不进入 paintList），而 `visibility:hidden` 现在正确继承到子元素。

### 3.3 脏标记系统（Dirty Flags）

```java
// Drawer.java
public static final int REPAINT = 1;   // 需重绘
public static final int REORDER = 2;   // 需重建 paintList
public static final int RELAYOUT = 4;  // 需重新布局
```

在 [`RenderElement.observeStyle()`](file:///d:/Files/Project_MinecraftMods/AUI/src/main/java/com/sighs/apricityui/init/RenderElement.java#L123-L253) 中，新旧样式的差异被归类并映射到 dirty flags：

| 样式变化 | 触发 dirty flag | 影响 |
|---------|----------------|------|
| `width` / `height` / `margin` / `flex` / `grid` | `RELAYOUT` | 清空 size/position 缓存，重建布局 |
| `padding` / `border` | `RELAYOUT` | 清空 size/box 缓存 |
| `font-size` / `line-height` | `RELAYOUT` + `REPAINT` | 清空 text/wrappedText 缓存 |
| `color` / `background` / `box-shadow` | `REPAINT` | 仅重绘 |
| `z-index` | `REORDER` | 重建绘制队列子树 |
| `overflow` / `filter` / `clip-path` 存在性变化 | `REORDER` | Mask/Filter/ClipPath 节点增删 |
| `transform` / `opacity` | `REPAINT` | 清空 transform/opacity 缓存 |

### 3.4 paintList 增量更新

[`Drawer.flushUpdates()`](file:///d:/Files/Project_MinecraftMods/AUI/src/main/java/com/sighs/apricityui/init/Drawer.java#L17-L53) 不会全量重建绘制列表：

1. 收集所有 dirty 元素，按深度排序
2. 对 `RELAYOUT` 元素：清空自身及祖先的 size/position 缓存
3. 对 `REORDER` 元素：找到最近的层叠上下文根节点 → **最小化重建**（只重建受影响的子树）
4. 用 `updateGlobalPaintList()` 将新子树替换到全局 paintList 中

### 3.5 动画与过渡推进

**动画**（`@keyframes`）和 **过渡**（`transition`）的推进在 **render 阶段** 通过 `stepMotionRender()` 完成：

```java
// Document.java:252-306
public void stepMotionRender() {
    if (!StyleFrameCache.isActive()) return;
    for (Map.Entry<Element, Integer> entry : motionFlags.entrySet()) {
        Style base = element.getRawComputedStyle();
        Style animated = base.clone();
        // 过渡推进
        boolean stillActive = Transition.updateStyle(element, animated);
        // 动画推进
        Animation.updateStyle(element, animated);
        // 写入当帧缓存（不修改元素本身的 rawComputedStyle）
        StyleFrameCache.put(element, animated);
    }
}
```

**已知限制**：过渡（`transition`）源码标注"似乎不大好用"，推荐 **用 Java class/style 切换或 `@keyframes` 动画替代**。

### 3.6 paintList 绘制的层叠顺序

[`Drawer.processStackingContext()`](file:///d:/Files/Project_MinecraftMods/AUI/src/main/java/com/sighs/apricityui/init/Drawer.java#L75-L178) 实现的绘制顺序：

```
对每个层叠上下文：
  1. clip-path push（如有）
  2. backdrop-filter（如有，仅占位）
  3. filter push（如有）
  4. BORDER phase
  5. SHADOW phase
  6. mask push（overflow: hidden）
  7. BodyRenderNodeProvider 节点（如有，v1.1.2 新增）
  8. BODY phase
  9. 子元素按 z-index 排序绘制：
     a. 负 z-index（升序）
     b. 普通流（DOM 顺序）
     c. auto/0 z-index 层叠上下文（DOM 顺序）
     d. 正 z-index（升序）
  10. mask pop
  11. filter pop
  12. clip-path pop
```

---

## 四、CSS 引擎能力

### 4.1 布局模式

| 布局 | 支持度 | 细节 |
|------|--------|------|
| Flexbox | ✅ 完整 | `flex-direction/flex-wrap/justify-content/align-items/gap/flex-grow/shrink/basis` |
| Grid | ✅ 有限 | `grid-template-columns/rows` 仅支持 `px` 和 `auto`（**不支持 `fr`**），支持 span |
| Normal Flow | ✅ | block（换行堆叠）、inline/inline-block（同行） |
| Position | ✅ | `static/relative/absolute/fixed` |
| Overflow | ✅ | `visible/hidden/scroll/auto`，带缓动滚动动画 |
| z-index | ✅ | 堆叠上下文在 `position != static` 或 `z-index != auto` 时创建 |

**不支持**：
- ❌ `float` / `clear`
- ❌ `position: sticky`
- ❌ `fr` 单位（grid）
- ❌ `grid-template-areas`
- ❌ `calc()` / `minmax()` / `clamp()` / `min()` / `max()`

### 4.2 CSS 属性支持

| 类别 | 支持的属性 | 单位限制 |
|------|-----------|---------|
| 尺寸 | `width/height/min-max-*/box-sizing` | **仅 `px` 和 `%`**。不支持 `em/rem/vw/vh/vmin/vmax` |
| 盒模型 | `margin/padding/border/border-radius` | 简写仅支持单值（如 `padding: 10px`），**多值简写不支持**（如 `padding: 0 10px` 或 `padding: 0 10px 4px` 均不生效，各部分会塌缩为 0）。必须用分方向属性 `padding-left/padding-right` 等 |
| 背景 | `background-color/image/repeat/size/position` | `url()` + `linear-gradient()`。<br>**⚠️ `background` 简写不支持 `var()**`：`background: var(--x)` 不会填充背景色。<br>**必须用 `background-color: var(--x)` 替代**（v1.1.3dev 之前 `var()` 在简写中完全不被识别，见 §8.5 #12）<br>**⚠️ `linear-gradient()` 内的颜色不能包含空格**：`Gradient.parse()` 用 `split("\\s+")` 切分颜色 stop，`rgba(r, g, b, a)` 带空格会被切碎。必须用 `rgba(r,g,b,a)` 无空格格式或 `#hex`。见 §8.5 #13。 |
| 文本 | `color/font-size/font-weight/font-family/line-height/text-align/letter-spacing/white-space/text-overflow:ellipsis/text-stroke` | 字号公式：`fontSize/16*9` |
| 变换 | `transform: translate/rotate/scale`（含 3D 变体） | 默认 `transform-origin` 为中心 |
| 滤镜 | `filter: blur/brightness/grayscale/invert/hue-rotate/opacity/drop-shadow` | CPU 实现 |
| 阴影 | `box-shadow`（多值逗号分隔） | |
| 光标 | `cursor: default/pointer/text/crosshair/ew-resize/ns-resize` + `url()` | 自定义光标使用伪光标渲染 |
| 动画 | `@keyframes` + `animation-*`（duration/delay/iteration-count/direction/fill-mode/timing-function） | 只支持 `linear` 和 `steps()` 缓动。<br>**⚠️ 选择器必须用百分比**：不支持 `from`/`to` 关键字。`from { ... }` 会导致 `NumberFormatException`，必须写 `0% { ... }`。见 §8.5 #16。 |
| 过渡 | `transition` | ⚠️ 不稳定，推荐用 class 切换替代 |

### 4.3 CSS 选择器支持

| 选择器类型 | 支持 | 示例 |
|-----------|------|------|
| 元素选择器 | ✅ | `div`, `span` |
| ID 选择器 | ✅ | `#my-id` |
| 类选择器 | ✅ | `.my-class` |
| 后代选择器 | ✅ | `.parent .child` |
| 子选择器 | ✅ | `.parent > .child` |
| 复合类选择器 | ✅ | `.class1.class2` |
| 属性选择器 | ✅ | `[attr]`, `[attr=value]` |
| 伪类: hover/active/focus | ✅ | |
| 伪类: first-child/last-child/nth-child | ✅ | `:nth-child(odd)` |
| 伪类: empty/checked | ✅ | |
| 伪类: enabled/disabled/required | ✅ | |
| **不支持**： | ❌ | `+`（相邻兄弟）、`~`（通用兄弟）、`:not()`、`:nth-of-type()`、`::before`、`::after` |

### 4.4 选择器匹配性能

[`Selector.Index`](file:///d:/Files/Project_MinecraftMods/AUI/src/main/java/com/sighs/apricityui/init/Selector.java#L125-L276) 是 **v1.1.2 的关键优化**：

- 按 id / class / tag / pseudo / attr 建立候选规则索引
- `match()` 只扫描匹配索引键的规则，不扫描全部 CSS 规则
- 复用 `scratchCandidates` + `scratchSeen` 缓冲区，减少分配
- 支持 `Specificity` 优先级排序（id > class/pseudo > tag > order）

匹配路径：
```java
public HashMap<String, String> match(Element element) {
    // 1. 按 element.id → byId 索引加入候选
    // 2. 按 element.getClassNames() → byClass 索引
    // 3. 按 element.tagName → byTag 索引
    // 4. 按伪类激活状态 → byPseudo 索引
    // 5. 按属性名 → byAttr 索引
    // 6. always（兜底）加入候选
    // 7. 对每个候选执行完整的 isMatch() 验证
    // 8. 按 Specificity 排序，合并最终样式
}
```

---

## 五、元素系统

### 5.1 注册的元素类型

| 标签 | Java 类 | 用途说明 |
|------|---------|---------|
| `body` | `Body` | 文档根元素，全局事件接收 |
| `div` / `span` | `Div` / `Span` | 通用容器 |
| `input` | `Input` | 文本输入 / checkbox / radio |
| `textarea` | `TextArea` | 多行文本 |
| `select` / `option` | `Select` / `Option` | 下拉选择器 |
| `a` | `A` | 链接（点击触发 click 事件） |
| `img` | `Img` | 图片渲染，支持资源路径和网络 URL |
| `pre` | `Pre` | 预格式化文本块 |
| `sprite` | `Sprite` | 精灵图动画（src/steps/duration/loop/autoplay/fit） |
| `canvas` | `Canvas` | 2D 渲染画布（完整 CanvasRenderingContext2D API） |
| `slot` | `Slot` | 物品槽（绑定 MC 容器槽位） |
| `container` | `Container` | 容器绑定系统（Player / BlockEntity / Entity / SavedData） |
| `recipe` | `Recipe` | 合成配方展示 |
| `translation` | `Translation` | Minecraft Component.translatable() i18n |

### 5.2 元素生命周期

```
new Element(document, tagName)
  → addTextSelectionEventListeners()     // 构造时注册内建事件
  → Element.init(origin)                 // HTML 解析后，替换为具体子类
    → runInitFromDomOnce(origin)         // 一次性 DOM 初始化钩子
      → onInitFromDom(origin)            // 子类覆盖
    → children.forEach(...)              // 子元素递归 init

→ document.createRelation(child, parent) // 添加到 DOM 树
  → child.invalidateStyle()              // 触发样式计算
  → document.markDirty(parent, RELAYOUT) // 触发父元素重排

→ element.tick()  [每帧 tick 阶段]
  → stepVerticalScroll() / stepHorizontalScroll()  // 缓动滚动
  → innerText 变化检测 → 清空 text/wrappedText 缓存

→ element.drawPhase()  [paintList 遍历时]
  → SHADOW:   box-shadow 绘制
  → BODY:     背景 → 文字选中高亮 → 文字 → 滚动条
  → BORDER:   边框绘制
```

### 5.3 Element 常用 API（不含子类特有方法）

```java
// 选择器
document.querySelector("#id");
document.querySelectorAll(".class");
document.getElementById("id");
element.querySelector("selector");        // ❌ Element 级不支持！只能用 Document 级

// DOM 操作
document.createElement("div");
element.append(child);                    // 追加子元素
element.prepend(child);                   // 头部追加
element.remove();                         // 从 DOM 移除

// 属性
element.setAttribute("id", "val");
element.getAttribute("src");
element.removeAttribute("class");
element.hasAttribute("name");

// 数据
element.innerText = "text";
element.value = "input text";
element.id = "my-id";
element.className = "abc";                // ❌ 不存在！用 classNames 或 setAttribute

// 样式
element.style = new Style();              // inline style 对象
element.setAttribute("style", "...");     // 通过属性设置
element.getComputedStyle();               // 计算后样式（含动画/过渡）
element.getRawComputedStyle();            // 原始计算后样式（无 motion）
element.getCustomProperty("--name");      // CSS 变量
element.getCustomPropertyInherit("--name"); // CSS 变量（继承冒泡）

// 事件
element.addEventListener("click", handler);
element.removeEventListener("click", handler);
element.addEventListener("mousedown", handler, true);  // useCapture 支持

// 遍历
element.parentElement;
element.children;           // CopyOnWriteArrayList
element.getRoute();         // 自身 → body 的 ArrayList
element.getRouteArray();    // 缓存数组版本（无分配）
element.forEachRoute(cb);   // 回调遍历
```

---

## 六、2D Canvas 渲染

[`Canvas`](file:///d:/Files/Project_MinecraftMods/AUI/src/main/java/com/sighs/apricityui/element/Canvas.java) 使用 **Java AWT `Graphics2D`** 渲染，然后上传为 Minecraft `DynamicTexture`：

### 渲染流程

```
Canvas.tick() 或脚本触发
  → Graphics2D API 调用（填充/描边/文字/变换/渐变/图案）
  → 渲染到 BufferedImage
  → 上传到 DynamicTexture（每帧上传）
  → 在 BODY phase 中作为背景图片绘制到屏幕
```

### 支持的操作

| 类别 | API |
|------|-----|
| 形状 | `fillRect/drawRect`, `fillRoundRect/drawRoundRect`, `fillOval/drawOval`, `fillArc/drawArc`, `fillPath/drawPath` |
| 文字 | `fillText/strokeText/measureText`（使用 Java AWT Font） |
| 边框 | `strokeRect/strokeRoundRect/strokeOval/strokeArc/strokePath` |
| 渐变 | `createLinearGradient/createRadialGradient/createConicGradient` |
| 画布状态 | `save/restore/translate/rotate/scale/resetTransform/setTransform` |
| 样式 | `fillStyle/strokeStyle/globalAlpha/lineWidth/font/textAlign/textBaseline` |
| 线帽 | `lineCap: butt/round/square; lineJoin: miter/round/bevel; miterLimit` |
| 阴影 | `shadowBlur/shadowColor/shadowOffsetX/shadowOffsetY` |
| 混合 | `globalCompositeOperation: source-over/source-atop/source-in/source-out/destination-over/destination-atop/destination-in/destination-out/lighter/copy/xor` |

### 性能限制

- 使用 `BufferedImage` + `Graphics2D` 软件渲染
- 每帧通过 `NativeImage` 上传到 GPU（`DynamicTexture.upload()`）
- 适合低频率绘制的自定义视觉效果，不适合每帧全量动画
- 透明度支持通过 `AlphaComposite` 实现

---

## 七、Minecraft 集成特性

### 7.1 Screen / 文档模式

AUI 支持两种显示模式：

| 模式 | 入口 | 渲染方式 | 适用场景 |
|------|------|---------|---------|
| **Screen 文档** | `Document.create(path)` | 全局 `Client.java` 在 `ScreenEvent.Render.Post` 自动绘制 | 叠加 UI、HUD 元素 |
| **World 窗口** | `Document.createInWorld(path)` | `WorldWindow` 在 `RenderLevelStageEvent` 中绘制 | 世界空间铭牌、设备面板 |

**关键定位**：AUI Document 是**全局独立**的，不隶属于任何 Screen。`Client.java` 的全局事件订阅会：
- 自动绘制所有非世界 Document（通过 `ScreenEvent.Render.Post`）
- 自动处理输入事件（通过 `InputEvent.MouseButton/Key`）
- 自动渲染自定义光标

### 7.2 WorldWindow 类型

| 类型 | 说明 | 创建方法 |
|------|------|---------|
| `WorldWindow` | 固定位置、朝向的世界空间 UI | `ApricityUI.createWorldWindow(path, pos, w, h, maxDist)` |
| `FollowFacingWorldWindow` | **v1.1.2 新增**。粘性窗口，自动面朝相机 | `ApricityUI.createFollowFacingWorldWindow(path, pos, w, h, maxDist, followFactor)` |

`FollowFacingWorldWindow` 特点：
- `followFactor ∈ [0, 1]` 控制跟随速度（0 = 固定，1 = 瞬时跟随）
- 自动计算偏航/俯仰面向相机
- 深度 > 0 时跟随视线，≤0 时回退到 basePosition

### 7.3 容器绑定系统

[`Container`](file:///d:/Files/Project_MinecraftMods/AUI/src/main/java/com/sighs/apricityui/instance/element/Container.java) + [`Slot`](file:///d:/Files/Project_MinecraftMods/AUI/src/main/java/com/sighs/apricityui/instance/element/Slot.java) 提供完整的物品栏绑定：

| BindType | 数据源 |
|---------|--------|
| `PLAYER` | 玩家背包（36 格） |
| `BLOCK_ENTITY` | 方块实体 `IItemHandler`（通过 NeoForge Capability API） |
| `ENTITY` | 生物实体 `IItemHandler` |
| `SAVED_DATA` | `ApricitySavedData` 持久化保存的数据 |
| `VIRTUAL_UI` | 无绑定，纯展示 |

**模板系统**（`Container.compileTemplate()`）：从 HTML 模板编译出 `TemplateSpec`，包含：
- 所有 `Container` 的 ID、bindType、必要容量
- 自动分配 slot-index（支持 `repeat` 和 `slot-index` 属性）
- 支持 `layout="preset:player"` 等预设布局
- 弹出菜单时自动生成 `MenuLayoutSpec`（服务端）和 Document（客户端）

**v1.1.3dev 重构**：槽位绑定逻辑从 `ApricityContainerScreen` 分离到独立的 [`SlotDataBinder`](file:///d:/Files/Project_MinecraftMods/AUI/src/main/java/com/sighs/apricityui/instance/screen/SlotDataBinder.java)，新增 `Document.refreshGeneration` 机制跟踪文档刷新状态，避免绑定过时。

### 7.4 字体系统

- 内置字库：`lxgw`（霞鹜文楷），以 `@font-face` 注册于 `global.css`
- 所有字体须通过 `font-family: lxgw` 引用
- 使用 Java AWT 字体加载（非 Minecraft 字体系统）
- 不支持 Google Fonts（无网络请求能力）

### 7.5 网络系统

[`ApricityUINetwork`](file:///d:/Files/Project_MinecraftMods/AUI/src/main/java/com/sighs/apricityui/instance/network/ApricityUINetwork.java) 使用 NeoForge 的 `RegisterPayloadHandlersEvent`：

- 自动扫描 `@NetworkRegister` 注解的类
- 支持三种方向：`COMMON`（双向）、`S2C`（服务端→客户端）、`C2S`（客户端→服务端）
- 通过 `CustomPacketPayload.Type` + `StreamCodec` + `IPayloadHandler` 注册

### 7.6 资源加载与异步

| 加载器 | 资源类型 | 异步 |
|--------|---------|------|
| `Loader("html")` | HTML 模板 | 同步（扫描时加载） |
| `CSS.Extractor` | CSS 外部文件 | 异步（`StyleAsyncHandler`） |
| `ImageAsyncHandler` | 图片解码 | 异步（`ImageAsyncHandler`） |
| `NetworkAsyncHandler` | 远程 URL 资源 | 异步（`NetworkAsyncHandler`） |

---

## 八、性能特征与限制

### 8.1 Thread-Safety 模型

| 数据结构 | 并发策略 |
|---------|---------|
| `Element.children` | `CopyOnWriteArrayList` |
| `Element.EventListener` | `CopyOnWriteArrayList` |
| `Document.dirtyElements` | `ConcurrentHashMap.newKeySet()` |
| `Document.motionFlags` | `ConcurrentHashMap` |
| `Document.documents` | `CopyOnWriteArrayList` |
| `RenderElement.Cache<T>` | 单线程 volatile 模式（tick 生命周期守卫） |
| `Style.customProperties` | 单线程 |
| `Selector.Index.scratch*` | 线程不安全（仅 tick 线程使用） |

### 8.2 已知性能限制

| 限制 | 根因 | 影响评估 |
|------|------|---------|
| **CSS 值全为 String** | 无类型化 CSS 值对象 | 每次 `Size.parse()` 做子串/正则解析，中 |
| **无脏区域渲染** | 每帧遍历整棵 paintList | 数百元素时可能影响帧率，中 |
| **无纹理图集** | 每张图片独立 GL 纹理 | 大量图片时上下文切换，中 |
| **overflow:scroll 每帧计算 clip rect** | 每帧 clip | 频繁滚动时开销，低 |
| **动画/过渡不影响 layout** | motion 只在 render 阶段推进 | 有意为之，低 |
| **Canvas 使用 AWT Graphics2D 软件渲染** | `BufferedImage` → `NativeImage` 上传 | 适合低频绘制，高 |
| **CSS 选择器索引仅按最后一个 component 建索引** | `Selector.Index.addRule()` 策略 | 大部分场景够用，中 |
| **无 `preventDefault()`** | 事件系统不提供 | 需在回调中手动处理，低 |

### 8.3 与其他技术的对比

| 技术 | 渲染引擎 | CSS 支持 | 性能 | 集成复杂度 |
|------|---------|---------|------|-----------|
| **AUI** | Java DOM + PoseStack | CSS 子集 | 中（CPU 绘制） | 低（直接 Java API） |
| **KubeJS UI** | Rhino JS + AUI | 同 AUI | 中 + JS 开销 | 中（需 KubeJS 环境） |
| **Minecraft Screen** | Vanilla GUI 系统 | 无 | 高 | 低 |
| **WebView（ImmersiveEngineering 等）** | OS WebView | 完整 | 中（跨进程） | 高 |

### 8.4 推荐使用场景

| 场景 | AUI 适合度 | 理由 |
|------|-----------|------|
| **载具控制面板（4 Tab）** | ✅ 非常适合 | Flexbox/Grid 布局、事件系统、Java DOM API 完整 |
| **世界空间铭牌/状态面板** | ✅ 适合 | WorldWindow + FollowFacingWorldWindow 原生支持 |
| **库存管理界面** | ✅ 适合 | Container/Slot 绑定系统成熟 |
| **复杂配置编辑器（三栏+列表+表单）** | ✅ 适合 | 动态 DOM 操作、input/select/textarea 控件 |
| **高帧率 2D 动画（≥60fps）** | ⚠️ 有限 | CPU 绘制，Canvas 使用 AWT 软件渲染 |
| **大量动态元素（>500 个）** | ⚠️ 注意 | 无脏区域渲染，每帧全量遍历 |
| **3D 场景中高密度 HUD** | ⚠️ 注意 | WorldWindow 在 3D 中渲染，受 Z-fighting 和距离影响 |

### 8.5 已知坑点（Trap Summary）

| # | 问题 | 描述 |
|---|------|------|
| 1 | body 默认 `display:block` | flex 子属性静默失效，需显式 `display:flex` |
| 2 | `position:absolute` + `height:100%` 链路 | 需要祖先链每层都有确定高度 |
| 3 | Element 没有 `querySelector` | 只有 Document 级提供，子元素必须用 ID 定位 |
| 4 | `transition` 不可靠 | 源码标注，推荐 class 切换或 `@keyframes` |
| 5 | Grid 不支持 `fr` 单位 | 必须用固定 `px` 或 Flexbox |
| 6 | Grid 子元素不受列宽约束 | `width:100%` 跨越列，需 `overflow:hidden` |
| 7 | `var()` 不被自动解析 | ❌ **已修复（v1.1.3dev）**，详见 §二.2.3 |
| 8 | 不支持 `vw`/`vh`/`calc()`/`clamp()` | 尺寸只能 `px` 和 `%` |
| 9 | 没有 `preventDefault()` | 只有 `stopPropagation()` |
| 10 | `::before`/`::after` 伪元素 | 不可用，需额外 div 替代 |
| 11 | `visibility:hidden` 继承 | ❌ **已修复（v1.1.3dev）**，现在正确继承到子元素，详见 §三.2.1 |
| 12 | `background` 简写不支持 `var()` | `background: var(--x)` 中的 `var()` 不会被 `isColorToken()` 识别，`backgroundColor` 保持 `"unset"`。需改用 `background-color: var(--x)` |
| 13 | `linear-gradient()` 内颜色不能含空格 | `Gradient.parse()` 用 `split("\\s+")` 切分 stop，`rgba(r, g, b, a)` 会被切成 `["rgba(r,", "g,", "b,", "a)"]`，`Color.parse()` 解析失败返回透明。必须在 gradient 中使用 `rgba(r,g,b,a)`（无空格）或 `#hex` / `#RRGGBBAA`（8 位 hex） |
| 14 | 8 位 hex `#RRGGBBAA` 的 alpha 被错误解析 | `Color.parseHex()` 对 8 位 hex 不做字节重排，直接 `Long.parseLong(hex, 16)` 原样存入 int。而内部颜色格式为 **ARGB**（alpha 在 bits 24-31，blue 在 bits 0-7）。`#FFFFFF4D` 期望白 @ 30% 透明度（A=0x4D），实际解析为完全不透明的蓝白色（A=0xFF, B=0x4D）。**必须用 `rgba(r,g,b,a)` 替代 8 位 hex 来表达半透明色**。详见 §九。 |
| 15 | `padding`/`margin` 多值简写不生效 | `applyPaddingAll("0 10px 4px")` 将完整字符串作为单一值传给 `Size.resolveLength()`，`parseNumber()` 无法解析多值字符串，返回 `fallback = 0`，所有方向的 padding 均塌缩为 0。**必须使用分方向属性**如 `padding-left: 10px; padding-right: 10px; padding-bottom: 4px;`。同理适用于 `margin`。根因：[Box.java:50-51](file:///d:/Files/Project_MinecraftMods/AUI/src/main/java/com/sighs/apricityui/style/Box.java#L50-L51)，`Size.resolveLength()` 只接受单数值。 |
| 16 | `@keyframes` 不支持 `from`/`to` 关键字 | `CSS.parseAndRegisterAnimations()` 对 keyframe 选择器调用 `Double.parseDouble()`，`from`/`to` 不是合法浮点数导致 `NumberFormatException` 崩溃。**必须用百分比语法**如 `0% { ... }` / `100% { ... }`。根因：[CSS.java:151](file:///d:/Files/Project_MinecraftMods/AUI/src/main/java/com/sighs/apricityui/resource/CSS.java#L151)。 |
| 17 | **父元素 class 变化不传播子元素 CSS 重匹配** | 修改父元素 class（如 `toggle.setAttribute("class", "toggle-switch on")`）后，AUI 不会自动对子元素重新执行 CSS 选择器匹配。子元素的样式依赖于父 class 的后代选择器（如 `.toggle-switch.on .knob`）将**保持旧值**。必须用内联 style 直接操作子元素。见 #18。 |
| 18 | **`innerText` 直接赋值不触发文本重渲染** | 在某些场景（尤其是父元素 class 同时变化时），`element.innerText = "新文本"` 不会触发 AUI 的文本缓存失效。必须用 **remove() 旧元素 → createElement() 创建新元素 → append() 追加** 的三步式替换。见 `GroupStripRenderer.java`。 |

---

## 九、8 位 hex 颜色解析 BUG 详解

### 9.1 根因

[`Color.parseHex()`](file:///d:/Files/Project_MinecraftMods/AUI/src/main/java/com/sighs/apricityui/style/Color.java#L95-L115) 的实现：

```java
private static int parseHex(String hex) {
    // ...
    if (cleanHex.length() == 6) {
        cleanHex = "FF" + cleanHex;   // 6 位 hex 补 FF 作为 alpha
    }
    return (int) Long.parseLong(cleanHex, 16);  // ← 直接按原始 byte 序列存入
}
```

而内部颜色提取采用 ARGB 布局：

```java
// lerpColor / toRgbaString 中的提取方式
int a = (value >>> 24) & 0xFF;
int r = (value >>> 16) & 0xFF;
int g = (value >>>  8) & 0xFF;
int b =  value        & 0xFF;
```

对于 8 位 hex `#RRGGBBAA`，hex 字符串的排列是 `RR GGBB AA`。解析后 int 的字节排列也是 `RR GG BB AA`。但 ARGB 提取要求 `AA RR GG BB`。结果：

| 8 位 hex | 解析为 int | 实际提取（ARGB） | 期望效果 |
|----------|-----------|-----------------|---------|
| `#FFFFFF0F` | `0xFFFFFF0F` | A=0xFF(不透), R=FF, G=FF, B=0F | 白 @ 6% 透明度 |
| `#FFFFFF4D` | `0xFFFFFF4D` | A=0xFF(不透), R=FF, G=FF, B=4D | 白 @ 30% 透明度 |
| `#FFFFFF80` | `0xFFFFFF80` | A=0xFF(不透), R=FF, G=FF, B=80 | 白 @ 50% 透明度 |

### 9.2 对比：`rgba()` 的正确路径

[`Color.parseRgba()`](file:///d:/Files/Project_MinecraftMods/AUI/src/main/java/com/sighs/apricityui/style/Color.java#L117-L151) 正确构造 ARGB：

```java
return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
```

| 写法 | 解析 int | 实际 ARGB |
|------|---------|-----------|
| `rgba(255,255,255,0.06)` | `0x0FFFFFFF` | A=0x0F, R=FF, G=FF, B=FF ✅ |
| `rgba(255,255,255,0.30)` | `0x4CFFFFFF` | A=0x4C, R=FF, G=FF, B=FF ✅ |

### 9.3 建议

- **始终使用 `rgba(r,g,b,a)`** 替代 8 位 hex 表达半透明色
- 6 位 hex（无 alpha）不受影响，因为 6 位路径补了 `"FF"` 前缀：`#FFFFFF` → `"FF" + "FFFFFF"` → `0xFFFFFFFF` → A=FF, R=FF, G=FF, B=FF ✅

---

## 十、CSS 变量参考（全局默认值）

以下为 [`global.css`](file:///d:/Files/Project_MinecraftMods/AUI/src/main/resources/assets/apricityui/apricity/global.css) 中定义的 CSS 变量：

| 变量 | 默认值 | 用途 |
|------|--------|------|
| `--aui-slot-size` | 18 | Slot 尺寸（像素） |
| `--aui-slot-render-bg` | 1 | Slot 是否渲染背景 |
| `--aui-slot-render-item` | 1 | Slot 是否渲染物品 |
| `--aui-slot-icon-scale` | 1 | Slot 图标缩放 |
| `--aui-slot-padding` | 0 | Slot 内边距 |
| `--aui-slot-z` | 0 | Slot Z 偏移 |
| `--aui-slot-interactive` | 1 | Slot 是否可交互 |
| `--aui-slot-cycle` | 1 | Slot 是否循环展示候选 |
| `--aui-slot-cycle-interval` | 1000 | 循环间隔（ms） |
| `--aui-player-gap` | 1px | 玩家背包 Grid 间距 |
| `--aui-player-columns` | 9 | 玩家背包列数 |
| `--aui-recipe-gap` | 2px | 合成表 Grid 间距 |
| `--aui-recipe-slot-size` | 18px | 合成表 Slot 尺寸 |
| `--aui-recipe-columns` | 5 | 合成表列数 |
| `--aui-container-columns` | (动态) | 容器 Grid 列数（ContainerExpander 注入） |
| `--aui-container-columns-effective` | (动态) | 容器有效列数 |

---

## 十一、关键源码文件索引

| 功能 | 文件路径 | 关键行 |
|------|---------|--------|
| CSS 变量声明/读取 | `init/Style.java` | L136 (customProperties), L478 (update), L632 (get), L650 (getCustomProperty) |
| CSS 变量 `var()` 自动解析 | `init/Style.java` | L660-792 (resolveVarReferences, resolveVarInValue, lookupVar, VAR_MAX_DEPTH) |
| CSS 变量继承读取 / 防重入 | `init/Element.java` | L188-215 (getCustomProperty, getRawCustomProperty, getCustomPropertyInherit) |
| CSS 解析 | `resource/CSS.java` | L19-258 |
| CSS 选择器匹配 | `init/Selector.java` | L125-276 (Index), L284-318 (match) |
| 帧调度 | `init/FrameScheduler.java` | L17-41 |
| Document 生命周期 | `init/Document.java` | L19-621 |
| 绘制队列构建 | `init/Drawer.java` | L12-289 |
| 样式差异观察 | `init/RenderElement.java` | L85-266 |
| 布局引擎 | `style/Layout.java` | L8-41 |
| Flexbox 布局 | `style/Flex.java` | — |
| Grid 布局 | `style/Grid.java` | — |
| 尺寸解析 | `style/Size.java` | L15-81 |
| 变换系统 | `style/Transform.java` | — |
| 动画引擎 | `style/Animation.java` | — |
| 过渡引擎 | `style/Transition.java` | — |
| 滤镜系统 | `style/Filter.java` | — |
| Canvas 2D | `element/Canvas.java` | — |
| 容器绑定 | `instance/element/Container.java` | L29-589 |
| Slot | `instance/element/Slot.java` | L22-299 |
| 容器屏幕槽位绑定 | `instance/screen/SlotDataBinder.java` | L16-120 |
| ContainerExpander | `instance/dom/expander/ContainerExpander.java` | L20-182 |
| 网络注册 | `instance/network/ApricityUINetwork.java` | L14-48 |
| WorldWindow | `instance/WorldWindow.java` | L25-100 |
| FollowFacingWorldWindow | `instance/FollowFacingWorldWindow.java` | L9-56 |
| 配置 | `instance/ApricityUIConfig.java` | L5-31 |
| 异步图片 | `resource/async/image/ImageAsyncHandler.java` | — |
| 异步样式 | `resource/async/style/StyleAsyncHandler.java` | — |
