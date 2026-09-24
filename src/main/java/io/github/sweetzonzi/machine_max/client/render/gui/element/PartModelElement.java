package io.github.sweetzonzi.machine_max.client.render.gui.element;

import cn.solarmoon.spark_core.animation.model.ModelPose;
import cn.solarmoon.spark_core.animation.model.origin.OBone;
import cn.solarmoon.spark_core.animation.model.origin.OCube;
import cn.solarmoon.spark_core.animation.renderer.ModelRenderHelperKt;
import com.jme3.math.Transform;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Rect;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.PartType;
import io.github.sweetzonzi.machine_max.common.visual.PartAnimatable;
import io.github.sweetzonzi.machine_max.common.visual.SubPartAnimatable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.util.Map;

/**
 * 在 AUI 页面里渲染一个部件 3D 模型的扩展元素（标签名 {@value #TAG_NAME}）。
 *
 * <p>用途是验证「AUI 自定义元素 + Spark-Core 模型渲染」这条链路是否可行：
 * 元素初始化时用硬编码的 {@link ResourceLocation} 取出 {@link PartType}，
 * 据此构造独立的 {@link PartAnimatable}（不需要 {@code Part} 实例，也不需要 {@code VehicleCore}），
 * 再在绘制钩子里按 {@link ModelRenderHelperKt#render} 的调用约定逐骨骼渲染。</p>
 *
 * <p>绘制走 AUI 的 {@code drawPhase(BODY)} 路径，所在上下文是 Minecraft 的 GUI 渲染管线，
 * 因此 {@code PoseStack} 就是 {@code GuiGraphics} 的姿态栈，可直接取用
 * {@code Minecraft.renderBuffers().bufferSource()} 作为顶点消费者。</p>
 *
 * <p><b>自适应</b>：模型尺寸每个绘制帧都由元素当前的内容区尺寸推导，
 * 窗口或布局变化会在下一帧自动反映。</p>
 *
 * <p>可用属性：</p>
 * <ul>
 *   <li>{@code fill} —— 模型占内容区短边的比例，默认 {@value #DEFAULT_FILL}；
 *       {@code 0.9} 表示模型最长的那个方向几乎贴满短边。</li>
 *   <li>{@code spin} —— 每秒绕竖直轴自转的角度，默认 {@value #DEFAULT_SPIN}（不自转）。
 *       自转能直观确认元素确实在按帧重绘。</li>
 * </ul>
 *
 * <p>注册：本类由 {@code MachineMaxClient} 在客户端模组构造期调用 {@link Element#register}
 * 直接登记标签。这里不使用 AUI 的 {@code @ElementRegister} 注解，因为 AUI 的注解扫描在它
 * 自己的模组构造期执行，且扫描前会把范围收窄到 {@code com.sighs.apricityui.element}，
 * 本模组声明了 {@code ordering="AFTER"}，注定在其之后构造。</p>
 *
 * <p>线程：仅客户端；构造函数与绘制均发生在渲染线程。</p>
 */
@OnlyIn(Dist.CLIENT)
public class PartModelElement extends Element {

    public static final String TAG_NAME = "MACHINEMAX-PART-MODEL";

    /** 硬编码的测试部件类型：内容包中 parts/wine_fox/wine_fox_hull.json 的注册名 */
    private static final ResourceLocation TEST_PART_ID =
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "wine_fox_hull");
    /** 单值 variants 形式的部件在 PartType 中的变体键名固定为 default */
    private static final String TEST_VARIANT = "default";

    private static final float DEFAULT_FILL = 0.9f;
    private static final float DEFAULT_SPIN = 0.0f;

    /** 基础观察视角：绕 X 轴俯视 20°，绕 Y 轴偏转 -30°，与载具预览的视角习惯一致 */
    private static final float BASE_PITCH = 20.0f;
    private static final float BASE_YAW = -30.0f;

    /**
     * 模型整体沿 Z 轴再前移的余量（逻辑像素）。
     *
     * <p>本相位里元素背景板先于模型绘制并且会写入深度，模型必须整体落在背景板之前，
     * 否则会被深度测试剔掉。前移量 = 包围盒半对角线 × scale + 本余量，
     * 半对角线保证任意视角旋转下模型都仍在背景板之前。</p>
     */
    private static final float DEPTH_MARGIN = 1.0f;

    private static final int FULL_WHITE = 0xFFFFFFFF;

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 解析出来的部件动画体，为 null 表示尚未成功建立（资源未加载或对应逻辑侧无此部件） */
    private PartAnimatable partAnimatable;
    /** 是否已经尝试过建立部件动画体，避免每帧重复查询注册表 */
    private boolean initAttempted;
    /** 是否已打印过首次绘制诊断日志 */
    private boolean firstDrawLogged;
    /** 是否已打印过渲染失败日志，避免每帧刷屏 */
    private boolean renderFailureLogged;

    // 部件空间下的包围盒（已含各子部件的相对摆放与 setTransform 给的位姿）。
    // 用于自适应缩放、居中，以及计算保证不被背景板遮挡的深度前移量。
    private final Vector3f boundsMin = new Vector3f();
    private final Vector3f boundsMax = new Vector3f();
    /** 包围盒是否有效（模型里存在可测量的立方体） */
    private boolean boundsValid;

    public PartModelElement(Document document) {
        super(document, TAG_NAME);
    }

    /**
     * 初始化钩子：属性与子节点迁移完成后才会被调用，因此可以在这里读取初始属性。
     *
     * @param origin 被替换掉的通用元素
     */
    @Override
    protected void onInitFromDom(Element origin) {
        initPart();
    }

    /**
     * 取出硬编码部件并构造 {@link PartAnimatable}。
     *
     * <p>需要内容包已加载该部件（{@code MMDynamicRes.PART_TYPES}），因此只能在客户端关卡可用时进行。</p>
     */
    private void initPart() {
        if (initAttempted) return;
        Level level = Minecraft.getInstance().level;
        if (level == null) return; // 关卡未就绪，留待首次绘制时再试
        initAttempted = true;

        /*
          本方法会从 onInitFromDom 调用，而 AUI 对那里的异常同样没有兜底：
          抛出会中断 Document 的建立，表现为「页面没被解析」。
        */
        try {
            PartType partType = PartType.get(level, TEST_PART_ID);
            if (partType == null) {
                LOGGER.warn("[AUI 测试] 未找到部件类型 {}，请确认内容包已加载", TEST_PART_ID);
                return;
            }
            if (partType.getVariant(TEST_VARIANT) == null) {
                LOGGER.warn("[AUI 测试] 部件 {} 不存在变体 {}，可用变体：{}",
                        TEST_PART_ID, TEST_VARIANT, partType.getVariants().keySet());
                return;
            }
            // 部件动画体自带根子部件的单位变换，无需 VehicleCore；渲染矩阵由各子部件自行给出
            partAnimatable = new PartAnimatable(level, partType, TEST_VARIANT);
            /*
              必须补一次初始位姿：PartAnimatable 只让根子部件保持单位变换，而
              SubPartAnimatable.oldTransform 的初值是 null，getRenderWorldPositionMatrix
              内部用到的 Transform.lerp 是非空扩展函数，收到 null 会抛 NPE。
              setTransform 会同时写入 transform 与 oldTransform，并把其余子部件摆到相对位置。
              传入单位变换等价于「把根子部件质心放在模型原点」，与物品独立渲染的处理一致。
            */
            partAnimatable.setTransform(new Transform());
            computeBounds();
            LOGGER.info("[AUI 测试] 已建立部件动画体 {}（变体 {}，子部件 {} 个，包围盒 {}）",
                    TEST_PART_ID, TEST_VARIANT, partAnimatable.getSubParts().size(),
                    boundsValid ? boundsSize() : "无");
        } catch (Throwable throwable) {
            LOGGER.error("[AUI 测试] 建立部件动画体失败：{}", TEST_PART_ID, throwable);
        }
    }

    /**
     * 计算部件空间下的包围盒。
     *
     * <p>坐标系链：立方体顶点 → 骨骼自身及所有父骨骼的局部位姿（
     * {@link OBone#applyTransformWithParents}）→ 模型根空间 → 子部件在部件中的摆放
     * （{@link SubPartAnimatable#getRenderWorldPositionMatrix}）。这条链与渲染时逐骨骼
     * 乘矩阵的顺序一致，所以量出来的范围就是画面上模型的实际范围。</p>
     */
    private void computeBounds() {
        if (partAnimatable == null) return;

        Matrix4f boneMatrix = new Matrix4f();
        Vector3f corner = new Vector3f();
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        boolean any = false;

        for (SubPartAnimatable subPart : partAnimatable.getSubParts().values()) {
            Matrix4f subPartMatrix = subPart.getRenderWorldPositionMatrix(1.0f);
            ModelPose pose = subPart.getModelController().getModel().getPose();
            for (OBone bone : subPart.getBones().values()) {
                boneMatrix.identity();
                bone.applyTransformWithParents(pose, boneMatrix, 1.0f);
                boneMatrix.mul(subPartMatrix);
                for (OCube cube : bone.getCubes()) {
                    Vec3 origin = cube.getOriginPos();
                    Vec3 size = cube.getSize();
                    // OCube 的顶点集合由 OVertexSet(originPos, size, inflate) 生成，
                    // 因此这里用同一来源还原它的八个角点
                    double inflate = cube.getInflate();
                    float x0 = (float) (origin.x - inflate);
                    float y0 = (float) (origin.y - inflate);
                    float z0 = (float) (origin.z - inflate);
                    float x1 = (float) (origin.x + size.x + inflate);
                    float y1 = (float) (origin.y + size.y + inflate);
                    float z1 = (float) (origin.z + size.z + inflate);
                    for (int i = 0; i < 8; i++) {
                        corner.set(
                                (i & 1) == 0 ? x0 : x1,
                                (i & 2) == 0 ? y0 : y1,
                                (i & 4) == 0 ? z0 : z1
                        );
                        boneMatrix.transformPosition(corner);
                        minX = Math.min(minX, corner.x);
                        minY = Math.min(minY, corner.y);
                        minZ = Math.min(minZ, corner.z);
                        maxX = Math.max(maxX, corner.x);
                        maxY = Math.max(maxY, corner.y);
                        maxZ = Math.max(maxZ, corner.z);
                        any = true;
                    }
                }
            }
        }

        if (!any) {
            LOGGER.warn("[AUI 测试] 部件 {} 没有可测量的立方体，无法计算包围盒", TEST_PART_ID);
            boundsValid = false;
            return;
        }
        boundsMin.set(minX, minY, minZ);
        boundsMax.set(maxX, maxY, maxZ);
        boundsValid = true;
    }

    /**
     * 包围盒三个方向的尺寸描述，仅用于日志。
     *
     * @return 形如 {@code 9.0x4.5x5.0} 的字符串
     */
    private String boundsSize() {
        return String.format("%.2fx%.2fx%.2f",
                boundsMax.x - boundsMin.x,
                boundsMax.y - boundsMin.y,
                boundsMax.z - boundsMin.z);
    }

    /**
     * 绘制钩子。分三个相位调用：阴影、主体、边框。
     * 模型画在主体相位、背景之后，从而叠在元素背景之上。
     */
    @Override
    public void drawPhase(PoseStack poseStack, Base.RenderPhase phase) {
        Rect rect = Rect.of(this);
        switch (phase) {
            case SHADOW -> rect.drawShadow(poseStack);
            case BODY -> {
                rect.drawBody(poseStack);
                drawModel(poseStack, rect);
            }
            case BORDER -> rect.drawBorder(poseStack);
        }
    }

    /**
     * 把部件模型渲染到元素的内容区中心，并按内容区尺寸自适应缩放。
     *
     * <p>渲染流程与 {@code CustomModelItemRenderer#renderPartAnimatable} 一致：
     * 逐子部件乘上其渲染世界矩阵，再逐骨骼调用 {@link ModelRenderHelperKt#render}。</p>
     */
    private void drawModel(PoseStack poseStack, Rect rect) {
        // 自定义绘制元素没有固有尺寸，尺寸为零时直接跳过，避免无意义的上传与绘制
        Size size = rect.getBodyRectSize();
        if (size.width() <= 0 || size.height() <= 0) return;

        if (!initAttempted) initPart(); // 关卡在初始化时还没就绪，这里补一次
        if (partAnimatable == null || !boundsValid) return;

        Position position = rect.getBodyRectPosition();
        float fill = readFloatAttribute("fill", DEFAULT_FILL);
        float spin = readFloatAttribute("spin", DEFAULT_SPIN);
        float yaw = BASE_YAW + spin * (Util.getMillis() % 3_600_000L) / 1000.0f;

        // 自适应：按内容区短边与模型最长方向的比值定 scale，布局一变下一帧就跟着变
        float extentX = boundsMax.x - boundsMin.x;
        float extentY = boundsMax.y - boundsMin.y;
        float extentZ = boundsMax.z - boundsMin.z;
        float maxExtent = Math.max(extentX, Math.max(extentY, extentZ));
        if (maxExtent <= 0) return;
        float boxShortSide = (float) Math.min(size.width(), size.height());
        float scale = boxShortSide * fill / maxExtent;

        // 半对角线 × scale：任意视角旋转下模型的 Z 跨度上界，加上余量后整体前移到背景板之前
        float halfDiagonal = (float) Math.sqrt(extentX * extentX + extentY * extentY + extentZ * extentZ) / 2.0f;
        float forward = halfDiagonal * scale + DEPTH_MARGIN;

        Vector3f center = new Vector3f(boundsMin).add(boundsMax).mul(0.5f);

        /*
          本方法在 AUI 的绘制列表遍历中被调用，而 AUI 不会捕获扩展元素绘制里的异常：
          异常会向上抛出并中断整篇文档的绘制，表现出来是「整个页面都不渲染」，
          很难与「标签没被解析」区分。因此这里单独兜住，只记一次日志。
          poseStack 的平衡由 AUI 在每个绘制节点前后自行 save/restore，无需在此处理。
        */
        try {
            if (!firstDrawLogged) {
                firstDrawLogged = true;
                logFirstDraw(size, scale, forward);
            }
            // 先冲刷 AUI 自己累积的几何与贴图批次：它的批处理是延迟提交的，
            // 不先冲刷会让后续的 3D 绘制与页面内容错序
            Base.commitLocalDraws();

            MultiBufferSource.BufferSource bufferSource =
                    Minecraft.getInstance().renderBuffers().bufferSource();

            poseStack.pushPose();
            // 原点落到内容区中心，并整体前移到背景板之前
            poseStack.translate(
                    (float) position.x + (float) size.width() / 2.0f,
                    (float) position.y + (float) size.height() / 2.0f,
                    forward);
            // 上下取反：模型空间 Y 向上，GUI 空间 Y 向下；X 保持正向以维持左右关系
            poseStack.scale(scale, -scale, scale);
            poseStack.mulPose(new Quaternionf()
                    .rotateX((float) Math.toRadians(BASE_PITCH))
                    .rotateY((float) Math.toRadians(yaw)));
            // 把模型包围盒中心挪到旋转中心，这样旋转与缩放都以模型中心为基准，
            // 无论视角怎么转、盒子多大，模型都居中
            poseStack.translate(-center.x, -center.y, -center.z);

            for (SubPartAnimatable subPart : partAnimatable.getSubParts().values()) {
                poseStack.pushPose();
                // 插值系数取 1：本元素是静态预览，不参与动画插值（与物品 3D 渲染同处理）
                poseStack.mulPose(subPart.getRenderWorldPositionMatrix(1.0f));
                for (OBone bone : subPart.getBones().values()) {
                    ModelRenderHelperKt.render(
                            bone,
                            subPart.getModelController().getModel().getPose(),
                            poseStack,
                            bufferSource.getBuffer(RenderType.entityCutout(
                                    subPart.getModelController().getTextureLocation())),
                            LightTexture.FULL_BRIGHT,
                            OverlayTexture.NO_OVERLAY,
                            FULL_WHITE,
                            1.0f,
                            false
                    );
                }
                poseStack.popPose();
            }
            poseStack.popPose();

            bufferSource.endBatch();
        } catch (Throwable throwable) {
            if (!renderFailureLogged) {
                renderFailureLogged = true;
                LOGGER.error("[AUI 测试] 部件模型渲染失败，本次之后不再重复报告", throwable);
            }
        }
    }

    /**
     * 首次绘制时打印一次元素状态，便于区分「标签没被解析」和「解析了但画不出来」。
     *
     * @param size    元素内容区尺寸
     * @param scale   本次推导出的缩放倍率
     * @param forward 本次使用的 Z 前移量
     */
    private void logFirstDraw(Size size, float scale, float forward) {
        StringBuilder detail = new StringBuilder();
        for (Map.Entry<String, SubPartAnimatable> entry : partAnimatable.getSubParts().entrySet()) {
            SubPartAnimatable subPart = entry.getValue();
            detail.append(System.lineSeparator())
                    .append("  子部件 ").append(entry.getKey())
                    .append("：骨骼 ").append(subPart.getBones().size()).append(" 个")
                    .append("，贴图 ").append(subPart.getModelController().getTextureLocation());
        }
        LOGGER.info("[AUI 测试] 首次绘制 内容区={}x{} 包围盒={} scale={} 前移={} 子部件 {} 个{}",
                (int) size.width(), (int) size.height(), boundsSize(),
                String.format("%.2f", scale), String.format("%.2f", forward),
                partAnimatable.getSubParts().size(), detail);
    }

    /**
     * 读取浮点属性，缺失或无法解析时返回默认值。
     *
     * @param name         属性名
     * @param defaultValue 默认值
     * @return 解析结果或默认值
     */
    private float readFloatAttribute(String name, float defaultValue) {
        String raw = getAttribute(name);
        if (raw == null || raw.isBlank()) return defaultValue;
        try {
            return Float.parseFloat(raw.trim());
        } catch (NumberFormatException e) {
            LOGGER.warn("[AUI 测试] 属性 {} 的值“{}”无法解析为浮点数，使用默认值 {}", name, raw, defaultValue);
            return defaultValue;
        }
    }
}
