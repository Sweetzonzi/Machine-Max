package io.github.sweetzonzi.machine_max.client.render.gui.element;

import cn.solarmoon.spark_core.animation.model.ModelPose;
import cn.solarmoon.spark_core.animation.model.origin.OBone;
import cn.solarmoon.spark_core.animation.renderer.ModelRenderHelperKt;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.parser.Color;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.FontDrawer;
import com.sighs.apricityui.render.Graph;
import com.sighs.apricityui.render.Mask;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.Text;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.client.MMClientConfig;
import io.github.sweetzonzi.machine_max.client.render.MMRenderTypes;
import io.github.sweetzonzi.machine_max.client.render.gui.hud.SubsystemDurabilityColors;
import io.github.sweetzonzi.machine_max.client.render.gui.hud.molang.HudVehicleSnapshot;
import io.github.sweetzonzi.machine_max.client.render.gui.seat.SeatMapLayout;
import io.github.sweetzonzi.machine_max.client.render.renderable.AuiHudAnimatable;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.BasicSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.HitBoxAttr;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Brightness;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class VehicleModelElement extends Element {
    public static final String TAG_NAME = "machinemax-vehicle-model";
    private static final int CURRENT = 0xFF4FD1FF;
    private static final int NEXT = 0xFF6CFF6C;
    private static final int EMPTY = 0xFFF2F6FA;
    private static final int OCCUPIED = 0xFFFFB020;
    private static final int UNAVAILABLE = 0xFF808080;
    private static final int FAILED = 0xFFFF4040;
    private static final int MODEL_COLOR = 0x26AEB9C4;
    private static final int MODEL_COLOR_DAMAGED_ALPHA = 0x5A;
    private static final int HITBOX_ALPHA = 0xE6;
    private static final float[] CLIP_RADII = {0f, 0f, 0f, 0f};
    private static final Color NUMBER_COLOR = new Color(0xFF0B1016);
    private static final Set<String> REPORTED_FAILURES = ConcurrentHashMap.newKeySet();
    private float headingYaw;

    public VehicleModelElement(Document document) {
        super(document, TAG_NAME);
    }

    @Override
    public void drawPhase(PoseStack pose, Base.RenderPhase phase) {
        super.drawPhase(pose, phase);
        if (phase != Base.RenderPhase.BODY) return;
        var context = AuiHudAnimatable.vehicleModelContext(getOwnerDocument());
        if (context == null) return;
        try {
            drawVehicle(pose, context);
        } catch (RuntimeException failure) {
            if (REPORTED_FAILURES.add(context.styleId() + ":" + failure.getClass().getName())) {
                MachineMax.LOGGER.warn("HUD {} 的载具模型绘制失败", context.styleId(), failure);
            }
        }
    }

    private void drawVehicle(PoseStack pose, AuiHudAnimatable.VehicleModelContext context) {
        if (!(context.runtime().host() instanceof AbstractSubsystem source)
                || !(source.getSubPart().part.assembly instanceof VehicleCore vehicle)) return;
        HudVehicleSnapshot snapshot = context.runtime().frame().vehicle();
        if (!vehicle.getAssemblyId().equals(snapshot.assemblyId())) return;
        var layout = SeatMapLayout.snapshot(vehicle);
        if (layout.reference() == null || layout.bounds() == null) return;

        Rect rect = Rect.of(this);
        Position position = rect.getContentPosition();
        var size = rect.box.innerSize();
        float x = (float) position.x;
        float y = (float) position.y;
        float width = (float) size.width();
        float height = (float) size.height();
        if (!(width > 0f && height > 0f && Float.isFinite(width) && Float.isFinite(height))) return;

        float partialTick = context.partialTick();
        Matrix4f referenceToWorld = layout.reference().getWorldPositionMatrix(partialTick);
        Matrix4f worldToReference = new Matrix4f(referenceToWorld).invert();
        Matrix4f rotation;
        String orientation = getAttribute("orientation");
        if (orientation == null || orientation.isBlank() || orientation.equalsIgnoreCase("fixed")) {
            rotation = fixedRotation(angle("yaw", 0f));
        } else if (orientation.equalsIgnoreCase("heading")) {
            headingYaw = SeatMapLayout.relativeHeadingYaw(referenceToWorld,
                    Minecraft.getInstance().gameRenderer.getMainCamera().getYRot(), headingYaw);
            rotation = fixedRotation(headingYaw + angle("yaw", 0f));
        } else if (orientation.equalsIgnoreCase("camera")) {
            rotation = SeatMapLayout.cameraRelativeRotation(referenceToWorld,
                    Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
        } else throw new IllegalArgumentException("未知载具模型 orientation: " + orientation);
        Matrix4f projection = layout.bounds().projection(worldToReference, x, y, width, height, rotation);
        if (!projection.isFinite()) return;

        Base.commitDraws();
        Mask.pushMask(pose, x, y, width, height, CLIP_RADII);
        try {
            boolean damage = Boolean.parseBoolean(getAttribute("damage")) && MMClientConfig.isSeatMapDamageLayerEnabled();
            drawGeometry(pose, layout, projection, partialTick, damage, false);
            if (damage) drawGeometry(pose, layout, projection, partialTick, true, true);
            if (Boolean.parseBoolean(getAttribute("markers"))) {
                drawMarkers(pose, layout, snapshot, projection, partialTick);
            }
        } finally {
            Mask.popMask(pose, x, y, width, height, CLIP_RADII);
        }
    }

    private Matrix4f fixedRotation(float yaw) {
        return new Matrix4f().rotateZ((float) Math.toRadians(angle("roll", 0f)))
                .rotateX((float) Math.toRadians(angle("pitch", 90f)))
                .rotateY((float) Math.toRadians(yaw));
    }

    private float angle(String attribute, float fallback) {
        String value = getAttribute(attribute);
        if (value == null || value.isBlank()) return fallback;
        try {
            float angle = Float.parseFloat(value);
            return Float.isFinite(angle) ? angle % 360f : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static void drawGeometry(PoseStack pose, SeatMapLayout.Snapshot layout, Matrix4f projection,
                                     float partialTick, boolean damage, boolean internal) {
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        var renderType = MMRenderTypes.solidXray();
        pose.pushPose();
        try {
            pose.mulPose(projection);
            VertexConsumer buffer = buffers.getBuffer(renderType);
            for (SubPart sub : layout.subParts()) {
                var model = sub.getPart().getModelController().getModel();
                if (model == null) continue;
                var transform = sub.getRenderWorldPositionMatrix(partialTick);
                if (!transform.isFinite()) continue;
                pose.pushPose();
                try {
                    pose.mulPose(transform);
                    var bones = sub.getBones();
                    if (internal) {
                        drawSubsystemBones(sub, bones, model.getPose(), pose, buffer, partialTick);
                    } else {
                        int color = damage ? modelColor(sub) : MODEL_COLOR;
                        for (OBone bone : bones.values()) {
                            ModelRenderHelperKt.render(bone, model.getPose(), pose, buffer,
                                    Brightness.FULL_BRIGHT.pack(), OverlayTexture.NO_OVERLAY, color, partialTick, false);
                        }
                    }
                } finally {
                    pose.popPose();
                }
            }
        } finally {
            pose.popPose();
            buffers.endBatch(renderType);
        }
    }

    private static void drawMarkers(PoseStack pose, SeatMapLayout.Snapshot layout, HudVehicleSnapshot snapshot,
                                    Matrix4f projection, float partialTick) {
        pose.pushPose();
        try {
            pose.translate(0f, 0f, 0.01f);
            Text label = new Text();
            label.fontSize = 9;
            label.lineHeight = 9;
            label.color = NUMBER_COLOR;
            int count = Math.min(layout.seats().size(), snapshot.seats().size());
            for (int i = 0; i < count; i++) {
                var seat = layout.seats().get(i);
                var data = snapshot.seats().get(i);
                if (!data.hasPosition() || seat.getSubPart().getId() != data.subPartId()
                        || !seat.getName().equals(data.key())) continue;
                var point = SeatMapLayout.seatWorldPosition(seat, partialTick);
                if (point == null || !projection.transformPosition(point).isFinite()) continue;
                int number = i + 1;
                boolean current = snapshot.current() == number;
                boolean pending = snapshot.pending() == number;
                boolean next = snapshot.next() == number;
                int color = data.failed() ? FAILED : current ? CURRENT : pending || next ? NEXT
                        : data.occupied() ? OCCUPIED : data.available() ? EMPTY : UNAVAILABLE;
                label.content = Integer.toString(number);
                float textWidth = (float) Text.measureText(label);
                float radius = Math.max(5f, textWidth * 0.5f + 1f);
                float height = 5f;
                if (pending || (current && snapshot.successPulse())) {
                    radius += 1f;
                    height += 1f;
                }
                Graph.drawFillRect(pose.last().pose(), point.x - radius, point.y - height,
                        point.x + radius, point.y + height, color);
                FontDrawer.drawFont(pose, label, new Position(point.x - textWidth / 2f, point.y - 4.5f));
            }
        } finally {
            pose.popPose();
        }
    }

    /** 内构层：带 hitbox 的骨骼按所属子系统的耐久染色，透过外壳可见 */
    private static void drawSubsystemBones(SubPart sub, Map<String, OBone> bones, ModelPose modelPose,
                                    PoseStack pose, VertexConsumer buffer,
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
    private static int modelColor(SubPart sub) {
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

}
