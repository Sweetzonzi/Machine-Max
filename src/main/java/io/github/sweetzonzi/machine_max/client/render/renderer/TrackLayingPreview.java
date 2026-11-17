package io.github.sweetzonzi.machine_max.client.render.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.sweetzonzi.machine_max.client.track.TrackLayingClient;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackEnvelope2D;
import io.github.sweetzonzi.machine_max.util.geometry.BeltPath2D;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Brightness;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.List;

/**
 * 履带铺设预览：把客户端草稿画成「现在闭合会得到的包络」。
 *
 * <p>几何与真正的履带同源：成员表按铺设顺序（主动轮在首位）交给 {@link TrackEnvelope2D}
 * 求包络，节数由主线程按 {@code TrackLink.refreshState} 的推导口径算好放进快照，逐节位置交给
 * {@link TrackBeltLayout}。因此预览与闭合后的带一致——绕向反了、少轮、多轮都在画面上直接
 * 看得见，而这正是铺设顺序唯一的反馈（顺序同时是包络的锚定绕行顺序）。</p>
 *
 * <p>成员表来自 {@link TrackLayingClient#preview()}：草稿与视线命中的候选轮在主线程解析好，
 * 本类在渲染线程只补位姿与几何，不读草稿、不读载具表。</p>
 *
 * <p>画的是程序化节框，不加载内容包模型：预览要在所有型号上都成立（含模型缺失、
 * {@code link_bones} 对不上），也不读内容包资源。节框用半透明实体几何——与零件放置预览
 * 同一个渲染类型、同一档透明度——颜色取载具放置预览的两档：绿 = 手上的履带够扣、
 * 红 = 不够（{@link #GHOST_COLOR} / {@link #GHOST_WARN_COLOR}），与「材料没成形的实体履带」
 * （中性灰线框）区分开。</p>
 *
 * <p>节数取快照里的 {@link TrackLayingClient.Preview#need()}：那是主线程按 tick 位姿推出的、
 * 闭合会扣的节数，因此画出来的节数就是这一笔的用料。背包里的同型号履带不够扣时整条带换成
 * 警示色（{@link #GHOST_WARN_COLOR}），够不够由主线程冻结在
 * {@link TrackLayingClient.Preview#affordable()} 里。</p>
 */
public final class TrackLayingPreview {
    /**
     * 用料够扣时的节框颜色：与载具放置预览的「可放置」同色（{@code Color.GREEN}），alpha 64。
     *
     * <p>两档颜色与 {@link PartAssemblyRenderer} 的放置预览是同一套语言：绿 = 这样放可以，
     * 红 = 不行；透明度也取同一档。</p>
     */
    private static final int GHOST_COLOR = new Color(0, 255, 0, 64).getRGB();
    /** 用料不够扣时的节框颜色：与载具放置预览的「不可放置」同色（{@code Color.RED}），alpha 64。 */
    private static final int GHOST_WARN_COLOR = new Color(255, 0, 0, 64).getRGB();
    /** 预览节的渲染类型：与零件放置预览相同的半透明实体几何，纯白贴图 + 顶点色着色。 */
    private static final RenderType GHOST_QUADS = RenderType.entityTranslucent(TrackRenderer.LINK_TEXTURE);
    /** 预览不读世界光照：整条带统一满亮，夜里或阴影里也能看出形状。 */
    private static final int GHOST_LIGHT = Brightness.FULL_BRIGHT.pack();

    private TrackLayingPreview() {
    }

    /**
     * 渲染线程：画当帧的铺设预览。
     *
     * @param camPos       相机位置；本方法自行把世界原点平移成相机相对原点
     * @param poseStack    当前姿态栈
     * @param bufferSource 顶点缓冲来源
     * @param partialTick  帧间插值系数，与成员轮位姿同一口径
     */
    public static void render(Vec3 camPos, PoseStack poseStack, MultiBufferSource bufferSource, float partialTick) {
        TrackLayingClient.Preview current = TrackLayingClient.preview();
        if (current == null) return;
        List<SubPart> members = current.members();
        TrackAttr attr = current.type().attr();
        float thickness = attr.thicknessMeters();
        boolean clockwise = TrackEnvelope2D.layingClockwise(
                members.stream().map(member -> member.getWorldPositionMatrix(partialTick)).toList());
        TrackEnvelope2D.Result envelope =
                TrackEnvelope2D.build(members, members.getFirst(), partialTick, thickness, clockwise);
        if (envelope == null) return;
        BeltPath2D.ArcContact contact = envelope.path().wheelContact(0);
        if (contact == null) return;
        TrackBeltLayout layout = new TrackBeltLayout(envelope.path(), contact, current.need(),
                attr.lengthPerLink(), 0);
        if (!layout.isValid()) return;

        // 节局部基与程序化履带同构：+X = 切线 / +Y = 外法线 / +Z = 轮轴
        float wheelAxis = (float) -Math.signum(envelope.path().signedArea());
        float halfThickness = thickness * 0.5f;
        float halfWidth = (float) (TrackRenderer.LINK_WIDTH * 0.5);
        int color = current.affordable() ? GHOST_COLOR : GHOST_WARN_COLOR;
        VertexConsumer quads = bufferSource.getBuffer(GHOST_QUADS);
        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        for (int i = 0; i < layout.count(); i++) {
            BeltPath2D.Sample sample = layout.sample(i);
            float halfLength = (float) (layout.spacing(sample) * TrackRenderer.LINK_LENGTH_RATIO * 0.5);
            poseStack.pushPose();
            poseStack.mulPose(new Matrix4f(envelope.sprocketWorld()).mul(
                    TrackLinkPose.procedural(sample.tangent(), sample.normal(), sample.point(),
                            wheelAxis, halfThickness)));
            TrackRenderer.renderProceduralLink(poseStack, quads, halfLength, halfThickness, halfWidth,
                    GHOST_LIGHT, color);
            poseStack.popPose();
        }
        poseStack.popPose();
    }
}
