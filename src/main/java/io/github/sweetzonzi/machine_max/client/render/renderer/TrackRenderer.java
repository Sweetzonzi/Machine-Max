package io.github.sweetzonzi.machine_max.client.render.renderer;

import cn.solarmoon.spark_core.animation.model.ModelIndex;
import cn.solarmoon.spark_core.animation.model.ModelInstance;
import cn.solarmoon.spark_core.animation.model.ModelPose;
import cn.solarmoon.spark_core.animation.model.origin.OBone;
import cn.solarmoon.spark_core.animation.model.origin.OModel;
import cn.solarmoon.spark_core.animation.renderer.ModelRenderHelperKt;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.visual_effect.VisualEffectRenderer;
import com.jme3.math.Vector3f;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.fitting.FittingVisual;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackEnvelope2D;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackLink;
import io.github.sweetzonzi.machine_max.util.geometry.BeltPath2D;
import io.github.sweetzonzi.machine_max.util.geometry.Circle2D;
import io.github.sweetzonzi.machine_max.util.geometry.Vec2;
import jme3utilities.math.MyQuaternion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 履带节渲染：在轮组包络线上按固定节距放置履带节，并按主动轮转速积分相位。
 *
 * <p>几何全部是客户端本地计算：每个渲染帧用 {@code partialTick} 的轮位姿重建
 * {@link TrackEnvelope2D}，节位置 {@code s_i = (L·i/N + phase) mod L}。相位在
 * {@link #tick()}（20tps）里积分，渲染时用上一 tick / 本 tick 双值插值。
 * {@link TrackLink} 本身只在创建 / 解散 / 断带 / 修复 / 成员变化时同步。</p>
 *
 * <p><b>两条绘制路径</b>：</p>
 * <ul>
 *   <li>{@link TrackRenderMode#MODEL}：近处实体渲染。型号的 {@code visual.model} 解析成
 *       {@link OModel} 后逐节绘制同一份单节模型；模型缺失时回退程序化。</li>
 *   <li>{@link TrackRenderMode#PROCEDURAL}：远处渲染。始终画程序化扁长方体，不加载内容包模型。</li>
 * </ul>
 *
 * <p><b>挂载方式</b>：本类作为 {@link VisualEffectRenderer} 只承担每 tick 的相位积分，
 * {@link #getRenderStage()} 不返回阶段，因此不参与 {@code RenderLevelStageEvent} 调度。
 * 实际绘制入口是 {@link #renderTrack}，由 {@link PartEntityRenderer}（以及
 * {@link DistantVehicleRenderer}）在主动轮所属实体的 {@code render} 中调用，使履带与零件
 * 模型共用实体渲染管线：</p>
 * <ul>
 *   <li>渲染类型使用带 lightmap / overlay / normal 的实体类型，节的颜色随世界光照；</li>
 *   <li>Iris 的阴影 pass 由 {@code ShadowRenderer} 直接调用 {@code EntityRenderDispatcher}
 *       与各实体的渲染器，不触发 {@code RenderLevelStageEvent}；只有实体管线里的几何会被
 *       写进阴影贴图。</li>
 * </ul>
 *
 * <p>线程：{@link #tick()} 在主线程，{@link #renderTrack} 在渲染线程；共享状态只有
 * {@link State} 的 volatile 相位 / 节数，以及按型号缓存的模型与渲染类型。</p>
 */
public class TrackRenderer extends VisualEffectRenderer {
    /** 绘制路径：近处用型号的单节模型，远处用程序化扁长方体。 */
    public enum TrackRenderMode {
        MODEL,
        PROCEDURAL
    }

    /** 程序化回退的单节宽度（米）；模型路径以模型横向尺寸为准。 */
    private static final float LINK_WIDTH = 0.35f;
    /** 节长占节距的比例，剩余部分作为节间小间隙。 */
    private static final double LINK_LENGTH_RATIO = 0.92;
    private static final double TICK_SECONDS = 1.0 / 20.0;
    /** 程序化回退的贴图：原版 4×4 纯白纹理。 */
    private static final ResourceLocation LINK_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    /**
     * 程序化回退的实体不透明渲染类型：写入深度、正面剔除，带 lightmap / overlay / normal
     * 顶点属性。与零件模型使用同一套实体光照，同时保证阴影 pass 里可作为实体几何绘制。
     */
    private static final RenderType LINK_RENDER_TYPE = RenderType.entitySolid(LINK_TEXTURE);
    /** 程序化回退的单节统一基色。 */
    private static final int LINK_COLOR = 0xFF9A968C;
    /** 模型路径的顶点色：不额外着色，明暗交给实体管线的两向光照。 */
    private static final int MODEL_COLOR = 0xFFFFFFFF;

    /** 相位状态按 TrackLink 索引；tick 写入，渲染读取。 */
    private static final Map<TrackLink, State> STATES = new ConcurrentHashMap<>();
    /** 单节模型的渲染资源按型号 id 缓存，避免每帧新建 {@link ModelPose} 与渲染类型。 */
    private static final Map<ResourceLocation, LinkVisual> VISUALS = new ConcurrentHashMap<>();
    /** 已经报过「模型缺失」的型号，避免每帧刷日志。 */
    private static final Set<ResourceLocation> MISSING_MODELS = ConcurrentHashMap.newKeySet();

    private static final class State {
        private volatile int linkCount;
        private volatile double phase;
        private volatile double prevPhase;
    }

    /**
     * 一个型号的单节渲染资源。
     *
     * @param renderType 该型号贴图的实体渲染类型
     * @param pose       静态姿态（单节模型没有动画）
     * @param bones      模型骨骼，按模型定义顺序
     */
    private record LinkVisual(RenderType renderType, ModelPose pose, List<OBone> bones) {
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
                int linkCount = envelope.path().linkCount(link.attr().lengthPerLink());
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
     * @param mode        绘制路径：近处 {@code MODEL}，远处 {@code PROCEDURAL}
     */
    public static void renderTrack(@NotNull SubPart subPart, @NotNull PoseStack poseStack,
                                   @NotNull MultiBufferSource bufferSource,
                                   float partialTick, int packedLight,
                                   @NotNull TrackRenderMode mode) {
        TrackLink link = subPart.trackLink;
        if (link == null || link.isDisposed() || !link.isSprocket(subPart)) return;

        TrackEnvelope2D.Result envelope = link.envelope(partialTick);
        if (envelope == null) return;

        State state = STATES.get(link);
        int linkCount = state == null || state.linkCount <= 0
                ? envelope.path().linkCount(link.attr().lengthPerLink())
                : state.linkCount;
        if (linkCount <= 0) return;

        double phase = state == null ? 0 : lerp(state.prevPhase, state.phase, partialTick);
        LinkVisual visual = mode == TrackRenderMode.MODEL ? visualFor(link, subPart) : null;
        renderBelt(poseStack, bufferSource, envelope, linkCount, phase, link, visual, packedLight);
    }

    /**
     * 计算实体剔除使用的世界 AABB。
     *
     * <p>非履带主动轮直接返回实体自身的完整刚体盒。履带主动轮额外并入整条履带的包络：
     * 包络在主动轮局部 (Y, Z) 平面给出，按单节几何的局部伸入量与履带厚度外扩，
     * 再用主动轮当前世界位姿把角点变换到世界空间，得到包住整条履带的保守 AABB。
     * 包络由轮组当前位姿求解，因此主动轮变更、成员变化、断带与解散在下次求值时就体现；
     * 这里不保留跨帧的履带范围缓存。</p>
     *
     * @param subPart 实体对应的子零件，可为 null
     * @param ownBox  实体自身的完整刚体 AABB
     * @return 剔除用 AABB；非主动轮或包络不可用时原样返回 {@code ownBox}
     */
    public static AABB cullingAabb(@Nullable SubPart subPart, AABB ownBox) {
        if (subPart == null || ownBox == null) return ownBox;
        TrackLink link = subPart.trackLink;
        if (link == null || link.isDisposed() || !link.isSprocket(subPart)) return ownBox;

        TrackEnvelope2D.Result envelope = link.envelope(1f);
        if (envelope == null) return ownBox;

        double spacing = envelope.path().length() / Math.max(1, link.linkCount());
        LocalBounds bounds = localBounds(link, subPart, spacing);
        if (!bounds.isFinite()) return ownBox;
        // 履带外表面比包络半径多一个厚度；单节几何在 (Y, Z) 平面内的伸入量按两轴绝对值相加保守估计
        double radialMargin = link.attr().thicknessMeters()
                + Math.max(Math.abs(bounds.minY()), Math.abs(bounds.maxY()))
                + Math.max(Math.abs(bounds.minZ()), Math.abs(bounds.maxZ()));
        double halfAxis = Math.max(Math.abs(bounds.minX()), Math.abs(bounds.maxX()));

        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (Vec2 point : envelope.path().points()) {
            minY = Math.min(minY, point.x());
            maxY = Math.max(maxY, point.x());
            minZ = Math.min(minZ, point.y());
            maxZ = Math.max(maxZ, point.y());
        }
        // 圆弧可能超出顶点连线，按每个输入圆的外接方框保守覆盖
        for (Circle2D circle : envelope.path().inputCircles()) {
            minY = Math.min(minY, circle.x() - circle.radius());
            maxY = Math.max(maxY, circle.x() + circle.radius());
            minZ = Math.min(minZ, circle.y() - circle.radius());
            maxZ = Math.max(maxZ, circle.y() + circle.radius());
        }
        if (!(minY <= maxY) || !(minZ <= maxZ)) return ownBox;
        minY -= radialMargin;
        maxY += radialMargin;
        minZ -= radialMargin;
        maxZ += radialMargin;

        Matrix4f world = envelope.sprocketWorld();
        double minX = Double.POSITIVE_INFINITY;
        double worldMaxX = Double.NEGATIVE_INFINITY;
        double worldMinY = Double.POSITIVE_INFINITY;
        double worldMaxY = Double.NEGATIVE_INFINITY;
        double worldMinZ = Double.POSITIVE_INFINITY;
        double worldMaxZ = Double.NEGATIVE_INFINITY;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    double lx = sx * halfAxis;
                    double ly = sy > 0 ? maxY : minY;
                    double lz = sz > 0 ? maxZ : minZ;
                    double wx = world.m00() * lx + world.m10() * ly + world.m20() * lz + world.m30();
                    double wy = world.m01() * lx + world.m11() * ly + world.m21() * lz + world.m31();
                    double wz = world.m02() * lx + world.m12() * ly + world.m22() * lz + world.m32();
                    minX = Math.min(minX, wx);
                    worldMaxX = Math.max(worldMaxX, wx);
                    worldMinY = Math.min(worldMinY, wy);
                    worldMaxY = Math.max(worldMaxY, wy);
                    worldMinZ = Math.min(worldMinZ, wz);
                    worldMaxZ = Math.max(worldMaxZ, wz);
                }
            }
        }
        return new AABB(
                Math.min(ownBox.minX, minX), Math.min(ownBox.minY, worldMinY), Math.min(ownBox.minZ, worldMinZ),
                Math.max(ownBox.maxX, worldMaxX), Math.max(ownBox.maxY, worldMaxY), Math.max(ownBox.maxZ, worldMaxZ)
        );
    }

    /**
     * 单节几何在履带局部坐标中的静态包围盒：X 为轮轴（宽度），Y 为内法向（厚度），Z 为切线（节长）。
     */
    private record LocalBounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        /** 模型数据异常时整盒不可用，调用方回退到实体自身盒而不是用 NaN 参与视锥判定。 */
        boolean isFinite() {
            return Float.isFinite(minX) && Float.isFinite(minY) && Float.isFinite(minZ)
                    && Float.isFinite(maxX) && Float.isFinite(maxY) && Float.isFinite(maxZ);
        }

        LocalBounds union(LocalBounds other) {
            return new LocalBounds(
                    Math.min(minX, other.minX),
                    Math.min(minY, other.minY),
                    Math.min(minZ, other.minZ),
                    Math.max(maxX, other.maxX),
                    Math.max(maxY, other.maxY),
                    Math.max(maxZ, other.maxZ)
            );
        }
    }

    /**
     * 履带单节的局部包围盒：型号模型存在时取模型骨骼立方体的真实外伸，并与程序化回退几何取并集，
     * 保证两条绘制路径任意一条都不被剔除。
     *
     * @param spacing 当前包络下的实际节距（路径长度 / 节数），用于覆盖节距与型号标称值的偏差
     */
    private static LocalBounds localBounds(TrackLink link, SubPart sprocket, double spacing) {
        LocalBounds bounds = proceduralBounds(link.attr(), spacing);
        LinkVisual visual = visualFor(link, sprocket);
        if (visual == null) return bounds;
        LocalBounds model = modelBounds(visual.pose(), visual.bones());
        return model == null ? bounds : model.union(bounds);
    }

    /**
     * 程序化回退单节的局部包围盒（与 {@link #renderProceduralLink} 的尺寸一致）。
     *
     * @param spacing 当前包络下的实际节距；节长取标称节距与实际节距中的较大者
     */
    private static LocalBounds proceduralBounds(TrackAttr attr, double spacing) {
        float halfWidth = LINK_WIDTH * 0.5f;
        float halfThickness = attr.thicknessMeters() * 0.5f;
        float halfLength = (float) (Math.max(attr.lengthPerLink(), spacing) * LINK_LENGTH_RATIO * 0.5);
        return new LocalBounds(-halfWidth, -halfThickness, -halfLength, halfWidth, halfThickness, halfLength);
    }

    /**
     * 按单节模型的骨骼层级与立方体顶点求模型空间包围盒。
     *
     * @return 模型没有骨骼立方体时返回 {@code null}
     */
    @Nullable
    private static LocalBounds modelBounds(ModelPose pose, List<OBone> bones) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        boolean any = false;
        Matrix4f boneMatrix = new Matrix4f();
        for (OBone bone : bones) {
            boneMatrix.identity();
            bone.applyTransformWithParents(pose, boneMatrix, 1f);
            for (var cube : bone.getCubes()) {
                // getTransformedVertices 会就地向传入矩阵叠加立方体自身的 pivot/rotation，每个立方体用独立副本
                for (var vertex : cube.getTransformedVertices(new Matrix4f(boneMatrix))) {
                    any = true;
                    minX = Math.min(minX, vertex.x);
                    minY = Math.min(minY, vertex.y);
                    minZ = Math.min(minZ, vertex.z);
                    maxX = Math.max(maxX, vertex.x);
                    maxY = Math.max(maxY, vertex.y);
                    maxZ = Math.max(maxZ, vertex.z);
                }
            }
        }
        if (!any) return null;
        return new LocalBounds((float) minX, (float) minY, (float) minZ,
                (float) maxX, (float) maxY, (float) maxZ);
    }

    private static void renderBelt(PoseStack poseStack, MultiBufferSource bufferSource,
                                   TrackEnvelope2D.Result envelope, int linkCount, double phase,
                                   TrackLink link, @Nullable LinkVisual visual, int packedLight) {
        double length = envelope.path().length();
        if (!(length > 1e-6)) return;
        float wheelAxis = (float) -Math.signum(envelope.path().signedArea());
        double spacing = length / linkCount;
        float thickness = link.attr().thicknessMeters();
        float halfLength = (float) (spacing * LINK_LENGTH_RATIO * 0.5);
        float halfThickness = thickness * 0.5f;
        float halfWidth = LINK_WIDTH * 0.5f;

        for (int i = 0; i < linkCount; i++) {
            double arc = (length * i / linkCount + phase) % length;
            if (arc < 0) arc += length;
            BeltPath2D.Sample sample = envelope.path().sampleAt(arc);
            Vec2 point = sample.point();
            Vec2 tangent = sample.tangent();
            Vec2 normal = sample.normal();

            // 节局部基：程序化路径 +X = 切线 / +Y = 外法线 / +Z = 轮轴，模型路径
            // +X = 轮轴 / +Y = 内法线 / +Z = 切线；两条矩阵都是右手系。
            poseStack.pushPose();
            if (visual != null) {
                poseStack.mulPose(new Matrix4f(envelope.sprocketWorld())
                        .mul(TrackLinkPose.model(tangent, normal, point, wheelAxis, thickness)));
                renderModelLink(poseStack, bufferSource, visual, packedLight);
            } else {
                poseStack.mulPose(new Matrix4f(envelope.sprocketWorld())
                        .mul(TrackLinkPose.procedural(tangent, normal, point, wheelAxis, halfThickness)));
                renderProceduralLink(poseStack, bufferSource, halfLength, halfThickness, halfWidth, packedLight);
            }
            poseStack.popPose();
        }
    }

    /**
     * 取型号的单节模型渲染资源；模型缺失时返回 {@code null}，调用方回退程序化。
     */
    @Nullable
    private static LinkVisual visualFor(TrackLink link, SubPart sprocket) {
        FittingVisual visual = link.type().visual();
        ResourceLocation key = link.typeId();
        LinkVisual cached = VISUALS.get(key);
        if (cached != null) return cached;

        ModelIndex index = new ModelIndex("fitting", visual.model());
        OModel model = OModel.getOrEmpty(index);
        if (model.getBones().isEmpty()) {
            if (MISSING_MODELS.add(key)) {
                MachineMax.LOGGER.warn("履带型号 {} 的模型 {} 不存在或为空，改用程序化履带", key, visual.model());
            }
            return null;
        }
        ModelInstance instance = new ModelInstance(sprocket.part, index);
        LinkVisual created = new LinkVisual(
                RenderType.entityCutout(visual.texture()),
                instance.getPose(),
                List.copyOf(model.getBones().values())
        );
        VISUALS.put(key, created);
        return created;
    }

    /** 逐骨骼绘制一份单节模型；明暗交给实体管线的两向光照。 */
    private static void renderModelLink(PoseStack poseStack, MultiBufferSource bufferSource,
                                        LinkVisual visual, int packedLight) {
        VertexConsumer consumer = bufferSource.getBuffer(visual.renderType());
        for (OBone bone : visual.bones()) {
            ModelRenderHelperKt.render(bone, visual.pose(), poseStack, consumer,
                    packedLight, OverlayTexture.NO_OVERLAY, MODEL_COLOR, 1f, false);
        }
    }

    /**
     * 画一节扁长方体。每个面按外法线顺序给出逆时针顶点，正面剔除下从外面可见；
     * 明暗由 {@link RenderType#entitySolid} 的实体着色器（两向光照 + 光照图）计算。
     */
    private static void renderProceduralLink(PoseStack poseStack, MultiBufferSource bufferSource,
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
