package io.github.sweetzonzi.machine_max.client.render.gui.hud.molang;

import org.joml.Vector3f;
import org.joml.Vector3fc;

public record HudViewSnapshot(double x, double y, double z, double heading, double pitch,
                              double roll, double fov, double zoom, int width, int height) {
    public static final HudViewSnapshot EMPTY = new HudViewSnapshot(0, 0, 0, 0, 0, 0, 70, 1, 0, 0);

    /** GameRenderer 把横滚施加在视图矩阵上，Camera 的 up 向量仅包含 yaw/pitch。 */
    public HudViewSnapshot withRoll(double roll) {
        return new HudViewSnapshot(x, y, z, heading, pitch, roll, fov, zoom, width, height);
    }

    public static HudViewSnapshot fromCamera(double x, double y, double z, Vector3fc forward,
                                              Vector3fc up, double fov, double zoom, int width, int height) {
        double heading = (Math.toDegrees(Math.atan2(forward.x(), -forward.z())) + 360) % 360;
        double pitch = Math.toDegrees(Math.asin(Math.clamp(forward.y(), -1, 1)));
        Vector3f right = new Vector3f(forward).cross(0, 1, 0);
        double roll = 0;
        if (right.lengthSquared() > 1e-8f) {
            right.normalize();
            Vector3f upright = new Vector3f(right).cross(forward).normalize();
            roll = Math.toDegrees(Math.atan2(up.dot(right), up.dot(upright)));
        }
        return new HudViewSnapshot(x, y, z, heading, pitch, roll, fov, zoom, width, height);
    }
}
