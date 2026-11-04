package io.github.sweetzonzi.machine_max.client.render.renderer;

import cn.solarmoon.spark_core.animation.model.ModelIndex;
import cn.solarmoon.spark_core.animation.model.ModelInstance;
import cn.solarmoon.spark_core.animation.model.ModelPose;
import cn.solarmoon.spark_core.animation.model.origin.OBone;
import cn.solarmoon.spark_core.animation.model.origin.OModel;
import cn.solarmoon.spark_core.animation.renderer.ModelRenderHelperKt;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.visual_effect.VisualEffectRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.fitting.FittingVisual;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackEnvelope2D;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackLink;
import io.github.sweetzonzi.machine_max.util.geometry.BeltPath2D;
import io.github.sweetzonzi.machine_max.util.geometry.Vec2;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 履带节渲染：主动轮包裹段锁定角节距，其余路径吸收包络长度变化。
 *
 * <p>几何全部是客户端本地计算：每个渲染帧用 {@code partialTick} 的轮位姿重建
 * {@link TrackEnvelope2D}；{@link TrackBeltLayout} 按连续节序映射位置。</p>
 *
 * <p><b>节数与节距</b>：N 取 {@link TrackLink#linkCount()}（服务端推导后持久化的权威值），
 * 节距取型号的 {@link TrackAttr#lengthPerLink()}。渲染状态只保存相位；两者都来自载具 / 型号
 * 数据，不随本端某一帧的包络变化（载具刚放出时成员位姿可能还没就绪，据此推导会把 N 锁在退化包络上）。
 * {@link #tick()}（20tps）按实际轮位姿展开角度，{@link TrackBeltPhase} 在渲染时对齐当帧位姿。
 * {@link TrackLink} 本身只在创建 / 解散 / 断带 / 修复 / 成员变化时同步。</p>
 *
 * <p><b>两条绘制路径</b>：</p>
 * <ul>
 *   <li>{@link TrackRenderMode#MODEL}：近处实体渲染。型号的 {@code visual.model} 解析成
 *       {@link OModel}，按型号的 {@code link_bones} 切成槽位后逐节取槽 {@code i % K} 绘制；
 *       {@code link_bones} 为空时整模型是一个槽位。模型或骨骼名不可用时回退程序化。</li>
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
 * {@link State} 的 volatile 相位快照 / 节数 / 节距，以及按型号缓存的模型与渲染类型。</p>
 */
public class TrackRenderer extends VisualEffectRenderer {
    /** 绘制路径：近处用型号模型（按槽位取节），远处用程序化扁长方体。 */
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

    /** 按安装拓扑保留相位；全量同步重建 TrackLink 时，断带和修复承接相同状态。 */
    private static final Map<TrackKey, State> STATES = new ConcurrentHashMap<>();
    /** 型号模型资源按型号 id 缓存，避免每帧新建 {@link ModelPose}、切槽与渲染类型。 */
    private static final Map<ResourceLocation, LinkVisual> VISUALS = new ConcurrentHashMap<>();
    /** 已经报过「模型不可用」的型号，避免每帧刷日志；持有时该型号一律走程序化路径。 */
    private static final Set<ResourceLocation> UNUSABLE_MODELS = ConcurrentHashMap.newKeySet();

    private record TrackKey(VehicleCore vehicle, SubPart sprocket, List<SubPart> members, ResourceLocation type) {
        TrackKey(TrackLink link) {
            this(link.vehicle(), link.sprocket(), link.members(), link.typeId());
        }
    }

    private static final class State {
        /** 最近一次成功的角度快照；渲染线程只读。 */
        private volatile TrackBeltPhase phase;
        private long sampleTick;
        /** 「主动轮无有效啮合段」已记录过，恢复后清零。 */
        private boolean warned;
        /** 凸包回退已记录过；同一安装拓扑只记一条。 */
        private boolean fallbackLogged;
    }

    /**
     * 一个型号的模型渲染资源。
     *
     * @param renderType 该型号贴图的实体渲染类型
     * @param pose       静态姿态（节模型没有动画）
     * @param slots      槽位表，按节序排列；第 i 节取 {@code slots.get(i % slots.size())}
     */
    private record LinkVisual(RenderType renderType, ModelPose pose, List<LinkSlot> slots) {
    }

    /**
     * 一个槽位：这一节的骨骼，以及它在模型里的参考点。
     *
     * @param bones     该槽位的骨骼，按模型定义顺序
     * @param refX      参考点（该节外表面中点）相对模型原点的位置，绘制前平移 {@code -ref}
     * @param refY      Y 分量
     * @param refZ      Z 分量
     */
    private record LinkSlot(List<OBone> bones, float refX, float refY, float refZ) {
    }

    @Override
    public void tick() {
        if (Minecraft.getInstance().level == null) return;

        for (VehicleCore vehicle : ObjectManager.clientAllVehicles.values()) {
            for (TrackLink link : vehicle.trackLinks) {
                if (link.isDisposed()) {
                    continue;
                }
                State state = STATES.computeIfAbsent(new TrackKey(link), ignored -> new State());

                TrackEnvelope2D.Result envelope =
                        link.envelope(1f);
                if (envelope == null) {
                    continue;
                }
                noteFallback(state, link, envelope);
                var contact = envelope.path().wheelContact(0);
                if (contact == null || !new TrackBeltLayout(envelope.path(), contact,
                        link.linkCount(), link.attr().lengthPerLink(), 0).isValid()) {
                    if (!state.warned) MachineMax.LOGGER.warn("履带主动轮无有效啮合段，暂缓绘制：{}", link.typeId());
                    state.warned = true;
                    continue;
                }
                state.warned = false;
                double wheel = wheelAngle(link, envelope, 1f);
                long now = vehicle.level.getGameTime();
                double elapsed = state.phase == null ? TICK_SECONDS : (now - state.sampleTick) * TICK_SECONDS;
                TrackBeltPhase next = state.phase == null ? new TrackBeltPhase() : new TrackBeltPhase(state.phase);
                next.advance(wheel, contact.startAngle() + wheel,
                        link.sprocketOmegaX() * elapsed, link.isBroken());
                state.sampleTick = now;
                state.phase = next;
            }
        }

        STATES.entrySet().removeIf(entry -> {
            TrackLink link = entry.getKey().sprocket().trackLink;
            return link == null || link.isDisposed() || !entry.getKey().equals(new TrackKey(link));
        });
    }

    @Override
    public void physTick(@NotNull PhysicsLevel physicsLevel) {
    }

    /**
     * 凸包回退每个安装拓扑只记一条：轮组重叠在悬挂压缩下是正常状态，逐帧记录会刷屏，
     * 而悬挂在阈值附近抖动时“进入 / 离开回退”也会反复触发。
     *
     * <p>回退原因由 {@link TrackEnvelope2D.Result#fallbackReason()} 给出。</p>
     */
    private static void noteFallback(State state, TrackLink link, TrackEnvelope2D.Result envelope) {
        if (!envelope.fallback() || state.fallbackLogged) return;
        state.fallbackLogged = true;
        MachineMax.LOGGER.warn("{}；履带 {} 的成员数={}（同一安装拓扑只记录一次）",
                envelope.fallbackReason(), link.typeId(), link.members().size());
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

        State state = STATES.get(new TrackKey(link));
        if (state == null || state.phase == null) return;
        var contact = envelope.path().wheelContact(0);
        if (contact == null) return;
        int linkCount = link.linkCount();
        double pitch = link.attr().lengthPerLink();
        double wheel = wheelAngle(link, envelope, partialTick);
        double label = state.phase.label(wheel, contact.startAngle() + wheel, partialTick,
                envelope.path().inputCircles().getFirst().radius() / pitch, Math.signum(contact.sweep()));
        TrackBeltLayout layout = new TrackBeltLayout(envelope.path(), contact, linkCount, pitch, label);
        if (!layout.isValid()) return;
        LinkVisual visual = mode == TrackRenderMode.MODEL ? visualFor(link, subPart) : null;
        renderBelt(poseStack, bufferSource, envelope, linkCount, layout, link, visual, packedLight);
    }

    private static double wheelAngle(TrackLink link, TrackEnvelope2D.Result envelope, float partialTick) {
        Matrix4f relative = link.sprocketReferencePose(partialTick).invert().mul(envelope.sprocketWorld());
        return Math.atan2(relative.m12(), relative.m11());
    }

    /**
     * 计算实体剔除使用的世界 AABB。
     *
     * <p>非履带主动轮直接返回实体自身的完整刚体盒。履带主动轮额外并入整条履带的范围：
     * 履带节贴着轮面走，因此取全部成员轮刚体 AABB 的并集，再按“履带厚度 + 一个节距”外扩。
     * 这个外扩量覆盖两处外伸——两轮之间不贴轮的直边段（切线段会离开两侧的轮盒），
     * 以及平直节端部探出弧面的部分。轮距远大于型号节距的布局仍可能让履带在屏幕边缘晚一帧出现。</p>
     *
     * <p>剔除盒只用于视锥判定，不参与绘制；判据取保守侧：偏大只是让刚出屏的载具多画一帧，
     * 偏小会让整条履带在包络超出剔除盒时整圈消失。成员轮尚未生成实体、取不到 AABB 时跳过该轮。</p>
     *
     * <p>轮位姿每帧变化，这里不保留跨帧缓存，也不解析型号模型。</p>
     *
     * @param subPart 实体对应的子零件，可为 null
     * @param ownBox  实体自身的完整刚体 AABB
     * @return 剔除用 AABB；非主动轮或取不到任何成员轮时原样返回 {@code ownBox}
     */
    public static AABB cullingAabb(@Nullable SubPart subPart, AABB ownBox) {
        if (subPart == null || ownBox == null) return ownBox;
        TrackLink link = subPart.trackLink;
        if (link == null || link.isDisposed() || !link.isSprocket(subPart)) return ownBox;

        double margin = link.attr().thicknessMeters() + link.attr().lengthPerLink();
        double minX = ownBox.minX;
        double minY = ownBox.minY;
        double minZ = ownBox.minZ;
        double maxX = ownBox.maxX;
        double maxY = ownBox.maxY;
        double maxZ = ownBox.maxZ;
        // 逐轮取盒时直接带外扩量比较，避开每帧为每个成员轮各建一个 inflate 后的 AABB
        for (SubPart member : link.members()) {
            if (member == null || member.entity == null) continue;
            AABB box = member.entity.getBoundingBoxForCulling();
            minX = Math.min(minX, box.minX - margin);
            minY = Math.min(minY, box.minY - margin);
            minZ = Math.min(minZ, box.minZ - margin);
            maxX = Math.max(maxX, box.maxX + margin);
            maxY = Math.max(maxY, box.maxY + margin);
            maxZ = Math.max(maxZ, box.maxZ + margin);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static void renderBelt(PoseStack poseStack, MultiBufferSource bufferSource,
                                   TrackEnvelope2D.Result envelope, int linkCount, TrackBeltLayout layout,
                                   TrackLink link, @Nullable LinkVisual visual, int packedLight) {
        double length = envelope.path().length();
        if (!(length > 1e-6)) return;
        float wheelAxis = (float) -Math.signum(envelope.path().signedArea());
        float thickness = link.attr().thicknessMeters();
        float halfThickness = thickness * 0.5f;
        float halfWidth = LINK_WIDTH * 0.5f;

        for (int i = 0; i < linkCount; i++) {
            BeltPath2D.Sample sample = layout.sample(i);
            float halfLength = (float) (layout.spacing(sample) * LINK_LENGTH_RATIO * 0.5);
            Vec2 point = sample.point();
            Vec2 tangent = sample.tangent();
            Vec2 normal = sample.normal();

            // 节局部基：程序化路径 +X = 切线 / +Y = 外法线 / +Z = 轮轴，模型路径
            // +X = 轮轴 / +Y = 内法线 / +Z = 切线；两条矩阵都是右手系。
            poseStack.pushPose();
            if (visual != null) {
                LinkSlot slot = visual.slots().get(i % visual.slots().size());
                poseStack.mulPose(new Matrix4f(envelope.sprocketWorld())
                        .mul(TrackLinkPose.model(tangent, normal, point, wheelAxis, thickness)));
                // 槽位骨骼的 pivot 不用对齐到采样点，平移量把该节自己的参考点送到采样点
                poseStack.translate(-slot.refX(), -slot.refY(), -slot.refZ());
                renderSlot(poseStack, bufferSource, visual, slot, packedLight);
            } else {
                poseStack.mulPose(new Matrix4f(envelope.sprocketWorld())
                        .mul(TrackLinkPose.procedural(tangent, normal, point, wheelAxis, halfThickness)));
                renderProceduralLink(poseStack, bufferSource, halfLength, halfThickness, halfWidth, packedLight);
            }
            poseStack.popPose();
        }
    }

    /**
     * 取型号的模型渲染资源；模型不可用（缺失、{@code link_bones} 与模型不符）时返回
     * {@code null}，调用方回退程序化。不可用的型号按 id 记住，不再重试。
     */
    @Nullable
    private static LinkVisual visualFor(TrackLink link, SubPart sprocket) {
        FittingVisual visual = link.type().visual();
        ResourceLocation key = link.typeId();
        LinkVisual cached = VISUALS.get(key);
        if (cached != null) return cached;
        if (UNUSABLE_MODELS.contains(key)) return null;

        ModelIndex index = new ModelIndex("fitting", visual.model());
        OModel model = OModel.getOrEmpty(index);
        if (model.getBones().isEmpty()) {
            markUnusable(key, "履带型号 {} 的模型 {} 不存在或为空，改用程序化履带", key, visual.model());
            return null;
        }
        ModelInstance instance = new ModelInstance(sprocket.part, index);
        ModelPose pose = instance.getPose();
        List<LinkSlot> slots = buildSlots(link, model, pose);
        if (slots == null) return null;
        LinkVisual created = new LinkVisual(RenderType.entityCutout(visual.texture()), pose, slots);
        VISUALS.put(key, created);
        return created;
    }

    /**
     * 按型号的 {@code link_bones} 切槽：每个名字一个槽位，参考点取该骨骼 pivot 在模型里的位置
     * （经父链静态变换）。列表为空时整个模型的全部骨骼是一个槽位，参考点取模型原点。
     *
     * @return 型号的 {@code link_bones} 与模型不符时返回 {@code null}（已告警一次）
     */
    @Nullable
    private static List<LinkSlot> buildSlots(TrackLink link, OModel model, ModelPose pose) {
        List<String> names = link.type().linkBones();
        if (names.isEmpty()) {
            // 空列表不读 pivot：整模型的骨骼一起画，参考点就是模型原点
            return List.of(new LinkSlot(List.copyOf(model.getBones().values()), 0f, 0f, 0f));
        }
        List<LinkSlot> slots = new ArrayList<>(names.size());
        for (String name : names) {
            OBone bone = model.getBone(name);
            if (bone == null) {
                markUnusable(link.typeId(), "履带型号 {} 的 link_bones 里 {} 不在模型 {} 的骨骼表中，改用程序化履带",
                        link.typeId(), name, link.type().visual().model());
                return null;
            }
            if (bone.getCubes().isEmpty() && bone.getMesh() == null) {
                markUnusable(link.typeId(), "履带型号 {} 的 link_bones 里 {} 没有几何，改用程序化履带",
                        link.typeId(), name);
                return null;
            }
            Vector3f reference = pose.getBonePose(name).getSpaceBonePivot(Vec3.ZERO, 1f);
            slots.add(new LinkSlot(List.of(bone), reference.x, reference.y, reference.z));
        }
        return List.copyOf(slots);
    }

    /** 型号模型不可用：记一次日志并按 id 记住，后续帧直接回退程序化。 */
    private static void markUnusable(ResourceLocation key, String message, Object... args) {
        if (UNUSABLE_MODELS.add(key)) {
            MachineMax.LOGGER.warn(message, args);
        }
    }

    /**
     * 丢掉按型号缓存的模型外观。
     *
     * <p>服务端下发内容包重载时 Spark-Core 会清空并重建 {@code OModel.ORIGINS} 与型号表，
     * 缓存里的 {@link ModelPose} 与骨骼对象随之失效：继续用会画旧几何、并把旧骨骼挂在新 pivot 上。
     * 由客户端入口在内容包加载完成时调用。</p>
     */
    public static void invalidateModels() {
        VISUALS.clear();
        UNUSABLE_MODELS.clear();
    }

    /** 绘制一个槽位的骨骼；明暗交给实体管线的两向光照。 */
    private static void renderSlot(PoseStack poseStack, MultiBufferSource bufferSource,
                                   LinkVisual visual, LinkSlot slot, int packedLight) {
        VertexConsumer consumer = bufferSource.getBuffer(visual.renderType());
        for (OBone bone : slot.bones()) {
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

}
