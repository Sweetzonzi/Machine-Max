package io.github.tt432.machinemax.utils;

import javax.vecmath.Quat4f;
import javax.vecmath.Vector3f;

import static java.lang.Math.PI;
import static java.lang.Math.exp;

public class MMMMath {
    /**
     * 取符号，但与signum不同，这个方法的输出值与输入值的关系是连续的
     * 即，±0左右不会发生跳变，而是得到一个0附近的数字
     * @param a 要取符号的值
     * @return 取得的值，介于[-1,1]之间
     */
    public static double sigmoidSignum(double a){
        return (2/(1+exp(-a))-1);
    }
    /**
     * @param roll  radians roll
     * @param pitch radians pitch
     * @param yaw   radians yaw
     * @return quaternion
     */
    public static Quat4f fromEuler(double roll, double pitch, double yaw) {
        double cr = Math.cos(roll * 0.5);
        double sr = Math.sin(roll * 0.5);
        double cp = Math.cos(pitch * 0.5);
        double sp = Math.sin(pitch * 0.5);
        double cy = Math.cos(yaw * 0.5);
        double sy = Math.sin(yaw * 0.5);

        Quat4f q = new Quat4f();
        q.w = (float) (cr * cp * cy + sr * sp * sy);
        q.x = (float) (sr * cp * cy - cr * sp * sy);
        q.y = (float) (cr * sp * cy + sr * cp * sy);
        q.z = (float) (cr * cp * sy - sr * sp * cy);
        return q;
    }
    /**
     * @param roll  roll degrees
     * @param pitch pitch degrees
     * @param yaw   yaw degrees
     * @return quaternion
     */
    public static Quat4f fromEulerDegrees(double roll, double pitch, double yaw) {
        return fromEuler(Math.toRadians(roll), Math.toRadians(pitch), Math.toRadians(yaw));
    }
    public Vector3f toEuler(Quat4f q) {
        Vector3f angles = new Vector3f();
        q.normalize();
        // roll (x-axis rotation)
        double sinr_cosp = 2 * (q.w * q.x + q.y * q.z);
        double cosr_cosp = 1 - 2 * (q.x * q.x + q.y * q.y);
        double x = (Math.atan2(sinr_cosp, cosr_cosp));

        // pitch (y-axis rotation)
        double sinp = Math.sqrt(1 + 2 * (q.w * q.y - q.x * q.z));
        double cosp = Math.sqrt(1 - 2 * (q.w * q.y - q.x * q.z));
        double y = (2 * Math.atan2(sinp, cosp) - PI / 2);

        // yaw (z-axis rotation)
        double siny_cosp = 2 * (q.w * q.z + q.x * q.y);
        double cosy_cosp = 1 - 2 * (q.y * q.y + q.z * q.z);
        double z = (Math.atan2(siny_cosp, cosy_cosp));
        angles.set((float) x, (float) y, (float) z);
        return angles;
    }

    public Vector3f toEulerDegrees(Quat4f q) {
        Vector3f angles = toEuler(q);
        angles.scale((float) (PI/180));
        return angles;
    }
}
