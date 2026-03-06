package io.github.sweetzonzi.machine_max.client.render.gui.hud3d;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.sweetzonzi.machine_max.client.render.MMRenderTypes;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Brightness;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
/**
 * 空间约定：
 * <p>
 * Local Space
 *   - IHud3DElement 使用的坐标空间
 *   - +X 右，+Y 上，-Z 朝向视线前方
 * <p>
 * Render Space
 *   - 已经应用 ModelViewMatrix（含 GameRenderer 预处理）
 *   - 与 PoseStack.last().pose() 所在空间一致
 */
public class Hud3DContext {

    public final Minecraft mc;
    public final Camera camera;
    public final Frustum frustum;
    public final LocalPlayer player;
    public final PoseStack poseStack;
    public final MultiBufferSource buffer;
    /** 当前渲染阶段的 ModelView 矩阵 */
    public final Matrix4f modelViewMatrix;
    /** 当前渲染阶段的 Projection 矩阵 */
    public final Matrix4f projectionMatrix;
    /** 预计算的 MVP 矩阵：Projection * ModelView */
    public final Matrix4f mvpMatrix;
    public final float partialTicks;
    public final Font font;
    public final ItemRenderer itemRenderer;
    public static final int FULL_BRIGHT = Brightness.FULL_BRIGHT.pack();
    /** HUD Anchor 矩阵（当前 ModelView 空间 → HUD Local）*/
    public final Matrix4f hudAnchorMatrix;
    /** HUD Anchor 逆矩阵（HUD Local → 当前 ModelView 空间）*/
    public final Matrix4f hudAnchorInverse;
    /**
     * 当前是否处于裁剪状态
     * 用于防止嵌套或遗漏 endClipRect
     */
    private boolean clippingActive = false;

    /**
     * 构造一个Hud3D上下文对象
     *
     * @param mc               Minecraft客户端实例
     * @param camera           相机实例
     * @param frustum          视锥体实例
     * @param player           本地玩家实例
     * @param poseStack        姿态栈
     * @param buffer           多缓冲源
     * @param modelViewMatrix  模型视图矩阵
     * @param projectionMatrix 投影矩阵
     * @param partialTicks     插值进度
     */
    public Hud3DContext(
            Minecraft mc,
            Camera camera,
            Frustum frustum,
            LocalPlayer player,
            PoseStack poseStack,
            MultiBufferSource buffer,
            Matrix4f modelViewMatrix,
            Matrix4f projectionMatrix,
            Matrix4f hudAnchorMatrix,
            float partialTicks
    ) {
        this.mc = mc;
        this.camera = camera;
        this.frustum = frustum;
        this.player = player;
        this.poseStack = poseStack;
        this.buffer = buffer;
        this.modelViewMatrix = modelViewMatrix;
        this.projectionMatrix = projectionMatrix;
        this.hudAnchorMatrix = hudAnchorMatrix;
        this.hudAnchorInverse = new Matrix4f(hudAnchorMatrix).invert();
        this.mvpMatrix = new Matrix4f(projectionMatrix).mul(modelViewMatrix);
        this.partialTicks = partialTicks;
        this.font = mc.font;
        this.itemRenderer = mc.getItemRenderer();
    }

    /* ======================== 基础矩形 ======================== */

    /**
     * 绘制一个指定颜色的矩形（默认z坐标为0）
     *
     * @param x1   矩形左上角x坐标
     * @param y1   矩形左上角y坐标
     * @param x2   矩形右下角x坐标
     * @param y2   矩形右下角y坐标
     * @param argb 颜色值（ARGB格式）
     */
    public void fill(float x1, float y1, float x2, float y2, int argb) {
        fill(x1, y1, x2, y2, argb, 0);
    }

    /**
     * 绘制一个指定颜色和z坐标的矩形
     *
     * @param x1   矩形左上角x坐标
     * @param y1   矩形左上角y坐标
     * @param x2   矩形右下角x坐标
     * @param y2   矩形右下角y坐标
     * @param argb 颜色值（ARGB格式）
     * @param z    z坐标（深度）
     */
    public void fill(float x1, float y1, float x2, float y2, int argb, float z) {
        fill(x1, y1, x2, y2, argb, z, MMRenderTypes.SOLID_ALWAYS_VISIBLE);
    }

    /**
     * 绘制一个指定颜色和z坐标的矩形
     *
     * @param x1   矩形左上角x坐标
     * @param y1   矩形左上角y坐标
     * @param x2   矩形右下角x坐标
     * @param y2   矩形右下角y坐标
     * @param argb 颜色值（ARGB格式）
     * @param z    z坐标（深度）
     */
    public void fill(float x1, float y1, float x2, float y2, int argb, float z, RenderType renderType) {
        float a = (argb >>> 24) / 255f;
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;

        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer vc = buffer.getBuffer(renderType);

        vc.addVertex(matrix, x1, y2, z).setColor(r, g, b, a);
        vc.addVertex(matrix, x2, y2, z).setColor(r, g, b, a);
        vc.addVertex(matrix, x2, y1, z).setColor(r, g, b, a);
        vc.addVertex(matrix, x1, y1, z).setColor(r, g, b, a);
    }

    /* ======================== 渐变矩形 ======================== */

    /**
     * 左→右线性渐变填充矩形
     *
     * @param x1         矩形左上角x坐标
     * @param y1         矩形左上角y坐标
     * @param x2         矩形右下角x坐标
     * @param y2         矩形右下角y坐标
     * @param leftColor  左侧颜色值（ARGB格式）
     * @param rightColor 右侧颜色值（ARGB格式）
     * @param z          z坐标（深度）
     */
    public void fillGradient(
            float x1, float y1, float x2, float y2,
            int leftColor, int rightColor,
            float z
    ) {
        float la = (leftColor >>> 24) / 255f;
        float lr = ((leftColor >> 16) & 0xFF) / 255f;
        float lg = ((leftColor >> 8) & 0xFF) / 255f;
        float lb = (leftColor & 0xFF) / 255f;

        float ra = (rightColor >>> 24) / 255f;
        float rr = ((rightColor >> 16) & 0xFF) / 255f;
        float rg = ((rightColor >> 8) & 0xFF) / 255f;
        float rb = (rightColor & 0xFF) / 255f;

        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        VertexConsumer vc = buffer.getBuffer(RenderType.gui());

        vc.addVertex(matrix, x1, y2, z).setColor(lr, lg, lb, la);
        vc.addVertex(matrix, x2, y2, z).setColor(rr, rg, rb, ra);
        vc.addVertex(matrix, x2, y1, z).setColor(rr, rg, rb, ra);
        vc.addVertex(matrix, x1, y1, z).setColor(lr, lg, lb, la);
    }

    /**
     * 绘制四边形，顶点坐标为当前poseStack下的局部HUD坐标空间
     */
    public void drawQuad(
            Vector3f v1, Vector3f v2,
            Vector3f v3, Vector3f v4,
            int argb,
            RenderType type
    ) {
        float a = (argb >>> 24) / 255f;
        float r = ((argb >> 16) & 255) / 255f;
        float g = ((argb >> 8) & 255) / 255f;
        float b = (argb & 255) / 255f;

        Matrix4f m = poseStack.last().pose();
        VertexConsumer vc = buffer.getBuffer(type);

        vc.addVertex(m, v1.x, v1.y, v1.z).setNormal(0, 0, 1).setColor(r, g, b, a);
        vc.addVertex(m, v2.x, v2.y, v2.z).setNormal(0, 0, 1).setColor(r, g, b, a);
        vc.addVertex(m, v3.x, v3.y, v3.z).setNormal(0, 0, 1).setColor(r, g, b, a);
        vc.addVertex(m, v4.x, v4.y, v4.z).setNormal(0, 0, 1).setColor(r, g, b, a);
    }


    /* ======================== 文本 ======================== */

    /**
     * 绘制文本（无阴影）
     *
     * @param text  要绘制的文本组件
     * @param x     文本左上角x坐标
     * @param y     文本左上角y坐标
     * @param color 文本颜色
     */
    public void drawText(Component text, float x, float y, int color) {
        drawText(text, x, y, color, false);
    }

    /**
     * 绘制文本
     *
     * @param text   要绘制的文本组件
     * @param x      文本左上角x坐标
     * @param y      文本左上角y坐标
     * @param color  文本颜色
     * @param shadow 是否绘制阴影
     */
    public void drawText(Component text, float x, float y, int color, boolean shadow) {
        poseStack.pushPose();
        poseStack.translate(x, y, 0);
        font.drawInBatch(
                text,
                0,
                0,
                color,
                shadow,
                poseStack.last().pose(),
                buffer,
                Font.DisplayMode.SEE_THROUGH,
                0,
                FULL_BRIGHT
        );
        poseStack.popPose();
    }

    /**
     * 绘制居中文本
     *
     * @param text    要绘制的文本组件
     * @param centerX 文本中心x坐标
     * @param y       文本左上角y坐标
     * @param color   文本颜色
     */
    public void drawCenteredText(Component text, float centerX, float y, int color) {
        drawText(text, centerX - font.width(text) / 2f, y, color);
    }

    /**
     * 绘制居中文本
     *
     * @param text    要绘制的文本组件
     * @param centerX 文本中心x坐标
     * @param y       文本左上角y坐标
     * @param color   文本颜色
     * @param shadow  是否绘制阴影
     */
    public void drawCenteredText(Component text, float centerX, float y, int color, boolean shadow) {
        drawText(text, centerX - font.width(text) / 2f, y, color, shadow);
    }

    /**
     * 在 3D HUD 平面中绘制物品
     *
     * @param stack 物品
     * @param x     左上角 X
     * @param y     左上角 Y
     * @param scale 缩放（1 = GUI 尺寸）
     */
    public void drawItem(ItemStack stack, float x, float y, float scale) {
        if (stack.isEmpty()) return;

        poseStack.pushPose();
        poseStack.translate(x, y, 0);
        poseStack.mulPose(Axis.ZP.rotationDegrees(180));
        poseStack.scale(scale, scale, scale);

        ItemRenderer itemRenderer = mc.getItemRenderer();
        BakedModel model = itemRenderer.getModel(stack, mc.level, mc.player, 0);

        if (model.isCustomRenderer()) {
            // —— BEWLR 路径 ——
            itemRenderer.render(
                    stack,
                    ItemDisplayContext.FIXED,
                    false,
                    poseStack,
                    buffer,
                    FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY,
                    model
            );
        } else {
            // —— 普通 BakedModel 路径 ——
            poseStack.pushPose();
            poseStack.translate(-0.5,-0.5,0);
            VertexConsumer vc = buffer.getBuffer(RenderType.cutout());
            itemRenderer.renderModelLists(
                    model,
                    stack,
                    FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY,
                    poseStack,
                    vc
            );
            poseStack.popPose();
        }

        poseStack.popPose();
    }

    /**
     * 在当前 PoseStack 空间中绘制一条直线
     *
     * @param from       起点（局部坐标）
     * @param to         终点（局部坐标）
     * @param argb       颜色（ARGB）
     * @param renderType 使用的 RenderType
     */
    public void drawLine(
            Vector3f from,
            Vector3f to,
            int argb,
            RenderType renderType
    ) {
        float a = (argb >>> 24) / 255f;
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;

        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        VertexConsumer vc = buffer.getBuffer(renderType);

        vc.addVertex(matrix, from.x(), from.y(), from.z())
                .setNormal(0, 0, 1)
                .setColor(r, g, b, a);

        vc.addVertex(matrix, to.x(), to.y(), to.z())
                .setNormal(0, 0, 1)
                .setColor(r, g, b, a);
    }

    /**
     * 在屏幕空间中绘制一条直线
     *
     * @param from       起点（屏幕坐标）
     * @param to         终点（屏幕坐标）
     * @param argb       颜色（ARGB）
     * @param renderType 使用的 RenderType
     */
    public void drawScreenLine(
            Vector3f from,
            Vector3f to,
            int argb,
            RenderType renderType
    ) {
        float a = (argb >>> 24) / 255f;
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;

        Matrix4f matrix = new Matrix4f().rotate(camera.rotation());
        VertexConsumer vc = buffer.getBuffer(renderType);

        vc.addVertex(matrix, from.x(), from.y(), from.z())
                .setNormal(0, 0, 1)
                .setColor(r, g, b, a);

        vc.addVertex(matrix, to.x(), to.y(), to.z())
                .setNormal(0, 0, 1)
                .setColor(r, g, b, a);
    }

    /**
     * 绘制保持朝向屏幕的有宽线段，坐标为当前poseStack下的局部坐标
     */
    public void drawScreenFacingLine(
            Vector3f localA,
            Vector3f localB,
            float thickness,
            int color,
            RenderType renderType
    ) {
        // 1. Local → NDC
        Vector3f ndcA = localToNdc(localA);
        Vector3f ndcB = localToNdc(localB);

        // 完全被裁剪或在背面
        if (ndcA.z > 1 && ndcB.z > 1) return;

        // 2. NDC → Screen
        Vector2f screenA = ndcToScreen(ndcA);
        Vector2f screenB = ndcToScreen(ndcB);

        // 3. 屏幕空间方向
        Vector2f dir = new Vector2f(screenB).sub(screenA);
        if (dir.lengthSquared() < 1e-6f) return;
        dir.normalize();

        // 4. 屏幕空间法线
        Vector2f normal = new Vector2f(-dir.y, dir.x)
                .mul(thickness * 0.5f);

        // 5. 屏幕四边形
        Vector2f s0 = new Vector2f(screenA).add(normal);
        Vector2f s1 = new Vector2f(screenA).sub(normal);
        Vector2f s2 = new Vector2f(screenB).sub(normal);
        Vector2f s3 = new Vector2f(screenB).add(normal);

        // 6. Screen → NDC（反向）
        Vector3f n0 = screenToNdc(s0, ndcA.z);
        Vector3f n1 = screenToNdc(s1, ndcA.z);
        Vector3f n2 = screenToNdc(s2, ndcB.z);
        Vector3f n3 = screenToNdc(s3, ndcB.z);

        // 7. NDC → View（逆 projection）
        Vector3f v0 = ndcToLocal(n0);
        Vector3f v1 = ndcToLocal(n1);
        Vector3f v2 = ndcToLocal(n2);
        Vector3f v3 = ndcToLocal(n3);

        // 8. 在当前 poseStack 下直接发顶点
        drawQuad(v0, v1, v2, v3, color, renderType);
    }


    /**
     * 从纹理中绘制一块区域，支持 Z 偏移
     */
    public void blit(
            ResourceLocation texture,
            float x, float y,
            float u, float v,
            float width, float height,
            float texW, float texH,
            float z
    ) {
        float u0 = u / texW;
        float v0 = v / texH;
        float u1 = (u + width) / texW;
        float v1 = (v + height) / texH;

        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();

        VertexConsumer vc = buffer.getBuffer(
                RenderType.entityCutout(texture)
        );

        vc.addVertex(matrix, x, y + height, z)
                .setUv(u0, v1)
                .setColor(1f, 1f, 1f, 1f);

        vc.addVertex(matrix, x + width, y + height, z)
                .setUv(u1, v1)
                .setColor(1f, 1f, 1f, 1f);

        vc.addVertex(matrix, x + width, y, z)
                .setUv(u1, v0)
                .setColor(1f, 1f, 1f, 1f);

        vc.addVertex(matrix, x, y, z)
                .setUv(u0, v0)
                .setColor(1f, 1f, 1f, 1f);
    }

    /**
     * 将世界坐标转换为当前 HUD PoseStack 下的局部坐标
     *
     * @param worldPos 世界坐标
     * @return HUD 局部空间坐标（UI 单位）
     */
    public Vector3f worldToLocal(Vector3f worldPos) {
        return viewToLocal(worldToView(worldPos));
    }

    /**
     * 将 HUD 局部坐标转换为世界坐标
     *
     * @param localPos 局部坐标（UI 单位）
     * @return 世界坐标
     */
    public Vector3f localToWorld(Vector3f localPos) {
        return viewToWorld(localToView(localPos));
    }

    /**
     * HUD空间 → 世界坐标
     */
    public Vector3f viewToWorld(Vector3f view) {
        Vector4f v = new Vector4f(view, 1.0f);

        // 1. HUD View → 相机朝向对齐空间
        hudAnchorMatrix.transform(v);

        // 2. 相机朝向对齐空间 → 世界方向
        new Matrix4f(modelViewMatrix).invert().transform(v);

        // 3. 加回相机位置
        Vec3 camPos = camera.getPosition();
        v.x += (float) camPos.x;
        v.y += (float) camPos.y;
        v.z += (float) camPos.z;

        return new Vector3f(v.x, v.y, v.z);
    }

    /**
     * 世界坐标 → HUD空间
     */
    public Vector3f worldToView(Vector3f world) {
        Vector4f v = new Vector4f(world, 1.0f);
        // 1. 世界 → 相机原点空间（减去相机位置）
        Vec3 camPos = camera.getPosition();
        v.x -= (float) camPos.x;
        v.y -= (float) camPos.y;
        v.z -= (float) camPos.z;
        // 2. 对齐相机朝向（旋转）
        modelViewMatrix.transform(v);
        // 3. 相机朝向空间 → HUD View
        hudAnchorInverse.transform(v);
        return new Vector3f(v.x, v.y, v.z);
    }

    /**
     * HUD 局部坐标 → HUD空间
     */
    public Vector3f localToView(Vector3f local) {
        Vector4f v = new Vector4f(local, 1.0f);
        poseStack.last().pose().transform(v);
        hudAnchorInverse.transform(v);
        return new Vector3f(v.x, v.y, v.z);
    }

    /**
     * HUD空间 → HUD 局部坐标
     */
    public Vector3f viewToLocal(Vector3f view) {
        Vector4f v = new Vector4f(view, 1.0f);
        hudAnchorMatrix.transform(v);
        new Matrix4f(poseStack.last().pose()).invert().transform(v);
        return new Vector3f(v.x, v.y, v.z);
    }

    public Vector3f localToNdc(Vector3f local) {
        Vector4f v = new Vector4f(local, 1.0f);

        // 1. Local → Camera View（已经是相机空间）
        poseStack.last().pose().transform(v);

        // 2. Camera View → Clip
        projectionMatrix.transform(v);

        // 3. Clip → NDC
        if (v.w != 0.0f) {
            v.div(v.w);
        }

        return new Vector3f(v.x, v.y, v.z);
    }

    public Vector3f ndcToLocal(Vector3f ndc) {
        Vector4f v = new Vector4f(ndc, 1.0f);

        // 1. NDC → Clip
        //    逆透视除法：恢复到 clip space
        //    这里约定 w = 1 即可（方向/平面反投影足够）
        //    如果你有真实的 clip.w，可在此传入
        //    否则保持 1 是 HUD 场景下的合理选择
        //    （与 localToNdc 对称）
        // v.w = 1.0f; // 已经是 1

        // 2. Clip → Camera View
        new Matrix4f(projectionMatrix).invert().transform(v);

        if (v.w != 0.0f) {
            v.div(v.w);
        }

        // 3. Camera View → Local
        new Matrix4f(poseStack.last().pose()).invert().transform(v);

        return new Vector3f(v.x, v.y, v.z);
    }

    public Vector2f ndcToScreen(Vector3f ndc) {
        float x = (ndc.x * 0.5f + 0.5f) * mc.getWindow().getGuiScaledWidth();
        float y = (1.0f - (ndc.y * 0.5f + 0.5f)) * mc.getWindow().getGuiScaledHeight();
        return new Vector2f(x, y);
    }

    private Vector3f screenToNdc(Vector2f screen, float z) {
        float x = (screen.x / mc.getWindow().getGuiScaledWidth()) * 2f - 1f;
        float y = 1f - (screen.y / mc.getWindow().getGuiScaledHeight()) * 2f;
        return new Vector3f(x, y, z);
    }


    /* ====================== 现版本RenderSystem不支持Stencil Test，相关方法无效，暂时注释 ======================= */

//    /**
//     * 开始一个基于 Stencil 的 3D 裁剪区域。
//     *
//     * <p><b>警告：</b></p>
//     * <ul>
//     *   <li>该方法会强制调用 {@link MultiBufferSource.BufferSource#endBatch()}，会打断批处理。</li>
//     *   <li>频繁调用会带来明显的性能开销，请仅用于必要的复杂裁剪场景。</li>
//     *   <li>不允许嵌套调用，必须与 {@link #endClipRect()} 成对使用。</li>
//     * </ul>
//     *
//     * @param x1 裁剪矩形左上角 X（当前 Pose 空间）
//     * @param y1 裁剪矩形左上角 Y
//     * @param x2 裁剪矩形右下角 X
//     * @param y2 裁剪矩形右下角 Y
//     */
//
//    public void beginClipRect(float x1, float y1, float x2, float y2) {
//        if (clippingActive) {
//            throw new IllegalStateException("Stencil 裁剪尚未结束，禁止嵌套 beginClipRect()");
//        }
//        if (!(buffer instanceof MultiBufferSource.BufferSource bufferSource)) {
//            throw new IllegalStateException(
//                    "当前 MultiBufferSource 不支持 endBatch，无法使用 Stencil 裁剪。"
//            );
//        }
//        clippingActive = true;
//
//        bufferSource.endBatch();
//        GL11.glEnable(GL11.GL_STENCIL_TEST);
//        // 清空 stencil buffer
//        RenderSystem.clearStencil(0);
//
//        // 禁止颜色 & 深度写入
//        RenderSystem.colorMask(false, false, false, false);
//        RenderSystem.depthMask(false);
//
//        // 允许写入 stencil
//        RenderSystem.stencilMask(0xFF);
//
//        // 所有片段都写入 stencil 值 1
//        RenderSystem.stencilFunc(GL11.GL_ALWAYS, 1, 0xFF);
//        RenderSystem.stencilOp(
//                GL11.GL_KEEP,
//                GL11.GL_KEEP,
//                GL11.GL_REPLACE
//        );
//
//        // 在当前 Pose 下绘制裁剪平面
//        fill(x1, y1, x2, y2, 0xFFFFFFFF, 0);
//
//        // 提交裁剪平面
//        bufferSource.endBatch();
//
//        // 恢复颜色 & 深度写入
//        RenderSystem.colorMask(true, true, true, true);
//        RenderSystem.depthMask(true);
//
//        // 禁止再写入 stencil
//        RenderSystem.stencilMask(0x00);
//
//        // 只允许 stencil == 1 的区域通过
//        RenderSystem.stencilFunc(GL11.GL_EQUAL, 1, 0xFF);
//        RenderSystem.stencilOp(
//                GL11.GL_KEEP,
//                GL11.GL_KEEP,
//                GL11.GL_KEEP
//        );
//    }
//
//    /**
//     * 结束当前 Stencil 裁剪区域。
//     *
//     * <p><b>必须与 {@link #beginClipRect(float, float, float, float)} 成对调用。</b></p>
//     *
//     * @throws IllegalStateException 如果当前未处于裁剪状态
//     */
//    public void endClipRect() {
//        if (!clippingActive) {
//            throw new IllegalStateException("未开始 Stencil 裁剪，却调用了 endClipRect()");
//        }
//        if (buffer instanceof MultiBufferSource.BufferSource bufferSource) {
//            bufferSource.endBatch();
//        }
//        GL11.glDisable(GL11.GL_STENCIL_TEST);
//        // 恢复 stencil 状态：允许所有片段通过
//        RenderSystem.stencilMask(0xFF);
//        RenderSystem.stencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
//        RenderSystem.stencilOp(
//                GL11.GL_KEEP,
//                GL11.GL_KEEP,
//                GL11.GL_KEEP
//        );
//
//        clippingActive = false;
//    }
}