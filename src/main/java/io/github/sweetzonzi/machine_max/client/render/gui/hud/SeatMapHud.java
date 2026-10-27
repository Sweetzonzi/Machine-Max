package io.github.sweetzonzi.machine_max.client.render.gui.hud;

import com.jme3.math.Transform;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sweetzonzi.machine_max.client.MMClientConfig;
import io.github.sweetzonzi.machine_max.client.input.CameraController;
import io.github.sweetzonzi.machine_max.client.input.KeyBinding;
import io.github.sweetzonzi.machine_max.client.render.MMRenderTypes;
import io.github.sweetzonzi.machine_max.client.render.gui.seat.SeatMapLayout;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.BasicSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.seat.SeatOrder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.seat.SeatSwitchFeedback;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.HitBoxAttr;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import cn.solarmoon.spark_core.animation.model.ModelInstance;
import cn.solarmoon.spark_core.animation.model.ModelPose;
import cn.solarmoon.spark_core.animation.model.origin.OBone;
import cn.solarmoon.spark_core.animation.renderer.ModelRenderHelperKt;
import cn.solarmoon.spark_core.util.SparkMathKt;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Brightness;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;

/**
 * 常驻座位图：俯视角的座位布局、占用、自己在哪、下一个是谁。
 *
 * <p>座位图的元素数量、位置、颜色都来自运行时装配结果，且需要逐帧刷新占用，所以是独立图层而不是
 * {@code HudLayout} 里的静态元素。显示条件全部满足才画：玩家在座位控制下、没有打开界面、没隐藏
 * GUI、不在炮镜/摄像机模式、座位数大于 1、配置没关。</p>
 *
 * <p>车体按 <strong>GUI 正交空间</strong>渲染：{@code LayeredDraw.Layer} 的 pose 单位就是 GUI 像素，
 * {@code GuiGraphics} 自带 bufferSource，所以模型可以直接画进这一层，不需要世界空间、相机或投影矩阵。
 * 变换顺序固定为「先俯视 {@code X(-90)}、再绕世界 Y 偏航 {@code 180-heading}」——反过来写会把世界高度
 * 混进横轴。参考系的原点与偏航逐帧从参考子零件的插值世界矩阵取，快照只提供参考子零件与局部前向轴。</p>
 */
public class SeatMapHud implements LayeredDraw.Layer {

    private static final int BG = 0xE610151C;
    private static final int BORDER = 0x40FFFFFF;
    private static final int ACCENT = 0xFF35C8E8;
    private static final int TEXT = 0xFFE8EEF4;
    private static final int TEXT_DIM = 0xFF8A96A3;

    private static final int CURRENT = 0xFF4FD1FF;
    private static final int NEXT = 0xFF6CFF6C;
    private static final int EMPTY = 0xFFF2F6FA;
    private static final int OCCUPIED = 0xFFFFB020;
    private static final int UNAVAILABLE = 0xFF808080;

    /** 标记半边长（GUI 像素）：一位数编号要 10px，两位数 12px */
    private static final int MARKER_RADIUS = 5;
    private static final int MARKER_RADIUS_WIDE = 6;
    /** 面板与屏幕底边的距离：热栏高 22px，留 2px 间隙 */
    private static final int BOTTOM_MARGIN = 24;

    /**
     * 车体剪影的基色：半透明纯色几何。外壳画得越淡，内部染色越读得出来。
     */
    private static final int MODEL_COLOR = 0x26AEB9C4;
    /** 受损零件的颜色，比外壳实一些，但仍要能看穿 */
    private static final int MODEL_COLOR_DAMAGED_ALPHA = 0x5A;
    /** 内构子系统染色：接近不透明，透过多层外壳仍然清晰 */
    private static final int HITBOX_ALPHA = 0xE6;

    @Override
    public void render(@NotNull GuiGraphics graphics, @NotNull DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) return;
        if (minecraft.screen != null || minecraft.options.hideGui) return;
        if (CameraController.isCameraMode()) return;
        if (!MMClientConfig.isSeatMapVisible()) return;
        if (!(((IEntityMixin) player).machine_Max$getControllingSubsystem() instanceof SeatSubsystem current)) return;
        if (!(current.getSubPart().getPart().assembly instanceof VehicleCore)) return;

        SeatMapLayout.Snapshot snapshot = SeatMapLayout.snapshot(current);
        if (snapshot == null || snapshot.seats().size() < 2) return;

        float partialTick = delta.getGameTimeDeltaPartialTick(false);
        int left = 6;
        int top = Math.max(4, graphics.guiHeight() - SeatMapLayout.PANEL_H - BOTTOM_MARGIN);
        int mapX = left + SeatMapLayout.MAP_PAD;
        int mapY = top + SeatMapLayout.HEADER_H + SeatMapLayout.MAP_PAD;
        int mapW = SeatMapLayout.mapWidth();
        int mapH = SeatMapLayout.mapHeight();
        int mapCenterX = mapX + mapW / 2;
        int mapCenterY = mapY + mapH / 2;

        drawPanel(graphics, left, top, snapshot, current);

        // 地图区域背景，让车体有个底
        graphics.fill(mapX, mapY, mapX + mapW, mapY + mapH, 0x40000000);
        graphics.drawString(minecraft.font, "▲", mapCenterX - 3, mapY + 1, TEXT_DIM, false);

        Matrix4f mapMatrix = buildMapMatrix(snapshot, partialTick, mapCenterX, mapCenterY);
        drawModel(graphics, snapshot, partialTick, mapMatrix);

        // 透视靠半透明叠加，顺序即层次：模型必须先提交，标记再画在上面
        graphics.flush();
        drawMarkers(graphics, minecraft.font, snapshot, current, partialTick, mapMatrix,
                mapX, mapY, mapW, mapH);

        drawFooter(graphics, minecraft, left, top, snapshot, current);
    }

    // ===== 面板 =====

    private void drawPanel(GuiGraphics graphics, int left, int top,
                           SeatMapLayout.Snapshot snapshot, SeatSubsystem current) {
        graphics.fill(left, top, left + SeatMapLayout.PANEL_W, top + SeatMapLayout.PANEL_H, BG);
        graphics.fill(left, top, left + SeatMapLayout.PANEL_W, top + 1, BORDER);
        graphics.fill(left, top + SeatMapLayout.PANEL_H - 1, left + SeatMapLayout.PANEL_W, top + SeatMapLayout.PANEL_H, BORDER);
        graphics.fill(left, top, left + 1, top + SeatMapLayout.PANEL_H, BORDER);
        graphics.fill(left + SeatMapLayout.PANEL_W - 1, top, left + SeatMapLayout.PANEL_W, top + SeatMapLayout.PANEL_H, BORDER);
        graphics.fill(left + 1, top + 1, left + SeatMapLayout.PANEL_W - 1, top + 3, ACCENT);

        var font = Minecraft.getInstance().font;
        int index = snapshot.indexOf(current);
        String counter = (index + 1) + "/" + snapshot.seats().size();
        int counterWidth = font.width(counter);
        // 标题写当前座位的名字：直接翻译子系统名，不做任何从变体名/locator 反推的解析
        int nameRoom = SeatMapLayout.PANEL_W - 12 - counterWidth - 4;
        graphics.drawString(font, clip(font, seatName(current).getString(), nameRoom),
                left + 6, top + 5, TEXT, false);
        graphics.drawString(font, counter, left + SeatMapLayout.PANEL_W - 6 - counterWidth,
                top + 5, TEXT_DIM, false);
    }

    // ===== 投影矩阵 =====

    /**
     * 构造“世界坐标 → GUI 像素”的矩阵，车体与标记共用。
     *
     * <p>参考系逐帧采样：车辆一平移、一转向，原点和偏航都变。变换链本身在
     * {@link SeatMapLayout#mapMatrix} 里，便于用纯数学用例守住“车头朝上”的判据。</p>
     */
    private Matrix4f buildMapMatrix(SeatMapLayout.Snapshot snapshot, float partialTick,
                                    int mapCenterX, int mapCenterY) {
        Matrix4f refMatrix = snapshot.reference().ref().getWorldPositionMatrix(partialTick);
        Vector3f origin = refMatrix.getTranslation(new Vector3f());
        Vector3f front = refMatrix.transformDirection(new Vector3f(snapshot.reference().frontLocal()));
        front.y = 0f;
        if (front.lengthSquared() < 1e-6f) front.set(0f, 0f, -1f);
        float headingDeg = (float) Math.toDegrees(Math.atan2(front.x, front.z));
        return SeatMapLayout.mapMatrix(snapshot.fit(), snapshot.centerSx(), snapshot.centerSy(),
                mapCenterX, mapCenterY, origin, headingDeg);
    }

    // ===== 车体 =====

    /**
     * 在面板里画两层：半透明外壳 + 内构子系统染色。
     *
     * <p>外壳用 {@link MMRenderTypes#solidXray()}——不测也不写深度，所有面都参与混合，叠起来就是
     * “看得见里面”的透视感；内构层随后用同一批顶点再画一遍，颜色来自
     * {@link SubsystemDurabilityColors}，接近不透明，因此能透过外壳读出来。之所以不用纹理，
     * 是因为地图只需要足迹与内构位置，贴图与光照在这里都是噪声。</p>
     *
     * <p>不带 hitbox 的零件只出现在外壳层；带 hitbox 但子系统已隐藏（`is_hidden`）的骨骼也不染色，
     * 与内构 HUD 的可见性规则保持一致。</p>
     */
    private void drawModel(GuiGraphics graphics, SeatMapLayout.Snapshot snapshot, float partialTick,
                           Matrix4f mapMatrix) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.mulPose(mapMatrix);
        try {
            var buffer = graphics.bufferSource().getBuffer(MMRenderTypes.solidXray());
            // 第一遍：所有子零件的外壳
            for (SubPart sub : snapshot.subParts()) {
                var model = modelOf(sub);
                if (model == null) continue;
                pose.pushPose();
                pose.mulPose(sub.getRenderWorldPositionMatrix(partialTick));
                for (OBone bone : sub.getBones().values()) {
                    ModelRenderHelperKt.render(bone, model.getPose(), pose, buffer,
                            Brightness.FULL_BRIGHT.pack(), OverlayTexture.NO_OVERLAY,
                            modelColor(sub), partialTick, false);
                }
                pose.popPose();
            }
            // 第二遍：内构骨骼。放在全部外壳之后，任何零件的子系统颜色都不会被后面零件的外壳压暗
            if (MMClientConfig.isSeatMapDamageLayerEnabled()) {
                for (SubPart sub : snapshot.subParts()) {
                    var model = modelOf(sub);
                    if (model == null) continue;
                    pose.pushPose();
                    pose.mulPose(sub.getRenderWorldPositionMatrix(partialTick));
                    drawSubsystemBones(sub, sub.getBones(), model.getPose(), pose, buffer, partialTick);
                    pose.popPose();
                }
            }
        } finally {
            pose.popPose();
        }
    }

    /** 资源重载窗口：模型为空时 {@code getBones} 会 NPE */
    private static ModelInstance modelOf(SubPart sub) {
        return sub.getPart().getModelController().getModel();
    }

    /** 内构层：带 hitbox 的骨骼按所属子系统的耐久染色，透过外壳可见 */
    private void drawSubsystemBones(SubPart sub, Map<String, OBone> bones, ModelPose modelPose,
                                    PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer buffer,
                                    float partialTick) {
        for (Map.Entry<String, HitBoxAttr> entry : sub.getAttr().getHitBoxes().entrySet()) {
            String subsystemName = entry.getValue().subsystem;
            if (subsystemName == null || subsystemName.isEmpty()) continue;
            OBone bone = bones.get(entry.getKey());
            if (bone == null) continue;
            if (!(sub.subsystems.get(subsystemName) instanceof BasicSubsystem subsystem)) continue;
            if (subsystem.isHidden()) continue;
            ModelRenderHelperKt.render(bone, modelPose, pose, buffer,
                    Brightness.FULL_BRIGHT.pack(), OverlayTexture.NO_OVERLAY,
                    SubsystemDurabilityColors.withAlpha(
                            SubsystemDurabilityColors.colorFor(subsystem.getDurability(), subsystem.getMaxDurability()),
                            HITBOX_ALPHA),
                    partialTick, true);
        }
    }

    /** 零件状态层：健康零件用剪影基色，低于 50% 或已毁坏时按色带偏红 */
    private int modelColor(SubPart sub) {
        if (!MMClientConfig.isSeatMapDamageLayerEnabled()) return MODEL_COLOR;
        float max = sub.getMaxDurability();
        if (max <= 0f) return MODEL_COLOR;
        float ratio = Math.max(0f, Math.min(1f, sub.getDurability() / max));
        if (ratio >= 0.5f && !sub.isDestroyed()) return MODEL_COLOR;
        // 黄 → 橙 → 暗红 → 毁坏近黑
        int rgb = ratio <= 0f ? 0x1A0000
                : ratio < 0.25f ? tint(0x8B0000, 0xC03000, ratio / 0.25f)
                : tint(0xC03000, 0xFFB020, (ratio - 0.25f) / 0.25f);
        return (MODEL_COLOR_DAMAGED_ALPHA << 24) | rgb;
    }

    private static int tint(int from, int to, float t) {
        int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (r << 16) | (g << 8) | b;
    }

    // ===== 标记 =====

    private void drawMarkers(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                             SeatMapLayout.Snapshot snapshot, SeatSubsystem current, float partialTick,
                             Matrix4f mapMatrix, int mapX, int mapY, int mapW, int mapH) {
        SeatSubsystem next = nextSeat(snapshot.seats(), current);
        boolean pending = SeatSwitchFeedback.isPending();
        boolean pulse = pending || SeatSwitchFeedback.pulseTicks() > 0;

        for (int index = 0; index < snapshot.seats().size(); index++) {
            SeatSubsystem seat = snapshot.seats().get(index);
            if (seat.getSubPart() == null) continue;
            Transform locator = seat.getSubPart()
                    .getLerpedLocatorWorldTransform(seat.getAttr().getLocator(), partialTick);
            // JME Transform → JOML 的副本：本步就地变换，绝不能改写 locator 内部的那个向量
            Vector3f point = mapMatrix.transformPosition(SparkMathKt.toVector3f(locator.getTranslation()));
            int x = Math.round(point.x);
            int y = Math.round(point.y);
            if (x < mapX - 8 || x > mapX + mapW + 8 || y < mapY - 8 || y > mapY + mapH + 8) continue;

            boolean isCurrent = seat == current;
            boolean isNext = seat == next;
            boolean occupied = seat.isOccupied();
            boolean available = SeatOrder.available(seat);
            int color = isCurrent ? CURRENT : isNext ? NEXT : occupied ? OCCUPIED : available ? EMPTY : UNAVAILABLE;

            // 两位数的编号要多一格宽度，否则字会压到标记外
            int radius = index >= 9 ? MARKER_RADIUS_WIDE : MARKER_RADIUS;
            if (isNext && pulse) radius += 1;
            if (SeatSwitchFeedback.failureAt(seat.getSubPart().getId(), seat.getName())) color = 0xFFFF4040;

            graphics.fill(x - radius, y - radius, x + radius, y + radius, color);
            if (isCurrent || isNext) {
                // 外环：让“你在哪”和“下一个是谁”在一堆方块里仍然显眼
                graphics.fill(x - radius - 1, y - radius - 1, x + radius + 1, y - radius, color);
                graphics.fill(x - radius - 1, y + radius, x + radius + 1, y + radius + 1, color);
                graphics.fill(x - radius - 1, y - radius, x - radius, y + radius, color);
                graphics.fill(x + radius, y - radius, x + radius + 1, y + radius, color);
            }
            // 编号画在标记中央：标记底色都偏亮，用深色字保证可读
            String number = Integer.toString(index + 1);
            graphics.drawString(font, number, x - font.width(number) / 2, y - 4, 0xFF0B1016, false);
        }
    }

    /** 用快照里已排好的顺序找下一个可用座位，不重新排序 */
    private static SeatSubsystem nextSeat(List<SeatSubsystem> seats, SeatSubsystem current) {
        int index = seats.indexOf(current);
        if (index < 0 || seats.size() < 2) return null;
        for (int step = 1; step < seats.size(); step++) {
            SeatSubsystem candidate = seats.get((index + step) % seats.size());
            if (candidate != current && SeatOrder.available(candidate)) return candidate;
        }
        return null;
    }

    // ===== 页脚 =====

    private void drawFooter(GuiGraphics graphics, Minecraft minecraft, int left, int top,
                            SeatMapLayout.Snapshot snapshot, SeatSubsystem current) {
        var font = minecraft.font;
        int y = top + SeatMapLayout.PANEL_H - SeatMapLayout.FOOTER_H + 3;
        int x = left + 6;

        // 图例按标记配色排：你（青）、下一个（绿）、空（白）、占用（琥珀）
        x = drawLegend(graphics, font, x, y, CURRENT, Component.translatable("seat_map.legend.you").getString());
        x = drawLegend(graphics, font, x + 4, y, NEXT, Component.translatable("seat_map.legend.next").getString());
        x = drawLegend(graphics, font, x + 4, y, EMPTY, Component.translatable("seat_map.legend.empty").getString());
        drawLegend(graphics, font, x + 4, y, OCCUPIED, Component.translatable("seat_map.legend.busy").getString());

        int hintY = y + 11;
        SeatSubsystem next = nextSeat(snapshot.seats(), current);
        String key = KeyBinding.generalCycleSeatKey.getTranslatedKeyMessage().getString();
        // 键位提示右侧写下一个座位的名字，直接翻译子系统名
        String hint = next == null
                ? key + " · " + SeatSwitchFeedback.fullMessage().getString()
                : key + " · " + seatName(next).getString();
        graphics.drawString(font, clip(font, hint, SeatMapLayout.PANEL_W - 12), left + 6, hintY,
                next == null ? OCCUPIED : TEXT_DIM, false);
    }

    /** 座位名：子系统名本身就是一个翻译键，缺键时按原样显示 */
    private static Component seatName(SeatSubsystem seat) {
        return Component.translatable(seat.getName());
    }

    /** 按像素宽度截断，超出时补省略号 */
    private static String clip(Font font, String text, int width) {
        if (font.width(text) <= width) return text;
        String ellipsis = "…";
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width(ellipsis))) + ellipsis;
    }

    private int drawLegend(GuiGraphics graphics, Font font, int x, int y,
                           int color, String text) {
        graphics.fill(x, y, x + 4, y + 4, color);
        graphics.drawString(font, text, x + 6, y - 2, TEXT_DIM, false);
        return x + 6 + font.width(text);
    }
}
