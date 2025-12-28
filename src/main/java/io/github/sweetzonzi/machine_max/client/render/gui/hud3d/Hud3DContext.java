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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Brightness;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.*;

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

        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
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
     * 绘制四边形，顶点坐标为透视投影下的屏幕空间
     */
    private void drawQuad(
            Vector3f v1, Vector3f v2,
            Vector3f v3, Vector3f v4,
            int argb,
            RenderType type
    ) {
        float a = (argb >>> 24) / 255f;
        float r = ((argb >> 16) & 255) / 255f;
        float g = ((argb >> 8) & 255) / 255f;
        float b = (argb & 255) / 255f;

        Matrix4f m = new Matrix4f().rotate(camera.rotation());
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
        font.drawInBatch(
                text,
                x,
                y,
                color,
                shadow,
                poseStack.last().pose(),
                buffer,
                Font.DisplayMode.NORMAL,
                0,
                FULL_BRIGHT
        );
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
        poseStack.scale(scale, scale, scale);
        poseStack.mulPose(Axis.XP.rotationDegrees(180));

        itemRenderer.renderStatic(
                stack,
                ItemDisplayContext.GUI,
                FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                buffer,
                Minecraft.getInstance().level,
                0
        );
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
            Vector3f from,
            Vector3f to,
            float screenWidth, // 屏幕空间宽度（NDC 单位）
            int argb,
            RenderType renderType
    ) {
        // 1. local -> view
        Vector3f v0 = localToView(new Vector3f(from));
        Vector3f v1 = localToView(new Vector3f(to));

        if (v0.z >= -0.01f && v1.z >= -0.01f) return;

        // 2. view -> NDC（不含 projection matrix，手动做透视除法）
        Vector2f s0 = new Vector2f(v0.x / -v0.z, v0.y / -v0.z);
        Vector2f s1 = new Vector2f(v1.x / -v1.z, v1.y / -v1.z);

        Vector2f dir = new Vector2f(s1).sub(s0);
        if (dir.lengthSquared() < 1e-6f) return;
        dir.normalize();

        // 屏幕空间法线
        Vector2f perp = new Vector2f(-dir.y, dir.x);

        Vector2f p0a = new Vector2f(s0).add(new Vector2f(perp).mul(screenWidth * 0.5f));
        Vector2f p0b = new Vector2f(s0).sub(new Vector2f(perp).mul(screenWidth * 0.5f));
        Vector2f p1a = new Vector2f(s1).add(new Vector2f(perp).mul(screenWidth * 0.5f));
        Vector2f p1b = new Vector2f(s1).sub(new Vector2f(perp).mul(screenWidth * 0.5f));

        // 3. NDC -> view（反透视）
        Vector3f q0a = new Vector3f(p0a.x * -v0.z, p0a.y * -v0.z, v0.z);
        Vector3f q0b = new Vector3f(p0b.x * -v0.z, p0b.y * -v0.z, v0.z);
        Vector3f q1a = new Vector3f(p1a.x * -v1.z, p1a.y * -v1.z, v1.z);
        Vector3f q1b = new Vector3f(p1b.x * -v1.z, p1b.y * -v1.z, v1.z);


        drawQuad(q0a, q0b, q1b, q1a, argb, renderType);
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
        Matrix4f inverse = new Matrix4f()
                .translate(camera.getPosition().toVector3f())
                .mul(poseStack.last().pose())
                .invert();

        Vector4f vec4 = new Vector4f(worldPos, 1.0F).mul(inverse);
        return new Vector3f(vec4.x, vec4.y, vec4.z);
    }

    /**
     * 将 HUD 局部坐标转换为世界坐标
     *
     * @param localPos 局部坐标（UI 单位）
     * @return 世界坐标
     */
    public Vector3f localToWorld(Vector3f localPos) {
        Matrix4f mat = new Matrix4f()
                .translate(camera.getPosition().toVector3f())
                .mul(poseStack.last().pose());

        Vector4f vec4 = new Vector4f(localPos, 1.0F).mul(mat);
        return new Vector3f(vec4.x, vec4.y, vec4.z);
    }

    /**
     * 相机视空间 → 世界坐标
     */
    public Vector3f viewToWorld(Vector3f viewPos) {
        // view -> local 的旋转
        camera.rotation().transform(viewPos);
        viewPos.add(camera.getPosition().toVector3f());
        return viewPos;
    }

    /**
     * 世界坐标 → 相机视空间
     * （显式使用相机位置 + 旋转）
     */
    public Vector3f worldToView(Vector3f world) {
        Vector3f v = new Vector3f(world)
                .sub(camera.getPosition().toVector3f());
        camera.rotation().conjugate(new Quaternionf()).transform(v);
        return v;
    }

    /**
     * HUD 局部坐标 → 相机视空间
     * 使用 PoseStack + modelViewMatrix（不含平移）
     */
    public Vector3f localToView(Vector3f local) {
        Vector4f v = new Vector4f(local, 1.0f)
                .mul(poseStack.last().pose())
                .mul(modelViewMatrix);
        return new Vector3f(v.x, v.y, v.z);
    }

    /**
     * 相机视空间 → HUD 局部坐标
     */
    public Vector3f viewToLocal(Vector3f view) {
        Matrix4f inv = new Matrix4f(poseStack.last().pose())
                .invert()
                .mul(new Matrix4f(modelViewMatrix).invert());
        Vector4f v = new Vector4f(view, 1.0f).mul(inv);
        return new Vector3f(v.x, v.y, v.z);
    }

    /**
     * 相机视空间 → Clip Space
     */
    public Vector4f viewToClip(Vector3f view) {
        return new Vector4f(view, 1.0f).mul(projectionMatrix);
    }

    /**
     * 相机视空间 → NDC
     */
    public Vector3f viewToNdc(Vector3f view) {
        Vector4f clip = viewToClip(view);
        return new Vector3f(
                clip.x / clip.w,
                clip.y / clip.w,
                clip.z / clip.w
        );
    }

    /**
     * HUD 局部坐标 → NDC
     */
    public Vector3f localToNdc(Vector3f local) {
        return viewToNdc(localToView(local));
    }

    /**
     * NDC → 相机视空间
     *
     * @param ndc   [-1,1]
     */
    public Vector3f ndcToView(Vector3f ndc) {
        Matrix4f invProj = new Matrix4f(projectionMatrix).invert();

        Vector4f clip = new Vector4f(ndc, 1.0f);
        Vector4f view = clip.mul(invProj);

        // 反齐次除法
        view.div(view.w);

        return new Vector3f(view.x, view.y, view.z);
    }

    /**
     * NDC → HUD 局部坐标
     */
    public Vector3f ndcToLocal(Vector3f ndc) {
        return viewToLocal(ndcToView(ndc));
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