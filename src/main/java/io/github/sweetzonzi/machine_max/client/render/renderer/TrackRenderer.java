package io.github.sweetzonzi.machine_max.client.render.renderer;

import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.visual_effect.VisualEffectRenderer;
import com.jme3.math.Vector3f;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackEnvelope2D;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackLink;
import io.github.sweetzonzi.machine_max.util.geometry.BeltPath2D;
import io.github.sweetzonzi.machine_max.util.geometry.Vec2;
import jme3utilities.math.MyQuaternion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 履带节渲染：在轮组包络线上按固定节距放置履带节，并按主动轮转速积分相位。
 *
 * <p>几何全部是客户端本地计算：每个渲染帧用 {@code partialTick} 的轮位姿重建
 * {@link TrackEnvelope2D}，节位置 {@code s_i = (L·i/N + phase) mod L}。相位在
 * {@link #tick()}（20tps）里积分，渲染时用上一 tick / 本 tick 双值插值。
 * {@link TrackLink} 本身只在创建 / 解散 / 断带 / 修复 / 成员变化时同步。</p>
 *
 * <p><b>挂载方式</b>：本类作为 {@link VisualEffectRenderer} 只承担每 tick 的相位积分，
 * {@link #getRenderStage()} 不返回阶段，因此不参与 {@code RenderLevelStageEvent} 调度。
 * 实际绘制入口是 {@link #renderTrack}，由 {@link PartEntityRenderer}（以及
 * {@link DistantVehicleRenderer}）在主动轮所属实体的 {@code render} 中调用，使履带与零件
 * 模型共用实体渲染管线：</p>
 * <ul>
 *   <li>渲染类型使用带 lightmap / overlay / normal 的实体不透明类型，节的颜色随世界光照，
 *       并按面法线产生明暗；</li>
 *   <li>Iris 的阴影 pass 由 {@code ShadowRenderer} 直接调用 {@code EntityRenderDispatcher}
 *       与各实体的渲染器，不触发 {@code RenderLevelStageEvent}；只有实体管线里的几何会被
 *       写进阴影贴图。</li>
 * </ul>
 *
 * <p>初版不依赖模型 / 贴图资产：每节是程序化扁长方体，贴图取原版纯白纹理，所有颜色与
 * 明暗来自顶点色、法线光照与光照图。单节模型接入后只需替换 {@link #renderLink} 的绘制
 * 部分。</p>
 *
 * <p>线程：{@link #tick()} 在主线程，{@link #renderTrack} 在渲染线程；共享状态只有
 * {@link State} 的 volatile 相位 / 节数。</p>
 */
public class TrackRenderer extends VisualEffectRenderer {
    /** 单节宽度（米）的占位值；接入单节模型后以模型横向尺寸为准。 */
    private static final float LINK_WIDTH = 0.35f;
    /** 节长占节距的比例，剩余部分作为节间小间隙。 */
    private static final double LINK_LENGTH_RATIO = 0.92;
    private static final double TICK_SECONDS = 1.0 / 20.0;
    /** 占位贴图：原版 4×4 纯白纹理；节的基色、面间明暗与光照图在此之上调制。 */
    private static final ResourceLocation LINK_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    /**
     * 实体不透明渲染类型：写入深度、正面剔除，带 lightmap / overlay / normal 顶点属性。
     * 与零件模型使用同一套实体光照，同时保证阴影 pass 里可作为实体几何绘制。
     */
    private static final RenderType LINK_RENDER_TYPE = RenderType.entitySolid(LINK_TEXTURE);
    /** 单节统一基色；面间明暗交给实体管线的两向光照，顶点色只承载基色。 */
    private static final int LINK_COLOR = 0xFF9A968C;

    /** 相位状态按 TrackLink 索引；tick 写入，渲染读取。 */
    private static final Map<TrackLink, State> STATES = new ConcurrentHashMap<>();

    private static final class State {
        private volatile int linkCount;
        private volatile double phase;
        private volatile double prevPhase;
    }

    @Override
    public void tick() {
        if (Minecraft.getInstance().level == null) return;

        for (VehicleCore vehicle : ObjectManager.clientAllVehicles.values()) {
            for (TrackLink link : vehicle.trackLinks) {
                if (link.isDisposed()) {
                    STATES.remove(link);
                    continue;
                }
                State state = STATES.computeIfAbsent(link, ignored -> new State());
                state.prevPhase = state.phase;

                TrackEnvelope2D.Result envelope =
                        link.envelope(1f);
                if (envelope == null) {
                    if (state.linkCount == 0) state.linkCount = link.linkCount();
                    continue;
                }
                int linkCount = envelope.path().linkCount(link.attr().linkLength());
                if (state.linkCount == 0) {
                    state.linkCount = linkCount;
                }
                if (link.isBroken()) continue;

                SubPart sprocket = link.sprocket();
                if (sprocket == null) continue;
                float omega = relativeAngularVelocityX(link, sprocket);
                // (Y,Z) 平面的正角绕轮轴 +X；闭环的有向圆弧与轮面同向运动。
                double winding = Math.signum(envelope.path().signedArea());
                state.phase += omega * sprocket.getMaxWheelRadius() * TICK_SECONDS * winding;
            }
        }

        STATES.entrySet().removeIf(entry -> {
            TrackLink link = entry.getKey();
            return link.isDisposed() || !link.vehicle().trackLinks.contains(link);
        });
    }

    @Override
    public void physTick(@NotNull PhysicsLevel physicsLevel) {
    }

    /**
     * 绘制 {@code subPart} 所属的整条履带；{@code subPart} 不是该履带的主动轮时直接返回。
     *
     * <p>由实体渲染管线调用（{@link PartEntityRenderer} / {@link DistantVehicleRenderer}），
     * 因此 {@code poseStack} 的当前变换必须把世界原点映射到相机相对原点：
     * {@code PartEntityRenderer} 用 {@code translate(-entityPos)} 达到，远处渲染器用
     * {@code modelViewMatrix + translate(-camPos)} 达到。</p>
     *
     * @param subPart     实体对应的 SubPart；只有主动轮才会触发绘制，避免同一条履带被每个成员轮各画一遍
     * @param poseStack   已把世界原点映射到相机相对原点的姿态栈
     * @param packedLight 主动轮处的打包光照坐标，整条履带共用
     * @param partialTick 帧间插值系数
     */
    public static void renderTrack(@NotNull SubPart subPart, @NotNull PoseStack poseStack,
                                   @NotNull MultiBufferSource bufferSource,
                                   float partialTick, int packedLight) {
        TrackLink link = subPart.trackLink;
        if (link == null || link.isDisposed() || !link.isSprocket(subPart)) return;

        TrackEnvelope2D.Result envelope = link.envelope(partialTick);
        if (envelope == null) return;

        State state = STATES.get(link);
        int linkCount = state == null || state.linkCount <= 0
                ? envelope.path().linkCount(link.attr().linkLength())
                : state.linkCount;
        if (linkCount <= 0) return;

        double phase = state == null ? 0 : lerp(state.prevPhase, state.phase, partialTick);
        renderBelt(poseStack, bufferSource, envelope, linkCount, phase,
                LINK_WIDTH, link.attr().linkThickness(), packedLight);
    }

    private static void renderBelt(PoseStack poseStack, MultiBufferSource bufferSource,
                                   TrackEnvelope2D.Result envelope, int linkCount, double phase,
                                   float width, float thickness, int packedLight) {
        double length = envelope.path().length();
        if (!(length > 1e-6)) return;
        float wheelAxis = (float) -Math.signum(envelope.path().signedArea());
        double spacing = length / linkCount;
        float halfLength = (float) (spacing * LINK_LENGTH_RATIO * 0.5);
        float halfThickness = thickness * 0.5f;
        float halfWidth = width * 0.5f;

        for (int i = 0; i < linkCount; i++) {
            double arc = (length * i / linkCount + phase) % length;
            if (arc < 0) arc += length;
            BeltPath2D.Sample sample = envelope.path().sampleAt(arc);
            Vec2 point = sample.point().add(sample.normal().mul(halfThickness));
            Vec2 tangent = sample.tangent();
            Vec2 normal = sample.normal();

            // 局部基：+X = 切线，+Y = 外法线，+Z = 轮轴。
            // 切线 × 外法线 = -绕向 × 局部X，第三轴随绕向取值以保持右手系。
            // JOML 的 16 参构造按列主序收参：前 4 个是第 0 列，最后 4 个是平移列。
            // 2D 采样点 (y,z) 映射回主动轮局部系是 (0, y, z)。
            Matrix4f local = new Matrix4f(
                    0, (float) tangent.x(), (float) tangent.y(), 0,
                    0, (float) normal.x(), (float) normal.y(), 0,
                    wheelAxis, 0, 0, 0,
                    0, (float) point.x(), (float) point.y(), 1
            );
            Matrix4f world = new Matrix4f(envelope.sprocketWorld()).mul(local);

            poseStack.pushPose();
            poseStack.mulPose(world);
            renderLink(poseStack, bufferSource, halfLength, halfThickness, halfWidth, packedLight);
            poseStack.popPose();
        }
    }

    /**
     * 画一节扁长方体。每个面按外法线顺序给出逆时针顶点，正面剔除下从外面可见；
     * 明暗由 {@link RenderType#entitySolid} 的实体着色器（两向光照 + 光照图）计算。
     */
    private static void renderLink(PoseStack poseStack, MultiBufferSource bufferSource,
                                   float halfLength, float halfThickness, float halfWidth,
                                   int packedLight) {
        VertexConsumer consumer = bufferSource.getBuffer(LINK_RENDER_TYPE);
        PoseStack.Pose pose = poseStack.last();

        float x0 = -halfLength;
        float x1 = halfLength;
        float y0 = -halfThickness;
        float y1 = halfThickness;
        float z0 = -halfWidth;
        float z1 = halfWidth;

        // +Y / -Y
        quad(consumer, pose, packedLight, 0, 1, 0,
                x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
        quad(consumer, pose, packedLight, 0, -1, 0,
                x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        // +Z / -Z
        quad(consumer, pose, packedLight, 0, 0, 1,
                x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(consumer, pose, packedLight, 0, 0, -1,
                x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
        // +X / -X
        quad(consumer, pose, packedLight, 1, 0, 0,
                x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
        quad(consumer, pose, packedLight, -1, 0, 0,
                x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose, int packedLight,
                             float nx, float ny, float nz,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3) {
        vertex(consumer, pose, packedLight, nx, ny, nz, x0, y0, z0);
        vertex(consumer, pose, packedLight, nx, ny, nz, x1, y1, z1);
        vertex(consumer, pose, packedLight, nx, ny, nz, x2, y2, z2);
        vertex(consumer, pose, packedLight, nx, ny, nz, x3, y3, z3);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, int packedLight,
                               float nx, float ny, float nz,
                               float x, float y, float z) {
        consumer.addVertex(pose, x, y, z)
                .setColor(LINK_COLOR)
                .setUv(0f, 0f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, nx, ny, nz);
    }

    /**
     * 主动轮相对其车体连接轮的局部 X 角速度。
     *
     * <p>优先用连接器另一端的 SubPart 做差，去掉车体俯仰 / 侧倾角速度；取不到
     * 连接时退回主动轮自身的局部角速度（车体角速度会混进来，但不会静止）。</p>
     */
    private static float relativeAngularVelocityX(TrackLink link, SubPart sprocket) {
        SubPart chassis = findChassis(link, sprocket);
        Vector3f relative = chassis == null
                ? sprocket.getAngularVelocity()
                : sprocket.getAngularVelocity().subtract(chassis.getAngularVelocity());
        Vector3f local = new Vector3f();
        MyQuaternion.rotateInverse(sprocket.getRotation(), relative, local);
        return local.x;
    }

    private static SubPart findChassis(TrackLink link, SubPart sprocket) {
        for (AbstractConnector connector : sprocket.connectors.values()) {
            AbstractConnector attached = connector.attachedConnector;
            if (attached == null || attached.subPart == null) continue;
            SubPart other = attached.subPart;
            if (other == sprocket || link.members().contains(other)) continue;
            return other;
        }
        return null;
    }

    private static double lerp(double a, double b, float t) {
        return a + (b - a) * t;
    }
}
