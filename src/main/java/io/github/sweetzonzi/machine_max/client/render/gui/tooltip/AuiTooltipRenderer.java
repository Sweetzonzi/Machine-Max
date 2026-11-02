package io.github.sweetzonzi.machine_max.client.render.gui.tooltip;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.render.GeometryQueryScope;
import com.sighs.apricityui.viewport.ApricityViewport;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.item.prop.BaseVehicleItem;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.BlueprintData;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 蓝图 / 装配体物品的 AUI tooltip 渲染器。
 *
 * <p>内容包在 {@code documents/} 下提供一份普通 AUI 页面，蓝图或装配体 JSON 的
 * {@code tooltip} 字段指向它：{@code namespace:tooltips/demo.html} 对应
 * {@code documents/tooltips/demo.html}。页面作者只负责外观，生命周期、定位与原版
 * tooltip 的接管由本类完成。</p>
 *
 * <p>显隐契约沿用 AUI 官方 tooltip 示例：页面 {@code body} 默认 {@code opacity: 0}，
 * 通过 {@code shown} / {@code hiding} 两个类名触发淡入淡出；本类只切换类名与
 * {@code #tooltip} 的 {@code left/top}，不覆盖页面自身的样式。</p>
 *
 * <ul>
 *   <li>页面以 {@link Document#setReloadPersistent(boolean)} 标记为持久文档，交给 AUI
 *       自身的屏幕合成通道在帧末绘制，因此本类不触碰批次提交、深度缓冲与遮罩状态；</li>
 *   <li>定位元素优先取 {@code #tooltip}，缺省退化为 {@code body}；</li>
 *   <li>首次悬浮时先测量一次布局再显示，该帧不接管渲染，原版 tooltip 仍然可见；</li>
 *   <li>页面缺失或创建失败时不取消原版渲染，物品回退为原版 tooltip（仅名称）。</li>
 * </ul>
 *
 * <p>线程：客户端主线程。</p>
 */
@EventBusSubscriber(modid = MachineMax.MOD_ID, value = Dist.CLIENT)
public final class AuiTooltipRenderer {

    /** 页面内的定位元素；缺失时退化为 {@code body}。 */
    private static final String ROOT_SELECTOR = "#tooltip";
    /** 淡入类名，对应页面 CSS 中的 {@code body.shown}。 */
    private static final String CLASS_SHOWN = "shown";
    /** 淡出类名，对应页面 CSS 中的 {@code body.hiding}。 */
    private static final String CLASS_HIDING = "hiding";
    /** 相对光标的默认偏移，与原版 tooltip 的 12px 偏移保持一致。 */
    private static final int CURSOR_OFFSET_X = 12;
    private static final int CURSOR_OFFSET_Y = 12;
    /** 贴边时保留的安全距离。 */
    private static final int SCREEN_MARGIN = 4;
    /** 光标移出物品后延迟淡出，避免界面重绘间隙造成闪烁。 */
    private static final long HIDE_DELAY_MS = 50L;
    /** 淡出动画结束后的回收延迟；此后停止绘制页面，只保留布局。 */
    private static final long HIDE_ANIMATION_MS = 260L;
    /** 布局尚未就绪时的最大重试帧数。 */
    private static final int MAX_MEASURE_ATTEMPTS = 3;
    /** 布局始终未就绪时采用的兜底尺寸。 */
    private static final double FALLBACK_WIDTH = 180.0d;
    private static final double FALLBACK_HEIGHT = 48.0d;
    /** 内容高于面板时强制重排的次数上限；用于修正样式晚到时残留的旧高度。 */
    private static final int MAX_RELAYOUT_RETRIES = 3;

    /** 页面键（命名空间 + 路径）到页面状态的映射；页面按需创建并长期复用。 */
    private static final Map<ResourceLocation, Page> PAGES = new HashMap<>();

    /** 当前处于显示状态的页面。切换物品时旧页面必须被隐藏，否则会残留在原位置。 */
    private static final Set<Page> SHOWN = Collections.newSetFromMap(new IdentityHashMap<>());

    private AuiTooltipRenderer() {
    }

    @SubscribeEvent
    public static void onRenderTooltip(RenderTooltipEvent.Pre event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !(stack.getItem() instanceof BaseVehicleItem item)) return;
        ResourceLocation id = item.getTooltipId(stack);
        if (id == null || BlueprintData.EMPTY.equals(id)) return;

        Page page = PAGES.computeIfAbsent(id, Page::new);
        if (!page.ready()) return;

        // 外链样式异步抵达、脚本改写内容都会改变尺寸，跟随前刷新一次，下一帧自愈。
        page.measure();

        if (!page.measured) {
            // 布局尚未就绪：本帧不接管渲染，让原版 tooltip 先顶上。
            return;
        }

        page.follow(event.getX(), event.getY(), event.getScreenWidth(), event.getScreenHeight());
        page.lastSeenAt = Util.getMillis();
        hideOthers(page);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        long now = Util.getMillis();
        if (!SHOWN.isEmpty()) {
            for (Iterator<Page> iterator = SHOWN.iterator(); iterator.hasNext(); ) {
                Page page = iterator.next();
                if (now - page.lastSeenAt <= HIDE_DELAY_MS) continue;
                page.hide();
                iterator.remove();
            }
        }
        // 淡出结束后停画，避免透明页面继续参与每帧合成。
        for (Page page : PAGES.values()) page.tickSuppress();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        for (Page page : SHOWN) page.hide();
        SHOWN.clear();
        for (Page page : PAGES.values()) page.dispose();
        PAGES.clear();
    }

    /** 同时只允许一个说明页面可见。 */
    private static void hideOthers(Page current) {
        if (!SHOWN.isEmpty()) {
            for (Iterator<Page> iterator = SHOWN.iterator(); iterator.hasNext(); ) {
                Page page = iterator.next();
                if (page == current) continue;
                page.hide();
                iterator.remove();
            }
        }
        SHOWN.add(current);
    }

    /** 单份描述页面的运行时状态；同一 tooltip id 复用同一份文档。 */
    private static final class Page {

        private final ResourceLocation id;
        private final String path;

        @Nullable
        private Document document;
        @Nullable
        private Element root;
        @Nullable
        private Element adoptedBody;

        /** 模板缺失或创建失败；内容包补上文件后由下一次调用重试。 */
        private boolean failed;
        /** 尺寸是否已完成一次有效测量。 */
        private boolean measured;
        /** 页面当前是否处于淡入/可见状态。 */
        private boolean shown;
        private double width;
        private double height;
        private int measureAttempts;
        /** 内容溢出面板时的强制重排次数。 */
        private int relayoutRetries;
        /** 重排修正失败只提示一次。 */
        private boolean overflowWarned;
        /** 是否已用行内 height 把面板高度钉到内容高度。 */
        private boolean heightPinned;
        /** 上一帧的屏幕高度（文档坐标），用于高度兜底的安全判断。 */
        private double viewportHeight;
        /** 坐标系换算结果只记录一次。 */
        private boolean scaleReported;
        private long lastSeenAt;
        /** 淡出开始时刻，用于动画结束后停止绘制。 */
        private long hidingSince;
        /** 是否已通过行内 {@code display:none} 停画。 */
        private boolean drawingSuppressed;
        private double lastLeft = Double.NaN;
        private double lastTop = Double.NaN;

        private Page(ResourceLocation id) {
            this.id = id;
            this.path = id.getNamespace() + "/" + id.getPath();
        }

        /** 页面是否可以参与渲染：文档存在、body 已就位、定位元素可解析。 */
        private boolean ready() {
            if (document != null && document.isDisposed()) reset();
            if (document == null && !create()) return false;
            if (document.body != adoptedBody) {
                adoptedBody = document.body;
                root = null;
                // 文档被刷新后 body 会换实例，淡入状态随之丢失，需要重新按需添加。
                shown = false;
                hidingSince = 0L;
            }
            if (root == null || !root.isConnected()) resolveRoot();
            if (root == null) return false;
            // 上一轮隐藏后停画过；这里恢复布局，测量才有真实尺寸。
            resumeDrawing();
            return true;
        }

        private boolean create() {
            if (failed && HTML.getTemple(path) == null) return false;
            failed = false;
            if (HTML.getTemple(path) == null) {
                failed = true;
                MachineMax.LOGGER.warn("[AUI Tooltip] 找不到描述页面 {}（{}），该物品回退为原版 tooltip", id, path);
                return false;
            }
            Document created;
            try {
                created = Document.create(path);
            } catch (RuntimeException | LinkageError failure) {
                failed = true;
                MachineMax.LOGGER.warn("[AUI Tooltip] 描述页面创建失败 {}", path, failure);
                return false;
            }
            if (created == null || created.isDisposed() || created.body == null) {
                if (created != null && !created.isDisposed()) created.remove();
                failed = true;
                MachineMax.LOGGER.warn("[AUI Tooltip] 描述页面缺少 body，已跳过 {}", path);
                return false;
            }
            created.setManuallyRendered(false);
            // 只有持久文档才会进入 AUI 的屏幕合成通道；普通文档需要自己提交绘制。
            created.setReloadPersistent(true);
            document = created;
            root = null;
            adoptedBody = null;
            measured = false;
            shown = false;
            drawingSuppressed = false;
            measureAttempts = 0;
            relayoutRetries = 0;
            overflowWarned = false;
            heightPinned = false;
            scaleReported = false;
            lastLeft = Double.NaN;
            lastTop = Double.NaN;
            return true;
        }

        private void resolveRoot() {
            if (document == null) return;
            Element candidate = null;
            try {
                candidate = document.querySelector(ROOT_SELECTOR);
            } catch (RuntimeException ignored) {
                // 选择器异常时退化为 body，不影响显示。
            }
            root = candidate != null ? candidate : document.body;
            measured = false;
            measureAttempts = 0;
            lastLeft = Double.NaN;
            lastTop = Double.NaN;
        }

        private void measure() {
            if (document == null || root == null) return;
            double measuredWidth;
            double measuredHeight;
            try (GeometryQueryScope scope = GeometryQueryScope.open()) {
                document.commitStyleRecalc();
                if (document.hasPendingRenderState()) document.commitRenderState();
                measuredWidth = root.getOffsetWidth();
                measuredHeight = root.getOffsetHeight();
            } catch (RuntimeException | LinkageError failure) {
                MachineMax.LOGGER.warn("[AUI Tooltip] 布局测量失败 {}", path, failure);
                return;
            }
            measureAttempts++;
            if (measuredWidth > 0.0d && measuredHeight > 0.0d) {
                width = measuredWidth;
                height = measuredHeight;
                measured = true;
                // 文字换行后的实际内容高于面板时，说明尺寸停留在旧布局上（多为外链样式晚到）。
                // 强制重排一次，下一帧再量；次数用尽后按现有尺寸显示，避免死循环。
                if (root.scrollHeight > measuredHeight + 1.0d) {
                    if (relayoutRetries < MAX_RELAYOUT_RETRIES) {
                        relayoutRetries++;
                        measured = false;
                        document.markDirty(root, Drawer.RELAYOUT | Drawer.REPAINT);
                        return;
                    }
                    if (!overflowWarned) {
                        overflowWarned = true;
                        MachineMax.LOGGER.warn("[AUI Tooltip] 描述页面 {} 的内容高度 {} 超出面板高度 {}，"
                                        + "重排 {} 次仍未收敛，改用行内 height 兜底",
                                path, root.scrollHeight, measuredHeight, MAX_RELAYOUT_RETRIES);
                    }
                    // 重排收敛不了时直接把面板高度钉到内容高度：内容是静态的，钉一次即可。
                    if (!heightPinned && viewportHeight > 0.0d && root.scrollHeight <= viewportHeight) {
                        heightPinned = true;
                        measured = false;
                        root.setInlineStyleProperty("height", px(root.scrollHeight), "important");
                        return;
                    }
                }
            } else if (!measured && measureAttempts >= MAX_MEASURE_ATTEMPTS) {
                // 页面始终没有尺寸（例如作者把根元素设为 display:none）：用兜底尺寸接管，
                // 至少不会把原版 tooltip 永久卡住。
                width = FALLBACK_WIDTH;
                height = FALLBACK_HEIGHT;
                measured = true;
            }
        }

        /**
         * 按光标位置摆放页面：默认落在光标右下方，越界时翻到另一侧，两侧都放不下时贴边滑动。
         *
         * <p>光标的 GUI 坐标必须换算到页面自己的坐标系：{@code mode=gui} 时
         * {@code renderScale == 1}，1 CSS px 就是 1 GUI px；而浏览器视口会按 DPI 折算
         * （常见 {@code renderScale ≈ 0.5}），此时 CSS 尺寸是 GUI 尺寸的两倍。拿 CSS 宽度
         * 直接和 GUI 宽度比较，右侧会被判定成“永远放不下”，左侧又算出负数，结果就是面板
         * 被死死夹在屏幕左边、只有纵向能跟随光标。</p>
         *
         * @param mouseGuiX   光标 GUI 坐标 X
         * @param mouseGuiY   光标 GUI 坐标 Y
         * @param screenGuiW  GUI 宽
         * @param screenGuiH  GUI 高
         */
        private void follow(double mouseGuiX, double mouseGuiY, int screenGuiW, int screenGuiH) {
            ApricityViewport viewport = document == null ? null : document.getViewport();
            double scale = viewport == null ? 1.0d : viewport.renderScale();
            if (!(scale > 0.0d)) scale = 1.0d;
            double screenWidth = viewport != null && viewport.layoutWidth() > 1
                    ? viewport.layoutWidth() : screenGuiW / scale;
            double screenHeight = viewport != null && viewport.layoutHeight() > 1
                    ? viewport.layoutHeight() : screenGuiH / scale;
            double mouseX = mouseGuiX / scale;
            double mouseY = mouseGuiY / scale;
            double offsetX = CURSOR_OFFSET_X / scale;
            double offsetY = CURSOR_OFFSET_Y / scale;
            double margin = SCREEN_MARGIN / scale;
            this.viewportHeight = screenHeight;
            reportScaleOnce(scale, screenWidth, screenHeight, mouseX, mouseY);

            double[] placed = TooltipPlacement.place(mouseX, mouseY, screenWidth, screenHeight,
                    width, height, offsetX, offsetY, margin);
            applyPosition(placed[0], placed[1]);
            show();
        }

        /**
         * 第一次摆放时记录一次坐标系换算结果，便于排查“位置不跟随”。
         * 只在非 GUI 视口（需要换算）时打 warn，正常情况留 debug。
         */
        private void reportScaleOnce(double scale, double screenWidth, double screenHeight,
                                     double mouseX, double mouseY) {
            if (scaleReported) return;
            scaleReported = true;
            String message = "[AUI Tooltip] 页面 {} 的视口 {}x{}，renderScale={}，光标换算后位于 ({}, {})";
            Object[] args = {path, screenWidth, screenHeight, scale, mouseX, mouseY};
            if (Math.abs(scale - 1.0d) > 0.01d) {
                MachineMax.LOGGER.warn(message + "；CSS 像素与 GUI 像素不等价，已按视口比例换算", args);
            } else {
                MachineMax.LOGGER.debug(message, args);
            }
        }

        private void applyPosition(double left, double top) {
            if (root == null) return;
            if (Double.compare(left, lastLeft) == 0 && Double.compare(top, lastTop) == 0) return;
            lastLeft = left;
            lastTop = top;
            // 只写定位相关的行内声明，其余外观完全交给页面 CSS。
            root.setInlineStyleProperty("position", "fixed", "important");
            root.setInlineStyleProperty("pointer-events", "none", "important");
            root.setInlineStyleProperty("left", px(left), "important");
            root.setInlineStyleProperty("top", px(top), "important");
        }

        /** 恢复绘制（若之前停画过），并按示例的类名契约触发淡入。 */
        private void show() {
            resumeDrawing();
            if (shown) return;
            shown = true;
            hidingSince = 0L;
            if (document != null && document.body != null) {
                document.body.getClassList().remove(CLASS_HIDING);
                document.body.getClassList().add(CLASS_SHOWN);
            }
        }

        /** 按示例的类名契约触发淡出；动画结束后停止绘制，避免透明页面每帧参与合成。 */
        private void hide() {
            if (!shown) return;
            shown = false;
            hidingSince = Util.getMillis();
            if (document != null && document.body != null) {
                document.body.getClassList().remove(CLASS_SHOWN);
                document.body.getClassList().add(CLASS_HIDING);
            }
        }

        private void resumeDrawing() {
            if (!drawingSuppressed || root == null) return;
            drawingSuppressed = false;
            root.removeInlineStyleProperty("display");
        }

        /** 由 tick 调用：淡出结束后把页面移出绘制列表。 */
        private void tickSuppress() {
            if (shown || drawingSuppressed || hidingSince == 0L) return;
            if (Util.getMillis() - hidingSince <= HIDE_ANIMATION_MS) return;
            if (root == null) return;
            drawingSuppressed = true;
            hidingSince = 0L;
            root.setInlineStyleProperty("display", "none", "important");
        }

        private void reset() {
            document = null;
            root = null;
            adoptedBody = null;
            failed = false;
            measured = false;
            relayoutRetries = 0;
            shown = false;
            drawingSuppressed = false;
            hidingSince = 0L;
            measureAttempts = 0;
            lastLeft = Double.NaN;
            lastTop = Double.NaN;
        }

        private void dispose() {
            Document target = document;
            reset();
            if (target != null && !target.isDisposed()) target.remove();
        }

        private static String px(double value) {
            return String.format(Locale.ROOT, "%.2fpx", value);
        }
    }
}
