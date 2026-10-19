package io.github.sweetzonzi.machine_max.client.render.renderable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.element.Body;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.render.Base;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.visual.AuiHudStyle;
import io.github.sweetzonzi.machine_max.common.visual.HudElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AUI 页面载体的运行时元素：一个布局条目对应一个 AUI Document。
 * <p>
 * 文档以 {@code manuallyRendered} 创建，由 {@link io.github.sweetzonzi.machine_max.client.render.gui.hud.CustomHud}
 * 在布局顺序里绘制，因此与模型元素共享同一条 HUD 提交路径；AUI 的 overlay
 * 批次按该标记跳过它。
 * <p>
 * 页面按内容高度收缩：运行时注入 {@code body.mm-hud} 规则（{@code inline-block}、
 * 无外边距、透明背景），再按 {@link HudElement#anchor()} 把元素对齐到屏幕，最后
 * 应用 {@link HudElement#offset()} 与 {@code scale.x}。元素尺寸取自 AUI 布局的
 * 测量值，首帧尚未提交布局时按 0 处理，下一帧即稳定。
 * <p>
 * <b>调用线程：</b>客户端主线程（创建、渲染、销毁）。
 */
@OnlyIn(Dist.CLIENT)
public final class AuiHudAnimatable implements HudElementInstance {

    /** 运行时加在页面 body 上的类名。 */
    public static final String BODY_CLASS = "mm-hud";

    /** 注入的页面规则：让页面按内容收缩并透出后面的世界。 */
    private static final String HUD_STYLESHEET = """
            body.mm-hud {
                display: inline-block;
                width: auto;
                height: auto;
                min-height: 0;
                margin: 0;
                background: transparent;
            }
            """;

    /** 注入样式表的上下文路径；只用于 AUI 的规则来源标记。 */
    private static final String STYLESHEET_CONTEXT = "machine_max:aui_hud";
    /** 注入样式表的层叠序号，高于页面自身样式。 */
    private static final int STYLESHEET_ORDER = 100_000;

    private final ResourceLocation styleId;
    private final AuiHudStyle style;
    private final HudElement element;

    @Nullable
    private Document document;
    /** 已应用 {@link #BODY_CLASS} 的 body；文档刷新后 body 会换实例。 */
    @Nullable
    private Body styledBody;
    /** 页面缺失或创建失败时只提示一次。 */
    private boolean warned;
    /** 创建已失败；不再逐帧重试。 */
    private boolean failed;
    /** 已报告过内容尺寸的页面键；用于确认页面真的完成了布局。 */
    private static final Set<String> REPORTED_SIZES = ConcurrentHashMap.newKeySet();

    public AuiHudAnimatable(ResourceLocation styleId, AuiHudStyle style, HudElement element) {
        this.styleId = styleId;
        this.style = style;
        this.element = element;
        createDocument();
    }

    private void createDocument() {
        if (failed) return;
        if (document != null && !document.isDisposed()) return;
        Document created;
        try {
            created = ApricityUI.createDocument(style.page());
        } catch (RuntimeException | LinkageError failure) {
            warnOnce("AUI HUD 页面创建失败: " + style.page(), failure);
            failed = true;
            created = null;
        }
        if (created == null || created.isDisposed() || created.body == null) {
            warnOnce("AUI HUD 页面不存在或没有 body: " + style.page(), null);
            failed = true;
            return;
        }
        created.setManuallyRendered(true);
        // HUD 页面随 /reload 刷新，方便内容包迭代；刷新后 body 换实例，下面会重新贴类名。
        created.setReloadPersistent(false);
        this.document = created;
        this.styledBody = null;
        applyPage(created);
    }

    /** 给页面贴类名、注入收缩规则，并写入样式声明的 CSS 变量。 */
    private void applyPage(Document target) {
        Body body = target.body;
        if (body == null) return;
        if (body != styledBody) {
            // 刷新会重建 body 并清空样式缓存，因此换 body 时重新注册。
            target.registerStylesheet(HUD_STYLESHEET, STYLESHEET_CONTEXT, STYLESHEET_ORDER);
            body.getClassList().add(BODY_CLASS);
            style.defaults().forEach(body::setInlineStyleProperty);
            styledBody = body;
        }
    }

    @Override
    public boolean isPerspective() {
        return false;
    }

    @Override
    public void renderGui(GuiGraphics guiGraphics, float partialTick) {
        Document target = document;
        if (target == null || target.isDisposed()) {
            // 文档被 AUI 刷新回收：重建一次，避免整个 HUD 永久消失。
            document = null;
            createDocument();
            target = document;
            if (target == null) return;
        }
        applyPage(target);

        Body body = target.body;
        double rawWidth = body != null ? body.getOffsetWidth() : 0.0d;
        double rawHeight = body != null ? body.getOffsetHeight() : 0.0d;
        float width = (float) Math.max(0.0d, rawWidth);
        float height = (float) Math.max(0.0d, rawHeight);
        if (width > 0f && height > 0f && REPORTED_SIZES.add(style.page())) {
            MachineMax.LOGGER.info("[AUI HUD] 页面 {} 完成布局，内容尺寸 {}x{}", style.page(), width, height);
        }
        float scale = (float) Math.max(0.0d, element.scale().x);
        if (scale == 0f) scale = 1f;

        float screenWidth = guiGraphics.guiWidth();
        float screenHeight = guiGraphics.guiHeight();
        float x = aligned(element.anchor().horizontal(), screenWidth, width * scale);
        float y = aligned(element.anchor().vertical(), screenHeight, height * scale);

        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.translate(
                x + (float) element.offset().x,
                y + (float) element.offset().y,
                (float) element.offset().z);
        poseStack.scale(scale, scale, 1.0f);
        // 先把已排队的模型几何刷掉，再让 AUI 自己成批提交，保证提交顺序与布局顺序一致。
        Base.commitDraws();
        Base.drawOverlayDocument(poseStack, target);
        Base.commitDraws();
        poseStack.popPose();
    }

    /** 锚点对齐：屏幕边到元素同侧边的距离。 */
    private static float aligned(int alignment, float screenSize, float elementSize) {
        return switch (alignment) {
            case 1 -> (screenSize - elementSize) / 2f;
            case 2 -> screenSize - elementSize;
            default -> 0f;
        };
    }

    @Override
    public void dispose() {
        Document target = document;
        document = null;
        styledBody = null;
        if (target != null && !target.isDisposed()) target.remove();
    }

    private void warnOnce(String message, @Nullable Throwable failure) {
        if (warned) return;
        warned = true;
        if (failure != null) {
            MachineMax.LOGGER.warn("{} (style={})", message, styleId, failure);
        } else {
            MachineMax.LOGGER.warn("{} (style={})", message, styleId);
        }
    }
}
