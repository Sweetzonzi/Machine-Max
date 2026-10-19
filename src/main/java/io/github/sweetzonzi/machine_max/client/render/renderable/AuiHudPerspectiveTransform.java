package io.github.sweetzonzi.machine_max.client.render.renderable;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Quaternionf;

/** 透视面板的局部变换：布局欧拉角按 ZYX 合成，CSS 内容中心映射到局部原点。 */
final class AuiHudPerspectiveTransform {
    private AuiHudPerspectiveTransform() {
    }

    static Quaternionf rotation(double xDegrees, double yDegrees, double zDegrees) {
        return new Quaternionf().rotationZYX(
                (float) Math.toRadians(zDegrees),
                (float) Math.toRadians(yDegrees),
                (float) Math.toRadians(xDegrees));
    }

    static void apply(PoseStack poseStack, Quaternionf rotation,
                      double scaleX, double scaleY, double scaleZ, double width, double height) {
        poseStack.mulPose(rotation);
        poseStack.scale((float) scaleX, (float) -scaleY, (float) scaleZ);
        poseStack.translate(-width / 2.0d, -height / 2.0d, 0.0d);
    }
}
