package io.github.sweetzonzi.machine_max.client.render.renderable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.element.Body;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.GeometryQueryScope;
import com.sighs.apricityui.render.Mask;
import com.sighs.apricityui.render.WorldPaintDepth;
import com.sighs.apricityui.render.WorldWindowRenderContext;
import com.sighs.apricityui.spi.AuiRenderService;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.viewport.ApricityViewport;
import com.sighs.apricityui.world.WorldWindowDisplayPrecision;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.visual.AuiHudStyle;
import io.github.sweetzonzi.machine_max.common.visual.HudElement;
import io.github.sweetzonzi.machine_max.common.visual.ScopeBehavior;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AUI 页面载体的运行时元素：一个布局条目对应一个 AUI Document。
 * <p>
 * 文档以 {@code manuallyRendered} 创建，由 {@link io.github.sweetzonzi.machine_max.client.render.gui.hud.CustomHud}
 * 在布局顺序里绘制，因此与模型元素共享同一条 HUD 提交路径；AUI 的 overlay
 * 批次按该标记跳过它。
 * <p>
 * 页面按内容高度收缩：body 的内联样式固定为 {@code inline-block}、无外边距、
 * 透明背景，使视口变化或 CSS 重载期间的样式缓存重建也保持载体结构；再按
 * {@link HudElement#anchor()} 把元素对齐到屏幕，最后
 * 应用 GUI 坐标中的 {@link HudElement#offset()} 与 {@link HudElement#scale()} 的 X/Y
 * 两轴缩放。元素尺寸取自文档上下文内提交的 AUI 布局测量值，经过文档视口缩放后
 * 参与锚点计算；同一组 CSS 到 GUI 映射同时用于绘制和裁剪。
 * <p>
 * 透视路径使用模型 HUD 的布局位移、ZYX 欧拉角与三轴缩放，CSS 像素直接作为缩放前的
 * 局部单位，内容中心为原点、Y 轴向上。{@code anchor} 只参与正交路径。透视页面保持二维
 * 图层语义：CSS {@code translateZ} 取 0，各绘制节点按 DOM 绘制顺序取得独立深度。
 * <p>
 * <b>调用线程：</b>客户端主线程（创建、渲染、销毁）。
 */
@OnlyIn(Dist.CLIENT)
public final class AuiHudAnimatable implements HudElementInstance {

    /** 运行时加在页面 body 上的类名。 */
    public static final String BODY_CLASS = "mm-hud";

    private final ResourceLocation styleId;
    private final AuiHudStyle style;
    private final HudElement element;

    @Nullable
    private Document document;
    /** 已应用样式默认值的 body；文档刷新后 body 会换实例。 */
    @Nullable
    private Body styledBody;
    /** 每份创建失败的模板只提示一次。 */
    private boolean warned;
    /** 同一份 HTML 模板创建失败时抑制重复创建；模板内容变化后允许重试。 */
    private boolean failed;
    @Nullable
    private String failedTemplate;
    /** 已报告过内容尺寸的页面键；用于确认页面真的完成了布局。 */
    private static final Set<String> REPORTED_SIZES = ConcurrentHashMap.newKeySet();
    private static final float[] SQUARE_CLIP_RADII = {0f, 0f, 0f, 0f};

    public AuiHudAnimatable(ResourceLocation styleId, AuiHudStyle style, HudElement element) {
        this.styleId = styleId;
        this.style = style;
        this.element = element;
        createDocument();
    }

    private void createDocument() {
        if (document != null && !document.isDisposed()) return;
        String template = HTML.getTemple(style.page());
        if (failed && Objects.equals(template, failedTemplate)) return;
        failed = false;
        failedTemplate = null;
        warned = false;
        Document created;
        try {
            created = ApricityUI.createDocument(style.page());
        } catch (RuntimeException | LinkageError failure) {
            warnOnce("AUI HUD 页面创建失败: " + style.page(), failure);
            failed = true;
            failedTemplate = template;
            return;
        }
        if (created == null || created.isDisposed() || created.body == null) {
            if (created != null && !created.isDisposed()) created.remove();
            warnOnce("AUI HUD 页面不存在或没有 body: " + style.page(), null);
            failed = true;
            failedTemplate = template;
            return;
        }
        created.setManuallyRendered(true);
        // HUD 页面随 /reload 刷新，方便内容包迭代；刷新后 body 换实例，下面会重新贴类名。
        created.setReloadPersistent(false);
        this.document = created;
        this.styledBody = null;
        applyPage(created);
    }

    /** 写入样式默认值和 HUD 必需的内联结构属性；属性值相同时不提交样式写入。 */
    private void applyPage(Document target) {
        Body body = target.body;
        if (body == null) return;
        if (body != styledBody) {
            style.defaults().forEach(body::setInlineStyleProperty);
            styledBody = body;
        }
        if (!body.getClassList().contains(BODY_CLASS)) body.getClassList().add(BODY_CLASS);
        // 内联声明属于 DOM，CSS 缓存重建会保留它；每次绘制也检查现有 body，支持 JBR 热重载。
        ensureInlineStyle(body, "display", "inline-block");
        ensureInlineStyle(body, "width", "auto");
        ensureInlineStyle(body, "height", "auto");
        ensureInlineStyle(body, "min-height", "0");
        ensureInlineStyle(body, "margin", "0");
        ensureInlineStyle(body, "background", "transparent");
    }

    private static void ensureInlineStyle(Body body, String property, String value) {
        if (!value.equals(body.getInlineStylePropertyValue(property))
                || !"important".equals(body.getInlineStylePropertyPriority(property))) {
            body.setInlineStyleProperty(property, value, "important");
        }
    }

    @Override
    public boolean isPerspective() {
        return element.perspective();
    }

    @Override
    public ScopeBehavior scopeBehavior() {
        return element.scopeBehavior();
    }

    @Override
    public boolean ignoreZoom() {
        return element.ignoreZoom();
    }

    @Override
    public Vector3f getOffset(float partialTick) {
        return new Vector3f((float) element.offset().x, (float) element.offset().y, (float) element.offset().z);
    }

    @Override
    public Quaternionf getQuaternion(float partialTick) {
        return AuiHudPerspectiveTransform.rotation(element.rotation().x, element.rotation().y, element.rotation().z);
    }

    @Nullable
    private Document resolveDocument() {
        Document target = document;
        if (target == null || target.isDisposed()) {
            // 文档被 AUI 刷新回收：重建一次，避免整个 HUD 永久消失。
            document = null;
            createDocument();
            target = document;
        }
        return target;
    }

    /** 调用方已进入文档上下文；只提交待处理布局工作后读取内容尺寸。 */
    private DocumentDimensions prepareDocument(Document target) {
        applyPage(target);
        double width;
        double height;
        try (GeometryQueryScope geometryScope = GeometryQueryScope.open()) {
            target.commitStyleRecalc();
            if (target.hasPendingRenderState()) target.commitRenderState();
            Body body = target.body;
            width = body != null ? body.getOffsetWidth() : 0.0d;
            height = body != null ? body.getOffsetHeight() : 0.0d;
        }
        if (width > 0d && height > 0d && REPORTED_SIZES.add(style.page())) {
            MachineMax.LOGGER.info("[AUI HUD] 页面 {} 完成布局，内容尺寸 {}x{}", style.page(), width, height);
        }
        return new DocumentDimensions(width, height);
    }

    private record DocumentDimensions(double width, double height) {
    }

    @Override
    public void renderGui(GuiGraphics guiGraphics, float partialTick) {
        Document target = resolveDocument();
        if (target == null) return;
        try (Document.ContextScope ignored = Document.withContext(target)) {
            DocumentDimensions dimensions = prepareDocument(target);

            ApricityViewport viewport = target.getViewport();
            AuiHudPlacement placement = AuiHudPlacement.resolve(
                    dimensions.width(), dimensions.height(), guiGraphics.guiWidth(), guiGraphics.guiHeight(), viewport.renderScale(),
                    element.scale().x, element.scale().y, element.offset().x, element.offset().y,
                    element.anchor().horizontal(), element.anchor().vertical());
            target.setViewportTransform(placement.scaleX(), placement.scaleY(), placement.x(), placement.y());

            PoseStack poseStack = guiGraphics.pose();
            poseStack.pushPose();
            try {
                // 裁剪基底取 HUD 变换前的 pose；CSS 裁剪经相同的位移和缩放映射到 GUI。
                Mask.pushSurfaceClip(poseStack, viewport.layoutWidth(), viewport.layoutHeight(),
                        placement.x(), placement.y(), placement.scaleX(), placement.scaleY());
                try {
                    poseStack.translate(placement.x(), placement.y(), (float) element.offset().z);
                    poseStack.scale(placement.scaleX(), placement.scaleY(), 1.0f);
                    Base.drawEmbeddedDocument(poseStack, target, null);
                } finally {
                    // Surface 的入栈/出栈都会提交批次，保持与其它 HUD 元素的绘制顺序。
                    Mask.popSurfaceClip();
                }
            } finally {
                poseStack.popPose();
            }
        }
    }

    /**
     * 客户端渲染线程。外层已应用布局位移；本方法在当前透视投影中绘制以内容中心为原点的页面。
     * 根遮罩与元素 overflow 使用随三维矩阵绘制的 stencil，CSS 背景、阴影与文字保持完整精度。
     */
    @Override
    public void renderContent(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float partialTick) {
        Document target = resolveDocument();
        if (target == null) return;
        try (Document.ContextScope ignored = Document.withContext(target)) {
            DocumentDimensions dimensions = prepareDocument(target);
            if (!(dimensions.width() > 0d) || !(dimensions.height() > 0d)) return;

            // 在改变深度、遮罩与绘制后端之前提交先前的模型 HUD 批次。
            bufferSource.endBatch();
            Base.commitDraws();
            AuiRenderService render = AuiServices.render();
            AuiRenderService.RenderStateScope state = render.pushFilterRenderState();
            if (state == null) state = AuiRenderService.RenderStateScope.NOOP;
            poseStack.pushPose();
            try {
                float viewDepth = Math.abs(poseStack.last().pose().m32());
                AuiHudPerspectiveTransform.apply(poseStack, getQuaternion(partialTick),
                        element.scale().x, element.scale().y, element.scale().z,
                        dimensions.width(), dimensions.height());
                // 总深度预算取观察距离的千分之三（至少 0.003 投放单位），由节点等分。
                float depthBudget = Math.max(0.003f, viewDepth * 0.003f);
                // Z 基向量包含外层炮镜缩放，预算以最终投放空间的单位衡量。
                var matrix = poseStack.last().pose();
                float zScale = Math.max(0.0001f, (float) Math.sqrt(
                        matrix.m20() * matrix.m20() + matrix.m21() * matrix.m21() + matrix.m22() * matrix.m22()));
                float localBudget = depthBudget / zScale;
                float localStep = localBudget / Math.max(1, target.getPaintList().size());
                drawPerspectiveDocument(poseStack, target, localStep, localBudget);
            } finally {
                try {
                    Base.commitDraws();
                } finally {
                    poseStack.popPose();
                    state.close();
                }
            }
        }
    }

    /** 三维遮罩、图层深度与物品深度遵循 AUI 的 {@code com/sighs/apricityui/world/WorldWindow.java#render}。 */
    private static void drawPerspectiveDocument(PoseStack poseStack, Document target, float localStep, float localBudget) {
        ApricityViewport viewport = target.getViewport();
        float width = viewport.layoutWidth();
        float height = viewport.layoutHeight();
        Base.pushDepthStep(localStep);
        Base.pushDepthMode(true);
        Base.pushDepthTest(true);
        Base.pushGuiItemZ(localStep * 0.25f, localStep * 0.5f);
        Base.pushDocumentZOffset(localBudget);
        WorldPaintDepth.pushFlatTransforms(true);
        Mask.resetDepth(width, height);
        Mask.pushForceStencil();
        try {
            Mask.pushMask(poseStack, 0f, 0f, width, height, SQUARE_CLIP_RADII, true);
            try (WorldWindowRenderContext.Scope ignored = WorldWindowRenderContext.push(WorldWindowDisplayPrecision.FULL)) {
                AuiServices.render().setDepthFunc(GL11.GL_LEQUAL);
                Base.drawDocument(poseStack, target);
            } finally {
                Mask.popMask(poseStack, 0f, 0f, width, height, SQUARE_CLIP_RADII);
            }
        } finally {
            Mask.popForceStencil();
            WorldPaintDepth.popFlatTransforms();
            Base.popDocumentZOffset();
            Base.popGuiItemZ();
            Base.popDepthTest();
            Base.popDepthMode();
            Base.popDepthStep();
        }
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
