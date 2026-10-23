package io.github.sweetzonzi.machine_max.common.mech.control.aim;

import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import net.minecraft.world.phys.Vec3;
import java.util.Objects;
import jme3utilities.math.MyQuaternion;

/**
 * 可共享的世界位姿快照：位置使用双精度 Vec3，JME 旋转在构造和读取时均复制。
 * 光轴前向为局部 -Z，调用方修改传入或取出的四元数不会改变快照。
 */
public record AimPose(Vec3 position, Quaternion rotation) {
    public static final AimPose IDENTITY = new AimPose(Vec3.ZERO, Quaternion.IDENTITY);
    public AimPose {
        Objects.requireNonNull(position);
        rotation = Objects.requireNonNull(rotation).clone();
    }
    @Override
    public Quaternion rotation() { return rotation.clone(); }
    public Vec3 forward() {
        return SparkMathKt.toVec3(MyQuaternion.rotate(rotation,Vector3f.UNIT_Z.negate(),null)).normalize();
    }
}
