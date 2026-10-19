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
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 履带节渲染器：在轮组包络线上按固定节距放置履带节，并按主动轮转速积分相位。
 *
 * <p>几何全部是客户端本地计算：每个渲染帧用 {@code partialTick} 的轮位姿重建
 * {@link TrackEnvelope2D}，节位置 {@code s_i = (L·i/N + phase) mod L}。相位在
 * {@link #tick()}（20tps）里积分，渲染时用上一 tick / 本 tick 双值插值。
 * {@link TrackLink} 本身只在创建 / 解散 / 断带 / 修复 / 成员变化时同步。</p>
 *
 * <p>初版用 {@link RenderType#debugQuads()} 直接画扁长方体，不依赖模型 / 贴图资产；
 * 单节模型接入后只需替换 {@link #renderLink} 的绘制部分。节数 N 在 tick 中按当前
 * 包络推导，运行中固定，避免悬挂行程导致节分布重排。</p>
 *
 * <p>线程：{@link #tick()} 在主线程，{@link #render} 在渲染线程；共享状态只有
 * {@link State} 的 volatile 相位 / 节数。</p>
 */
public class TrackRenderer extends VisualEffectRenderer {
    /** 单节厚度（米），也是节中心相对包络线的外移量。这是纯渲染尺寸。 */
    private static final float LINK_THICKNESS = 0.05f;
    /** 单节宽度（米）的占位值；接入单节模型后以模型横向尺寸为准。 */
    private static final float LINK_WIDTH = 0.35f;
    /** 节长占节距的比例，剩余部分作为节间小间隙。 */
    private static final double LINK_LENGTH_RATIO = 0.92;
    /** 超过该距离不画履带，避免和远处载具的位姿精度较劲。 */
    private static final double MAX_RENDER_DISTANCE_SQR = 512.0 * 512.0;
    private static final double TICK_SECONDS = 1.0 / 20.0;

    private final Map<TrackLink, State> states = new ConcurrentHashMap<>();

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
                    states.remove(link);
                    continue;
                }
                State state = states.computeIfAbsent(link, ignored -> new State());
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

        states.entrySet().removeIf(entry -> {
            TrackLink link = entry.getKey();
            return link.isDisposed() || !link.vehicle().trackLinks.contains(link);
        });
    }

    @Override
    public void physTick(@NotNull PhysicsLevel physicsLevel) {
    }

    @Override
    public @NotNull RenderLevelStageEvent.Stage getRenderStage() {
        return RenderLevelStageEvent.Stage.AFTER_ENTITIES;
    }

    @Override
    public void render(@NotNull RenderLevelStageEvent event, @NotNull MultiBufferSource bufferSource,
                       float partialTick) {
        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();

        for (VehicleCore vehicle : ObjectManager.clientAllVehicles.values()) {
            for (TrackLink link : vehicle.trackLinks) {
                if (link.isDisposed()) continue;
                TrackEnvelope2D.Result envelope =
                        link.envelope(partialTick);
                if (envelope == null) continue;

                SubPart sprocket = link.sprocket();
                if (sprocket == null) continue;
                org.joml.Vector3f sprocketPos = sprocket.getWorldPositionMatrix(partialTick)
                        .getTranslation(new org.joml.Vector3f());
                double dx = sprocketPos.x - camPos.x;
                double dy = sprocketPos.y - camPos.y;
                double dz = sprocketPos.z - camPos.z;
                if (dx * dx + dy * dy + dz * dz > MAX_RENDER_DISTANCE_SQR) continue;

                double length = envelope.path().length();
                if (!(length > 1e-6)) continue;
                State state = states.get(link);
                int linkCount = state == null || state.linkCount <= 0
                        ? envelope.path().linkCount(link.attr().linkLength())
                        : state.linkCount;
                if (linkCount <= 0) continue;

                double phase = state == null
                        ? 0
                        : lerp(state.prevPhase, state.phase, partialTick);
                renderBelt(poseStack, bufferSource, camPos, envelope, linkCount, phase, LINK_WIDTH);
            }
        }
    }

    private static void renderBelt(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 camPos,
                                   TrackEnvelope2D.Result envelope, int linkCount, double phase,
                                   float width) {
        double length = envelope.path().length();
        float wheelAxis = (float) -Math.signum(envelope.path().signedArea());
        double spacing = length / linkCount;
        float halfLength = (float) (spacing * LINK_LENGTH_RATIO * 0.5);
        float halfThickness = LINK_THICKNESS * 0.5f;
        float halfWidth = width * 0.5f;

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

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
            renderLink(poseStack, bufferSource, halfLength, halfThickness, halfWidth);
            poseStack.popPose();
        }

        poseStack.popPose();
    }

    /**
     * 画一节扁长方体。初版无模型资产，用无贴图的彩色四边形；颜色只做面间明暗，
     * 保证在游戏里能读出节距和运动方向。
     */
    private static void renderLink(PoseStack poseStack, MultiBufferSource bufferSource,
                                   float halfLength, float halfThickness, float halfWidth) {
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.debugQuads());
        PoseStack.Pose pose = poseStack.last();

        float x0 = -halfLength;
        float x1 = halfLength;
        float y0 = -halfThickness;
        float y1 = halfThickness;
        float z0 = -halfWidth;
        float z1 = halfWidth;

        int top = 0xFF9A968C;
        int side = 0xFF726E66;
        int bottom = 0xFF46433E;

        // +Y / -Y
        quad(consumer, pose, top, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
        quad(consumer, pose, bottom, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        // +Z / -Z
        quad(consumer, pose, side, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(consumer, pose, side, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0);
        // +X / -X
        quad(consumer, pose, side, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0);
        quad(consumer, pose, side, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose, int color,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3) {
        int a = (color >>> 24) & 0xFF;
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8) & 0xFF;
        int b = color & 0xFF;
        consumer.addVertex(pose, x0, y0, z0).setColor(r, g, b, a);
        consumer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a);
        consumer.addVertex(pose, x2, y2, z2).setColor(r, g, b, a);
        consumer.addVertex(pose, x3, y3, z3).setColor(r, g, b, a);
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
